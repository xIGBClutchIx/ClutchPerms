package me.clutchy.clutchperms.fabric;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
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
import me.clutchy.clutchperms.common.SubjectMetadataServices;
import me.clutchy.clutchperms.common.command.ClutchPermsCommandEnvironment;
import me.clutchy.clutchperms.common.command.ClutchPermsCommands;
import me.clutchy.clutchperms.common.command.CommandSourceKind;
import me.clutchy.clutchperms.common.command.CommandStatusDiagnostics;
import me.clutchy.clutchperms.common.command.CommandSubject;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

    @Test
    void commandMutationPersistsAndResolvesThroughBridge(@TempDir Path temporaryDirectory) throws CommandSyntaxException {
        Path permissionsFile = temporaryDirectory.resolve("permissions.json");
        PermissionService persistedPermissionService = PermissionServices.jsonFile(permissionsFile);
        CommandDispatcher<TestSource> dispatcher = dispatcher(persistedPermissionService, temporaryDirectory);
        TestSource console = TestSource.console();

        assertEquals(1, dispatcher.execute("clutchperms user " + SUBJECT_ID + " set example.smoke true", console));
        assertEquals(PermissionValue.TRUE, PermissionServices.jsonFile(permissionsFile).getPermission(SUBJECT_ID, "example.smoke"));
        assertEquals(TriState.TRUE, FabricRuntimePermissionBridge.resolve(persistedPermissionService, SUBJECT_ID, "example.smoke"));

        assertEquals(1, dispatcher.execute("clutchperms user " + SUBJECT_ID + " set example.smoke false", console));
        assertEquals(PermissionValue.FALSE, PermissionServices.jsonFile(permissionsFile).getPermission(SUBJECT_ID, "example.smoke"));
        assertEquals(TriState.FALSE, FabricRuntimePermissionBridge.resolve(persistedPermissionService, SUBJECT_ID, "example.smoke"));

        assertEquals(1, dispatcher.execute("clutchperms user " + SUBJECT_ID + " clear example.smoke", console));
        assertEquals(PermissionValue.UNSET, PermissionServices.jsonFile(permissionsFile).getPermission(SUBJECT_ID, "example.smoke"));
        assertEquals(TriState.DEFAULT, FabricRuntimePermissionBridge.resolve(persistedPermissionService, SUBJECT_ID, "example.smoke"));
    }

    @Test
    void reloadCommandReloadsStorageForBridgeResolution(@TempDir Path temporaryDirectory) throws CommandSyntaxException {
        Path permissionsFile = temporaryDirectory.resolve("permissions.json");
        TestEnvironment environment = new TestEnvironment(PermissionServices.jsonFile(permissionsFile), temporaryDirectory);
        CommandDispatcher<TestSource> dispatcher = dispatcher(environment);
        TestSource console = TestSource.console();

        PermissionServices.jsonFile(permissionsFile).setPermission(SUBJECT_ID, "Example.Reload", PermissionValue.TRUE);
        assertEquals(TriState.DEFAULT, FabricRuntimePermissionBridge.resolve(environment.permissionService(), SUBJECT_ID, "example.reload"));

        assertEquals(1, dispatcher.execute("clutchperms reload", console));

        assertEquals(TriState.TRUE, FabricRuntimePermissionBridge.resolve(environment.permissionService(), SUBJECT_ID, "example.reload"));
        assertEquals(1, environment.runtimeRefreshes());
        assertEquals(List.of("Reloaded permissions and subjects from disk."), console.messages());
    }

    @Test
    void malformedPermissionsFileFailsReloadWithoutReplacingBridgeState(@TempDir Path temporaryDirectory) throws Exception {
        Path permissionsFile = temporaryDirectory.resolve("permissions.json");
        PermissionService activePermissionService = PermissionServices.jsonFile(permissionsFile);
        activePermissionService.setPermission(SUBJECT_ID, "Example.Reload", PermissionValue.TRUE);
        TestEnvironment environment = new TestEnvironment(activePermissionService, temporaryDirectory);
        CommandDispatcher<TestSource> dispatcher = dispatcher(environment);
        TestSource console = TestSource.console();

        Files.writeString(permissionsFile, "{ malformed permissions json", StandardCharsets.UTF_8);

        CommandSyntaxException exception = assertThrows(CommandSyntaxException.class, () -> dispatcher.execute("clutchperms reload", console));

        assertTrue(exception.getMessage().contains("Failed to reload ClutchPerms storage:"));
        assertEquals(TriState.TRUE, FabricRuntimePermissionBridge.resolve(environment.permissionService(), SUBJECT_ID, "example.reload"));
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
                    "test fabric bridge");
        }

        @Override
        public void reloadStorage() {
            permissionService = PermissionServices.jsonFile(storageDirectory.resolve("permissions.json"));
            subjectMetadataService = SubjectMetadataServices.jsonFile(storageDirectory.resolve("subjects.json"));
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
