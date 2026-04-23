package me.clutchy.clutchperms.common;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies the baseline behavior of {@link InMemoryPermissionService}.
 */
class InMemoryPermissionServiceTest {

    /**
     * Stable test subject used across the individual permission assertions.
     */
    private final UUID subjectId = UUID.randomUUID();

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
     * Confirms that clearing a node removes its explicit assignment entirely.
     */
    @Test
    void clearingPermissionReturnsItToUnset() {
        permissionService.setPermission(subjectId, PermissionNodes.ADMIN, PermissionValue.TRUE);

        permissionService.clearPermission(subjectId, PermissionNodes.ADMIN);

        assertEquals(PermissionValue.UNSET, permissionService.getPermission(subjectId, PermissionNodes.ADMIN));
        assertFalse(permissionService.hasPermission(subjectId, PermissionNodes.ADMIN));
    }
}
