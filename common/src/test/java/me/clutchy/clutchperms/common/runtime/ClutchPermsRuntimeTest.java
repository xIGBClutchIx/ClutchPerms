package me.clutchy.clutchperms.common.runtime;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import me.clutchy.clutchperms.common.audit.AuditLogRecord;
import me.clutchy.clutchperms.common.audit.AuditLogServices;
import me.clutchy.clutchperms.common.command.CommandSourceKind;
import me.clutchy.clutchperms.common.config.ClutchPermsBackupConfig;
import me.clutchy.clutchperms.common.config.ClutchPermsChatConfig;
import me.clutchy.clutchperms.common.config.ClutchPermsCommandConfig;
import me.clutchy.clutchperms.common.config.ClutchPermsConfig;
import me.clutchy.clutchperms.common.config.ClutchPermsConfigs;
import me.clutchy.clutchperms.common.group.GroupServices;
import me.clutchy.clutchperms.common.node.PermissionNodeRegistries;
import me.clutchy.clutchperms.common.node.PermissionNodeSource;
import me.clutchy.clutchperms.common.permission.PermissionServices;
import me.clutchy.clutchperms.common.permission.PermissionValue;
import me.clutchy.clutchperms.common.storage.PermissionStorageException;
import me.clutchy.clutchperms.common.storage.SqliteDependencyMode;
import me.clutchy.clutchperms.common.storage.SqliteStore;
import me.clutchy.clutchperms.common.storage.SqliteTestSupport;
import me.clutchy.clutchperms.common.storage.StorageBackup;
import me.clutchy.clutchperms.common.storage.StorageFileKind;
import me.clutchy.clutchperms.common.track.TrackServices;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies the shared platform-neutral SQLite storage runtime lifecycle.
 */
class ClutchPermsRuntimeTest {

    private static final UUID SUBJECT_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @TempDir
    private Path temporaryDirectory;

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
        assertNotNull(runtime.trackService());
        assertNotNull(runtime.manualPermissionNodeRegistry());
        assertNotNull(runtime.permissionResolver());
        assertTrue(runtime.groupService().hasGroup("default"));
        assertTrue(runtime.permissionNodeRegistry().getKnownNode("platform.node").isPresent());
        assertTrue(Files.exists(storagePaths.databaseFile()));
        assertTrue(Files.exists(storagePaths.configFile()));
        assertEquals(ClutchPermsConfig.defaults(), runtime.config());
        assertEquals(ClutchPermsConfig.defaults(), ClutchPermsConfigs.jsonFile(storagePaths.configFile()));
    }

    @Test
    void validateDoesNotReplaceActiveServices() throws IOException {
        ClutchPermsStoragePaths storagePaths = ClutchPermsStoragePaths.inDirectory(temporaryDirectory);
        ClutchPermsRuntime runtime = new ClutchPermsRuntime(storagePaths, ClutchPermsRuntimeHooks.noop());
        runtime.reload();
        ClutchPermsRuntimeServices activeServices = runtime.services();

        try (SqliteStore externalStore = SqliteTestSupport.open(storagePaths.databaseFile())) {
            PermissionServices.sqlite(externalStore).setPermission(SUBJECT_ID, "example.validate", PermissionValue.TRUE);
            GroupServices.sqlite(externalStore).createGroup("staff");
            TrackServices.sqlite(externalStore, GroupServices.sqlite(externalStore)).createTrack("ranks");
        }
        Files.writeString(storagePaths.configFile(), customConfig(3, 4, 5));

        runtime.validate();

        assertSame(activeServices, runtime.services());
        assertEquals(ClutchPermsConfig.defaults(), runtime.config());
        assertEquals(PermissionValue.UNSET, runtime.permissionService().getPermission(SUBJECT_ID, "example.validate"));
        assertFalse(runtime.trackService().hasTrack("ranks"));
    }

    @Test
    void reloadLoadsPersistedTracks() {
        ClutchPermsStoragePaths storagePaths = ClutchPermsStoragePaths.inDirectory(temporaryDirectory);
        try (SqliteStore store = SqliteTestSupport.open(storagePaths.databaseFile())) {
            GroupServices.sqlite(store).createGroup("staff");
            TrackServices.sqlite(store, GroupServices.sqlite(store)).createTrack("ranks");
            TrackServices.sqlite(store, GroupServices.sqlite(store)).setTrackGroups("ranks", List.of("default", "staff"));
        }
        ClutchPermsRuntime runtime = new ClutchPermsRuntime(storagePaths, ClutchPermsRuntimeHooks.noop());

        runtime.reload();

        assertEquals(List.of("default", "staff"), runtime.trackService().getTrackGroups("ranks"));
    }

    @Test
    void failedReloadKeepsPreviousServices() {
        ClutchPermsStoragePaths storagePaths = ClutchPermsStoragePaths.inDirectory(temporaryDirectory);
        ClutchPermsRuntime runtime = new ClutchPermsRuntime(storagePaths, ClutchPermsRuntimeHooks.noop());
        runtime.reload();
        ClutchPermsRuntimeServices activeServices = runtime.services();

        insertInvalidPermissionRow(runtime.services().sqliteStore());

        assertThrows(PermissionStorageException.class, runtime::reload);
        assertSame(activeServices, runtime.services());
    }

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

    @Test
    void failedUpdateConfigRestoresConfigAndKeepsPreviousServices() throws IOException {
        ClutchPermsStoragePaths storagePaths = ClutchPermsStoragePaths.inDirectory(temporaryDirectory);
        ClutchPermsRuntime runtime = new ClutchPermsRuntime(storagePaths, ClutchPermsRuntimeHooks.noop());
        runtime.reload();
        ClutchPermsRuntimeServices activeServices = runtime.services();
        String configBefore = Files.readString(storagePaths.configFile());
        insertInvalidPermissionRow(runtime.services().sqliteStore());

        assertThrows(PermissionStorageException.class, () -> runtime.updateConfig(config -> new ClutchPermsConfig(new ClutchPermsBackupConfig(3), config.commands())));
        assertSame(activeServices, runtime.services());
        assertEquals(ClutchPermsConfig.defaults(), runtime.config());
        assertEquals(configBefore, Files.readString(storagePaths.configFile()));
        assertEquals(ClutchPermsConfig.defaults(), ClutchPermsConfigs.jsonFile(storagePaths.configFile()));
    }

    @Test
    void reloadUpdatesChatConfigFromDisk() throws IOException {
        ClutchPermsStoragePaths storagePaths = ClutchPermsStoragePaths.inDirectory(temporaryDirectory);
        ClutchPermsRuntime runtime = new ClutchPermsRuntime(storagePaths, ClutchPermsRuntimeHooks.noop());
        runtime.reload();

        Files.writeString(storagePaths.configFile(), customConfig(10, 7, 8, false));

        runtime.reload();

        assertEquals(false, runtime.config().chat().enabled());
    }

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

    @Test
    void backupServiceUsesDatabasePathAndBackupRoot() {
        ClutchPermsStoragePaths storagePaths = ClutchPermsStoragePaths.inDirectory(temporaryDirectory);
        ClutchPermsRuntime runtime = new ClutchPermsRuntime(storagePaths, ClutchPermsRuntimeHooks.noop());
        runtime.reload();

        StorageBackup backup = runtime.storageBackupService().createBackup().orElseThrow();

        assertEquals(List.of(StorageFileKind.DATABASE), runtime.storageBackupService().fileKinds());
        assertEquals(ClutchPermsConfig.defaults().backups().retentionLimit(), runtime.storageBackupService().retentionLimit());
        assertTrue(backup.path().startsWith(storagePaths.backupRoot().resolve(StorageFileKind.DATABASE.token())));
    }

    @Test
    void configuredBackupRetentionAppliesToManualDatabaseSnapshots() throws IOException {
        ClutchPermsStoragePaths storagePaths = ClutchPermsStoragePaths.inDirectory(temporaryDirectory);
        Files.writeString(storagePaths.configFile(), customConfig(2, 7, 8));
        ClutchPermsRuntime runtime = new ClutchPermsRuntime(storagePaths, ClutchPermsRuntimeHooks.noop());
        runtime.reload();

        for (int index = 0; index < 4; index++) {
            runtime.permissionService().setPermission(SUBJECT_ID, "example.retention", index % 2 == 0 ? PermissionValue.TRUE : PermissionValue.FALSE);
            runtime.storageBackupService().createBackup();
        }

        assertEquals(2, runtime.storageBackupService().retentionLimit());
        assertEquals(2, runtime.storageBackupService().listBackups(StorageFileKind.DATABASE).size());
    }

    @Test
    void reloadAppliesAutomaticAuditAgeRetention() {
        ClutchPermsStoragePaths storagePaths = ClutchPermsStoragePaths.inDirectory(temporaryDirectory);
        try (SqliteStore store = SqliteStore.open(storagePaths.databaseFile(), SqliteDependencyMode.ANY_VISIBLE)) {
            AuditLogServices.sqlite(store).append(auditRecord(Instant.parse("2020-01-01T00:00:00Z"), "old"));
            AuditLogServices.sqlite(store).append(auditRecord(Instant.now(), "new"));
        }
        ClutchPermsRuntime runtime = new ClutchPermsRuntime(storagePaths, ClutchPermsRuntimeHooks.noop());

        runtime.reload();

        assertEquals(List.of("new"), runtime.auditLogService().listNewestFirst().stream().map(entry -> entry.action()).toList());
    }

    @Test
    void disabledAutomaticAuditRetentionDoesNotPrune() throws IOException {
        ClutchPermsStoragePaths storagePaths = ClutchPermsStoragePaths.inDirectory(temporaryDirectory);
        Files.writeString(storagePaths.configFile(), auditRetentionConfig(false, 90, 0));
        try (SqliteStore store = SqliteStore.open(storagePaths.databaseFile(), SqliteDependencyMode.ANY_VISIBLE)) {
            AuditLogServices.sqlite(store).append(auditRecord(Instant.parse("2020-01-01T00:00:00Z"), "old"));
        }
        ClutchPermsRuntime runtime = new ClutchPermsRuntime(storagePaths, ClutchPermsRuntimeHooks.noop());

        runtime.reload();

        assertEquals(List.of("old"), runtime.auditLogService().listNewestFirst().stream().map(entry -> entry.action()).toList());
    }

    @Test
    void automaticAuditCountRetentionCanBeDisabled() throws IOException {
        ClutchPermsStoragePaths storagePaths = ClutchPermsStoragePaths.inDirectory(temporaryDirectory);
        Files.writeString(storagePaths.configFile(), auditRetentionConfig(true, 3650, 0));
        try (SqliteStore store = SqliteStore.open(storagePaths.databaseFile(), SqliteDependencyMode.ANY_VISIBLE)) {
            AuditLogServices.sqlite(store).append(auditRecord(Instant.now(), "first"));
            AuditLogServices.sqlite(store).append(auditRecord(Instant.now(), "second"));
        }
        ClutchPermsRuntime runtime = new ClutchPermsRuntime(storagePaths, ClutchPermsRuntimeHooks.noop());

        runtime.reload();

        assertEquals(List.of("second", "first"), runtime.auditLogService().listNewestFirst().stream().map(entry -> entry.action()).toList());
    }

    private static void insertInvalidPermissionRow(SqliteStore store) {
        store.write(connection -> {
            try (Statement statement = connection.createStatement()) {
                statement.executeUpdate("INSERT INTO subject_permissions (subject_id, node, value) VALUES ('00000000-0000-0000-0000-000000000001', 'example*', 'TRUE')");
            }
        });
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

    private static String auditRetentionConfig(boolean enabled, int maxAgeDays, int maxEntries) {
        return """
                {
                  "version": 1,
                  "backups": {
                    "retentionLimit": 10
                  },
                  "audit": {
                    "retention": {
                      "enabled": %s,
                      "maxAgeDays": %s,
                      "maxEntries": %s
                    }
                  },
                  "commands": {
                    "helpPageSize": 7,
                    "resultPageSize": 8
                  }
                }
                """.formatted(enabled, maxAgeDays, maxEntries);
    }

    private static AuditLogRecord auditRecord(Instant timestamp, String action) {
        return new AuditLogRecord(timestamp, CommandSourceKind.CONSOLE, Optional.empty(), Optional.of("console"), action, "config", "test", "test", "{}", "{}",
                "/clutchperms config set chat.enabled off", true);
    }
}
