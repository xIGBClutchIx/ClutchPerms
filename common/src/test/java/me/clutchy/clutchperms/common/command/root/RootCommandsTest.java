package me.clutchy.clutchperms.common.command;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.mojang.brigadier.exceptions.CommandSyntaxException;

import me.clutchy.clutchperms.common.audit.AuditEntry;
import me.clutchy.clutchperms.common.config.ClutchPermsAuditRetentionConfig;
import me.clutchy.clutchperms.common.config.ClutchPermsBackupConfig;
import me.clutchy.clutchperms.common.config.ClutchPermsChatConfig;
import me.clutchy.clutchperms.common.config.ClutchPermsCommandConfig;
import me.clutchy.clutchperms.common.config.ClutchPermsConfig;
import me.clutchy.clutchperms.common.config.ClutchPermsPaperConfig;
import me.clutchy.clutchperms.common.display.DisplayText;
import me.clutchy.clutchperms.common.permission.PermissionNodes;
import me.clutchy.clutchperms.common.permission.PermissionValue;
import me.clutchy.clutchperms.common.storage.PermissionStorageException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies shared Brigadier command behavior independent of a server platform.
 */
class RootCommandsTest extends CommandTestBase {

    /**
     * Confirms the root command returns the shared command list.
     *
     * @throws CommandSyntaxException when command execution fails unexpectedly
     */
    @Test
    void rootCommandSendsCommandList() throws CommandSyntaxException {
        TestSource console = TestSource.console();

        dispatcher.execute("clutchperms", console);

        assertEquals(commandListPageOneMessages(), console.messages());
        assertSuggests(console.commandMessages().get(1), "/clutchperms help [page]");
        assertRuns(console.commandMessages().getLast(), "/clutchperms help 2");
    }

    /**
     * Confirms root command aliases execute the shared command tree and keep alias-specific usage output.
     *
     * @throws CommandSyntaxException when command execution fails unexpectedly
     */
    @Test
    void rootAliasesExecuteSharedCommandTree() throws CommandSyntaxException {
        TestSource cperms = TestSource.console();
        TestSource perms = TestSource.console();

        dispatcher.execute("cperms", cperms);
        dispatcher.execute("perms group", perms);

        assertEquals(commandListPageOneMessages("cperms"), cperms.messages());
        assertEquals(List.of("Missing group command.", "List groups or choose a group to inspect or mutate.", "Try one:", "  /perms group list",
                "  /perms group <group> <create|delete|info|list|members|parents>", "  /perms group <group> <get|clear> <node>", "  /perms group <group> set <node> <true|false>",
                "  /perms group <group> clear-all", "  /perms group <group> rename <new-group>", "  /perms group <group> parent <add|remove> <parent>",
                "  /perms group <group> <prefix|suffix> get|set|clear"), perms.messages());
        assertSuggests(perms.commandMessages().get(3), "/perms group list");
    }

    /**
     * Confirms explicit help pages use compact navigation and preserve the active root literal.
     *
     * @throws CommandSyntaxException when command execution fails unexpectedly
     */
    @Test
    void helpCommandSupportsPagedOutputAndAliases() throws CommandSyntaxException {
        TestSource helpPage = TestSource.console();
        TestSource aliasPage = TestSource.console();

        dispatcher.execute("clutchperms help 2", helpPage);
        dispatcher.execute("perms help 2", aliasPage);

        assertEquals(commandListPageTwoMessages(), helpPage.messages());
        assertEquals(commandListPageTwoMessages("perms"), aliasPage.messages());
        assertRuns(helpPage.commandMessages().getLast(), "/clutchperms help 3");
        assertRuns(aliasPage.commandMessages().getLast(), "/perms help 3");
    }

    /**
     * Confirms command help includes the dedicated group members list command.
     *
     * @throws CommandSyntaxException when command execution fails unexpectedly
     */
    @Test
    void helpCommandIncludesGroupMembersCommand() throws CommandSyntaxException {
        TestSource console = TestSource.console();

        dispatcher.execute("clutchperms help 6", console);

        assertMessageContains(console, "/clutchperms group <group> members [page]");
    }

    /**
     * Confirms command help page size follows active config.
     *
     * @throws CommandSyntaxException when command execution fails unexpectedly
     */
    @Test
    void helpCommandUsesConfiguredPageSize() throws CommandSyntaxException {
        environment.setConfig(new ClutchPermsConfig(ClutchPermsBackupConfig.defaults(), new ClutchPermsCommandConfig(3, 8)));
        TestSource console = TestSource.console();

        dispatcher.execute("clutchperms", console);

        assertEquals(List.of("ClutchPerms commands (page 1/22):", "/clutchperms help [page]", "/clutchperms status", "/clutchperms reload", "Page 1/22 | Next >"),
                console.messages());
        assertRuns(console.commandMessages().getLast(), "/clutchperms help 2");
    }

    /**
     * Confirms invalid and out-of-range help pages return styled ClutchPerms feedback.
     *
     * @throws CommandSyntaxException when command execution fails unexpectedly
     */
    @Test
    void helpPagesRejectInvalidPages() throws CommandSyntaxException {
        TestSource invalid = TestSource.console();
        TestSource outOfRange = TestSource.console();

        assertEquals(0, dispatcher.execute("clutchperms help nope", invalid));
        assertEquals(List.of("Invalid page: nope", "Pages start at 1.", "Try one:", "  /clutchperms help 1"), invalid.messages());

        assertEquals(0, dispatcher.execute("clutchperms help 99", outOfRange));
        assertEquals(List.of("Page 99 is out of range.", "Available pages: 1-10.", "Try one:", "  /clutchperms help 10"), outOfRange.messages());
    }

    /**
     * Confirms command syntax suggestions keep plain text intact while highlighting arguments separately.
     */
    @Test
    void commandSyntaxSuggestionsHighlightArguments() {
        CommandMessage message = CommandLang.suggestion("clutchperms", "group admin set <node> <true|false>");

        assertEquals("  /clutchperms group admin set <node> <true|false>", message.plainText());
        assertEquals(CommandMessage.Color.GRAY, message.segments().getFirst().color());
        assertTrue(message.segments().stream().anyMatch(segment -> segment.text().equals("/clutchperms group admin set ") && segment.color() == CommandMessage.Color.WHITE));
        assertTrue(message.segments().stream().anyMatch(segment -> segment.text().equals("node") && segment.color() == CommandMessage.Color.YELLOW));
        assertTrue(message.segments().stream().anyMatch(segment -> segment.text().equals("true") && segment.color() == CommandMessage.Color.GREEN));
        assertTrue(message.segments().stream().anyMatch(segment -> segment.text().equals("|") && segment.color() == CommandMessage.Color.GRAY));
        assertTrue(message.segments().stream().anyMatch(segment -> segment.text().equals("false") && segment.color() == CommandMessage.Color.GREEN));
        assertSuggests(message, "/clutchperms group admin set <node> <true|false>");
        assertTrue(message.segments().stream().anyMatch(segment -> segment.hover() != null && segment.hover().plainText().contains("Click to paste")));
    }

    /**
     * Confirms incomplete command branches report contextual usage instead of falling through silently.
     *
     * @throws CommandSyntaxException when command execution fails unexpectedly
     */
    @Test
    void incompleteCommandsReportContextualUsage() throws CommandSyntaxException {
        TestSource groupRoot = TestSource.console();
        TestSource groupTarget = TestSource.console();
        TestSource groupGet = TestSource.console();
        TestSource groupRename = TestSource.console();
        TestSource userRoot = TestSource.console();
        TestSource userSet = TestSource.console();
        TestSource backupRestore = TestSource.console();
        TestSource nodesRoot = TestSource.console();

        assertEquals(0, dispatcher.execute("clutchperms group", groupRoot));
        assertEquals(groupRootUsageMessages(), groupRoot.messages());

        assertEquals(0, dispatcher.execute("clutchperms group test", groupTarget));
        assertEquals(groupTargetUsageMessages("test"), groupTarget.messages());

        assertEquals(0, dispatcher.execute("clutchperms group test get", groupGet));
        assertEquals(List.of("Missing permission node.", "Choose the group permission node to read.", "Try one:", "  /clutchperms group test get <node>"), groupGet.messages());

        assertEquals(0, dispatcher.execute("clutchperms group test rename", groupRename));
        assertEquals(List.of("Missing new group name.", "Choose the new group name.", "Try one:", "  /clutchperms group test rename <new-group>"), groupRename.messages());

        assertEquals(0, dispatcher.execute("clutchperms user", userRoot));
        assertEquals(userRootUsageMessages(), userRoot.messages());

        assertEquals(0, dispatcher.execute("clutchperms user Target set", userSet));
        assertEquals(List.of("Missing permission assignment.", "Set a node to true or false.", "Try one:", "  /clutchperms user Target set <node> <true|false>"),
                userSet.messages());

        assertEquals(0, dispatcher.execute("clutchperms backup restore", backupRestore));
        assertEquals(List.of("Missing backup file.", "Pick a database backup file.", "Try one:", "  /clutchperms backup restore <backup-file>"), backupRestore.messages());

        assertEquals(0, dispatcher.execute("clutchperms nodes", nodesRoot));
        assertEquals(nodesUsageMessages(), nodesRoot.messages());
    }

    /**
     * Confirms the explicit status subcommand returns the same diagnostics with the current subject count.
     *
     * @throws CommandSyntaxException when command execution fails unexpectedly
     */
    @Test
    void statusSubcommandSendsDiagnostics() throws CommandSyntaxException {
        subjectMetadataService.recordSubject(TARGET_ID, "Target", FIRST_SEEN);
        TestSource console = TestSource.console();

        dispatcher.execute("clutchperms status", console);

        assertEquals(statusMessages(1), console.messages());
    }

    /**
     * Confirms status reports active config values.
     *
     * @throws CommandSyntaxException when command execution fails unexpectedly
     */
    @Test
    void statusSubcommandReportsActiveConfig() throws CommandSyntaxException {
        environment.setConfig(new ClutchPermsConfig(new ClutchPermsBackupConfig(3), new ClutchPermsCommandConfig(4, 5), new ClutchPermsChatConfig(false)));
        TestSource console = TestSource.console();

        dispatcher.execute("clutchperms status", console);

        assertTrue(console.messages().contains("Backup retention: newest 3 database backups."));
        assertTrue(console.messages().contains("Audit retention: enabled, max age 90 days, max entries none."));
        assertTrue(console.messages().contains("Command page sizes: help 4, lists 5."));
        assertTrue(console.messages().contains("Chat formatting: disabled."));
    }

    /**
     * Confirms command mutations write audit history and history lists newest entries first.
     *
     * @throws CommandSyntaxException when command execution fails unexpectedly
     */
    @Test
    void historyListsAuditedCommandMutationsNewestFirst() throws CommandSyntaxException {
        TestSource console = TestSource.console();

        dispatcher.execute("clutchperms user Target set example.node true", console);
        dispatcher.execute("clutchperms group staff create", console);
        dispatcher.execute("clutchperms group staff set group.node false", console);
        dispatcher.execute("clutchperms user Target group add staff", console);
        dispatcher.execute("clutchperms user Target prefix set &aTarget", console);
        dispatcher.execute("clutchperms config set backups.retentionLimit 3", console);

        List<AuditEntry> entries = environment.auditLogService().listNewestFirst();
        assertEquals(List.of("config.set", "user.display.prefix.set", "user.group.add", "group.permission.set", "group.create", "user.permission.set"),
                entries.stream().map(AuditEntry::action).toList());
        assertEquals("user-permissions", entries.getLast().targetType());
        assertTrue(entries.getLast().beforeJson().contains("\"permissions\":{}"));
        assertTrue(entries.getLast().afterJson().contains("\"example.node\":\"TRUE\""));

        TestSource history = TestSource.console();
        dispatcher.execute("clutchperms history", history);

        assertMessageContains(history, "#6");
        assertMessageContains(history, "config.set backups.retentionLimit");
        assertSuggests(history.commandMessages().get(1), "/clutchperms undo 6");
    }

    @Test
    void historyPruneDaysRequiresConfirmationAndDeletesOldRows() throws CommandSyntaxException {
        environment.auditLogService().append(auditRecord(Instant.parse("2026-01-01T00:00:00Z"), "old"));
        environment.auditLogService().append(auditRecord(Instant.now(), "new"));
        TestSource console = TestSource.console();

        dispatcher.execute("clutchperms history prune days 90", console);

        assertEquals(2, environment.auditLogService().listNewestFirst().size());
        assertTrue(console.messages().contains("Destructive command confirmation required."));

        dispatcher.execute("clutchperms history prune days 90", console);

        assertEquals(List.of("history.prune.days", "new"), environment.auditLogService().listNewestFirst().stream().map(AuditEntry::action).toList());
        assertFalse(environment.auditLogService().listNewestFirst().getFirst().undoable());
        assertTrue(console.messages().contains("Pruned 1 audit history entries."));
    }

    @Test
    void historyPruneCountKeepsNewestEntriesIncludingPruneAuditRow() throws CommandSyntaxException {
        environment.auditLogService().append(auditRecord(Instant.now(), "first"));
        environment.auditLogService().append(auditRecord(Instant.now(), "second"));
        environment.auditLogService().append(auditRecord(Instant.now(), "third"));
        TestSource console = TestSource.console();

        dispatcher.execute("clutchperms history prune count 2", console);
        dispatcher.execute("clutchperms history prune count 2", console);

        assertEquals(List.of("history.prune.count", "third"), environment.auditLogService().listNewestFirst().stream().map(AuditEntry::action).toList());
        assertTrue(console.messages().contains("Pruned 2 audit history entries."));
    }

    @Test
    void historyPruneWithNoMatchesSkipsConfirmation() throws CommandSyntaxException {
        environment.auditLogService().append(auditRecord(Instant.now(), "new"));
        TestSource console = TestSource.console();

        dispatcher.execute("clutchperms history prune days 1", console);

        assertEquals(1, environment.auditLogService().listNewestFirst().size());
        assertEquals(List.of("No audit history entries matched the prune criteria."), console.messages());
    }

    @Test
    void historyPruneRequiresPrunePermission() throws CommandSyntaxException {
        environment.auditLogService().append(auditRecord(Instant.parse("2026-01-01T00:00:00Z"), "old"));
        TestSource player = TestSource.player(ADMIN_ID);

        permissionService.setPermission(ADMIN_ID, PermissionNodes.ADMIN_HISTORY, PermissionValue.TRUE);
        assertCommandUnavailable("clutchperms history prune days 90", player);

        permissionService.setPermission(ADMIN_ID, PermissionNodes.ADMIN_HISTORY_PRUNE, PermissionValue.TRUE);
        assertEquals(1, dispatcher.execute("clutchperms history prune days 90", player));
    }

    @Test
    void prunedUndoableEntriesCanNoLongerBeUndone() throws CommandSyntaxException {
        TestSource console = TestSource.console();
        dispatcher.execute("clutchperms user Target set example.node true", console);
        dispatcher.execute("clutchperms history prune count 1", console);
        dispatcher.execute("clutchperms history prune count 1", console);

        assertCommandFails("clutchperms undo 1", console, "Unknown audit history entry: 1");
    }

    @Test
    void automaticAuditRetentionRunsAfterAuditedCommandMutation() throws CommandSyntaxException {
        environment.setConfig(new ClutchPermsConfig(ClutchPermsBackupConfig.defaults(), new ClutchPermsAuditRetentionConfig(true, 3650, 2), ClutchPermsCommandConfig.defaults(),
                ClutchPermsChatConfig.defaults(), ClutchPermsPaperConfig.defaults()));
        TestSource console = TestSource.console();

        dispatcher.execute("clutchperms user Target set example.one true", console);
        dispatcher.execute("clutchperms user Target set example.two true", console);
        dispatcher.execute("clutchperms user Target set example.three true", console);

        assertEquals(List.of("user.permission.set", "user.permission.set"), environment.auditLogService().listNewestFirst().stream().map(AuditEntry::action).toList());
        assertTrue(environment.auditLogService().get(1).isEmpty());
    }

    /**
     * Confirms undo restores a user permission mutation and marks the original entry as undone.
     *
     * @throws CommandSyntaxException when command execution fails unexpectedly
     */
    @Test
    void undoRestoresUserPermissionMutation() throws CommandSyntaxException {
        TestSource console = TestSource.console();

        dispatcher.execute("clutchperms user Target set example.node true", console);
        dispatcher.execute("clutchperms undo 1", console);

        assertEquals(PermissionValue.UNSET, permissionService.getPermission(TARGET_ID, "example.node"));
        AuditEntry original = environment.auditLogService().get(1).orElseThrow();
        assertTrue(original.undone());
        assertEquals(Optional.of(2L), original.undoneByEntryId());
        assertEquals("undo", environment.auditLogService().get(2).orElseThrow().action());
        assertEquals(List.of("Set example.node for Target (00000000-0000-0000-0000-000000000002) to TRUE.", "Undid audit history entry 1."), console.messages());
    }

    /**
     * Confirms undo refuses to overwrite newer changes.
     *
     * @throws CommandSyntaxException when command execution fails unexpectedly
     */
    @Test
    void undoFailsWhenCurrentStateConflicts() throws CommandSyntaxException {
        TestSource console = TestSource.console();

        dispatcher.execute("clutchperms user Target set example.node true", console);
        permissionService.setPermission(TARGET_ID, "example.node", PermissionValue.FALSE);

        assertCommandFails("clutchperms undo 1", console, "Audit history entry 1 cannot be undone because the current target state has changed.");
        assertEquals(PermissionValue.FALSE, permissionService.getPermission(TARGET_ID, "example.node"));
    }

    /**
     * Confirms undo can restore deleted group state.
     *
     * @throws CommandSyntaxException when command execution fails unexpectedly
     */
    @Test
    void undoRestoresDeletedGroupState() throws CommandSyntaxException {
        groupService.createGroup("staff");
        groupService.setGroupPermission("staff", "group.node", PermissionValue.TRUE);
        groupService.setGroupPrefix("staff", DisplayText.parse("&7[Staff]"));
        groupService.addSubjectGroup(TARGET_ID, "staff");
        TestSource console = TestSource.console();

        dispatcher.execute("clutchperms group staff delete", console);
        dispatcher.execute("clutchperms group staff delete", console);
        dispatcher.execute("clutchperms undo 1", console);

        assertTrue(groupService.hasGroup("staff"));
        assertEquals(PermissionValue.TRUE, groupService.getGroupPermission("staff", "group.node"));
        assertEquals("&7[Staff]", groupService.getGroupDisplay("staff").prefix().orElseThrow().rawText());
        assertTrue(groupService.getSubjectGroups(TARGET_ID).contains("staff"));
    }

    /**
     * Confirms reload refreshes storage and runtime bridges in order.
     *
     * @throws CommandSyntaxException when command execution fails unexpectedly
     */
    @Test
    void reloadSubcommandReloadsStorageAndRefreshesRuntimePermissions() throws CommandSyntaxException {
        TestSource console = TestSource.console();

        dispatcher.execute("clutchperms reload", console);

        assertEquals(1, environment.reloads());
        assertEquals(1, environment.runtimeRefreshes());
        assertEquals(List.of("Reloaded config and database storage from disk."), console.messages());
    }

    /**
     * Confirms a failed reload reports a command failure and does not refresh runtime state.
     */
    @Test
    void reloadSubcommandFailsWithoutRuntimeRefreshWhenStorageReloadFails() {
        TestSource console = TestSource.console();
        environment.failReload(new PermissionStorageException("bad permissions file"));

        assertCommandFails("clutchperms reload", console, "Failed to reload ClutchPerms storage: bad permissions file");

        assertEquals(0, environment.reloads());
        assertEquals(0, environment.runtimeRefreshes());
    }

    /**
     * Confirms validation checks storage without reloading active services or refreshing runtime state.
     *
     * @throws CommandSyntaxException when command execution fails unexpectedly
     */
    @Test
    void validateSubcommandValidatesStorageWithoutReloadingOrRefreshingRuntimePermissions() throws CommandSyntaxException {
        TestSource console = TestSource.console();

        dispatcher.execute("clutchperms validate", console);

        assertEquals(1, environment.validations());
        assertEquals(0, environment.reloads());
        assertEquals(0, environment.runtimeRefreshes());
        assertEquals(List.of("Validated config and database storage from disk."), console.messages());
    }

    /**
     * Confirms validation failures report a command failure and do not reload or refresh runtime state.
     */
    @Test
    void validateSubcommandFailsWithoutReloadingOrRefreshingRuntimePermissions() {
        TestSource console = TestSource.console();
        environment.failValidation(new PermissionStorageException("bad groups file"));

        assertCommandFails("clutchperms validate", console, "Failed to validate ClutchPerms storage: bad groups file");

        assertEquals(0, environment.validations());
        assertEquals(0, environment.reloads());
        assertEquals(0, environment.runtimeRefreshes());
    }

    /**
     * Confirms destructive command confirmations expire instead of staying armed indefinitely.
     *
     * @throws CommandSyntaxException when command execution fails unexpectedly
     */
    @Test
    void destructiveConfirmationExpiresAfterThirtySeconds() throws CommandSyntaxException {
        MutableClock clock = new MutableClock(FIRST_SEEN);
        ClutchPermsCommands.setConfirmationClockForTests(clock);
        groupService.createGroup("staff");
        TestSource console = TestSource.console();

        dispatcher.execute("clutchperms group staff delete", console);
        clock.advance(Duration.ofSeconds(31));
        dispatcher.execute("clutchperms group staff delete", console);

        assertTrue(groupService.hasGroup("staff"));
        assertEquals(List.of("Destructive command confirmation required.", "Repeat this command within 30 seconds to confirm: /clutchperms group staff delete",
                "Destructive command confirmation required.", "Repeat this command within 30 seconds to confirm: /clutchperms group staff delete"), console.messages());

        dispatcher.execute("clutchperms group staff delete", console);

        assertFalse(groupService.hasGroup("staff"));
    }

    /**
     * Confirms another destructive command replaces a pending confirmation for the same source.
     *
     * @throws CommandSyntaxException when command execution fails unexpectedly
     */
    @Test
    void differentDestructiveCommandReplacesPendingConfirmation() throws CommandSyntaxException {
        permissionService.setPermission(TARGET_ID, "example.node", PermissionValue.TRUE);
        groupService.createGroup("staff");
        groupService.setGroupPermission("staff", "group.node", PermissionValue.TRUE);
        TestSource console = TestSource.console();

        dispatcher.execute("clutchperms user Target clear-all", console);
        dispatcher.execute("clutchperms group staff clear-all", console);
        dispatcher.execute("clutchperms group staff clear-all", console);
        dispatcher.execute("clutchperms user Target clear-all", console);

        assertEquals(Map.of("example.node", PermissionValue.TRUE), permissionService.getPermissions(TARGET_ID));
        assertEquals(Map.of(), groupService.getGroupPermissions("staff"));
        assertEquals("Repeat this command within 30 seconds to confirm: /clutchperms user Target clear-all", console.messages().getLast());
    }

    /**
     * Confirms player confirmation state is isolated by source UUID.
     *
     * @throws CommandSyntaxException when command execution fails unexpectedly
     */
    @Test
    void destructiveConfirmationIsIsolatedByPlayer() throws CommandSyntaxException {
        permissionService.setPermission(ADMIN_ID, PermissionNodes.ADMIN_ALL, PermissionValue.TRUE);
        permissionService.setPermission(SECOND_TARGET_ID, PermissionNodes.ADMIN_ALL, PermissionValue.TRUE);
        permissionService.setPermission(TARGET_ID, "example.node", PermissionValue.TRUE);
        TestSource firstPlayer = TestSource.player(ADMIN_ID);
        TestSource secondPlayer = TestSource.player(SECOND_TARGET_ID);

        dispatcher.execute("clutchperms user Target clear-all", firstPlayer);
        dispatcher.execute("clutchperms user Target clear-all", secondPlayer);

        assertEquals(Map.of("example.node", PermissionValue.TRUE), permissionService.getPermissions(TARGET_ID));
        assertEquals(List.of("Destructive command confirmation required.", "Repeat this command within 30 seconds to confirm: /clutchperms user Target clear-all"),
                secondPlayer.messages());

        dispatcher.execute("clutchperms user Target clear-all", firstPlayer);

        assertEquals(Map.of(), permissionService.getPermissions(TARGET_ID));
    }

    /**
     * Confirms aliases can confirm the same normalized destructive operation.
     *
     * @throws CommandSyntaxException when command execution fails unexpectedly
     */
    @Test
    void destructiveConfirmationCanUseAnyRootAlias() throws CommandSyntaxException {
        groupService.createGroup("staff");
        TestSource console = TestSource.console();

        dispatcher.execute("clutchperms group staff delete", console);
        dispatcher.execute("cperms group staff delete", console);

        assertFalse(groupService.hasGroup("staff"));
    }

    /**
     * Confirms invalid or protected destructive targets do not arm confirmation state.
     *
     * @throws CommandSyntaxException when command execution fails unexpectedly
     */
    @Test
    void invalidAndProtectedDestructiveTargetsDoNotArmConfirmation() throws CommandSyntaxException {
        groupService.createGroup("staff");
        groupService.setGroupPermission("staff", "group.node", PermissionValue.TRUE);
        TestSource console = TestSource.console();

        assertCommandFails("clutchperms group missing delete", console, "Unknown group: missing");
        dispatcher.execute("clutchperms group staff delete", console);
        assertTrue(groupService.hasGroup("staff"));

        assertCommandFails("clutchperms group op clear-all", console, "Group operation failed: op group permissions are protected");
        dispatcher.execute("clutchperms group staff clear-all", console);
        assertEquals(Map.of("group.node", PermissionValue.TRUE), groupService.getGroupPermissions("staff"));
    }

}
