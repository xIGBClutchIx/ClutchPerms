package me.clutchy.clutchperms.forge;

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
import me.clutchy.clutchperms.common.permission.PermissionNodes;
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

import net.minecraftforge.server.permission.nodes.PermissionNode;
import net.minecraftforge.server.permission.nodes.PermissionTypes;

final class ForgeClutchPermsPermissionHandlerTest {

    private static final UUID SUBJECT_ID = UUID.fromString("00000000-0000-0000-0000-000000000003");

    private PermissionService permissionService;

    private GroupService groupService;

    private PermissionResolver permissionResolver;

    private PermissionNode<Boolean> booleanNode;

    private ForgeClutchPermsPermissionHandler handler;

    @BeforeEach
    void setUp() {
        PermissionService storagePermissionService = new InMemoryPermissionService();
        GroupService storageGroupService = new InMemoryGroupService();
        permissionResolver = new PermissionResolver(storagePermissionService, storageGroupService);
        permissionService = PermissionServices.observing(storagePermissionService, permissionResolver::invalidateSubject);
        groupService = observingGroupService(storageGroupService, permissionResolver);
        booleanNode = new PermissionNode<>("example", "node", PermissionTypes.BOOLEAN, (player, subjectId, context) -> Boolean.TRUE);
        handler = new ForgeClutchPermsPermissionHandler(permissionResolver, List.of(booleanNode));
    }

    @Test
    void handlerReportsIdentifierAndRegisteredNodes() {
        assertEquals(ForgeClutchPermsPermissionHandler.IDENTIFIER, handler.getIdentifier());
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
    void inheritedGroupAssignmentOverridesBooleanDefault() {
        groupService.createGroup("base");
        groupService.createGroup("staff");
        groupService.addGroupParent("staff", "base");
        groupService.addSubjectGroup(SUBJECT_ID, "staff");

        groupService.setGroupPermission("base", "example.node", PermissionValue.TRUE);
        assertEquals(Boolean.TRUE, handler.getOfflinePermission(SUBJECT_ID, booleanNode));

        groupService.setGroupPermission("base", "example.node", PermissionValue.FALSE);
        assertEquals(Boolean.FALSE, handler.getOfflinePermission(SUBJECT_ID, booleanNode));

        groupService.clearGroupPermission("base", "example.node");
        assertEquals(Boolean.TRUE, handler.getOfflinePermission(SUBJECT_ID, booleanNode));
    }

    @Test
    void wildcardAssignmentOverridesBooleanDefault() {
        permissionService.setPermission(SUBJECT_ID, "example.*", PermissionValue.FALSE);
        assertEquals(Boolean.FALSE, handler.getOfflinePermission(SUBJECT_ID, booleanNode));

        permissionService.setPermission(SUBJECT_ID, "example.*", PermissionValue.TRUE);
        assertEquals(Boolean.TRUE, handler.getOfflinePermission(SUBJECT_ID, booleanNode));

        permissionService.clearPermission(SUBJECT_ID, "example.*");
        assertEquals(Boolean.TRUE, handler.getOfflinePermission(SUBJECT_ID, booleanNode));
    }

    @Test
    void nonBooleanNodeFallsBackToNodeDefault() {
        PermissionNode<String> stringNode = new PermissionNode<>("example", "label", PermissionTypes.STRING, (player, subjectId, context) -> "default");
        permissionService.setPermission(SUBJECT_ID, "example.label", PermissionValue.FALSE);

        assertEquals("default", handler.resolve(SUBJECT_ID, stringNode, "default"));
    }

    @Test
    void registeredBooleanNodeNamesExcludeNonBooleanNodes() {
        PermissionNode<Boolean> booleanNode = new PermissionNode<>("example", "boolean", PermissionTypes.BOOLEAN, (player, subjectId, context) -> Boolean.FALSE);
        PermissionNode<String> stringNode = new PermissionNode<>("example", "string", PermissionTypes.STRING, (player, subjectId, context) -> "default");

        assertEquals(List.of("example.boolean"), ForgeClutchPermsPermissionHandler.booleanNodeNames(List.of(stringNode, booleanNode)));
    }

    @Test
    void commandNodesMatchSharedBooleanRegistrations() {
        assertTrue(ForgeClutchPermsPermissionHandler.COMMAND_NODES.stream().allMatch(node -> node.getType() == PermissionTypes.BOOLEAN));
        assertEquals(PermissionNodes.commandNodes(), ForgeClutchPermsPermissionHandler.COMMAND_NODES.stream().map(PermissionNode::getNodeName).toList());
    }

    @Test
    void suppliedPermissionServiceCanBeReplacedAfterReload() {
        AtomicReference<PermissionResolver> permissionResolverReference = new AtomicReference<>(permissionResolver);
        ForgeClutchPermsPermissionHandler suppliedHandler = new ForgeClutchPermsPermissionHandler(permissionResolverReference::get, List.of(booleanNode));
        PermissionService reloadedPermissionService = new InMemoryPermissionService();
        GroupService reloadedGroupService = new InMemoryGroupService();

        reloadedPermissionService.setPermission(SUBJECT_ID, "example.node", PermissionValue.FALSE);
        permissionResolverReference.set(new PermissionResolver(reloadedPermissionService, reloadedGroupService));

        assertEquals(Boolean.FALSE, suppliedHandler.getOfflinePermission(SUBJECT_ID, booleanNode));
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
    void commandMutationPersistsAndResolvesThroughPermissionHandler(@TempDir Path temporaryDirectory) throws CommandSyntaxException {
        Path permissionsFile = temporaryDirectory.resolve("permissions.json");
        Path groupsFile = temporaryDirectory.resolve("groups.json");
        PermissionService persistedPermissionService = PermissionServices.jsonFile(permissionsFile);
        GroupService persistedGroupService = GroupServices.jsonFile(groupsFile);
        PermissionResolver persistedPermissionResolver = new PermissionResolver(persistedPermissionService, persistedGroupService);
        PermissionNode<Boolean> falseDefaultNode = new PermissionNode<>("example", "allow", PermissionTypes.BOOLEAN, (player, subjectId, context) -> Boolean.FALSE);
        PermissionNode<Boolean> trueDefaultNode = new PermissionNode<>("example", "deny", PermissionTypes.BOOLEAN, (player, subjectId, context) -> Boolean.TRUE);
        PermissionNode<Boolean> groupNode = new PermissionNode<>("example", "group", PermissionTypes.BOOLEAN, (player, subjectId, context) -> Boolean.FALSE);
        PermissionNode<Boolean> wildcardNode = new PermissionNode<>("wildcard", "node", PermissionTypes.BOOLEAN, (player, subjectId, context) -> Boolean.TRUE);
        ForgeClutchPermsPermissionHandler persistedHandler = new ForgeClutchPermsPermissionHandler(persistedPermissionResolver,
                List.of(falseDefaultNode, trueDefaultNode, groupNode, wildcardNode));
        CommandDispatcher<TestSource> dispatcher = dispatcher(persistedPermissionService, persistedGroupService, persistedPermissionResolver, temporaryDirectory);
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

        assertEquals(1, dispatcher.execute("clutchperms group admin create", console));
        assertEquals(1, dispatcher.execute("clutchperms group admin set example.group true", console));
        assertEquals(1, dispatcher.execute("clutchperms user " + SUBJECT_ID + " group add admin", console));
        assertEquals(PermissionValue.TRUE, GroupServices.jsonFile(groupsFile).getGroupPermission("admin", "example.group"));
        assertEquals(Boolean.TRUE, persistedHandler.getOfflinePermission(SUBJECT_ID, groupNode));

        assertEquals(1, dispatcher.execute("clutchperms group base create", console));
        assertEquals(1, dispatcher.execute("clutchperms group base set example.group true", console));
        assertEquals(1, dispatcher.execute("clutchperms group admin parent add base", console));
        assertEquals(Boolean.TRUE, persistedHandler.getOfflinePermission(SUBJECT_ID, groupNode));

        assertEquals(1, dispatcher.execute("clutchperms user " + SUBJECT_ID + " set wildcard.* false", console));
        assertEquals(PermissionValue.FALSE, PermissionServices.jsonFile(permissionsFile).getPermission(SUBJECT_ID, "wildcard.*"));
        assertEquals(Boolean.FALSE, persistedHandler.getOfflinePermission(SUBJECT_ID, wildcardNode));
    }

    @Test
    void backupRestoreReloadsPermissionHandlerState(@TempDir Path temporaryDirectory) throws CommandSyntaxException {
        TestEnvironment environment = new TestEnvironment(PermissionServices.jsonFile(temporaryDirectory.resolve("permissions.json")), temporaryDirectory);
        PermissionNode<Boolean> backupNode = new PermissionNode<>("example", "backup", PermissionTypes.BOOLEAN, (player, subjectId, context) -> Boolean.FALSE);
        ForgeClutchPermsPermissionHandler suppliedHandler = new ForgeClutchPermsPermissionHandler(environment::permissionResolver, List.of(backupNode));
        CommandDispatcher<TestSource> dispatcher = dispatcher(environment);
        TestSource console = TestSource.console();

        assertEquals(1, dispatcher.execute("clutchperms user " + SUBJECT_ID + " set example.backup true", console));
        assertEquals(1, dispatcher.execute("clutchperms user " + SUBJECT_ID + " set example.backup false", console));
        assertEquals(Boolean.FALSE, suppliedHandler.getOfflinePermission(SUBJECT_ID, backupNode));
        StorageBackup backup = environment.storageBackupService().listBackups(StorageFileKind.PERMISSIONS).getFirst();
        assertEquals(PermissionValue.TRUE, PermissionServices.jsonFile(backup.path()).getPermission(SUBJECT_ID, "example.backup"));

        assertEquals(1, dispatcher.execute("clutchperms backup restore permissions " + backup.fileName(), console));

        assertEquals(PermissionValue.TRUE, PermissionServices.jsonFile(temporaryDirectory.resolve("permissions.json")).getPermission(SUBJECT_ID, "example.backup"));
        assertEquals(Boolean.TRUE, suppliedHandler.getOfflinePermission(SUBJECT_ID, backupNode));
        assertEquals(1, environment.runtimeRefreshes());
    }

    @Test
    void malformedBackupRestoreFailsValidationAndPreservesPermissionHandlerState(@TempDir Path temporaryDirectory) throws Exception {
        Path permissionsFile = temporaryDirectory.resolve("permissions.json");
        PermissionService activePermissionService = PermissionServices.jsonFile(permissionsFile);
        activePermissionService.setPermission(SUBJECT_ID, "example.backup", PermissionValue.TRUE);
        TestEnvironment environment = new TestEnvironment(activePermissionService, temporaryDirectory);
        PermissionNode<Boolean> backupNode = new PermissionNode<>("example", "backup", PermissionTypes.BOOLEAN, (player, subjectId, context) -> Boolean.FALSE);
        ForgeClutchPermsPermissionHandler suppliedHandler = new ForgeClutchPermsPermissionHandler(environment::permissionResolver, List.of(backupNode));
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
                    "00000000-0000-0000-0000-000000000003": {
                      "example.*.bad": "TRUE"
                    }
                  }
                }
                """, StandardCharsets.UTF_8);

        assertCommandFails(dispatcher, "clutchperms backup restore permissions " + backupFileName, console,
                "Backup operation failed: Failed to validate permissions backup " + backupFileName);

        assertEquals(liveBeforeRestore, Files.readString(permissionsFile));
        assertEquals(Boolean.TRUE, suppliedHandler.getOfflinePermission(SUBJECT_ID, backupNode));
        assertEquals(0, environment.runtimeRefreshes());
    }

    @Test
    void malformedPermissionsFileFailsReloadWithoutReplacingPermissionHandlerState(@TempDir Path temporaryDirectory) throws Exception {
        Path permissionsFile = temporaryDirectory.resolve("permissions.json");
        PermissionService activePermissionService = PermissionServices.jsonFile(permissionsFile);
        activePermissionService.setPermission(SUBJECT_ID, "example.node", PermissionValue.FALSE);
        TestEnvironment environment = new TestEnvironment(activePermissionService, temporaryDirectory);
        ForgeClutchPermsPermissionHandler suppliedHandler = new ForgeClutchPermsPermissionHandler(environment::permissionResolver, List.of(booleanNode));
        CommandDispatcher<TestSource> dispatcher = dispatcher(environment);
        TestSource console = TestSource.console();

        Files.writeString(permissionsFile, """
                {
                  "version": 1,
                  "subjects": {
                    "00000000-0000-0000-0000-000000000003": {
                      "example.*.bad": "TRUE"
                    }
                  }
                }
                """, StandardCharsets.UTF_8);

        assertCommandFails(dispatcher, "clutchperms reload", console, "Failed to reload ClutchPerms storage:");

        assertEquals(Boolean.FALSE, suppliedHandler.getOfflinePermission(SUBJECT_ID, booleanNode));
        assertEquals(0, environment.runtimeRefreshes());
    }

    @Test
    void validateCommandChecksStorageWithoutReplacingPermissionHandlerState(@TempDir Path temporaryDirectory) throws CommandSyntaxException {
        Path permissionsFile = temporaryDirectory.resolve("permissions.json");
        PermissionService activePermissionService = PermissionServices.jsonFile(permissionsFile);
        activePermissionService.setPermission(SUBJECT_ID, "example.node", PermissionValue.FALSE);
        TestEnvironment environment = new TestEnvironment(activePermissionService, temporaryDirectory);
        ForgeClutchPermsPermissionHandler suppliedHandler = new ForgeClutchPermsPermissionHandler(environment::permissionResolver, List.of(booleanNode));
        CommandDispatcher<TestSource> dispatcher = dispatcher(environment);
        TestSource console = TestSource.console();

        PermissionServices.jsonFile(permissionsFile).setPermission(SUBJECT_ID, "example.node", PermissionValue.TRUE);

        assertEquals(1, dispatcher.execute("clutchperms validate", console));

        assertEquals(Boolean.FALSE, suppliedHandler.getOfflinePermission(SUBJECT_ID, booleanNode));
        assertEquals(0, environment.runtimeRefreshes());
        assertEquals(List.of("Validated permissions, subjects, groups, and known nodes from disk."), console.messages());
    }

    @Test
    void malformedPermissionsFileFailsValidateWithoutReplacingPermissionHandlerState(@TempDir Path temporaryDirectory) throws Exception {
        Path permissionsFile = temporaryDirectory.resolve("permissions.json");
        PermissionService activePermissionService = PermissionServices.jsonFile(permissionsFile);
        activePermissionService.setPermission(SUBJECT_ID, "example.node", PermissionValue.FALSE);
        TestEnvironment environment = new TestEnvironment(activePermissionService, temporaryDirectory);
        ForgeClutchPermsPermissionHandler suppliedHandler = new ForgeClutchPermsPermissionHandler(environment::permissionResolver, List.of(booleanNode));
        CommandDispatcher<TestSource> dispatcher = dispatcher(environment);
        TestSource console = TestSource.console();

        Files.writeString(permissionsFile, """
                {
                  "version": 1,
                  "subjects": {
                    "00000000-0000-0000-0000-000000000003": {
                      "example.*.bad": "TRUE"
                    }
                  }
                }
                """, StandardCharsets.UTF_8);

        assertCommandFails(dispatcher, "clutchperms validate", console, "Failed to validate ClutchPerms storage:");

        assertEquals(Boolean.FALSE, suppliedHandler.getOfflinePermission(SUBJECT_ID, booleanNode));
        assertEquals(0, environment.runtimeRefreshes());
    }

    @Test
    void commandMutationPersistsAndReloadsKnownNodes(@TempDir Path temporaryDirectory) throws CommandSyntaxException {
        Path nodesFile = temporaryDirectory.resolve("nodes.json");
        TestEnvironment environment = new TestEnvironment(PermissionServices.jsonFile(temporaryDirectory.resolve("permissions.json")), temporaryDirectory);
        CommandDispatcher<TestSource> dispatcher = dispatcher(environment);
        TestSource console = TestSource.console();

        assertEquals(1, dispatcher.execute("clutchperms nodes add example.forge Forge node", console));
        assertTrue(PermissionNodeRegistries.jsonFile(nodesFile).getKnownNode("example.forge").isPresent());
        assertEquals(1, environment.runtimeRefreshes());

        PermissionNodeRegistries.jsonFile(nodesFile).addNode("manual.reload", "Reloaded node");
        assertTrue(environment.permissionNodeRegistry().getKnownNode("manual.reload").isEmpty());

        assertEquals(1, dispatcher.execute("clutchperms reload", console));

        assertTrue(environment.permissionNodeRegistry().getKnownNode("manual.reload").isPresent());
    }

    @Test
    void malformedNodesFileFailsReloadWithoutReplacingPermissionHandlerState(@TempDir Path temporaryDirectory) throws Exception {
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

    @Test
    void malformedGroupsFileFailsReloadWithoutReplacingPermissionHandlerState(@TempDir Path temporaryDirectory) throws Exception {
        Path permissionsFile = temporaryDirectory.resolve("permissions.json");
        Path groupsFile = temporaryDirectory.resolve("groups.json");
        PermissionService activePermissionService = PermissionServices.jsonFile(permissionsFile);
        GroupService activeGroupService = GroupServices.jsonFile(groupsFile);
        activeGroupService.createGroup("staff");
        activeGroupService.setGroupPermission("staff", "example.groupreload", PermissionValue.TRUE);
        activeGroupService.addSubjectGroup(SUBJECT_ID, "staff");
        TestEnvironment environment = new TestEnvironment(activePermissionService, activeGroupService, new PermissionResolver(activePermissionService, activeGroupService),
                temporaryDirectory);
        PermissionNode<Boolean> groupReloadNode = new PermissionNode<>("example", "groupreload", PermissionTypes.BOOLEAN, (player, subjectId, context) -> Boolean.FALSE);
        ForgeClutchPermsPermissionHandler suppliedHandler = new ForgeClutchPermsPermissionHandler(environment::permissionResolver, List.of(groupReloadNode));
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

        assertEquals(Boolean.TRUE, suppliedHandler.getOfflinePermission(SUBJECT_ID, groupReloadNode));
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
                    storageDirectory.resolve("groups.json").toString(), storageDirectory.resolve("nodes.json").toString(), "test forge bridge");
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
