package me.clutchy.clutchperms.common.command;

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

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestion;

import me.clutchy.clutchperms.common.group.GroupService;
import me.clutchy.clutchperms.common.group.InMemoryGroupService;
import me.clutchy.clutchperms.common.node.MutablePermissionNodeRegistry;
import me.clutchy.clutchperms.common.node.PermissionNodeRegistries;
import me.clutchy.clutchperms.common.node.PermissionNodeRegistry;
import me.clutchy.clutchperms.common.node.PermissionNodeSource;
import me.clutchy.clutchperms.common.permission.InMemoryPermissionService;
import me.clutchy.clutchperms.common.permission.PermissionNodes;
import me.clutchy.clutchperms.common.permission.PermissionResolver;
import me.clutchy.clutchperms.common.permission.PermissionService;
import me.clutchy.clutchperms.common.permission.PermissionValue;
import me.clutchy.clutchperms.common.storage.PermissionStorageException;
import me.clutchy.clutchperms.common.subject.InMemorySubjectMetadataService;
import me.clutchy.clutchperms.common.subject.SubjectMetadataService;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
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

    /**
     * Creates a fresh command dispatcher and permission service for each test case.
     */
    @BeforeEach
    void setUp() {
        permissionService = new InMemoryPermissionService();
        subjectMetadataService = new InMemorySubjectMetadataService();
        groupService = new InMemoryGroupService();
        manualPermissionNodeRegistry = PermissionNodeRegistries.inMemory();
        permissionResolver = new PermissionResolver(permissionService, groupService);
        environment = new TestEnvironment(permissionService, subjectMetadataService, groupService, manualPermissionNodeRegistry, permissionResolver);
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

        CommandSyntaxException exception = assertThrows(CommandSyntaxException.class, () -> dispatcher.execute("clutchperms reload", console));

        assertTrue(exception.getMessage().contains("Failed to reload ClutchPerms storage: bad permissions file"));
        assertEquals(0, environment.reloads());
        assertEquals(0, environment.runtimeRefreshes());
        assertEquals(List.of(), console.messages());
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

        CommandSyntaxException exception = assertThrows(CommandSyntaxException.class, () -> dispatcher.execute("clutchperms validate", console));

        assertTrue(exception.getMessage().contains("Failed to validate ClutchPerms storage: bad groups file"));
        assertEquals(0, environment.validations());
        assertEquals(0, environment.reloads());
        assertEquals(0, environment.runtimeRefreshes());
        assertEquals(List.of(), console.messages());
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

        assertThrows(CommandSyntaxException.class, () -> dispatcher.execute("clutchperms", player));
    }

    /**
     * Confirms players with the ClutchPerms admin node can mutate permissions.
     *
     * @throws CommandSyntaxException when command execution fails unexpectedly
     */
    @Test
    void playerWithAdminPermissionCanMutatePermissions() throws CommandSyntaxException {
        permissionService.setPermission(ADMIN_ID, PermissionNodes.ADMIN, PermissionValue.TRUE);
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
        groupService.setGroupPermission("staff", PermissionNodes.ADMIN, PermissionValue.TRUE);
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
        groupService.setGroupPermission("admin", PermissionNodes.ADMIN, PermissionValue.TRUE);
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
    void playerWithWildcardAdminPermissionCanMutatePermissions() throws CommandSyntaxException {
        permissionService.setPermission(ADMIN_ID, "clutchperms.*", PermissionValue.TRUE);
        TestSource player = TestSource.player(ADMIN_ID);

        dispatcher.execute("clutchperms user Target set example.node false", player);

        assertEquals(PermissionValue.FALSE, permissionService.getPermission(TARGET_ID, "example.node"));
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

        CommandSyntaxException unknown = assertThrows(CommandSyntaxException.class, () -> dispatcher.execute("clutchperms group admin parent add missing", console));
        CommandSyntaxException self = assertThrows(CommandSyntaxException.class, () -> dispatcher.execute("clutchperms group admin parent add admin", console));
        CommandSyntaxException cycle = assertThrows(CommandSyntaxException.class, () -> dispatcher.execute("clutchperms group staff parent add admin", console));

        assertTrue(unknown.getMessage().contains("Group operation failed: unknown group: missing"));
        assertTrue(self.getMessage().contains("Group operation failed: group cannot inherit itself: admin"));
        assertTrue(cycle.getMessage().contains("Group operation failed: group inheritance cycle detected"));
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

        CommandSyntaxException exception = assertThrows(CommandSyntaxException.class, () -> dispatcher.execute("clutchperms user Target group add default", console));

        assertTrue(exception.getMessage().contains("Group operation failed: default group membership is implicit"));
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

        CommandSyntaxException exception = assertThrows(CommandSyntaxException.class, () -> dispatcher.execute("clutchperms user Duplicate list", console));

        assertTrue(exception.getMessage().contains("Ambiguous known user Duplicate:"));
        assertTrue(exception.getMessage().contains("Duplicate (00000000-0000-0000-0000-000000000002, last seen 2026-04-24T12:00:00Z)"));
        assertTrue(exception.getMessage().contains("duplicate (00000000-0000-0000-0000-000000000004, last seen 2026-04-24T13:00:00Z)"));
    }

    /**
     * Confirms unknown targets and malformed command input fail through Brigadier.
     */
    @Test
    void invalidTargetAndNodeFailExecution() {
        TestSource console = TestSource.console();

        assertThrows(CommandSyntaxException.class, () -> dispatcher.execute("clutchperms user Missing list", console));
        assertThrows(CommandSyntaxException.class, () -> dispatcher.execute("clutchperms user " + TARGET_ID + " get bad node", console));
        assertThrows(CommandSyntaxException.class, () -> dispatcher.execute("clutchperms user Target set example* true", console));
        assertThrows(CommandSyntaxException.class, () -> dispatcher.execute("clutchperms group staff set example.*.node true", console));
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

        assertEquals(List.of("clutchperms.admin", "example.*", "example.node", "known.node", "zeta.node"), suggestionTexts("clutchperms user Target get "));
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

        assertEquals(List.of("clutchperms.admin", "default.node", "example.group", "example.inherited"), suggestionTexts("clutchperms user Target check "));
    }

    /**
     * Confirms group permission commands suggest permissions already assigned to that group.
     */
    @Test
    void nodeSuggestionsIncludeSelectedGroupAssignments() {
        groupService.createGroup("staff");
        groupService.setGroupPermission("staff", "example.group", PermissionValue.TRUE);

        assertEquals(List.of("clutchperms.admin", "example.group"), suggestionTexts("clutchperms group staff get "));
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

        assertEquals(List.of("Registered known permission node example.fly.", "Registered known permission node example.build.",
                "Known permission nodes: clutchperms.admin [built-in] - Allows managing ClutchPerms permissions., example.build [manual], "
                        + "example.fly [manual] - Allows flight",
                "Matched known permission nodes: example.fly [manual] - Allows flight", "Removed known permission node example.fly.", "No known permission nodes matched flight."),
                console.messages());
        assertEquals(3, environment.runtimeRefreshes());
    }

    /**
     * Confirms platform and built-in known nodes cannot be removed through the manual registry command.
     */
    @Test
    void nodeRegistryRemoveFailsForNonManualNodes() {
        environment.addPlatformNode("platform.node");
        TestSource console = TestSource.console();

        CommandSyntaxException builtInException = assertThrows(CommandSyntaxException.class, () -> dispatcher.execute("clutchperms nodes remove clutchperms.admin", console));
        CommandSyntaxException platformException = assertThrows(CommandSyntaxException.class, () -> dispatcher.execute("clutchperms nodes remove platform.node", console));

        assertTrue(builtInException.getMessage().contains("known permission node is not manually registered: clutchperms.admin"));
        assertTrue(platformException.getMessage().contains("known permission node is not manually registered: platform.node"));
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

        assertThrows(CommandSyntaxException.class, () -> dispatcher.execute("clutchperms nodes add example.*", console));
        dispatcher.execute("clutchperms user Target set example.* true", console);

        assertEquals(PermissionValue.TRUE, permissionService.getPermission(TARGET_ID, "example.*"));
    }

    private List<String> suggestionTexts(String command) {
        return dispatcher.getCompletionSuggestions(dispatcher.parse(command, TestSource.console())).join().getList().stream().map(Suggestion::getText).toList();
    }

    private static List<String> statusMessages(int knownSubjects) {
        return List.of(ClutchPermsCommands.STATUS_MESSAGE, "Permissions file: " + STATUS_DIAGNOSTICS.permissionsFile(), "Subjects file: " + STATUS_DIAGNOSTICS.subjectsFile(),
                "Groups file: " + STATUS_DIAGNOSTICS.groupsFile(), "Known nodes file: " + STATUS_DIAGNOSTICS.nodesFile(), "Known subjects: " + knownSubjects, "Known groups: 0",
                "Known permission nodes: 1", "Runtime bridge: " + STATUS_DIAGNOSTICS.runtimeBridgeStatus());
    }

    private static List<String> commandListMessages() {
        return List.of("ClutchPerms commands:", "/clutchperms status", "/clutchperms reload", "/clutchperms validate", "/clutchperms user <target> list",
                "/clutchperms user <target> get <node>", "/clutchperms user <target> set <node> <true|false>", "/clutchperms user <target> clear <node>",
                "/clutchperms user <target> groups", "/clutchperms user <target> group add <group>", "/clutchperms user <target> group remove <group>",
                "/clutchperms user <target> check <node>", "/clutchperms user <target> explain <node>", "/clutchperms group list", "/clutchperms group <group> create",
                "/clutchperms group <group> delete", "/clutchperms group <group> list", "/clutchperms group <group> get <node>",
                "/clutchperms group <group> set <node> <true|false>", "/clutchperms group <group> clear <node>", "/clutchperms group <group> parents",
                "/clutchperms group <group> parent add <parent>", "/clutchperms group <group> parent remove <parent>", "/clutchperms users list",
                "/clutchperms users search <name>", "/clutchperms nodes list", "/clutchperms nodes search <query>", "/clutchperms nodes add <node>",
                "/clutchperms nodes add <node> <description>", "/clutchperms nodes remove <node>");
    }

    private static final class TestEnvironment implements ClutchPermsCommandEnvironment<TestSource> {

        private final PermissionService permissionService;

        private final SubjectMetadataService subjectMetadataService;

        private final GroupService groupService;

        private final MutablePermissionNodeRegistry manualPermissionNodeRegistry;

        private final PermissionResolver permissionResolver;

        private final Map<String, CommandSubject> onlineSubjects = new LinkedHashMap<>();

        private final List<String> platformNodes = new ArrayList<>();

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
