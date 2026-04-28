package me.clutchy.clutchperms.common.command;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.mojang.brigadier.exceptions.CommandSyntaxException;

import me.clutchy.clutchperms.common.permission.PermissionNodes;
import me.clutchy.clutchperms.common.permission.PermissionValue;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies shared Brigadier command behavior independent of a server platform.
 */
class NodesCommandsTest extends CommandTestBase {

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

}
