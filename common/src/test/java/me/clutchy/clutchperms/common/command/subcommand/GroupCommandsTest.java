package me.clutchy.clutchperms.common.command;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.mojang.brigadier.exceptions.CommandSyntaxException;

import me.clutchy.clutchperms.common.config.ClutchPermsBackupConfig;
import me.clutchy.clutchperms.common.config.ClutchPermsCommandConfig;
import me.clutchy.clutchperms.common.config.ClutchPermsConfig;
import me.clutchy.clutchperms.common.display.DisplayText;
import me.clutchy.clutchperms.common.group.GroupService;
import me.clutchy.clutchperms.common.permission.PermissionValue;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies shared Brigadier command behavior independent of a server platform.
 */
class GroupCommandsTest extends CommandTestBase {

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

        assertEquals(List.of("Group staff:", "  name staff", "  direct permissions 1", "  parents base", "  child groups child", "  tracks none",
                "  explicit members Target (00000000-0000-0000-0000-000000000002)", "  prefix &7[Staff]", "  suffix unset"), console.messages());
        assertSuggests(console.commandMessages().get(2), "/clutchperms group staff list");
        assertSuggests(console.commandMessages().get(3), "/clutchperms group staff parents");
        assertSuggests(console.commandMessages().get(5), "/clutchperms track list");
        assertSuggests(console.commandMessages().get(6), "/clutchperms group staff members");
        assertSuggests(console.commandMessages().get(7), "/clutchperms group staff prefix get");
        assertSuggests(console.commandMessages().get(8), "/clutchperms group staff suffix get");
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
                "  tracks none", "  explicit members none", "  prefix &8[Default]", "  suffix unset"), console.messages());
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
        dispatcher.execute("clutchperms group admin delete", console);

        assertEquals(PermissionValue.UNSET, groupService.getGroups().contains("admin") ? groupService.getGroupPermission("admin", "example.node") : PermissionValue.UNSET);
        assertEquals(
                List.of("Created group admin.", "Set example.node for group admin to TRUE.", "Added Target (00000000-0000-0000-0000-000000000002) to group admin.",
                        "Groups for Target (00000000-0000-0000-0000-000000000002) (page 1/1):", "  admin", "  default (implicit)",
                        "Target (00000000-0000-0000-0000-000000000002) effective example.node = TRUE from group admin.", "Group admin (page 1/1):",
                        "  permission example.node=TRUE", "  member Target (00000000-0000-0000-0000-000000000002)", "Group admin has example.node = TRUE.",
                        "Cleared example.node for group admin.", "Removed Target (00000000-0000-0000-0000-000000000002) from group admin.",
                        "Destructive command confirmation required.", "Repeat this command within 30 seconds to confirm: /clutchperms group admin delete", "Deleted group admin."),
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
     * Confirms idempotent group mutations warn and skip audit writes.
     *
     * @throws CommandSyntaxException when command execution fails unexpectedly
     */
    @Test
    void groupMutationNoOpsWarnWithoutAudit() throws CommandSyntaxException {
        groupService.createGroup("staff");
        groupService.createGroup("base");
        TestSource console = TestSource.console();

        dispatcher.execute("clutchperms group staff create", console);
        dispatcher.execute("clutchperms group staff rename Staff", console);
        dispatcher.execute("clutchperms group staff set example.node true", console);
        dispatcher.execute("clutchperms group staff set example.node true", console);
        dispatcher.execute("clutchperms group staff clear example.node", console);
        dispatcher.execute("clutchperms group staff clear example.node", console);
        dispatcher.execute("clutchperms group staff parent add base", console);
        dispatcher.execute("clutchperms group staff parent add base", console);
        dispatcher.execute("clutchperms group staff parent remove base", console);
        dispatcher.execute("clutchperms group staff parent remove base", console);
        dispatcher.execute("clutchperms group staff prefix set &7[Staff]", console);
        dispatcher.execute("clutchperms group staff prefix set &7[Staff]", console);
        dispatcher.execute("clutchperms group staff prefix clear", console);
        dispatcher.execute("clutchperms group staff prefix clear", console);

        assertEquals(Set.of(), groupService.getGroupParents("staff"));
        assertEquals(PermissionValue.UNSET, groupService.getGroupPermission("staff", "example.node"));
        assertEquals(6, environment.auditLogService().listNewestFirst().size());
        assertMessageContains(console, "Group staff already exists.");
        assertMessageContains(console, "Group staff is already named staff.");
        assertMessageContains(console, "Group staff already has example.node = TRUE.");
        assertMessageContains(console, "Group staff already has example.node unset.");
        assertMessageContains(console, "Parent group base is already linked to group staff.");
        assertMessageContains(console, "Parent group base is already absent from group staff.");
        assertMessageContains(console, "Group staff prefix is already &7[Staff].");
        assertMessageContains(console, "Group staff prefix is already unset.");
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
     * Confirms the protected op group grants wildcard permissions only to explicit members.
     *
     * @throws CommandSyntaxException when command execution fails unexpectedly
     */
    @Test
    void opGroupGrantsWildcardToExplicitMembersOnly() throws CommandSyntaxException {
        subjectMetadataService.recordSubject(TARGET_ID, "Target", FIRST_SEEN);
        TestSource console = TestSource.console();

        dispatcher.execute("clutchperms user Target check example.node", console);
        dispatcher.execute("clutchperms user Target group add OP", console);
        dispatcher.execute("clutchperms user Target groups", console);
        dispatcher.execute("clutchperms user Target check example.node", console);
        dispatcher.execute("clutchperms group op info", console);
        dispatcher.execute("clutchperms user Target group remove op", console);
        dispatcher.execute("clutchperms user Target check example.node", console);

        assertEquals(Set.of(), groupService.getSubjectGroups(TARGET_ID));
        assertEquals(Map.of("*", PermissionValue.TRUE), groupService.getGroupPermissions("op"));
        assertEquals(List.of("Target (00000000-0000-0000-0000-000000000002) effective example.node = UNSET from unset.",
                "Added Target (00000000-0000-0000-0000-000000000002) to group op.", "Groups for Target (00000000-0000-0000-0000-000000000002) (page 1/1):", "  op",
                "  default (implicit)", "Target (00000000-0000-0000-0000-000000000002) effective example.node = TRUE from group op via *.", "Group op:", "  name op",
                "  op group grants * to explicit members only", "  direct permissions 1", "  parents none", "  child groups none", "  tracks none",
                "  explicit members Target (00000000-0000-0000-0000-000000000002)", "  prefix unset", "  suffix unset",
                "Removed Target (00000000-0000-0000-0000-000000000002) from group op.", "Target (00000000-0000-0000-0000-000000000002) effective example.node = UNSET from unset."),
                console.messages());
    }

    /**
     * Confirms op group definition commands are rejected while membership commands remain available.
     *
     * @throws CommandSyntaxException when command setup fails unexpectedly
     */
    @Test
    void opGroupDefinitionIsProtected() throws CommandSyntaxException {
        TestSource console = TestSource.console();
        dispatcher.execute("clutchperms group staff create", console);

        assertCommandFails("clutchperms group op delete", console, "Group operation failed: op group cannot be deleted");
        assertCommandFails("clutchperms group op rename owner", console, "Group operation failed: op group cannot be renamed");
        assertCommandFails("clutchperms group staff rename op", console, "Group operation failed: group cannot be renamed to op");
        assertCommandFails("clutchperms group op set example.node true", console, "Group operation failed: op group permissions are protected");
        assertCommandFails("clutchperms group op clear *", console, "Group operation failed: op group permissions are protected");
        assertCommandFails("clutchperms group op clear-all", console, "Group operation failed: op group permissions are protected");
        assertCommandFails("clutchperms group op prefix set &c[OP]", console, "Display operation failed: op group display is protected");
        assertCommandFails("clutchperms group op parent add staff", console, "Group operation failed: op group inheritance is protected");
        assertCommandFails("clutchperms group staff parent add op", console, "Group operation failed: op group inheritance is protected");

        assertTrue(groupService.hasGroup("op"));
        assertEquals(Map.of("*", PermissionValue.TRUE), groupService.getGroupPermissions("op"));
        assertEquals(Set.of(), groupService.getGroupMembers("op"));
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
        assertCommandFails("clutchperms group staff rename op", console, "Group operation failed: group cannot be renamed to op");
        assertCommandFails("clutchperms group staff rename admin", console, "Group operation failed: group already exists: admin");

        assertTrue(groupService.hasGroup("staff"));
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
     * Confirms add suggestions exclude the implicit default group and groups already assigned to the target while keeping op assignable.
     */
    @Test
    void userGroupAddSuggestionsExcludeDefaultAndAssignedGroups() {
        groupService.createGroup("staff");
        groupService.createGroup("builder");
        groupService.addSubjectGroup(TARGET_ID, "staff");

        assertEquals(List.of("builder", "op"), suggestionTexts("clutchperms user Target group add "));
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
     * Confirms parent suggestions include valid groups and exclude the current group and existing parents.
     */
    @Test
    void parentSuggestionsIncludeValidGroups() {
        groupService.createGroup("admin");
        groupService.createGroup("staff");
        groupService.addGroupParent("admin", "staff");

        assertEquals(List.of("default"), suggestionTexts("clutchperms group admin parent add "));
    }

}
