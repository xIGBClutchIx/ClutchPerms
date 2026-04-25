package me.clutchy.clutchperms.common.permission;

import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies the baseline behavior of {@link InMemoryPermissionService}.
 */
class InMemoryPermissionServiceTest {

    /**
     * Stable test subject used across the individual permission assertions.
     */
    private final UUID subjectId = UUID.randomUUID();

    private final UUID otherSubjectId = UUID.randomUUID();

    /**
     * Service instance recreated before each test to keep cases independent.
     */
    private PermissionService permissionService;

    /**
     * Creates a new service instance for each test case.
     */
    @BeforeEach
    void setUp() {
        permissionService = new InMemoryPermissionService();
    }

    /**
     * Confirms the default state for unknown permissions.
     */
    @Test
    void unsetPermissionsReturnUnsetAndFalse() {
        assertEquals(PermissionValue.UNSET, permissionService.getPermission(subjectId, PermissionNodes.ADMIN));
        assertFalse(permissionService.hasPermission(subjectId, PermissionNodes.ADMIN));
    }

    /**
     * Confirms that explicit grants and denials can both be stored and retrieved.
     */
    @Test
    void explicitTrueAndFalseValuesRoundTrip() {
        permissionService.setPermission(subjectId, PermissionNodes.ADMIN, PermissionValue.TRUE);
        assertEquals(PermissionValue.TRUE, permissionService.getPermission(subjectId, PermissionNodes.ADMIN));
        assertTrue(permissionService.hasPermission(subjectId, PermissionNodes.ADMIN));

        permissionService.setPermission(subjectId, PermissionNodes.ADMIN, PermissionValue.FALSE);
        assertEquals(PermissionValue.FALSE, permissionService.getPermission(subjectId, PermissionNodes.ADMIN));
        assertFalse(permissionService.hasPermission(subjectId, PermissionNodes.ADMIN));
    }

    /**
     * Confirms explicit assignments can be listed as an immutable normalized snapshot.
     */
    @Test
    void explicitPermissionsReturnImmutableNormalizedSnapshot() {
        permissionService.setPermission(subjectId, " Example.Node ", PermissionValue.TRUE);
        permissionService.setPermission(subjectId, "Example.Denied", PermissionValue.FALSE);

        Map<String, PermissionValue> permissions = permissionService.getPermissions(subjectId);

        assertEquals(Map.of("example.node", PermissionValue.TRUE, "example.denied", PermissionValue.FALSE), permissions);
        assertThrows(UnsupportedOperationException.class, () -> permissions.put("other.node", PermissionValue.TRUE));
        assertEquals(PermissionValue.UNSET, permissionService.getPermission(subjectId, "other.node"));
    }

    /**
     * Confirms that clearing a node removes its explicit assignment entirely.
     */
    @Test
    void clearingPermissionReturnsItToUnset() {
        permissionService.setPermission(subjectId, PermissionNodes.ADMIN, PermissionValue.TRUE);

        permissionService.clearPermission(subjectId, PermissionNodes.ADMIN);

        assertEquals(PermissionValue.UNSET, permissionService.getPermission(subjectId, PermissionNodes.ADMIN));
        assertFalse(permissionService.hasPermission(subjectId, PermissionNodes.ADMIN));
        assertEquals(Map.of(), permissionService.getPermissions(subjectId));
    }

    /**
     * Confirms clearing all permissions removes only the selected subject's explicit assignments.
     */
    @Test
    void clearingAllPermissionsReturnsRemovedCountAndLeavesOtherSubjects() {
        permissionService.setPermission(subjectId, "example.node", PermissionValue.TRUE);
        permissionService.setPermission(subjectId, "example.*", PermissionValue.FALSE);
        permissionService.setPermission(otherSubjectId, "other.node", PermissionValue.TRUE);

        assertEquals(2, permissionService.clearPermissions(subjectId));
        assertEquals(0, permissionService.clearPermissions(subjectId));

        assertEquals(Map.of(), permissionService.getPermissions(subjectId));
        assertEquals(Map.of("other.node", PermissionValue.TRUE), permissionService.getPermissions(otherSubjectId));
    }
}
