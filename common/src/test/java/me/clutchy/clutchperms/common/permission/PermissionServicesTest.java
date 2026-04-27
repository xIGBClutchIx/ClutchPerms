package me.clutchy.clutchperms.common.permission;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import me.clutchy.clutchperms.common.storage.PermissionStorageException;
import me.clutchy.clutchperms.common.storage.SqliteStore;
import me.clutchy.clutchperms.common.storage.SqliteTestSupport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies SQLite-backed permission service loading and persistence.
 */
class PermissionServicesTest {

    private static final UUID FIRST_SUBJECT = UUID.fromString("00000000-0000-0000-0000-000000000001");

    private static final UUID SECOND_SUBJECT = UUID.fromString("00000000-0000-0000-0000-000000000002");

    @TempDir
    private Path temporaryDirectory;

    @Test
    void missingDatabaseCreatesEmptyPermissionsSchema() {
        Path databaseFile = SqliteTestSupport.databaseFile(temporaryDirectory);

        try (SqliteStore store = SqliteTestSupport.open(databaseFile)) {
            PermissionService permissionService = PermissionServices.sqlite(store);

            assertEquals(PermissionValue.UNSET, permissionService.getPermission(FIRST_SUBJECT, PermissionNodes.ADMIN));
            assertEquals(Map.of(), permissionService.getPermissions(FIRST_SUBJECT));
            assertEquals(0, permissionService.clearPermissions(FIRST_SUBJECT));
        }

        assertTrue(Files.exists(databaseFile));
    }

    @Test
    void trueAndFalseValuesRoundTripThroughSqlite() {
        Path databaseFile = SqliteTestSupport.databaseFile(temporaryDirectory);
        try (SqliteStore store = SqliteTestSupport.open(databaseFile)) {
            PermissionService permissionService = PermissionServices.sqlite(store);
            permissionService.setPermission(FIRST_SUBJECT, " Example.Node ", PermissionValue.TRUE);
            permissionService.setPermission(FIRST_SUBJECT, "Example.Denied", PermissionValue.FALSE);
            permissionService.setPermission(FIRST_SUBJECT, "Example.*", PermissionValue.TRUE);
            permissionService.setPermission(SECOND_SUBJECT, "*", PermissionValue.FALSE);
        }

        try (SqliteStore store = SqliteTestSupport.open(databaseFile)) {
            PermissionService reloadedPermissionService = PermissionServices.sqlite(store);

            assertEquals(PermissionValue.TRUE, reloadedPermissionService.getPermission(FIRST_SUBJECT, "example.node"));
            assertEquals(PermissionValue.FALSE, reloadedPermissionService.getPermission(FIRST_SUBJECT, "example.denied"));
            assertEquals(PermissionValue.TRUE, reloadedPermissionService.getPermission(FIRST_SUBJECT, "example.*"));
            assertEquals(PermissionValue.FALSE, reloadedPermissionService.getPermission(SECOND_SUBJECT, "*"));
            assertEquals(Map.of("example.node", PermissionValue.TRUE, "example.denied", PermissionValue.FALSE, "example.*", PermissionValue.TRUE),
                    reloadedPermissionService.getPermissions(FIRST_SUBJECT));
        }
    }

    @Test
    void unsetAndClearRemovePersistedEntries() {
        Path databaseFile = SqliteTestSupport.databaseFile(temporaryDirectory);
        try (SqliteStore store = SqliteTestSupport.open(databaseFile)) {
            PermissionService permissionService = PermissionServices.sqlite(store);
            permissionService.setPermission(FIRST_SUBJECT, "first.node", PermissionValue.TRUE);
            permissionService.setPermission(FIRST_SUBJECT, "first.node", PermissionValue.UNSET);
            permissionService.setPermission(FIRST_SUBJECT, "second.node", PermissionValue.FALSE);
            permissionService.clearPermission(FIRST_SUBJECT, "second.node");
        }

        try (SqliteStore store = SqliteTestSupport.open(databaseFile)) {
            PermissionService reloadedPermissionService = PermissionServices.sqlite(store);
            assertEquals(Map.of(), reloadedPermissionService.getPermissions(FIRST_SUBJECT));
        }
    }

    @Test
    void clearPermissionsRemovesOnlySelectedSubjectAndPersists() {
        Path databaseFile = SqliteTestSupport.databaseFile(temporaryDirectory);
        try (SqliteStore store = SqliteTestSupport.open(databaseFile)) {
            PermissionService permissionService = PermissionServices.sqlite(store);
            permissionService.setPermission(FIRST_SUBJECT, "first.node", PermissionValue.TRUE);
            permissionService.setPermission(FIRST_SUBJECT, "first.*", PermissionValue.FALSE);
            permissionService.setPermission(SECOND_SUBJECT, "second.node", PermissionValue.TRUE);

            assertEquals(2, permissionService.clearPermissions(FIRST_SUBJECT));
            assertEquals(0, permissionService.clearPermissions(FIRST_SUBJECT));
        }

        try (SqliteStore store = SqliteTestSupport.open(databaseFile)) {
            PermissionService reloadedPermissionService = PermissionServices.sqlite(store);
            assertEquals(Map.of(), reloadedPermissionService.getPermissions(FIRST_SUBJECT));
            assertEquals(Map.of("second.node", PermissionValue.TRUE), reloadedPermissionService.getPermissions(SECOND_SUBJECT));
        }
    }

    @Test
    void failedWritesDoNotCommitPermissionMutations() {
        Path databaseFile = SqliteTestSupport.databaseFile(temporaryDirectory);
        SqliteStore store = SqliteTestSupport.open(databaseFile);
        PermissionService permissionService = PermissionServices.sqlite(store);
        permissionService.setPermission(FIRST_SUBJECT, "clear.node", PermissionValue.FALSE);
        permissionService.setPermission(FIRST_SUBJECT, "old.node", PermissionValue.TRUE);
        store.close();

        assertThrows(PermissionStorageException.class, () -> permissionService.setPermission(FIRST_SUBJECT, "new.node", PermissionValue.TRUE));
        assertPermissionStatePreserved(permissionService, databaseFile);

        assertThrows(PermissionStorageException.class, () -> permissionService.clearPermission(FIRST_SUBJECT, "clear.node"));
        assertPermissionStatePreserved(permissionService, databaseFile);

        assertThrows(PermissionStorageException.class, () -> permissionService.clearPermissions(FIRST_SUBJECT));
        assertPermissionStatePreserved(permissionService, databaseFile);
    }

    @Test
    void invalidSqlitePermissionRowsFailLoad() {
        Path databaseFile = SqliteTestSupport.databaseFile(temporaryDirectory);
        try (SqliteStore store = SqliteTestSupport.open(databaseFile)) {
            store.write(connection -> connection.createStatement()
                    .executeUpdate("INSERT INTO subject_permissions (subject_id, node, value) VALUES ('00000000-0000-0000-0000-000000000001', 'example*', 'TRUE')"));
        }

        try (SqliteStore store = SqliteTestSupport.open(databaseFile)) {
            assertThrows(PermissionStorageException.class, () -> PermissionServices.sqlite(store));
        }
    }

    @Test
    void duplicateNormalizedSqlitePermissionRowsFailLoad() {
        Path databaseFile = SqliteTestSupport.databaseFile(temporaryDirectory);
        try (SqliteStore store = SqliteTestSupport.open(databaseFile)) {
            store.write(connection -> {
                connection.createStatement()
                        .executeUpdate("INSERT INTO subject_permissions (subject_id, node, value) VALUES ('00000000-0000-0000-0000-000000000001', 'Example.Node', 'TRUE')");
                connection.createStatement()
                        .executeUpdate("INSERT INTO subject_permissions (subject_id, node, value) VALUES ('00000000-0000-0000-0000-000000000001', ' example.node ', 'FALSE')");
            });
        }

        try (SqliteStore store = SqliteTestSupport.open(databaseFile)) {
            assertThrows(PermissionStorageException.class, () -> PermissionServices.sqlite(store));
        }
    }

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

    @Test
    void observingServiceDoesNotReportFailedMutations() {
        List<UUID> changedSubjects = new ArrayList<>();
        PermissionService permissionService = PermissionServices.observing(new InMemoryPermissionService(), changedSubjects::add);

        assertThrows(IllegalArgumentException.class, () -> permissionService.setPermission(FIRST_SUBJECT, "   ", PermissionValue.TRUE));
        assertThrows(NullPointerException.class, () -> permissionService.clearPermission(null, "example.node"));
        assertThrows(NullPointerException.class, () -> permissionService.clearPermissions(null));

        assertEquals(List.of(), changedSubjects);
    }

    private static void assertPermissionStatePreserved(PermissionService permissionService, Path databaseFile) {
        assertPermissionRuntimeState(permissionService);
        try (SqliteStore store = SqliteTestSupport.open(databaseFile)) {
            assertPermissionRuntimeState(PermissionServices.sqlite(store));
        }
    }

    private static void assertPermissionRuntimeState(PermissionService permissionService) {
        assertEquals(PermissionValue.TRUE, permissionService.getPermission(FIRST_SUBJECT, "old.node"));
        assertEquals(PermissionValue.FALSE, permissionService.getPermission(FIRST_SUBJECT, "clear.node"));
        assertEquals(PermissionValue.UNSET, permissionService.getPermission(FIRST_SUBJECT, "new.node"));
        assertEquals(Map.of("clear.node", PermissionValue.FALSE, "old.node", PermissionValue.TRUE), permissionService.getPermissions(FIRST_SUBJECT));
    }
}
