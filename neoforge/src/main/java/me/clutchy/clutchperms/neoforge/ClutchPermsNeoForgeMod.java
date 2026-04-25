package me.clutchy.clutchperms.neoforge;

import java.nio.file.Path;
import java.time.Instant;
import java.util.Objects;
import java.util.function.UnaryOperator;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

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
import me.clutchy.clutchperms.common.storage.PermissionStorageException;
import me.clutchy.clutchperms.common.storage.StorageBackupService;
import me.clutchy.clutchperms.common.subject.SubjectMetadataService;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.ServerChatEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.server.permission.PermissionAPI;
import net.neoforged.neoforge.server.permission.events.PermissionGatherEvent;

/**
 * NeoForge mod entrypoint that boots the shared persisted permission service, registers shared Brigadier commands, and contributes a native permission handler.
 */
@Mod(ClutchPermsNeoForgeMod.MOD_ID)
public final class ClutchPermsNeoForgeMod {

    /**
     * Mod identifier used by NeoForge metadata and the annotated entrypoint.
     */
    public static final String MOD_ID = "clutchperms";

    private static final Logger LOGGER = LogUtils.getLogger();

    /**
     * Active shared storage runtime for the current NeoForge server lifecycle.
     */
    private static ClutchPermsRuntime runtime;

    /**
     * Initializes the shared persisted service and hooks command registration into the NeoForge lifecycle.
     */
    public ClutchPermsNeoForgeMod() {
        Path storageDirectory = FMLPaths.CONFIGDIR.get().resolve(MOD_ID);
        runtime = new ClutchPermsRuntime(ClutchPermsStoragePaths.inDirectory(storageDirectory),
                () -> PermissionNodeRegistries.supplying(PermissionNodeSource.PLATFORM, ClutchPermsNeoForgeMod::registeredBooleanPermissionNodes), ClutchPermsRuntimeHooks.noop());
        try {
            reloadStorage();
        } catch (PermissionStorageException exception) {
            throw new IllegalStateException("Failed to load ClutchPerms storage", exception);
        }

        NeoForge.EVENT_BUS.addListener(this::registerCommands);
        NeoForge.EVENT_BUS.addListener(this::registerPermissionHandler);
        NeoForge.EVENT_BUS.addListener(this::registerPermissionNodes);
        NeoForge.EVENT_BUS.addListener(this::onServerChat);
        NeoForge.EVENT_BUS.addListener(this::onServerStarted);
        NeoForge.EVENT_BUS.addListener(this::onPlayerLoggedIn);
        NeoForge.EVENT_BUS.addListener(this::onServerStopped);
    }

    private void registerCommands(RegisterCommandsEvent event) {
        ClutchPermsCommands.ROOT_LITERALS.forEach(rootLiteral -> event.getDispatcher()
                .register(NeoForgeClutchPermsCommand.create(ClutchPermsNeoForgeMod::getPermissionService, ClutchPermsNeoForgeMod::getSubjectMetadataService,
                        ClutchPermsNeoForgeMod::getGroupService, ClutchPermsNeoForgeMod::getPermissionNodeRegistry, ClutchPermsNeoForgeMod::getManualPermissionNodeRegistry,
                        ClutchPermsNeoForgeMod::getPermissionResolver, ClutchPermsNeoForgeMod::getStatusDiagnostics, ClutchPermsNeoForgeMod::reloadStorage,
                        ClutchPermsNeoForgeMod::validateStorage, ClutchPermsNeoForgeMod::getStorageBackupService, ClutchPermsNeoForgeMod::getClutchPermsConfig,
                        ClutchPermsNeoForgeMod::updateConfig, ClutchPermsNeoForgeMod::refreshRuntimePermissions, rootLiteral)));
    }

    private void registerPermissionHandler(PermissionGatherEvent.Handler event) {
        event.addPermissionHandler(NeoForgeClutchPermsPermissionHandler.IDENTIFIER,
                registeredNodes -> new NeoForgeClutchPermsPermissionHandler(ClutchPermsNeoForgeMod::getPermissionResolver, registeredNodes));
    }

    private void registerPermissionNodes(PermissionGatherEvent.Nodes event) {
        event.addNodes(NeoForgeClutchPermsPermissionHandler.commandNodes());
    }

    private void onServerStarted(ServerStartedEvent event) {
        event.getServer().getPlayerList().getPlayers().forEach(this::recordSubject);

        if (NeoForgeClutchPermsPermissionHandler.IDENTIFIER.equals(PermissionAPI.getActivePermissionHandler())) {
            LOGGER.info("ClutchPerms NeoForge runtime permission bridge is active.");
            return;
        }

        LOGGER.info("ClutchPerms NeoForge runtime permission bridge is registered but inactive. Set the NeoForge server permissionHandler config value to {} to activate it.",
                NeoForgeClutchPermsPermissionHandler.IDENTIFIER);
    }

    private void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            recordSubject(player);
        }
    }

    private void onServerChat(ServerChatEvent event) {
        if (!getRuntime().config().chat().enabled()) {
            return;
        }
        Component formattedMessage = NeoForgeDisplayComponents.chatLine(event.getPlayer().getUUID(), Component.literal(event.getUsername()), event.getMessage(),
                getRuntime().displayResolver());
        event.getPlayer().level().getServer().getPlayerList().broadcastSystemMessage(formattedMessage, false);
        event.setCanceled(true);
    }

    private void onServerStopped(ServerStoppedEvent event) {
        if (runtime != null) {
            runtime.clear();
            runtime = null;
        }
    }

    /**
     * Returns the active permission service instance.
     *
     * @return the service initialized during NeoForge bootstrap
     * @throws NullPointerException if the service is requested before initialization completes
     */
    public static PermissionService getPermissionService() {
        return getRuntime().permissionService();
    }

    /**
     * Returns the active subject metadata service instance.
     *
     * @return the service initialized during NeoForge bootstrap
     * @throws NullPointerException if the service is requested before initialization completes
     */
    public static SubjectMetadataService getSubjectMetadataService() {
        return getRuntime().subjectMetadataService();
    }

    /**
     * Returns the active group service instance.
     *
     * @return the service initialized during NeoForge bootstrap
     */
    public static GroupService getGroupService() {
        return getRuntime().groupService();
    }

    /**
     * Returns the active merged known node registry.
     *
     * @return the registry initialized during NeoForge bootstrap
     */
    public static PermissionNodeRegistry getPermissionNodeRegistry() {
        return getRuntime().permissionNodeRegistry();
    }

    /**
     * Returns the active manual known node registry.
     *
     * @return the manual registry initialized during NeoForge bootstrap
     */
    public static MutablePermissionNodeRegistry getManualPermissionNodeRegistry() {
        return getRuntime().manualPermissionNodeRegistry();
    }

    /**
     * Returns the active effective permission resolver.
     *
     * @return the resolver initialized during NeoForge bootstrap
     */
    public static PermissionResolver getPermissionResolver() {
        return getRuntime().permissionResolver();
    }

    /**
     * Returns the active runtime config.
     *
     * @return runtime config initialized during NeoForge bootstrap
     */
    public static ClutchPermsConfig getClutchPermsConfig() {
        return getRuntime().config();
    }

    /**
     * Updates config through the shared runtime.
     *
     * @param updater config updater
     */
    public static void updateConfig(UnaryOperator<ClutchPermsConfig> updater) {
        getRuntime().updateConfig(updater);
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
     * Refreshes NeoForge runtime permission state after reload.
     */
    public static void refreshRuntimePermissions() {
        // NeoForge asks the active permission handler on demand, and the handler reads the current storage supplier.
    }

    private static String runtimeBridgeStatus() {
        if (NeoForgeClutchPermsPermissionHandler.IDENTIFIER.equals(PermissionAPI.getActivePermissionHandler())) {
            return "NeoForge permission handler active as " + NeoForgeClutchPermsPermissionHandler.IDENTIFIER;
        }
        return "NeoForge permission handler registered but inactive; set server permissionHandler to " + NeoForgeClutchPermsPermissionHandler.IDENTIFIER;
    }

    private static java.util.List<String> registeredBooleanPermissionNodes() {
        return NeoForgeClutchPermsPermissionHandler.booleanNodeNames(PermissionAPI.getRegisteredNodes());
    }

    private static ClutchPermsRuntime getRuntime() {
        return Objects.requireNonNull(runtime, "ClutchPerms runtime has not been initialized");
    }

    private static Path storageRoot() {
        return getRuntime().storagePaths().storageRoot();
    }

    private static void logStorageLoadStart() {
        LOGGER.debug("ClutchPerms storage files: permissions={}, subjects={}, groups={}, nodes={}", getRuntime().storagePaths().permissionsFile(),
                getRuntime().storagePaths().subjectsFile(), getRuntime().storagePaths().groupsFile(), getRuntime().storagePaths().nodesFile());
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
