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
import me.clutchy.clutchperms.common.display.DisplayResolver;
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
import me.clutchy.clutchperms.common.storage.SqliteDependencyMode;
import me.clutchy.clutchperms.common.storage.SqliteStore;
import me.clutchy.clutchperms.common.storage.StorageBackupService;
import me.clutchy.clutchperms.common.storage.StorageFileKind;
import me.clutchy.clutchperms.common.storage.StorageFiles;
import me.clutchy.clutchperms.common.subject.SubjectMetadataService;
import me.clutchy.clutchperms.common.subject.SubjectMetadataServices;

/**
 * Owns platform-neutral ClutchPerms SQLite storage loading, validation, backup wiring, active services, and resolver cache invalidation.
 */
public final class ClutchPermsRuntime {

    private final ClutchPermsStoragePaths storagePaths;

    private final Supplier<PermissionNodeRegistry> platformPermissionNodeRegistry;

    private final ClutchPermsRuntimeHooks runtimeHooks;

    private final SqliteDependencyMode dependencyMode;

    private volatile ClutchPermsRuntimeServices services;

    /**
     * Creates a runtime without platform-provided known permission nodes.
     *
     * @param storagePaths storage paths
     * @param runtimeHooks platform runtime refresh hooks
     */
    public ClutchPermsRuntime(ClutchPermsStoragePaths storagePaths, ClutchPermsRuntimeHooks runtimeHooks) {
        this(storagePaths, () -> null, runtimeHooks, SqliteDependencyMode.ANY_VISIBLE);
    }

    /**
     * Creates a runtime without platform-provided known permission nodes.
     *
     * @param storagePaths storage paths
     * @param runtimeHooks platform runtime refresh hooks
     * @param dependencyMode expected SQLite dependency provisioning mode
     */
    public ClutchPermsRuntime(ClutchPermsStoragePaths storagePaths, ClutchPermsRuntimeHooks runtimeHooks, SqliteDependencyMode dependencyMode) {
        this(storagePaths, () -> null, runtimeHooks, dependencyMode);
    }

    /**
     * Creates a runtime with optional platform-provided known permission nodes.
     *
     * @param storagePaths storage paths
     * @param platformPermissionNodeRegistry supplier for the platform known-node registry, or {@code null} for no platform registry
     * @param runtimeHooks platform runtime refresh hooks
     */
    public ClutchPermsRuntime(ClutchPermsStoragePaths storagePaths, Supplier<PermissionNodeRegistry> platformPermissionNodeRegistry, ClutchPermsRuntimeHooks runtimeHooks) {
        this(storagePaths, platformPermissionNodeRegistry, runtimeHooks, SqliteDependencyMode.ANY_VISIBLE);
    }

    /**
     * Creates a runtime with optional platform-provided known permission nodes.
     *
     * @param storagePaths storage paths
     * @param platformPermissionNodeRegistry supplier for the platform known-node registry, or {@code null} for no platform registry
     * @param runtimeHooks platform runtime refresh hooks
     * @param dependencyMode expected SQLite dependency provisioning mode
     */
    public ClutchPermsRuntime(ClutchPermsStoragePaths storagePaths, Supplier<PermissionNodeRegistry> platformPermissionNodeRegistry, ClutchPermsRuntimeHooks runtimeHooks,
            SqliteDependencyMode dependencyMode) {
        this.storagePaths = Objects.requireNonNull(storagePaths, "storagePaths");
        this.platformPermissionNodeRegistry = Objects.requireNonNull(platformPermissionNodeRegistry, "platformPermissionNodeRegistry");
        this.runtimeHooks = Objects.requireNonNull(runtimeHooks, "runtimeHooks");
        this.dependencyMode = Objects.requireNonNull(dependencyMode, "dependencyMode");
    }

    /**
     * Reloads SQLite storage and atomically replaces active services after the database and config parse successfully.
     *
     * @return previously active services, or {@code null} when this is the first successful load
     */
    public synchronized ClutchPermsRuntimeServices reload() {
        ClutchPermsRuntimeServices reloadedServices = loadServices();
        try {
            ClutchPermsConfigs.materializeDefault(storagePaths.configFile());
            ClutchPermsRuntimeServices previousServices = services;
            services = reloadedServices;
            closeQuietly(previousServices);
            return previousServices;
        } catch (RuntimeException exception) {
            closeQuietly(reloadedServices);
            throw exception;
        }
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
     * Validates config and SQLite storage without replacing active services or materializing missing files.
     */
    public void validate() {
        ClutchPermsConfigs.jsonFile(storagePaths.configFile());
        if (Files.notExists(storagePaths.databaseFile())) {
            return;
        }
        try (SqliteStore store = SqliteStore.openExisting(storagePaths.databaseFile(), dependencyMode)) {
            PermissionServices.sqlite(store);
            SubjectMetadataServices.sqlite(store);
            GroupServices.sqlite(store);
            PermissionNodeRegistries.sqlite(store);
        }
    }

    /**
     * Clears active runtime services.
     */
    public synchronized void clear() {
        closeQuietly(services);
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
     * Creates an effective display resolver for the active subject and group services.
     *
     * @return display resolver
     */
    public DisplayResolver displayResolver() {
        return new DisplayResolver(subjectMetadataService(), groupService());
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
        return StorageBackupService.forDatabase(storagePaths.backupRoot(), storagePaths.databaseFile(), activeServices().sqliteStore(), config().backups().retentionLimit());
    }

    /**
     * Restores a database backup after closing the active SQLite pool, then reloads services from the restored database.
     *
     * @param kind storage kind; must be {@link StorageFileKind#DATABASE}
     * @param backupFileName backup filename
     * @return previously active services
     */
    public synchronized ClutchPermsRuntimeServices restoreBackup(StorageFileKind kind, String backupFileName) {
        if (Objects.requireNonNull(kind, "kind") != StorageFileKind.DATABASE) {
            throw new IllegalArgumentException("unsupported backup kind: " + kind.token());
        }
        ClutchPermsRuntimeServices previousServices = activeServices();
        StorageBackupService backupService = StorageBackupService.forDatabase(storagePaths.backupRoot(), storagePaths.databaseFile(), previousServices.sqliteStore(),
                config().backups().retentionLimit());
        boolean[] closedActiveStore = {false};
        try {
            backupService.restoreBackup(StorageFileKind.DATABASE, backupFileName, () -> {
                closedActiveStore[0] = true;
                previousServices.close();
            }, this::reload);
            return previousServices;
        } catch (RuntimeException exception) {
            if (closedActiveStore[0]) {
                try {
                    reload();
                } catch (RuntimeException rollbackReloadFailure) {
                    exception.addSuppressed(rollbackReloadFailure);
                }
            }
            throw exception;
        }
    }

    /**
     * Creates status diagnostics using platform-provided runtime bridge status text.
     *
     * @param runtimeBridgeStatus platform runtime bridge status
     * @return command status diagnostics
     */
    public CommandStatusDiagnostics statusDiagnostics(String runtimeBridgeStatus) {
        return new CommandStatusDiagnostics(formatPath(storagePaths.databaseFile()), runtimeBridgeStatus, formatPath(storagePaths.configFile()));
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
        SqliteStore sqliteStore = SqliteStore.open(storagePaths.databaseFile(), dependencyMode);
        PermissionService loadedPermissionService;
        SubjectMetadataService loadedSubjectMetadataService;
        GroupService loadedGroupService;
        MutablePermissionNodeRegistry loadedManualPermissionNodeRegistry;
        try {
            loadedPermissionService = PermissionServices.sqlite(sqliteStore);
            loadedSubjectMetadataService = SubjectMetadataServices.sqlite(sqliteStore);
            loadedGroupService = GroupServices.sqlite(sqliteStore);
            loadedManualPermissionNodeRegistry = PermissionNodeRegistries.sqlite(sqliteStore);
        } catch (RuntimeException exception) {
            sqliteStore.close();
            throw exception;
        }

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
                mergedPermissionNodeRegistry, loadedPermissionResolver, loadedConfig, sqliteStore);
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

    private static void closeQuietly(ClutchPermsRuntimeServices services) {
        if (services == null) {
            return;
        }
        services.close();
    }
}
