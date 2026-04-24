package me.clutchy.clutchperms.neoforge;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import me.clutchy.clutchperms.common.InMemoryPermissionService;
import me.clutchy.clutchperms.common.InMemorySubjectMetadataService;
import me.clutchy.clutchperms.common.PermissionService;
import me.clutchy.clutchperms.common.PermissionServices;
import me.clutchy.clutchperms.common.PermissionValue;
import me.clutchy.clutchperms.common.SubjectMetadataService;
import me.clutchy.clutchperms.common.command.ClutchPermsCommandEnvironment;
import me.clutchy.clutchperms.common.command.ClutchPermsCommands;
import me.clutchy.clutchperms.common.command.CommandSourceKind;
import me.clutchy.clutchperms.common.command.CommandStatusDiagnostics;
import me.clutchy.clutchperms.common.command.CommandSubject;

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

    @Test
    void commandMutationPersistsAndResolvesThroughPermissionHandler(@TempDir Path temporaryDirectory) throws CommandSyntaxException {
        Path permissionsFile = temporaryDirectory.resolve("permissions.json");
        PermissionService persistedPermissionService = PermissionServices.jsonFile(permissionsFile);
        PermissionNode<Boolean> falseDefaultNode = new PermissionNode<>("example", "allow", PermissionTypes.BOOLEAN, (player, subjectId, context) -> Boolean.FALSE);
        PermissionNode<Boolean> trueDefaultNode = new PermissionNode<>("example", "deny", PermissionTypes.BOOLEAN, (player, subjectId, context) -> Boolean.TRUE);
        NeoForgeClutchPermsPermissionHandler persistedHandler = new NeoForgeClutchPermsPermissionHandler(persistedPermissionService, List.of(falseDefaultNode, trueDefaultNode));
        CommandDispatcher<TestSource> dispatcher = dispatcher(persistedPermissionService, temporaryDirectory);
        TestSource console = TestSource.console();

        assertEquals(1, dispatcher.execute("clutchperms user " + SUBJECT_ID + " set example.allow true", console));
        assertEquals(PermissionValue.TRUE, PermissionServices.jsonFile(permissionsFile).getPermission(SUBJECT_ID, "example.allow"));
        assertEquals(Boolean.TRUE, persistedHandler.getOfflinePermission(SUBJECT_ID, falseDefaultNode));

        assertEquals(1, dispatcher.execute("clutchperms user " + SUBJECT_ID + " set example.deny false", console));
        assertEquals(PermissionValue.FALSE, PermissionServices.jsonFile(permissionsFile).getPermission(SUBJECT_ID, "example.deny"));
        assertEquals(Boolean.FALSE, persistedHandler.getOfflinePermission(SUBJECT_ID, trueDefaultNode));

        assertEquals(1, dispatcher.execute("clutchperms user " + SUBJECT_ID + " clear example.deny", console));
        assertEquals(PermissionValue.UNSET, PermissionServices.jsonFile(permissionsFile).getPermission(SUBJECT_ID, "example.deny"));
        assertEquals(Boolean.TRUE, persistedHandler.getOfflinePermission(SUBJECT_ID, trueDefaultNode));
    }

    private static CommandDispatcher<TestSource> dispatcher(PermissionService permissionService, Path storageDirectory) {
        CommandDispatcher<TestSource> dispatcher = new CommandDispatcher<>();
        dispatcher.getRoot().addChild(ClutchPermsCommands.create(new TestEnvironment(permissionService, storageDirectory)));
        return dispatcher;
    }

    private static final class TestEnvironment implements ClutchPermsCommandEnvironment<TestSource> {

        private final PermissionService permissionService;

        private final SubjectMetadataService subjectMetadataService = new InMemorySubjectMetadataService();

        private final Path storageDirectory;

        private TestEnvironment(PermissionService permissionService, Path storageDirectory) {
            this.permissionService = permissionService;
            this.storageDirectory = storageDirectory;
        }

        @Override
        public PermissionService permissionService() {
            return permissionService;
        }

        @Override
        public SubjectMetadataService subjectMetadataService() {
            return subjectMetadataService;
        }

        @Override
        public CommandStatusDiagnostics statusDiagnostics() {
            return new CommandStatusDiagnostics(storageDirectory.resolve("permissions.json").toString(), storageDirectory.resolve("subjects.json").toString(),
                    "test neoforge bridge");
        }

        @Override
        public CommandSourceKind sourceKind(TestSource source) {
            return source.kind();
        }

        @Override
        public Optional<UUID> sourceSubjectId(TestSource source) {
            return Optional.empty();
        }

        @Override
        public Optional<CommandSubject> findOnlineSubject(TestSource source, String target) {
            return Optional.empty();
        }

        @Override
        public Collection<String> onlineSubjectNames(TestSource source) {
            return List.of();
        }

        @Override
        public void sendMessage(TestSource source, String message) {
            source.messages().add(message);
        }
    }

    private record TestSource(CommandSourceKind kind, List<String> messages) {

        private static TestSource console() {
            return new TestSource(CommandSourceKind.CONSOLE, new ArrayList<>());
        }
    }
}
