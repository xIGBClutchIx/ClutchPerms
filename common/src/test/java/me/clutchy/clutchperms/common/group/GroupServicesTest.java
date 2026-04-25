package me.clutchy.clutchperms.common.group;

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

import me.clutchy.clutchperms.common.display.DisplayText;
import me.clutchy.clutchperms.common.permission.InMemoryPermissionService;
import me.clutchy.clutchperms.common.permission.PermissionResolution;
import me.clutchy.clutchperms.common.permission.PermissionResolver;
import me.clutchy.clutchperms.common.permission.PermissionService;
import me.clutchy.clutchperms.common.permission.PermissionValue;
import me.clutchy.clutchperms.common.storage.PermissionStorageException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies JSON-backed group service loading, persistence, observing, and effective resolution.
 */
class GroupServicesTest {

    private static final UUID FIRST_SUBJECT = UUID.fromString("00000000-0000-0000-0000-000000000001");

    private static final UUID SECOND_SUBJECT = UUID.fromString("00000000-0000-0000-0000-000000000002");

    @TempDir
    private Path temporaryDirectory;

    /**
     * Confirms a missing groups file starts with the built-in default group.
     */
    @Test
    void missingFileLoadsDefaultGroup() {
        Path groupsFile = temporaryDirectory.resolve("groups.json");

        GroupService groupService = GroupServices.jsonFile(groupsFile);

        assertEquals(Set.of("default"), groupService.getGroups());
        assertTrue(groupService.hasGroup("default"));
        assertFalse(Files.exists(groupsFile));
    }

    /**
     * Confirms group permissions and memberships survive a reload.
     */
    @Test
    void groupsRoundTripThroughJson() {
        Path groupsFile = temporaryDirectory.resolve("groups.json");
        GroupService groupService = GroupServices.jsonFile(groupsFile);

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

        GroupService reloadedGroupService = GroupServices.jsonFile(groupsFile);

        assertEquals(Set.of("admin", "default", "staff"), reloadedGroupService.getGroups());
        assertEquals(PermissionValue.TRUE, reloadedGroupService.getGroupPermission("admin", "example.node"));
        assertEquals(PermissionValue.FALSE, reloadedGroupService.getGroupPermission("admin", "example.denied"));
        assertEquals(PermissionValue.FALSE, reloadedGroupService.getGroupPermission("staff", "staff.*"));
        assertEquals(Map.of("example.node", PermissionValue.TRUE, "example.denied", PermissionValue.FALSE), reloadedGroupService.getGroupPermissions("admin"));
        assertEquals("&c[Admin]", reloadedGroupService.getGroupDisplay("admin").prefix().orElseThrow().rawText());
        assertEquals("&f*", reloadedGroupService.getGroupDisplay("admin").suffix().orElseThrow().rawText());
        assertEquals(Set.of("staff"), reloadedGroupService.getGroupParents("admin"));
        assertEquals(Set.of(), reloadedGroupService.getGroupParents("staff"));
        assertEquals(Set.of("admin"), reloadedGroupService.getSubjectGroups(FIRST_SUBJECT));
        assertEquals(Set.of(FIRST_SUBJECT), reloadedGroupService.getGroupMembers("admin"));
    }

    /**
     * Confirms legacy group files without parent arrays load with empty parent state.
     *
     * @throws IOException if the test file cannot be written
     */
    @Test
    void missingParentsFieldLoadsEmptyParents() throws IOException {
        Path groupsFile = temporaryDirectory.resolve("groups.json");
        Files.writeString(groupsFile, """
                {
                  "version": 1,
                  "groups": {
                    "admin": {
                      "permissions": {}
                    }
                  },
                  "memberships": {}
                }
                """);

        GroupService groupService = GroupServices.jsonFile(groupsFile);

        assertEquals(Set.of(), groupService.getGroupParents("admin"));
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
        groupService.addGroupParent("alpha", "zeta");
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
                      },
                      "parents": [
                        "zeta"
                      ]
                    },
                    "default": {
                      "permissions": {}
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
     * Confirms failed JSON saves leave group runtime state and persisted state unchanged.
     *
     * @throws IOException if test storage setup cannot be written or read
     */
    @Test
    void failedSavesDoNotCommitGroupMutations() throws IOException {
        Path groupsFile = temporaryDirectory.resolve("groups.json");
        Files.writeString(groupsFile, """
                {
                  "version": 1,
                  "groups": {
                    "base": {
                      "permissions": {}
                    },
                    "guest": {
                      "permissions": {}
                    },
                    "staff": {
                      "permissions": {
                        "old.node": "TRUE"
                      },
                      "prefix": "&7[Old]",
                      "parents": [
                        "base"
                      ]
                    }
                  },
                  "memberships": {
                    "00000000-0000-0000-0000-000000000001": [
                      "staff"
                    ]
                  }
                }
                """);
        GroupService groupService = GroupServices.jsonFile(groupsFile);
        String persistedJson = Files.readString(groupsFile);
        blockBackupRoot();

        assertThrows(PermissionStorageException.class, () -> groupService.createGroup("new"));
        assertGroupStatePreserved(groupService, groupsFile, persistedJson);

        assertThrows(PermissionStorageException.class, () -> groupService.deleteGroup("staff"));
        assertGroupStatePreserved(groupService, groupsFile, persistedJson);

        assertThrows(PermissionStorageException.class, () -> groupService.setGroupPermission("staff", "new.node", PermissionValue.FALSE));
        assertGroupStatePreserved(groupService, groupsFile, persistedJson);

        assertThrows(PermissionStorageException.class, () -> groupService.clearGroupPermission("staff", "old.node"));
        assertGroupStatePreserved(groupService, groupsFile, persistedJson);

        assertThrows(PermissionStorageException.class, () -> groupService.setGroupPrefix("staff", DisplayText.parse("&c[New]")));
        assertGroupStatePreserved(groupService, groupsFile, persistedJson);

        assertThrows(PermissionStorageException.class, () -> groupService.clearGroupPrefix("staff"));
        assertGroupStatePreserved(groupService, groupsFile, persistedJson);

        assertThrows(PermissionStorageException.class, () -> groupService.setGroupSuffix("staff", DisplayText.parse("&f*")));
        assertGroupStatePreserved(groupService, groupsFile, persistedJson);

        assertThrows(PermissionStorageException.class, () -> groupService.addSubjectGroup(SECOND_SUBJECT, "base"));
        assertGroupStatePreserved(groupService, groupsFile, persistedJson);

        assertThrows(PermissionStorageException.class, () -> groupService.removeSubjectGroup(FIRST_SUBJECT, "staff"));
        assertGroupStatePreserved(groupService, groupsFile, persistedJson);

        assertThrows(PermissionStorageException.class, () -> groupService.addGroupParent("guest", "base"));
        assertGroupStatePreserved(groupService, groupsFile, persistedJson);

        assertThrows(PermissionStorageException.class, () -> groupService.removeGroupParent("staff", "base"));
        assertGroupStatePreserved(groupService, groupsFile, persistedJson);
    }

    /**
     * Confirms deleting a group removes parent references to it.
     */
    @Test
    void deleteGroupRemovesParentReferences() {
        GroupService groupService = new InMemoryGroupService();
        groupService.createGroup("base");
        groupService.createGroup("staff");
        groupService.addGroupParent("staff", "base");

        groupService.deleteGroup("base");

        assertEquals(Set.of(), groupService.getGroupParents("staff"));
    }

    /**
     * Confirms the built-in default group is always present and cannot be deleted.
     */
    @Test
    void defaultGroupIsAlwaysPresentAndCannotBeDeleted() {
        GroupService groupService = new InMemoryGroupService();

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> groupService.deleteGroup("default"));

        assertEquals("default group cannot be deleted", exception.getMessage());
        assertEquals(Set.of("default"), groupService.getGroups());
        assertTrue(groupService.hasGroup("default"));
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
                        "Example.Node": "TRUE",
                        " example.node ": "FALSE"
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
        assertFailsToLoad("""
                {
                  "version": 1,
                  "groups": {
                    "admin": {
                      "permissions": {},
                      "parents": {}
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
                      "permissions": {},
                      "parents": [
                        "   "
                      ]
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
                      "permissions": {},
                      "parents": [
                        "staff",
                        " Staff "
                      ]
                    },
                    "staff": {
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
                      "permissions": {},
                      "parents": [
                        "missing"
                      ]
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
                      "permissions": {},
                      "parents": [
                        "admin"
                      ]
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
                      "permissions": {},
                      "parents": [
                        "staff"
                      ]
                    },
                    "staff": {
                      "permissions": {},
                      "parents": [
                        "admin"
                      ]
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
                      "permissions": {},
                      "parents": [
                        "staff"
                      ]
                    },
                    "staff": {
                      "permissions": {},
                      "parents": [
                        "base"
                      ]
                    },
                    "base": {
                      "permissions": {},
                      "parents": [
                        "admin"
                      ]
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
                        "example*": "TRUE"
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
                      "permissions": {
                        "example.*.node": "TRUE"
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
                      "permissions": {
                        " .* ": "TRUE"
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
                      "permissions": {},
                      "prefix": "§cBad"
                    }
                  },
                  "memberships": {}
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

    /**
     * Confirms effective resolution supports terminal wildcard assignments without changing source-tier precedence.
     */
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
        assertEquals(Map.of("*", PermissionValue.TRUE, "example.*", PermissionValue.FALSE, "example.node", PermissionValue.TRUE, "tier.*", PermissionValue.TRUE, "tier.node",
                PermissionValue.TRUE), resolver.getEffectivePermissions(FIRST_SUBJECT));

        groupService.createGroup("parent");
        groupService.setGroupPermission("parent", "child.node", PermissionValue.TRUE);
        groupService.createGroup("child");
        groupService.setGroupPermission("child", "child.*", PermissionValue.FALSE);
        groupService.addGroupParent("child", "parent");
        groupService.createGroup("allow");
        groupService.setGroupPermission("allow", "same.*", PermissionValue.TRUE);
        groupService.setGroupPermission("allow", "specific.node", PermissionValue.TRUE);
        groupService.createGroup("deny");
        groupService.setGroupPermission("deny", "same.*", PermissionValue.FALSE);
        groupService.setGroupPermission("deny", "specific.*", PermissionValue.FALSE);
        groupService.addSubjectGroup(SECOND_SUBJECT, "child");
        groupService.addSubjectGroup(SECOND_SUBJECT, "allow");
        groupService.addSubjectGroup(SECOND_SUBJECT, "deny");
        groupService.setGroupPermission("default", "default.node", PermissionValue.TRUE);
        groupService.createGroup("default-parent");
        groupService.setGroupPermission("default-parent", "default.parent.*", PermissionValue.TRUE);
        groupService.addGroupParent("default", "default-parent");
        UUID defaultOnlySubject = UUID.randomUUID();

        assertEquals(PermissionValue.FALSE, resolver.resolve(SECOND_SUBJECT, "child.node").value());
        assertEquals("child", resolver.resolve(SECOND_SUBJECT, "child.node").groupName());
        assertEquals("child.*", resolver.resolve(SECOND_SUBJECT, "child.node").assignmentNode());
        assertEquals(PermissionValue.FALSE, resolver.resolve(SECOND_SUBJECT, "same.node").value());
        assertEquals("same.*", resolver.resolve(SECOND_SUBJECT, "same.node").assignmentNode());
        assertEquals(PermissionValue.TRUE, resolver.resolve(SECOND_SUBJECT, "specific.node").value());
        assertEquals("specific.node", resolver.resolve(SECOND_SUBJECT, "specific.node").assignmentNode());
        assertEquals(PermissionValue.TRUE, resolver.resolve(defaultOnlySubject, "default.parent.node").value());
        assertEquals(PermissionResolution.Source.DEFAULT, resolver.resolve(defaultOnlySubject, "default.parent.node").source());
        assertEquals("default.parent.*", resolver.resolve(defaultOnlySubject, "default.parent.node").assignmentNode());
    }

    /**
     * Confirms parent mutation validation catches unknown groups, self-parenting, and cycles.
     */
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
        groupService.setGroupPrefix("admin", DisplayText.parse("&7[Admin]"));
        groupService.clearGroupPrefix("admin");
        groupService.createGroup("base");
        groupService.addGroupParent("admin", "base");
        groupService.removeGroupParent("admin", "base");
        groupService.addSubjectGroup(FIRST_SUBJECT, "admin");
        groupService.removeSubjectGroup(FIRST_SUBJECT, "admin");
        assertThrows(IllegalArgumentException.class, () -> groupService.addSubjectGroup(FIRST_SUBJECT, "missing"));
        assertThrows(IllegalArgumentException.class, () -> groupService.addGroupParent("admin", "missing"));

        assertEquals(List.of("all", "all", "all", "all", "all", "all", "all"), fullRefreshes);
        assertEquals(List.of(FIRST_SUBJECT, FIRST_SUBJECT), subjectRefreshes);
    }

    private void assertFailsToLoad(String json) throws IOException {
        Path groupsFile = temporaryDirectory.resolve("invalid-groups.json");
        Files.writeString(groupsFile, json);

        assertThrows(PermissionStorageException.class, () -> GroupServices.jsonFile(groupsFile));
    }

    private void assertGroupStatePreserved(GroupService groupService, Path groupsFile, String persistedJson) throws IOException {
        assertGroupRuntimeState(groupService);
        assertEquals(persistedJson, Files.readString(groupsFile));
        assertGroupRuntimeState(GroupServices.jsonFile(groupsFile));
    }

    private static void assertGroupRuntimeState(GroupService groupService) {
        assertEquals(Set.of("base", "default", "guest", "staff"), groupService.getGroups());
        assertFalse(groupService.hasGroup("new"));
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

    private void blockBackupRoot() throws IOException {
        Files.writeString(temporaryDirectory.resolve("backups"), "blocked");
    }
}
