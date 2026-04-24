package me.clutchy.clutchperms.fabric;

import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import me.clutchy.clutchperms.common.command.CommandStatusDiagnostics;
import me.clutchy.clutchperms.common.group.GroupChangeListener;
import me.clutchy.clutchperms.common.group.GroupService;
import me.clutchy.clutchperms.common.group.GroupServices;
import me.clutchy.clutchperms.common.node.MutablePermissionNodeRegistry;
import me.clutchy.clutchperms.common.node.PermissionNodeRegistries;
import me.clutchy.clutchperms.common.node.PermissionNodeRegistry;
import me.clutchy.clutchperms.common.permission.PermissionResolver;
import me.clutchy.clutchperms.common.permission.PermissionService;
import me.clutchy.clutchperms.common.permission.PermissionServices;
import me.clutchy.clutchperms.common.storage.PermissionStorageException;
import me.clutchy.clutchperms.common.storage.StorageBackupService;
import me.clutchy.clutchperms.common.storage.StorageFileKind;
import me.clutchy.clutchperms.common.subject.SubjectMetadataService;
import me.clutchy.clutchperms.common.subject.SubjectMetadataServices;

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
     * Active permission service instance for the current Fabric server lifecycle.
     */
    private static PermissionService permissionService;

    /**
     * Active subject metadata service instance for the current Fabric server lifecycle.
     */
    private static SubjectMetadataService subjectMetadataService;

    /**
     * Active group service instance for the current Fabric server lifecycle.
     */
    private static GroupService groupService;

    /**
     * Active manual known node registry for the current Fabric server lifecycle.
     */
    private static MutablePermissionNodeRegistry manualPermissionNodeRegistry;

    /**
     * Active merged known node registry for the current Fabric server lifecycle.
     */
    private static PermissionNodeRegistry permissionNodeRegistry;

    /**
     * Active effective permission resolver for the current Fabric server lifecycle.
     */
    private static PermissionResolver permissionResolver;

    /**
     * Permission assignment storage path for diagnostics.
     */
    private static Path permissionsFile;

    /**
     * Subject metadata storage path for diagnostics.
     */
    private static Path subjectsFile;

    /**
     * Group storage path for diagnostics.
     */
    private static Path groupsFile;

    /**
     * Manual known permission node registry storage path for diagnostics.
     */
    private static Path nodesFile;

    /**
     * Tracks whether the Fabric permissions API bridge was registered during bootstrap.
     */
    private static boolean runtimeBridgeRegistered;

    /**
     * Initializes the shared persisted service and hooks command registration into the Fabric lifecycle.
     */
    @Override
    public void onInitialize() {
        permissionsFile = FabricLoader.getInstance().getConfigDir().resolve(MOD_ID).resolve("permissions.json");
        subjectsFile = FabricLoader.getInstance().getConfigDir().resolve(MOD_ID).resolve("subjects.json");
        groupsFile = FabricLoader.getInstance().getConfigDir().resolve(MOD_ID).resolve("groups.json");
        nodesFile = FabricLoader.getInstance().getConfigDir().resolve(MOD_ID).resolve("nodes.json");
        try {
            reloadStorage();
        } catch (PermissionStorageException exception) {
            LOGGER.error("Failed to load ClutchPerms storage from {}", FabricLoader.getInstance().getConfigDir().resolve(MOD_ID), exception);
            throw new IllegalStateException("Failed to load ClutchPerms storage", exception);
        }

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess,
                environment) -> dispatcher.register(FabricClutchPermsCommand.create(ClutchPermsFabricMod::getPermissionService, ClutchPermsFabricMod::getSubjectMetadataService,
                        ClutchPermsFabricMod::getGroupService, ClutchPermsFabricMod::getPermissionNodeRegistry, ClutchPermsFabricMod::getManualPermissionNodeRegistry,
                        ClutchPermsFabricMod::getPermissionResolver, ClutchPermsFabricMod::getStatusDiagnostics, ClutchPermsFabricMod::reloadStorage,
                        ClutchPermsFabricMod::validateStorage, ClutchPermsFabricMod::getStorageBackupService, ClutchPermsFabricMod::refreshRuntimePermissions)));
        FabricRuntimePermissionBridge.register(ClutchPermsFabricMod::getPermissionResolver);
        runtimeBridgeRegistered = true;
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> recordSubject(handler.getPlayer()));
        ServerLifecycleEvents.SERVER_STARTED.register(server -> server.getPlayerList().getPlayers().forEach(ClutchPermsFabricMod::recordSubject));

        // Clear the static reference when the server stops so stale state is not retained.
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            permissionService = null;
            subjectMetadataService = null;
            groupService = null;
            manualPermissionNodeRegistry = null;
            permissionNodeRegistry = null;
            permissionResolver = null;
        });
    }

    /**
     * Returns the active permission service instance.
     *
     * @return the service initialized during Fabric bootstrap
     * @throws NullPointerException if the service is requested before initialization completes
     */
    public static PermissionService getPermissionService() {
        return Objects.requireNonNull(permissionService, "Permission service has not been initialized");
    }

    /**
     * Returns the active subject metadata service instance.
     *
     * @return the service initialized during Fabric bootstrap
     * @throws NullPointerException if the service is requested before initialization completes
     */
    public static SubjectMetadataService getSubjectMetadataService() {
        return Objects.requireNonNull(subjectMetadataService, "Subject metadata service has not been initialized");
    }

    /**
     * Returns the active group service instance.
     *
     * @return the service initialized during Fabric bootstrap
     */
    public static GroupService getGroupService() {
        return Objects.requireNonNull(groupService, "Group service has not been initialized");
    }

    /**
     * Returns the active merged known node registry.
     *
     * @return the registry initialized during Fabric bootstrap
     */
    public static PermissionNodeRegistry getPermissionNodeRegistry() {
        return Objects.requireNonNull(permissionNodeRegistry, "Permission node registry has not been initialized");
    }

    /**
     * Returns the active manual known node registry.
     *
     * @return the manual registry initialized during Fabric bootstrap
     */
    public static MutablePermissionNodeRegistry getManualPermissionNodeRegistry() {
        return Objects.requireNonNull(manualPermissionNodeRegistry, "Manual permission node registry has not been initialized");
    }

    /**
     * Returns the active effective permission resolver.
     *
     * @return the resolver initialized during Fabric bootstrap
     */
    public static PermissionResolver getPermissionResolver() {
        return Objects.requireNonNull(permissionResolver, "Permission resolver has not been initialized");
    }

    /**
     * Returns status diagnostics for the shared command tree.
     *
     * @return active command status diagnostics
     */
    public static CommandStatusDiagnostics getStatusDiagnostics() {
        String bridgeStatus = runtimeBridgeRegistered ? "Fabric permissions API bridge registered" : "Fabric permissions API bridge not registered";
        return new CommandStatusDiagnostics(formatPath(Objects.requireNonNull(permissionsFile, "Permissions file has not been initialized")),
                formatPath(Objects.requireNonNull(subjectsFile, "Subjects file has not been initialized")),
                formatPath(Objects.requireNonNull(groupsFile, "Groups file has not been initialized")),
                formatPath(Objects.requireNonNull(nodesFile, "Known nodes file has not been initialized")), bridgeStatus);
    }

    /**
     * Reloads persisted storage from disk for the shared reload command.
     */
    public static void reloadStorage() {
        PermissionService reloadedPermissionService = observablePermissionService(
                PermissionServices.jsonFile(Objects.requireNonNull(permissionsFile, "Permissions file has not been initialized")));
        SubjectMetadataService reloadedSubjectMetadataService = SubjectMetadataServices.jsonFile(Objects.requireNonNull(subjectsFile, "Subjects file has not been initialized"));
        GroupService reloadedGroupService = observableGroupService(GroupServices.jsonFile(Objects.requireNonNull(groupsFile, "Groups file has not been initialized")));
        MutablePermissionNodeRegistry reloadedManualPermissionNodeRegistry = PermissionNodeRegistries.observing(
                PermissionNodeRegistries.jsonFile(Objects.requireNonNull(nodesFile, "Known nodes file has not been initialized")), ClutchPermsFabricMod::refreshRuntimePermissions);
        PermissionNodeRegistry reloadedPermissionNodeRegistry = PermissionNodeRegistries.composite(PermissionNodeRegistries.builtIn(), reloadedManualPermissionNodeRegistry);
        permissionService = reloadedPermissionService;
        subjectMetadataService = reloadedSubjectMetadataService;
        groupService = reloadedGroupService;
        manualPermissionNodeRegistry = reloadedManualPermissionNodeRegistry;
        permissionNodeRegistry = reloadedPermissionNodeRegistry;
        permissionResolver = new PermissionResolver(permissionService, groupService);
    }

    /**
     * Validates persisted storage from disk without replacing active services or runtime state.
     */
    public static void validateStorage() {
        PermissionServices.jsonFile(Objects.requireNonNull(permissionsFile, "Permissions file has not been initialized"));
        SubjectMetadataServices.jsonFile(Objects.requireNonNull(subjectsFile, "Subjects file has not been initialized"));
        GroupServices.jsonFile(Objects.requireNonNull(groupsFile, "Groups file has not been initialized"));
        PermissionNodeRegistries.jsonFile(Objects.requireNonNull(nodesFile, "Known nodes file has not been initialized"));
    }

    /**
     * Returns the backup service used by shared backup commands.
     *
     * @return active storage backup service
     */
    public static StorageBackupService getStorageBackupService() {
        return StorageBackupService.forFiles(Objects.requireNonNull(permissionsFile, "Permissions file has not been initialized").getParent().resolve("backups"),
                Map.of(StorageFileKind.PERMISSIONS, permissionsFile, StorageFileKind.SUBJECTS, Objects.requireNonNull(subjectsFile, "Subjects file has not been initialized"),
                        StorageFileKind.GROUPS, Objects.requireNonNull(groupsFile, "Groups file has not been initialized"), StorageFileKind.NODES,
                        Objects.requireNonNull(nodesFile, "Known nodes file has not been initialized")));
    }

    /**
     * Refreshes Fabric runtime permission state after reload.
     */
    public static void refreshRuntimePermissions() {
        // The Fabric permissions API bridge queries the active supplier on every check, so there is no cached runtime state to refresh.
    }

    private static String formatPath(Path path) {
        return path.toAbsolutePath().normalize().toString();
    }

    private static PermissionService observablePermissionService(PermissionService storagePermissionService) {
        return PermissionServices.observing(storagePermissionService, ClutchPermsFabricMod::invalidateSubjectCache);
    }

    private static GroupService observableGroupService(GroupService storageGroupService) {
        return GroupServices.observing(storageGroupService, new GroupChangeListener() {

            @Override
            public void subjectGroupsChanged(UUID subjectId) {
                invalidateSubjectCache(subjectId);
            }

            @Override
            public void groupsChanged() {
                invalidateAllResolverCache();
            }
        });
    }

    private static void invalidateSubjectCache(UUID subjectId) {
        if (permissionResolver != null) {
            permissionResolver.invalidateSubject(subjectId);
        }
    }

    private static void invalidateAllResolverCache() {
        if (permissionResolver != null) {
            permissionResolver.invalidateAll();
        }
    }

    private static void recordSubject(ServerPlayer player) {
        getSubjectMetadataService().recordSubject(player.getUUID(), player.getGameProfile().name(), Instant.now());
    }
}
