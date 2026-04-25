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
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestion;

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
import me.clutchy.clutchperms.common.storage.StorageBackupService;
import me.clutchy.clutchperms.common.storage.StorageFileKind;
import me.clutchy.clutchperms.common.subject.InMemorySubjectMetadataService;
import me.clutchy.clutchperms.common.subject.SubjectMetadataService;

import static org.junit.jupiter.api.Assertions.assertEquals;
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

    private static final CommandStatusDiagnostics STATUS_DIAGNOSTICS = new CommandStatusDiagnostics("/tmp/clutchperms/permissions.json", "/tmp/clutchperms/subjects.json",
            "/tmp/clutchperms/groups.json", "/tmp/clutchperms/nodes.json", "test bridge active");

    private PermissionService permissionService;

    private SubjectMetadataService subjectMetadataService;

    private GroupService groupService;

    private MutablePermissionNodeRegistry manualPermissionNodeRegistry;

    private PermissionResolver permissionResolver;

    private TestEnvironment environment;

    private CommandDispatcher<TestSource> dispatcher;

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
        environment.setStorageBackupService(StorageBackupService.forFiles(temporaryDirectory.resolve("backups"),
                Map.of(StorageFileKind.PERMISSIONS, temporaryDirectory.resolve("permissions.json"), StorageFileKind.SUBJECTS, temporaryDirectory.resolve("subjects.json"),
                        StorageFileKind.GROUPS, temporaryDirectory.resolve("groups.json"), StorageFileKind.NODES, temporaryDirectory.resolve("nodes.json"))));
        environment.addOnlineSubject("Target", TARGET_ID);
        dispatcher = new CommandDispatcher<>();
        dispatcher.getRoot().addChild(ClutchPermsCommands.create(environment));
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

        assertEquals(commandListMessages(), console.messages());
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

        assertEquals(0, dispatcher.execute("clutchperms user", userRoot));
        assertEquals(userRootUsageMessages(), userRoot.messages());

        assertEquals(0, dispatcher.execute("clutchperms user Target set", userSet));
        assertEquals(List.of("Missing permission assignment.", "Set a node to true or false.", "Try one:", "  /clutchperms user Target set <node> <true|false>"),
                userSet.messages());

        assertEquals(0, dispatcher.execute("clutchperms backup restore permissions", backupRestore));
        assertEquals(List.of("Missing backup file.", "Pick a backup file for permissions.", "Try one:", "  /clutchperms backup restore permissions <backup-file>"),
                backupRestore.messages());

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
        assertEquals(List.of("Reloaded permissions, subjects, groups, and known nodes from disk."), console.messages());
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
        assertEquals(List.of("Validated permissions, subjects, groups, and known nodes from disk."), console.messages());
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
     * Confirms backup list commands report all backups and per-file backups newest first.
     *
     * @throws IOException when test backup setup fails
     * @throws CommandSyntaxException when command execution fails unexpectedly
     */
    @Test
    void backupListReportsAllAndFileSpecificBackups() throws IOException, CommandSyntaxException {
        writeBackup(StorageFileKind.PERMISSIONS, "permissions-20260424-120000000.json", "first");
        writeBackup(StorageFileKind.PERMISSIONS, "permissions-20260424-120001000.json", "second");
        writeBackup(StorageFileKind.GROUPS, "groups-20260424-120000000.json", "groups");
        TestSource console = TestSource.console();

        dispatcher.execute("clutchperms backup list", console);
        dispatcher.execute("clutchperms backup list permissions", console);

        assertEquals(List.of("Backups for permissions: permissions-20260424-120001000.json, permissions-20260424-120000000.json",
                "Backups for groups: groups-20260424-120000000.json", "Backups for permissions: permissions-20260424-120001000.json, permissions-20260424-120000000.json"),
                console.messages());
    }

    /**
     * Confirms backup restore replaces the selected file and refreshes runtime state through the reload path.
     *
     * @throws IOException when test backup setup fails
     * @throws CommandSyntaxException when command execution fails unexpectedly
     */
    @Test
    void backupRestoreRestoresFileAndRefreshesRuntimeState() throws IOException, CommandSyntaxException {
        Path liveFile = temporaryDirectory.resolve("permissions.json");
        Files.writeString(liveFile, "current");
        writeBackup(StorageFileKind.PERMISSIONS, "permissions-20260424-120000000.json", "restored");
        TestSource console = TestSource.console();

        dispatcher.execute("clutchperms backup restore permissions permissions-20260424-120000000.json", console);

        assertEquals("restored", Files.readString(liveFile));
        assertEquals(1, environment.reloads());
        assertEquals(1, environment.runtimeRefreshes());
        assertEquals(List.of("Restored permissions from backup permissions-20260424-120000000.json."), console.messages());
    }

    /**
     * Confirms backup restore rolls disk back and skips runtime refresh when reload rejects the restored file.
     *
     * @throws IOException when test backup setup fails
     */
    @Test
    void backupRestoreFailureRollsBackFileAndDoesNotRefreshRuntimeState() throws IOException {
        Path liveFile = temporaryDirectory.resolve("permissions.json");
        Files.writeString(liveFile, "current");
        writeBackup(StorageFileKind.PERMISSIONS, "permissions-20260424-120000000.json", "restored");
        environment.failReload(new PermissionStorageException("bad restored permissions"));
        TestSource console = TestSource.console();

        assertCommandFails("clutchperms backup restore permissions permissions-20260424-120000000.json", console,
                "Backup operation failed: Failed to apply restored permissions backup permissions-20260424-120000000.json");

        assertEquals("current", Files.readString(liveFile));
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
     * Confirms backup command suggestions include file kinds and backups for the selected kind.
     *
     * @throws IOException when test backup setup fails
     */
    @Test
    void backupSuggestionsIncludeKindsAndBackupFiles() throws IOException {
        writeBackup(StorageFileKind.PERMISSIONS, "permissions-20260424-120000000.json", "first");
        writeBackup(StorageFileKind.GROUPS, "groups-20260424-120000000.json", "groups");

        assertEquals(List.of("groups", "nodes", "permissions", "subjects"), suggestionTexts("clutchperms backup list "));
        assertEquals(List.of("permissions-20260424-120000000.json"), suggestionTexts("clutchperms backup restore permissions "));
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
                        "Permissions for Target (00000000-0000-0000-0000-000000000002): example.node=TRUE",
                        "Cleared example.node for Target (00000000-0000-0000-0000-000000000002).", "No permissions set for Target (00000000-0000-0000-0000-000000000002)."),
                console.messages());
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
     * Confirms wildcard admin permissions authorize command execution through the shared resolver.
     *
     * @throws CommandSyntaxException when command execution fails unexpectedly
     */
    @Test
    void playerWithCategoryWildcardCanUseMatchingCommandsOnly() throws CommandSyntaxException {
        permissionService.setPermission(ADMIN_ID, "clutchperms.admin.user.*", PermissionValue.TRUE);
        TestSource player = TestSource.player(ADMIN_ID);

        dispatcher.execute("clutchperms user Target set example.node false", player);

        assertEquals(PermissionValue.FALSE, permissionService.getPermission(TARGET_ID, "example.node"));
        assertCommandFails("clutchperms group list", player, "You do not have permission to use ClutchPerms commands.");
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
        assertEquals(List.of("Created group admin.", "Set example.node for group admin to TRUE.", "Added Target (00000000-0000-0000-0000-000000000002) to group admin.",
                "Groups for Target (00000000-0000-0000-0000-000000000002): admin", "Target (00000000-0000-0000-0000-000000000002) effective example.node = TRUE from group admin.",
                "Permissions for group admin: example.node=TRUE", "Members of group admin: Target (00000000-0000-0000-0000-000000000002)", "Group admin has example.node = TRUE.",
                "Cleared example.node for group admin.", "Removed Target (00000000-0000-0000-0000-000000000002) from group admin.", "Deleted group admin."), console.messages());
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
                "Permissions for Target (00000000-0000-0000-0000-000000000002): example.*=TRUE", "Created group wildcard.", "Set other.* for group wildcard to FALSE.",
                "Added Target (00000000-0000-0000-0000-000000000002) to group wildcard.",
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
        groupService.createGroup("default");
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
                "Parents of group staff: base", "Added Target (00000000-0000-0000-0000-000000000002) to group staff.",
                "Target (00000000-0000-0000-0000-000000000002) effective example.inherited = TRUE from group base.", "No permissions set for group staff.",
                "Parents of group staff: base", "Members of group staff: 00000000-0000-0000-0000-000000000002 (00000000-0000-0000-0000-000000000002)",
                "Removed parent group base from group staff.", "Group staff has no parent groups."), console.messages());
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

        assertCommandFails("clutchperms group admin parent add missing", console, "Group operation failed: unknown group: missing");
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

        dispatcher.execute("clutchperms group default create", console);
        dispatcher.execute("clutchperms group default set example.default false", console);
        dispatcher.execute("clutchperms user Target groups", console);
        dispatcher.execute("clutchperms user Target check example.default", console);

        assertEquals(PermissionValue.FALSE, permissionResolver.resolve(TARGET_ID, "example.default").value());
        assertEquals(
                List.of("Created group default.", "Set example.default for group default to FALSE.", "Groups for Target (00000000-0000-0000-0000-000000000002): default (implicit)",
                        "Target (00000000-0000-0000-0000-000000000002) effective example.default = FALSE from default group."),
                console.messages());
    }

    /**
     * Confirms explicit default group membership is rejected because default applies implicitly.
     */
    @Test
    void defaultGroupCannotBeAssignedExplicitly() throws CommandSyntaxException {
        TestSource console = TestSource.console();
        dispatcher.execute("clutchperms group default create", console);

        assertCommandFails("clutchperms user Target group add default", console, "Group operation failed: default group membership is implicit");
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

        assertCommandFails("clutchperms user Duplicate list", console, "Ambiguous known user Duplicate:");
        assertLatestMessageContains(console, "Duplicate (00000000-0000-0000-0000-000000000002, last seen 2026-04-24T12:00:00Z)");
        assertLatestMessageContains(console, "duplicate (00000000-0000-0000-0000-000000000004, last seen 2026-04-24T13:00:00Z)");
    }

    /**
     * Confirms unknown targets and malformed command input fail through Brigadier.
     */
    @Test
    void invalidTargetAndNodeFailExecution() {
        TestSource console = TestSource.console();

        assertCommandFails("clutchperms user Missing list", console, "Unknown online player or invalid UUID: Missing");
        assertCommandFails("clutchperms user " + TARGET_ID + " get bad node", console, "Invalid permission node: bad node");
        assertCommandFails("clutchperms user Target set example* true", console, "Invalid permission node: example*");
        assertCommandFails("clutchperms group staff set example.*.node true", console, "Invalid permission node: example.*.node");
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
        groupService.createGroup("default");
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
        groupService.createGroup("default");
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

        assertEquals(List.of("Known users: Alpha (00000000-0000-0000-0000-000000000004, last seen 2026-04-24T12:00:00Z), "
                + "Zed (00000000-0000-0000-0000-000000000002, last seen 2026-04-24T13:00:00Z)"), console.messages());
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

        assertEquals(List.of("Matched users: Target (00000000-0000-0000-0000-000000000002, last seen 2026-04-24T12:00:00Z)"), console.messages());
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
        assertTrue(console.messages().get(2).contains(PermissionNodes.ADMIN_STATUS + " [built-in] - Allows the matching ClutchPerms admin command."));
        assertTrue(console.messages().get(2).contains("example.build [manual]"));
        assertTrue(console.messages().get(2).contains("example.fly [manual] - Allows flight"));
        assertEquals("Matched known permission nodes: example.fly [manual] - Allows flight", console.messages().get(3));
        assertEquals("Removed known permission node example.fly.", console.messages().get(4));
        assertEquals("No known permission nodes matched flight.", console.messages().get(5));
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
                "known permission node is not manually registered: " + PermissionNodes.ADMIN_STATUS);
        assertCommandFails("clutchperms nodes remove platform.node", console, "known permission node is not manually registered: platform.node");

        assertEquals(0, environment.runtimeRefreshes());
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
        try {
            assertEquals(0, dispatcher.execute(command, source));
        } catch (CommandSyntaxException exception) {
            throw new AssertionError("Expected styled command failure for " + command, exception);
        }
        assertLatestMessageContains(source, expectedMessage);
    }

    private static void assertLatestMessageContains(TestSource source, String expectedMessage) {
        List<String> messages = source.messages();
        assertTrue(!messages.isEmpty(), "Expected command feedback");
        assertTrue(messages.get(messages.size() - 1).contains(expectedMessage), () -> "Expected latest message to contain <" + expectedMessage + "> but was " + messages);
    }

    private void writeBackup(StorageFileKind kind, String fileName, String content) throws IOException {
        Path backupDirectory = temporaryDirectory.resolve("backups").resolve(kind.token());
        Files.createDirectories(backupDirectory);
        Files.writeString(backupDirectory.resolve(fileName), content);
    }

    private static List<String> statusMessages(int knownSubjects) {
        return List.of(ClutchPermsCommands.STATUS_MESSAGE, "Permissions file: " + STATUS_DIAGNOSTICS.permissionsFile(), "Subjects file: " + STATUS_DIAGNOSTICS.subjectsFile(),
                "Groups file: " + STATUS_DIAGNOSTICS.groupsFile(), "Known nodes file: " + STATUS_DIAGNOSTICS.nodesFile(), "Known subjects: " + knownSubjects, "Known groups: 0",
                "Known permission nodes: " + PermissionNodes.commandNodes().size(), "Resolver cache: 0 subjects, 0 node results, 0 effective snapshots.",
                "Runtime bridge: " + STATUS_DIAGNOSTICS.runtimeBridgeStatus());
    }

    private static List<String> commandListMessages() {
        return List.of("ClutchPerms commands:", "/clutchperms <status|reload|validate>", "/clutchperms backup list [permissions|subjects|groups|nodes]",
                "/clutchperms backup restore <permissions|subjects|groups|nodes> <backup-file>", "/clutchperms user <target> <list|groups>",
                "/clutchperms user <target> <get|clear|check|explain> <node>", "/clutchperms user <target> set <node> <true|false>",
                "/clutchperms user <target> group <add|remove> <group>", "/clutchperms group list", "/clutchperms group <group> <create|delete|list|parents>",
                "/clutchperms group <group> <get|clear> <node>", "/clutchperms group <group> set <node> <true|false>", "/clutchperms group <group> parent <add|remove> <parent>",
                "/clutchperms users list", "/clutchperms users search <name>", "/clutchperms nodes list", "/clutchperms nodes search <query>",
                "/clutchperms nodes add <node> [description]", "/clutchperms nodes remove <node>");
    }

    private static List<String> groupRootUsageMessages() {
        return List.of("Missing group command.", "List groups or choose a group to inspect or mutate.", "Try one:", "  /clutchperms group list",
                "  /clutchperms group <group> <create|delete|list|parents>", "  /clutchperms group <group> <get|clear> <node>",
                "  /clutchperms group <group> set <node> <true|false>", "  /clutchperms group <group> parent <add|remove> <parent>");
    }

    private static List<String> groupTargetUsageMessages(String group) {
        return List.of("Missing group command.", "Choose what to do with group " + group + ".", "Try one:", "  /clutchperms group " + group + " <create|delete|list|parents>",
                "  /clutchperms group " + group + " <get|clear> <node>", "  /clutchperms group " + group + " set <node> <true|false>",
                "  /clutchperms group " + group + " parent <add|remove> <parent>");
    }

    private static List<String> userRootUsageMessages() {
        return List.of("Missing user target.", "Provide an online name, stored last-known name, or UUID.", "Try one:", "  /clutchperms user <target> <list|groups>",
                "  /clutchperms user <target> <get|clear|check|explain> <node>", "  /clutchperms user <target> set <node> <true|false>",
                "  /clutchperms user <target> group <add|remove> <group>");
    }

    private static List<String> nodesUsageMessages() {
        return List.of("Missing nodes command.", "List, search, add, or remove known permission nodes.", "Try one:", "  /clutchperms nodes list",
                "  /clutchperms nodes search <query>", "  /clutchperms nodes add <node> [description]", "  /clutchperms nodes remove <node>");
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

        private int reloads;

        private int validations;

        private int runtimeRefreshes;

        private RuntimeException reloadFailure;

        private RuntimeException validationFailure;

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

        private void setStorageBackupService(StorageBackupService storageBackupService) {
            this.storageBackupService = storageBackupService;
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
        }
    }

    private record TestSource(CommandSourceKind kind, UUID subjectId, List<String> messages) {

        private static TestSource console() {
            return new TestSource(CommandSourceKind.CONSOLE, null, new ArrayList<>());
        }

        private static TestSource player(UUID subjectId) {
            return new TestSource(CommandSourceKind.PLAYER, subjectId, new ArrayList<>());
        }
    }
}
