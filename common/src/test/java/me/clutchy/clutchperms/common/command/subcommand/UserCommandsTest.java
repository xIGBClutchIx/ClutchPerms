package me.clutchy.clutchperms.common.command;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import me.clutchy.clutchperms.common.display.DisplayText;
import me.clutchy.clutchperms.common.group.GroupService;
import me.clutchy.clutchperms.common.permission.PermissionResolver;
import me.clutchy.clutchperms.common.permission.PermissionValue;
import me.clutchy.clutchperms.common.storage.PermissionStorageException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies shared Brigadier command behavior independent of a server platform.
 */
class UserCommandsTest extends CommandTestBase {

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
        dispatcher.execute("clutchperms user Target clear-all", console);
        dispatcher.execute("clutchperms group staff clear-all", console);
        dispatcher.execute("clutchperms group staff clear-all", console);

        assertEquals(Map.of(), permissionService.getPermissions(TARGET_ID));
        assertEquals("&a[Target]", subjectMetadataService.getSubjectDisplay(TARGET_ID).prefix().orElseThrow().rawText());
        assertEquals(Map.of(), groupService.getGroupPermissions("staff"));
        assertEquals("&7[Staff]", groupService.getGroupDisplay("staff").prefix().orElseThrow().rawText());
        assertEquals(Set.of("staff"), groupService.getSubjectGroups(TARGET_ID));
        assertEquals(
                List.of("Destructive command confirmation required.", "Repeat this command within 30 seconds to confirm: /clutchperms user Target clear-all",
                        "Cleared 2 direct permissions for Target (00000000-0000-0000-0000-000000000002).", "Destructive command confirmation required.",
                        "Repeat this command within 30 seconds to confirm: /clutchperms group staff clear-all", "Cleared 2 direct permissions for group staff."),
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
        dispatcher.execute("clutchperms group default clear-all", console);

        assertTrue(groupService.hasGroup(GroupService.DEFAULT_GROUP));
        assertEquals(Map.of(), groupService.getGroupPermissions(GroupService.DEFAULT_GROUP));
        assertEquals(List.of("Destructive command confirmation required.", "Repeat this command within 30 seconds to confirm: /clutchperms group default clear-all",
                "Cleared 2 direct permissions for group default."), console.messages());
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
                "  groups default (implicit), staff", "  tracks none", "  direct prefix &a[Target]", "  effective prefix &a[Target] from direct", "  direct suffix unset",
                "  effective suffix &f* from group staff"), console.messages());
        assertSuggests(console.commandMessages().get(3), "/clutchperms user 00000000-0000-0000-0000-000000000002 list");
        assertSuggests(console.commandMessages().get(5), "/clutchperms user 00000000-0000-0000-0000-000000000002 groups");
        assertSuggests(console.commandMessages().get(6), "/clutchperms user 00000000-0000-0000-0000-000000000002 tracks");
        assertSuggests(console.commandMessages().get(7), "/clutchperms user 00000000-0000-0000-0000-000000000002 prefix get");
        assertSuggests(console.commandMessages().get(9), "/clutchperms user 00000000-0000-0000-0000-000000000002 suffix get");
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
                "  effective permissions 0", "  groups default (implicit)", "  tracks none", "  direct prefix unset", "  effective prefix unset", "  direct suffix unset",
                "  effective suffix unset"), console.messages());
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
     * Confirms idempotent user mutation commands warn and skip audit writes.
     *
     * @throws CommandSyntaxException when command execution fails unexpectedly
     */
    @Test
    void userMutationNoOpsWarnWithoutAudit() throws CommandSyntaxException {
        groupService.createGroup("staff");
        TestSource console = TestSource.console();

        dispatcher.execute("clutchperms user Target set example.node true", console);
        dispatcher.execute("clutchperms user Target set example.node true", console);
        dispatcher.execute("clutchperms user Target clear example.node", console);
        dispatcher.execute("clutchperms user Target clear example.node", console);
        dispatcher.execute("clutchperms user Target prefix set &aTarget", console);
        dispatcher.execute("clutchperms user Target prefix set &aTarget", console);
        dispatcher.execute("clutchperms user Target prefix clear", console);
        dispatcher.execute("clutchperms user Target prefix clear", console);
        dispatcher.execute("clutchperms user Target group add staff", console);
        dispatcher.execute("clutchperms user Target group add staff", console);
        dispatcher.execute("clutchperms user Target group remove staff", console);
        dispatcher.execute("clutchperms user Target group remove staff", console);

        assertEquals(Set.of(), groupService.getSubjectGroups(TARGET_ID));
        assertEquals(PermissionValue.UNSET, permissionService.getPermission(TARGET_ID, "example.node"));
        assertEquals(6, environment.auditLogService().listNewestFirst().size());
        assertMessageContains(console, "Target (00000000-0000-0000-0000-000000000002) already has example.node = TRUE.");
        assertMessageContains(console, "Target (00000000-0000-0000-0000-000000000002) already has example.node unset.");
        assertMessageContains(console, "Target (00000000-0000-0000-0000-000000000002) direct prefix is already &aTarget.");
        assertMessageContains(console, "Target (00000000-0000-0000-0000-000000000002) direct prefix is already unset.");
        assertMessageContains(console, "Target (00000000-0000-0000-0000-000000000002) is already in group staff.");
        assertMessageContains(console, "Target (00000000-0000-0000-0000-000000000002) is already not in group staff.");
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
        environment = new TestEnvironment(permissionService, subjectMetadataService, groupService, trackService, manualPermissionNodeRegistry, permissionResolver);
        environment.addOnlineSubject("Target", TARGET_ID);
        dispatcher = new CommandDispatcher<>();
        ClutchPermsCommands.ROOT_LITERALS.forEach(rootLiteral -> dispatcher.getRoot().addChild(ClutchPermsCommands.create(environment, rootLiteral)));
        TestSource console = TestSource.console();

        assertEquals(0, dispatcher.execute("clutchperms user Target set example.node false", console));
        assertEquals(List.of("Permission operation failed: save blocked"), console.messages());

        console.messages().clear();
        assertEquals(0, dispatcher.execute("clutchperms user Target clear example.node", console));
        assertEquals(List.of("Permission operation failed: save blocked"), console.messages());

        console.messages().clear();
        assertEquals(1, dispatcher.execute("clutchperms user Target clear-all", console));
        console.messages().clear();
        assertEquals(0, dispatcher.execute("clutchperms user Target clear-all", console));
        assertEquals(List.of("Permission operation failed: save blocked"), console.messages());
    }

}
