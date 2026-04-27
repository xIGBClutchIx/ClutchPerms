package me.clutchy.clutchperms.fabric;

import java.nio.file.Path;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.UnaryOperator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import me.clutchy.clutchperms.common.audit.AuditLogService;
import me.clutchy.clutchperms.common.command.ClutchPermsCommands;
import me.clutchy.clutchperms.common.command.CommandStatusDiagnostics;
import me.clutchy.clutchperms.common.config.ClutchPermsConfig;
import me.clutchy.clutchperms.common.group.GroupService;
import me.clutchy.clutchperms.common.node.MutablePermissionNodeRegistry;
import me.clutchy.clutchperms.common.node.PermissionNodeRegistry;
import me.clutchy.clutchperms.common.permission.PermissionResolver;
import me.clutchy.clutchperms.common.permission.PermissionService;
import me.clutchy.clutchperms.common.runtime.ClutchPermsRuntime;
import me.clutchy.clutchperms.common.runtime.ClutchPermsRuntimeHooks;
import me.clutchy.clutchperms.common.runtime.ClutchPermsStoragePaths;
import me.clutchy.clutchperms.common.runtime.ScheduledBackupService;
import me.clutchy.clutchperms.common.storage.PermissionStorageException;
import me.clutchy.clutchperms.common.storage.SqliteDependencyMode;
import me.clutchy.clutchperms.common.storage.StorageBackupService;
import me.clutchy.clutchperms.common.storage.StorageFileKind;
import me.clutchy.clutchperms.common.subject.SubjectMetadataService;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * Fabric mod entrypoint that boots the shared persisted permission service, registers shared Brigadier commands, and exposes assignments to fabric-permissions-api.
 */
public final class ClutchPermsFabricMod implements ModInitializer {

    /**
     * Mod identifier used by Fabric metadata and config paths.
     */
    public static final String MOD_ID = "clutchperms";

    private static final Logger LOGGER = LoggerFactory.getLogger(ClutchPermsFabricMod.class);

    /**
     * Active shared storage runtime for the current Fabric server lifecycle.
     */
    private static ClutchPermsRuntime runtime;

    /**
     * Tracks whether the Fabric permissions API bridge was registered during bootstrap.
     */
    private static boolean runtimeBridgeRegistered;

    /**
     * Active server used to resend Brigadier command trees after permission changes.
     */
    private static MinecraftServer activeServer;

    /**
     * Runs automatic database backups for the active server lifecycle.
     */
    private static ScheduledBackupService scheduledBackupService;

    /**
     * Initializes the shared persisted service and hooks command registration into the Fabric lifecycle.
     */
    @Override
    public void onInitialize() {
        Path storageDirectory = FabricLoader.getInstance().getConfigDir().resolve(MOD_ID);
        runtime = new ClutchPermsRuntime(ClutchPermsStoragePaths.inDirectory(storageDirectory),
                new ClutchPermsRuntimeHooks(ClutchPermsFabricMod::refreshRuntimeSubject, ClutchPermsFabricMod::refreshRuntimePermissions),
                SqliteDependencyMode.BUNDLED_WITH_CLUTCHPERMS);
        try {
            reloadStorage();
        } catch (PermissionStorageException exception) {
            throw new IllegalStateException("Failed to load ClutchPerms storage", exception);
        }
        scheduledBackupService = new ScheduledBackupService(ClutchPermsFabricMod::getClutchPermsConfig, ClutchPermsFabricMod::getStorageBackupService, LOGGER::info, LOGGER::error);

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> ClutchPermsCommands.ROOT_LITERALS
                .forEach(rootLiteral -> dispatcher.register(FabricClutchPermsCommand.create(ClutchPermsFabricMod::getPermissionService,
                        ClutchPermsFabricMod::getSubjectMetadataService, ClutchPermsFabricMod::getGroupService, ClutchPermsFabricMod::getPermissionNodeRegistry,
                        ClutchPermsFabricMod::getManualPermissionNodeRegistry, ClutchPermsFabricMod::getPermissionResolver, ClutchPermsFabricMod::getStatusDiagnostics,
                        ClutchPermsFabricMod::reloadStorage, ClutchPermsFabricMod::validateStorage, ClutchPermsFabricMod::getStorageBackupService,
                        ClutchPermsFabricMod::getClutchPermsConfig, ClutchPermsFabricMod::updateConfig, ClutchPermsFabricMod::getAuditLogService,
                        ClutchPermsFabricMod::restoreBackup, ClutchPermsFabricMod::refreshRuntimePermissions, ClutchPermsFabricMod::getScheduledBackupService, rootLiteral))));
        FabricRuntimePermissionBridge.register(ClutchPermsFabricMod::getPermissionResolver);
        runtimeBridgeRegistered = true;
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> recordSubject(handler.getPlayer()));
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            activeServer = server;
            server.getPlayerList().getPlayers().forEach(ClutchPermsFabricMod::recordSubject);
            refreshRuntimePermissions();
            getScheduledBackupService().start();
        });

        // Clear the static reference when the server stops so stale state is not retained.
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            if (runtime != null) {
                closeScheduledBackups();
                runtime.clear();
                runtime = null;
            }
            activeServer = null;
        });
    }

    /**
     * Returns the active permission service instance.
     *
     * @return the service initialized during Fabric bootstrap
     * @throws NullPointerException if the service is requested before initialization completes
     */
    public static PermissionService getPermissionService() {
        return getRuntime().permissionService();
    }

    /**
     * Returns the active subject metadata service instance.
     *
     * @return the service initialized during Fabric bootstrap
     * @throws NullPointerException if the service is requested before initialization completes
     */
    public static SubjectMetadataService getSubjectMetadataService() {
        return getRuntime().subjectMetadataService();
    }

    /**
     * Returns the active group service instance.
     *
     * @return the service initialized during Fabric bootstrap
     */
    public static GroupService getGroupService() {
        return getRuntime().groupService();
    }

    /**
     * Returns the active merged known node registry.
     *
     * @return the registry initialized during Fabric bootstrap
     */
    public static PermissionNodeRegistry getPermissionNodeRegistry() {
        return getRuntime().permissionNodeRegistry();
    }

    /**
     * Returns the active manual known node registry.
     *
     * @return the manual registry initialized during Fabric bootstrap
     */
    public static MutablePermissionNodeRegistry getManualPermissionNodeRegistry() {
        return getRuntime().manualPermissionNodeRegistry();
    }

    /**
     * Returns the active effective permission resolver.
     *
     * @return the resolver initialized during Fabric bootstrap
     */
    public static PermissionResolver getPermissionResolver() {
        return getRuntime().permissionResolver();
    }

    /**
     * Formats a full Fabric chat line with the active display resolver.
     *
     * @param player chat sender
     * @param message original chat message component
     * @return formatted chat component, or empty when ClutchPerms runtime is not active
     */
    public static Optional<Component> formatChatMessage(ServerPlayer player, Component message) {
        if (runtime == null || !getRuntime().config().chat().enabled()) {
            return Optional.empty();
        }
        Component displayName = Component.literal(player.getGameProfile().name());
        return Optional.of(FabricDisplayComponents.chatLine(player.getUUID(), displayName, message, getRuntime().displayResolver()));
    }

    /**
     * Returns the active runtime config.
     *
     * @return runtime config initialized during Fabric bootstrap
     */
    public static ClutchPermsConfig getClutchPermsConfig() {
        return getRuntime().config();
    }

    /**
     * Returns command audit history storage.
     *
     * @return active audit log service
     */
    public static AuditLogService getAuditLogService() {
        return getRuntime().auditLogService();
    }

    /**
     * Returns the scheduled backup runner used by shared backup schedule commands.
     *
     * @return active scheduled backup runner
     */
    public static ScheduledBackupService getScheduledBackupService() {
        return Objects.requireNonNull(scheduledBackupService, "Scheduled backup service has not been initialized");
    }

    /**
     * Updates config through the shared runtime.
     *
     * @param updater config updater
     */
    public static void updateConfig(UnaryOperator<ClutchPermsConfig> updater) {
        getRuntime().updateConfig(updater);
        restartScheduledBackups();
    }

    /**
     * Returns status diagnostics for the shared command tree.
     *
     * @return active command status diagnostics
     */
    public static CommandStatusDiagnostics getStatusDiagnostics() {
        String bridgeStatus = runtimeBridgeRegistered ? "Fabric permissions API bridge registered" : "Fabric permissions API bridge not registered";
        return getRuntime().statusDiagnostics(bridgeStatus);
    }

    /**
     * Reloads persisted storage from disk for the shared reload command.
     */
    public static void reloadStorage() {
        logStorageLoadStart();
        try {
            getRuntime().reload();
            restartScheduledBackups();
            logStorageLoadSuccess();
        } catch (RuntimeException exception) {
            LOGGER.error("Failed to load ClutchPerms storage from {}", storageRoot(), exception);
            throw exception;
        }
    }

    /**
     * Validates persisted storage from disk without replacing active services or runtime state.
     */
    public static void validateStorage() {
        getRuntime().validate();
    }

    /**
     * Returns the backup service used by shared backup commands.
     *
     * @return active storage backup service
     */
    public static StorageBackupService getStorageBackupService() {
        return getRuntime().storageBackupService();
    }

    /**
     * Restores one database backup after closing the active SQLite pool.
     *
     * @param kind selected storage kind
     * @param backupFileName backup filename
     */
    public static void restoreBackup(StorageFileKind kind, String backupFileName) {
        logStorageLoadStart();
        try {
            getRuntime().restoreBackup(kind, backupFileName);
            refreshRuntimePermissions();
            restartScheduledBackups();
            logStorageLoadSuccess();
        } catch (RuntimeException exception) {
            LOGGER.error("Failed to restore ClutchPerms database backup {} from {}", backupFileName, storageRoot(), exception);
            throw exception;
        }
    }

    /**
     * Refreshes Fabric command suggestions for one online subject after a permission mutation.
     *
     * @param subjectId subject whose command tree should be resent
     */
    static void refreshRuntimeSubject(UUID subjectId) {
        MinecraftServer server = activeServer;
        if (server == null) {
            return;
        }
        server.executeIfPossible(() -> {
            refreshOnlineSubject(subjectId, server.getPlayerList()::getPlayer, player -> server.getCommands().sendCommands(player));
        });
    }

    /**
     * Refreshes Fabric command suggestions for every online player after broad permission changes or reload.
     */
    public static void refreshRuntimePermissions() {
        MinecraftServer server = activeServer;
        if (server == null) {
            return;
        }
        server.executeIfPossible(() -> refreshOnlinePlayers(server.getPlayerList().getPlayers(), player -> server.getCommands().sendCommands(player)));
    }

    static <P> void refreshOnlineSubject(UUID subjectId, Function<UUID, P> onlinePlayerLookup, Consumer<P> commandSender) {
        P player = onlinePlayerLookup.apply(subjectId);
        if (player != null) {
            commandSender.accept(player);
        }
    }

    static <P> void refreshOnlinePlayers(Iterable<P> onlinePlayers, Consumer<P> commandSender) {
        onlinePlayers.forEach(commandSender);
    }

    private static ClutchPermsRuntime getRuntime() {
        return Objects.requireNonNull(runtime, "ClutchPerms runtime has not been initialized");
    }

    private static Path storageRoot() {
        return getRuntime().storagePaths().storageRoot();
    }

    private static void logStorageLoadStart() {
        LOGGER.debug("ClutchPerms database file: {}", getRuntime().storagePaths().databaseFile());
    }

    private static void restartScheduledBackups() {
        if (activeServer != null && scheduledBackupService != null) {
            scheduledBackupService.restart();
        }
    }

    private static void closeScheduledBackups() {
        if (scheduledBackupService != null) {
            scheduledBackupService.close();
            scheduledBackupService = null;
        }
    }

    private static void logStorageLoadSuccess() {
        LOGGER.info("Loaded ClutchPerms storage from {}: {} known subjects, {} groups, {} manual known nodes, {} total known nodes.", storageRoot(),
                getSubjectMetadataService().getSubjects().size(), getGroupService().getGroups().size(), getManualPermissionNodeRegistry().getKnownNodes().size(),
                getPermissionNodeRegistry().getKnownNodes().size());
    }

    private static void recordSubject(ServerPlayer player) {
        getSubjectMetadataService().recordSubject(player.getUUID(), player.getGameProfile().name(), Instant.now());
    }
}
