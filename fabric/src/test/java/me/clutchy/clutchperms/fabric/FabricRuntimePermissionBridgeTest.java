package me.clutchy.clutchperms.fabric;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Statement;
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
        TestEnvironment environment = new TestEnvironment(temporaryDirectory);
        PermissionResolver persistedPermissionResolver = environment.permissionResolver();
        CommandDispatcher<TestSource> dispatcher = dispatcher(environment);
        TestSource console = TestSource.console();

        assertEquals(1, dispatcher.execute("clutchperms user " + SUBJECT_ID + " set example.smoke true", console));
        assertEquals(PermissionValue.TRUE, persistedPermissionValue(temporaryDirectory, "example.smoke"));
        assertEquals(TriState.TRUE, FabricRuntimePermissionBridge.resolve(persistedPermissionResolver, SUBJECT_ID, "example.smoke"));

        assertEquals(1, dispatcher.execute("clutchperms user " + SUBJECT_ID + " set example.smoke false", console));
        assertEquals(PermissionValue.FALSE, persistedPermissionValue(temporaryDirectory, "example.smoke"));
        assertEquals(TriState.FALSE, FabricRuntimePermissionBridge.resolve(persistedPermissionResolver, SUBJECT_ID, "example.smoke"));

        assertEquals(1, dispatcher.execute("clutchperms user " + SUBJECT_ID + " clear example.smoke", console));
        assertEquals(PermissionValue.UNSET, persistedPermissionValue(temporaryDirectory, "example.smoke"));
        assertEquals(TriState.DEFAULT, FabricRuntimePermissionBridge.resolve(persistedPermissionResolver, SUBJECT_ID, "example.smoke"));

        assertEquals(1, dispatcher.execute("clutchperms group admin create", console));
        assertEquals(1, dispatcher.execute("clutchperms group admin set example.group true", console));
        assertEquals(1, dispatcher.execute("clutchperms user " + SUBJECT_ID + " group add admin", console));
        assertEquals(PermissionValue.TRUE, persistedGroupPermissionValue(temporaryDirectory, "admin", "example.group"));
        assertEquals(TriState.TRUE, FabricRuntimePermissionBridge.resolve(persistedPermissionResolver, SUBJECT_ID, "example.group"));

        assertEquals(1, dispatcher.execute("clutchperms group base create", console));
        assertEquals(1, dispatcher.execute("clutchperms group base set example.inherited true", console));
        assertEquals(1, dispatcher.execute("clutchperms group admin parent add base", console));
        assertEquals(PermissionValue.TRUE, persistedGroupPermissionValue(temporaryDirectory, "base", "example.inherited"));
        assertEquals(TriState.TRUE, FabricRuntimePermissionBridge.resolve(persistedPermissionResolver, SUBJECT_ID, "example.inherited"));

        assertEquals(1, dispatcher.execute("clutchperms user " + SUBJECT_ID + " set wildcard.* false", console));
        assertEquals(PermissionValue.FALSE, persistedPermissionValue(temporaryDirectory, "wildcard.*"));
        assertEquals(TriState.FALSE, FabricRuntimePermissionBridge.resolve(persistedPermissionResolver, SUBJECT_ID, "wildcard.node"));
    }

    @Test
    void backupRestoreReloadsBridgeVisibleState(@TempDir Path temporaryDirectory) throws CommandSyntaxException {
        TestEnvironment environment = new TestEnvironment(temporaryDirectory);
        CommandDispatcher<TestSource> dispatcher = dispatcher(environment);
        TestSource console = TestSource.console();

        assertEquals(1, dispatcher.execute("clutchperms user " + SUBJECT_ID + " set example.backup true", console));
        assertEquals(1, dispatcher.execute("clutchperms backup create", console));
        assertEquals(1, dispatcher.execute("clutchperms user " + SUBJECT_ID + " set example.backup false", console));
        assertEquals(TriState.FALSE, FabricRuntimePermissionBridge.resolve(environment.permissionResolver(), SUBJECT_ID, "example.backup"));
        StorageBackup backup = environment.storageBackupService().listBackups(StorageFileKind.DATABASE).getFirst();
        assertEquals(PermissionValue.TRUE, persistedPermissionValueFromDatabase(backup.path(), "example.backup"));

        assertEquals(1, dispatcher.execute("clutchperms backup restore " + backup.fileName(), console));

        assertEquals(PermissionValue.TRUE, persistedPermissionValue(temporaryDirectory, "example.backup"));
        assertEquals(TriState.TRUE, FabricRuntimePermissionBridge.resolve(environment.permissionResolver(), SUBJECT_ID, "example.backup"));
        assertEquals(1, environment.runtimeRefreshes());
    }

    @Test
    void malformedBackupRestoreFailsValidationAndPreservesBridgeState(@TempDir Path temporaryDirectory) throws Exception {
        TestEnvironment environment = new TestEnvironment(temporaryDirectory);
        environment.permissionService().setPermission(SUBJECT_ID, "Example.Backup", PermissionValue.TRUE);
        CommandDispatcher<TestSource> dispatcher = dispatcher(environment);
        TestSource console = TestSource.console();
        PermissionValue liveBeforeRestore = persistedPermissionValue(temporaryDirectory, "example.backup");
        Path backupDirectory = temporaryDirectory.resolve("backups").resolve("database");
        Files.createDirectories(backupDirectory);
        String backupFileName = "database-20260424-120000000.db";
        Files.writeString(backupDirectory.resolve(backupFileName), "not sqlite");

        assertCommandFails(dispatcher, "clutchperms backup restore " + backupFileName, console, "Backup operation failed: Failed to validate database backup " + backupFileName);

        assertEquals(liveBeforeRestore, persistedPermissionValue(temporaryDirectory, "example.backup"));
        assertEquals(TriState.TRUE, FabricRuntimePermissionBridge.resolve(environment.permissionResolver(), SUBJECT_ID, "example.backup"));
        assertEquals(0, environment.runtimeRefreshes());
    }

    @Test
    void reloadCommandReloadsStorageForBridgeResolution(@TempDir Path temporaryDirectory) throws CommandSyntaxException {
        TestEnvironment environment = new TestEnvironment(temporaryDirectory);
        CommandDispatcher<TestSource> dispatcher = dispatcher(environment);
        TestSource console = TestSource.console();

        try (SqliteStore store = openStore(temporaryDirectory)) {
            PermissionServices.sqlite(store).setPermission(SUBJECT_ID, "Example.Reload", PermissionValue.TRUE);
        }
        assertEquals(TriState.DEFAULT, FabricRuntimePermissionBridge.resolve(environment.permissionResolver(), SUBJECT_ID, "example.reload"));

        assertEquals(1, dispatcher.execute("clutchperms reload", console));

        assertEquals(TriState.TRUE, FabricRuntimePermissionBridge.resolve(environment.permissionResolver(), SUBJECT_ID, "example.reload"));
        assertEquals(1, environment.runtimeRefreshes());
        assertEquals(List.of("Reloaded config and database storage from disk."), console.messages());
    }

    @Test
    void validateCommandChecksStorageWithoutReplacingBridgeState(@TempDir Path temporaryDirectory) throws CommandSyntaxException {
        TestEnvironment environment = new TestEnvironment(temporaryDirectory);
        environment.permissionService().setPermission(SUBJECT_ID, "Example.Validate", PermissionValue.FALSE);
        CommandDispatcher<TestSource> dispatcher = dispatcher(environment);
        TestSource console = TestSource.console();

        try (SqliteStore store = openStore(temporaryDirectory)) {
            PermissionServices.sqlite(store).setPermission(SUBJECT_ID, "Example.Validate", PermissionValue.TRUE);
        }

        assertEquals(1, dispatcher.execute("clutchperms validate", console));

        assertEquals(TriState.FALSE, FabricRuntimePermissionBridge.resolve(environment.permissionResolver(), SUBJECT_ID, "example.validate"));
        assertEquals(0, environment.runtimeRefreshes());
        assertEquals(List.of("Validated config and database storage from disk."), console.messages());
    }

    @Test
    void malformedPermissionsFileFailsValidateWithoutReplacingBridgeState(@TempDir Path temporaryDirectory) throws Exception {
        TestEnvironment environment = new TestEnvironment(temporaryDirectory);
        environment.permissionService().setPermission(SUBJECT_ID, "Example.Validate", PermissionValue.FALSE);
        CommandDispatcher<TestSource> dispatcher = dispatcher(environment);
        TestSource console = TestSource.console();

        insertInvalidPermissionRow(temporaryDirectory);

        assertCommandFails(dispatcher, "clutchperms validate", console, "Failed to validate ClutchPerms storage:");

        assertEquals(TriState.FALSE, FabricRuntimePermissionBridge.resolve(environment.permissionResolver(), SUBJECT_ID, "example.validate"));
        assertEquals(0, environment.runtimeRefreshes());
    }

    @Test
    void malformedPermissionsFileFailsReloadWithoutReplacingBridgeState(@TempDir Path temporaryDirectory) throws Exception {
        TestEnvironment environment = new TestEnvironment(temporaryDirectory);
        environment.permissionService().setPermission(SUBJECT_ID, "Example.Reload", PermissionValue.TRUE);
        CommandDispatcher<TestSource> dispatcher = dispatcher(environment);
        TestSource console = TestSource.console();

        insertInvalidPermissionRow(temporaryDirectory);

        assertCommandFails(dispatcher, "clutchperms reload", console, "Failed to reload ClutchPerms storage:");

        assertEquals(TriState.TRUE, FabricRuntimePermissionBridge.resolve(environment.permissionResolver(), SUBJECT_ID, "example.reload"));
        assertEquals(0, environment.runtimeRefreshes());
    }

    @Test
    void malformedGroupsFileFailsReloadWithoutReplacingBridgeState(@TempDir Path temporaryDirectory) throws Exception {
        TestEnvironment environment = new TestEnvironment(temporaryDirectory);
        environment.groupService().createGroup("staff");
        environment.groupService().setGroupPermission("staff", "Example.GroupReload", PermissionValue.TRUE);
        environment.groupService().addSubjectGroup(SUBJECT_ID, "staff");
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

        assertEquals(TriState.TRUE, FabricRuntimePermissionBridge.resolve(environment.permissionResolver(), SUBJECT_ID, "example.groupreload"));
        assertEquals(0, environment.runtimeRefreshes());
    }

    @Test
    void commandMutationPersistsAndReloadsKnownNodes(@TempDir Path temporaryDirectory) throws CommandSyntaxException {
        TestEnvironment environment = new TestEnvironment(temporaryDirectory);
        CommandDispatcher<TestSource> dispatcher = dispatcher(environment);
        TestSource console = TestSource.console();

        assertEquals(1, dispatcher.execute("clutchperms nodes add example.fabric Fabric node", console));
        assertTrue(persistedKnownNode(temporaryDirectory, "example.fabric"));
        assertEquals(1, environment.runtimeRefreshes());

        try (SqliteStore store = openStore(temporaryDirectory)) {
            PermissionNodeRegistries.sqlite(store).addNode("manual.reload", "Reloaded node");
        }
        assertTrue(environment.permissionNodeRegistry().getKnownNode("manual.reload").isEmpty());

        assertEquals(1, dispatcher.execute("clutchperms reload", console));

        assertTrue(environment.permissionNodeRegistry().getKnownNode("manual.reload").isPresent());
    }

    @Test
    void malformedNodesFileFailsReloadWithoutReplacingBridgeState(@TempDir Path temporaryDirectory) throws Exception {
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
                    statement.executeUpdate("INSERT INTO subject_permissions (subject_id, node, value) VALUES ('00000000-0000-0000-0000-000000000001', 'example.*.bad', 'TRUE')");
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
            this.permissionService = PermissionServices.observing(storagePermissionService, this.permissionResolver::invalidateSubject);
            this.groupService = observingGroupService(storageGroupService, this.permissionResolver);
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
            return new CommandStatusDiagnostics(databaseFile(storageDirectory).toString(), "test fabric bridge", storageDirectory.resolve("config.json").toString());
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
