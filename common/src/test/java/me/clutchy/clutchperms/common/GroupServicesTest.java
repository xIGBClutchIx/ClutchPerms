package me.clutchy.clutchperms.common;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Verifies JSON-backed group service loading, persistence, observing, and effective resolution.
 */
class GroupServicesTest {

    private static final UUID FIRST_SUBJECT = UUID.fromString("00000000-0000-0000-0000-000000000001");

    private static final UUID SECOND_SUBJECT = UUID.fromString("00000000-0000-0000-0000-000000000002");

    @TempDir
    private Path temporaryDirectory;

    /**
     * Confirms a missing groups file starts with empty state.
     */
    @Test
    void missingFileLoadsEmptyGroups() {
        Path groupsFile = temporaryDirectory.resolve("groups.json");

        GroupService groupService = GroupServices.jsonFile(groupsFile);

        assertEquals(Set.of(), groupService.getGroups());
        assertFalse(Files.exists(groupsFile));
    }

    /**
     * Confirms group permissions and memberships survive a reload.
     */
    @Test
    void groupsRoundTripThroughJson() {
        Path groupsFile = temporaryDirectory.resolve("groups.json");
        GroupService groupService = GroupServices.jsonFile(groupsFile);

        groupService.createGroup("Admin");
        groupService.setGroupPermission("admin", "Example.Node", PermissionValue.TRUE);
        groupService.setGroupPermission("admin", "Example.Denied", PermissionValue.FALSE);
        groupService.addSubjectGroup(FIRST_SUBJECT, "admin");

        GroupService reloadedGroupService = GroupServices.jsonFile(groupsFile);

        assertEquals(Set.of("admin"), reloadedGroupService.getGroups());
        assertEquals(PermissionValue.TRUE, reloadedGroupService.getGroupPermission("admin", "example.node"));
        assertEquals(PermissionValue.FALSE, reloadedGroupService.getGroupPermission("admin", "example.denied"));
        assertEquals(Map.of("example.node", PermissionValue.TRUE, "example.denied", PermissionValue.FALSE), reloadedGroupService.getGroupPermissions("admin"));
        assertEquals(Set.of("admin"), reloadedGroupService.getSubjectGroups(FIRST_SUBJECT));
        assertEquals(Set.of(FIRST_SUBJECT), reloadedGroupService.getGroupMembers("admin"));
    }

    /**
     * Confirms group saves create parent directories and write deterministic JSON.
     *
     * @throws IOException if the persisted file cannot be read
     */
    @Test
    void saveCreatesParentDirectoriesAndWritesDeterministicJson() throws IOException {
        Path groupsFile = temporaryDirectory.resolve("nested").resolve("data").resolve("groups.json");
        GroupService groupService = GroupServices.jsonFile(groupsFile);

        groupService.createGroup("zeta");
        groupService.setGroupPermission("zeta", "z.node", PermissionValue.FALSE);
        groupService.createGroup("alpha");
        groupService.setGroupPermission("alpha", "B.Node", PermissionValue.TRUE);
        groupService.setGroupPermission("alpha", "a.node", PermissionValue.FALSE);
        groupService.addSubjectGroup(SECOND_SUBJECT, "zeta");
        groupService.addSubjectGroup(FIRST_SUBJECT, "alpha");

        String persistedJson = Files.readString(groupsFile).replace("\r\n", "\n");

        assertEquals("""
                {
                  "version": 1,
                  "groups": {
                    "alpha": {
                      "permissions": {
                        "a.node": "FALSE",
                        "b.node": "TRUE"
                      }
                    },
                    "zeta": {
                      "permissions": {
                        "z.node": "FALSE"
                      }
                    }
                  },
                  "memberships": {
                    "00000000-0000-0000-0000-000000000001": [
                      "alpha"
                    ],
                    "00000000-0000-0000-0000-000000000002": [
                      "zeta"
                    ]
                  }
                }
                """, persistedJson);
    }

    /**
     * Confirms malformed or invalid group files fail during construction.
     *
     * @throws IOException if the test file cannot be written
     */
    @Test
    void invalidJsonFilesFailLoad() throws IOException {
        assertFailsToLoad("{not-json");
        assertFailsToLoad("""
                {
                  "version": 2,
                  "groups": {},
                  "memberships": {}
                }
                """);
        assertFailsToLoad("""
                {
                  "version": 1,
                  "groups": {
                    "   ": {
                      "permissions": {}
                    }
                  },
                  "memberships": {}
                }
                """);
        assertFailsToLoad("""
                {
                  "version": 1,
                  "groups": {
                    "admin": {
                      "permissions": {
                        "example.node": "UNSET"
                      }
                    }
                  },
                  "memberships": {}
                }
                """);
        assertFailsToLoad("""
                {
                  "version": 1,
                  "groups": {
                    "admin": {
                      "permissions": {}
                    }
                  },
                  "memberships": {
                    "not-a-uuid": [
                      "admin"
                    ]
                  }
                }
                """);
        assertFailsToLoad("""
                {
                  "version": 1,
                  "groups": {
                    "admin": {
                      "permissions": {}
                    }
                  },
                  "memberships": {
                    "00000000-0000-0000-0000-000000000001": [
                      "missing"
                    ]
                  }
                }
                """);
    }

    /**
     * Confirms effective resolution uses direct, group, and default precedence.
     */
    @Test
    void resolverAppliesDirectGroupAndDefaultPrecedence() {
        PermissionService permissionService = new InMemoryPermissionService();
        GroupService groupService = new InMemoryGroupService();
        PermissionResolver resolver = new PermissionResolver(permissionService, groupService);

        groupService.createGroup("default");
        groupService.setGroupPermission("default", "example.node", PermissionValue.TRUE);
        groupService.createGroup("staff");
        groupService.setGroupPermission("staff", "example.node", PermissionValue.FALSE);
        groupService.setGroupPermission("staff", "staff.node", PermissionValue.TRUE);
        groupService.createGroup("builder");
        groupService.setGroupPermission("builder", "staff.node", PermissionValue.FALSE);
        groupService.addSubjectGroup(FIRST_SUBJECT, "staff");
        groupService.addSubjectGroup(FIRST_SUBJECT, "builder");

        assertEquals(PermissionValue.FALSE, resolver.resolve(FIRST_SUBJECT, "example.node").value());
        assertEquals(PermissionResolution.Source.GROUP, resolver.resolve(FIRST_SUBJECT, "example.node").source());
        assertEquals(PermissionValue.FALSE, resolver.resolve(FIRST_SUBJECT, "staff.node").value());
        assertEquals(PermissionValue.TRUE, resolver.resolve(SECOND_SUBJECT, "example.node").value());

        permissionService.setPermission(FIRST_SUBJECT, "example.node", PermissionValue.TRUE);

        assertEquals(PermissionValue.TRUE, resolver.resolve(FIRST_SUBJECT, "example.node").value());
        assertEquals(PermissionResolution.Source.DIRECT, resolver.resolve(FIRST_SUBJECT, "example.node").source());
        assertEquals(Map.of("example.node", PermissionValue.TRUE, "staff.node", PermissionValue.FALSE), resolver.getEffectivePermissions(FIRST_SUBJECT));
    }

    /**
     * Confirms observing group services notify after successful mutations only.
     */
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
        groupService.addSubjectGroup(FIRST_SUBJECT, "admin");
        groupService.removeSubjectGroup(FIRST_SUBJECT, "admin");
        assertThrows(IllegalArgumentException.class, () -> groupService.addSubjectGroup(FIRST_SUBJECT, "missing"));

        assertEquals(List.of("all", "all"), fullRefreshes);
        assertEquals(List.of(FIRST_SUBJECT, FIRST_SUBJECT), subjectRefreshes);
    }

    private void assertFailsToLoad(String json) throws IOException {
        Path groupsFile = temporaryDirectory.resolve("invalid-groups.json");
        Files.writeString(groupsFile, json);

        assertThrows(PermissionStorageException.class, () -> GroupServices.jsonFile(groupsFile));
    }
}
