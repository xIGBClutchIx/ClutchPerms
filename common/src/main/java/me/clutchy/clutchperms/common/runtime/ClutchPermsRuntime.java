package me.clutchy.clutchperms.common.runtime;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

import me.clutchy.clutchperms.common.command.CommandStatusDiagnostics;
import me.clutchy.clutchperms.common.config.ClutchPermsConfig;
import me.clutchy.clutchperms.common.config.ClutchPermsConfigs;
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
import me.clutchy.clutchperms.common.storage.StorageFiles;
import me.clutchy.clutchperms.common.storage.StorageWriteOptions;
import me.clutchy.clutchperms.common.subject.SubjectMetadataService;
import me.clutchy.clutchperms.common.subject.SubjectMetadataServices;

/**
 * Owns platform-neutral ClutchPerms storage loading, validation, backup wiring, active services, and resolver cache invalidation.
 */
public final class ClutchPermsRuntime {

    private final ClutchPermsStoragePaths storagePaths;

    private final Supplier<PermissionNodeRegistry> platformPermissionNodeRegistry;

    private final ClutchPermsRuntimeHooks runtimeHooks;

    private volatile ClutchPermsRuntimeServices services;

    /**
     * Creates a runtime without platform-provided known permission nodes.
     *
     * @param storagePaths JSON storage paths
     * @param runtimeHooks platform runtime refresh hooks
     */
    public ClutchPermsRuntime(ClutchPermsStoragePaths storagePaths, ClutchPermsRuntimeHooks runtimeHooks) {
        this(storagePaths, () -> null, runtimeHooks);
    }

    /**
     * Creates a runtime with optional platform-provided known permission nodes.
     *
     * @param storagePaths JSON storage paths
     * @param platformPermissionNodeRegistry supplier for the platform known-node registry, or {@code null} for no platform registry
     * @param runtimeHooks platform runtime refresh hooks
     */
    public ClutchPermsRuntime(ClutchPermsStoragePaths storagePaths, Supplier<PermissionNodeRegistry> platformPermissionNodeRegistry, ClutchPermsRuntimeHooks runtimeHooks) {
        this.storagePaths = Objects.requireNonNull(storagePaths, "storagePaths");
        this.platformPermissionNodeRegistry = Objects.requireNonNull(platformPermissionNodeRegistry, "platformPermissionNodeRegistry");
        this.runtimeHooks = Objects.requireNonNull(runtimeHooks, "runtimeHooks");
    }

    /**
     * Reloads all JSON storage and atomically replaces active services after every file parses and missing files are materialized.
     *
     * @return previously active services, or {@code null} when this is the first successful load
     */
    public synchronized ClutchPermsRuntimeServices reload() {
        ClutchPermsRuntimeServices reloadedServices = loadServices();
        StorageFiles.materializeMissingJsonFiles(storagePaths.storageFiles());
        ClutchPermsConfigs.materializeDefault(storagePaths.configFile());

        ClutchPermsRuntimeServices previousServices = services;
        services = reloadedServices;
        return previousServices;
    }

    /**
     * Writes an updated config, reloads all runtime services, and restores the previous config file if reload fails.
     *
     * @param updater config updater
     * @return previously active services, or {@code null} when the updated config is unchanged
     */
    public synchronized ClutchPermsRuntimeServices updateConfig(UnaryOperator<ClutchPermsConfig> updater) {
        ClutchPermsConfig currentConfig = config();
        ClutchPermsConfig updatedConfig = Objects.requireNonNull(Objects.requireNonNull(updater, "updater").apply(currentConfig), "updatedConfig");
        if (updatedConfig.equals(currentConfig)) {
            return null;
        }

        Path configFile = storagePaths.configFile();
        Path parentDirectory = configFile.getParent();
        Path rollbackFile = null;
        boolean configExisted = Files.exists(configFile);
        try {
            if (parentDirectory != null) {
                Files.createDirectories(parentDirectory);
            }
            if (configExisted) {
                rollbackFile = parentDirectory == null
                        ? Files.createTempFile(configFile.getFileName().toString(), ".rollback")
                        : Files.createTempFile(parentDirectory, configFile.getFileName().toString(), ".rollback");
                Files.copy(configFile, rollbackFile, StandardCopyOption.REPLACE_EXISTING);
            }

            ClutchPermsConfigs.write(configFile, updatedConfig);
            try {
                return reload();
            } catch (RuntimeException exception) {
                restoreConfigFile(configFile, rollbackFile, configExisted, exception);
                throw new PermissionStorageException("Failed to apply updated config; restored previous config file", exception);
            }
        } catch (IOException exception) {
            throw new PermissionStorageException("Failed to update config at " + configFile, exception);
        } finally {
            if (rollbackFile != null) {
                try {
                    Files.deleteIfExists(rollbackFile);
                } catch (IOException ignored) {
                    // Best-effort cleanup; update failures are reported above.
                }
            }
        }
    }

    /**
     * Validates all JSON storage without replacing active services or materializing missing files.
     */
    public void validate() {
        ClutchPermsConfig loadedConfig = ClutchPermsConfigs.jsonFile(storagePaths.configFile());
        StorageWriteOptions writeOptions = loadedConfig.storageWriteOptions();
        PermissionServices.jsonFile(storagePaths.permissionsFile(), writeOptions);
        SubjectMetadataServices.jsonFile(storagePaths.subjectsFile(), writeOptions);
        GroupServices.jsonFile(storagePaths.groupsFile(), writeOptions);
        PermissionNodeRegistries.jsonFile(storagePaths.nodesFile(), writeOptions);
    }

    /**
     * Clears active runtime services.
     */
    public synchronized void clear() {
        services = null;
    }

    /**
     * Returns active service snapshot.
     *
     * @return active services
     */
    public synchronized ClutchPermsRuntimeServices services() {
        return activeServices();
    }

    /**
     * Returns active direct permission service.
     *
     * @return permission service
     */
    public PermissionService permissionService() {
        return activeServices().permissionService();
    }

    /**
     * Returns active subject metadata service.
     *
     * @return subject metadata service
     */
    public SubjectMetadataService subjectMetadataService() {
        return activeServices().subjectMetadataService();
    }

    /**
     * Returns active group service.
     *
     * @return group service
     */
    public GroupService groupService() {
        return activeServices().groupService();
    }

    /**
     * Returns active manual known-node registry.
     *
     * @return manual known-node registry
     */
    public MutablePermissionNodeRegistry manualPermissionNodeRegistry() {
        return activeServices().manualPermissionNodeRegistry();
    }

    /**
     * Returns active merged known-node registry.
     *
     * @return known-node registry
     */
    public PermissionNodeRegistry permissionNodeRegistry() {
        return activeServices().permissionNodeRegistry();
    }

    /**
     * Returns active effective permission resolver.
     *
     * @return permission resolver
     */
    public PermissionResolver permissionResolver() {
        return activeServices().permissionResolver();
    }

    /**
     * Returns active runtime config.
     *
     * @return active runtime config
     */
    public ClutchPermsConfig config() {
        return activeServices().config();
    }

    /**
     * Creates the backup service for the active storage path set.
     *
     * @return storage backup service
     */
    public StorageBackupService storageBackupService() {
        return StorageBackupService.forFiles(storagePaths.backupRoot(), storagePaths.storageFiles(), config().backups().retentionLimit());
    }

    /**
     * Creates status diagnostics using platform-provided runtime bridge status text.
     *
     * @param runtimeBridgeStatus platform runtime bridge status
     * @return command status diagnostics
     */
    public CommandStatusDiagnostics statusDiagnostics(String runtimeBridgeStatus) {
        return new CommandStatusDiagnostics(formatPath(storagePaths.permissionsFile()), formatPath(storagePaths.subjectsFile()), formatPath(storagePaths.groupsFile()),
                formatPath(storagePaths.nodesFile()), runtimeBridgeStatus, formatPath(storagePaths.configFile()));
    }

    /**
     * Returns storage paths owned by this runtime.
     *
     * @return storage paths
     */
    public ClutchPermsStoragePaths storagePaths() {
        return storagePaths;
    }

    private ClutchPermsRuntimeServices loadServices() {
        ClutchPermsConfig loadedConfig = ClutchPermsConfigs.jsonFile(storagePaths.configFile());
        StorageWriteOptions writeOptions = loadedConfig.storageWriteOptions();
        PermissionService loadedPermissionService = PermissionServices.jsonFile(storagePaths.permissionsFile(), writeOptions);
        SubjectMetadataService loadedSubjectMetadataService = SubjectMetadataServices.jsonFile(storagePaths.subjectsFile(), writeOptions);
        GroupService loadedGroupService = GroupServices.jsonFile(storagePaths.groupsFile(), writeOptions);
        MutablePermissionNodeRegistry loadedManualPermissionNodeRegistry = PermissionNodeRegistries.jsonFile(storagePaths.nodesFile(), writeOptions);

        PermissionService observedPermissionService = PermissionServices.observing(loadedPermissionService, subjectId -> {
            invalidateSubjectCache(subjectId);
            runtimeHooks.refreshSubject(subjectId);
        });
        GroupService observedGroupService = GroupServices.observing(loadedGroupService, new GroupChangeListener() {

            @Override
            public void subjectGroupsChanged(UUID subjectId) {
                invalidateSubjectCache(subjectId);
                runtimeHooks.refreshSubject(subjectId);
            }

            @Override
            public void groupsChanged() {
                invalidateAllResolverCache();
                runtimeHooks.refreshAll();
            }
        });
        MutablePermissionNodeRegistry observedManualPermissionNodeRegistry = PermissionNodeRegistries.observing(loadedManualPermissionNodeRegistry, () -> {
            invalidateAllResolverCache();
            runtimeHooks.refreshAll();
        });
        PermissionNodeRegistry mergedPermissionNodeRegistry = createPermissionNodeRegistry(observedManualPermissionNodeRegistry);
        PermissionResolver loadedPermissionResolver = new PermissionResolver(observedPermissionService, observedGroupService);
        return new ClutchPermsRuntimeServices(observedPermissionService, loadedSubjectMetadataService, observedGroupService, observedManualPermissionNodeRegistry,
                mergedPermissionNodeRegistry, loadedPermissionResolver, loadedConfig);
    }

    private PermissionNodeRegistry createPermissionNodeRegistry(MutablePermissionNodeRegistry manualPermissionNodeRegistry) {
        PermissionNodeRegistry platformRegistry = platformPermissionNodeRegistry.get();
        if (platformRegistry == null) {
            return PermissionNodeRegistries.composite(PermissionNodeRegistries.builtIn(), manualPermissionNodeRegistry);
        }
        return PermissionNodeRegistries.composite(PermissionNodeRegistries.builtIn(), manualPermissionNodeRegistry, platformRegistry);
    }

    private static void restoreConfigFile(Path configFile, Path rollbackFile, boolean configExisted, RuntimeException reloadFailure) {
        try {
            if (configExisted) {
                if (rollbackFile == null) {
                    throw new IOException("rollback config file was not created");
                }
                StorageFiles.moveAtomically(rollbackFile, configFile);
            } else {
                Files.deleteIfExists(configFile);
            }
        } catch (IOException rollbackFailure) {
            PermissionStorageException exception = new PermissionStorageException("Failed to apply updated config and failed to restore previous config file", reloadFailure);
            exception.addSuppressed(rollbackFailure);
            throw exception;
        }
    }

    private synchronized ClutchPermsRuntimeServices activeServices() {
        return Objects.requireNonNull(services, "ClutchPerms runtime services have not been initialized");
    }

    private void invalidateSubjectCache(UUID subjectId) {
        ClutchPermsRuntimeServices active = services;
        if (active != null) {
            active.permissionResolver().invalidateSubject(subjectId);
        }
    }

    private void invalidateAllResolverCache() {
        ClutchPermsRuntimeServices active = services;
        if (active != null) {
            active.permissionResolver().invalidateAll();
        }
    }

    private static String formatPath(Path path) {
        return path.toAbsolutePath().normalize().toString();
    }
}
