package me.clutchy.clutchperms.common.command;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.UnaryOperator;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestion;

import me.clutchy.clutchperms.common.config.ClutchPermsBackupConfig;
import me.clutchy.clutchperms.common.config.ClutchPermsChatConfig;
import me.clutchy.clutchperms.common.config.ClutchPermsCommandConfig;
import me.clutchy.clutchperms.common.config.ClutchPermsConfig;
import me.clutchy.clutchperms.common.display.DisplayText;
import me.clutchy.clutchperms.common.group.GroupChangeListener;
import me.clutchy.clutchperms.common.group.GroupService;
import me.clutchy.clutchperms.common.group.GroupServices;
import me.clutchy.clutchperms.common.group.InMemoryGroupService;
import me.clutchy.clutchperms.common.node.MutablePermissionNodeRegistry;
import me.clutchy.clutchperms.common.node.PermissionNodeRegistries;
import me.clutchy.clutchperms.common.node.PermissionNodeRegistry;
import me.clutchy.clutchperms.common.node.PermissionNodeSource;
import me.clutchy.clutchperms.common.permission.InMemoryPermissionService;
import me.clutchy.clutchperms.common.permission.PermissionNodes;
import me.clutchy.clutchperms.common.permission.PermissionResolver;
import me.clutchy.clutchperms.common.permission.PermissionService;
import me.clutchy.clutchperms.common.permission.PermissionServices;
import me.clutchy.clutchperms.common.permission.PermissionValue;
import me.clutchy.clutchperms.common.storage.PermissionStorageException;
import me.clutchy.clutchperms.common.storage.SqliteDependencyMode;
import me.clutchy.clutchperms.common.storage.SqliteStore;
import me.clutchy.clutchperms.common.storage.StorageBackupService;
import me.clutchy.clutchperms.common.storage.StorageFileKind;
import me.clutchy.clutchperms.common.subject.InMemorySubjectMetadataService;
import me.clutchy.clutchperms.common.subject.SubjectMetadataService;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies shared Brigadier command behavior independent of a server platform.
 */
class ClutchPermsCommandsTest {

    private static final UUID ADMIN_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    private static final UUID TARGET_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");

    private static final UUID UUID_NAMED_PLAYER_ID = UUID.fromString("00000000-0000-0000-0000-000000000003");

    private static final UUID SECOND_TARGET_ID = UUID.fromString("00000000-0000-0000-0000-000000000004");

    private static final Instant FIRST_SEEN = Instant.parse("2026-04-24T12:00:00Z");

    private static final Instant SECOND_SEEN = Instant.parse("2026-04-24T13:00:00Z");

    private static final CommandStatusDiagnostics STATUS_DIAGNOSTICS = new CommandStatusDiagnostics("/tmp/clutchperms/database.db", "test bridge active",
            "/tmp/clutchperms/config.json");

    private PermissionService permissionService;

    private SubjectMetadataService subjectMetadataService;

    private GroupService groupService;

    private MutablePermissionNodeRegistry manualPermissionNodeRegistry;

    private PermissionResolver permissionResolver;

    private TestEnvironment environment;

    private CommandDispatcher<TestSource> dispatcher;

    private SqliteStore backupStore;

    @TempDir
    private Path temporaryDirectory;

    /**
     * Creates a fresh command dispatcher and permission service for each test case.
     */
    @BeforeEach
    void setUp() {
        PermissionService storagePermissionService = new InMemoryPermissionService();
        subjectMetadataService = new InMemorySubjectMetadataService();
        GroupService storageGroupService = new InMemoryGroupService();
        manualPermissionNodeRegistry = PermissionNodeRegistries.inMemory();
        permissionResolver = new PermissionResolver(storagePermissionService, storageGroupService);
        permissionService = PermissionServices.observing(storagePermissionService, permissionResolver::invalidateSubject);
        groupService = GroupServices.observing(storageGroupService, new GroupChangeListener() {

            @Override
            public void subjectGroupsChanged(UUID subjectId) {
                permissionResolver.invalidateSubject(subjectId);
            }

            @Override
            public void groupsChanged() {
                permissionResolver.invalidateAll();
            }
        });
        environment = new TestEnvironment(permissionService, subjectMetadataService, groupService, manualPermissionNodeRegistry, permissionResolver);
        backupStore = SqliteStore.open(temporaryDirectory.resolve("database.db"), SqliteDependencyMode.ANY_VISIBLE);
        environment.setStorageBackupService(StorageBackupService.forDatabase(temporaryDirectory.resolve("backups"), backupStore.databaseFile(), backupStore,
                ClutchPermsConfig.defaults().backups().retentionLimit()));
        environment.addOnlineSubject("Target", TARGET_ID);
        dispatcher = new CommandDispatcher<>();
        ClutchPermsCommands.ROOT_LITERALS.forEach(rootLiteral -> dispatcher.getRoot().addChild(ClutchPermsCommands.create(environment, rootLiteral)));
    }

    @AfterEach
    void tearDown() {
        if (backupStore != null) {
            backupStore.close();
        }
    }

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

        dispatcher.execute("clutchperms help 5", console);

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

        assertEquals(List.of("ClutchPerms commands (page 1/15):", "/clutchperms help [page]", "/clutchperms status", "/clutchperms reload", "Page 1/15 | Next >"),
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
        assertEquals(List.of("Page 99 is out of range.", "Available pages: 1-7.", "Try one:", "  /clutchperms help 7"), outOfRange.messages());
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
        assertTrue(console.messages().contains("Command page sizes: help 4, lists 5."));
        assertTrue(console.messages().contains("Chat formatting: disabled."));
    }

    /**
     * Confirms config list and get show active runtime config values.
     *
     * @throws CommandSyntaxException when command execution fails unexpectedly
     */
    @Test
    void configCommandsListAndGetValues() throws CommandSyntaxException {
        environment.setConfig(new ClutchPermsConfig(new ClutchPermsBackupConfig(3), new ClutchPermsCommandConfig(4, 5), new ClutchPermsChatConfig(false)));
        TestSource console = TestSource.console();

        dispatcher.execute("clutchperms config", console);
        dispatcher.execute("clutchperms config get commands.helpPageSize", console);
        dispatcher.execute("clutchperms config get chat.enabled", console);

        assertEquals(List.of("ClutchPerms config:", "backups.retentionLimit = 3 (newest database backups kept; range 1-1000)",
                "commands.helpPageSize = 4 (command rows shown per help page; range 1-50)", "commands.resultPageSize = 5 (rows shown per list-result page; range 1-50)",
                "chat.enabled = false (prefix and suffix chat formatting; values true/false or on/off)", "commands.helpPageSize = 4 (command rows shown per help page; range 1-50)",
                "chat.enabled = false (prefix and suffix chat formatting; values true/false or on/off)"), console.messages());
    }

    /**
     * Confirms config set applies immediately to active command behavior.
     *
     * @throws CommandSyntaxException when command execution fails unexpectedly
     */
    @Test
    void configSetUpdatesValuesAndReloadsRuntimeConfig() throws CommandSyntaxException {
        TestSource console = TestSource.console();

        dispatcher.execute("clutchperms config set backups.retentionLimit 3", console);
        dispatcher.execute("clutchperms config set commands.helpPageSize 3", console);
        dispatcher.execute("clutchperms config set commands.resultPageSize 2", console);
        dispatcher.execute("clutchperms config set chat.enabled off", console);

        assertEquals(new ClutchPermsConfig(new ClutchPermsBackupConfig(3), new ClutchPermsCommandConfig(3, 2), new ClutchPermsChatConfig(false)), environment.config());
        assertEquals(4, environment.configUpdates());
        assertEquals(4, environment.runtimeRefreshes());
        assertTrue(console.messages().contains("Updated config backups.retentionLimit: 10 -> 3. Runtime reloaded."));
        assertTrue(console.messages().contains("Updated config commands.helpPageSize: 7 -> 3. Runtime reloaded."));
        assertTrue(console.messages().contains("Updated config commands.resultPageSize: 8 -> 2. Runtime reloaded."));
        assertTrue(console.messages().contains("Updated config chat.enabled: true -> false. Runtime reloaded."));

        TestSource help = TestSource.console();
        dispatcher.execute("clutchperms", help);
        assertEquals(List.of("ClutchPerms commands (page 1/15):", "/clutchperms help [page]", "/clutchperms status", "/clutchperms reload", "Page 1/15 | Next >"), help.messages());

        permissionService.setPermission(TARGET_ID, "example.01", PermissionValue.TRUE);
        permissionService.setPermission(TARGET_ID, "example.02", PermissionValue.TRUE);
        permissionService.setPermission(TARGET_ID, "example.03", PermissionValue.TRUE);
        TestSource list = TestSource.console();
        dispatcher.execute("clutchperms user Target list 2", list);
        assertEquals(List.of("Permissions for Target (00000000-0000-0000-0000-000000000002) (page 2/2):", "  example.03=TRUE", "< Prev | Page 2/2"), list.messages());
    }

    /**
     * Confirms config reset supports one key and all keys.
     *
     * @throws CommandSyntaxException when command execution fails unexpectedly
     */
    @Test
    void configResetRestoresDefaults() throws CommandSyntaxException {
        environment.setConfig(new ClutchPermsConfig(new ClutchPermsBackupConfig(3), new ClutchPermsCommandConfig(4, 5), new ClutchPermsChatConfig(false)));
        TestSource console = TestSource.console();

        dispatcher.execute("clutchperms config reset backups.retentionLimit", console);
        dispatcher.execute("clutchperms config reset all", console);

        assertEquals(ClutchPermsConfig.defaults(), environment.config());
        assertEquals(2, environment.configUpdates());
        assertEquals(2, environment.runtimeRefreshes());
        assertEquals(List.of("Reset config backups.retentionLimit: 3 -> 10. Runtime reloaded.", "Reset all config values to defaults. Runtime reloaded."), console.messages());
    }

    /**
     * Confirms same-value config changes succeed without writing or refreshing runtime state.
     *
     * @throws CommandSyntaxException when command execution fails unexpectedly
     */
    @Test
    void configSameValueChangesAvoidReload() throws CommandSyntaxException {
        TestSource console = TestSource.console();

        dispatcher.execute("clutchperms config set backups.retentionLimit 10", console);
        dispatcher.execute("clutchperms config set chat.enabled on", console);
        dispatcher.execute("clutchperms config reset all", console);

        assertEquals(ClutchPermsConfig.defaults(), environment.config());
        assertEquals(0, environment.configUpdates());
        assertEquals(0, environment.runtimeRefreshes());
        assertEquals(List.of("Config backups.retentionLimit is already 10.", "Config chat.enabled is already true.", "Config already matches defaults."), console.messages());
    }

    /**
     * Confirms config command failures are styled and do not apply changes.
     */
    @Test
    void configCommandsRejectBadKeysValuesAndUpdateFailures() {
        TestSource console = TestSource.console();

        assertCommandFails("clutchperms config get commands.help", console, "Unknown config key: commands.help");
        assertCommandFails("clutchperms config set commands.helpPageSize nope", console, "Invalid config value for commands.helpPageSize: nope");
        assertCommandFails("clutchperms config set commands.helpPageSize 51", console, "commands.helpPageSize must be an integer between 1 and 50.");
        assertCommandFails("clutchperms config set chat.enabled maybe", console, "chat.enabled must be true/false or on/off.");

        environment.failConfigUpdate(new PermissionStorageException("disk blocked"));
        assertCommandFails("clutchperms config set backups.retentionLimit 3", console, "Config operation failed: disk blocked");

        assertEquals(ClutchPermsConfig.defaults(), environment.config());
        assertEquals(0, environment.configUpdates());
        assertEquals(0, environment.runtimeRefreshes());
    }

    /**
     * Confirms config view, set, and reset use separate command permissions.
     *
     * @throws CommandSyntaxException when command execution fails unexpectedly
     */
    @Test
    void configCommandsUseGranularPermissions() throws CommandSyntaxException {
        TestSource player = TestSource.player(ADMIN_ID);

        assertCommandFails("clutchperms config list", player, "You do not have permission to use ClutchPerms commands.");
        permissionService.setPermission(ADMIN_ID, PermissionNodes.ADMIN_CONFIG_VIEW, PermissionValue.TRUE);
        assertEquals(1, dispatcher.execute("clutchperms config list", player));
        assertCommandFails("clutchperms config set backups.retentionLimit 3", player, "You do not have permission to use ClutchPerms commands.");

        permissionService.setPermission(ADMIN_ID, PermissionNodes.ADMIN_CONFIG_SET, PermissionValue.TRUE);
        assertEquals(1, dispatcher.execute("clutchperms config set backups.retentionLimit 3", player));
        assertCommandFails("clutchperms config reset backups.retentionLimit", player, "You do not have permission to use ClutchPerms commands.");

        permissionService.setPermission(ADMIN_ID, PermissionNodes.ADMIN_CONFIG_RESET, PermissionValue.TRUE);
        assertEquals(1, dispatcher.execute("clutchperms config reset backups.retentionLimit", player));
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

        assertEquals(PermissionValue.TRUE, permissionFromDatabase("example.restore"));
        assertEquals(1, environment.reloads());
        assertEquals(1, environment.runtimeRefreshes());
        assertEquals(List.of("Restored database from backup database-20260424-120000000.db."), console.messages());
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
     */
    @Test
    void backupRestoreFailureRollsBackDatabaseAndDoesNotRefreshRuntimeState() throws IOException {
        PermissionServices.sqlite(backupStore).setPermission(TARGET_ID, "example.restore", PermissionValue.FALSE);
        writeBackup("database-20260424-120000000.db", "example.restore");
        environment.failReload(new PermissionStorageException("bad restored database"));
        TestSource console = TestSource.console();

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

        assertCommandFails("clutchperms backup list", player, "You do not have permission to use ClutchPerms commands.");
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

    /**
     * Confirms a console source can bootstrap direct permission assignments.
     *
     * @throws CommandSyntaxException when command execution fails unexpectedly
     */
    @Test
    void consoleCanSetGetListAndClearPermissions() throws CommandSyntaxException {
        TestSource console = TestSource.console();

        dispatcher.execute("clutchperms user Target set example.node true", console);
        dispatcher.execute("clutchperms user Target get example.node", console);
        dispatcher.execute("clutchperms user Target list", console);
        dispatcher.execute("clutchperms user Target clear example.node", console);
        dispatcher.execute("clutchperms user Target list", console);

        assertEquals(PermissionValue.UNSET, permissionService.getPermission(TARGET_ID, "example.node"));
        assertEquals(
                List.of("Set example.node for Target (00000000-0000-0000-0000-000000000002) to TRUE.", "Target (00000000-0000-0000-0000-000000000002) has example.node = TRUE.",
                        "Permissions for Target (00000000-0000-0000-0000-000000000002) (page 1/1):", "  example.node=TRUE",
                        "Cleared example.node for Target (00000000-0000-0000-0000-000000000002).", "No permissions set for Target (00000000-0000-0000-0000-000000000002)."),
                console.messages());
    }

    /**
     * Confirms bulk clear commands remove only direct permission assignments.
     *
     * @throws CommandSyntaxException when command execution fails unexpectedly
     */
    @Test
    void consoleCanBulkClearUserAndGroupPermissions() throws CommandSyntaxException {
        permissionService.setPermission(TARGET_ID, "example.node", PermissionValue.TRUE);
        permissionService.setPermission(TARGET_ID, "example.*", PermissionValue.FALSE);
        subjectMetadataService.setSubjectPrefix(TARGET_ID, DisplayText.parse("&a[Target]"));
        groupService.createGroup("staff");
        groupService.setGroupPermission("staff", "group.node", PermissionValue.TRUE);
        groupService.setGroupPermission("staff", "group.*", PermissionValue.FALSE);
        groupService.setGroupPrefix("staff", DisplayText.parse("&7[Staff]"));
        groupService.addSubjectGroup(TARGET_ID, "staff");
        TestSource console = TestSource.console();

        dispatcher.execute("clutchperms user Target clear-all", console);
        dispatcher.execute("clutchperms group staff clear-all", console);

        assertEquals(Map.of(), permissionService.getPermissions(TARGET_ID));
        assertEquals("&a[Target]", subjectMetadataService.getSubjectDisplay(TARGET_ID).prefix().orElseThrow().rawText());
        assertEquals(Map.of(), groupService.getGroupPermissions("staff"));
        assertEquals("&7[Staff]", groupService.getGroupDisplay("staff").prefix().orElseThrow().rawText());
        assertEquals(Set.of("staff"), groupService.getSubjectGroups(TARGET_ID));
        assertEquals(List.of("Cleared 2 direct permissions for Target (00000000-0000-0000-0000-000000000002).", "Cleared 2 direct permissions for group staff."),
                console.messages());
    }

    /**
     * Confirms bulk clear no-ops reuse the existing empty-permissions feedback.
     *
     * @throws CommandSyntaxException when command execution fails unexpectedly
     */
    @Test
    void bulkClearReportsNoDirectPermissionsWhenAlreadyEmpty() throws CommandSyntaxException {
        groupService.createGroup("staff");
        TestSource console = TestSource.console();

        dispatcher.execute("clutchperms user Target clear-all", console);
        dispatcher.execute("clutchperms group staff clear-all", console);

        assertEquals(List.of("No permissions set for Target (00000000-0000-0000-0000-000000000002).", "No permissions set for group staff."), console.messages());
    }

    /**
     * Confirms default group permissions can be bulk-cleared without removing the built-in group.
     *
     * @throws CommandSyntaxException when command execution fails unexpectedly
     */
    @Test
    void bulkClearSupportsDefaultGroupPermissions() throws CommandSyntaxException {
        groupService.setGroupPermission(GroupService.DEFAULT_GROUP, "default.node", PermissionValue.FALSE);
        groupService.setGroupPermission(GroupService.DEFAULT_GROUP, "default.*", PermissionValue.TRUE);
        TestSource console = TestSource.console();

        dispatcher.execute("clutchperms group default clear-all", console);

        assertTrue(groupService.hasGroup(GroupService.DEFAULT_GROUP));
        assertEquals(Map.of(), groupService.getGroupPermissions(GroupService.DEFAULT_GROUP));
        assertEquals(List.of("Cleared 2 direct permissions for group default."), console.messages());
    }

    /**
     * Confirms user and group display commands manage prefixes and suffixes with effective feedback.
     *
     * @throws CommandSyntaxException when command execution fails unexpectedly
     */
    @Test
    void consoleCanManageUserAndGroupDisplayValues() throws CommandSyntaxException {
        TestSource console = TestSource.console();

        dispatcher.execute("clutchperms group staff create", console);
        dispatcher.execute("clutchperms group staff prefix set &7[Staff]", console);
        dispatcher.execute("clutchperms group staff suffix set &f*", console);
        dispatcher.execute("clutchperms user Target group add staff", console);
        dispatcher.execute("clutchperms user Target prefix get", console);
        dispatcher.execute("clutchperms user Target prefix set &c[Admin]", console);
        dispatcher.execute("clutchperms user Target suffix set &e!", console);
        dispatcher.execute("clutchperms user Target prefix get", console);
        dispatcher.execute("clutchperms user Target list", console);
        dispatcher.execute("clutchperms group staff list", console);
        dispatcher.execute("clutchperms user Target prefix clear", console);
        dispatcher.execute("clutchperms user Target prefix get", console);
        dispatcher.execute("clutchperms group staff suffix clear", console);
        dispatcher.execute("clutchperms group staff suffix get", console);

        assertTrue(subjectMetadataService.getSubjectDisplay(TARGET_ID).prefix().isEmpty());
        assertEquals("&e!", subjectMetadataService.getSubjectDisplay(TARGET_ID).suffix().orElseThrow().rawText());
        assertEquals("&7[Staff]", groupService.getGroupDisplay("staff").prefix().orElseThrow().rawText());
        assertTrue(console.messages().contains("Target (00000000-0000-0000-0000-000000000002) direct prefix is unset."));
        assertTrue(console.messages().contains("Target (00000000-0000-0000-0000-000000000002) effective prefix = &7[Staff] from group staff."));
        assertTrue(console.messages().contains("Target (00000000-0000-0000-0000-000000000002) direct prefix = &c[Admin]."));
        assertTrue(console.messages().contains("Target (00000000-0000-0000-0000-000000000002) effective prefix = &c[Admin] from direct."));
        assertMessageContains(console, "  direct prefix &c[Admin]");
        assertMessageContains(console, "  effective suffix &e! from direct");
        assertMessageContains(console, "  prefix &7[Staff]");
        assertMessageContains(console, "  suffix &f*");
        assertTrue(console.messages().contains("Group staff suffix is unset."));
    }

    /**
     * Confirms user info summarizes identity, metadata, permissions, groups, and display values.
     *
     * @throws CommandSyntaxException when command execution fails unexpectedly
     */
    @Test
    void consoleCanShowUserInfoSummary() throws CommandSyntaxException {
        subjectMetadataService.recordSubject(TARGET_ID, "StoredTarget", FIRST_SEEN);
        permissionService.setPermission(TARGET_ID, "direct.node", PermissionValue.TRUE);
        groupService.createGroup("staff");
        groupService.setGroupPermission("staff", "group.node", PermissionValue.TRUE);
        groupService.addSubjectGroup(TARGET_ID, "staff");
        groupService.setGroupPermission(GroupService.DEFAULT_GROUP, "default.node", PermissionValue.FALSE);
        subjectMetadataService.setSubjectPrefix(TARGET_ID, DisplayText.parse("&a[Target]"));
        groupService.setGroupSuffix("staff", DisplayText.parse("&f*"));
        TestSource console = TestSource.console();

        dispatcher.execute("clutchperms user Target info", console);

        assertEquals(List.of("User Target (00000000-0000-0000-0000-000000000002):", "  subject Target (00000000-0000-0000-0000-000000000002)",
                "  stored last-known name StoredTarget, last seen 2026-04-24T12:00:00Z", "  direct permissions 1", "  effective permissions 3",
                "  groups default (implicit), staff", "  direct prefix &a[Target]", "  effective prefix &a[Target] from direct", "  direct suffix unset",
                "  effective suffix &f* from group staff"), console.messages());
        assertSuggests(console.commandMessages().get(3), "/clutchperms user 00000000-0000-0000-0000-000000000002 list");
        assertSuggests(console.commandMessages().get(5), "/clutchperms user 00000000-0000-0000-0000-000000000002 groups");
        assertSuggests(console.commandMessages().get(6), "/clutchperms user 00000000-0000-0000-0000-000000000002 prefix get");
        assertSuggests(console.commandMessages().get(8), "/clutchperms user 00000000-0000-0000-0000-000000000002 suffix get");
    }

    /**
     * Confirms UUID-only user info targets remain usable without stored subject metadata.
     *
     * @throws CommandSyntaxException when command execution fails unexpectedly
     */
    @Test
    void userInfoSupportsUuidOnlyTargets() throws CommandSyntaxException {
        TestSource console = TestSource.console();

        dispatcher.execute("clutchperms user " + SECOND_TARGET_ID + " info", console);

        assertEquals(List.of("User 00000000-0000-0000-0000-000000000004 (00000000-0000-0000-0000-000000000004):",
                "  subject 00000000-0000-0000-0000-000000000004 (00000000-0000-0000-0000-000000000004)", "  stored metadata none", "  direct permissions 0",
                "  effective permissions 0", "  groups default (implicit)", "  direct prefix unset", "  effective prefix unset", "  direct suffix unset",
                "  effective suffix unset"), console.messages());
    }

    /**
     * Confirms group info summarizes permissions, relationships, members, display values, and default status.
     *
     * @throws CommandSyntaxException when command execution fails unexpectedly
     */
    @Test
    void consoleCanShowGroupInfoSummary() throws CommandSyntaxException {
        subjectMetadataService.recordSubject(TARGET_ID, "Target", FIRST_SEEN);
        groupService.createGroup("base");
        groupService.createGroup("staff");
        groupService.createGroup("child");
        groupService.setGroupPermission("staff", "group.node", PermissionValue.TRUE);
        groupService.addGroupParent("staff", "base");
        groupService.addGroupParent("child", "staff");
        groupService.addSubjectGroup(TARGET_ID, "staff");
        groupService.setGroupPrefix("staff", DisplayText.parse("&7[Staff]"));
        TestSource console = TestSource.console();

        dispatcher.execute("clutchperms group staff info", console);

        assertEquals(List.of("Group staff:", "  name staff", "  direct permissions 1", "  parents base", "  child groups child",
                "  explicit members Target (00000000-0000-0000-0000-000000000002)", "  prefix &7[Staff]", "  suffix unset"), console.messages());
        assertSuggests(console.commandMessages().get(2), "/clutchperms group staff list");
        assertSuggests(console.commandMessages().get(3), "/clutchperms group staff parents");
        assertSuggests(console.commandMessages().get(5), "/clutchperms group staff members");
        assertSuggests(console.commandMessages().get(6), "/clutchperms group staff prefix get");
        assertSuggests(console.commandMessages().get(7), "/clutchperms group staff suffix get");
    }

    /**
     * Confirms default group info calls out implicit membership and direct display values.
     *
     * @throws CommandSyntaxException when command execution fails unexpectedly
     */
    @Test
    void groupInfoSummarizesDefaultGroup() throws CommandSyntaxException {
        groupService.setGroupPermission(GroupService.DEFAULT_GROUP, "default.node", PermissionValue.FALSE);
        groupService.setGroupPrefix(GroupService.DEFAULT_GROUP, DisplayText.parse("&8[Default]"));
        TestSource console = TestSource.console();

        dispatcher.execute("clutchperms group default info", console);

        assertEquals(List.of("Group default:", "  name default", "  default group applies implicitly", "  direct permissions 1", "  parents none", "  child groups none",
                "  explicit members none", "  prefix &8[Default]", "  suffix unset"), console.messages());
    }

    /**
     * Confirms info summaries cap long lists deterministically.
     *
     * @throws CommandSyntaxException when command execution fails unexpectedly
     */
    @Test
    void infoSummariesCapLongLists() throws CommandSyntaxException {
        groupService.createGroup("staff");
        for (int index = 1; index <= 6; index++) {
            String suffix = String.format("%02d", index);
            groupService.createGroup("group" + suffix);
            groupService.addSubjectGroup(TARGET_ID, "group" + suffix);
            groupService.createGroup("child" + suffix);
            groupService.addGroupParent("child" + suffix, "staff");
        }
        TestSource userInfo = TestSource.console();
        TestSource groupInfo = TestSource.console();

        dispatcher.execute("clutchperms user Target info", userInfo);
        dispatcher.execute("clutchperms group staff info", groupInfo);

        assertMessageContains(userInfo, "  groups default (implicit), group01, group02, group03, group04, +2 more");
        assertMessageContains(groupInfo, "  child groups child01, child02, child03, child04, child05, +1 more");
    }

    /**
     * Confirms display commands validate ampersand formatting and report usage for incomplete sets.
     *
     * @throws CommandSyntaxException when command execution fails unexpectedly
     */
    @Test
    void displayCommandsRejectInvalidValuesAndShowUsage() throws CommandSyntaxException {
        TestSource missing = TestSource.console();
        TestSource invalid = TestSource.console();

        assertEquals(0, dispatcher.execute("clutchperms user Target prefix set", missing));
        assertEquals(List.of("Missing display text.", "Use ampersand formatting codes like &7, &a, &l, &o, &r, and && for a literal ampersand.", "Try one:",
                "  /clutchperms user Target prefix set <text>"), missing.messages());

        assertCommandFails("clutchperms user Target suffix set &xBad", invalid, "Display operation failed: display text contains invalid formatting code &x");
    }

    /**
     * Confirms players need the ClutchPerms admin node before command execution is allowed.
     */
    @Test
    void playerWithoutAdminPermissionIsDenied() {
        TestSource player = TestSource.player(ADMIN_ID);

        assertCommandFails("clutchperms", player, "You do not have permission to use ClutchPerms commands.");
    }

    /**
     * Confirms the old namespace root no longer grants command access.
     */
    @Test
    void playerWithLegacyAdminPermissionIsDenied() {
        permissionService.setPermission(ADMIN_ID, PermissionNodes.ADMIN, PermissionValue.TRUE);
        TestSource player = TestSource.player(ADMIN_ID);

        assertCommandFails("clutchperms status", player, "You do not have permission to use ClutchPerms commands.");
    }

    /**
     * Confirms one exact command node grants only the matching command.
     *
     * @throws CommandSyntaxException when command execution fails unexpectedly
     */
    @Test
    void playerWithSingleCommandPermissionCanUseOnlyThatCommand() throws CommandSyntaxException {
        permissionService.setPermission(ADMIN_ID, PermissionNodes.ADMIN_STATUS, PermissionValue.TRUE);
        TestSource player = TestSource.player(ADMIN_ID);

        dispatcher.execute("clutchperms status", player);

        assertCommandFails("clutchperms reload", player, "You do not have permission to use ClutchPerms commands.");
    }

    /**
     * Confirms players with the ClutchPerms admin wildcard can mutate permissions.
     *
     * @throws CommandSyntaxException when command execution fails unexpectedly
     */
    @Test
    void playerWithAdminWildcardPermissionCanMutatePermissions() throws CommandSyntaxException {
        permissionService.setPermission(ADMIN_ID, PermissionNodes.ADMIN_ALL, PermissionValue.TRUE);
        TestSource player = TestSource.player(ADMIN_ID);

        dispatcher.execute("clutchperms user Target set example.node false", player);

        assertEquals(PermissionValue.FALSE, permissionService.getPermission(TARGET_ID, "example.node"));
    }

    /**
     * Confirms effective command authorization can come from a group assignment.
     *
     * @throws CommandSyntaxException when command execution fails unexpectedly
     */
    @Test
    void playerWithGroupAdminPermissionCanMutatePermissions() throws CommandSyntaxException {
        groupService.createGroup("staff");
        groupService.setGroupPermission("staff", PermissionNodes.ADMIN_USER_SET, PermissionValue.TRUE);
        groupService.addSubjectGroup(ADMIN_ID, "staff");
        TestSource player = TestSource.player(ADMIN_ID);

        dispatcher.execute("clutchperms user Target set example.node false", player);

        assertEquals(PermissionValue.FALSE, permissionService.getPermission(TARGET_ID, "example.node"));
    }

    /**
     * Confirms effective command authorization can come from an inherited parent group.
     *
     * @throws CommandSyntaxException when command execution fails unexpectedly
     */
    @Test
    void playerWithInheritedGroupAdminPermissionCanMutatePermissions() throws CommandSyntaxException {
        groupService.createGroup("admin");
        groupService.createGroup("staff");
        groupService.setGroupPermission("admin", PermissionNodes.ADMIN_USER_SET, PermissionValue.TRUE);
        groupService.addGroupParent("staff", "admin");
        groupService.addSubjectGroup(ADMIN_ID, "staff");
        TestSource player = TestSource.player(ADMIN_ID);

        dispatcher.execute("clutchperms user Target set example.node false", player);

        assertEquals(PermissionValue.FALSE, permissionService.getPermission(TARGET_ID, "example.node"));
    }

    /**
     * Confirms the exact group rename permission authorizes only the rename command.
     *
     * @throws CommandSyntaxException when command execution fails unexpectedly
     */
    @Test
    void playerWithGroupRenamePermissionCanRenameGroupsOnly() throws CommandSyntaxException {
        groupService.createGroup("staff");
        permissionService.setPermission(ADMIN_ID, PermissionNodes.ADMIN_GROUP_RENAME, PermissionValue.TRUE);
        TestSource player = TestSource.player(ADMIN_ID);

        dispatcher.execute("clutchperms group staff rename moderator", player);

        assertFalse(groupService.hasGroup("staff"));
        assertTrue(groupService.hasGroup("moderator"));
        assertCommandFails("clutchperms group moderator delete", player, "You do not have permission to use ClutchPerms commands.");
    }

    /**
     * Confirms exact info permissions authorize only the matching read-only summary commands.
     *
     * @throws CommandSyntaxException when command execution fails unexpectedly
     */
    @Test
    void playerWithInfoPermissionsCanUseInfoOnly() throws CommandSyntaxException {
        groupService.createGroup("staff");
        TestSource player = TestSource.player(ADMIN_ID);

        assertCommandFails("clutchperms user Target info", player, "You do not have permission to use ClutchPerms commands.");

        permissionService.setPermission(ADMIN_ID, PermissionNodes.ADMIN_USER_INFO, PermissionValue.TRUE);
        dispatcher.execute("clutchperms user Target info", player);

        assertCommandFails("clutchperms user Target list", player, "You do not have permission to use ClutchPerms commands.");
        assertCommandFails("clutchperms group staff info", player, "You do not have permission to use ClutchPerms commands.");

        permissionService.setPermission(ADMIN_ID, PermissionNodes.ADMIN_GROUP_INFO, PermissionValue.TRUE);
        dispatcher.execute("clutchperms group staff info", player);

        assertCommandFails("clutchperms group staff list", player, "You do not have permission to use ClutchPerms commands.");
    }

    /**
     * Confirms exact bulk clear permissions authorize only the matching bulk clear commands.
     *
     * @throws CommandSyntaxException when command execution fails unexpectedly
     */
    @Test
    void playerWithBulkClearPermissionsCanUseBulkClearOnly() throws CommandSyntaxException {
        permissionService.setPermission(TARGET_ID, "example.node", PermissionValue.TRUE);
        groupService.createGroup("staff");
        groupService.setGroupPermission("staff", "group.node", PermissionValue.TRUE);
        TestSource player = TestSource.player(ADMIN_ID);

        assertCommandFails("clutchperms user Target clear-all", player, "You do not have permission to use ClutchPerms commands.");

        permissionService.setPermission(ADMIN_ID, PermissionNodes.ADMIN_USER_CLEAR_ALL, PermissionValue.TRUE);
        dispatcher.execute("clutchperms user Target clear-all", player);

        assertEquals(Map.of(), permissionService.getPermissions(TARGET_ID));
        assertCommandFails("clutchperms user Target clear example.node", player, "You do not have permission to use ClutchPerms commands.");
        assertCommandFails("clutchperms group staff clear-all", player, "You do not have permission to use ClutchPerms commands.");

        permissionService.setPermission(ADMIN_ID, PermissionNodes.ADMIN_GROUP_CLEAR_ALL, PermissionValue.TRUE);
        dispatcher.execute("clutchperms group staff clear-all", player);

        assertEquals(Map.of(), groupService.getGroupPermissions("staff"));
        assertCommandFails("clutchperms group staff clear group.node", player, "You do not have permission to use ClutchPerms commands.");
    }

    /**
     * Confirms exact group member-list permission authorizes only that group member view.
     *
     * @throws CommandSyntaxException when command execution fails unexpectedly
     */
    @Test
    void playerWithGroupMembersPermissionCanListMembersOnly() throws CommandSyntaxException {
        groupService.createGroup("staff");
        groupService.addSubjectGroup(TARGET_ID, "staff");
        TestSource player = TestSource.player(ADMIN_ID);

        assertCommandFails("clutchperms group staff members", player, "You do not have permission to use ClutchPerms commands.");

        permissionService.setPermission(ADMIN_ID, PermissionNodes.ADMIN_GROUP_MEMBERS, PermissionValue.TRUE);
        TestSource permittedPlayer = TestSource.player(ADMIN_ID);
        dispatcher.execute("clutchperms group staff members", permittedPlayer);

        assertEquals(List.of("Members of group staff (page 1/1):", "  00000000-0000-0000-0000-000000000002 (00000000-0000-0000-0000-000000000002)"), permittedPlayer.messages());
        assertCommandFails("clutchperms group staff list", permittedPlayer, "You do not have permission to use ClutchPerms commands.");
        assertCommandFails("clutchperms group staff parents", permittedPlayer, "You do not have permission to use ClutchPerms commands.");
    }

    /**
     * Confirms wildcard admin permissions authorize command execution through the shared resolver.
     *
     * @throws CommandSyntaxException when command execution fails unexpectedly
     */
    @Test
    void playerWithCategoryWildcardCanUseMatchingCommandsOnly() throws CommandSyntaxException {
        permissionService.setPermission(ADMIN_ID, "clutchperms.admin.user.*", PermissionValue.TRUE);
        permissionService.setPermission(TARGET_ID, "example.bulk", PermissionValue.TRUE);
        TestSource player = TestSource.player(ADMIN_ID);

        dispatcher.execute("clutchperms user Target info", player);
        dispatcher.execute("clutchperms user Target set example.node false", player);
        dispatcher.execute("clutchperms user Target clear-all", player);

        assertEquals(Map.of(), permissionService.getPermissions(TARGET_ID));
        assertCommandFails("clutchperms group list", player, "You do not have permission to use ClutchPerms commands.");

        groupService.createGroup("staff");
        groupService.setGroupPermission("staff", "group.bulk", PermissionValue.TRUE);
        permissionService.setPermission(ADMIN_ID, "clutchperms.admin.group.*", PermissionValue.TRUE);
        dispatcher.execute("clutchperms group staff members", player);
        dispatcher.execute("clutchperms group staff clear-all", player);

        assertEquals(Map.of(), groupService.getGroupPermissions("staff"));
    }

    /**
     * Confirms exact denies can override category wildcard grants.
     *
     * @throws CommandSyntaxException when command execution fails unexpectedly
     */
    @Test
    void exactCommandDenyOverridesWildcardGrant() throws CommandSyntaxException {
        permissionService.setPermission(ADMIN_ID, "clutchperms.admin.user.*", PermissionValue.TRUE);
        permissionService.setPermission(ADMIN_ID, PermissionNodes.ADMIN_USER_SET, PermissionValue.FALSE);
        TestSource player = TestSource.player(ADMIN_ID);

        dispatcher.execute("clutchperms user Target get example.node", player);

        assertCommandFails("clutchperms user Target set example.node false", player, "You do not have permission to use ClutchPerms commands.");
    }

    /**
     * Confirms group commands manage group permissions and subject memberships.
     *
     * @throws CommandSyntaxException when command execution fails unexpectedly
     */
    @Test
    void consoleCanManageGroupsAndCheckEffectivePermissions() throws CommandSyntaxException {
        subjectMetadataService.recordSubject(TARGET_ID, "Target", FIRST_SEEN);
        TestSource console = TestSource.console();

        dispatcher.execute("clutchperms group admin create", console);
        dispatcher.execute("clutchperms group admin set example.node true", console);
        dispatcher.execute("clutchperms user Target group add admin", console);
        dispatcher.execute("clutchperms user Target groups", console);
        dispatcher.execute("clutchperms user Target check example.node", console);
        dispatcher.execute("clutchperms group admin list", console);
        dispatcher.execute("clutchperms group admin get example.node", console);
        dispatcher.execute("clutchperms group admin clear example.node", console);
        dispatcher.execute("clutchperms user Target group remove admin", console);
        dispatcher.execute("clutchperms group admin delete", console);

        assertEquals(PermissionValue.UNSET, groupService.getGroups().contains("admin") ? groupService.getGroupPermission("admin", "example.node") : PermissionValue.UNSET);
        assertEquals(
                List.of("Created group admin.", "Set example.node for group admin to TRUE.", "Added Target (00000000-0000-0000-0000-000000000002) to group admin.",
                        "Groups for Target (00000000-0000-0000-0000-000000000002) (page 1/1):", "  admin", "  default (implicit)",
                        "Target (00000000-0000-0000-0000-000000000002) effective example.node = TRUE from group admin.", "Group admin (page 1/1):",
                        "  permission example.node=TRUE", "  member Target (00000000-0000-0000-0000-000000000002)", "Group admin has example.node = TRUE.",
                        "Cleared example.node for group admin.", "Removed Target (00000000-0000-0000-0000-000000000002) from group admin.", "Deleted group admin."),
                console.messages());
    }

    /**
     * Confirms explicit group members can be listed with stored names and UUID-only subjects.
     *
     * @throws CommandSyntaxException when command execution fails unexpectedly
     */
    @Test
    void consoleCanListExplicitGroupMembers() throws CommandSyntaxException {
        environment.setConfig(new ClutchPermsConfig(ClutchPermsBackupConfig.defaults(), new ClutchPermsCommandConfig(7, 2)));
        subjectMetadataService.recordSubject(TARGET_ID, "Target", FIRST_SEEN);
        subjectMetadataService.recordSubject(SECOND_TARGET_ID, "alpha", SECOND_SEEN);
        groupService.createGroup("staff");
        groupService.addSubjectGroup(TARGET_ID, "staff");
        groupService.addSubjectGroup(UUID_NAMED_PLAYER_ID, "staff");
        groupService.addSubjectGroup(SECOND_TARGET_ID, "staff");
        TestSource pageOne = TestSource.console();
        TestSource pageTwo = TestSource.console();

        dispatcher.execute("clutchperms group staff members", pageOne);
        dispatcher.execute("clutchperms group staff members 2", pageTwo);

        assertEquals(List.of("Members of group staff (page 1/2):", "  00000000-0000-0000-0000-000000000003 (00000000-0000-0000-0000-000000000003)",
                "  alpha (00000000-0000-0000-0000-000000000004)", "Page 1/2 | Next >"), pageOne.messages());
        assertEquals(List.of("Members of group staff (page 2/2):", "  Target (00000000-0000-0000-0000-000000000002)", "< Prev | Page 2/2"), pageTwo.messages());
        assertSuggests(pageOne.commandMessages().get(1), "/clutchperms user 00000000-0000-0000-0000-000000000003 list");
        assertSuggests(pageOne.commandMessages().get(2), "/clutchperms user 00000000-0000-0000-0000-000000000004 list");
        assertSuggests(pageTwo.commandMessages().get(1), "/clutchperms user 00000000-0000-0000-0000-000000000002 list");
    }

    /**
     * Confirms empty explicit member lists report direct-membership absence, including default.
     *
     * @throws CommandSyntaxException when command execution fails unexpectedly
     */
    @Test
    void groupMembersReportEmptyExplicitMemberships() throws CommandSyntaxException {
        groupService.createGroup("staff");
        TestSource console = TestSource.console();

        dispatcher.execute("clutchperms group staff members", console);
        dispatcher.execute("clutchperms group default members", console);

        assertEquals(List.of("Group staff has no explicit members.", "Group default has no explicit members."), console.messages());
    }

    /**
     * Confirms group rename command updates memberships and invalidates cached effective permissions.
     *
     * @throws CommandSyntaxException when command execution fails unexpectedly
     */
    @Test
    void consoleCanRenameGroupsAndKeepEffectivePermissions() throws CommandSyntaxException {
        TestSource console = TestSource.console();

        dispatcher.execute("clutchperms group staff create", console);
        dispatcher.execute("clutchperms group staff set staff.node true", console);
        dispatcher.execute("clutchperms user Target group add staff", console);
        assertEquals(PermissionValue.TRUE, permissionResolver.resolve(TARGET_ID, "staff.node").value());
        dispatcher.execute("clutchperms group staff rename moderator", console);
        dispatcher.execute("clutchperms user Target check staff.node", console);

        assertFalse(groupService.hasGroup("staff"));
        assertTrue(groupService.hasGroup("moderator"));
        assertEquals(Set.of("moderator"), groupService.getSubjectGroups(TARGET_ID));
        assertEquals(PermissionValue.TRUE, permissionResolver.resolve(TARGET_ID, "staff.node").value());
        assertEquals(
                List.of("Created group staff.", "Set staff.node for group staff to TRUE.", "Added Target (00000000-0000-0000-0000-000000000002) to group staff.",
                        "Renamed group staff to moderator.", "Target (00000000-0000-0000-0000-000000000002) effective staff.node = TRUE from group moderator."),
                console.messages());
    }

    /**
     * Confirms command mutation and check output support terminal wildcard nodes.
     *
     * @throws CommandSyntaxException when command execution fails unexpectedly
     */
    @Test
    void consoleCanManageWildcardPermissions() throws CommandSyntaxException {
        TestSource console = TestSource.console();

        dispatcher.execute("clutchperms user Target set example.* true", console);
        dispatcher.execute("clutchperms user Target check example.node", console);
        dispatcher.execute("clutchperms user Target list", console);
        dispatcher.execute("clutchperms group wildcard create", console);
        dispatcher.execute("clutchperms group wildcard set other.* false", console);
        dispatcher.execute("clutchperms user Target group add wildcard", console);
        dispatcher.execute("clutchperms user Target check other.node", console);
        dispatcher.execute("clutchperms user Target clear example.*", console);

        assertEquals(PermissionValue.UNSET, permissionService.getPermission(TARGET_ID, "example.*"));
        assertEquals(PermissionValue.FALSE, permissionResolver.resolve(TARGET_ID, "other.node").value());
        assertEquals(List.of("Set example.* for Target (00000000-0000-0000-0000-000000000002) to TRUE.",
                "Target (00000000-0000-0000-0000-000000000002) effective example.node = TRUE from direct via example.*.",
                "Permissions for Target (00000000-0000-0000-0000-000000000002) (page 1/1):", "  example.*=TRUE", "Created group wildcard.",
                "Set other.* for group wildcard to FALSE.", "Added Target (00000000-0000-0000-0000-000000000002) to group wildcard.",
                "Target (00000000-0000-0000-0000-000000000002) effective other.node = FALSE from group wildcard via other.*.",
                "Cleared example.* for Target (00000000-0000-0000-0000-000000000002)."), console.messages());
    }

    /**
     * Confirms command mutations invalidate cached resolver results before later command reads.
     *
     * @throws CommandSyntaxException when command execution fails unexpectedly
     */
    @Test
    void commandMutationsInvalidateResolverCacheBeforeCheckOutput() throws CommandSyntaxException {
        TestSource console = TestSource.console();

        dispatcher.execute("clutchperms user Target set example.cache true", console);
        assertEquals(PermissionValue.TRUE, permissionResolver.resolve(TARGET_ID, "example.cache").value());
        dispatcher.execute("clutchperms user Target set example.cache false", console);
        dispatcher.execute("clutchperms user Target check example.cache", console);

        dispatcher.execute("clutchperms group staff create", console);
        dispatcher.execute("clutchperms group staff set example.groupcache true", console);
        dispatcher.execute("clutchperms user Target group add staff", console);
        assertEquals(PermissionValue.TRUE, permissionResolver.resolve(TARGET_ID, "example.groupcache").value());
        dispatcher.execute("clutchperms group staff set example.groupcache false", console);
        dispatcher.execute("clutchperms user Target check example.groupcache", console);

        assertEquals(PermissionValue.FALSE, permissionResolver.resolve(TARGET_ID, "example.cache").value());
        assertEquals(PermissionValue.FALSE, permissionResolver.resolve(TARGET_ID, "example.groupcache").value());
        assertTrue(console.messages().contains("Target (00000000-0000-0000-0000-000000000002) effective example.cache = FALSE from direct."));
        assertTrue(console.messages().contains("Target (00000000-0000-0000-0000-000000000002) effective example.groupcache = FALSE from group staff."));
    }

    /**
     * Confirms explain reports the winning assignment and ignored matching candidates.
     *
     * @throws CommandSyntaxException when command execution fails unexpectedly
     */
    @Test
    void consoleCanExplainEffectivePermissions() throws CommandSyntaxException {
        permissionService.setPermission(TARGET_ID, "example.*", PermissionValue.TRUE);
        groupService.createGroup("staff");
        groupService.setGroupPermission("staff", "example.node", PermissionValue.FALSE);
        groupService.addSubjectGroup(TARGET_ID, "staff");
        groupService.setGroupPermission("default", "*", PermissionValue.FALSE);
        TestSource console = TestSource.console();

        dispatcher.execute("clutchperms user Target explain example.node", console);

        assertEquals(
                List.of("Resolution for Target (00000000-0000-0000-0000-000000000002) example.node:", "Result: TRUE from direct via example.*.",
                        "Order: direct > explicit groups by depth > default; exact > closest wildcard > broader wildcard > *; FALSE wins same-rank ties.",
                        "Match: direct example.*=TRUE (winner).", "Match: group staff depth 0 example.node=FALSE (ignored).", "Match: default group depth 0 *=FALSE (ignored)."),
                console.messages());
    }

    /**
     * Confirms explain reports unset values without fake candidates.
     *
     * @throws CommandSyntaxException when command execution fails unexpectedly
     */
    @Test
    void consoleCanExplainUnsetPermissions() throws CommandSyntaxException {
        TestSource console = TestSource.console();

        dispatcher.execute("clutchperms user Target explain missing.node", console);

        assertEquals(
                List.of("Resolution for Target (00000000-0000-0000-0000-000000000002) missing.node:", "Result: UNSET.",
                        "Order: direct > explicit groups by depth > default; exact > closest wildcard > broader wildcard > *; FALSE wins same-rank ties.", "Matches: none."),
                console.messages());
    }

    /**
     * Confirms group parent commands manage inherited group links.
     *
     * @throws CommandSyntaxException when command execution fails unexpectedly
     */
    @Test
    void consoleCanManageGroupParentsAndCheckInheritedPermissions() throws CommandSyntaxException {
        TestSource console = TestSource.console();

        dispatcher.execute("clutchperms group base create", console);
        dispatcher.execute("clutchperms group base set example.inherited true", console);
        dispatcher.execute("clutchperms group staff create", console);
        dispatcher.execute("clutchperms group staff parent add base", console);
        dispatcher.execute("clutchperms group staff parents", console);
        dispatcher.execute("clutchperms user Target group add staff", console);
        dispatcher.execute("clutchperms user Target check example.inherited", console);
        dispatcher.execute("clutchperms group staff list", console);
        dispatcher.execute("clutchperms group staff parent remove base", console);
        dispatcher.execute("clutchperms group staff parents", console);

        assertEquals(PermissionValue.UNSET, permissionResolver.resolve(TARGET_ID, "example.inherited").value());
        assertEquals(List.of("Created group base.", "Set example.inherited for group base to TRUE.", "Created group staff.", "Added parent group base to group staff.",
                "Parents of group staff (page 1/1):", "  base", "Added Target (00000000-0000-0000-0000-000000000002) to group staff.",
                "Target (00000000-0000-0000-0000-000000000002) effective example.inherited = TRUE from group base.", "Group staff (page 1/1):", "  parent base",
                "  member 00000000-0000-0000-0000-000000000002 (00000000-0000-0000-0000-000000000002)", "Removed parent group base from group staff.",
                "Group staff has no parent groups."), console.messages());
    }

    /**
     * Confirms parent command failures report invalid inheritance operations.
     *
     * @throws CommandSyntaxException when command setup fails unexpectedly
     */
    @Test
    void parentCommandsRejectInvalidLinks() throws CommandSyntaxException {
        TestSource console = TestSource.console();
        dispatcher.execute("clutchperms group admin create", console);
        dispatcher.execute("clutchperms group staff create", console);
        dispatcher.execute("clutchperms group admin parent add staff", console);

        assertCommandFails("clutchperms group admin parent add missing", console, "Unknown parent group: missing");
        assertCommandFails("clutchperms group admin parent add admin", console, "Group operation failed: group cannot inherit itself: admin");
        assertCommandFails("clutchperms group staff parent add admin", console, "Group operation failed: group inheritance cycle detected");
    }

    /**
     * Confirms the default group applies without explicit subject membership.
     *
     * @throws CommandSyntaxException when command execution fails unexpectedly
     */
    @Test
    void defaultGroupAppliesImplicitly() throws CommandSyntaxException {
        TestSource console = TestSource.console();

        dispatcher.execute("clutchperms group default set example.default false", console);
        dispatcher.execute("clutchperms user Target groups", console);
        dispatcher.execute("clutchperms user Target check example.default", console);

        assertEquals(PermissionValue.FALSE, permissionResolver.resolve(TARGET_ID, "example.default").value());
        assertEquals(List.of("Set example.default for group default to FALSE.", "Groups for Target (00000000-0000-0000-0000-000000000002) (page 1/1):", "  default (implicit)",
                "Target (00000000-0000-0000-0000-000000000002) effective example.default = FALSE from default group."), console.messages());
    }

    /**
     * Confirms explicit default group membership is rejected because default applies implicitly.
     */
    @Test
    void defaultGroupCannotBeAssignedExplicitly() {
        TestSource console = TestSource.console();

        assertCommandFails("clutchperms user Target group add default", console, "Group operation failed: default group membership is implicit");
    }

    /**
     * Confirms the built-in default group cannot be deleted.
     */
    @Test
    void defaultGroupCannotBeDeleted() {
        TestSource console = TestSource.console();

        assertCommandFails("clutchperms group default delete", console, "Group operation failed: default group cannot be deleted");
        assertCommandFails("clutchperms group default rename fallback", console, "Group operation failed: default group cannot be renamed");

        assertTrue(groupService.hasGroup("default"));
    }

    /**
     * Confirms group rename rejects destinations that would break group invariants.
     *
     * @throws CommandSyntaxException when command setup fails unexpectedly
     */
    @Test
    void groupRenameRejectsInvalidDestinations() throws CommandSyntaxException {
        TestSource console = TestSource.console();
        dispatcher.execute("clutchperms group staff create", console);
        dispatcher.execute("clutchperms group admin create", console);

        assertCommandFails("clutchperms group staff rename default", console, "Group operation failed: group cannot be renamed to default");
        assertCommandFails("clutchperms group staff rename admin", console, "Group operation failed: group already exists: admin");
        assertCommandFails("clutchperms group staff rename Staff", console, "Group operation failed: group already exists: staff");

        assertTrue(groupService.hasGroup("staff"));
    }

    /**
     * Confirms an exact online name wins before UUID parsing.
     *
     * @throws CommandSyntaxException when command execution fails unexpectedly
     */
    @Test
    void onlinePlayerTargetResolvesBeforeUuidParsing() throws CommandSyntaxException {
        String uuidLookingName = TARGET_ID.toString();
        environment.addOnlineSubject(uuidLookingName, UUID_NAMED_PLAYER_ID);
        TestSource console = TestSource.console();

        dispatcher.execute("clutchperms user " + uuidLookingName + " set example.node true", console);

        assertEquals(PermissionValue.UNSET, permissionService.getPermission(TARGET_ID, "example.node"));
        assertEquals(PermissionValue.TRUE, permissionService.getPermission(UUID_NAMED_PLAYER_ID, "example.node"));
    }

    /**
     * Confirms an exact online name wins before stored subject metadata.
     *
     * @throws CommandSyntaxException when command execution fails unexpectedly
     */
    @Test
    void onlinePlayerTargetResolvesBeforeStoredMetadata() throws CommandSyntaxException {
        subjectMetadataService.recordSubject(SECOND_TARGET_ID, "Target", FIRST_SEEN);
        TestSource console = TestSource.console();

        dispatcher.execute("clutchperms user Target set example.node true", console);

        assertEquals(PermissionValue.TRUE, permissionService.getPermission(TARGET_ID, "example.node"));
        assertEquals(PermissionValue.UNSET, permissionService.getPermission(SECOND_TARGET_ID, "example.node"));
    }

    /**
     * Confirms stored subject metadata names can be used as offline command targets.
     *
     * @throws CommandSyntaxException when command execution fails unexpectedly
     */
    @Test
    void offlineLastKnownNameTargetResolvesBeforeUuidParsing() throws CommandSyntaxException {
        subjectMetadataService.recordSubject(SECOND_TARGET_ID, "OfflineTarget", FIRST_SEEN);
        TestSource console = TestSource.console();

        dispatcher.execute("clutchperms user offlinetarget set example.node true", console);

        assertEquals(PermissionValue.TRUE, permissionService.getPermission(SECOND_TARGET_ID, "example.node"));
        assertEquals(List.of("Set example.node for OfflineTarget (00000000-0000-0000-0000-000000000004) to TRUE."), console.messages());
    }

    /**
     * Confirms UUID targets use stored metadata names in command feedback when available.
     *
     * @throws CommandSyntaxException when command execution fails unexpectedly
     */
    @Test
    void uuidTargetUsesLastKnownNameInCommandFeedback() throws CommandSyntaxException {
        subjectMetadataService.recordSubject(SECOND_TARGET_ID, "OfflineTarget", FIRST_SEEN);
        TestSource console = TestSource.console();

        dispatcher.execute("clutchperms user " + SECOND_TARGET_ID + " set example.node true", console);
        dispatcher.execute("clutchperms user " + SECOND_TARGET_ID + " get example.node", console);

        assertEquals(List.of("Set example.node for OfflineTarget (00000000-0000-0000-0000-000000000004) to TRUE.",
                "OfflineTarget (00000000-0000-0000-0000-000000000004) has example.node = TRUE."), console.messages());
    }

    /**
     * Confirms ambiguous stored subject names fail instead of choosing an arbitrary UUID.
     */
    @Test
    void ambiguousLastKnownNameTargetFails() {
        subjectMetadataService.recordSubject(TARGET_ID, "Duplicate", FIRST_SEEN);
        subjectMetadataService.recordSubject(SECOND_TARGET_ID, "duplicate", SECOND_SEEN);
        TestSource console = TestSource.console();

        assertCommandFails("clutchperms user Duplicate list", console, "Ambiguous known user: Duplicate");

        assertMessageContains(console, "More than one stored subject matches Duplicate.");
        assertMessageContains(console, "  Duplicate (00000000-0000-0000-0000-000000000002, last seen 2026-04-24T12:00:00Z)");
        assertMessageContains(console, "  duplicate (00000000-0000-0000-0000-000000000004, last seen 2026-04-24T13:00:00Z)");
    }

    /**
     * Confirms unknown user targets show close online and stored-name matches.
     */
    @Test
    void unknownUserTargetSuggestsClosestOnlineAndStoredMatches() {
        subjectMetadataService.recordSubject(SECOND_TARGET_ID, "Targe", FIRST_SEEN);
        TestSource console = TestSource.console();

        assertCommandFails("clutchperms user Targt list", console, "Unknown user target: Targt");

        assertMessageContains(console, "Use an exact online name, stored last-known name, or UUID.");
        assertMessageContains(console, "Closest online players: Target");
        assertMessageContains(console, "Closest known users: Targe (00000000-0000-0000-0000-000000000004, last seen 2026-04-24T12:00:00Z)");
    }

    /**
     * Confirms info commands reuse existing closest-match feedback for unknown targets.
     */
    @Test
    void infoCommandsUseExistingClosestMatchFeedback() {
        groupService.createGroup("staff");
        TestSource console = TestSource.console();

        assertCommandFails("clutchperms user Targt info", console, "Unknown user target: Targt");
        assertMessageContains(console, "Closest online players: Target");

        assertCommandFails("clutchperms group staf info", console, "Unknown group: staf");
        assertMessageContains(console, "Closest groups: staff");
    }

    /**
     * Confirms bulk clear commands reuse existing closest-match feedback for unknown targets.
     */
    @Test
    void bulkClearCommandsUseExistingClosestMatchFeedback() {
        groupService.createGroup("staff");
        TestSource console = TestSource.console();

        assertCommandFails("clutchperms user Targt clear-all", console, "Unknown user target: Targt");
        assertMessageContains(console, "Closest online players: Target");

        assertCommandFails("clutchperms group staf clear-all", console, "Unknown group: staf");
        assertMessageContains(console, "Closest groups: staff");
    }

    /**
     * Confirms unknown user targets without close matches suggest the users search command.
     */
    @Test
    void unknownUserTargetWithoutMatchesSuggestsSearch() {
        TestSource console = TestSource.console();

        assertCommandFails("clutchperms user CompletelyMissing list", console, "Unknown user target: CompletelyMissing");

        assertMessageContains(console, "No close user matches.");
        assertMessageContains(console, "  /clutchperms users search CompletelyMissing");
    }

    /**
     * Confirms user target suggestions include online players and stored last-known names.
     */
    @Test
    void userTargetSuggestionsIncludeOnlineAndStoredNames() {
        environment.addOnlineSubject("Builder", UUID_NAMED_PLAYER_ID);
        subjectMetadataService.recordSubject(SECOND_TARGET_ID, "OfflineTarget", FIRST_SEEN);

        assertEquals(List.of("Builder", "OfflineTarget", "Target"), suggestionTexts("clutchperms user "));
    }

    /**
     * Confirms user target suggestions filter by typed prefix without requiring exact casing.
     */
    @Test
    void userTargetSuggestionsFilterByTypedPrefixCaseInsensitively() {
        environment.addOnlineSubject("Operator", UUID_NAMED_PLAYER_ID);
        subjectMetadataService.recordSubject(SECOND_TARGET_ID, "OfflineTarget", FIRST_SEEN);

        assertEquals(List.of("OfflineTarget", "Operator"), suggestionTexts("clutchperms user o"));
    }

    /**
     * Confirms user target suggestions are stable and do not repeat the exact same name.
     */
    @Test
    void userTargetSuggestionsAreDeterministicAndAvoidExactDuplicates() {
        environment.addOnlineSubject("Alpha", UUID_NAMED_PLAYER_ID);
        subjectMetadataService.recordSubject(TARGET_ID, "Target", FIRST_SEEN);
        subjectMetadataService.recordSubject(SECOND_TARGET_ID, "alpha", SECOND_SEEN);

        assertEquals(List.of("Alpha", "alpha"), suggestionTexts("clutchperms user a"));
        assertEquals(List.of("Target"), suggestionTexts("clutchperms user t"));
    }

    /**
     * Confirms unknown group targets show close group matches.
     */
    @Test
    void unknownGroupTargetSuggestsClosestGroup() {
        groupService.createGroup("staff");
        groupService.createGroup("builder");
        TestSource console = TestSource.console();

        assertCommandFails("clutchperms group staf list", console, "Unknown group: staf");
        assertCommandFails("clutchperms group staf members", console, "Unknown group: staf");

        assertMessageContains(console, "Closest groups: staff");
    }

    /**
     * Confirms rename pre-checks unknown source groups and reports close matches.
     */
    @Test
    void unknownGroupRenameSourceSuggestsClosestGroup() {
        groupService.createGroup("staff");
        TestSource console = TestSource.console();

        assertCommandFails("clutchperms group staf rename moderator", console, "Unknown group: staf");

        assertMessageContains(console, "Closest groups: staff");
    }

    /**
     * Confirms unknown group targets suggest listing groups when there are no close matches.
     */
    @Test
    void unknownGroupTargetWithoutCloseMatchesSuggestsList() {
        TestSource console = TestSource.console();

        assertCommandFails("clutchperms group missing list", console, "Unknown group: missing");

        assertMessageContains(console, "No close group matches.");
        assertMessageContains(console, "  /clutchperms group list");
    }

    /**
     * Confirms user group membership commands pre-check unknown group targets.
     */
    @Test
    void unknownUserGroupTargetSuggestsClosestGroup() {
        groupService.createGroup("staff");
        TestSource console = TestSource.console();

        assertCommandFails("clutchperms user Target group add staf", console, "Unknown group: staf");
        assertCommandFails("clutchperms user Target group remove staf", console, "Unknown group: staf");

        assertMessageContains(console, "Closest groups: staff");
    }

    /**
     * Confirms add suggestions exclude the implicit default group and groups already assigned to the target.
     */
    @Test
    void userGroupAddSuggestionsExcludeDefaultAndAssignedGroups() {
        groupService.createGroup("staff");
        groupService.createGroup("builder");
        groupService.addSubjectGroup(TARGET_ID, "staff");

        assertEquals(List.of("builder"), suggestionTexts("clutchperms user Target group add "));
    }

    /**
     * Confirms remove suggestions include only direct target memberships.
     */
    @Test
    void userGroupRemoveSuggestionsIncludeOnlyDirectMemberships() {
        groupService.createGroup("staff");
        groupService.createGroup("builder");
        groupService.addSubjectGroup(TARGET_ID, "staff");

        assertEquals(List.of("staff"), suggestionTexts("clutchperms user Target group remove "));
    }

    /**
     * Confirms remove suggestions do not include the implicit default group.
     */
    @Test
    void userGroupRemoveSuggestionsExcludeImplicitDefaultGroup() {
        groupService.createGroup("staff");

        assertEquals(List.of(), suggestionTexts("clutchperms user Target group remove "));
    }

    /**
     * Confirms unresolved targets fall back to broad add suggestions and empty remove suggestions.
     */
    @Test
    void userGroupSuggestionsHandleUnresolvedTargets() {
        groupService.createGroup("staff");
        subjectMetadataService.recordSubject(TARGET_ID, "Ambiguous", FIRST_SEEN);
        subjectMetadataService.recordSubject(SECOND_TARGET_ID, "ambiguous", SECOND_SEEN);

        assertEquals(List.of("staff"), suggestionTexts("clutchperms user Missing group add "));
        assertEquals(List.of(), suggestionTexts("clutchperms user Missing group remove "));
        assertEquals(List.of("staff"), suggestionTexts("clutchperms user Ambiguous group add "));
        assertEquals(List.of(), suggestionTexts("clutchperms user Ambiguous group remove "));
    }

    /**
     * Confirms rename destination is treated as a new name rather than suggesting existing groups.
     */
    @Test
    void groupRenameDestinationHasNoExistingGroupSuggestions() {
        groupService.createGroup("staff");
        groupService.createGroup("builder");

        assertEquals(List.of(), suggestionTexts("clutchperms group staff rename "));
    }

    /**
     * Confirms group parent commands identify an unknown parent separately from the child group.
     */
    @Test
    void unknownParentGroupTargetSuggestsClosestGroup() {
        groupService.createGroup("admin");
        groupService.createGroup("staff");
        TestSource console = TestSource.console();

        assertCommandFails("clutchperms group admin parent add staf", console, "Unknown parent group: staf");

        assertMessageContains(console, "Closest groups: staff");
    }

    /**
     * Confirms unknown targets and malformed command input fail through Brigadier.
     */
    @Test
    void invalidTargetAndNodeFailExecution() {
        TestSource console = TestSource.console();

        assertCommandFails("clutchperms user Missing list", console, "Unknown user target: Missing");
        assertCommandFails("clutchperms user " + TARGET_ID + " get bad node", console, "Invalid permission node: bad node");
        assertCommandFails("clutchperms user Target set example* true", console, "Invalid permission node: example*");
        groupService.createGroup("staff");
        assertCommandFails("clutchperms group staff set example.*.node true", console, "Invalid permission node: example.*.node");
    }

    /**
     * Confirms direct permission storage failures return styled command feedback instead of raw exceptions.
     *
     * @throws CommandSyntaxException if command failures are not handled by the command layer
     */
    @Test
    void directPermissionMutationFailuresReturnStyledErrors() throws CommandSyntaxException {
        permissionService = new FailingMutationPermissionService(new PermissionStorageException("save blocked"));
        permissionResolver = new PermissionResolver(permissionService, groupService);
        environment = new TestEnvironment(permissionService, subjectMetadataService, groupService, manualPermissionNodeRegistry, permissionResolver);
        environment.addOnlineSubject("Target", TARGET_ID);
        dispatcher = new CommandDispatcher<>();
        ClutchPermsCommands.ROOT_LITERALS.forEach(rootLiteral -> dispatcher.getRoot().addChild(ClutchPermsCommands.create(environment, rootLiteral)));
        TestSource console = TestSource.console();

        assertEquals(0, dispatcher.execute("clutchperms user Target set example.node true", console));
        assertEquals(List.of("Permission operation failed: save blocked"), console.messages());

        console.messages().clear();
        assertEquals(0, dispatcher.execute("clutchperms user Target clear example.node", console));
        assertEquals(List.of("Permission operation failed: save blocked"), console.messages());

        console.messages().clear();
        assertEquals(0, dispatcher.execute("clutchperms user Target clear-all", console));
        assertEquals(List.of("Permission operation failed: save blocked"), console.messages());
    }

    /**
     * Confirms node suggestions include built-in nodes and explicit assignments for the selected target.
     */
    @Test
    void nodeSuggestionsIncludeBuiltInAndTargetAssignments() {
        manualPermissionNodeRegistry.addNode("known.node");
        permissionService.setPermission(TARGET_ID, "example.node", PermissionValue.TRUE);
        permissionService.setPermission(TARGET_ID, "example.*", PermissionValue.FALSE);
        permissionService.setPermission(TARGET_ID, "Zeta.Node", PermissionValue.FALSE);
        permissionService.setPermission(UUID_NAMED_PLAYER_ID, "other.node", PermissionValue.TRUE);

        assertEquals(List.of("example.*", "example.node"), suggestionTexts("clutchperms user Target get ex"));
        assertTrue(suggestionTexts("clutchperms user Target get clutchperms.admin.user.").contains(PermissionNodes.ADMIN_USER_SET));
        assertTrue(suggestionTexts("clutchperms user Target get clutchperms.admin.").contains(PermissionNodes.ADMIN_ALL));
    }

    /**
     * Confirms node suggestions are filtered by the partial node text already typed by the command source.
     */
    @Test
    void nodeSuggestionsFilterByPartialInput() {
        permissionService.setPermission(TARGET_ID, "example.node", PermissionValue.TRUE);
        permissionService.setPermission(TARGET_ID, "other.node", PermissionValue.TRUE);

        assertEquals(List.of("example.node"), suggestionTexts("clutchperms user Target clear ex"));
    }

    /**
     * Confirms node suggestions include effective group and default group assignments for the selected target.
     */
    @Test
    void nodeSuggestionsIncludeEffectiveGroupAssignments() {
        groupService.createGroup("staff");
        groupService.setGroupPermission("staff", "example.group", PermissionValue.TRUE);
        groupService.createGroup("base");
        groupService.setGroupPermission("base", "example.inherited", PermissionValue.TRUE);
        groupService.addGroupParent("staff", "base");
        groupService.addSubjectGroup(TARGET_ID, "staff");
        groupService.setGroupPermission("default", "default.node", PermissionValue.TRUE);

        assertEquals(List.of("example.group", "example.inherited"), suggestionTexts("clutchperms user Target check ex"));
    }

    /**
     * Confirms group permission commands suggest permissions already assigned to that group.
     */
    @Test
    void nodeSuggestionsIncludeSelectedGroupAssignments() {
        groupService.createGroup("staff");
        groupService.setGroupPermission("staff", "example.group", PermissionValue.TRUE);

        assertEquals(List.of("example.group"), suggestionTexts("clutchperms group staff get ex"));
    }

    /**
     * Confirms parent suggestions include valid groups and exclude the current group and existing parents.
     */
    @Test
    void parentSuggestionsIncludeValidGroups() {
        groupService.createGroup("admin");
        groupService.createGroup("staff");
        groupService.addGroupParent("admin", "staff");

        assertEquals(List.of("default"), suggestionTexts("clutchperms group admin parent add "));
    }

    /**
     * Confirms the users list command reports an empty metadata store clearly.
     *
     * @throws CommandSyntaxException when command execution fails unexpectedly
     */
    @Test
    void usersListReportsNoKnownUsers() throws CommandSyntaxException {
        TestSource console = TestSource.console();

        dispatcher.execute("clutchperms users list", console);

        assertEquals(List.of("No known users."), console.messages());
    }

    /**
     * Confirms the users list command reports known users in stable name order.
     *
     * @throws CommandSyntaxException when command execution fails unexpectedly
     */
    @Test
    void usersListReportsKnownUsers() throws CommandSyntaxException {
        subjectMetadataService.recordSubject(TARGET_ID, "Zed", SECOND_SEEN);
        subjectMetadataService.recordSubject(SECOND_TARGET_ID, "Alpha", FIRST_SEEN);
        TestSource console = TestSource.console();

        dispatcher.execute("clutchperms users list", console);

        assertEquals(List.of("Known users (page 1/1):", "  Alpha (00000000-0000-0000-0000-000000000004, last seen 2026-04-24T12:00:00Z)",
                "  Zed (00000000-0000-0000-0000-000000000002, last seen 2026-04-24T13:00:00Z)"), console.messages());
    }

    /**
     * Confirms the users search command matches names case-insensitively.
     *
     * @throws CommandSyntaxException when command execution fails unexpectedly
     */
    @Test
    void usersSearchReportsMatchingKnownUsers() throws CommandSyntaxException {
        subjectMetadataService.recordSubject(TARGET_ID, "Target", FIRST_SEEN);
        subjectMetadataService.recordSubject(SECOND_TARGET_ID, "Other", SECOND_SEEN);
        TestSource console = TestSource.console();

        dispatcher.execute("clutchperms users search tar", console);

        assertEquals(List.of("Matched users (page 1/1):", "  Target (00000000-0000-0000-0000-000000000002, last seen 2026-04-24T12:00:00Z)"), console.messages());
    }

    /**
     * Confirms the users search command reports no-match searches clearly.
     *
     * @throws CommandSyntaxException when command execution fails unexpectedly
     */
    @Test
    void usersSearchReportsNoMatches() throws CommandSyntaxException {
        subjectMetadataService.recordSubject(TARGET_ID, "Target", FIRST_SEEN);
        TestSource console = TestSource.console();

        dispatcher.execute("clutchperms users search Missing", console);

        assertEquals(List.of("No users matched Missing."), console.messages());
    }

    /**
     * Confirms list-style commands accept explicit pages and keep row clicks useful.
     *
     * @throws IOException when test backup setup fails
     * @throws CommandSyntaxException when command execution fails unexpectedly
     */
    @Test
    void listCommandsSupportExplicitPages() throws IOException, CommandSyntaxException {
        groupService.createGroup("paged");
        for (int index = 1; index <= 9; index++) {
            String suffix = String.format("%02d", index);
            permissionService.setPermission(TARGET_ID, "example." + suffix, PermissionValue.TRUE);
            groupService.createGroup("group" + suffix);
            groupService.addSubjectGroup(TARGET_ID, "group" + suffix);
            groupService.createGroup("parent" + suffix);
            groupService.addGroupParent("group01", "parent" + suffix);
            groupService.setGroupPermission("paged", "example." + suffix, PermissionValue.FALSE);
            subjectMetadataService.recordSubject(new UUID(0L, 100L + index), "User" + suffix, FIRST_SEEN.plusSeconds(index));
            manualPermissionNodeRegistry.addNode("example.page" + suffix);
            writeBackup("database-20260424-12000" + index + "000.db", "backup.node" + index);
        }

        TestSource userPermissions = TestSource.console();
        dispatcher.execute("clutchperms user Target list 2", userPermissions);
        assertEquals(List.of("Permissions for Target (00000000-0000-0000-0000-000000000002) (page 2/2):", "  example.09", "< Prev | Page 2/2"),
                userPermissions.messages().stream().map(message -> message.replace("=TRUE", "")).toList());
        assertSuggests(userPermissions.commandMessages().get(1), "/clutchperms user 00000000-0000-0000-0000-000000000002 get example.09");

        TestSource userGroups = TestSource.console();
        dispatcher.execute("clutchperms user Target groups 2", userGroups);
        assertEquals("Groups for Target (00000000-0000-0000-0000-000000000002) (page 2/2):", userGroups.messages().getFirst());
        assertTrue(userGroups.messages().contains("  group09"));

        TestSource groups = TestSource.console();
        dispatcher.execute("clutchperms group list 2", groups);
        assertTrue(groups.messages().getFirst().startsWith("Groups (page 2/"));
        assertTrue(groups.messages().stream().anyMatch(message -> message.contains("group")));

        TestSource groupDetails = TestSource.console();
        dispatcher.execute("clutchperms group paged list 2", groupDetails);
        assertEquals("Group paged (page 2/2):", groupDetails.messages().getFirst());
        assertTrue(groupDetails.messages().stream().anyMatch(message -> message.contains("permission example.09=FALSE")));

        TestSource groupParents = TestSource.console();
        dispatcher.execute("clutchperms group group01 parents 2", groupParents);
        assertEquals("Parents of group group01 (page 2/2):", groupParents.messages().getFirst());
        assertTrue(groupParents.messages().contains("  parent09"));

        TestSource users = TestSource.console();
        dispatcher.execute("clutchperms users list 2", users);
        assertEquals("Known users (page 2/2):", users.messages().getFirst());
        assertTrue(users.messages().stream().anyMatch(message -> message.contains("User09")));

        TestSource usersSearch = TestSource.console();
        dispatcher.execute("clutchperms users search User 2", usersSearch);
        assertEquals("Matched users (page 2/2):", usersSearch.messages().getFirst());
        assertTrue(usersSearch.messages().stream().anyMatch(message -> message.contains("User09")));

        TestSource nodes = TestSource.console();
        dispatcher.execute("clutchperms nodes list 2", nodes);
        assertTrue(nodes.messages().getFirst().startsWith("Known permission nodes (page 2/"));

        TestSource nodesSearch = TestSource.console();
        dispatcher.execute("clutchperms nodes search example.page 2", nodesSearch);
        assertEquals("Matched known permission nodes (page 2/2):", nodesSearch.messages().getFirst());
        assertTrue(nodesSearch.messages().contains("  example.page09 [manual]"));

        TestSource backups = TestSource.console();
        dispatcher.execute("clutchperms backup list page 2", backups);
        assertEquals("Backups (page 2/2):", backups.messages().getFirst());
        assertTrue(backups.messages().get(1).startsWith("  database-"));
        assertSuggests(backups.commandMessages().get(1), "/clutchperms backup restore " + backups.messages().get(1).trim());

        TestSource allBackups = TestSource.console();
        dispatcher.execute("clutchperms backup list page 2", allBackups);
        assertTrue(allBackups.messages().getFirst().startsWith("Backups (page 2/"));
    }

    /**
     * Confirms list result page size follows active config.
     *
     * @throws CommandSyntaxException when command execution fails unexpectedly
     */
    @Test
    void listCommandsUseConfiguredPageSize() throws CommandSyntaxException {
        environment.setConfig(new ClutchPermsConfig(ClutchPermsBackupConfig.defaults(), new ClutchPermsCommandConfig(7, 2)));
        permissionService.setPermission(TARGET_ID, "example.01", PermissionValue.TRUE);
        permissionService.setPermission(TARGET_ID, "example.02", PermissionValue.TRUE);
        permissionService.setPermission(TARGET_ID, "example.03", PermissionValue.TRUE);
        TestSource console = TestSource.console();

        dispatcher.execute("clutchperms user Target list 2", console);

        assertEquals(List.of("Permissions for Target (00000000-0000-0000-0000-000000000002) (page 2/2):", "  example.03=TRUE", "< Prev | Page 2/2"), console.messages());
    }

    /**
     * Confirms list pages reject invalid page tokens through styled feedback.
     *
     * @throws CommandSyntaxException when command execution fails unexpectedly
     */
    @Test
    void listCommandsRejectInvalidPages() throws CommandSyntaxException {
        permissionService.setPermission(TARGET_ID, "example.node", PermissionValue.TRUE);
        TestSource invalid = TestSource.console();
        TestSource outOfRange = TestSource.console();

        assertEquals(0, dispatcher.execute("clutchperms user Target list 0", invalid));
        assertEquals(List.of("Invalid page: 0", "Pages start at 1.", "Try one:", "  /clutchperms user Target list 1"), invalid.messages());

        assertEquals(0, dispatcher.execute("clutchperms user Target list 9", outOfRange));
        assertEquals(List.of("Page 9 is out of range.", "Available pages: 1-1.", "Try one:", "  /clutchperms user Target list 1"), outOfRange.messages());
    }

    /**
     * Confirms node registry commands list, search, add, and remove manual known nodes.
     *
     * @throws CommandSyntaxException when command execution fails unexpectedly
     */
    @Test
    void nodeRegistryCommandsListSearchAddAndRemoveKnownNodes() throws CommandSyntaxException {
        TestSource console = TestSource.console();

        dispatcher.execute("clutchperms nodes add example.fly Allows flight", console);
        dispatcher.execute("clutchperms nodes add example.build", console);
        dispatcher.execute("clutchperms nodes list", console);
        dispatcher.execute("clutchperms nodes search flight", console);
        dispatcher.execute("clutchperms nodes remove example.fly", console);
        dispatcher.execute("clutchperms nodes search flight", console);

        assertEquals("Registered known permission node example.fly.", console.messages().get(0));
        assertEquals("Registered known permission node example.build.", console.messages().get(1));
        assertTrue(console.messages().get(2).startsWith("Known permission nodes (page 1/"));
        assertMessageContains(console, PermissionNodes.ADMIN_BACKUP_LIST + " [built-in] - Allows the matching ClutchPerms admin command.");
        assertMessageContains(console, "Matched known permission nodes (page 1/1):");
        assertMessageContains(console, "  example.fly [manual] - Allows flight");
        assertMessageContains(console, "Removed known permission node example.fly.");
        assertEquals("No known permission nodes matched flight.", console.messages().getLast());
        assertEquals(3, environment.runtimeRefreshes());
    }

    /**
     * Confirms platform and built-in known nodes cannot be removed through the manual registry command.
     */
    @Test
    void nodeRegistryRemoveFailsForNonManualNodes() {
        environment.addPlatformNode("platform.node");
        TestSource console = TestSource.console();

        assertCommandFails("clutchperms nodes remove " + PermissionNodes.ADMIN_STATUS, console,
                "Known permission node is supplied by built-in and cannot be removed: " + PermissionNodes.ADMIN_STATUS);
        assertCommandFails("clutchperms nodes remove platform.node", console, "Known permission node is supplied by platform and cannot be removed: platform.node");

        assertEquals(0, environment.runtimeRefreshes());
    }

    /**
     * Confirms unknown manual known-node removals suggest close manually registered nodes.
     */
    @Test
    void unknownManualKnownNodeSuggestsClosestManualNode() {
        manualPermissionNodeRegistry.addNode("example.fly");
        manualPermissionNodeRegistry.addNode("example.build");
        TestSource console = TestSource.console();

        assertCommandFails("clutchperms nodes remove example.fli", console, "Known permission node is not manually registered: example.fli");

        assertMessageContains(console, "Only manually registered known permission nodes can be removed.");
        assertMessageContains(console, "Closest manual known permission nodes: example.fly");
    }

    /**
     * Confirms known-node registration rejects wildcard nodes while permission assignments still accept them.
     *
     * @throws CommandSyntaxException when wildcard permission assignment fails unexpectedly
     */
    @Test
    void nodeRegistryRejectsWildcardsButAssignmentsAllowThem() throws CommandSyntaxException {
        TestSource console = TestSource.console();

        assertCommandFails("clutchperms nodes add example.*", console, "Known permission node operation failed: known permission nodes must be exact nodes");
        dispatcher.execute("clutchperms user Target set example.* true", console);

        assertEquals(PermissionValue.TRUE, permissionService.getPermission(TARGET_ID, "example.*"));
    }

    private List<String> suggestionTexts(String command) {
        return dispatcher.getCompletionSuggestions(dispatcher.parse(command, TestSource.console())).join().getList().stream().map(Suggestion::getText).toList();
    }

    private void assertCommandFails(String command, TestSource source, String expectedMessage) {
        int firstNewMessage = source.messages().size();
        try {
            assertEquals(0, dispatcher.execute(command, source));
        } catch (CommandSyntaxException exception) {
            throw new AssertionError("Expected styled command failure for " + command, exception);
        }
        assertMessageContains(source, firstNewMessage, expectedMessage);
    }

    private static void assertMessageContains(TestSource source, String expectedMessage) {
        assertMessageContains(source, 0, expectedMessage);
    }

    private static void assertMessageContains(TestSource source, int firstMessageIndex, String expectedMessage) {
        List<String> messages = source.messages();
        assertTrue(messages.size() > firstMessageIndex, "Expected command feedback");
        assertTrue(messages.subList(firstMessageIndex, messages.size()).stream().anyMatch(message -> message.contains(expectedMessage)),
                () -> "Expected message to contain <" + expectedMessage + "> but was " + messages.subList(firstMessageIndex, messages.size()));
    }

    private static void assertSuggests(CommandMessage message, String command) {
        assertTrue(
                message.segments().stream().anyMatch(
                        segment -> segment.click() != null && segment.click().action() == CommandMessage.ClickAction.SUGGEST_COMMAND && segment.click().value().equals(command)),
                () -> "Expected suggest click for " + command + " in " + message);
    }

    private static void assertRuns(CommandMessage message, String command) {
        assertTrue(
                message.segments().stream().anyMatch(
                        segment -> segment.click() != null && segment.click().action() == CommandMessage.ClickAction.RUN_COMMAND && segment.click().value().equals(command)),
                () -> "Expected run click for " + command + " in " + message);
    }

    private void writeBackup(String fileName, String permissionNode) throws IOException {
        Path backupDirectory = temporaryDirectory.resolve("backups").resolve(StorageFileKind.DATABASE.token());
        Files.createDirectories(backupDirectory);
        try (SqliteStore store = SqliteStore.open(backupDirectory.resolve(fileName), SqliteDependencyMode.ANY_VISIBLE)) {
            PermissionServices.sqlite(store).setPermission(TARGET_ID, permissionNode, PermissionValue.TRUE);
        }
    }

    private void writeRawBackup(String fileName, String content) throws IOException {
        Path backupDirectory = temporaryDirectory.resolve("backups").resolve(StorageFileKind.DATABASE.token());
        Files.createDirectories(backupDirectory);
        Files.writeString(backupDirectory.resolve(fileName), content);
    }

    private PermissionValue permissionFromDatabase(String node) {
        try (SqliteStore store = SqliteStore.open(temporaryDirectory.resolve("database.db"), SqliteDependencyMode.ANY_VISIBLE)) {
            return PermissionServices.sqlite(store).getPermission(TARGET_ID, node);
        }
    }

    private static List<String> statusMessages(int knownSubjects) {
        return List.of(ClutchPermsCommands.STATUS_MESSAGE, "Database file: " + STATUS_DIAGNOSTICS.databaseFile(), "Config file: " + STATUS_DIAGNOSTICS.configFile(),
                "Backup retention: newest 10 database backups.", "Command page sizes: help 7, lists 8.", "Chat formatting: enabled.", "Known subjects: " + knownSubjects,
                "Known groups: 1", "Known permission nodes: " + PermissionNodes.commandNodes().size(), "Resolver cache: 0 subjects, 0 node results, 0 effective snapshots.",
                "Runtime bridge: " + STATUS_DIAGNOSTICS.runtimeBridgeStatus());
    }

    private static List<String> commandListPageOneMessages() {
        return commandListPageOneMessages("clutchperms");
    }

    private static List<String> commandListPageOneMessages(String rootLiteral) {
        return List.of("ClutchPerms commands (page 1/7):", "/" + rootLiteral + " help [page]", "/" + rootLiteral + " status", "/" + rootLiteral + " reload",
                "/" + rootLiteral + " validate", "/" + rootLiteral + " config list", "/" + rootLiteral + " config get <key>", "/" + rootLiteral + " config set <key> <value>",
                "Page 1/7 | Next >");
    }

    private static List<String> commandListPageTwoMessages() {
        return commandListPageTwoMessages("clutchperms");
    }

    private static List<String> commandListPageTwoMessages(String rootLiteral) {
        return List.of("ClutchPerms commands (page 2/7):", "/" + rootLiteral + " config reset <key|all>", "/" + rootLiteral + " backup create",
                "/" + rootLiteral + " backup list [page]", "/" + rootLiteral + " backup list page <page>", "/" + rootLiteral + " backup restore <backup-file>",
                "/" + rootLiteral + " user <target> info", "/" + rootLiteral + " user <target> list [page]", "< Prev | Page 2/7 | Next >");
    }

    private static List<String> groupRootUsageMessages() {
        return List.of("Missing group command.", "List groups or choose a group to inspect or mutate.", "Try one:", "  /clutchperms group list",
                "  /clutchperms group <group> <create|delete|info|list|members|parents>", "  /clutchperms group <group> <get|clear> <node>",
                "  /clutchperms group <group> set <node> <true|false>", "  /clutchperms group <group> clear-all", "  /clutchperms group <group> rename <new-group>",
                "  /clutchperms group <group> parent <add|remove> <parent>", "  /clutchperms group <group> <prefix|suffix> get|set|clear");
    }

    private static List<String> groupTargetUsageMessages(String group) {
        return List.of("Missing group command.", "Choose what to do with group " + group + ".", "Try one:",
                "  /clutchperms group " + group + " <create|delete|info|list|members|parents>", "  /clutchperms group " + group + " <get|clear> <node>",
                "  /clutchperms group " + group + " set <node> <true|false>", "  /clutchperms group " + group + " clear-all",
                "  /clutchperms group " + group + " rename <new-group>", "  /clutchperms group " + group + " parent <add|remove> <parent>",
                "  /clutchperms group " + group + " <prefix|suffix> get|set|clear");
    }

    private static List<String> userRootUsageMessages() {
        return List.of("Missing user target.", "Provide an online name, stored last-known name, or UUID.", "Try one:", "  /clutchperms user <target> <info|list|groups>",
                "  /clutchperms user <target> <get|clear|check|explain> <node>", "  /clutchperms user <target> set <node> <true|false>", "  /clutchperms user <target> clear-all",
                "  /clutchperms user <target> group <add|remove> <group>", "  /clutchperms user <target> <prefix|suffix> get|set|clear");
    }

    private static List<String> nodesUsageMessages() {
        return List.of("Missing nodes command.", "List, search, add, or remove known permission nodes.", "Try one:", "  /clutchperms nodes list",
                "  /clutchperms nodes search <query>", "  /clutchperms nodes add <node> [description]", "  /clutchperms nodes remove <node>");
    }

    private static final class FailingMutationPermissionService implements PermissionService {

        private final RuntimeException failure;

        private FailingMutationPermissionService(RuntimeException failure) {
            this.failure = failure;
        }

        @Override
        public PermissionValue getPermission(UUID subjectId, String node) {
            return PermissionValue.UNSET;
        }

        @Override
        public Map<String, PermissionValue> getPermissions(UUID subjectId) {
            return Map.of();
        }

        @Override
        public void setPermission(UUID subjectId, String node, PermissionValue value) {
            throw failure;
        }

        @Override
        public void clearPermission(UUID subjectId, String node) {
            throw failure;
        }

        @Override
        public int clearPermissions(UUID subjectId) {
            throw failure;
        }
    }

    private static final class TestEnvironment implements ClutchPermsCommandEnvironment<TestSource> {

        private final PermissionService permissionService;

        private final SubjectMetadataService subjectMetadataService;

        private final GroupService groupService;

        private final MutablePermissionNodeRegistry manualPermissionNodeRegistry;

        private final PermissionResolver permissionResolver;

        private final Map<String, CommandSubject> onlineSubjects = new LinkedHashMap<>();

        private final List<String> platformNodes = new ArrayList<>();

        private StorageBackupService storageBackupService;

        private ClutchPermsConfig config = ClutchPermsConfig.defaults();

        private int reloads;

        private int validations;

        private int runtimeRefreshes;

        private int configUpdates;

        private RuntimeException reloadFailure;

        private RuntimeException validationFailure;

        private RuntimeException configUpdateFailure;

        private TestEnvironment(PermissionService permissionService, SubjectMetadataService subjectMetadataService, GroupService groupService,
                MutablePermissionNodeRegistry manualPermissionNodeRegistry, PermissionResolver permissionResolver) {
            this.permissionService = permissionService;
            this.subjectMetadataService = subjectMetadataService;
            this.groupService = groupService;
            this.manualPermissionNodeRegistry = PermissionNodeRegistries.observing(manualPermissionNodeRegistry, this::refreshRuntimePermissions);
            this.permissionResolver = permissionResolver;
        }

        private void addOnlineSubject(String name, UUID subjectId) {
            onlineSubjects.put(name, new CommandSubject(subjectId, name));
        }

        private void addPlatformNode(String node) {
            platformNodes.add(node);
        }

        private void failReload(RuntimeException reloadFailure) {
            this.reloadFailure = reloadFailure;
        }

        private void failValidation(RuntimeException validationFailure) {
            this.validationFailure = validationFailure;
        }

        private void failConfigUpdate(RuntimeException configUpdateFailure) {
            this.configUpdateFailure = configUpdateFailure;
        }

        private void setStorageBackupService(StorageBackupService storageBackupService) {
            this.storageBackupService = storageBackupService;
        }

        private void setConfig(ClutchPermsConfig config) {
            this.config = config;
        }

        private int reloads() {
            return reloads;
        }

        private int validations() {
            return validations;
        }

        private int runtimeRefreshes() {
            return runtimeRefreshes;
        }

        private int configUpdates() {
            return configUpdates;
        }

        @Override
        public PermissionService permissionService() {
            return permissionService;
        }

        @Override
        public GroupService groupService() {
            return groupService;
        }

        @Override
        public PermissionNodeRegistry permissionNodeRegistry() {
            return PermissionNodeRegistries.composite(PermissionNodeRegistries.builtIn(), manualPermissionNodeRegistry,
                    PermissionNodeRegistries.staticNodes(PermissionNodeSource.PLATFORM, platformNodes));
        }

        @Override
        public MutablePermissionNodeRegistry manualPermissionNodeRegistry() {
            return manualPermissionNodeRegistry;
        }

        @Override
        public PermissionResolver permissionResolver() {
            return permissionResolver;
        }

        @Override
        public SubjectMetadataService subjectMetadataService() {
            return subjectMetadataService;
        }

        @Override
        public CommandStatusDiagnostics statusDiagnostics() {
            return STATUS_DIAGNOSTICS;
        }

        @Override
        public ClutchPermsConfig config() {
            return config;
        }

        @Override
        public void updateConfig(UnaryOperator<ClutchPermsConfig> updater) {
            if (configUpdateFailure != null) {
                throw configUpdateFailure;
            }
            config = updater.apply(config);
            configUpdates++;
        }

        @Override
        public void reloadStorage() {
            if (reloadFailure != null) {
                throw reloadFailure;
            }
            reloads++;
        }

        @Override
        public void validateStorage() {
            if (validationFailure != null) {
                throw validationFailure;
            }
            validations++;
        }

        @Override
        public StorageBackupService storageBackupService() {
            return storageBackupService;
        }

        @Override
        public void refreshRuntimePermissions() {
            runtimeRefreshes++;
        }

        @Override
        public CommandSourceKind sourceKind(TestSource source) {
            return source.kind();
        }

        @Override
        public Optional<UUID> sourceSubjectId(TestSource source) {
            return Optional.ofNullable(source.subjectId());
        }

        @Override
        public Optional<CommandSubject> findOnlineSubject(TestSource source, String target) {
            return Optional.ofNullable(onlineSubjects.get(target));
        }

        @Override
        public Collection<String> onlineSubjectNames(TestSource source) {
            return onlineSubjects.keySet();
        }

        @Override
        public void sendMessage(TestSource source, String message) {
            source.messages().add(message);
            source.commandMessages().add(CommandMessage.plain(message));
        }

        @Override
        public void sendMessage(TestSource source, CommandMessage message) {
            source.messages().add(message.plainText());
            source.commandMessages().add(message);
        }
    }

    private record TestSource(CommandSourceKind kind, UUID subjectId, List<String> messages, List<CommandMessage> commandMessages) {

        private static TestSource console() {
            return new TestSource(CommandSourceKind.CONSOLE, null, new ArrayList<>(), new ArrayList<>());
        }

        private static TestSource player(UUID subjectId) {
            return new TestSource(CommandSourceKind.PLAYER, subjectId, new ArrayList<>(), new ArrayList<>());
        }
    }
}
