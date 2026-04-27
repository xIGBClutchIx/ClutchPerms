package me.clutchy.clutchperms.neoforge;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Statement;
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
import me.clutchy.clutchperms.common.storage.SqliteDependencyMode;
import me.clutchy.clutchperms.common.storage.SqliteStore;
import me.clutchy.clutchperms.common.storage.StorageBackup;
import me.clutchy.clutchperms.common.storage.StorageBackupService;
import me.clutchy.clutchperms.common.storage.StorageFileKind;
import me.clutchy.clutchperms.common.subject.InMemorySubjectMetadataService;
import me.clutchy.clutchperms.common.subject.SubjectMetadataService;
import me.clutchy.clutchperms.common.subject.SubjectMetadataServices;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.neoforged.neoforge.server.permission.nodes.PermissionNode;
import net.neoforged.neoforge.server.permission.nodes.PermissionTypes;

final class NeoForgeClutchPermsPermissionHandlerTest {

    private static final UUID SUBJECT_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");

    private PermissionService permissionService;

    private GroupService groupService;

    private PermissionResolver permissionResolver;

    private PermissionNode<Boolean> booleanNode;

    private NeoForgeClutchPermsPermissionHandler handler;

    @BeforeEach
    void setUp() {
        PermissionService storagePermissionService = new InMemoryPermissionService();
        GroupService storageGroupService = new InMemoryGroupService();
        permissionResolver = new PermissionResolver(storagePermissionService, storageGroupService);
        permissionService = PermissionServices.observing(storagePermissionService, permissionResolver::invalidateSubject);
        groupService = observingGroupService(storageGroupService, permissionResolver);
        booleanNode = new PermissionNode<>("example", "node", PermissionTypes.BOOLEAN, (player, subjectId, context) -> Boolean.TRUE);
        handler = new NeoForgeClutchPermsPermissionHandler(permissionResolver, List.of(booleanNode));
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

        assertEquals(List.of("example.boolean"), NeoForgeClutchPermsPermissionHandler.booleanNodeNames(List.of(stringNode, booleanNode)));
    }

    @Test
    void commandNodesMatchSharedBooleanRegistrations() {
        assertTrue(NeoForgeClutchPermsPermissionHandler.COMMAND_NODES.stream().allMatch(node -> node.getType() == PermissionTypes.BOOLEAN));
        assertEquals(PermissionNodes.commandNodes(), NeoForgeClutchPermsPermissionHandler.COMMAND_NODES.stream().map(PermissionNode::getNodeName).toList());
    }

    @Test
    void suppliedPermissionServiceCanBeReplacedAfterReload() {
        AtomicReference<PermissionResolver> permissionResolverReference = new AtomicReference<>(permissionResolver);
        NeoForgeClutchPermsPermissionHandler suppliedHandler = new NeoForgeClutchPermsPermissionHandler(permissionResolverReference::get, List.of(booleanNode));
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
        TestEnvironment environment = new TestEnvironment(temporaryDirectory);
        PermissionResolver persistedPermissionResolver = environment.permissionResolver();
        PermissionNode<Boolean> falseDefaultNode = new PermissionNode<>("example", "allow", PermissionTypes.BOOLEAN, (player, subjectId, context) -> Boolean.FALSE);
        PermissionNode<Boolean> trueDefaultNode = new PermissionNode<>("example", "deny", PermissionTypes.BOOLEAN, (player, subjectId, context) -> Boolean.TRUE);
        PermissionNode<Boolean> groupNode = new PermissionNode<>("example", "group", PermissionTypes.BOOLEAN, (player, subjectId, context) -> Boolean.FALSE);
        PermissionNode<Boolean> wildcardNode = new PermissionNode<>("wildcard", "node", PermissionTypes.BOOLEAN, (player, subjectId, context) -> Boolean.TRUE);
        NeoForgeClutchPermsPermissionHandler persistedHandler = new NeoForgeClutchPermsPermissionHandler(persistedPermissionResolver,
                List.of(falseDefaultNode, trueDefaultNode, groupNode, wildcardNode));
        CommandDispatcher<TestSource> dispatcher = dispatcher(environment);
        TestSource console = TestSource.console();

        assertEquals(1, dispatcher.execute("clutchperms user " + SUBJECT_ID + " set example.allow true", console));
        assertEquals(PermissionValue.TRUE, persistedPermissionValue(temporaryDirectory, "example.allow"));
        assertEquals(Boolean.TRUE, persistedHandler.getOfflinePermission(SUBJECT_ID, falseDefaultNode));

        assertEquals(1, dispatcher.execute("clutchperms user " + SUBJECT_ID + " set example.deny false", console));
        assertEquals(PermissionValue.FALSE, persistedPermissionValue(temporaryDirectory, "example.deny"));
        assertEquals(Boolean.FALSE, persistedHandler.getOfflinePermission(SUBJECT_ID, trueDefaultNode));

        assertEquals(1, dispatcher.execute("clutchperms user " + SUBJECT_ID + " clear example.deny", console));
        assertEquals(PermissionValue.UNSET, persistedPermissionValue(temporaryDirectory, "example.deny"));
        assertEquals(Boolean.TRUE, persistedHandler.getOfflinePermission(SUBJECT_ID, trueDefaultNode));

        assertEquals(1, dispatcher.execute("clutchperms group admin create", console));
        assertEquals(1, dispatcher.execute("clutchperms group admin set example.group true", console));
        assertEquals(1, dispatcher.execute("clutchperms user " + SUBJECT_ID + " group add admin", console));
        assertEquals(PermissionValue.TRUE, persistedGroupPermissionValue(temporaryDirectory, "admin", "example.group"));
        assertEquals(Boolean.TRUE, persistedHandler.getOfflinePermission(SUBJECT_ID, groupNode));

        assertEquals(1, dispatcher.execute("clutchperms group base create", console));
        assertEquals(1, dispatcher.execute("clutchperms group base set example.group true", console));
        assertEquals(1, dispatcher.execute("clutchperms group admin parent add base", console));
        assertEquals(Boolean.TRUE, persistedHandler.getOfflinePermission(SUBJECT_ID, groupNode));

        assertEquals(1, dispatcher.execute("clutchperms user " + SUBJECT_ID + " set wildcard.* false", console));
        assertEquals(PermissionValue.FALSE, persistedPermissionValue(temporaryDirectory, "wildcard.*"));
        assertEquals(Boolean.FALSE, persistedHandler.getOfflinePermission(SUBJECT_ID, wildcardNode));
    }

    @Test
    void commandMutationsTriggerScopedCommandRefreshes(@TempDir Path temporaryDirectory) throws CommandSyntaxException {
        TestEnvironment environment = new TestEnvironment(temporaryDirectory);
        CommandDispatcher<TestSource> dispatcher = dispatcher(environment);
        TestSource console = TestSource.console();

        assertEquals(1, dispatcher.execute("clutchperms user " + SUBJECT_ID + " set example.refresh true", console));
        assertEquals(1, environment.subjectRuntimeRefreshes());
        assertEquals(0, environment.runtimeRefreshes());

        assertEquals(1, dispatcher.execute("clutchperms user " + SUBJECT_ID + " clear example.refresh", console));
        assertEquals(2, environment.subjectRuntimeRefreshes());
        assertEquals(0, environment.runtimeRefreshes());

        assertEquals(1, dispatcher.execute("clutchperms group admin create", console));
        assertEquals(2, environment.subjectRuntimeRefreshes());
        assertEquals(1, environment.runtimeRefreshes());

        assertEquals(1, dispatcher.execute("clutchperms group admin set example.group true", console));
        assertEquals(2, environment.subjectRuntimeRefreshes());
        assertEquals(2, environment.runtimeRefreshes());

        assertEquals(1, dispatcher.execute("clutchperms user " + SUBJECT_ID + " group add admin", console));
        assertEquals(3, environment.subjectRuntimeRefreshes());
        assertEquals(2, environment.runtimeRefreshes());

        assertEquals(1, dispatcher.execute("clutchperms user " + SUBJECT_ID + " group remove admin", console));
        assertEquals(4, environment.subjectRuntimeRefreshes());
        assertEquals(2, environment.runtimeRefreshes());

        assertEquals(1, dispatcher.execute("clutchperms group base create", console));
        assertEquals(4, environment.subjectRuntimeRefreshes());
        assertEquals(3, environment.runtimeRefreshes());

        assertEquals(1, dispatcher.execute("clutchperms group admin parent add base", console));
        assertEquals(4, environment.subjectRuntimeRefreshes());
        assertEquals(4, environment.runtimeRefreshes());

        assertEquals(1, dispatcher.execute("clutchperms nodes add example.refresh Refresh node", console));
        assertEquals(4, environment.subjectRuntimeRefreshes());
        assertEquals(5, environment.runtimeRefreshes());
    }

    @Test
    void inactiveNeoForgeCommandRefreshesAreNoOps() {
        ClutchPermsNeoForgeMod.refreshRuntimeSubject(SUBJECT_ID);
        ClutchPermsNeoForgeMod.refreshRuntimePermissions();

        List<UUID> subjectRefreshes = new ArrayList<>();
        ClutchPermsNeoForgeMod.<UUID>refreshOnlineSubject(SUBJECT_ID, subjectId -> null, subjectRefreshes::add);
        assertTrue(subjectRefreshes.isEmpty());

        ClutchPermsNeoForgeMod.refreshOnlineSubject(SUBJECT_ID, subjectId -> subjectId, subjectRefreshes::add);
        assertEquals(List.of(SUBJECT_ID), subjectRefreshes);

        List<String> fullRefreshes = new ArrayList<>();
        ClutchPermsNeoForgeMod.refreshOnlinePlayers(List.of("one", "two"), fullRefreshes::add);
        assertEquals(List.of("one", "two"), fullRefreshes);
    }

    @Test
    void backupRestoreReloadsPermissionHandlerState(@TempDir Path temporaryDirectory) throws CommandSyntaxException {
        TestEnvironment environment = new TestEnvironment(temporaryDirectory);
        PermissionNode<Boolean> backupNode = new PermissionNode<>("example", "backup", PermissionTypes.BOOLEAN, (player, subjectId, context) -> Boolean.FALSE);
        NeoForgeClutchPermsPermissionHandler suppliedHandler = new NeoForgeClutchPermsPermissionHandler(environment::permissionResolver, List.of(backupNode));
        CommandDispatcher<TestSource> dispatcher = dispatcher(environment);
        TestSource console = TestSource.console();

        assertEquals(1, dispatcher.execute("clutchperms user " + SUBJECT_ID + " set example.backup true", console));
        assertEquals(1, dispatcher.execute("clutchperms backup create", console));
        assertEquals(1, dispatcher.execute("clutchperms user " + SUBJECT_ID + " set example.backup false", console));
        assertEquals(Boolean.FALSE, suppliedHandler.getOfflinePermission(SUBJECT_ID, backupNode));
        StorageBackup backup = environment.storageBackupService().listBackups(StorageFileKind.DATABASE).getFirst();
        assertEquals(PermissionValue.TRUE, persistedPermissionValueFromDatabase(backup.path(), "example.backup"));

        assertEquals(1, dispatcher.execute("clutchperms backup restore " + backup.fileName(), console));

        assertEquals(PermissionValue.TRUE, persistedPermissionValue(temporaryDirectory, "example.backup"));
        assertEquals(Boolean.TRUE, suppliedHandler.getOfflinePermission(SUBJECT_ID, backupNode));
        assertEquals(1, environment.runtimeRefreshes());
    }

    @Test
    void malformedBackupRestoreFailsValidationAndPreservesPermissionHandlerState(@TempDir Path temporaryDirectory) throws Exception {
        TestEnvironment environment = new TestEnvironment(temporaryDirectory);
        environment.permissionService().setPermission(SUBJECT_ID, "example.backup", PermissionValue.TRUE);
        PermissionNode<Boolean> backupNode = new PermissionNode<>("example", "backup", PermissionTypes.BOOLEAN, (player, subjectId, context) -> Boolean.FALSE);
        NeoForgeClutchPermsPermissionHandler suppliedHandler = new NeoForgeClutchPermsPermissionHandler(environment::permissionResolver, List.of(backupNode));
        CommandDispatcher<TestSource> dispatcher = dispatcher(environment);
        TestSource console = TestSource.console();
        PermissionValue liveBeforeRestore = persistedPermissionValue(temporaryDirectory, "example.backup");
        Path backupDirectory = temporaryDirectory.resolve("backups").resolve("database");
        Files.createDirectories(backupDirectory);
        String backupFileName = "database-20260424-120000000.db";
        Files.writeString(backupDirectory.resolve(backupFileName), "not sqlite");

        assertCommandFails(dispatcher, "clutchperms backup restore " + backupFileName, console, "Backup operation failed: Failed to validate database backup " + backupFileName);

        assertEquals(liveBeforeRestore, persistedPermissionValue(temporaryDirectory, "example.backup"));
        assertEquals(Boolean.TRUE, suppliedHandler.getOfflinePermission(SUBJECT_ID, backupNode));
        assertEquals(0, environment.runtimeRefreshes());
    }

    @Test
    void malformedPermissionsFileFailsReloadWithoutReplacingPermissionHandlerState(@TempDir Path temporaryDirectory) throws Exception {
        TestEnvironment environment = new TestEnvironment(temporaryDirectory);
        environment.permissionService().setPermission(SUBJECT_ID, "example.node", PermissionValue.FALSE);
        NeoForgeClutchPermsPermissionHandler suppliedHandler = new NeoForgeClutchPermsPermissionHandler(environment::permissionResolver, List.of(booleanNode));
        CommandDispatcher<TestSource> dispatcher = dispatcher(environment);
        TestSource console = TestSource.console();

        insertInvalidPermissionRow(temporaryDirectory);

        assertCommandFails(dispatcher, "clutchperms reload", console, "Failed to reload ClutchPerms storage:");

        assertEquals(Boolean.FALSE, suppliedHandler.getOfflinePermission(SUBJECT_ID, booleanNode));
        assertEquals(0, environment.runtimeRefreshes());
    }

    @Test
    void validateCommandChecksStorageWithoutReplacingPermissionHandlerState(@TempDir Path temporaryDirectory) throws CommandSyntaxException {
        TestEnvironment environment = new TestEnvironment(temporaryDirectory);
        environment.permissionService().setPermission(SUBJECT_ID, "example.node", PermissionValue.FALSE);
        NeoForgeClutchPermsPermissionHandler suppliedHandler = new NeoForgeClutchPermsPermissionHandler(environment::permissionResolver, List.of(booleanNode));
        CommandDispatcher<TestSource> dispatcher = dispatcher(environment);
        TestSource console = TestSource.console();

        try (SqliteStore store = openStore(temporaryDirectory)) {
            PermissionServices.sqlite(store).setPermission(SUBJECT_ID, "example.node", PermissionValue.TRUE);
        }

        assertEquals(1, dispatcher.execute("clutchperms validate", console));

        assertEquals(Boolean.FALSE, suppliedHandler.getOfflinePermission(SUBJECT_ID, booleanNode));
        assertEquals(0, environment.runtimeRefreshes());
        assertEquals(List.of("Validated config and database storage from disk."), console.messages());
    }

    @Test
    void malformedPermissionsFileFailsValidateWithoutReplacingPermissionHandlerState(@TempDir Path temporaryDirectory) throws Exception {
        TestEnvironment environment = new TestEnvironment(temporaryDirectory);
        environment.permissionService().setPermission(SUBJECT_ID, "example.node", PermissionValue.FALSE);
        NeoForgeClutchPermsPermissionHandler suppliedHandler = new NeoForgeClutchPermsPermissionHandler(environment::permissionResolver, List.of(booleanNode));
        CommandDispatcher<TestSource> dispatcher = dispatcher(environment);
        TestSource console = TestSource.console();

        insertInvalidPermissionRow(temporaryDirectory);

        assertCommandFails(dispatcher, "clutchperms validate", console, "Failed to validate ClutchPerms storage:");

        assertEquals(Boolean.FALSE, suppliedHandler.getOfflinePermission(SUBJECT_ID, booleanNode));
        assertEquals(0, environment.runtimeRefreshes());
    }

    @Test
    void commandMutationPersistsAndReloadsKnownNodes(@TempDir Path temporaryDirectory) throws CommandSyntaxException {
        TestEnvironment environment = new TestEnvironment(temporaryDirectory);
        CommandDispatcher<TestSource> dispatcher = dispatcher(environment);
        TestSource console = TestSource.console();

        assertEquals(1, dispatcher.execute("clutchperms nodes add example.neoforge NeoForge node", console));
        assertTrue(persistedKnownNode(temporaryDirectory, "example.neoforge"));
        assertEquals(1, environment.runtimeRefreshes());

        try (SqliteStore store = openStore(temporaryDirectory)) {
            PermissionNodeRegistries.sqlite(store).addNode("manual.reload", "Reloaded node");
        }
        assertTrue(environment.permissionNodeRegistry().getKnownNode("manual.reload").isEmpty());

        assertEquals(1, dispatcher.execute("clutchperms reload", console));

        assertTrue(environment.permissionNodeRegistry().getKnownNode("manual.reload").isPresent());
    }

    @Test
    void malformedNodesFileFailsReloadWithoutReplacingPermissionHandlerState(@TempDir Path temporaryDirectory) throws Exception {
        TestEnvironment environment = new TestEnvironment(temporaryDirectory);
        environment.manualPermissionNodeRegistry().addNode("active.node", "Active");
        int refreshesBeforeReload = environment.runtimeRefreshes();
        CommandDispatcher<TestSource> dispatcher = dispatcher(environment);
        TestSource console = TestSource.console();

        try (SqliteStore store = openStore(temporaryDirectory)) {
            store.write(connection -> {
                try (Statement statement = connection.createStatement()) {
                    statement.executeUpdate("INSERT INTO known_nodes (node, description) VALUES ('bad.*', '')");
                }
            });
        }

        assertCommandFails(dispatcher, "clutchperms reload", console, "Failed to reload ClutchPerms storage:");

        assertTrue(environment.permissionNodeRegistry().getKnownNode("active.node").isPresent());
        assertEquals(refreshesBeforeReload, environment.runtimeRefreshes());
    }

    @Test
    void malformedGroupsFileFailsReloadWithoutReplacingPermissionHandlerState(@TempDir Path temporaryDirectory) throws Exception {
        TestEnvironment environment = new TestEnvironment(temporaryDirectory);
        environment.groupService().createGroup("staff");
        environment.groupService().setGroupPermission("staff", "example.groupreload", PermissionValue.TRUE);
        environment.groupService().addSubjectGroup(SUBJECT_ID, "staff");
        int refreshesBeforeReload = environment.runtimeRefreshes();
        PermissionNode<Boolean> groupReloadNode = new PermissionNode<>("example", "groupreload", PermissionTypes.BOOLEAN, (player, subjectId, context) -> Boolean.FALSE);
        NeoForgeClutchPermsPermissionHandler suppliedHandler = new NeoForgeClutchPermsPermissionHandler(environment::permissionResolver, List.of(groupReloadNode));
        CommandDispatcher<TestSource> dispatcher = dispatcher(environment);
        TestSource console = TestSource.console();

        try (SqliteStore store = openStore(temporaryDirectory)) {
            store.write(connection -> {
                try (Statement statement = connection.createStatement()) {
                    statement.executeUpdate("INSERT INTO group_parents (group_name, parent_name) VALUES ('staff', 'staff')");
                }
            });
        }

        assertCommandFails(dispatcher, "clutchperms reload", console, "Failed to reload ClutchPerms storage:");

        assertEquals(Boolean.TRUE, suppliedHandler.getOfflinePermission(SUBJECT_ID, groupReloadNode));
        assertEquals(refreshesBeforeReload, environment.runtimeRefreshes());
    }

    private static CommandDispatcher<TestSource> dispatcher(PermissionService permissionService, GroupService groupService, PermissionResolver permissionResolver,
            Path storageDirectory) {
        return dispatcher(new TestEnvironment(storageDirectory));
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

    private static SqliteStore openStore(Path storageDirectory) {
        return SqliteStore.open(databaseFile(storageDirectory), SqliteDependencyMode.ANY_VISIBLE);
    }

    private static Path databaseFile(Path storageDirectory) {
        return storageDirectory.resolve("database.db");
    }

    private static PermissionValue persistedPermissionValue(Path storageDirectory, String node) {
        return persistedPermissionValueFromDatabase(databaseFile(storageDirectory), node);
    }

    private static PermissionValue persistedPermissionValueFromDatabase(Path databaseFile, String node) {
        try (SqliteStore store = SqliteStore.open(databaseFile, SqliteDependencyMode.ANY_VISIBLE)) {
            return PermissionServices.sqlite(store).getPermission(SUBJECT_ID, node);
        }
    }

    private static PermissionValue persistedGroupPermissionValue(Path storageDirectory, String groupName, String node) {
        try (SqliteStore store = openStore(storageDirectory)) {
            return GroupServices.sqlite(store).getGroupPermission(groupName, node);
        }
    }

    private static boolean persistedKnownNode(Path storageDirectory, String node) {
        try (SqliteStore store = openStore(storageDirectory)) {
            return PermissionNodeRegistries.sqlite(store).getKnownNode(node).isPresent();
        }
    }

    private static void insertInvalidPermissionRow(Path storageDirectory) {
        try (SqliteStore store = openStore(storageDirectory)) {
            store.write(connection -> {
                try (Statement statement = connection.createStatement()) {
                    statement.executeUpdate("INSERT INTO subject_permissions (subject_id, node, value) VALUES ('00000000-0000-0000-0000-000000000002', 'example.*.bad', 'TRUE')");
                }
            });
        }
    }

    private static final class TestEnvironment implements ClutchPermsCommandEnvironment<TestSource> {

        private PermissionService permissionService;

        private GroupService groupService;

        private PermissionResolver permissionResolver;

        private MutablePermissionNodeRegistry manualPermissionNodeRegistry;

        private PermissionNodeRegistry permissionNodeRegistry;

        private SubjectMetadataService subjectMetadataService = new InMemorySubjectMetadataService();

        private final Path storageDirectory;

        private SqliteStore store;

        private int runtimeRefreshes;

        private int subjectRuntimeRefreshes;

        private TestEnvironment(Path storageDirectory) {
            this(openStore(storageDirectory), storageDirectory);
        }

        private TestEnvironment(SqliteStore store, Path storageDirectory) {
            this.storageDirectory = storageDirectory;
            applyStore(store);
        }

        private void applyStore(SqliteStore newStore) {
            this.store = newStore;
            PermissionService storagePermissionService = PermissionServices.sqlite(newStore);
            GroupService storageGroupService = GroupServices.sqlite(newStore);
            SubjectMetadataService storageSubjectMetadataService = SubjectMetadataServices.sqlite(newStore);
            MutablePermissionNodeRegistry storageManualPermissionNodeRegistry = PermissionNodeRegistries.observing(PermissionNodeRegistries.sqlite(newStore),
                    this::refreshRuntimePermissions);
            this.permissionResolver = new PermissionResolver(storagePermissionService, storageGroupService);
            this.permissionService = PermissionServices.observing(storagePermissionService, subjectId -> {
                this.permissionResolver.invalidateSubject(subjectId);
                refreshRuntimeSubject(subjectId);
            });
            this.groupService = observingGroupService(storageGroupService, this.permissionResolver, this::refreshRuntimeSubject, this::refreshRuntimePermissions);
            this.subjectMetadataService = storageSubjectMetadataService;
            this.manualPermissionNodeRegistry = storageManualPermissionNodeRegistry;
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
            return new CommandStatusDiagnostics(databaseFile(storageDirectory).toString(), "test neoforge bridge", storageDirectory.resolve("config.json").toString());
        }

        @Override
        public void reloadStorage() {
            SqliteStore previousStore = store;
            applyStore(openStore(storageDirectory));
            previousStore.close();
        }

        @Override
        public void validateStorage() {
            try (SqliteStore validationStore = SqliteStore.openExisting(databaseFile(storageDirectory), SqliteDependencyMode.ANY_VISIBLE)) {
                PermissionServices.sqlite(validationStore);
                SubjectMetadataServices.sqlite(validationStore);
                GroupServices.sqlite(validationStore);
                PermissionNodeRegistries.sqlite(validationStore);
            }
        }

        @Override
        public StorageBackupService storageBackupService() {
            return StorageBackupService.forDatabase(storageDirectory.resolve("backups"), databaseFile(storageDirectory), store, 10);
        }

        @Override
        public void refreshRuntimePermissions() {
            runtimeRefreshes++;
        }

        private int runtimeRefreshes() {
            return runtimeRefreshes;
        }

        private void refreshRuntimeSubject(UUID subjectId) {
            subjectRuntimeRefreshes++;
        }

        private int subjectRuntimeRefreshes() {
            return subjectRuntimeRefreshes;
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
        return observingGroupService(groupService, permissionResolver, subjectId -> {
        }, () -> {
        });
    }

    private static GroupService observingGroupService(GroupService groupService, PermissionResolver permissionResolver, java.util.function.Consumer<UUID> subjectRefresher,
            Runnable fullRefresher) {
        return GroupServices.observing(groupService, new GroupChangeListener() {

            @Override
            public void subjectGroupsChanged(UUID subjectId) {
                permissionResolver.invalidateSubject(subjectId);
                subjectRefresher.accept(subjectId);
            }

            @Override
            public void groupsChanged() {
                permissionResolver.invalidateAll();
                fullRefresher.run();
            }
        });
    }
}
