package me.clutchy.clutchperms.forge;

import java.nio.file.Path;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.UnaryOperator;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import me.clutchy.clutchperms.common.audit.AuditLogService;
import me.clutchy.clutchperms.common.command.ClutchPermsCommands;
import me.clutchy.clutchperms.common.command.CommandStatusDiagnostics;
import me.clutchy.clutchperms.common.config.ClutchPermsConfig;
import me.clutchy.clutchperms.common.group.GroupService;
import me.clutchy.clutchperms.common.node.MutablePermissionNodeRegistry;
import me.clutchy.clutchperms.common.node.PermissionNodeRegistries;
import me.clutchy.clutchperms.common.node.PermissionNodeRegistry;
import me.clutchy.clutchperms.common.node.PermissionNodeSource;
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
import me.clutchy.clutchperms.common.track.TrackService;

import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.ServerChatEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.server.permission.PermissionAPI;
import net.minecraftforge.server.permission.events.PermissionGatherEvent;

/**
 * Forge mod entrypoint that boots the shared persisted permission service, registers shared Brigadier commands, and contributes a native permission handler.
 */
@Mod(ClutchPermsForgeMod.MOD_ID)
public final class ClutchPermsForgeMod {

    /**
     * Mod identifier used by Forge metadata and the annotated entrypoint.
     */
    public static final String MOD_ID = "clutchperms";

    private static final Logger LOGGER = LogUtils.getLogger();

    /**
     * Active shared storage runtime for the current Forge server lifecycle.
     */
    private static ClutchPermsRuntime runtime;

    /**
     * Active server used to resend Brigadier command trees after permission changes.
     */
    private static MinecraftServer activeServer;

    /**
     * Runs automatic database backups for the active server lifecycle.
     */
    private static ScheduledBackupService scheduledBackupService;

    /**
     * Initializes the shared persisted service and hooks command registration into the Forge lifecycle.
     */
    public ClutchPermsForgeMod() {
        Path storageDirectory = FMLPaths.CONFIGDIR.get().resolve(MOD_ID);
        runtime = new ClutchPermsRuntime(ClutchPermsStoragePaths.inDirectory(storageDirectory),
                () -> PermissionNodeRegistries.supplying(PermissionNodeSource.PLATFORM, ClutchPermsForgeMod::registeredBooleanPermissionNodes),
                new ClutchPermsRuntimeHooks(ClutchPermsForgeMod::refreshRuntimeSubject, ClutchPermsForgeMod::refreshRuntimePermissions),
                SqliteDependencyMode.BUNDLED_WITH_CLUTCHPERMS);
        try {
            reloadStorage();
        } catch (PermissionStorageException exception) {
            throw new IllegalStateException("Failed to load ClutchPerms storage", exception);
        }
        scheduledBackupService = new ScheduledBackupService(ClutchPermsForgeMod::getClutchPermsConfig, ClutchPermsForgeMod::getStorageBackupService, LOGGER::info, LOGGER::error);

        RegisterCommandsEvent.BUS.addListener(this::registerCommands);
        PermissionGatherEvent.Handler.BUS.addListener(this::registerPermissionHandler);
        PermissionGatherEvent.Nodes.BUS.addListener(this::registerPermissionNodes);
        ServerChatEvent.BUS.addListener((java.util.function.Predicate<ServerChatEvent>) this::onServerChat);
        ServerStartedEvent.BUS.addListener(this::onServerStarted);
        PlayerEvent.PlayerLoggedInEvent.BUS.addListener(this::onPlayerLoggedIn);
        ServerStoppedEvent.BUS.addListener(this::onServerStopped);
    }

    private void registerCommands(RegisterCommandsEvent event) {
        ClutchPermsCommands.ROOT_LITERALS.forEach(rootLiteral -> event.getDispatcher()
                .register(ForgeClutchPermsCommand.create(ClutchPermsForgeMod::getPermissionService, ClutchPermsForgeMod::getSubjectMetadataService,
                        ClutchPermsForgeMod::getGroupService, ClutchPermsForgeMod::getTrackService, ClutchPermsForgeMod::getPermissionNodeRegistry,
                        ClutchPermsForgeMod::getManualPermissionNodeRegistry, ClutchPermsForgeMod::getPermissionResolver, ClutchPermsForgeMod::getStatusDiagnostics,
                        ClutchPermsForgeMod::reloadStorage, ClutchPermsForgeMod::validateStorage, ClutchPermsForgeMod::getStorageBackupService,
                        ClutchPermsForgeMod::getClutchPermsConfig, ClutchPermsForgeMod::updateConfig, ClutchPermsForgeMod::getAuditLogService, ClutchPermsForgeMod::restoreBackup,
                        ClutchPermsForgeMod::refreshRuntimePermissions, ClutchPermsForgeMod::getScheduledBackupService, rootLiteral)));
    }

    private void registerPermissionHandler(PermissionGatherEvent.Handler event) {
        event.addPermissionHandler(ForgeClutchPermsPermissionHandler.IDENTIFIER,
                registeredNodes -> new ForgeClutchPermsPermissionHandler(ClutchPermsForgeMod::getPermissionResolver, registeredNodes));
    }

    private void registerPermissionNodes(PermissionGatherEvent.Nodes event) {
        event.addNodes(ForgeClutchPermsPermissionHandler.commandNodes());
    }

    private void onServerStarted(ServerStartedEvent event) {
        activeServer = event.getServer();
        event.getServer().getPlayerList().getPlayers().forEach(this::recordSubject);
        refreshRuntimePermissions();
        getScheduledBackupService().start();

        if (ForgeClutchPermsPermissionHandler.IDENTIFIER.equals(PermissionAPI.getActivePermissionHandler())) {
            LOGGER.info("ClutchPerms Forge runtime permission bridge is active.");
            return;
        }

        LOGGER.info("ClutchPerms Forge runtime permission bridge is registered but inactive. Set the Forge server permissionHandler config value to {} to activate it.",
                ForgeClutchPermsPermissionHandler.IDENTIFIER);
    }

    private void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            recordSubject(player);
        }
    }

    private boolean onServerChat(ServerChatEvent event) {
        if (!getRuntime().config().chat().enabled()) {
            return false;
        }
        Component formattedMessage = ForgeDisplayComponents.chatLine(event.getPlayer().getUUID(), Component.literal(event.getUsername()), event.getMessage(),
                getRuntime().displayResolver());
        event.getPlayer().level().getServer().getPlayerList().broadcastSystemMessage(formattedMessage, false);
        return true;
    }

    private void onServerStopped(ServerStoppedEvent event) {
        if (runtime != null) {
            closeScheduledBackups();
            runtime.clear();
            runtime = null;
        }
        activeServer = null;
    }

    /**
     * Returns the active permission service instance.
     *
     * @return the service initialized during Forge bootstrap
     * @throws NullPointerException if the service is requested before initialization completes
     */
    public static PermissionService getPermissionService() {
        return getRuntime().permissionService();
    }

    /**
     * Returns the active subject metadata service instance.
     *
     * @return the service initialized during Forge bootstrap
     * @throws NullPointerException if the service is requested before initialization completes
     */
    public static SubjectMetadataService getSubjectMetadataService() {
        return getRuntime().subjectMetadataService();
    }

    /**
     * Returns the active group service instance.
     *
     * @return the service initialized during Forge bootstrap
     */
    public static GroupService getGroupService() {
        return getRuntime().groupService();
    }

    /**
     * Returns the active track service instance.
     *
     * @return the service initialized during Forge bootstrap
     */
    public static TrackService getTrackService() {
        return getRuntime().trackService();
    }

    /**
     * Returns the active merged known node registry.
     *
     * @return the registry initialized during Forge bootstrap
     */
    public static PermissionNodeRegistry getPermissionNodeRegistry() {
        return getRuntime().permissionNodeRegistry();
    }

    /**
     * Returns the active manual known node registry.
     *
     * @return the manual registry initialized during Forge bootstrap
     */
    public static MutablePermissionNodeRegistry getManualPermissionNodeRegistry() {
        return getRuntime().manualPermissionNodeRegistry();
    }

    /**
     * Returns the active effective permission resolver.
     *
     * @return the resolver initialized during Forge bootstrap
     */
    public static PermissionResolver getPermissionResolver() {
        return getRuntime().permissionResolver();
    }

    /**
     * Returns the active runtime config.
     *
     * @return runtime config initialized during Forge bootstrap
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
        return getRuntime().statusDiagnostics(runtimeBridgeStatus());
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
     * Refreshes Forge command suggestions for one online subject after a permission mutation.
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
     * Refreshes Forge command suggestions for every online player after broad permission changes or reload.
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

    private static String runtimeBridgeStatus() {
        if (ForgeClutchPermsPermissionHandler.IDENTIFIER.equals(PermissionAPI.getActivePermissionHandler())) {
            return "Forge permission handler active as " + ForgeClutchPermsPermissionHandler.IDENTIFIER;
        }
        return "Forge permission handler registered but inactive; set server permissionHandler to " + ForgeClutchPermsPermissionHandler.IDENTIFIER;
    }

    private static java.util.List<String> registeredBooleanPermissionNodes() {
        return ForgeClutchPermsPermissionHandler.booleanNodeNames(PermissionAPI.getRegisteredNodes());
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

    private void recordSubject(ServerPlayer player) {
        getSubjectMetadataService().recordSubject(player.getUUID(), player.getGameProfile().name(), Instant.now());
    }
}
