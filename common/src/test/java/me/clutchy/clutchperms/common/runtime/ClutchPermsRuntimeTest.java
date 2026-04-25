package me.clutchy.clutchperms.common.runtime;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import me.clutchy.clutchperms.common.config.ClutchPermsBackupConfig;
import me.clutchy.clutchperms.common.config.ClutchPermsChatConfig;
import me.clutchy.clutchperms.common.config.ClutchPermsCommandConfig;
import me.clutchy.clutchperms.common.config.ClutchPermsConfig;
import me.clutchy.clutchperms.common.config.ClutchPermsConfigs;
import me.clutchy.clutchperms.common.node.PermissionNodeRegistries;
import me.clutchy.clutchperms.common.node.PermissionNodeSource;
import me.clutchy.clutchperms.common.permission.PermissionServices;
import me.clutchy.clutchperms.common.permission.PermissionValue;
import me.clutchy.clutchperms.common.storage.PermissionStorageException;
import me.clutchy.clutchperms.common.storage.StorageBackup;
import me.clutchy.clutchperms.common.storage.StorageFileKind;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies the shared platform-neutral storage runtime lifecycle.
 */
class ClutchPermsRuntimeTest {

    private static final UUID SUBJECT_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @TempDir
    private Path temporaryDirectory;

    /**
     * Confirms reload loads storage, composes platform known nodes, and materializes missing files.
     *
     * @throws IOException if materialized storage cannot be inspected
     */
    @Test
    void reloadLoadsServicesAndMaterializesMissingFiles() throws IOException {
        ClutchPermsStoragePaths storagePaths = ClutchPermsStoragePaths.inDirectory(temporaryDirectory);
        ClutchPermsRuntime runtime = new ClutchPermsRuntime(storagePaths, () -> PermissionNodeRegistries.staticNodes(PermissionNodeSource.PLATFORM, List.of("platform.node")),
                ClutchPermsRuntimeHooks.noop());

        ClutchPermsRuntimeServices previousServices = runtime.reload();

        assertNull(previousServices);
        assertNotNull(runtime.permissionService());
        assertNotNull(runtime.subjectMetadataService());
        assertNotNull(runtime.groupService());
        assertNotNull(runtime.manualPermissionNodeRegistry());
        assertNotNull(runtime.permissionResolver());
        assertTrue(runtime.groupService().hasGroup("default"));
        assertTrue(runtime.permissionNodeRegistry().getKnownNode("platform.node").isPresent());
        assertTrue(Files.exists(storagePaths.permissionsFile()));
        assertTrue(Files.exists(storagePaths.subjectsFile()));
        assertTrue(Files.exists(storagePaths.groupsFile()));
        assertTrue(Files.readString(storagePaths.groupsFile()).contains("\"default\""));
        assertTrue(Files.exists(storagePaths.nodesFile()));
        assertTrue(Files.exists(storagePaths.configFile()));
        assertEquals(ClutchPermsConfig.defaults(), runtime.config());
        assertEquals(ClutchPermsConfig.defaults(), ClutchPermsConfigs.jsonFile(storagePaths.configFile()));
    }

    /**
     * Confirms validation parses disk state without replacing active services.
     */
    @Test
    void validateDoesNotReplaceActiveServices() throws IOException {
        ClutchPermsStoragePaths storagePaths = ClutchPermsStoragePaths.inDirectory(temporaryDirectory);
        ClutchPermsRuntime runtime = new ClutchPermsRuntime(storagePaths, ClutchPermsRuntimeHooks.noop());
        runtime.reload();
        ClutchPermsRuntimeServices activeServices = runtime.services();

        PermissionServices.jsonFile(storagePaths.permissionsFile()).setPermission(SUBJECT_ID, "example.validate", PermissionValue.TRUE);
        Files.writeString(storagePaths.configFile(), customConfig(3, 4, 5));

        runtime.validate();

        assertSame(activeServices, runtime.services());
        assertEquals(ClutchPermsConfig.defaults(), runtime.config());
        assertEquals(PermissionValue.UNSET, runtime.permissionService().getPermission(SUBJECT_ID, "example.validate"));
    }

    /**
     * Confirms failed reload attempts leave the previous runtime snapshot active.
     *
     * @throws IOException if the test file cannot be overwritten
     */
    @Test
    void failedReloadKeepsPreviousServices() throws IOException {
        ClutchPermsStoragePaths storagePaths = ClutchPermsStoragePaths.inDirectory(temporaryDirectory);
        ClutchPermsRuntime runtime = new ClutchPermsRuntime(storagePaths, ClutchPermsRuntimeHooks.noop());
        runtime.reload();
        ClutchPermsRuntimeServices activeServices = runtime.services();

        Files.writeString(storagePaths.permissionsFile(), "{not-json");

        assertThrows(PermissionStorageException.class, runtime::reload);
        assertSame(activeServices, runtime.services());
    }

    /**
     * Confirms a failed config reload leaves the previous runtime snapshot and config active.
     *
     * @throws IOException if the config file cannot be overwritten
     */
    @Test
    void failedConfigReloadKeepsPreviousServicesAndConfig() throws IOException {
        ClutchPermsStoragePaths storagePaths = ClutchPermsStoragePaths.inDirectory(temporaryDirectory);
        Files.writeString(storagePaths.configFile(), customConfig(3, 4, 5));
        ClutchPermsRuntime runtime = new ClutchPermsRuntime(storagePaths, ClutchPermsRuntimeHooks.noop());
        runtime.reload();
        ClutchPermsRuntimeServices activeServices = runtime.services();
        ClutchPermsConfig activeConfig = runtime.config();

        Files.writeString(storagePaths.configFile(), "{not-json");

        assertThrows(PermissionStorageException.class, runtime::reload);
        assertSame(activeServices, runtime.services());
        assertEquals(activeConfig, runtime.config());
    }

    /**
     * Confirms config updates write disk config and replace active runtime services.
     */
    @Test
    void updateConfigWritesConfigAndReloadsServices() {
        ClutchPermsStoragePaths storagePaths = ClutchPermsStoragePaths.inDirectory(temporaryDirectory);
        ClutchPermsRuntime runtime = new ClutchPermsRuntime(storagePaths, ClutchPermsRuntimeHooks.noop());
        runtime.reload();
        ClutchPermsRuntimeServices activeServices = runtime.services();

        ClutchPermsRuntimeServices previousServices = runtime
                .updateConfig(config -> new ClutchPermsConfig(new ClutchPermsBackupConfig(3), new ClutchPermsCommandConfig(4, 5), new ClutchPermsChatConfig(false)));

        assertSame(activeServices, previousServices);
        assertEquals(new ClutchPermsConfig(new ClutchPermsBackupConfig(3), new ClutchPermsCommandConfig(4, 5), new ClutchPermsChatConfig(false)), runtime.config());
        assertEquals(runtime.config(), ClutchPermsConfigs.jsonFile(storagePaths.configFile()));
    }

    /**
     * Confirms failed config updates restore disk config and leave active runtime services unchanged.
     *
     * @throws IOException if test file setup or inspection fails
     */
    @Test
    void failedUpdateConfigRestoresConfigAndKeepsPreviousServices() throws IOException {
        ClutchPermsStoragePaths storagePaths = ClutchPermsStoragePaths.inDirectory(temporaryDirectory);
        ClutchPermsRuntime runtime = new ClutchPermsRuntime(storagePaths, ClutchPermsRuntimeHooks.noop());
        runtime.reload();
        ClutchPermsRuntimeServices activeServices = runtime.services();
        String configBefore = Files.readString(storagePaths.configFile());

        Files.writeString(storagePaths.permissionsFile(), "{not-json");

        assertThrows(PermissionStorageException.class, () -> runtime.updateConfig(config -> new ClutchPermsConfig(new ClutchPermsBackupConfig(3), config.commands())));
        assertSame(activeServices, runtime.services());
        assertEquals(ClutchPermsConfig.defaults(), runtime.config());
        assertEquals(configBefore, Files.readString(storagePaths.configFile()));
        assertEquals(ClutchPermsConfig.defaults(), ClutchPermsConfigs.jsonFile(storagePaths.configFile()));
    }

    /**
     * Confirms reload picks up chat formatting changes from config.json.
     *
     * @throws IOException if the config file cannot be written
     */
    @Test
    void reloadUpdatesChatConfigFromDisk() throws IOException {
        ClutchPermsStoragePaths storagePaths = ClutchPermsStoragePaths.inDirectory(temporaryDirectory);
        ClutchPermsRuntime runtime = new ClutchPermsRuntime(storagePaths, ClutchPermsRuntimeHooks.noop());
        runtime.reload();

        Files.writeString(storagePaths.configFile(), customConfig(10, 7, 8, false));

        runtime.reload();

        assertEquals(false, runtime.config().chat().enabled());
    }

    /**
     * Confirms storage mutation observers invalidate resolver cache and invoke runtime refresh hooks.
     */
    @Test
    void mutationObserversInvalidateResolverCacheAndRefreshRuntimeHooks() {
        List<UUID> refreshedSubjects = new ArrayList<>();
        AtomicInteger fullRefreshes = new AtomicInteger();
        ClutchPermsRuntime runtime = new ClutchPermsRuntime(ClutchPermsStoragePaths.inDirectory(temporaryDirectory),
                new ClutchPermsRuntimeHooks(refreshedSubjects::add, fullRefreshes::incrementAndGet));
        runtime.reload();

        runtime.permissionResolver().resolve(SUBJECT_ID, "example.node");
        assertTrue(runtime.permissionResolver().cacheStats().subjects() > 0);

        runtime.permissionService().setPermission(SUBJECT_ID, "example.node", PermissionValue.TRUE);
        assertEquals(List.of(SUBJECT_ID), refreshedSubjects);
        assertEquals(0, runtime.permissionResolver().cacheStats().subjects());

        runtime.permissionResolver().resolve(SUBJECT_ID, "example.node");
        assertTrue(runtime.permissionResolver().cacheStats().subjects() > 0);
        runtime.groupService().createGroup("staff");
        assertEquals(0, runtime.permissionResolver().cacheStats().subjects());
        assertEquals(1, fullRefreshes.get());

        runtime.permissionResolver().resolve(SUBJECT_ID, "example.node");
        assertTrue(runtime.permissionResolver().cacheStats().subjects() > 0);
        runtime.manualPermissionNodeRegistry().addNode("example.known");
        assertEquals(0, runtime.permissionResolver().cacheStats().subjects());
        assertEquals(2, fullRefreshes.get());
    }

    /**
     * Confirms backup services are created from the shared storage path map and backup root.
     */
    @Test
    void backupServiceUsesStoragePathMapAndBackupRoot() {
        ClutchPermsStoragePaths storagePaths = ClutchPermsStoragePaths.inDirectory(temporaryDirectory);
        ClutchPermsRuntime runtime = new ClutchPermsRuntime(storagePaths, ClutchPermsRuntimeHooks.noop());
        runtime.reload();

        StorageBackup backup = runtime.storageBackupService().backupExistingFile(StorageFileKind.PERMISSIONS).orElseThrow();

        assertEquals(List.of(StorageFileKind.PERMISSIONS, StorageFileKind.SUBJECTS, StorageFileKind.GROUPS, StorageFileKind.NODES), runtime.storageBackupService().fileKinds());
        assertEquals(ClutchPermsConfig.defaults().backups().retentionLimit(), runtime.storageBackupService().retentionLimit());
        assertTrue(backup.path().startsWith(storagePaths.backupRoot().resolve(StorageFileKind.PERMISSIONS.token())));
    }

    /**
     * Confirms configured retention applies to ordinary JSON-backed service mutations.
     *
     * @throws IOException if the config file cannot be written
     */
    @Test
    void configuredBackupRetentionAppliesToJsonBackedMutations() throws IOException {
        ClutchPermsStoragePaths storagePaths = ClutchPermsStoragePaths.inDirectory(temporaryDirectory);
        Files.writeString(storagePaths.configFile(), customConfig(2, 7, 8));
        ClutchPermsRuntime runtime = new ClutchPermsRuntime(storagePaths, ClutchPermsRuntimeHooks.noop());
        runtime.reload();

        for (int index = 0; index < 4; index++) {
            runtime.permissionService().setPermission(SUBJECT_ID, "example.retention", index % 2 == 0 ? PermissionValue.TRUE : PermissionValue.FALSE);
            runtime.subjectMetadataService().recordSubject(SUBJECT_ID, "Subject" + index, java.time.Instant.parse("2026-04-24T12:00:0" + index + "Z"));
            runtime.groupService().createGroup("group" + index);
            runtime.manualPermissionNodeRegistry().addNode("example.retention" + index);
        }

        assertEquals(2, runtime.storageBackupService().retentionLimit());
        assertEquals(2, runtime.storageBackupService().listBackups(StorageFileKind.PERMISSIONS).size());
        assertEquals(2, runtime.storageBackupService().listBackups(StorageFileKind.SUBJECTS).size());
        assertEquals(2, runtime.storageBackupService().listBackups(StorageFileKind.GROUPS).size());
        assertEquals(2, runtime.storageBackupService().listBackups(StorageFileKind.NODES).size());
    }

    private static String customConfig(int retentionLimit, int helpPageSize, int resultPageSize) {
        return """
                {
                  "version": 1,
                  "backups": {
                    "retentionLimit": %s
                  },
                  "commands": {
                    "helpPageSize": %s,
                    "resultPageSize": %s
                  }
                }
                """.formatted(retentionLimit, helpPageSize, resultPageSize);
    }

    private static String customConfig(int retentionLimit, int helpPageSize, int resultPageSize, boolean chatEnabled) {
        return """
                {
                  "version": 1,
                  "backups": {
                    "retentionLimit": %s
                  },
                  "commands": {
                    "helpPageSize": %s,
                    "resultPageSize": %s
                  },
                  "chat": {
                    "enabled": %s
                  }
                }
                """.formatted(retentionLimit, helpPageSize, resultPageSize, chatEnabled);
    }
}
