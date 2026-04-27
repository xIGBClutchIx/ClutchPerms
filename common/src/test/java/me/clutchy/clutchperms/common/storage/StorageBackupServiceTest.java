package me.clutchy.clutchperms.common.storage;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import me.clutchy.clutchperms.common.permission.PermissionService;
import me.clutchy.clutchperms.common.permission.PermissionServices;
import me.clutchy.clutchperms.common.permission.PermissionValue;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies SQLite database backup listing, creation, retention, and restore rollback behavior.
 */
class StorageBackupServiceTest {

    private static final UUID SUBJECT_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    private final Clock clock = Clock.fixed(Instant.parse("2026-04-24T12:34:56.789Z"), ZoneOffset.UTC);

    @Test
    void listBackupsReturnsEmptyForMissingBackupDirectory(@TempDir Path temporaryDirectory) {
        try (SqliteStore store = SqliteTestSupport.openDirectory(temporaryDirectory)) {
            StorageBackupService backupService = backupService(temporaryDirectory.resolve("backups"), store.databaseFile(), store, 10);

            assertEquals(Map.of(StorageFileKind.DATABASE, List.of()), backupService.listBackups());
            assertEquals(List.of(), backupService.listBackups(StorageFileKind.DATABASE));
        }
    }

    @Test
    void createBackupUsesDatabaseSnapshotAndListsNewestFirst(@TempDir Path temporaryDirectory) {
        Path backupRoot = temporaryDirectory.resolve("backups");
        Path databaseFile = SqliteTestSupport.databaseFile(temporaryDirectory);
        try (SqliteStore store = SqliteTestSupport.open(databaseFile)) {
            PermissionServices.sqlite(store).setPermission(SUBJECT_ID, "example.snapshot", PermissionValue.TRUE);
            StorageBackupService backupService = backupService(backupRoot, databaseFile, store, 10);

            StorageBackup firstBackup = backupService.createBackup().orElseThrow();
            StorageBackup secondBackup = backupService.createBackup().orElseThrow();

            assertEquals(StorageFileKind.DATABASE, firstBackup.kind());
            assertTrue(firstBackup.fileName().startsWith("database-20260424-123456789"));
            assertTrue(firstBackup.fileName().endsWith(".db"));
            assertEquals(List.of(secondBackup, firstBackup), backupService.listBackups(StorageFileKind.DATABASE));
            assertEquals(PermissionValue.TRUE, permissionAt(firstBackup.path(), "example.snapshot"));
        }
    }

    @Test
    void retentionPrunesOldDatabaseBackups(@TempDir Path temporaryDirectory) {
        Path databaseFile = SqliteTestSupport.databaseFile(temporaryDirectory);
        try (SqliteStore store = SqliteTestSupport.open(databaseFile)) {
            StorageBackupService backupService = backupService(temporaryDirectory.resolve("backups"), databaseFile, store, 2);

            backupService.createBackup();
            backupService.createBackup();
            backupService.createBackup();

            assertEquals(2, backupService.listBackups(StorageFileKind.DATABASE).size());
        }
    }

    @Test
    void restoreBackupReplacesLiveDatabase(@TempDir Path temporaryDirectory) {
        Path databaseFile = SqliteTestSupport.databaseFile(temporaryDirectory);
        Path backupRoot = temporaryDirectory.resolve("backups");
        StorageBackup backup;
        try (SqliteStore store = SqliteTestSupport.open(databaseFile)) {
            PermissionService permissionService = PermissionServices.sqlite(store);
            permissionService.setPermission(SUBJECT_ID, "example.restore", PermissionValue.TRUE);
            backup = backupService(backupRoot, databaseFile, store, 10).createBackup().orElseThrow();
            permissionService.setPermission(SUBJECT_ID, "example.restore", PermissionValue.FALSE);
        }

        try (SqliteStore closedSnapshotStore = SqliteTestSupport.open(databaseFile)) {
            StorageBackupService backupService = backupService(backupRoot, databaseFile, closedSnapshotStore, 10);
            closedSnapshotStore.close();
            backupService.restoreBackup(StorageFileKind.DATABASE, backup.fileName(), () -> assertEquals(PermissionValue.TRUE, permissionAt(databaseFile, "example.restore")));
        }

        assertEquals(PermissionValue.TRUE, permissionAt(databaseFile, "example.restore"));
        assertFalse(Files.exists(Path.of(databaseFile.toString() + "-wal")));
        assertFalse(Files.exists(Path.of(databaseFile.toString() + "-shm")));
    }

    @Test
    void restoreRollsBackLiveDatabaseWhenApplyFails(@TempDir Path temporaryDirectory) {
        Path databaseFile = SqliteTestSupport.databaseFile(temporaryDirectory);
        Path backupRoot = temporaryDirectory.resolve("backups");
        StorageBackup backup;
        try (SqliteStore store = SqliteTestSupport.open(databaseFile)) {
            PermissionService permissionService = PermissionServices.sqlite(store);
            permissionService.setPermission(SUBJECT_ID, "example.rollback", PermissionValue.TRUE);
            backup = backupService(backupRoot, databaseFile, store, 10).createBackup().orElseThrow();
            permissionService.setPermission(SUBJECT_ID, "example.rollback", PermissionValue.FALSE);
        }

        try (SqliteStore closedSnapshotStore = SqliteTestSupport.open(databaseFile)) {
            StorageBackupService backupService = backupService(backupRoot, databaseFile, closedSnapshotStore, 10);
            closedSnapshotStore.close();
            assertThrows(PermissionStorageException.class, () -> backupService.restoreBackup(StorageFileKind.DATABASE, backup.fileName(), () -> {
                throw new PermissionStorageException("reload failed");
            }));
        }

        assertEquals(PermissionValue.FALSE, permissionAt(databaseFile, "example.rollback"));
    }

    @Test
    void restoreRejectsUnknownOrPathTraversalBackupNames(@TempDir Path temporaryDirectory) {
        Path databaseFile = SqliteTestSupport.databaseFile(temporaryDirectory);
        try (SqliteStore store = SqliteTestSupport.open(databaseFile)) {
            StorageBackupService backupService = backupService(temporaryDirectory.resolve("backups"), databaseFile, store, 10);

            assertThrows(IllegalArgumentException.class, () -> backupService.restoreBackup(StorageFileKind.DATABASE, "../database.db", () -> {
            }));
            assertThrows(IllegalArgumentException.class, () -> backupService.restoreBackup(StorageFileKind.DATABASE, "missing.db", () -> {
            }));
        }
    }

    private static PermissionValue permissionAt(Path databaseFile, String node) {
        try (SqliteStore store = SqliteTestSupport.open(databaseFile)) {
            return PermissionServices.sqlite(store).getPermission(SUBJECT_ID, node);
        }
    }

    private StorageBackupService backupService(Path backupRoot, Path databaseFile, SqliteStore store, int retentionLimit) {
        return new StorageBackupService(backupRoot, databaseFile, store, clock, retentionLimit);
    }
}
