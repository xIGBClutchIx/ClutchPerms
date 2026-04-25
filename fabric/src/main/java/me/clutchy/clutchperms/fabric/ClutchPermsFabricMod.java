package me.clutchy.clutchperms.fabric;

import java.nio.file.Path;
import java.time.Instant;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import me.clutchy.clutchperms.common.command.ClutchPermsCommands;
import me.clutchy.clutchperms.common.command.CommandStatusDiagnostics;
import me.clutchy.clutchperms.common.group.GroupService;
import me.clutchy.clutchperms.common.node.MutablePermissionNodeRegistry;
import me.clutchy.clutchperms.common.node.PermissionNodeRegistry;
import me.clutchy.clutchperms.common.permission.PermissionResolver;
import me.clutchy.clutchperms.common.permission.PermissionService;
import me.clutchy.clutchperms.common.runtime.ClutchPermsRuntime;
import me.clutchy.clutchperms.common.runtime.ClutchPermsRuntimeHooks;
import me.clutchy.clutchperms.common.runtime.ClutchPermsStoragePaths;
import me.clutchy.clutchperms.common.storage.PermissionStorageException;
import me.clutchy.clutchperms.common.storage.StorageBackupService;
import me.clutchy.clutchperms.common.subject.SubjectMetadataService;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
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
     * Initializes the shared persisted service and hooks command registration into the Fabric lifecycle.
     */
    @Override
    public void onInitialize() {
        Path storageDirectory = FabricLoader.getInstance().getConfigDir().resolve(MOD_ID);
        runtime = new ClutchPermsRuntime(ClutchPermsStoragePaths.inDirectory(storageDirectory), ClutchPermsRuntimeHooks.noop());
        try {
            reloadStorage();
        } catch (PermissionStorageException exception) {
            throw new IllegalStateException("Failed to load ClutchPerms storage", exception);
        }

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> ClutchPermsCommands.ROOT_LITERALS.forEach(
                rootLiteral -> dispatcher.register(FabricClutchPermsCommand.create(ClutchPermsFabricMod::getPermissionService, ClutchPermsFabricMod::getSubjectMetadataService,
                        ClutchPermsFabricMod::getGroupService, ClutchPermsFabricMod::getPermissionNodeRegistry, ClutchPermsFabricMod::getManualPermissionNodeRegistry,
                        ClutchPermsFabricMod::getPermissionResolver, ClutchPermsFabricMod::getStatusDiagnostics, ClutchPermsFabricMod::reloadStorage,
                        ClutchPermsFabricMod::validateStorage, ClutchPermsFabricMod::getStorageBackupService, ClutchPermsFabricMod::refreshRuntimePermissions, rootLiteral))));
        FabricRuntimePermissionBridge.register(ClutchPermsFabricMod::getPermissionResolver);
        runtimeBridgeRegistered = true;
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> recordSubject(handler.getPlayer()));
        ServerLifecycleEvents.SERVER_STARTED.register(server -> server.getPlayerList().getPlayers().forEach(ClutchPermsFabricMod::recordSubject));

        // Clear the static reference when the server stops so stale state is not retained.
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            if (runtime != null) {
                runtime.clear();
                runtime = null;
            }
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
     * Refreshes Fabric runtime permission state after reload.
     */
    public static void refreshRuntimePermissions() {
        // The Fabric permissions API bridge queries the active supplier on every check, so there is no cached runtime state to refresh.
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

    private static void recordSubject(ServerPlayer player) {
        getSubjectMetadataService().recordSubject(player.getUUID(), player.getGameProfile().name(), Instant.now());
    }
}
