package me.clutchy.clutchperms.common.command;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.UnaryOperator;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.io.TempDir;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestion;
import com.mojang.brigadier.tree.CommandNode;

import me.clutchy.clutchperms.common.audit.AuditLogRecord;
import me.clutchy.clutchperms.common.audit.AuditLogService;
import me.clutchy.clutchperms.common.audit.AuditLogServices;
import me.clutchy.clutchperms.common.config.ClutchPermsConfig;
import me.clutchy.clutchperms.common.group.GroupChangeListener;
import me.clutchy.clutchperms.common.group.GroupService;
import me.clutchy.clutchperms.common.group.GroupServices;
import me.clutchy.clutchperms.common.group.InMemoryGroupService;
import me.clutchy.clutchperms.common.node.MutablePermissionNodeRegistry;
import me.clutchy.clutchperms.common.node.PermissionNodeRegistries;
import me.clutchy.clutchperms.common.node.PermissionNodeRegistry;
import me.clutchy.clutchperms.common.node.PermissionNodeSource;
import me.clutchy.clutchperms.common.permission.InMemoryPermissionService;
import me.clutchy.clutchperms.common.permission.PermissionNodes;
import me.clutchy.clutchperms.common.permission.PermissionResolver;
import me.clutchy.clutchperms.common.permission.PermissionService;
import me.clutchy.clutchperms.common.permission.PermissionServices;
import me.clutchy.clutchperms.common.permission.PermissionValue;
import me.clutchy.clutchperms.common.runtime.ScheduledBackupStatus;
import me.clutchy.clutchperms.common.storage.SqliteDependencyMode;
import me.clutchy.clutchperms.common.storage.SqliteStore;
import me.clutchy.clutchperms.common.storage.StorageBackupService;
import me.clutchy.clutchperms.common.storage.StorageFileKind;
import me.clutchy.clutchperms.common.subject.InMemorySubjectMetadataService;
import me.clutchy.clutchperms.common.subject.SubjectMetadataService;
import me.clutchy.clutchperms.common.track.TrackService;
import me.clutchy.clutchperms.common.track.TrackServices;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies shared Brigadier command behavior independent of a server platform.
 */
abstract class CommandTestBase {

    protected static final UUID ADMIN_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    protected static final UUID TARGET_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");

    protected static final UUID UUID_NAMED_PLAYER_ID = UUID.fromString("00000000-0000-0000-0000-000000000003");

    protected static final UUID SECOND_TARGET_ID = UUID.fromString("00000000-0000-0000-0000-000000000004");

    protected static final Instant FIRST_SEEN = Instant.parse("2026-04-24T12:00:00Z");

    protected static final Instant SECOND_SEEN = Instant.parse("2026-04-24T13:00:00Z");

    protected static final CommandStatusDiagnostics STATUS_DIAGNOSTICS = new CommandStatusDiagnostics("/tmp/clutchperms/database.db", "test bridge active",
            "/tmp/clutchperms/config.json");

    protected PermissionService permissionService;

    protected SubjectMetadataService subjectMetadataService;

    protected GroupService groupService;

    protected TrackService trackService;

    protected MutablePermissionNodeRegistry manualPermissionNodeRegistry;

    protected PermissionResolver permissionResolver;

    protected TestEnvironment environment;

    protected CommandDispatcher<TestSource> dispatcher;

    protected SqliteStore backupStore;

    @TempDir
    protected Path temporaryDirectory;

    /**
     * Creates a fresh command dispatcher and permission service for each test case.
     */
    @BeforeEach
    void setUp() {
        ClutchPermsCommands.resetDestructiveConfirmationsForTests();
        PermissionService storagePermissionService = new InMemoryPermissionService();
        subjectMetadataService = new InMemorySubjectMetadataService();
        GroupService storageGroupService = new InMemoryGroupService();
        trackService = TrackServices.inMemory(storageGroupService);
        manualPermissionNodeRegistry = PermissionNodeRegistries.inMemory();
        permissionResolver = new PermissionResolver(storagePermissionService, storageGroupService);
        permissionService = PermissionServices.observing(storagePermissionService, permissionResolver::invalidateSubject);
        groupService = GroupServices.observing(storageGroupService, new GroupChangeListener() {

            @Override
            public void subjectGroupsChanged(UUID subjectId) {
                permissionResolver.invalidateSubject(subjectId);
            }

            @Override
            public void groupsChanged() {
                permissionResolver.invalidateAll();
            }

            @Override
            public void groupDeleted(String groupName) {
                if (trackService instanceof GroupChangeListener listener) {
                    listener.groupDeleted(groupName);
                }
            }

            @Override
            public void groupRenamed(String groupName, String newGroupName) {
                if (trackService instanceof GroupChangeListener listener) {
                    listener.groupRenamed(groupName, newGroupName);
                }
            }
        });
        environment = new TestEnvironment(permissionService, subjectMetadataService, groupService, trackService, manualPermissionNodeRegistry, permissionResolver);
        backupStore = SqliteStore.open(temporaryDirectory.resolve("database.db"), SqliteDependencyMode.ANY_VISIBLE);
        environment.setStorageBackupService(StorageBackupService.forDatabase(temporaryDirectory.resolve("backups"), backupStore.databaseFile(), backupStore,
                ClutchPermsConfig.defaults().backups().retentionLimit()));
        environment.addOnlineSubject("Target", TARGET_ID);
        dispatcher = new CommandDispatcher<>();
        ClutchPermsCommands.ROOT_LITERALS.forEach(rootLiteral -> dispatcher.getRoot().addChild(ClutchPermsCommands.create(environment, rootLiteral)));
    }

    @AfterEach
    void tearDown() {
        ClutchPermsCommands.resetDestructiveConfirmationsForTests();
        if (backupStore != null) {
            backupStore.close();
        }
    }

    protected List<String> suggestionTexts(String command) {
        return suggestionTexts(command, TestSource.console());
    }

    protected List<String> suggestionTexts(String command, TestSource source) {
        return dispatcher.getCompletionSuggestions(dispatcher.parse(command, source)).join().getList().stream().map(Suggestion::getText).toList();
    }

    protected CommandNode<TestSource> clutchPermsNode() {
        return dispatcher.getRoot().getChild(ClutchPermsCommands.ROOT_LITERAL);
    }

    protected CommandNode<TestSource> userTargetNode() {
        return clutchPermsNode().getChild("user").getChild(CommandArguments.TARGET);
    }

    protected CommandNode<TestSource> groupTargetNode() {
        return clutchPermsNode().getChild("group").getChild(CommandArguments.GROUP);
    }

    protected static List<String> visibleChildNames(CommandNode<TestSource> node, TestSource source) {
        return node.getChildren().stream().filter(child -> child.canUse(source)).map(CommandNode::getName).sorted().toList();
    }

    protected void assertCommandFails(String command, TestSource source, String expectedMessage) {
        int firstNewMessage = source.messages().size();
        try {
            assertEquals(0, dispatcher.execute(command, source));
        } catch (CommandSyntaxException exception) {
            throw new AssertionError("Expected styled command failure for " + command, exception);
        }
        assertMessageContains(source, firstNewMessage, expectedMessage);
    }

    protected void assertCommandUnavailable(String command, TestSource source) {
        int firstNewMessage = source.messages().size();

        assertThrows(CommandSyntaxException.class, () -> dispatcher.execute(command, source));
        assertEquals(firstNewMessage, source.messages().size());
    }

    protected static void assertMessageContains(TestSource source, String expectedMessage) {
        assertMessageContains(source, 0, expectedMessage);
    }

    protected static void assertMessageContains(TestSource source, int firstMessageIndex, String expectedMessage) {
        List<String> messages = source.messages();
        assertTrue(messages.size() > firstMessageIndex, "Expected command feedback");
        assertTrue(messages.subList(firstMessageIndex, messages.size()).stream().anyMatch(message -> message.contains(expectedMessage)),
                () -> "Expected message to contain <" + expectedMessage + "> but was " + messages.subList(firstMessageIndex, messages.size()));
    }

    protected static void assertSuggests(CommandMessage message, String command) {
        assertTrue(
                message.segments().stream().anyMatch(
                        segment -> segment.click() != null && segment.click().action() == CommandMessage.ClickAction.SUGGEST_COMMAND && segment.click().value().equals(command)),
                () -> "Expected suggest click for " + command + " in " + message);
    }

    protected static void assertRuns(CommandMessage message, String command) {
        assertTrue(
                message.segments().stream().anyMatch(
                        segment -> segment.click() != null && segment.click().action() == CommandMessage.ClickAction.RUN_COMMAND && segment.click().value().equals(command)),
                () -> "Expected run click for " + command + " in " + message);
    }

    protected void writeBackup(String fileName, String permissionNode) throws IOException {
        Path backupDirectory = temporaryDirectory.resolve("backups").resolve(StorageFileKind.DATABASE.token());
        Files.createDirectories(backupDirectory);
        try (SqliteStore store = SqliteStore.open(backupDirectory.resolve(fileName), SqliteDependencyMode.ANY_VISIBLE)) {
            PermissionServices.sqlite(store).setPermission(TARGET_ID, permissionNode, PermissionValue.TRUE);
        }
    }

    protected void writeRawBackup(String fileName, String content) throws IOException {
        Path backupDirectory = temporaryDirectory.resolve("backups").resolve(StorageFileKind.DATABASE.token());
        Files.createDirectories(backupDirectory);
        Files.writeString(backupDirectory.resolve(fileName), content);
    }

    protected PermissionValue permissionFromDatabase(String node) {
        try (SqliteStore store = SqliteStore.open(temporaryDirectory.resolve("database.db"), SqliteDependencyMode.ANY_VISIBLE)) {
            return PermissionServices.sqlite(store).getPermission(TARGET_ID, node);
        }
    }

    protected static AuditLogRecord auditRecord(Instant timestamp, String action) {
        return new AuditLogRecord(timestamp, CommandSourceKind.CONSOLE, Optional.empty(), Optional.of("console"), action, "user-permissions", TARGET_ID.toString(), "Target",
                "{\"permissions\":{}}", "{\"permissions\":{\"example.node\":\"TRUE\"}}", "/clutchperms user Target set example.node true", true);
    }

    protected static List<String> statusMessages(int knownSubjects) {
        return List.of(ClutchPermsCommands.STATUS_MESSAGE, "Database file: " + STATUS_DIAGNOSTICS.databaseFile(), "Config file: " + STATUS_DIAGNOSTICS.configFile(),
                "Backup retention: newest 10 database backups.", "Enabled: false; timer running: false.", "Audit retention: enabled, max age 90 days, max entries none.",
                "Command page sizes: help 7, lists 8.", "Chat formatting: enabled.", "Known subjects: " + knownSubjects, "Known groups: 2", "Known tracks: 0",
                "Known permission nodes: " + PermissionNodes.commandNodes().size(), "Resolver cache: 0 subjects, 0 node results, 0 effective snapshots.",
                "Runtime bridge: " + STATUS_DIAGNOSTICS.runtimeBridgeStatus());
    }

    protected static List<String> commandListPageOneMessages() {
        return commandListPageOneMessages("clutchperms");
    }

    protected static List<String> commandListPageOneMessages(String rootLiteral) {
        return List.of("ClutchPerms commands (page 1/10):", "/" + rootLiteral + " help [page]", "/" + rootLiteral + " status", "/" + rootLiteral + " reload",
                "/" + rootLiteral + " validate", "/" + rootLiteral + " history [page]", "/" + rootLiteral + " history prune days <days>",
                "/" + rootLiteral + " history prune count <count>", "Page 1/10 | Next >");
    }

    protected static List<String> commandListPageTwoMessages() {
        return commandListPageTwoMessages("clutchperms");
    }

    protected static List<String> commandListPageTwoMessages(String rootLiteral) {
        return List.of("ClutchPerms commands (page 2/10):", "/" + rootLiteral + " undo <id>", "/" + rootLiteral + " config list", "/" + rootLiteral + " config get <key>",
                "/" + rootLiteral + " config set <key> <value>", "/" + rootLiteral + " config reset <key|all>", "/" + rootLiteral + " backup create",
                "/" + rootLiteral + " backup list [page]", "< Prev | Page 2/10 | Next >");
    }

    protected static List<String> groupRootUsageMessages() {
        return List.of("Missing group command.", "List groups or choose a group to inspect or mutate.", "Try one:", "  /clutchperms group list",
                "  /clutchperms group <group> <create|delete|info|list|members|parents>", "  /clutchperms group <group> <get|clear> <node>",
                "  /clutchperms group <group> set <node> <true|false>", "  /clutchperms group <group> clear-all", "  /clutchperms group <group> rename <new-group>",
                "  /clutchperms group <group> parent <add|remove> <parent>", "  /clutchperms group <group> <prefix|suffix> get|set|clear");
    }

    protected static List<String> groupTargetUsageMessages(String group) {
        return List.of("Missing group command.", "Choose what to do with group " + group + ".", "Try one:",
                "  /clutchperms group " + group + " <create|delete|info|list|members|parents>", "  /clutchperms group " + group + " <get|clear> <node>",
                "  /clutchperms group " + group + " set <node> <true|false>", "  /clutchperms group " + group + " clear-all",
                "  /clutchperms group " + group + " rename <new-group>", "  /clutchperms group " + group + " parent <add|remove> <parent>",
                "  /clutchperms group " + group + " <prefix|suffix> get|set|clear");
    }

    protected static List<String> userRootUsageMessages() {
        return List.of("Missing user target.", "Provide an exact online name, resolvable offline name, stored last-known name, or UUID.", "Try one:",
                "  /clutchperms user <target> <info|list|groups>", "  /clutchperms user <target> <get|clear|check|explain> <node>",
                "  /clutchperms user <target> set <node> <true|false>", "  /clutchperms user <target> clear-all", "  /clutchperms user <target> group <add|remove> <group>",
                "  /clutchperms user <target> tracks", "  /clutchperms user <target> track <promote|demote> <track>", "  /clutchperms user <target> <prefix|suffix> get|set|clear");
    }

    protected static List<String> nodesUsageMessages() {
        return List.of("Missing nodes command.", "List, search, add, or remove known permission nodes.", "Try one:", "  /clutchperms nodes list",
                "  /clutchperms nodes search <query>", "  /clutchperms nodes add <node> [description]", "  /clutchperms nodes remove <node>");
    }

    protected static final class FailingMutationPermissionService implements PermissionService {

        protected final RuntimeException failure;

        protected FailingMutationPermissionService(RuntimeException failure) {
            this.failure = failure;
        }

        @Override
        public PermissionValue getPermission(UUID subjectId, String node) {
            return PermissionValue.TRUE;
        }

        @Override
        public Map<String, PermissionValue> getPermissions(UUID subjectId) {
            return Map.of("example.node", PermissionValue.TRUE);
        }

        @Override
        public void setPermission(UUID subjectId, String node, PermissionValue value) {
            throw failure;
        }

        @Override
        public void clearPermission(UUID subjectId, String node) {
            throw failure;
        }

        @Override
        public int clearPermissions(UUID subjectId) {
            throw failure;
        }
    }

    protected static final class TestEnvironment implements ClutchPermsCommandEnvironment<TestSource> {

        protected final PermissionService permissionService;

        protected final SubjectMetadataService subjectMetadataService;

        protected final GroupService groupService;

        protected final TrackService trackService;

        protected final MutablePermissionNodeRegistry manualPermissionNodeRegistry;

        protected final PermissionResolver permissionResolver;

        protected final AuditLogService auditLogService = AuditLogServices.inMemory();

        protected final Map<String, CommandSubject> onlineSubjects = new LinkedHashMap<>();

        protected final Map<String, CommandSubject> cachedSubjects = new LinkedHashMap<>();

        protected final Map<String, CompletableFuture<Optional<CommandSubject>>> asyncSubjects = new LinkedHashMap<>();

        protected final List<String> platformNodes = new ArrayList<>();

        protected StorageBackupService storageBackupService;

        protected ScheduledBackupStatus scheduledBackupStatus;

        protected ClutchPermsConfig config = ClutchPermsConfig.defaults();

        protected int reloads;

        protected int validations;

        protected int runtimeRefreshes;

        protected int configUpdates;

        protected RuntimeException reloadFailure;

        protected RuntimeException validationFailure;

        protected RuntimeException configUpdateFailure;

        protected TestEnvironment(PermissionService permissionService, SubjectMetadataService subjectMetadataService, GroupService groupService, TrackService trackService,
                MutablePermissionNodeRegistry manualPermissionNodeRegistry, PermissionResolver permissionResolver) {
            this.permissionService = permissionService;
            this.subjectMetadataService = subjectMetadataService;
            this.groupService = groupService;
            this.trackService = trackService;
            this.manualPermissionNodeRegistry = PermissionNodeRegistries.observing(manualPermissionNodeRegistry, this::refreshRuntimePermissions);
            this.permissionResolver = permissionResolver;
        }

        protected void addOnlineSubject(String name, UUID subjectId) {
            onlineSubjects.put(name, new CommandSubject(subjectId, name));
        }

        protected void addCachedSubject(String name, UUID subjectId) {
            cachedSubjects.put(name, new CommandSubject(subjectId, name));
        }

        protected CompletableFuture<Optional<CommandSubject>> addAsyncSubjectLookup(String target) {
            CompletableFuture<Optional<CommandSubject>> future = new CompletableFuture<>();
            asyncSubjects.put(target, future);
            return future;
        }

        protected void addPlatformNode(String node) {
            platformNodes.add(node);
        }

        protected void failReload(RuntimeException reloadFailure) {
            this.reloadFailure = reloadFailure;
        }

        protected void failValidation(RuntimeException validationFailure) {
            this.validationFailure = validationFailure;
        }

        protected void failConfigUpdate(RuntimeException configUpdateFailure) {
            this.configUpdateFailure = configUpdateFailure;
        }

        protected void setStorageBackupService(StorageBackupService storageBackupService) {
            this.storageBackupService = storageBackupService;
        }

        protected void setScheduledBackupStatus(ScheduledBackupStatus scheduledBackupStatus) {
            this.scheduledBackupStatus = scheduledBackupStatus;
        }

        protected void setConfig(ClutchPermsConfig config) {
            this.config = config;
        }

        protected int reloads() {
            return reloads;
        }

        protected int validations() {
            return validations;
        }

        protected int runtimeRefreshes() {
            return runtimeRefreshes;
        }

        protected int configUpdates() {
            return configUpdates;
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
        public TrackService trackService() {
            return trackService;
        }

        @Override
        public PermissionNodeRegistry permissionNodeRegistry() {
            return PermissionNodeRegistries.composite(PermissionNodeRegistries.builtIn(), manualPermissionNodeRegistry,
                    PermissionNodeRegistries.staticNodes(PermissionNodeSource.PLATFORM, platformNodes));
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
            return STATUS_DIAGNOSTICS;
        }

        @Override
        public ClutchPermsConfig config() {
            return config;
        }

        @Override
        public AuditLogService auditLogService() {
            return auditLogService;
        }

        @Override
        public void updateConfig(UnaryOperator<ClutchPermsConfig> updater) {
            if (configUpdateFailure != null) {
                throw configUpdateFailure;
            }
            config = updater.apply(config);
            configUpdates++;
        }

        @Override
        public void reloadStorage() {
            if (reloadFailure != null) {
                throw reloadFailure;
            }
            reloads++;
        }

        @Override
        public void validateStorage() {
            if (validationFailure != null) {
                throw validationFailure;
            }
            validations++;
        }

        @Override
        public StorageBackupService storageBackupService() {
            return storageBackupService;
        }

        @Override
        public ScheduledBackupStatus scheduledBackupStatus() {
            if (scheduledBackupStatus != null) {
                return scheduledBackupStatus;
            }
            return ClutchPermsCommandEnvironment.super.scheduledBackupStatus();
        }

        @Override
        public void refreshRuntimePermissions() {
            runtimeRefreshes++;
        }

        @Override
        public CommandSourceKind sourceKind(TestSource source) {
            return source.kind();
        }

        @Override
        public Optional<UUID> sourceSubjectId(TestSource source) {
            return Optional.ofNullable(source.subjectId());
        }

        @Override
        public Optional<CommandSubject> findOnlineSubject(TestSource source, String target) {
            return Optional.ofNullable(onlineSubjects.get(target));
        }

        @Override
        public Optional<CommandSubject> findCachedSubject(TestSource source, String target) {
            return Optional.ofNullable(cachedSubjects.get(target));
        }

        @Override
        public CompletableFuture<Optional<CommandSubject>> resolveSubjectAsync(TestSource source, String target) {
            return asyncSubjects.getOrDefault(target, CompletableFuture.completedFuture(Optional.empty()));
        }

        @Override
        public void executeOnCommandThread(TestSource source, Runnable task) {
            task.run();
        }

        @Override
        public Collection<String> onlineSubjectNames(TestSource source) {
            return onlineSubjects.keySet();
        }

        @Override
        public void sendMessage(TestSource source, String message) {
            source.messages().add(message);
            source.commandMessages().add(CommandMessage.plain(message));
        }

        @Override
        public void sendMessage(TestSource source, CommandMessage message) {
            source.messages().add(message.plainText());
            source.commandMessages().add(message);
        }
    }

    protected record TestSource(CommandSourceKind kind, UUID subjectId, List<String> messages, List<CommandMessage> commandMessages) {

        protected static TestSource console() {
            return new TestSource(CommandSourceKind.CONSOLE, null, new ArrayList<>(), new ArrayList<>());
        }

        protected static TestSource player(UUID subjectId) {
            return new TestSource(CommandSourceKind.PLAYER, subjectId, new ArrayList<>(), new ArrayList<>());
        }
    }

    protected static final class MutableClock extends Clock {

        protected Instant instant;

        protected MutableClock(Instant instant) {
            this.instant = instant;
        }

        protected void advance(Duration duration) {
            instant = instant.plus(duration);
        }

        @Override
        public java.time.ZoneId getZone() {
            return java.time.ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
