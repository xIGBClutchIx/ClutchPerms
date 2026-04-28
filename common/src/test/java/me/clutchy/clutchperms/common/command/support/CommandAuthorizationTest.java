package me.clutchy.clutchperms.common.command;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.mojang.brigadier.exceptions.CommandSyntaxException;

import me.clutchy.clutchperms.common.permission.PermissionNodes;
import me.clutchy.clutchperms.common.permission.PermissionValue;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies shared Brigadier command behavior independent of a server platform.
 */
class CommandAuthorizationTest extends CommandTestBase {

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

        assertCommandUnavailable("clutchperms status", player);
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

        assertCommandUnavailable("clutchperms reload", player);
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
        assertCommandUnavailable("clutchperms group moderator delete", player);
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

        assertCommandUnavailable("clutchperms user Target info", player);

        permissionService.setPermission(ADMIN_ID, PermissionNodes.ADMIN_USER_INFO, PermissionValue.TRUE);
        dispatcher.execute("clutchperms user Target info", player);

        assertCommandUnavailable("clutchperms user Target list", player);
        assertCommandUnavailable("clutchperms group staff info", player);

        permissionService.setPermission(ADMIN_ID, PermissionNodes.ADMIN_GROUP_INFO, PermissionValue.TRUE);
        dispatcher.execute("clutchperms group staff info", player);

        assertCommandUnavailable("clutchperms group staff list", player);
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

        assertCommandUnavailable("clutchperms user Target clear-all", player);

        permissionService.setPermission(ADMIN_ID, PermissionNodes.ADMIN_USER_CLEAR_ALL, PermissionValue.TRUE);
        dispatcher.execute("clutchperms user Target clear-all", player);
        dispatcher.execute("clutchperms user Target clear-all", player);

        assertEquals(Map.of(), permissionService.getPermissions(TARGET_ID));
        assertCommandUnavailable("clutchperms user Target clear example.node", player);
        assertCommandUnavailable("clutchperms group staff clear-all", player);

        permissionService.setPermission(ADMIN_ID, PermissionNodes.ADMIN_GROUP_CLEAR_ALL, PermissionValue.TRUE);
        dispatcher.execute("clutchperms group staff clear-all", player);
        dispatcher.execute("clutchperms group staff clear-all", player);

        assertEquals(Map.of(), groupService.getGroupPermissions("staff"));
        assertCommandUnavailable("clutchperms group staff clear group.node", player);
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

        assertCommandUnavailable("clutchperms group staff members", player);

        permissionService.setPermission(ADMIN_ID, PermissionNodes.ADMIN_GROUP_MEMBERS, PermissionValue.TRUE);
        TestSource permittedPlayer = TestSource.player(ADMIN_ID);
        dispatcher.execute("clutchperms group staff members", permittedPlayer);

        assertEquals(List.of("Members of group staff (page 1/1):", "  00000000-0000-0000-0000-000000000002 (00000000-0000-0000-0000-000000000002)"), permittedPlayer.messages());
        assertCommandUnavailable("clutchperms group staff list", permittedPlayer);
        assertCommandUnavailable("clutchperms group staff parents", permittedPlayer);
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
        dispatcher.execute("clutchperms user Target clear-all", player);

        assertEquals(Map.of(), permissionService.getPermissions(TARGET_ID));
        assertCommandUnavailable("clutchperms group list", player);

        groupService.createGroup("staff");
        groupService.setGroupPermission("staff", "group.bulk", PermissionValue.TRUE);
        permissionService.setPermission(ADMIN_ID, "clutchperms.admin.group.*", PermissionValue.TRUE);
        dispatcher.execute("clutchperms group staff members", player);
        dispatcher.execute("clutchperms group staff clear-all", player);
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

        assertCommandUnavailable("clutchperms user Target set example.node false", player);
    }

    /**
     * Confirms player command completions hide root children that the player cannot run.
     */
    @Test
    void playerCommandTreeHidesUnauthorizedRootChildren() {
        TestSource player = TestSource.player(ADMIN_ID);

        assertEquals(List.of(), visibleChildNames(clutchPermsNode(), player));

        permissionService.setPermission(ADMIN_ID, PermissionNodes.ADMIN_STATUS, PermissionValue.TRUE);

        assertEquals(List.of("status"), visibleChildNames(clutchPermsNode(), player));
    }

    /**
     * Confirms player command trees expose only authorized nested user commands.
     */
    @Test
    void playerCommandTreeShowsOnlyAuthorizedNestedUserCommands() {
        permissionService.setPermission(ADMIN_ID, PermissionNodes.ADMIN_USER_INFO, PermissionValue.TRUE);
        TestSource player = TestSource.player(ADMIN_ID);

        assertEquals(List.of("user"), visibleChildNames(clutchPermsNode(), player));
        assertTrue(userTargetNode().canUse(player));
        assertEquals(List.of("info"), visibleChildNames(userTargetNode(), player));

        permissionService.setPermission(ADMIN_ID, PermissionNodes.ADMIN_USER_SET, PermissionValue.TRUE);

        assertEquals(List.of("info", "set"), visibleChildNames(userTargetNode(), player));
    }

    /**
     * Confirms player command trees expose only authorized nested group commands.
     */
    @Test
    void playerCommandTreeShowsOnlyAuthorizedNestedGroupCommands() {
        groupService.createGroup("staff");
        permissionService.setPermission(ADMIN_ID, PermissionNodes.ADMIN_GROUP_RENAME, PermissionValue.TRUE);
        TestSource player = TestSource.player(ADMIN_ID);

        assertEquals(List.of("group"), visibleChildNames(clutchPermsNode(), player));
        assertTrue(groupTargetNode().canUse(player));
        assertEquals(List.of("rename"), visibleChildNames(groupTargetNode(), player));
    }

}
