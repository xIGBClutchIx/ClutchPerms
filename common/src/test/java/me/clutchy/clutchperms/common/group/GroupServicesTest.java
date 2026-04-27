package me.clutchy.clutchperms.common.group;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import me.clutchy.clutchperms.common.display.DisplayText;
import me.clutchy.clutchperms.common.permission.InMemoryPermissionService;
import me.clutchy.clutchperms.common.permission.PermissionResolution;
import me.clutchy.clutchperms.common.permission.PermissionResolver;
import me.clutchy.clutchperms.common.permission.PermissionService;
import me.clutchy.clutchperms.common.permission.PermissionValue;
import me.clutchy.clutchperms.common.storage.PermissionStorageException;
import me.clutchy.clutchperms.common.storage.SqliteDependencyMode;
import me.clutchy.clutchperms.common.storage.SqliteStore;
import me.clutchy.clutchperms.common.storage.SqliteTestSupport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies SQLite-backed group service loading, persistence, observing, and effective resolution.
 */
class GroupServicesTest {

    private static final UUID FIRST_SUBJECT = UUID.fromString("00000000-0000-0000-0000-000000000001");

    private static final UUID SECOND_SUBJECT = UUID.fromString("00000000-0000-0000-0000-000000000002");

    @TempDir
    private Path temporaryDirectory;

    @Test
    void missingDatabaseCreatesDefaultGroupSchema() {
        Path databaseFile = SqliteTestSupport.databaseFile(temporaryDirectory);

        try (SqliteStore store = SqliteTestSupport.open(databaseFile)) {
            GroupService groupService = GroupServices.sqlite(store);

            assertEquals(Set.of("default", "op"), groupService.getGroups());
            assertTrue(groupService.hasGroup("default"));
            assertTrue(groupService.hasGroup("op"));
            assertEquals(PermissionValue.TRUE, groupService.getGroupPermission("op", "*"));
            assertEquals(Map.of("*", PermissionValue.TRUE), groupService.getGroupPermissions("op"));
            assertEquals(Set.of(), groupService.getGroupMembers("op"));
            assertEquals(0, groupService.clearGroupPermissions("default"));
        }

        assertTrue(Files.exists(databaseFile));
    }

    @Test
    void groupsRoundTripThroughSqlite() {
        Path databaseFile = SqliteTestSupport.databaseFile(temporaryDirectory);
        try (SqliteStore store = SqliteTestSupport.open(databaseFile)) {
            GroupService groupService = GroupServices.sqlite(store);
            groupService.createGroup("Staff");
            groupService.createGroup("Admin");
            groupService.setGroupPermission("staff", "Staff.Node", PermissionValue.TRUE);
            groupService.setGroupPermission("staff", "Staff.*", PermissionValue.FALSE);
            groupService.setGroupPermission("admin", "Example.Node", PermissionValue.TRUE);
            groupService.setGroupPermission("admin", "Example.Denied", PermissionValue.FALSE);
            groupService.setGroupPrefix("admin", DisplayText.parse("&c[Admin]"));
            groupService.setGroupSuffix("admin", DisplayText.parse("&f*"));
            groupService.addGroupParent("admin", "staff");
            groupService.addSubjectGroup(FIRST_SUBJECT, "admin");
        }

        try (SqliteStore store = SqliteTestSupport.open(databaseFile)) {
            GroupService reloadedGroupService = GroupServices.sqlite(store);

            assertEquals(Set.of("admin", "default", "op", "staff"), reloadedGroupService.getGroups());
            assertEquals(PermissionValue.TRUE, reloadedGroupService.getGroupPermission("admin", "example.node"));
            assertEquals(PermissionValue.FALSE, reloadedGroupService.getGroupPermission("admin", "example.denied"));
            assertEquals(PermissionValue.FALSE, reloadedGroupService.getGroupPermission("staff", "staff.*"));
            assertEquals(Map.of("example.node", PermissionValue.TRUE, "example.denied", PermissionValue.FALSE), reloadedGroupService.getGroupPermissions("admin"));
            assertEquals("&c[Admin]", reloadedGroupService.getGroupDisplay("admin").prefix().orElseThrow().rawText());
            assertEquals("&f*", reloadedGroupService.getGroupDisplay("admin").suffix().orElseThrow().rawText());
            assertEquals(Set.of("staff"), reloadedGroupService.getGroupParents("admin"));
            assertEquals(Set.of("admin"), reloadedGroupService.getSubjectGroups(FIRST_SUBJECT));
            assertEquals(Set.of(FIRST_SUBJECT), reloadedGroupService.getGroupMembers("admin"));
            assertEquals(Map.of("*", PermissionValue.TRUE), reloadedGroupService.getGroupPermissions("op"));
        }
    }

    @Test
    void renameGroupPreservesStateAndPersistsThroughSqlite() {
        Path databaseFile = SqliteTestSupport.databaseFile(temporaryDirectory);
        try (SqliteStore store = SqliteTestSupport.open(databaseFile)) {
            GroupService groupService = GroupServices.sqlite(store);
            groupService.createGroup("base");
            groupService.setGroupPermission("base", "base.node", PermissionValue.TRUE);
            groupService.createGroup("staff");
            groupService.setGroupPermission("staff", "staff.node", PermissionValue.FALSE);
            groupService.setGroupPrefix("staff", DisplayText.parse("&7[Staff]"));
            groupService.setGroupSuffix("staff", DisplayText.parse("&f*"));
            groupService.addGroupParent("staff", "base");
            groupService.addSubjectGroup(FIRST_SUBJECT, "staff");
            groupService.createGroup("child");
            groupService.addGroupParent("child", "staff");

            groupService.renameGroup("staff", "Moderator");
        }

        try (SqliteStore store = SqliteTestSupport.open(databaseFile)) {
            GroupService reloadedGroupService = GroupServices.sqlite(store);

            assertFalse(reloadedGroupService.hasGroup("staff"));
            assertTrue(reloadedGroupService.hasGroup("moderator"));
            assertEquals(PermissionValue.FALSE, reloadedGroupService.getGroupPermission("moderator", "staff.node"));
            assertEquals("&7[Staff]", reloadedGroupService.getGroupDisplay("moderator").prefix().orElseThrow().rawText());
            assertEquals("&f*", reloadedGroupService.getGroupDisplay("moderator").suffix().orElseThrow().rawText());
            assertEquals(Set.of("base"), reloadedGroupService.getGroupParents("moderator"));
            assertEquals(Set.of("moderator"), reloadedGroupService.getGroupParents("child"));
            assertEquals(Set.of("moderator"), reloadedGroupService.getSubjectGroups(FIRST_SUBJECT));
            assertEquals(Set.of(FIRST_SUBJECT), reloadedGroupService.getGroupMembers("moderator"));
        }
    }

    @Test
    void clearGroupPermissionsRemovesOnlyDirectPermissionsAndPersists() {
        Path databaseFile = SqliteTestSupport.databaseFile(temporaryDirectory);
        try (SqliteStore store = SqliteTestSupport.open(databaseFile)) {
            GroupService groupService = GroupServices.sqlite(store);
            groupService.createGroup("base");
            groupService.createGroup("staff");
            groupService.setGroupPermission("staff", "staff.node", PermissionValue.TRUE);
            groupService.setGroupPermission("staff", "staff.*", PermissionValue.FALSE);
            groupService.setGroupPrefix("staff", DisplayText.parse("&7[Staff]"));
            groupService.addGroupParent("staff", "base");
            groupService.addSubjectGroup(FIRST_SUBJECT, "staff");
            groupService.setGroupPermission("base", "base.node", PermissionValue.TRUE);

            assertEquals(2, groupService.clearGroupPermissions("staff"));
            assertEquals(0, groupService.clearGroupPermissions("staff"));
        }

        try (SqliteStore store = SqliteTestSupport.open(databaseFile)) {
            GroupService reloadedGroupService = GroupServices.sqlite(store);
            assertEquals(Map.of(), reloadedGroupService.getGroupPermissions("staff"));
            assertEquals(Map.of("base.node", PermissionValue.TRUE), reloadedGroupService.getGroupPermissions("base"));
            assertEquals("&7[Staff]", reloadedGroupService.getGroupDisplay("staff").prefix().orElseThrow().rawText());
            assertEquals(Set.of("base"), reloadedGroupService.getGroupParents("staff"));
            assertEquals(Set.of("staff"), reloadedGroupService.getSubjectGroups(FIRST_SUBJECT));
        }
    }

    @Test
    void failedWritesDoNotCommitGroupMutations() {
        Path databaseFile = SqliteTestSupport.databaseFile(temporaryDirectory);
        SqliteStore store = SqliteTestSupport.open(databaseFile);
        GroupService groupService = GroupServices.sqlite(store);
        groupService.createGroup("base");
        groupService.createGroup("guest");
        groupService.createGroup("staff");
        groupService.setGroupPermission("staff", "old.node", PermissionValue.TRUE);
        groupService.setGroupPrefix("staff", DisplayText.parse("&7[Old]"));
        groupService.addGroupParent("staff", "base");
        groupService.addSubjectGroup(FIRST_SUBJECT, "staff");
        store.close();

        assertThrows(PermissionStorageException.class, () -> groupService.createGroup("new"));
        assertGroupStatePreserved(groupService, databaseFile);

        assertThrows(PermissionStorageException.class, () -> groupService.deleteGroup("staff"));
        assertGroupStatePreserved(groupService, databaseFile);

        assertThrows(PermissionStorageException.class, () -> groupService.renameGroup("staff", "renamed"));
        assertGroupStatePreserved(groupService, databaseFile);

        assertThrows(PermissionStorageException.class, () -> groupService.setGroupPermission("staff", "new.node", PermissionValue.FALSE));
        assertGroupStatePreserved(groupService, databaseFile);

        assertThrows(PermissionStorageException.class, () -> groupService.clearGroupPermission("staff", "old.node"));
        assertGroupStatePreserved(groupService, databaseFile);

        assertThrows(PermissionStorageException.class, () -> groupService.setGroupPrefix("staff", DisplayText.parse("&c[New]")));
        assertGroupStatePreserved(groupService, databaseFile);

        assertThrows(PermissionStorageException.class, () -> groupService.addSubjectGroup(SECOND_SUBJECT, "base"));
        assertGroupStatePreserved(groupService, databaseFile);

        assertThrows(PermissionStorageException.class, () -> groupService.addGroupParent("guest", "base"));
        assertGroupStatePreserved(groupService, databaseFile);
    }

    @Test
    void duplicateNormalizedSqliteGroupsFailLoad() {
        Path databaseFile = SqliteTestSupport.databaseFile(temporaryDirectory);
        try (SqliteStore store = SqliteTestSupport.open(databaseFile)) {
            store.write(connection -> {
                try (Statement statement = connection.createStatement()) {
                    statement.executeUpdate("INSERT INTO groups (name, prefix, suffix) VALUES ('Admin', NULL, NULL)");
                    statement.executeUpdate("INSERT INTO groups (name, prefix, suffix) VALUES (' admin ', NULL, NULL)");
                }
            });
        }

        try (SqliteStore store = SqliteTestSupport.open(databaseFile)) {
            assertThrows(PermissionStorageException.class, () -> GroupServices.sqlite(store));
        }
    }

    @Test
    void invalidSqliteDisplayRowsFailLoad() {
        Path databaseFile = SqliteTestSupport.databaseFile(temporaryDirectory);
        try (SqliteStore store = SqliteTestSupport.open(databaseFile)) {
            store.write(connection -> {
                try (Statement statement = connection.createStatement()) {
                    statement.executeUpdate("UPDATE groups SET prefix = '§cBad' WHERE name = 'default'");
                }
            });
        }

        try (SqliteStore store = SqliteTestSupport.open(databaseFile)) {
            assertThrows(PermissionStorageException.class, () -> GroupServices.sqlite(store));
        }
    }

    @Test
    void invalidSqliteParentsAndMembershipsFailLoad() {
        assertInvalidGroupDatabase("INSERT INTO group_parents (group_name, parent_name) VALUES ('default', 'default')");
        assertInvalidGroupDatabase("INSERT INTO memberships (subject_id, group_name) VALUES ('00000000-0000-0000-0000-000000000001', 'default')");
    }

    @Test
    void invalidSqliteOpDefinitionsFailLoad() {
        assertInvalidProtectedOpDatabase("UPDATE groups SET prefix = '&c[OP]' WHERE name = 'op'");
        assertInvalidProtectedOpDatabase("DELETE FROM group_permissions WHERE group_name = 'op'");
        assertInvalidProtectedOpDatabase("INSERT INTO group_permissions (group_name, node, value) VALUES ('op', 'extra.node', 'TRUE')");
        assertInvalidProtectedOpDatabase("INSERT INTO group_parents (group_name, parent_name) VALUES ('op', 'staff')");
        assertInvalidProtectedOpDatabase("INSERT INTO group_parents (group_name, parent_name) VALUES ('staff', 'op')");
    }

    @Test
    void parentMutationsRejectInvalidLinks() {
        GroupService groupService = new InMemoryGroupService();
        groupService.createGroup("admin");
        groupService.createGroup("staff");
        groupService.createGroup("base");

        assertThrows(IllegalArgumentException.class, () -> groupService.addGroupParent("admin", "missing"));
        assertThrows(IllegalArgumentException.class, () -> groupService.addGroupParent("admin", "admin"));

        groupService.addGroupParent("admin", "staff");
        groupService.addGroupParent("staff", "base");

        assertThrows(IllegalArgumentException.class, () -> groupService.addGroupParent("base", "admin"));
    }

    @Test
    void builtInGroupsAreAlwaysPresentAndProtected() {
        GroupService groupService = new InMemoryGroupService();

        IllegalArgumentException defaultDelete = assertThrows(IllegalArgumentException.class, () -> groupService.deleteGroup("default"));
        IllegalArgumentException opDelete = assertThrows(IllegalArgumentException.class, () -> groupService.deleteGroup("op"));
        IllegalArgumentException opRename = assertThrows(IllegalArgumentException.class, () -> groupService.renameGroup("op", "owner"));
        IllegalArgumentException renameToOp = assertThrows(IllegalArgumentException.class, () -> {
            groupService.createGroup("staff");
            groupService.renameGroup("staff", "op");
        });

        assertEquals("default group cannot be deleted", defaultDelete.getMessage());
        assertEquals("op group cannot be deleted", opDelete.getMessage());
        assertEquals("op group cannot be renamed", opRename.getMessage());
        assertEquals("group cannot be renamed to op", renameToOp.getMessage());
        assertEquals(Set.of("default", "op", "staff"), groupService.getGroups());
        assertTrue(groupService.hasGroup("default"));
        assertTrue(groupService.hasGroup("op"));
        assertEquals(Map.of("*", PermissionValue.TRUE), groupService.getGroupPermissions("op"));
    }

    @Test
    void opGroupAllowsOnlyExplicitMembershipMutations() {
        GroupService groupService = new InMemoryGroupService();
        groupService.createGroup("staff");

        assertThrows(IllegalArgumentException.class, () -> groupService.setGroupPermission("op", "example.node", PermissionValue.TRUE));
        assertThrows(IllegalArgumentException.class, () -> groupService.clearGroupPermission("op", "*"));
        assertThrows(IllegalArgumentException.class, () -> groupService.clearGroupPermissions("op"));
        assertThrows(IllegalArgumentException.class, () -> groupService.setGroupPrefix("op", DisplayText.parse("&c[OP]")));
        assertThrows(IllegalArgumentException.class, () -> groupService.addGroupParent("op", "staff"));
        assertThrows(IllegalArgumentException.class, () -> groupService.addGroupParent("staff", "op"));

        groupService.addSubjectGroup(FIRST_SUBJECT, "op");
        assertEquals(Set.of("op"), groupService.getSubjectGroups(FIRST_SUBJECT));
        assertEquals(Set.of(FIRST_SUBJECT), groupService.getGroupMembers("op"));
        groupService.removeSubjectGroup(FIRST_SUBJECT, "op");
        assertEquals(Set.of(), groupService.getSubjectGroups(FIRST_SUBJECT));
        assertEquals(Set.of(), groupService.getGroupMembers("op"));
        assertEquals(Map.of("*", PermissionValue.TRUE), groupService.getGroupPermissions("op"));
    }

    @Test
    void resolverAppliesOpOnlyToExplicitMembers() {
        PermissionService permissionService = new InMemoryPermissionService();
        GroupService groupService = new InMemoryGroupService();
        PermissionResolver resolver = new PermissionResolver(permissionService, groupService);

        assertEquals(PermissionValue.UNSET, resolver.resolve(FIRST_SUBJECT, "example.node").value());
        groupService.addSubjectGroup(FIRST_SUBJECT, "op");
        resolver.invalidateSubject(FIRST_SUBJECT);

        assertEquals(PermissionValue.TRUE, resolver.resolve(FIRST_SUBJECT, "example.node").value());
        assertEquals(PermissionResolution.Source.GROUP, resolver.resolve(FIRST_SUBJECT, "example.node").source());
        assertEquals("op", resolver.resolve(FIRST_SUBJECT, "example.node").groupName());
        assertEquals("*", resolver.resolve(FIRST_SUBJECT, "example.node").assignmentNode());
        assertEquals(PermissionValue.UNSET, resolver.resolve(SECOND_SUBJECT, "example.node").value());
    }

    @Test
    void resolverAppliesDirectGroupAndDefaultPrecedence() {
        PermissionService permissionService = new InMemoryPermissionService();
        GroupService groupService = new InMemoryGroupService();
        PermissionResolver resolver = new PermissionResolver(permissionService, groupService);

        groupService.createGroup("base");
        groupService.setGroupPermission("base", "parent.node", PermissionValue.TRUE);
        groupService.setGroupPermission("base", "shared.node", PermissionValue.FALSE);
        groupService.createGroup("trusted");
        groupService.setGroupPermission("trusted", "same-depth.node", PermissionValue.TRUE);
        groupService.createGroup("restricted");
        groupService.setGroupPermission("restricted", "same-depth.node", PermissionValue.FALSE);
        groupService.setGroupPermission("default", "example.node", PermissionValue.TRUE);
        groupService.createGroup("default-parent");
        groupService.setGroupPermission("default-parent", "default.parent", PermissionValue.TRUE);
        groupService.addGroupParent("default", "default-parent");
        groupService.createGroup("staff");
        groupService.setGroupPermission("staff", "example.node", PermissionValue.FALSE);
        groupService.setGroupPermission("staff", "shared.node", PermissionValue.TRUE);
        groupService.setGroupPermission("staff", "staff.node", PermissionValue.TRUE);
        groupService.addGroupParent("staff", "base");
        groupService.addGroupParent("staff", "trusted");
        groupService.addGroupParent("staff", "restricted");
        groupService.createGroup("builder");
        groupService.setGroupPermission("builder", "staff.node", PermissionValue.FALSE);
        groupService.addSubjectGroup(FIRST_SUBJECT, "staff");
        groupService.addSubjectGroup(FIRST_SUBJECT, "builder");

        assertEquals(PermissionValue.FALSE, resolver.resolve(FIRST_SUBJECT, "example.node").value());
        assertEquals(PermissionResolution.Source.GROUP, resolver.resolve(FIRST_SUBJECT, "example.node").source());
        assertEquals(PermissionValue.FALSE, resolver.resolve(FIRST_SUBJECT, "staff.node").value());
        assertEquals(PermissionValue.TRUE, resolver.resolve(FIRST_SUBJECT, "parent.node").value());
        assertEquals("base", resolver.resolve(FIRST_SUBJECT, "parent.node").groupName());
        assertEquals(PermissionValue.TRUE, resolver.resolve(FIRST_SUBJECT, "shared.node").value());
        assertEquals("staff", resolver.resolve(FIRST_SUBJECT, "shared.node").groupName());
        assertEquals(PermissionValue.FALSE, resolver.resolve(FIRST_SUBJECT, "same-depth.node").value());
        assertEquals("restricted", resolver.resolve(FIRST_SUBJECT, "same-depth.node").groupName());
        assertEquals(PermissionValue.TRUE, resolver.resolve(SECOND_SUBJECT, "example.node").value());
        assertEquals(PermissionValue.TRUE, resolver.resolve(SECOND_SUBJECT, "default.parent").value());
        assertEquals(PermissionResolution.Source.DEFAULT, resolver.resolve(SECOND_SUBJECT, "default.parent").source());
        assertEquals("default-parent", resolver.resolve(SECOND_SUBJECT, "default.parent").groupName());

        permissionService.setPermission(FIRST_SUBJECT, "example.node", PermissionValue.TRUE);
        resolver.invalidateSubject(FIRST_SUBJECT);

        assertEquals(PermissionValue.TRUE, resolver.resolve(FIRST_SUBJECT, "example.node").value());
        assertEquals(PermissionResolution.Source.DIRECT, resolver.resolve(FIRST_SUBJECT, "example.node").source());
        assertEquals(Map.of("example.node", PermissionValue.TRUE, "staff.node", PermissionValue.FALSE, "parent.node", PermissionValue.TRUE, "shared.node", PermissionValue.TRUE,
                "same-depth.node", PermissionValue.FALSE, "default.parent", PermissionValue.TRUE), resolver.getEffectivePermissions(FIRST_SUBJECT));
    }

    @Test
    void resolverAppliesWildcardPermissionPrecedence() {
        PermissionService permissionService = new InMemoryPermissionService();
        GroupService groupService = new InMemoryGroupService();
        PermissionResolver resolver = new PermissionResolver(permissionService, groupService);

        permissionService.setPermission(FIRST_SUBJECT, "*", PermissionValue.TRUE);
        permissionService.setPermission(FIRST_SUBJECT, "example.*", PermissionValue.FALSE);
        permissionService.setPermission(FIRST_SUBJECT, "example.node", PermissionValue.TRUE);
        permissionService.setPermission(FIRST_SUBJECT, "tier.*", PermissionValue.TRUE);
        groupService.createGroup("staff");
        groupService.setGroupPermission("staff", "tier.node", PermissionValue.FALSE);
        groupService.addSubjectGroup(FIRST_SUBJECT, "staff");

        assertEquals(PermissionValue.TRUE, resolver.resolve(FIRST_SUBJECT, "unlisted.node").value());
        assertEquals("*", resolver.resolve(FIRST_SUBJECT, "unlisted.node").assignmentNode());
        assertEquals(PermissionValue.FALSE, resolver.resolve(FIRST_SUBJECT, "example.deep.node").value());
        assertEquals("example.*", resolver.resolve(FIRST_SUBJECT, "example.deep.node").assignmentNode());
        assertEquals(PermissionValue.TRUE, resolver.resolve(FIRST_SUBJECT, "example.node").value());
        assertEquals("example.node", resolver.resolve(FIRST_SUBJECT, "example.node").assignmentNode());
        assertEquals(PermissionValue.TRUE, resolver.resolve(FIRST_SUBJECT, "example").value());
        assertEquals("*", resolver.resolve(FIRST_SUBJECT, "example").assignmentNode());
        assertEquals(PermissionValue.TRUE, resolver.resolve(FIRST_SUBJECT, "tier.node").value());
        assertEquals(PermissionResolution.Source.DIRECT, resolver.resolve(FIRST_SUBJECT, "tier.node").source());
    }

    @Test
    void observingServiceReportsSuccessfulMutations() {
        GroupService delegate = new InMemoryGroupService();
        List<UUID> subjectRefreshes = new ArrayList<>();
        List<String> fullRefreshes = new ArrayList<>();
        GroupService groupService = GroupServices.observing(delegate, new GroupChangeListener() {

            @Override
            public void subjectGroupsChanged(UUID subjectId) {
                subjectRefreshes.add(subjectId);
            }

            @Override
            public void groupsChanged() {
                fullRefreshes.add("all");
            }
        });

        groupService.createGroup("admin");
        groupService.setGroupPermission("admin", "example.node", PermissionValue.TRUE);
        assertEquals(1, groupService.clearGroupPermissions("admin"));
        assertEquals(0, groupService.clearGroupPermissions("admin"));
        groupService.setGroupPrefix("admin", DisplayText.parse("&7[Admin]"));
        groupService.clearGroupPrefix("admin");
        groupService.createGroup("base");
        groupService.addGroupParent("admin", "base");
        groupService.removeGroupParent("admin", "base");
        groupService.renameGroup("admin", "owner");
        groupService.addSubjectGroup(FIRST_SUBJECT, "owner");
        groupService.removeSubjectGroup(FIRST_SUBJECT, "owner");
        assertThrows(IllegalArgumentException.class, () -> groupService.addSubjectGroup(FIRST_SUBJECT, "missing"));
        assertThrows(IllegalArgumentException.class, () -> groupService.addGroupParent("owner", "missing"));
        assertThrows(IllegalArgumentException.class, () -> groupService.renameGroup("missing", "other"));
        assertThrows(IllegalArgumentException.class, () -> groupService.clearGroupPermissions("missing"));

        assertEquals(List.of("all", "all", "all", "all", "all", "all", "all", "all", "all"), fullRefreshes);
        assertEquals(List.of(FIRST_SUBJECT, FIRST_SUBJECT), subjectRefreshes);
    }

    private void assertInvalidGroupDatabase(String invalidSql) {
        Path databaseFile = temporaryDirectory.resolve(UUID.randomUUID().toString()).resolve("database.db");
        try (SqliteStore store = SqliteTestSupport.open(databaseFile)) {
            store.write(connection -> {
                try (Statement statement = connection.createStatement()) {
                    statement.executeUpdate("INSERT OR IGNORE INTO groups (name, prefix, suffix) VALUES ('staff', NULL, NULL)");
                    statement.executeUpdate(invalidSql);
                }
            });
        }

        try (SqliteStore store = SqliteTestSupport.open(databaseFile)) {
            assertThrows(PermissionStorageException.class, () -> GroupServices.sqlite(store));
        }
    }

    private void assertInvalidProtectedOpDatabase(String invalidSql) {
        Path databaseFile = temporaryDirectory.resolve(UUID.randomUUID().toString()).resolve("database.db");
        try (SqliteStore store = SqliteTestSupport.open(databaseFile)) {
            store.write(connection -> {
                try (Statement statement = connection.createStatement()) {
                    statement.executeUpdate("INSERT OR IGNORE INTO groups (name, prefix, suffix) VALUES ('staff', NULL, NULL)");
                    statement.executeUpdate(invalidSql);
                }
            });
        }

        assertThrows(PermissionStorageException.class, () -> {
            try (SqliteStore ignored = SqliteTestSupport.open(databaseFile)) {
                // Opening live storage validates protected built-ins.
            }
        });
        try (SqliteStore store = SqliteStore.openExisting(databaseFile, SqliteDependencyMode.ANY_VISIBLE)) {
            assertThrows(PermissionStorageException.class, () -> GroupServices.sqlite(store));
        }
    }

    private static void assertGroupStatePreserved(GroupService groupService, Path databaseFile) {
        assertGroupRuntimeState(groupService);
        try (SqliteStore store = SqliteTestSupport.open(databaseFile)) {
            assertGroupRuntimeState(GroupServices.sqlite(store));
        }
    }

    private static void assertGroupRuntimeState(GroupService groupService) {
        assertEquals(Set.of("base", "default", "guest", "op", "staff"), groupService.getGroups());
        assertFalse(groupService.hasGroup("new"));
        assertFalse(groupService.hasGroup("renamed"));
        assertEquals(PermissionValue.TRUE, groupService.getGroupPermission("staff", "old.node"));
        assertEquals(PermissionValue.UNSET, groupService.getGroupPermission("staff", "new.node"));
        assertEquals(Map.of("old.node", PermissionValue.TRUE), groupService.getGroupPermissions("staff"));
        assertEquals("&7[Old]", groupService.getGroupDisplay("staff").prefix().orElseThrow().rawText());
        assertFalse(groupService.getGroupDisplay("staff").suffix().isPresent());
        assertEquals(Set.of("base"), groupService.getGroupParents("staff"));
        assertEquals(Set.of(), groupService.getGroupParents("guest"));
        assertEquals(Set.of("staff"), groupService.getSubjectGroups(FIRST_SUBJECT));
        assertEquals(Set.of(), groupService.getSubjectGroups(SECOND_SUBJECT));
        assertEquals(Set.of(FIRST_SUBJECT), groupService.getGroupMembers("staff"));
    }
}
