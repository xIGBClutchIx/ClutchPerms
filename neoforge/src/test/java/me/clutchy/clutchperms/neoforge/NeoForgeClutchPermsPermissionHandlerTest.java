package me.clutchy.clutchperms.neoforge;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

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
import me.clutchy.clutchperms.common.SubjectMetadataServices;
import me.clutchy.clutchperms.common.command.ClutchPermsCommandEnvironment;
import me.clutchy.clutchperms.common.command.ClutchPermsCommands;
import me.clutchy.clutchperms.common.command.CommandSourceKind;
import me.clutchy.clutchperms.common.command.CommandStatusDiagnostics;
import me.clutchy.clutchperms.common.command.CommandSubject;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
    void suppliedPermissionServiceCanBeReplacedAfterReload() {
        AtomicReference<PermissionService> permissionServiceReference = new AtomicReference<>(permissionService);
        NeoForgeClutchPermsPermissionHandler suppliedHandler = new NeoForgeClutchPermsPermissionHandler(permissionServiceReference::get, List.of(booleanNode));
        PermissionService reloadedPermissionService = new InMemoryPermissionService();

        reloadedPermissionService.setPermission(SUBJECT_ID, "example.node", PermissionValue.FALSE);
        permissionServiceReference.set(reloadedPermissionService);

        assertEquals(Boolean.FALSE, suppliedHandler.getOfflinePermission(SUBJECT_ID, booleanNode));
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

    @Test
    void malformedPermissionsFileFailsReloadWithoutReplacingPermissionHandlerState(@TempDir Path temporaryDirectory) throws Exception {
        Path permissionsFile = temporaryDirectory.resolve("permissions.json");
        PermissionService activePermissionService = PermissionServices.jsonFile(permissionsFile);
        activePermissionService.setPermission(SUBJECT_ID, "example.node", PermissionValue.FALSE);
        TestEnvironment environment = new TestEnvironment(activePermissionService, temporaryDirectory);
        NeoForgeClutchPermsPermissionHandler suppliedHandler = new NeoForgeClutchPermsPermissionHandler(environment::permissionService, List.of(booleanNode));
        CommandDispatcher<TestSource> dispatcher = dispatcher(environment);
        TestSource console = TestSource.console();

        Files.writeString(permissionsFile, "{ malformed permissions json", StandardCharsets.UTF_8);

        CommandSyntaxException exception = assertThrows(CommandSyntaxException.class, () -> dispatcher.execute("clutchperms reload", console));

        assertTrue(exception.getMessage().contains("Failed to reload ClutchPerms storage:"));
        assertEquals(Boolean.FALSE, suppliedHandler.getOfflinePermission(SUBJECT_ID, booleanNode));
        assertEquals(0, environment.runtimeRefreshes());
        assertEquals(List.of(), console.messages());
    }

    private static CommandDispatcher<TestSource> dispatcher(PermissionService permissionService, Path storageDirectory) {
        return dispatcher(new TestEnvironment(permissionService, storageDirectory));
    }

    private static CommandDispatcher<TestSource> dispatcher(TestEnvironment environment) {
        CommandDispatcher<TestSource> dispatcher = new CommandDispatcher<>();
        dispatcher.getRoot().addChild(ClutchPermsCommands.create(environment));
        return dispatcher;
    }

    private static final class TestEnvironment implements ClutchPermsCommandEnvironment<TestSource> {

        private PermissionService permissionService;

        private SubjectMetadataService subjectMetadataService = new InMemorySubjectMetadataService();

        private final Path storageDirectory;

        private int runtimeRefreshes;

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
        public void reloadStorage() {
            PermissionService reloadedPermissionService = PermissionServices.jsonFile(storageDirectory.resolve("permissions.json"));
            SubjectMetadataService reloadedSubjectMetadataService = SubjectMetadataServices.jsonFile(storageDirectory.resolve("subjects.json"));
            permissionService = reloadedPermissionService;
            subjectMetadataService = reloadedSubjectMetadataService;
        }

        @Override
        public void refreshRuntimePermissions() {
            runtimeRefreshes++;
        }

        private int runtimeRefreshes() {
            return runtimeRefreshes;
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
