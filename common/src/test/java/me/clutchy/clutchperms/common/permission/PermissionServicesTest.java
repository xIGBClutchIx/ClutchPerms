package me.clutchy.clutchperms.common.permission;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import me.clutchy.clutchperms.common.storage.PermissionStorageException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Verifies JSON-backed permission service loading and persistence.
 */
class PermissionServicesTest {

    private static final UUID FIRST_SUBJECT = UUID.fromString("00000000-0000-0000-0000-000000000001");

    private static final UUID SECOND_SUBJECT = UUID.fromString("00000000-0000-0000-0000-000000000002");

    /**
     * Temporary directory used for JSON persistence test files.
     */
    @TempDir
    private Path temporaryDirectory;

    /**
     * Confirms a missing JSON file starts with empty state.
     */
    @Test
    void missingFileLoadsEmptyPermissions() {
        Path permissionsFile = temporaryDirectory.resolve("permissions.json");

        PermissionService permissionService = PermissionServices.jsonFile(permissionsFile);

        assertEquals(PermissionValue.UNSET, permissionService.getPermission(FIRST_SUBJECT, PermissionNodes.ADMIN));
        assertEquals(Map.of(), permissionService.getPermissions(FIRST_SUBJECT));
        assertEquals(0, permissionService.clearPermissions(FIRST_SUBJECT));
        assertFalse(Files.exists(permissionsFile));
    }

    /**
     * Confirms explicit grant and denial values survive a reload.
     */
    @Test
    void trueAndFalseValuesRoundTripThroughJson() {
        Path permissionsFile = temporaryDirectory.resolve("permissions.json");
        PermissionService permissionService = PermissionServices.jsonFile(permissionsFile);

        permissionService.setPermission(FIRST_SUBJECT, " Example.Node ", PermissionValue.TRUE);
        permissionService.setPermission(FIRST_SUBJECT, "Example.Denied", PermissionValue.FALSE);
        permissionService.setPermission(FIRST_SUBJECT, "Example.*", PermissionValue.TRUE);
        permissionService.setPermission(SECOND_SUBJECT, "*", PermissionValue.FALSE);

        PermissionService reloadedPermissionService = PermissionServices.jsonFile(permissionsFile);

        assertEquals(PermissionValue.TRUE, reloadedPermissionService.getPermission(FIRST_SUBJECT, "example.node"));
        assertEquals(PermissionValue.FALSE, reloadedPermissionService.getPermission(FIRST_SUBJECT, "example.denied"));
        assertEquals(PermissionValue.TRUE, reloadedPermissionService.getPermission(FIRST_SUBJECT, "example.*"));
        assertEquals(PermissionValue.FALSE, reloadedPermissionService.getPermission(SECOND_SUBJECT, "*"));
        assertEquals(Map.of("example.node", PermissionValue.TRUE, "example.denied", PermissionValue.FALSE, "example.*", PermissionValue.TRUE),
                reloadedPermissionService.getPermissions(FIRST_SUBJECT));
    }

    /**
     * Confirms unset and clear operations remove explicit values from persisted state.
     */
    @Test
    void unsetAndClearRemovePersistedEntries() {
        Path permissionsFile = temporaryDirectory.resolve("permissions.json");
        PermissionService permissionService = PermissionServices.jsonFile(permissionsFile);

        permissionService.setPermission(FIRST_SUBJECT, "first.node", PermissionValue.TRUE);
        permissionService.setPermission(FIRST_SUBJECT, "first.node", PermissionValue.UNSET);
        permissionService.setPermission(FIRST_SUBJECT, "second.node", PermissionValue.FALSE);
        permissionService.clearPermission(FIRST_SUBJECT, "second.node");

        PermissionService reloadedPermissionService = PermissionServices.jsonFile(permissionsFile);

        assertEquals(PermissionValue.UNSET, reloadedPermissionService.getPermission(FIRST_SUBJECT, "first.node"));
        assertEquals(PermissionValue.UNSET, reloadedPermissionService.getPermission(FIRST_SUBJECT, "second.node"));
        assertEquals(Map.of(), reloadedPermissionService.getPermissions(FIRST_SUBJECT));
    }

    /**
     * Confirms bulk clears remove every direct assignment for one subject and persist the result.
     */
    @Test
    void clearPermissionsRemovesOnlySelectedSubjectAndPersists() {
        Path permissionsFile = temporaryDirectory.resolve("permissions.json");
        PermissionService permissionService = PermissionServices.jsonFile(permissionsFile);

        permissionService.setPermission(FIRST_SUBJECT, "first.node", PermissionValue.TRUE);
        permissionService.setPermission(FIRST_SUBJECT, "first.*", PermissionValue.FALSE);
        permissionService.setPermission(SECOND_SUBJECT, "second.node", PermissionValue.TRUE);

        assertEquals(2, permissionService.clearPermissions(FIRST_SUBJECT));
        assertEquals(0, permissionService.clearPermissions(FIRST_SUBJECT));

        PermissionService reloadedPermissionService = PermissionServices.jsonFile(permissionsFile);
        assertEquals(Map.of(), reloadedPermissionService.getPermissions(FIRST_SUBJECT));
        assertEquals(Map.of("second.node", PermissionValue.TRUE), reloadedPermissionService.getPermissions(SECOND_SUBJECT));
    }

    /**
     * Confirms failed JSON saves leave both runtime state and persisted state unchanged.
     *
     * @throws IOException if test storage setup cannot be written or read
     */
    @Test
    void failedSavesDoNotCommitPermissionMutations() throws IOException {
        Path permissionsFile = temporaryDirectory.resolve("permissions.json");
        Files.writeString(permissionsFile, """
                {
                  "version": 1,
                  "subjects": {
                    "00000000-0000-0000-0000-000000000001": {
                      "clear.node": "FALSE",
                      "old.node": "TRUE"
                    }
                  }
                }
                """);
        PermissionService permissionService = PermissionServices.jsonFile(permissionsFile);
        String persistedJson = Files.readString(permissionsFile);
        blockBackupRoot();

        assertThrows(PermissionStorageException.class, () -> permissionService.setPermission(FIRST_SUBJECT, "new.node", PermissionValue.TRUE));
        assertPermissionStatePreserved(permissionService, permissionsFile, persistedJson);

        assertThrows(PermissionStorageException.class, () -> permissionService.clearPermission(FIRST_SUBJECT, "clear.node"));
        assertPermissionStatePreserved(permissionService, permissionsFile, persistedJson);

        assertThrows(PermissionStorageException.class, () -> permissionService.clearPermissions(FIRST_SUBJECT));
        assertPermissionStatePreserved(permissionService, permissionsFile, persistedJson);
    }

    /**
     * Confirms malformed or invalid permission files fail during construction.
     *
     * @throws IOException if the test file cannot be written
     */
    @Test
    void invalidJsonFilesFailLoad() throws IOException {
        assertFailsToLoad("{not-json");
        assertFailsToLoad("""
                {
                  "version": 2,
                  "subjects": {}
                }
                """);
        assertFailsToLoad("""
                {
                  "version": 1,
                  "subjects": {
                    "not-a-uuid": {}
                  }
                }
                """);
        assertFailsToLoad("""
                {
                  "version": 1,
                  "subjects": {
                    "00000000-0000-0000-0000-000000000001": {
                      "   ": "TRUE"
                    }
                  }
                }
                """);
        assertFailsToLoad("""
                {
                  "version": 1,
                  "subjects": {
                    "00000000-0000-0000-0000-000000000001": {
                      "Example.Node": "TRUE",
                      " example.node ": "FALSE"
                    }
                  }
                }
                """);
        assertFailsToLoad("""
                {
                  "version": 1,
                  "subjects": {
                    "00000000-0000-0000-0000-000000000001": {
                      "example.node": "UNSET"
                    }
                  }
                }
                """);
        assertFailsToLoad("""
                {
                  "version": 1,
                  "subjects": {
                    "00000000-0000-0000-0000-000000000001": {
                      "example*": "TRUE"
                    }
                  }
                }
                """);
        assertFailsToLoad("""
                {
                  "version": 1,
                  "subjects": {
                    "00000000-0000-0000-0000-000000000001": {
                      "example.*.node": "TRUE"
                    }
                  }
                }
                """);
        assertFailsToLoad("""
                {
                  "version": 1,
                  "subjects": {
                    "00000000-0000-0000-0000-000000000001": {
                      " .* ": "TRUE"
                    }
                  }
                }
                """);
    }

    /**
     * Confirms saves create parent directories and use deterministic subject and node ordering.
     *
     * @throws IOException if the persisted file cannot be read
     */
    @Test
    void saveCreatesParentDirectoriesAndWritesDeterministicJson() throws IOException {
        Path permissionsFile = temporaryDirectory.resolve("nested").resolve("data").resolve("permissions.json");
        PermissionService permissionService = PermissionServices.jsonFile(permissionsFile);

        permissionService.setPermission(SECOND_SUBJECT, "z.node", PermissionValue.FALSE);
        permissionService.setPermission(FIRST_SUBJECT, "B.Node", PermissionValue.TRUE);
        permissionService.setPermission(FIRST_SUBJECT, "a.node", PermissionValue.FALSE);

        String persistedJson = Files.readString(permissionsFile).replace("\r\n", "\n");

        assertEquals("""
                {
                  "version": 1,
                  "subjects": {
                    "00000000-0000-0000-0000-000000000001": {
                      "a.node": "FALSE",
                      "b.node": "TRUE"
                    },
                    "00000000-0000-0000-0000-000000000002": {
                      "z.node": "FALSE"
                    }
                  }
                }
                """, persistedJson);
    }

    /**
     * Confirms observing services preserve delegate reads and report successful mutations.
     */
    @Test
    void observingServiceDelegatesReadsAndReportsSuccessfulMutations() {
        PermissionService delegate = new InMemoryPermissionService();
        List<UUID> changedSubjects = new ArrayList<>();
        PermissionService permissionService = PermissionServices.observing(delegate, changedSubjects::add);

        permissionService.setPermission(FIRST_SUBJECT, "Example.Node", PermissionValue.TRUE);
        permissionService.setPermission(FIRST_SUBJECT, "Example.Node", PermissionValue.UNSET);
        permissionService.clearPermission(SECOND_SUBJECT, "Other.Node");
        permissionService.setPermission(SECOND_SUBJECT, "Other.Node", PermissionValue.TRUE);
        assertEquals(1, permissionService.clearPermissions(SECOND_SUBJECT));
        assertEquals(0, permissionService.clearPermissions(SECOND_SUBJECT));

        assertEquals(PermissionValue.UNSET, permissionService.getPermission(FIRST_SUBJECT, "example.node"));
        assertEquals(Map.of(), permissionService.getPermissions(FIRST_SUBJECT));
        assertEquals(List.of(FIRST_SUBJECT, FIRST_SUBJECT, SECOND_SUBJECT, SECOND_SUBJECT, SECOND_SUBJECT), changedSubjects);
    }

    /**
     * Confirms observing services do not report failed delegate mutations.
     */
    @Test
    void observingServiceDoesNotReportFailedMutations() {
        List<UUID> changedSubjects = new ArrayList<>();
        PermissionService permissionService = PermissionServices.observing(new InMemoryPermissionService(), changedSubjects::add);

        assertThrows(IllegalArgumentException.class, () -> permissionService.setPermission(FIRST_SUBJECT, "   ", PermissionValue.TRUE));
        assertThrows(NullPointerException.class, () -> permissionService.clearPermission(null, "example.node"));
        assertThrows(NullPointerException.class, () -> permissionService.clearPermissions(null));

        assertEquals(List.of(), changedSubjects);
    }

    private void assertFailsToLoad(String json) throws IOException {
        Path permissionsFile = temporaryDirectory.resolve("invalid-permissions.json");
        Files.writeString(permissionsFile, json);

        assertThrows(PermissionStorageException.class, () -> PermissionServices.jsonFile(permissionsFile));
    }

    private void assertPermissionStatePreserved(PermissionService permissionService, Path permissionsFile, String persistedJson) throws IOException {
        assertPermissionRuntimeState(permissionService);
        assertEquals(persistedJson, Files.readString(permissionsFile));
        assertPermissionRuntimeState(PermissionServices.jsonFile(permissionsFile));
    }

    private static void assertPermissionRuntimeState(PermissionService permissionService) {
        assertEquals(PermissionValue.TRUE, permissionService.getPermission(FIRST_SUBJECT, "old.node"));
        assertEquals(PermissionValue.FALSE, permissionService.getPermission(FIRST_SUBJECT, "clear.node"));
        assertEquals(PermissionValue.UNSET, permissionService.getPermission(FIRST_SUBJECT, "new.node"));
        assertEquals(Map.of("clear.node", PermissionValue.FALSE, "old.node", PermissionValue.TRUE), permissionService.getPermissions(FIRST_SUBJECT));
    }

    private void blockBackupRoot() throws IOException {
        Files.writeString(temporaryDirectory.resolve("backups"), "blocked");
    }
}
