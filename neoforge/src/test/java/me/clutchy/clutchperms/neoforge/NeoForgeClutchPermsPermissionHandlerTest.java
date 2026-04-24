package me.clutchy.clutchperms.neoforge;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import me.clutchy.clutchperms.common.InMemoryPermissionService;
import me.clutchy.clutchperms.common.PermissionService;
import me.clutchy.clutchperms.common.PermissionValue;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.neoforged.neoforge.server.permission.nodes.PermissionNode;
import net.neoforged.neoforge.server.permission.nodes.PermissionTypes;

final class NeoForgeClutchPermsPermissionHandlerTest {

    private static final UUID SUBJECT_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");

    private PermissionService permissionService;

    private PermissionNode<Boolean> booleanNode;

    private NeoForgeClutchPermsPermissionHandler handler;

    @BeforeEach
    void setUp() {
        permissionService = new InMemoryPermissionService();
        booleanNode = new PermissionNode<>("example", "node", PermissionTypes.BOOLEAN, (player, subjectId, context) -> Boolean.TRUE);
        handler = new NeoForgeClutchPermsPermissionHandler(permissionService, List.of(booleanNode));
    }

    @Test
    void handlerReportsIdentifierAndRegisteredNodes() {
        assertEquals(NeoForgeClutchPermsPermissionHandler.IDENTIFIER, handler.getIdentifier());
        assertTrue(handler.getRegisteredNodes().contains(booleanNode));
    }

    @Test
    void directTrueAssignmentOverridesBooleanDefault() {
        permissionService.setPermission(SUBJECT_ID, "example.node", PermissionValue.TRUE);

        assertEquals(Boolean.TRUE, handler.getOfflinePermission(SUBJECT_ID, booleanNode));
    }

    @Test
    void directFalseAssignmentOverridesBooleanDefault() {
        permissionService.setPermission(SUBJECT_ID, "example.node", PermissionValue.FALSE);

        assertEquals(Boolean.FALSE, handler.getOfflinePermission(SUBJECT_ID, booleanNode));
    }

    @Test
    void unsetBooleanAssignmentFallsBackToNodeDefault() {
        assertEquals(Boolean.TRUE, handler.getOfflinePermission(SUBJECT_ID, booleanNode));
    }

    @Test
    void nonBooleanNodeFallsBackToNodeDefault() {
        PermissionNode<String> stringNode = new PermissionNode<>("example", "label", PermissionTypes.STRING, (player, subjectId, context) -> "default");
        permissionService.setPermission(SUBJECT_ID, "example.label", PermissionValue.FALSE);

        assertEquals("default", handler.resolve(SUBJECT_ID, stringNode, "default"));
    }

    @Test
    void adminNodeMatchesSharedBooleanRegistration() {
        assertSame(PermissionTypes.BOOLEAN, NeoForgeClutchPermsPermissionHandler.ADMIN_NODE.getType());
        assertEquals("clutchperms.admin", NeoForgeClutchPermsPermissionHandler.ADMIN_NODE.getNodeName());
    }
}
