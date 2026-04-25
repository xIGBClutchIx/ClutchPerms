package me.clutchy.clutchperms.forge;

import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import me.clutchy.clutchperms.common.command.CommandStatusDiagnostics;
import me.clutchy.clutchperms.common.group.GroupChangeListener;
import me.clutchy.clutchperms.common.group.GroupService;
import me.clutchy.clutchperms.common.group.GroupServices;
import me.clutchy.clutchperms.common.node.MutablePermissionNodeRegistry;
import me.clutchy.clutchperms.common.node.PermissionNodeRegistries;
import me.clutchy.clutchperms.common.node.PermissionNodeRegistry;
import me.clutchy.clutchperms.common.node.PermissionNodeSource;
import me.clutchy.clutchperms.common.permission.PermissionResolver;
import me.clutchy.clutchperms.common.permission.PermissionService;
import me.clutchy.clutchperms.common.permission.PermissionServices;
import me.clutchy.clutchperms.common.storage.PermissionStorageException;
import me.clutchy.clutchperms.common.storage.StorageBackupService;
import me.clutchy.clutchperms.common.storage.StorageFileKind;
import me.clutchy.clutchperms.common.storage.StorageFiles;
import me.clutchy.clutchperms.common.subject.SubjectMetadataService;
import me.clutchy.clutchperms.common.subject.SubjectMetadataServices;

import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
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
     * Active permission service instance for the current Forge server lifecycle.
     */
    private static PermissionService permissionService;

    /**
     * Active subject metadata service instance for the current Forge server lifecycle.
     */
    private static SubjectMetadataService subjectMetadataService;

    /**
     * Active group service instance for the current Forge server lifecycle.
     */
    private static GroupService groupService;

    /**
     * Active manual known node registry for the current Forge server lifecycle.
     */
    private static MutablePermissionNodeRegistry manualPermissionNodeRegistry;

    /**
     * Active merged known node registry for the current Forge server lifecycle.
     */
    private static PermissionNodeRegistry permissionNodeRegistry;

    /**
     * Active effective permission resolver for the current Forge server lifecycle.
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
     * Initializes the shared persisted service and hooks command registration into the Forge lifecycle.
     */
    public ClutchPermsForgeMod() {
        permissionsFile = FMLPaths.CONFIGDIR.get().resolve(MOD_ID).resolve("permissions.json");
        subjectsFile = FMLPaths.CONFIGDIR.get().resolve(MOD_ID).resolve("subjects.json");
        groupsFile = FMLPaths.CONFIGDIR.get().resolve(MOD_ID).resolve("groups.json");
        nodesFile = FMLPaths.CONFIGDIR.get().resolve(MOD_ID).resolve("nodes.json");
        try {
            reloadStorage();
        } catch (PermissionStorageException exception) {
            throw new IllegalStateException("Failed to load ClutchPerms storage", exception);
        }

        RegisterCommandsEvent.BUS.addListener(this::registerCommands);
        PermissionGatherEvent.Handler.BUS.addListener(this::registerPermissionHandler);
        PermissionGatherEvent.Nodes.BUS.addListener(this::registerPermissionNodes);
        ServerStartedEvent.BUS.addListener(this::onServerStarted);
        PlayerEvent.PlayerLoggedInEvent.BUS.addListener(this::onPlayerLoggedIn);
        ServerStoppedEvent.BUS.addListener(this::onServerStopped);
    }

    private void registerCommands(RegisterCommandsEvent event) {
        event.getDispatcher()
                .register(ForgeClutchPermsCommand.create(ClutchPermsForgeMod::getPermissionService, ClutchPermsForgeMod::getSubjectMetadataService,
                        ClutchPermsForgeMod::getGroupService, ClutchPermsForgeMod::getPermissionNodeRegistry, ClutchPermsForgeMod::getManualPermissionNodeRegistry,
                        ClutchPermsForgeMod::getPermissionResolver, ClutchPermsForgeMod::getStatusDiagnostics, ClutchPermsForgeMod::reloadStorage,
                        ClutchPermsForgeMod::validateStorage, ClutchPermsForgeMod::getStorageBackupService, ClutchPermsForgeMod::refreshRuntimePermissions));
    }

    private void registerPermissionHandler(PermissionGatherEvent.Handler event) {
        event.addPermissionHandler(ForgeClutchPermsPermissionHandler.IDENTIFIER,
                registeredNodes -> new ForgeClutchPermsPermissionHandler(ClutchPermsForgeMod::getPermissionResolver, registeredNodes));
    }

    private void registerPermissionNodes(PermissionGatherEvent.Nodes event) {
        event.addNodes(ForgeClutchPermsPermissionHandler.commandNodes());
    }

    private void onServerStarted(ServerStartedEvent event) {
        event.getServer().getPlayerList().getPlayers().forEach(this::recordSubject);

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

    private void onServerStopped(ServerStoppedEvent event) {
        permissionService = null;
        subjectMetadataService = null;
        groupService = null;
        manualPermissionNodeRegistry = null;
        permissionNodeRegistry = null;
        permissionResolver = null;
    }

    /**
     * Returns the active permission service instance.
     *
     * @return the service initialized during Forge bootstrap
     * @throws NullPointerException if the service is requested before initialization completes
     */
    public static PermissionService getPermissionService() {
        return Objects.requireNonNull(permissionService, "Permission service has not been initialized");
    }

    /**
     * Returns the active subject metadata service instance.
     *
     * @return the service initialized during Forge bootstrap
     * @throws NullPointerException if the service is requested before initialization completes
     */
    public static SubjectMetadataService getSubjectMetadataService() {
        return Objects.requireNonNull(subjectMetadataService, "Subject metadata service has not been initialized");
    }

    /**
     * Returns the active group service instance.
     *
     * @return the service initialized during Forge bootstrap
     */
    public static GroupService getGroupService() {
        return Objects.requireNonNull(groupService, "Group service has not been initialized");
    }

    /**
     * Returns the active merged known node registry.
     *
     * @return the registry initialized during Forge bootstrap
     */
    public static PermissionNodeRegistry getPermissionNodeRegistry() {
        return Objects.requireNonNull(permissionNodeRegistry, "Permission node registry has not been initialized");
    }

    /**
     * Returns the active manual known node registry.
     *
     * @return the manual registry initialized during Forge bootstrap
     */
    public static MutablePermissionNodeRegistry getManualPermissionNodeRegistry() {
        return Objects.requireNonNull(manualPermissionNodeRegistry, "Manual permission node registry has not been initialized");
    }

    /**
     * Returns the active effective permission resolver.
     *
     * @return the resolver initialized during Forge bootstrap
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
        return new CommandStatusDiagnostics(formatPath(Objects.requireNonNull(permissionsFile, "Permissions file has not been initialized")),
                formatPath(Objects.requireNonNull(subjectsFile, "Subjects file has not been initialized")),
                formatPath(Objects.requireNonNull(groupsFile, "Groups file has not been initialized")),
                formatPath(Objects.requireNonNull(nodesFile, "Known nodes file has not been initialized")), runtimeBridgeStatus());
    }

    /**
     * Reloads persisted storage from disk for the shared reload command.
     */
    public static void reloadStorage() {
        logStorageLoadStart();
        try {
            PermissionService reloadedPermissionService = observablePermissionService(
                    PermissionServices.jsonFile(Objects.requireNonNull(permissionsFile, "Permissions file has not been initialized")));
            SubjectMetadataService reloadedSubjectMetadataService = SubjectMetadataServices
                    .jsonFile(Objects.requireNonNull(subjectsFile, "Subjects file has not been initialized"));
            GroupService reloadedGroupService = observableGroupService(GroupServices.jsonFile(Objects.requireNonNull(groupsFile, "Groups file has not been initialized")));
            MutablePermissionNodeRegistry reloadedManualPermissionNodeRegistry = PermissionNodeRegistries.observing(
                    PermissionNodeRegistries.jsonFile(Objects.requireNonNull(nodesFile, "Known nodes file has not been initialized")),
                    ClutchPermsForgeMod::refreshRuntimePermissions);
            PermissionNodeRegistry reloadedPermissionNodeRegistry = PermissionNodeRegistries.composite(PermissionNodeRegistries.builtIn(), reloadedManualPermissionNodeRegistry,
                    PermissionNodeRegistries.supplying(PermissionNodeSource.PLATFORM, ClutchPermsForgeMod::registeredBooleanPermissionNodes));
            materializeStorageFiles();
            permissionService = reloadedPermissionService;
            subjectMetadataService = reloadedSubjectMetadataService;
            groupService = reloadedGroupService;
            manualPermissionNodeRegistry = reloadedManualPermissionNodeRegistry;
            permissionNodeRegistry = reloadedPermissionNodeRegistry;
            permissionResolver = new PermissionResolver(permissionService, groupService);
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
        return StorageBackupService.forFiles(Objects.requireNonNull(permissionsFile, "Permissions file has not been initialized").getParent().resolve("backups"), storageFiles());
    }

    /**
     * Refreshes Forge runtime permission state after reload.
     */
    public static void refreshRuntimePermissions() {
        // Forge asks the active permission handler on demand, and the handler reads the current storage supplier.
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

    private static String formatPath(Path path) {
        return path.toAbsolutePath().normalize().toString();
    }

    private static Path storageRoot() {
        return Objects.requireNonNull(permissionsFile, "Permissions file has not been initialized").getParent();
    }

    private static void materializeStorageFiles() {
        StorageFiles.materializeMissingJsonFiles(storageFiles());
    }

    private static Map<StorageFileKind, Path> storageFiles() {
        return Map.of(StorageFileKind.PERMISSIONS, Objects.requireNonNull(permissionsFile, "Permissions file has not been initialized"), StorageFileKind.SUBJECTS,
                Objects.requireNonNull(subjectsFile, "Subjects file has not been initialized"), StorageFileKind.GROUPS,
                Objects.requireNonNull(groupsFile, "Groups file has not been initialized"), StorageFileKind.NODES,
                Objects.requireNonNull(nodesFile, "Known nodes file has not been initialized"));
    }

    private static void logStorageLoadStart() {
        LOGGER.debug("ClutchPerms storage files: permissions={}, subjects={}, groups={}, nodes={}",
                formatPath(Objects.requireNonNull(permissionsFile, "Permissions file has not been initialized")),
                formatPath(Objects.requireNonNull(subjectsFile, "Subjects file has not been initialized")),
                formatPath(Objects.requireNonNull(groupsFile, "Groups file has not been initialized")),
                formatPath(Objects.requireNonNull(nodesFile, "Known nodes file has not been initialized")));
    }

    private static void logStorageLoadSuccess() {
        LOGGER.info("Loaded ClutchPerms storage from {}: {} known subjects, {} groups, {} manual known nodes, {} total known nodes.", storageRoot(),
                getSubjectMetadataService().getSubjects().size(), getGroupService().getGroups().size(), getManualPermissionNodeRegistry().getKnownNodes().size(),
                getPermissionNodeRegistry().getKnownNodes().size());
    }

    private static PermissionService observablePermissionService(PermissionService storagePermissionService) {
        return PermissionServices.observing(storagePermissionService, ClutchPermsForgeMod::invalidateSubjectCache);
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

    private void recordSubject(ServerPlayer player) {
        getSubjectMetadataService().recordSubject(player.getUUID(), player.getGameProfile().name(), Instant.now());
    }
}
