package me.clutchy.clutchperms.fabric;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import me.clutchy.clutchperms.common.InMemoryPermissionService;
import me.clutchy.clutchperms.common.PermissionService;
import me.clutchy.clutchperms.common.PermissionValue;

import static org.junit.jupiter.api.Assertions.assertEquals;

import net.fabricmc.fabric.api.util.TriState;

final class FabricRuntimePermissionBridgeTest {

    private static final UUID SUBJECT_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    private PermissionService permissionService;

    @BeforeEach
    void setUp() {
        permissionService = new InMemoryPermissionService();
    }

    @Test
    void directTrueAssignmentResolvesToTrue() {
        permissionService.setPermission(SUBJECT_ID, "example.node", PermissionValue.TRUE);

        assertEquals(TriState.TRUE, FabricRuntimePermissionBridge.resolve(permissionService, SUBJECT_ID, "example.node"));
    }

    @Test
    void directFalseAssignmentResolvesToFalse() {
        permissionService.setPermission(SUBJECT_ID, "example.node", PermissionValue.FALSE);

        assertEquals(TriState.FALSE, FabricRuntimePermissionBridge.resolve(permissionService, SUBJECT_ID, "example.node"));
    }

    @Test
    void unsetAssignmentResolvesToDefault() {
        assertEquals(TriState.DEFAULT, FabricRuntimePermissionBridge.resolve(permissionService, SUBJECT_ID, "example.node"));
    }

    @Test
    void invalidNodeResolvesToDefault() {
        assertEquals(TriState.DEFAULT, FabricRuntimePermissionBridge.resolve(permissionService, SUBJECT_ID, " "));
    }
}
