package me.clutchy.clutchperms.common.storage;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies shared rolling backup and restore behavior for persisted storage files.
 */
class StorageBackupServiceTest {

    /**
     * Confirms the first save of a missing live file does not create a backup.
     */
    @Test
    void missingLiveFileDoesNotCreateBackup(@TempDir Path temporaryDirectory) {
        Path liveFile = temporaryDirectory.resolve("permissions.json");
        Path backupRoot = temporaryDirectory.resolve("backups");
        StorageBackupService backupService = backupService(backupRoot, liveFile);

        assertTrue(backupService.backupExistingFile(StorageFileKind.PERMISSIONS).isEmpty());
        assertFalse(Files.exists(backupRoot.resolve("permissions")));
    }

    /**
     * Confirms storage bootstrap creates visible empty JSON files for a fresh storage directory.
     *
     * @throws IOException when test file inspection fails
     */
    @Test
    void materializeMissingJsonFilesCreatesEmptyStorageFiles(@TempDir Path temporaryDirectory) throws IOException {
        Path permissionsFile = temporaryDirectory.resolve("permissions.json");
        Path subjectsFile = temporaryDirectory.resolve("subjects.json");
        Path groupsFile = temporaryDirectory.resolve("groups.json");
        Path nodesFile = temporaryDirectory.resolve("nodes.json");

        StorageFiles.materializeMissingJsonFiles(
                Map.of(StorageFileKind.PERMISSIONS, permissionsFile, StorageFileKind.SUBJECTS, subjectsFile, StorageFileKind.GROUPS, groupsFile, StorageFileKind.NODES, nodesFile));

        assertTrue(Files.readString(permissionsFile).contains("\"subjects\": {}"));
        assertTrue(Files.readString(subjectsFile).contains("\"subjects\": {}"));
        assertTrue(Files.readString(groupsFile).contains("\"groups\": {}"));
        assertTrue(Files.readString(groupsFile).contains("\"memberships\": {}"));
        assertTrue(Files.readString(nodesFile).contains("\"nodes\": {}"));
    }

    /**
     * Confirms storage bootstrap leaves existing files untouched.
     *
     * @throws IOException when test file setup or inspection fails
     */
    @Test
    void materializeMissingJsonFilesDoesNotOverwriteExistingStorage(@TempDir Path temporaryDirectory) throws IOException {
        Path permissionsFile = temporaryDirectory.resolve("permissions.json");
        Files.writeString(permissionsFile, "existing");

        StorageFiles.materializeMissingJsonFiles(Map.of(StorageFileKind.PERMISSIONS, permissionsFile));

        assertEquals("existing", Files.readString(permissionsFile));
    }

    /**
     * Confirms shared writes back up the current live file before replacement.
     *
     * @throws IOException when test file setup fails
     */
    @Test
    void existingLiveFileIsBackedUpBeforeReplacement(@TempDir Path temporaryDirectory) throws IOException {
        Path liveFile = temporaryDirectory.resolve("permissions.json");
        Files.writeString(liveFile, "old");

        StorageFiles.writeAtomicallyWithBackup(liveFile, StorageFileKind.PERMISSIONS, writer -> writer.write("new"));

        assertEquals("new", Files.readString(liveFile));
        List<StorageBackup> backups = backupService(temporaryDirectory.resolve("backups"), liveFile).listBackups(StorageFileKind.PERMISSIONS);
        assertEquals(1, backups.size());
        assertTrue(backups.getFirst().path().startsWith(temporaryDirectory.resolve("backups").resolve("permissions")));
        assertEquals("old", Files.readString(backups.getFirst().path()));
    }

    /**
     * Confirms retention keeps the newest 10 backups and drops older entries.
     *
     * @throws IOException when test file setup fails
     */
    @Test
    void retentionKeepsNewestTenBackups(@TempDir Path temporaryDirectory) throws IOException {
        Path liveFile = temporaryDirectory.resolve("permissions.json");
        MutableClock clock = new MutableClock(Instant.parse("2026-04-24T12:00:00Z"));
        StorageBackupService backupService = new StorageBackupService(temporaryDirectory.resolve("backups"), Map.of(StorageFileKind.PERMISSIONS, liveFile), clock);

        for (int index = 0; index < 12; index++) {
            Files.writeString(liveFile, "value-" + index);
            backupService.backupExistingFile(StorageFileKind.PERMISSIONS);
            clock.advanceMillis(1);
        }

        List<StorageBackup> backups = backupService.listBackups(StorageFileKind.PERMISSIONS);
        assertEquals(10, backups.size());
        assertEquals("value-11", Files.readString(backups.getFirst().path()));
        assertEquals("value-2", Files.readString(backups.getLast().path()));
    }

    /**
     * Confirms backups created in the same millisecond sort by their collision suffix as newest first.
     *
     * @throws IOException when test file setup fails
     */
    @Test
    void sameTimestampBackupsSortByCollisionSuffix(@TempDir Path temporaryDirectory) throws IOException {
        Path liveFile = temporaryDirectory.resolve("permissions.json");
        Clock clock = Clock.fixed(Instant.parse("2026-04-24T12:00:00Z"), ZoneId.of("UTC"));
        StorageBackupService backupService = new StorageBackupService(temporaryDirectory.resolve("backups"), Map.of(StorageFileKind.PERMISSIONS, liveFile), clock);

        Files.writeString(liveFile, "first");
        backupService.backupExistingFile(StorageFileKind.PERMISSIONS);
        Files.writeString(liveFile, "second");
        backupService.backupExistingFile(StorageFileKind.PERMISSIONS);

        List<StorageBackup> backups = backupService.listBackups(StorageFileKind.PERMISSIONS);
        assertEquals(2, backups.size());
        assertEquals("second", Files.readString(backups.getFirst().path()));
        assertEquals("first", Files.readString(backups.getLast().path()));
    }

    /**
     * Confirms restore copies the selected backup over the live file.
     *
     * @throws IOException when test file setup fails
     */
    @Test
    void restoreCopiesSelectedBackupIntoLiveFile(@TempDir Path temporaryDirectory) throws IOException {
        Path liveFile = temporaryDirectory.resolve("permissions.json");
        Files.writeString(liveFile, "backup");
        StorageBackupService backupService = backupService(temporaryDirectory.resolve("backups"), liveFile);
        StorageBackup backup = backupService.backupExistingFile(StorageFileKind.PERMISSIONS).orElseThrow();
        Files.writeString(liveFile, "current");

        backupService.restoreBackup(StorageFileKind.PERMISSIONS, backup.fileName(), () -> {
        });

        assertEquals("backup", Files.readString(liveFile));
    }

    /**
     * Confirms restore rolls the live file back when applying the restored file fails.
     *
     * @throws IOException when test file setup fails
     */
    @Test
    void restoreRollbackRestoresPreviousLiveFileWhenApplyFails(@TempDir Path temporaryDirectory) throws IOException {
        Path liveFile = temporaryDirectory.resolve("permissions.json");
        Files.writeString(liveFile, "backup");
        StorageBackupService backupService = backupService(temporaryDirectory.resolve("backups"), liveFile);
        StorageBackup backup = backupService.backupExistingFile(StorageFileKind.PERMISSIONS).orElseThrow();
        Files.writeString(liveFile, "current");

        PermissionStorageException exception = assertThrows(PermissionStorageException.class,
                () -> backupService.restoreBackup(StorageFileKind.PERMISSIONS, backup.fileName(), () -> {
                    throw new PermissionStorageException("bad restored file");
                }));

        assertTrue(exception.getMessage().contains("Failed to apply restored permissions backup"));
        assertEquals("current", Files.readString(liveFile));
    }

    /**
     * Confirms restore rollback removes a restored file when no live file existed before the restore.
     *
     * @throws IOException when test file setup fails
     */
    @Test
    void restoreRollbackDeletesRestoredFileWhenLiveFileWasMissing(@TempDir Path temporaryDirectory) throws IOException {
        Path liveFile = temporaryDirectory.resolve("permissions.json");
        Path backupDirectory = temporaryDirectory.resolve("backups").resolve("permissions");
        Files.createDirectories(backupDirectory);
        String backupFileName = "permissions-20260424-120000000.json";
        Files.writeString(backupDirectory.resolve(backupFileName), "backup");
        StorageBackupService backupService = backupService(temporaryDirectory.resolve("backups"), liveFile);

        assertThrows(PermissionStorageException.class, () -> backupService.restoreBackup(StorageFileKind.PERMISSIONS, backupFileName, () -> {
            throw new PermissionStorageException("bad restored file");
        }));

        assertFalse(Files.exists(liveFile));
    }

    /**
     * Confirms restore rejects filenames outside the selected backup directory.
     */
    @Test
    void restoreRejectsPathTraversalBackupFileNames(@TempDir Path temporaryDirectory) {
        Path liveFile = temporaryDirectory.resolve("permissions.json");
        StorageBackupService backupService = backupService(temporaryDirectory.resolve("backups"), liveFile);

        assertThrows(IllegalArgumentException.class, () -> backupService.restoreBackup(StorageFileKind.PERMISSIONS, "../permissions.json", () -> {
        }));
    }

    private static StorageBackupService backupService(Path backupRoot, Path liveFile) {
        return StorageBackupService.forFiles(backupRoot, Map.of(StorageFileKind.PERMISSIONS, liveFile));
    }

    private static final class MutableClock extends Clock {

        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        @Override
        public ZoneId getZone() {
            return ZoneId.of("UTC");
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }

        private void advanceMillis(long millis) {
            instant = instant.plusMillis(millis);
        }
    }
}
