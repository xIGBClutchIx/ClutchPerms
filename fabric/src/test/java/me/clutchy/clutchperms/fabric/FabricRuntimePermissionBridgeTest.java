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

import me.clutchy.clutchperms.common.command.ClutchPermsCommandEnvironment;
import me.clutchy.clutchperms.common.command.ClutchPermsCommands;
import me.clutchy.clutchperms.common.command.CommandSourceKind;
import me.clutchy.clutchperms.common.command.CommandStatusDiagnostics;
import me.clutchy.clutchperms.common.command.CommandSubject;
import me.clutchy.clutchperms.common.group.GroupChangeListener;
import me.clutchy.clutchperms.common.group.GroupService;
import me.clutchy.clutchperms.common.group.GroupServices;
import me.clutchy.clutchperms.common.group.InMemoryGroupService;
import me.clutchy.clutchperms.common.node.MutablePermissionNodeRegistry;
import me.clutchy.clutchperms.common.node.PermissionNodeRegistries;
import me.clutchy.clutchperms.common.node.PermissionNodeRegistry;
import me.clutchy.clutchperms.common.permission.InMemoryPermissionService;
import me.clutchy.clutchperms.common.permission.PermissionResolver;
import me.clutchy.clutchperms.common.permission.PermissionService;
import me.clutchy.clutchperms.common.permission.PermissionServices;
import me.clutchy.clutchperms.common.permission.PermissionValue;
import me.clutchy.clutchperms.common.storage.StorageBackup;
import me.clutchy.clutchperms.common.storage.StorageBackupService;
import me.clutchy.clutchperms.common.storage.StorageFileKind;
import me.clutchy.clutchperms.common.subject.InMemorySubjectMetadataService;
import me.clutchy.clutchperms.common.subject.SubjectMetadataService;
import me.clutchy.clutchperms.common.subject.SubjectMetadataServices;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.fabricmc.fabric.api.util.TriState;

final class FabricRuntimePermissionBridgeTest {

    private static final UUID SUBJECT_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    private PermissionService permissionService;

    private GroupService groupService;

    private PermissionResolver permissionResolver;

    @BeforeEach
    void setUp() {
        PermissionService storagePermissionService = new InMemoryPermissionService();
        GroupService storageGroupService = new InMemoryGroupService();
        permissionResolver = new PermissionResolver(storagePermissionService, storageGroupService);
        permissionService = PermissionServices.observing(storagePermissionService, permissionResolver::invalidateSubject);
        groupService = observingGroupService(storageGroupService, permissionResolver);
    }

    @Test
    void directTrueAssignmentResolvesToTrue() {
        permissionService.setPermission(SUBJECT_ID, "example.node", PermissionValue.TRUE);

        assertEquals(TriState.TRUE, FabricRuntimePermissionBridge.resolve(permissionResolver, SUBJECT_ID, "example.node"));
    }

    @Test
    void directFalseAssignmentResolvesToFalse() {
        permissionService.setPermission(SUBJECT_ID, "example.node", PermissionValue.FALSE);

        assertEquals(TriState.FALSE, FabricRuntimePermissionBridge.resolve(permissionResolver, SUBJECT_ID, "example.node"));
    }

    @Test
    void unsetAssignmentResolvesToDefault() {
        assertEquals(TriState.DEFAULT, FabricRuntimePermissionBridge.resolve(permissionResolver, SUBJECT_ID, "example.node"));
    }

    @Test
    void invalidNodeResolvesToDefault() {
        assertEquals(TriState.DEFAULT, FabricRuntimePermissionBridge.resolve(permissionResolver, SUBJECT_ID, " "));
    }

    @Test
    void inheritedGroupAssignmentResolvesThroughBridge() {
        groupService.createGroup("base");
        groupService.createGroup("staff");
        groupService.addGroupParent("staff", "base");
        groupService.addSubjectGroup(SUBJECT_ID, "staff");

        groupService.setGroupPermission("base", "example.inherited", PermissionValue.TRUE);
        assertEquals(TriState.TRUE, FabricRuntimePermissionBridge.resolve(permissionResolver, SUBJECT_ID, "example.inherited"));

        groupService.setGroupPermission("base", "example.inherited", PermissionValue.FALSE);
        assertEquals(TriState.FALSE, FabricRuntimePermissionBridge.resolve(permissionResolver, SUBJECT_ID, "example.inherited"));

        groupService.clearGroupPermission("base", "example.inherited");
        assertEquals(TriState.DEFAULT, FabricRuntimePermissionBridge.resolve(permissionResolver, SUBJECT_ID, "example.inherited"));
    }

    @Test
    void wildcardAssignmentResolvesThroughBridge() {
        permissionService.setPermission(SUBJECT_ID, "example.*", PermissionValue.TRUE);
        assertEquals(TriState.TRUE, FabricRuntimePermissionBridge.resolve(permissionResolver, SUBJECT_ID, "example.node"));

        permissionService.setPermission(SUBJECT_ID, "example.*", PermissionValue.FALSE);
        assertEquals(TriState.FALSE, FabricRuntimePermissionBridge.resolve(permissionResolver, SUBJECT_ID, "example.node"));

        permissionService.clearPermission(SUBJECT_ID, "example.*");
        assertEquals(TriState.DEFAULT, FabricRuntimePermissionBridge.resolve(permissionResolver, SUBJECT_ID, "example.node"));
    }

    @Test
    void commandAliasesExecuteStatusCommand(@TempDir Path temporaryDirectory) throws CommandSyntaxException {
        CommandDispatcher<TestSource> dispatcher = dispatcher(permissionService, groupService, permissionResolver, temporaryDirectory);
        TestSource console = TestSource.console();

        assertEquals(1, dispatcher.execute("cperms status", console));
        assertTrue(console.messages().contains(ClutchPermsCommands.STATUS_MESSAGE));

        console.messages().clear();

        assertEquals(1, dispatcher.execute("perms status", console));
        assertTrue(console.messages().contains(ClutchPermsCommands.STATUS_MESSAGE));
    }

    @Test
    void commandMutationPersistsAndResolvesThroughBridge(@TempDir Path temporaryDirectory) throws CommandSyntaxException {
        Path permissionsFile = temporaryDirectory.resolve("permissions.json");
        Path groupsFile = temporaryDirectory.resolve("groups.json");
        PermissionService persistedPermissionService = PermissionServices.jsonFile(permissionsFile);
        GroupService persistedGroupService = GroupServices.jsonFile(groupsFile);
        PermissionResolver persistedPermissionResolver = new PermissionResolver(persistedPermissionService, persistedGroupService);
        CommandDispatcher<TestSource> dispatcher = dispatcher(persistedPermissionService, persistedGroupService, persistedPermissionResolver, temporaryDirectory);
        TestSource console = TestSource.console();

        assertEquals(1, dispatcher.execute("clutchperms user " + SUBJECT_ID + " set example.smoke true", console));
        assertEquals(PermissionValue.TRUE, PermissionServices.jsonFile(permissionsFile).getPermission(SUBJECT_ID, "example.smoke"));
        assertEquals(TriState.TRUE, FabricRuntimePermissionBridge.resolve(persistedPermissionResolver, SUBJECT_ID, "example.smoke"));

        assertEquals(1, dispatcher.execute("clutchperms user " + SUBJECT_ID + " set example.smoke false", console));
        assertEquals(PermissionValue.FALSE, PermissionServices.jsonFile(permissionsFile).getPermission(SUBJECT_ID, "example.smoke"));
        assertEquals(TriState.FALSE, FabricRuntimePermissionBridge.resolve(persistedPermissionResolver, SUBJECT_ID, "example.smoke"));

        assertEquals(1, dispatcher.execute("clutchperms user " + SUBJECT_ID + " clear example.smoke", console));
        assertEquals(PermissionValue.UNSET, PermissionServices.jsonFile(permissionsFile).getPermission(SUBJECT_ID, "example.smoke"));
        assertEquals(TriState.DEFAULT, FabricRuntimePermissionBridge.resolve(persistedPermissionResolver, SUBJECT_ID, "example.smoke"));

        assertEquals(1, dispatcher.execute("clutchperms group admin create", console));
        assertEquals(1, dispatcher.execute("clutchperms group admin set example.group true", console));
        assertEquals(1, dispatcher.execute("clutchperms user " + SUBJECT_ID + " group add admin", console));
        assertEquals(PermissionValue.TRUE, GroupServices.jsonFile(groupsFile).getGroupPermission("admin", "example.group"));
        assertEquals(TriState.TRUE, FabricRuntimePermissionBridge.resolve(persistedPermissionResolver, SUBJECT_ID, "example.group"));

        assertEquals(1, dispatcher.execute("clutchperms group base create", console));
        assertEquals(1, dispatcher.execute("clutchperms group base set example.inherited true", console));
        assertEquals(1, dispatcher.execute("clutchperms group admin parent add base", console));
        assertEquals(PermissionValue.TRUE, GroupServices.jsonFile(groupsFile).getGroupPermission("base", "example.inherited"));
        assertEquals(TriState.TRUE, FabricRuntimePermissionBridge.resolve(persistedPermissionResolver, SUBJECT_ID, "example.inherited"));

        assertEquals(1, dispatcher.execute("clutchperms user " + SUBJECT_ID + " set wildcard.* false", console));
        assertEquals(PermissionValue.FALSE, PermissionServices.jsonFile(permissionsFile).getPermission(SUBJECT_ID, "wildcard.*"));
        assertEquals(TriState.FALSE, FabricRuntimePermissionBridge.resolve(persistedPermissionResolver, SUBJECT_ID, "wildcard.node"));
    }

    @Test
    void backupRestoreReloadsBridgeVisibleState(@TempDir Path temporaryDirectory) throws CommandSyntaxException {
        TestEnvironment environment = new TestEnvironment(PermissionServices.jsonFile(temporaryDirectory.resolve("permissions.json")), temporaryDirectory);
        CommandDispatcher<TestSource> dispatcher = dispatcher(environment);
        TestSource console = TestSource.console();

        assertEquals(1, dispatcher.execute("clutchperms user " + SUBJECT_ID + " set example.backup true", console));
        assertEquals(1, dispatcher.execute("clutchperms user " + SUBJECT_ID + " set example.backup false", console));
        assertEquals(TriState.FALSE, FabricRuntimePermissionBridge.resolve(environment.permissionResolver(), SUBJECT_ID, "example.backup"));
        StorageBackup backup = environment.storageBackupService().listBackups(StorageFileKind.PERMISSIONS).getFirst();
        assertEquals(PermissionValue.TRUE, PermissionServices.jsonFile(backup.path()).getPermission(SUBJECT_ID, "example.backup"));

        assertEquals(1, dispatcher.execute("clutchperms backup restore permissions " + backup.fileName(), console));

        assertEquals(PermissionValue.TRUE, PermissionServices.jsonFile(temporaryDirectory.resolve("permissions.json")).getPermission(SUBJECT_ID, "example.backup"));
        assertEquals(TriState.TRUE, FabricRuntimePermissionBridge.resolve(environment.permissionResolver(), SUBJECT_ID, "example.backup"));
        assertEquals(1, environment.runtimeRefreshes());
    }

    @Test
    void malformedBackupRestoreFailsValidationAndPreservesBridgeState(@TempDir Path temporaryDirectory) throws Exception {
        Path permissionsFile = temporaryDirectory.resolve("permissions.json");
        PermissionService activePermissionService = PermissionServices.jsonFile(permissionsFile);
        activePermissionService.setPermission(SUBJECT_ID, "Example.Backup", PermissionValue.TRUE);
        TestEnvironment environment = new TestEnvironment(activePermissionService, temporaryDirectory);
        CommandDispatcher<TestSource> dispatcher = dispatcher(environment);
        TestSource console = TestSource.console();
        String liveBeforeRestore = Files.readString(permissionsFile);
        Path backupDirectory = temporaryDirectory.resolve("backups").resolve("permissions");
        Files.createDirectories(backupDirectory);
        String backupFileName = "permissions-20260424-120000000.json";
        Files.writeString(backupDirectory.resolve(backupFileName), """
                {
                  "version": 1,
                  "subjects": {
                    "00000000-0000-0000-0000-000000000001": {
                      "example.*.bad": "TRUE"
                    }
                  }
                }
                """, StandardCharsets.UTF_8);

        assertCommandFails(dispatcher, "clutchperms backup restore permissions " + backupFileName, console,
                "Backup operation failed: Failed to validate permissions backup " + backupFileName);

        assertEquals(liveBeforeRestore, Files.readString(permissionsFile));
        assertEquals(TriState.TRUE, FabricRuntimePermissionBridge.resolve(environment.permissionResolver(), SUBJECT_ID, "example.backup"));
        assertEquals(0, environment.runtimeRefreshes());
    }

    @Test
    void reloadCommandReloadsStorageForBridgeResolution(@TempDir Path temporaryDirectory) throws CommandSyntaxException {
        Path permissionsFile = temporaryDirectory.resolve("permissions.json");
        TestEnvironment environment = new TestEnvironment(PermissionServices.jsonFile(permissionsFile), temporaryDirectory);
        CommandDispatcher<TestSource> dispatcher = dispatcher(environment);
        TestSource console = TestSource.console();

        PermissionServices.jsonFile(permissionsFile).setPermission(SUBJECT_ID, "Example.Reload", PermissionValue.TRUE);
        assertEquals(TriState.DEFAULT, FabricRuntimePermissionBridge.resolve(environment.permissionResolver(), SUBJECT_ID, "example.reload"));

        assertEquals(1, dispatcher.execute("clutchperms reload", console));

        assertEquals(TriState.TRUE, FabricRuntimePermissionBridge.resolve(environment.permissionResolver(), SUBJECT_ID, "example.reload"));
        assertEquals(1, environment.runtimeRefreshes());
        assertEquals(List.of("Reloaded config, permissions, subjects, groups, and known nodes from disk."), console.messages());
    }

    @Test
    void validateCommandChecksStorageWithoutReplacingBridgeState(@TempDir Path temporaryDirectory) throws CommandSyntaxException {
        Path permissionsFile = temporaryDirectory.resolve("permissions.json");
        PermissionService activePermissionService = PermissionServices.jsonFile(permissionsFile);
        activePermissionService.setPermission(SUBJECT_ID, "Example.Validate", PermissionValue.FALSE);
        TestEnvironment environment = new TestEnvironment(activePermissionService, temporaryDirectory);
        CommandDispatcher<TestSource> dispatcher = dispatcher(environment);
        TestSource console = TestSource.console();

        PermissionServices.jsonFile(permissionsFile).setPermission(SUBJECT_ID, "Example.Validate", PermissionValue.TRUE);

        assertEquals(1, dispatcher.execute("clutchperms validate", console));

        assertEquals(TriState.FALSE, FabricRuntimePermissionBridge.resolve(environment.permissionResolver(), SUBJECT_ID, "example.validate"));
        assertEquals(0, environment.runtimeRefreshes());
        assertEquals(List.of("Validated config, permissions, subjects, groups, and known nodes from disk."), console.messages());
    }

    @Test
    void malformedPermissionsFileFailsValidateWithoutReplacingBridgeState(@TempDir Path temporaryDirectory) throws Exception {
        Path permissionsFile = temporaryDirectory.resolve("permissions.json");
        PermissionService activePermissionService = PermissionServices.jsonFile(permissionsFile);
        activePermissionService.setPermission(SUBJECT_ID, "Example.Validate", PermissionValue.FALSE);
        TestEnvironment environment = new TestEnvironment(activePermissionService, temporaryDirectory);
        CommandDispatcher<TestSource> dispatcher = dispatcher(environment);
        TestSource console = TestSource.console();

        Files.writeString(permissionsFile, """
                {
                  "version": 1,
                  "subjects": {
                    "00000000-0000-0000-0000-000000000001": {
                      "example.*.bad": "TRUE"
                    }
                  }
                }
                """, StandardCharsets.UTF_8);

        assertCommandFails(dispatcher, "clutchperms validate", console, "Failed to validate ClutchPerms storage:");

        assertEquals(TriState.FALSE, FabricRuntimePermissionBridge.resolve(environment.permissionResolver(), SUBJECT_ID, "example.validate"));
        assertEquals(0, environment.runtimeRefreshes());
    }

    @Test
    void malformedPermissionsFileFailsReloadWithoutReplacingBridgeState(@TempDir Path temporaryDirectory) throws Exception {
        Path permissionsFile = temporaryDirectory.resolve("permissions.json");
        PermissionService activePermissionService = PermissionServices.jsonFile(permissionsFile);
        activePermissionService.setPermission(SUBJECT_ID, "Example.Reload", PermissionValue.TRUE);
        TestEnvironment environment = new TestEnvironment(activePermissionService, temporaryDirectory);
        CommandDispatcher<TestSource> dispatcher = dispatcher(environment);
        TestSource console = TestSource.console();

        Files.writeString(permissionsFile, """
                {
                  "version": 1,
                  "subjects": {
                    "00000000-0000-0000-0000-000000000001": {
                      "example.*.bad": "TRUE"
                    }
                  }
                }
                """, StandardCharsets.UTF_8);

        assertCommandFails(dispatcher, "clutchperms reload", console, "Failed to reload ClutchPerms storage:");

        assertEquals(TriState.TRUE, FabricRuntimePermissionBridge.resolve(environment.permissionResolver(), SUBJECT_ID, "example.reload"));
        assertEquals(0, environment.runtimeRefreshes());
    }

    @Test
    void malformedGroupsFileFailsReloadWithoutReplacingBridgeState(@TempDir Path temporaryDirectory) throws Exception {
        Path permissionsFile = temporaryDirectory.resolve("permissions.json");
        Path groupsFile = temporaryDirectory.resolve("groups.json");
        PermissionService activePermissionService = PermissionServices.jsonFile(permissionsFile);
        GroupService activeGroupService = GroupServices.jsonFile(groupsFile);
        activeGroupService.createGroup("staff");
        activeGroupService.setGroupPermission("staff", "Example.GroupReload", PermissionValue.TRUE);
        activeGroupService.addSubjectGroup(SUBJECT_ID, "staff");
        TestEnvironment environment = new TestEnvironment(activePermissionService, activeGroupService, new PermissionResolver(activePermissionService, activeGroupService),
                temporaryDirectory);
        CommandDispatcher<TestSource> dispatcher = dispatcher(environment);
        TestSource console = TestSource.console();

        Files.writeString(groupsFile, """
                {
                  "version": 1,
                  "groups": {
                    "staff": {
                      "permissions": {},
                      "parents": {}
                    }
                  },
                  "memberships": {}
                }
                """, StandardCharsets.UTF_8);

        assertCommandFails(dispatcher, "clutchperms reload", console, "Failed to reload ClutchPerms storage:");

        assertEquals(TriState.TRUE, FabricRuntimePermissionBridge.resolve(environment.permissionResolver(), SUBJECT_ID, "example.groupreload"));
        assertEquals(0, environment.runtimeRefreshes());
    }

    @Test
    void commandMutationPersistsAndReloadsKnownNodes(@TempDir Path temporaryDirectory) throws CommandSyntaxException {
        Path nodesFile = temporaryDirectory.resolve("nodes.json");
        TestEnvironment environment = new TestEnvironment(PermissionServices.jsonFile(temporaryDirectory.resolve("permissions.json")), temporaryDirectory);
        CommandDispatcher<TestSource> dispatcher = dispatcher(environment);
        TestSource console = TestSource.console();

        assertEquals(1, dispatcher.execute("clutchperms nodes add example.fabric Fabric node", console));
        assertTrue(PermissionNodeRegistries.jsonFile(nodesFile).getKnownNode("example.fabric").isPresent());
        assertEquals(1, environment.runtimeRefreshes());

        PermissionNodeRegistries.jsonFile(nodesFile).addNode("manual.reload", "Reloaded node");
        assertTrue(environment.permissionNodeRegistry().getKnownNode("manual.reload").isEmpty());

        assertEquals(1, dispatcher.execute("clutchperms reload", console));

        assertTrue(environment.permissionNodeRegistry().getKnownNode("manual.reload").isPresent());
    }

    @Test
    void malformedNodesFileFailsReloadWithoutReplacingBridgeState(@TempDir Path temporaryDirectory) throws Exception {
        Path nodesFile = temporaryDirectory.resolve("nodes.json");
        PermissionNodeRegistries.jsonFile(nodesFile).addNode("active.node", "Active");
        TestEnvironment environment = new TestEnvironment(PermissionServices.jsonFile(temporaryDirectory.resolve("permissions.json")), temporaryDirectory);
        CommandDispatcher<TestSource> dispatcher = dispatcher(environment);
        TestSource console = TestSource.console();

        Files.writeString(nodesFile, """
                {
                  "version": 1,
                  "nodes": {
                    "bad.*": {}
                  }
                }
                """, StandardCharsets.UTF_8);

        assertCommandFails(dispatcher, "clutchperms reload", console, "Failed to reload ClutchPerms storage:");

        assertTrue(environment.permissionNodeRegistry().getKnownNode("active.node").isPresent());
        assertEquals(0, environment.runtimeRefreshes());
    }

    private static CommandDispatcher<TestSource> dispatcher(PermissionService permissionService, GroupService groupService, PermissionResolver permissionResolver,
            Path storageDirectory) {
        return dispatcher(new TestEnvironment(permissionService, groupService, permissionResolver, storageDirectory));
    }

    private static CommandDispatcher<TestSource> dispatcher(TestEnvironment environment) {
        CommandDispatcher<TestSource> dispatcher = new CommandDispatcher<>();
        ClutchPermsCommands.ROOT_LITERALS.forEach(rootLiteral -> dispatcher.getRoot().addChild(ClutchPermsCommands.create(environment, rootLiteral)));
        return dispatcher;
    }

    private static void assertCommandFails(CommandDispatcher<TestSource> dispatcher, String command, TestSource source, String expectedMessage) {
        try {
            assertEquals(0, dispatcher.execute(command, source));
        } catch (CommandSyntaxException exception) {
            throw new AssertionError("Expected styled command failure for " + command, exception);
        }
        assertTrue(!source.messages().isEmpty(), "Expected command feedback");
        assertTrue(source.messages().get(source.messages().size() - 1).contains(expectedMessage),
                () -> "Expected latest message to contain <" + expectedMessage + "> but was " + source.messages());
    }

    private static final class TestEnvironment implements ClutchPermsCommandEnvironment<TestSource> {

        private PermissionService permissionService;

        private GroupService groupService;

        private PermissionResolver permissionResolver;

        private MutablePermissionNodeRegistry manualPermissionNodeRegistry;

        private PermissionNodeRegistry permissionNodeRegistry;

        private SubjectMetadataService subjectMetadataService = new InMemorySubjectMetadataService();

        private final Path storageDirectory;

        private int runtimeRefreshes;

        private TestEnvironment(PermissionService permissionService, Path storageDirectory) {
            this(permissionService, GroupServices.jsonFile(storageDirectory.resolve("groups.json")), null, storageDirectory);
        }

        private TestEnvironment(PermissionService permissionService, GroupService groupService, PermissionResolver permissionResolver, Path storageDirectory) {
            this.permissionResolver = permissionResolver == null ? new PermissionResolver(permissionService, groupService) : permissionResolver;
            this.permissionService = PermissionServices.observing(permissionService, this.permissionResolver::invalidateSubject);
            this.groupService = observingGroupService(groupService, this.permissionResolver);
            this.storageDirectory = storageDirectory;
            this.manualPermissionNodeRegistry = PermissionNodeRegistries.observing(PermissionNodeRegistries.jsonFile(storageDirectory.resolve("nodes.json")),
                    this::refreshRuntimePermissions);
            this.permissionNodeRegistry = PermissionNodeRegistries.composite(PermissionNodeRegistries.builtIn(), manualPermissionNodeRegistry);
        }

        @Override
        public PermissionService permissionService() {
            return permissionService;
        }

        @Override
        public GroupService groupService() {
            return groupService;
        }

        @Override
        public PermissionNodeRegistry permissionNodeRegistry() {
            return permissionNodeRegistry;
        }

        @Override
        public MutablePermissionNodeRegistry manualPermissionNodeRegistry() {
            return manualPermissionNodeRegistry;
        }

        @Override
        public PermissionResolver permissionResolver() {
            return permissionResolver;
        }

        @Override
        public SubjectMetadataService subjectMetadataService() {
            return subjectMetadataService;
        }

        @Override
        public CommandStatusDiagnostics statusDiagnostics() {
            return new CommandStatusDiagnostics(storageDirectory.resolve("permissions.json").toString(), storageDirectory.resolve("subjects.json").toString(),
                    storageDirectory.resolve("groups.json").toString(), storageDirectory.resolve("nodes.json").toString(), "test fabric bridge");
        }

        @Override
        public void reloadStorage() {
            PermissionService reloadedStoragePermissionService = PermissionServices.jsonFile(storageDirectory.resolve("permissions.json"));
            SubjectMetadataService reloadedSubjectMetadataService = SubjectMetadataServices.jsonFile(storageDirectory.resolve("subjects.json"));
            GroupService reloadedStorageGroupService = GroupServices.jsonFile(storageDirectory.resolve("groups.json"));
            MutablePermissionNodeRegistry reloadedManualPermissionNodeRegistry = PermissionNodeRegistries
                    .observing(PermissionNodeRegistries.jsonFile(storageDirectory.resolve("nodes.json")), this::refreshRuntimePermissions);
            PermissionNodeRegistry reloadedPermissionNodeRegistry = PermissionNodeRegistries.composite(PermissionNodeRegistries.builtIn(), reloadedManualPermissionNodeRegistry);
            permissionResolver = new PermissionResolver(reloadedStoragePermissionService, reloadedStorageGroupService);
            permissionService = PermissionServices.observing(reloadedStoragePermissionService, permissionResolver::invalidateSubject);
            subjectMetadataService = reloadedSubjectMetadataService;
            groupService = observingGroupService(reloadedStorageGroupService, permissionResolver);
            manualPermissionNodeRegistry = reloadedManualPermissionNodeRegistry;
            permissionNodeRegistry = reloadedPermissionNodeRegistry;
        }

        @Override
        public void validateStorage() {
            PermissionServices.jsonFile(storageDirectory.resolve("permissions.json"));
            SubjectMetadataServices.jsonFile(storageDirectory.resolve("subjects.json"));
            GroupServices.jsonFile(storageDirectory.resolve("groups.json"));
            PermissionNodeRegistries.jsonFile(storageDirectory.resolve("nodes.json"));
        }

        @Override
        public StorageBackupService storageBackupService() {
            return StorageBackupService.forFiles(storageDirectory.resolve("backups"),
                    java.util.Map.of(StorageFileKind.PERMISSIONS, storageDirectory.resolve("permissions.json"), StorageFileKind.SUBJECTS, storageDirectory.resolve("subjects.json"),
                            StorageFileKind.GROUPS, storageDirectory.resolve("groups.json"), StorageFileKind.NODES, storageDirectory.resolve("nodes.json")));
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

    private static GroupService observingGroupService(GroupService groupService, PermissionResolver permissionResolver) {
        return GroupServices.observing(groupService, new GroupChangeListener() {

            @Override
            public void subjectGroupsChanged(UUID subjectId) {
                permissionResolver.invalidateSubject(subjectId);
            }

            @Override
            public void groupsChanged() {
                permissionResolver.invalidateAll();
            }
        });
    }
}
