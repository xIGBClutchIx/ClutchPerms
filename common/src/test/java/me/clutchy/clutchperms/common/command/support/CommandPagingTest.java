package me.clutchy.clutchperms.common.command;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.mojang.brigadier.exceptions.CommandSyntaxException;

import me.clutchy.clutchperms.common.config.ClutchPermsBackupConfig;
import me.clutchy.clutchperms.common.config.ClutchPermsCommandConfig;
import me.clutchy.clutchperms.common.config.ClutchPermsConfig;
import me.clutchy.clutchperms.common.permission.PermissionValue;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies shared Brigadier command behavior independent of a server platform.
 */
class CommandPagingTest extends CommandTestBase {

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

}
