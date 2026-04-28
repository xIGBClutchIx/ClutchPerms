package me.clutchy.clutchperms.common.command;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.mojang.brigadier.exceptions.CommandSyntaxException;

import me.clutchy.clutchperms.common.permission.PermissionServices;
import me.clutchy.clutchperms.common.permission.PermissionValue;
import me.clutchy.clutchperms.common.runtime.ScheduledBackupStatus;
import me.clutchy.clutchperms.common.storage.PermissionStorageException;
import me.clutchy.clutchperms.common.storage.StorageFileKind;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies shared Brigadier command behavior independent of a server platform.
 */
class BackupCommandsTest extends CommandTestBase {

    /**
     * Confirms backup create and list commands report database backups newest first.
     *
     * @throws IOException when test backup setup fails
     * @throws CommandSyntaxException when command execution fails unexpectedly
     */
    @Test
    void backupCreateAndListReportsDatabaseBackups() throws IOException, CommandSyntaxException {
        writeBackup("database-20260424-120000000.db", "first.node");
        writeBackup("database-20260424-120001000.db", "second.node");
        TestSource console = TestSource.console();

        dispatcher.execute("clutchperms backup create", console);
        dispatcher.execute("clutchperms backup list", console);

        assertTrue(console.messages().getFirst().startsWith("Created database backup database-"));
        assertEquals("Backups (page 1/1):", console.messages().get(1));
        assertTrue(console.messages().contains("  database-20260424-120001000.db"));
        assertTrue(console.messages().contains("  database-20260424-120000000.db"));
    }

    /**
     * Confirms backup schedule commands expose status and mutate schedule config through the config update path.
     *
     * @throws CommandSyntaxException when command execution fails unexpectedly
     */
    @Test
    void backupScheduleCommandsReportStatusAndUpdateConfig() throws CommandSyntaxException {
        environment.setScheduledBackupStatus(
                new ScheduledBackupStatus(false, 60, false, false, Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty()));
        TestSource console = TestSource.console();

        dispatcher.execute("clutchperms backup schedule status", console);
        dispatcher.execute("clutchperms backup schedule enable", console);
        dispatcher.execute("clutchperms backup schedule interval 120", console);
        dispatcher.execute("clutchperms backup schedule disable", console);

        assertEquals(false, environment.config().backups().schedule().enabled());
        assertEquals(120, environment.config().backups().schedule().intervalMinutes());
        assertEquals(3, environment.configUpdates());
        assertEquals(3, environment.runtimeRefreshes());
        assertTrue(console.messages().contains("Scheduled database backups:"));
        assertTrue(console.messages().contains("Updated config backups.schedule.enabled: false -> true. Runtime reloaded."));
        assertTrue(console.messages().contains("Updated config backups.schedule.intervalMinutes: 60 -> 120. Runtime reloaded."));
        assertTrue(console.messages().contains("Updated config backups.schedule.enabled: true -> false. Runtime reloaded."));
        assertEquals("backups.schedule.enabled", environment.auditLogService().listNewestFirst().getFirst().targetKey());
    }

    /**
     * Confirms schedule run-now creates a database backup even when automatic backups are disabled.
     *
     * @throws CommandSyntaxException when command execution fails unexpectedly
     */
    @Test
    void backupScheduleRunNowCreatesImmediateBackup() throws CommandSyntaxException {
        TestSource console = TestSource.console();

        dispatcher.execute("clutchperms backup schedule run-now", console);

        assertTrue(console.messages().getFirst().startsWith("Created database backup database-"));
        assertEquals(1, environment.storageBackupService().listBackups(StorageFileKind.DATABASE).size());
    }

    /**
     * Confirms schedule interval validation uses the shared config validation range.
     */
    @Test
    void backupScheduleIntervalRejectsInvalidValues() {
        TestSource console = TestSource.console();

        assertCommandFails("clutchperms backup schedule interval 4", console, "backups.schedule.intervalMinutes must be an integer between 5 and 10080.");
    }

    /**
     * Confirms backup restore replaces the database and refreshes runtime state through the reload path.
     *
     * @throws IOException when test backup setup fails
     * @throws CommandSyntaxException when command execution fails unexpectedly
     */
    @Test
    void backupRestoreRestoresDatabaseAndRefreshesRuntimeState() throws IOException, CommandSyntaxException {
        PermissionServices.sqlite(backupStore).setPermission(TARGET_ID, "example.restore", PermissionValue.FALSE);
        writeBackup("database-20260424-120000000.db", "example.restore");
        TestSource console = TestSource.console();

        dispatcher.execute("clutchperms backup restore database-20260424-120000000.db", console);

        assertEquals(PermissionValue.FALSE, permissionFromDatabase("example.restore"));
        assertEquals(0, environment.reloads());
        assertEquals(0, environment.runtimeRefreshes());

        dispatcher.execute("clutchperms backup restore database-20260424-120000000.db", console);

        assertEquals(PermissionValue.TRUE, permissionFromDatabase("example.restore"));
        assertEquals(1, environment.reloads());
        assertEquals(1, environment.runtimeRefreshes());
        assertEquals(List.of("Destructive command confirmation required.",
                "Repeat this command within 30 seconds to confirm: /clutchperms backup restore database-20260424-120000000.db",
                "Restored database from backup database-20260424-120000000.db."), console.messages());
    }

    /**
     * Confirms backup restore validates the selected backup before replacing the live database.
     *
     * @throws IOException when test backup setup fails
     */
    @Test
    void backupRestoreValidatesBackupBeforeReplacingLiveDatabase() throws IOException {
        PermissionServices.sqlite(backupStore).setPermission(TARGET_ID, "example.restore", PermissionValue.FALSE);
        writeRawBackup("database-20260424-120000000.db", "not sqlite");
        TestSource console = TestSource.console();

        assertCommandFails("clutchperms backup restore database-20260424-120000000.db", console,
                "Backup operation failed: Failed to validate database backup database-20260424-120000000.db");

        assertEquals(PermissionValue.FALSE, permissionFromDatabase("example.restore"));
        assertEquals(0, environment.reloads());
        assertEquals(0, environment.runtimeRefreshes());
    }

    /**
     * Confirms backup restore rolls disk back and skips runtime refresh when reload rejects the restored database.
     *
     * @throws IOException when test backup setup fails
     * @throws CommandSyntaxException when command execution fails unexpectedly
     */
    @Test
    void backupRestoreFailureRollsBackDatabaseAndDoesNotRefreshRuntimeState() throws IOException, CommandSyntaxException {
        PermissionServices.sqlite(backupStore).setPermission(TARGET_ID, "example.restore", PermissionValue.FALSE);
        writeBackup("database-20260424-120000000.db", "example.restore");
        environment.failReload(new PermissionStorageException("bad restored database"));
        TestSource console = TestSource.console();

        dispatcher.execute("clutchperms backup restore database-20260424-120000000.db", console);
        console.messages().clear();

        assertCommandFails("clutchperms backup restore database-20260424-120000000.db", console,
                "Backup operation failed: Failed to apply restored database backup database-20260424-120000000.db");

        assertEquals(PermissionValue.FALSE, permissionFromDatabase("example.restore"));
        assertEquals(0, environment.reloads());
        assertEquals(0, environment.runtimeRefreshes());
    }

    /**
     * Confirms players without admin permission cannot use backup commands.
     */
    @Test
    void playerWithoutAdminPermissionCannotUseBackupCommands() {
        TestSource player = TestSource.player(ADMIN_ID);

        assertCommandUnavailable("clutchperms backup list", player);
    }

    /**
     * Confirms backup command suggestions include backup files.
     *
     * @throws IOException when test backup setup fails
     */
    @Test
    void backupSuggestionsIncludeBackupFiles() throws IOException {
        writeBackup("database-20260424-120000000.db", "first.node");

        assertEquals(List.of("page"), suggestionTexts("clutchperms backup list "));
        assertEquals(List.of("database-20260424-120000000.db"), suggestionTexts("clutchperms backup restore "));
    }

    /**
     * Confirms unknown backup files report close files for the selected kind.
     *
     * @throws IOException when test backup setup fails
     */
    @Test
    void unknownBackupFileSuggestsClosestBackupFile() throws IOException {
        writeBackup("database-20260424-120000000.db", "first.node");
        TestSource console = TestSource.console();

        assertCommandFails("clutchperms backup restore database-20260424-120000001.db", console, "Unknown database backup file: database-20260424-120000001.db");

        assertMessageContains(console, "Closest backup files: database-20260424-120000000.db");
    }

    /**
     * Confirms unknown backup files explain when the selected kind has no backups.
     */
    @Test
    void unknownBackupFileWithoutBackupsSuggestsList() {
        TestSource console = TestSource.console();

        assertCommandFails("clutchperms backup restore database-20260424-120000001.db", console, "Unknown database backup file: database-20260424-120000001.db");

        assertMessageContains(console, "  /clutchperms backup list");
    }

}
