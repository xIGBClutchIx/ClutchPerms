package me.clutchy.clutchperms.paper;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerQuitEvent.QuitReason;
import org.bukkit.event.server.PluginEnableEvent;
import org.bukkit.permissions.Permission;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import io.papermc.paper.command.brigadier.CommandSourceStack;

import me.clutchy.clutchperms.common.group.GroupService;
import me.clutchy.clutchperms.common.group.GroupServices;
import me.clutchy.clutchperms.common.node.MutablePermissionNodeRegistry;
import me.clutchy.clutchperms.common.node.PermissionNodeRegistries;
import me.clutchy.clutchperms.common.node.PermissionNodeRegistry;
import me.clutchy.clutchperms.common.permission.PermissionNodes;
import me.clutchy.clutchperms.common.permission.PermissionResolver;
import me.clutchy.clutchperms.common.permission.PermissionResolverCacheStats;
import me.clutchy.clutchperms.common.permission.PermissionService;
import me.clutchy.clutchperms.common.permission.PermissionServices;
import me.clutchy.clutchperms.common.permission.PermissionValue;
import me.clutchy.clutchperms.common.storage.StorageBackup;
import me.clutchy.clutchperms.common.storage.StorageFileKind;
import me.clutchy.clutchperms.common.subject.SubjectMetadata;
import me.clutchy.clutchperms.common.subject.SubjectMetadataService;
import me.clutchy.clutchperms.common.subject.SubjectMetadataServices;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.kyori.adventure.text.Component;

/**
 * Integration-style tests that verify the Paper plugin bootstrap against MockBukkit.
 */
class ClutchPermsPaperPluginTest {

    /**
     * Mock server used to host the plugin under test.
     */
    private ServerMock server;

    /**
     * Loaded plugin instance under test.
     */
    private ClutchPermsPaperPlugin plugin;

    /**
     * Starts a fresh MockBukkit server and loads the plugin before each test.
     */
    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.load(ClutchPermsPaperPlugin.class);
    }

    /**
     * Tears down the MockBukkit server after each test to prevent leaked global state.
     */
    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    /**
     * Confirms that the plugin enables successfully during bootstrap.
     */
    @Test
    void pluginLoadsAndEnablesCleanly() {
        assertTrue(plugin.isEnabled());
    }

    /**
     * Confirms fresh Paper startup creates visible empty storage files.
     */
    @Test
    void startupMaterializesStorageFiles() {
        Path dataFolder = plugin.getDataFolder().toPath();

        assertTrue(Files.exists(dataFolder.resolve("permissions.json")));
        assertTrue(Files.exists(dataFolder.resolve("subjects.json")));
        assertTrue(Files.exists(dataFolder.resolve("groups.json")));
        assertTrue(Files.exists(dataFolder.resolve("nodes.json")));
        assertFalse(Files.exists(dataFolder.resolve("backups")));
    }

    /**
     * Confirms MockBukkit uses the intentional permission manager fallback path.
     */
    @Test
    void permissionManagerOverrideFallsBackWhenUnsupported() {
        assertFalse(plugin.isPaperPermissionManagerOverrideActive());
        assertTrue(plugin.getStatusDiagnostics().runtimeBridgeStatus().contains("permission manager override fallback"));
    }

    /**
     * Confirms that the shared permission service is exposed through Paper's Bukkit-derived service registry.
     */
    @Test
    void permissionServiceIsRegistered() {
        RegisteredServiceProvider<PermissionService> registration = server.getServicesManager().getRegistration(PermissionService.class);

        assertNotNull(registration);
        assertSame(plugin.getPermissionService(), registration.getProvider());
        assertEquals(Map.of(), registration.getProvider().getPermissions(playerId()));
    }

    /**
     * Confirms Paper metadata exposes the forward command permission nodes.
     */
    @Test
    void paperPermissionMetadataExposesCommandPermissionNodes() {
        assertNull(server.getPluginManager().getPermission(PermissionNodes.ADMIN));
        assertNotNull(server.getPluginManager().getPermission(PermissionNodes.ADMIN_ALL));
        assertNotNull(server.getPluginManager().getPermission(PermissionNodes.ADMIN_STATUS));
        assertNotNull(server.getPluginManager().getPermission(PermissionNodes.ADMIN_USER_SET));
    }

    /**
     * Confirms that subject metadata is exposed through Paper's Bukkit-derived service registry.
     */
    @Test
    void subjectMetadataServiceIsRegistered() {
        RegisteredServiceProvider<SubjectMetadataService> registration = server.getServicesManager().getRegistration(SubjectMetadataService.class);

        assertNotNull(registration);
        assertSame(plugin.getSubjectMetadataService(), registration.getProvider());
        assertEquals(Map.of(), registration.getProvider().getSubjects());
    }

    /**
     * Confirms group storage and effective resolution are exposed through Paper's Bukkit-derived service registry.
     */
    @Test
    void groupServicesAreRegistered() {
        RegisteredServiceProvider<GroupService> groupRegistration = server.getServicesManager().getRegistration(GroupService.class);
        RegisteredServiceProvider<PermissionResolver> resolverRegistration = server.getServicesManager().getRegistration(PermissionResolver.class);
        RegisteredServiceProvider<PermissionNodeRegistry> nodeRegistryRegistration = server.getServicesManager().getRegistration(PermissionNodeRegistry.class);
        RegisteredServiceProvider<MutablePermissionNodeRegistry> manualNodeRegistryRegistration = server.getServicesManager().getRegistration(MutablePermissionNodeRegistry.class);

        assertNotNull(groupRegistration);
        assertNotNull(resolverRegistration);
        assertNotNull(nodeRegistryRegistration);
        assertNotNull(manualNodeRegistryRegistration);
        assertSame(plugin.getGroupService(), groupRegistration.getProvider());
        assertSame(plugin.getPermissionResolver(), resolverRegistration.getProvider());
        assertSame(plugin.getPermissionNodeRegistry(), nodeRegistryRegistration.getProvider());
        assertSame(plugin.getManualPermissionNodeRegistry(), manualNodeRegistryRegistration.getProvider());
    }

    /**
     * Confirms player joins record lightweight subject metadata.
     */
    @Test
    void playerJoinRecordsSubjectMetadata() {
        PlayerMock player = server.addPlayer("Target");

        SubjectMetadata metadata = plugin.getSubjectMetadataService().getSubject(player.getUniqueId()).orElseThrow();

        assertEquals(player.getUniqueId(), metadata.subjectId());
        assertEquals("Target", metadata.lastKnownName());
        assertNotNull(metadata.lastSeen());
    }

    /**
     * Confirms that the Paper adapter executes the shared Brigadier status diagnostics.
     *
     * @throws Exception when Brigadier command execution fails unexpectedly
     */
    @Test
    void clutchPermsCommandRespondsWithStatusDiagnostics() throws Exception {
        PlayerMock player = server.addPlayer("Admin");
        plugin.getPermissionService().setPermission(player.getUniqueId(), PermissionNodes.ADMIN_ALL, PermissionValue.TRUE);
        CommandDispatcher<CommandSourceStack> dispatcher = new CommandDispatcher<>();
        dispatcher.getRoot().addChild(PaperClutchPermsCommand.create(plugin));

        assertEquals(1, dispatcher.execute("clutchperms status", new TestCommandSourceStack(player)));
        assertEquals(Component.text(ClutchPermsPaperPlugin.STATUS_MESSAGE), player.nextComponentMessage());
        assertEquals(Component.text("Permissions file: " + plugin.getDataFolder().toPath().resolve("permissions.json").toAbsolutePath().normalize()),
                player.nextComponentMessage());
        assertEquals(Component.text("Subjects file: " + plugin.getDataFolder().toPath().resolve("subjects.json").toAbsolutePath().normalize()), player.nextComponentMessage());
        assertEquals(Component.text("Groups file: " + plugin.getDataFolder().toPath().resolve("groups.json").toAbsolutePath().normalize()), player.nextComponentMessage());
        assertEquals(Component.text("Known nodes file: " + plugin.getDataFolder().toPath().resolve("nodes.json").toAbsolutePath().normalize()), player.nextComponentMessage());
        assertEquals(Component.text("Known subjects: 1"), player.nextComponentMessage());
        assertEquals(Component.text("Known groups: 0"), player.nextComponentMessage());
        assertEquals(Component.text("Known permission nodes: " + plugin.getPermissionNodeRegistry().getKnownNodes().size()), player.nextComponentMessage());
        PermissionResolverCacheStats cacheStats = plugin.getPermissionResolver().cacheStats();
        assertEquals(Component.text("Resolver cache: " + cacheStats.subjects() + " subjects, " + cacheStats.nodeResults() + " node results, " + cacheStats.effectiveSnapshots()
                + " effective snapshots."), player.nextComponentMessage());
        assertEquals(Component.text("Runtime bridge: " + plugin.getStatusDiagnostics().runtimeBridgeStatus()), player.nextComponentMessage());
    }

    /**
     * Confirms the legacy admin namespace root no longer authorizes Paper commands.
     */
    @Test
    void clutchPermsCommandDeniesLegacyAdminRoot() {
        PlayerMock player = server.addPlayer("Admin");
        plugin.getPermissionService().setPermission(player.getUniqueId(), PermissionNodes.ADMIN, PermissionValue.TRUE);
        CommandDispatcher<CommandSourceStack> dispatcher = new CommandDispatcher<>();
        dispatcher.getRoot().addChild(PaperClutchPermsCommand.create(plugin));

        assertThrows(CommandSyntaxException.class, () -> dispatcher.execute("clutchperms status", new TestCommandSourceStack(player)));
    }

    /**
     * Confirms persisted permissions are applied when a matching player joins.
     */
    @Test
    void storedPermissionsApplyWhenMatchingPlayerJoins() {
        UUID targetId = UUID.fromString("00000000-0000-0000-0000-000000000101");
        plugin.getPermissionService().setPermission(targetId, "Example.Join", PermissionValue.TRUE);
        PlayerMock player = new PlayerMock(server, "Target", targetId);

        server.addPlayer(player);

        assertTrue(player.isPermissionSet("example.join"));
        assertTrue(player.hasPermission("example.join"));
    }

    /**
     * Confirms persisted group permissions are applied when a matching player joins.
     */
    @Test
    void storedGroupPermissionsApplyWhenMatchingPlayerJoins() {
        UUID targetId = UUID.fromString("00000000-0000-0000-0000-000000000102");
        plugin.getGroupService().createGroup("staff");
        plugin.getGroupService().setGroupPermission("staff", "Example.GroupJoin", PermissionValue.TRUE);
        plugin.getGroupService().addSubjectGroup(targetId, "staff");
        PlayerMock player = new PlayerMock(server, "Target", targetId);

        server.addPlayer(player);

        assertTrue(player.isPermissionSet("example.groupjoin"));
        assertTrue(player.hasPermission("example.groupjoin"));
    }

    /**
     * Confirms inherited group permissions are applied when a matching player joins.
     */
    @Test
    void storedInheritedGroupPermissionsApplyWhenMatchingPlayerJoins() {
        UUID targetId = UUID.fromString("00000000-0000-0000-0000-000000000103");
        plugin.getGroupService().createGroup("base");
        plugin.getGroupService().setGroupPermission("base", "Example.InheritedJoin", PermissionValue.TRUE);
        plugin.getGroupService().createGroup("staff");
        plugin.getGroupService().addGroupParent("staff", "base");
        plugin.getGroupService().addSubjectGroup(targetId, "staff");
        PlayerMock player = new PlayerMock(server, "Target", targetId);

        server.addPlayer(player);

        assertTrue(player.isPermissionSet("example.inheritedjoin"));
        assertTrue(player.hasPermission("example.inheritedjoin"));
    }

    /**
     * Confirms online players are refreshed immediately after direct service mutations.
     */
    @Test
    void onlinePlayerPermissionUpdatesAfterServiceMutation() {
        PlayerMock player = server.addPlayer("Target");

        plugin.getPermissionService().setPermission(player.getUniqueId(), "Example.Runtime", PermissionValue.TRUE);
        assertTrue(player.isPermissionSet("example.runtime"));
        assertTrue(player.hasPermission("example.runtime"));

        plugin.getPermissionService().setPermission(player.getUniqueId(), "Example.Runtime", PermissionValue.FALSE);
        assertTrue(player.isPermissionSet("example.runtime"));
        assertFalse(player.hasPermission("example.runtime"));

        plugin.getPermissionService().clearPermission(player.getUniqueId(), "Example.Runtime");
        assertFalse(player.isPermissionSet("example.runtime"));
        assertFalse(player.hasPermission("example.runtime"));
    }

    /**
     * Confirms online players are refreshed immediately after group service mutations.
     */
    @Test
    void onlinePlayerPermissionUpdatesAfterGroupMutation() {
        PlayerMock player = server.addPlayer("Target");

        plugin.getGroupService().createGroup("staff");
        plugin.getGroupService().setGroupPermission("staff", "Example.GroupRuntime", PermissionValue.TRUE);
        assertFalse(player.isPermissionSet("example.groupruntime"));

        plugin.getGroupService().addSubjectGroup(player.getUniqueId(), "staff");
        assertTrue(player.isPermissionSet("example.groupruntime"));
        assertTrue(player.hasPermission("example.groupruntime"));

        plugin.getGroupService().setGroupPermission("staff", "Example.GroupRuntime", PermissionValue.FALSE);
        assertTrue(player.isPermissionSet("example.groupruntime"));
        assertFalse(player.hasPermission("example.groupruntime"));

        plugin.getGroupService().clearGroupPermission("staff", "Example.GroupRuntime");
        assertFalse(player.isPermissionSet("example.groupruntime"));

        plugin.getGroupService().createGroup("base");
        plugin.getGroupService().setGroupPermission("base", "Example.InheritedRuntime", PermissionValue.TRUE);
        plugin.getGroupService().addGroupParent("staff", "base");
        assertTrue(player.isPermissionSet("example.inheritedruntime"));
        assertTrue(player.hasPermission("example.inheritedruntime"));

        plugin.getGroupService().setGroupPermission("base", "Example.InheritedRuntime", PermissionValue.FALSE);
        assertTrue(player.isPermissionSet("example.inheritedruntime"));
        assertFalse(player.hasPermission("example.inheritedruntime"));

        plugin.getGroupService().removeGroupParent("staff", "base");
        assertFalse(player.isPermissionSet("example.inheritedruntime"));
    }

    /**
     * Confirms command mutations refresh the target player's Paper permissions.
     *
     * @throws Exception when Brigadier command execution fails unexpectedly
     */
    @Test
    void clutchPermsCommandMutationRefreshesTargetPermissions() throws Exception {
        PlayerMock target = server.addPlayer("Target");
        CommandDispatcher<CommandSourceStack> dispatcher = new CommandDispatcher<>();
        dispatcher.getRoot().addChild(PaperClutchPermsCommand.create(plugin));

        assertEquals(1, dispatcher.execute("clutchperms user Target set example.command true", new TestCommandSourceStack(server.getConsoleSender())));

        assertTrue(target.isPermissionSet("example.command"));
        assertTrue(target.hasPermission("example.command"));
    }

    /**
     * Confirms Paper command authorization can come from an effective group permission.
     *
     * @throws Exception when Brigadier command execution fails unexpectedly
     */
    @Test
    void clutchPermsCommandAllowsGroupAdmin() throws Exception {
        PlayerMock admin = server.addPlayer("Admin");
        PlayerMock target = server.addPlayer("Target");
        plugin.getGroupService().createGroup("admin");
        plugin.getGroupService().createGroup("staff");
        plugin.getGroupService().setGroupPermission("admin", PermissionNodes.ADMIN_ALL, PermissionValue.TRUE);
        plugin.getGroupService().addGroupParent("staff", "admin");
        plugin.getGroupService().addSubjectGroup(admin.getUniqueId(), "staff");
        CommandDispatcher<CommandSourceStack> dispatcher = new CommandDispatcher<>();
        dispatcher.getRoot().addChild(PaperClutchPermsCommand.create(plugin));

        assertEquals(1, dispatcher.execute("clutchperms user Target set example.groupadmin true", new TestCommandSourceStack(admin)));

        assertTrue(target.isPermissionSet("example.groupadmin"));
        assertTrue(target.hasPermission("example.groupadmin"));
    }

    /**
     * Confirms Paper command authorization uses wildcard resolution and wildcard assignments are attached as stored nodes.
     *
     * @throws Exception when Brigadier command execution fails unexpectedly
     */
    @Test
    void wildcardPermissionsAttachAndAuthorizeCommands() throws Exception {
        PlayerMock admin = server.addPlayer("Admin");
        PlayerMock target = server.addPlayer("Target");
        plugin.getPermissionService().setPermission(admin.getUniqueId(), PermissionNodes.ADMIN_ALL, PermissionValue.TRUE);
        plugin.getPermissionService().setPermission(target.getUniqueId(), "example.*", PermissionValue.TRUE);
        CommandDispatcher<CommandSourceStack> dispatcher = new CommandDispatcher<>();
        dispatcher.getRoot().addChild(PaperClutchPermsCommand.create(plugin));

        assertTrue(admin.isPermissionSet(PermissionNodes.ADMIN_ALL));
        assertTrue(target.isPermissionSet("example.*"));
        assertTrue(target.hasPermission("example.*"));

        assertEquals(1, dispatcher.execute("clutchperms user Target set example.wildcardauth true", new TestCommandSourceStack(admin)));

        assertTrue(target.isPermissionSet("example.wildcardauth"));
        assertTrue(target.hasPermission("example.wildcardauth"));
    }

    /**
     * Confirms Paper expands ClutchPerms wildcards onto exact registered Paper permission nodes.
     */
    @Test
    void wildcardPermissionsExpandToRegisteredPaperNodes() {
        PlayerMock target = server.addPlayer("Target");
        server.getPluginManager().addPermission(new Permission("example.registered"));

        plugin.getPermissionService().setPermission(target.getUniqueId(), "example.*", PermissionValue.TRUE);

        assertTrue(target.isPermissionSet("example.*"));
        assertTrue(target.isPermissionSet("example.registered"));
        assertTrue(target.hasPermission("example.registered"));
        assertFalse(target.isPermissionSet("example.dynamic"));
        assertFalse(target.hasPermission("example.dynamic"));
    }

    /**
     * Confirms manually known nodes participate in Paper wildcard expansion even when Paper has not registered the exact node.
     */
    @Test
    void wildcardPermissionsExpandToManualKnownNodes() {
        PlayerMock target = server.addPlayer("Target");

        plugin.getPermissionService().setPermission(target.getUniqueId(), "manual.*", PermissionValue.TRUE);
        assertFalse(target.isPermissionSet("manual.registered"));

        plugin.getManualPermissionNodeRegistry().addNode("manual.registered", "Manual node");

        assertTrue(target.isPermissionSet("manual.registered"));
        assertTrue(target.hasPermission("manual.registered"));
    }

    /**
     * Confirms fallback registry snapshots are refreshed after other plugins enable.
     */
    @Test
    void pluginEnableRefreshesRegisteredPaperWildcardExpansions() {
        PlayerMock target = server.addPlayer("Target");
        plugin.getPermissionService().setPermission(target.getUniqueId(), "late.*", PermissionValue.TRUE);

        assertFalse(target.isPermissionSet("late.registered"));

        server.getPluginManager().addPermission(new Permission("late.registered"));
        server.getPluginManager().callEvent(new PluginEnableEvent(plugin));

        assertTrue(target.isPermissionSet("late.registered"));
        assertTrue(target.hasPermission("late.registered"));
    }

    /**
     * Confirms exact assignments still beat wildcard assignments after Paper node expansion.
     */
    @Test
    void exactPermissionBeatsWildcardDuringPaperNodeExpansion() {
        PlayerMock target = server.addPlayer("Target");
        server.getPluginManager().addPermission(new Permission("example.registered"));

        plugin.getPermissionService().setPermission(target.getUniqueId(), "example.*", PermissionValue.TRUE);
        plugin.getPermissionService().setPermission(target.getUniqueId(), "example.registered", PermissionValue.FALSE);

        assertTrue(target.isPermissionSet("example.registered"));
        assertFalse(target.hasPermission("example.registered"));
    }

    /**
     * Confirms group, default, and inherited wildcard assignments expand to registered Paper nodes.
     */
    @Test
    void groupWildcardPermissionsExpandToRegisteredPaperNodes() {
        PlayerMock target = server.addPlayer("Target");
        server.getPluginManager().addPermission(new Permission("group.wild.node"));
        server.getPluginManager().addPermission(new Permission("default.wild.node"));
        server.getPluginManager().addPermission(new Permission("inherited.wild.node"));

        plugin.getGroupService().createGroup("staff");
        plugin.getGroupService().setGroupPermission("staff", "group.wild.*", PermissionValue.TRUE);
        plugin.getGroupService().addSubjectGroup(target.getUniqueId(), "staff");
        assertTrue(target.isPermissionSet("group.wild.node"));
        assertTrue(target.hasPermission("group.wild.node"));

        plugin.getGroupService().createGroup("default");
        plugin.getGroupService().setGroupPermission("default", "default.wild.*", PermissionValue.TRUE);
        assertTrue(target.isPermissionSet("default.wild.node"));
        assertTrue(target.hasPermission("default.wild.node"));

        plugin.getGroupService().createGroup("base");
        plugin.getGroupService().setGroupPermission("base", "inherited.wild.*", PermissionValue.TRUE);
        plugin.getGroupService().addGroupParent("staff", "base");
        assertTrue(target.isPermissionSet("inherited.wild.node"));
        assertTrue(target.hasPermission("inherited.wild.node"));
    }

    /**
     * Confirms command mutations persist to disk and refresh Paper runtime permissions end to end.
     *
     * @throws Exception when Brigadier command execution or storage reload fails unexpectedly
     */
    @Test
    void commandMutationPersistsAndRefreshesRuntimeBridge() throws Exception {
        PlayerMock admin = server.addPlayer("Admin");
        PlayerMock target = server.addPlayer("Target");
        plugin.getPermissionService().setPermission(admin.getUniqueId(), PermissionNodes.ADMIN_ALL, PermissionValue.TRUE);
        CommandDispatcher<CommandSourceStack> dispatcher = new CommandDispatcher<>();
        dispatcher.getRoot().addChild(PaperClutchPermsCommand.create(plugin));
        TestCommandSourceStack adminSource = new TestCommandSourceStack(admin);
        Path permissionsFile = plugin.getDataFolder().toPath().resolve("permissions.json");
        Path groupsFile = plugin.getDataFolder().toPath().resolve("groups.json");
        Path nodesFile = plugin.getDataFolder().toPath().resolve("nodes.json");

        assertEquals(1, dispatcher.execute("clutchperms user Target set example.smoke true", adminSource));
        assertEquals(PermissionValue.TRUE, PermissionServices.jsonFile(permissionsFile).getPermission(target.getUniqueId(), "example.smoke"));
        assertTrue(target.isPermissionSet("example.smoke"));
        assertTrue(target.hasPermission("example.smoke"));

        assertEquals(1, dispatcher.execute("clutchperms user Target set example.smoke false", adminSource));
        assertEquals(PermissionValue.FALSE, PermissionServices.jsonFile(permissionsFile).getPermission(target.getUniqueId(), "example.smoke"));
        assertTrue(target.isPermissionSet("example.smoke"));
        assertFalse(target.hasPermission("example.smoke"));

        assertEquals(1, dispatcher.execute("clutchperms user Target clear example.smoke", adminSource));
        assertEquals(PermissionValue.UNSET, PermissionServices.jsonFile(permissionsFile).getPermission(target.getUniqueId(), "example.smoke"));
        assertFalse(target.isPermissionSet("example.smoke"));
        assertFalse(target.hasPermission("example.smoke"));

        assertEquals(1, dispatcher.execute("clutchperms group admin create", adminSource));
        assertEquals(1, dispatcher.execute("clutchperms group admin set example.group true", adminSource));
        assertEquals(1, dispatcher.execute("clutchperms user Target group add admin", adminSource));
        assertEquals(PermissionValue.TRUE, GroupServices.jsonFile(groupsFile).getGroupPermission("admin", "example.group"));
        assertTrue(target.isPermissionSet("example.group"));
        assertTrue(target.hasPermission("example.group"));

        assertEquals(1, dispatcher.execute("clutchperms group base create", adminSource));
        assertEquals(1, dispatcher.execute("clutchperms group base set example.inherited true", adminSource));
        assertEquals(1, dispatcher.execute("clutchperms group admin parent add base", adminSource));
        assertEquals(PermissionValue.TRUE, GroupServices.jsonFile(groupsFile).getGroupPermission("base", "example.inherited"));
        assertTrue(GroupServices.jsonFile(groupsFile).getGroupParents("admin").contains("base"));
        assertTrue(target.isPermissionSet("example.inherited"));
        assertTrue(target.hasPermission("example.inherited"));

        assertEquals(1, dispatcher.execute("clutchperms user Target set wildcard.* false", adminSource));
        assertEquals(PermissionValue.FALSE, PermissionServices.jsonFile(permissionsFile).getPermission(target.getUniqueId(), "wildcard.*"));
        assertTrue(target.isPermissionSet("wildcard.*"));
        assertFalse(target.hasPermission("wildcard.*"));

        assertEquals(1, dispatcher.execute("clutchperms nodes add wildcard.node Paper smoke node", adminSource));
        assertTrue(PermissionNodeRegistries.jsonFile(nodesFile).getKnownNode("wildcard.node").isPresent());
        assertTrue(target.isPermissionSet("wildcard.node"));
        assertFalse(target.hasPermission("wildcard.node"));
    }

    /**
     * Confirms command mutations create backups on later saves and restore reloads Paper runtime permissions.
     *
     * @throws Exception when command execution or storage inspection fails unexpectedly
     */
    @Test
    void backupRestoreReloadsStorageAndRuntimeBridge() throws Exception {
        PlayerMock admin = server.addPlayer("Admin");
        PlayerMock target = server.addPlayer("Target");
        plugin.getPermissionService().setPermission(admin.getUniqueId(), PermissionNodes.ADMIN_ALL, PermissionValue.TRUE);
        CommandDispatcher<CommandSourceStack> dispatcher = new CommandDispatcher<>();
        dispatcher.getRoot().addChild(PaperClutchPermsCommand.create(plugin));
        TestCommandSourceStack adminSource = new TestCommandSourceStack(admin);
        Path permissionsFile = plugin.getDataFolder().toPath().resolve("permissions.json");

        assertEquals(1, dispatcher.execute("clutchperms user Target set example.backup true", adminSource));
        assertTrue(target.hasPermission("example.backup"));
        assertEquals(1, dispatcher.execute("clutchperms user Target set example.backup false", adminSource));
        assertFalse(target.hasPermission("example.backup"));

        StorageBackup backup = plugin.getStorageBackupService().listBackups(StorageFileKind.PERMISSIONS).getFirst();
        assertEquals(PermissionValue.TRUE, PermissionServices.jsonFile(backup.path()).getPermission(target.getUniqueId(), "example.backup"));

        assertEquals(1, dispatcher.execute("clutchperms backup restore permissions " + backup.fileName(), adminSource));

        assertEquals(PermissionValue.TRUE, PermissionServices.jsonFile(permissionsFile).getPermission(target.getUniqueId(), "example.backup"));
        assertEquals(PermissionValue.TRUE, plugin.getPermissionService().getPermission(target.getUniqueId(), "example.backup"));
        assertTrue(target.isPermissionSet("example.backup"));
        assertTrue(target.hasPermission("example.backup"));
    }

    /**
     * Confirms malformed restored files roll disk back and preserve active Paper runtime state.
     *
     * @throws Exception when file setup fails unexpectedly
     */
    @Test
    void malformedBackupRestoreRollsBackAndPreservesRuntimeBridge() throws Exception {
        PlayerMock admin = server.addPlayer("Admin");
        PlayerMock target = server.addPlayer("Target");
        plugin.getPermissionService().setPermission(admin.getUniqueId(), PermissionNodes.ADMIN_ALL, PermissionValue.TRUE);
        plugin.getPermissionService().setPermission(target.getUniqueId(), "Example.Backup", PermissionValue.TRUE);
        CommandDispatcher<CommandSourceStack> dispatcher = new CommandDispatcher<>();
        dispatcher.getRoot().addChild(PaperClutchPermsCommand.create(plugin));
        Path permissionsFile = plugin.getDataFolder().toPath().resolve("permissions.json");
        String liveBeforeRestore = Files.readString(permissionsFile);
        Path backupDirectory = plugin.getDataFolder().toPath().resolve("backups").resolve("permissions");
        Files.createDirectories(backupDirectory);
        String backupFileName = "permissions-20260424-120000000.json";
        Files.writeString(backupDirectory.resolve(backupFileName), """
                {
                  "version": 1,
                  "subjects": {
                    "00000000-0000-0000-0000-000000000001": {
                      "bad.*.node": "TRUE"
                    }
                  }
                }
                """, StandardCharsets.UTF_8);

        CommandSyntaxException exception = assertThrows(CommandSyntaxException.class,
                () -> dispatcher.execute("clutchperms backup restore permissions " + backupFileName, new TestCommandSourceStack(admin)));

        assertTrue(exception.getMessage().contains("Backup operation failed: Failed to apply restored permissions backup " + backupFileName));
        assertEquals(liveBeforeRestore, Files.readString(permissionsFile));
        assertEquals(PermissionValue.TRUE, plugin.getPermissionService().getPermission(target.getUniqueId(), "example.backup"));
        assertTrue(target.isPermissionSet("example.backup"));
        assertTrue(target.hasPermission("example.backup"));
    }

    /**
     * Confirms reload picks up manual file edits and refreshes online Paper permissions.
     *
     * @throws Exception when Brigadier command execution or storage reload fails unexpectedly
     */
    @Test
    void reloadCommandReloadsStorageAndRefreshesRuntimeBridge() throws Exception {
        PlayerMock admin = server.addPlayer("Admin");
        PlayerMock target = server.addPlayer("Target");
        UUID offlineId = UUID.fromString("00000000-0000-0000-0000-000000000404");
        plugin.getPermissionService().setPermission(admin.getUniqueId(), PermissionNodes.ADMIN_ALL, PermissionValue.TRUE);
        CommandDispatcher<CommandSourceStack> dispatcher = new CommandDispatcher<>();
        dispatcher.getRoot().addChild(PaperClutchPermsCommand.create(plugin));
        Path permissionsFile = plugin.getDataFolder().toPath().resolve("permissions.json");
        Path subjectsFile = plugin.getDataFolder().toPath().resolve("subjects.json");
        Path groupsFile = plugin.getDataFolder().toPath().resolve("groups.json");
        Path nodesFile = plugin.getDataFolder().toPath().resolve("nodes.json");

        PermissionServices.jsonFile(permissionsFile).setPermission(target.getUniqueId(), "Example.Reload", PermissionValue.TRUE);
        SubjectMetadataServices.jsonFile(subjectsFile).recordSubject(offlineId, "OfflineReload", Instant.parse("2026-04-24T12:00:00Z"));
        GroupService groupStorage = GroupServices.jsonFile(groupsFile);
        groupStorage.createGroup("staff");
        groupStorage.setGroupPermission("staff", "Example.GroupReload", PermissionValue.TRUE);
        groupStorage.addSubjectGroup(target.getUniqueId(), "staff");
        PermissionNodeRegistries.jsonFile(nodesFile).addNode("example.reloadknown", "Reloaded known node");
        PermissionServices.jsonFile(permissionsFile).setPermission(target.getUniqueId(), "Example.*", PermissionValue.TRUE);

        assertEquals(PermissionValue.UNSET, plugin.getPermissionService().getPermission(target.getUniqueId(), "example.reload"));
        assertFalse(target.isPermissionSet("example.reload"));
        assertFalse(target.isPermissionSet("example.groupreload"));

        assertEquals(1, dispatcher.execute("clutchperms reload", new TestCommandSourceStack(admin)));

        assertEquals(Component.text("Reloaded permissions, subjects, groups, and known nodes from disk."), admin.nextComponentMessage());
        assertEquals(PermissionValue.TRUE, plugin.getPermissionService().getPermission(target.getUniqueId(), "example.reload"));
        assertTrue(target.isPermissionSet("example.reload"));
        assertTrue(target.hasPermission("example.reload"));
        assertTrue(plugin.getPermissionNodeRegistry().getKnownNode("example.reloadknown").isPresent());
        assertTrue(target.isPermissionSet("example.reloadknown"));
        assertTrue(target.hasPermission("example.reloadknown"));
        assertTrue(target.isPermissionSet("example.groupreload"));
        assertTrue(target.hasPermission("example.groupreload"));
        assertEquals("OfflineReload", plugin.getSubjectMetadataService().getSubject(offlineId).orElseThrow().lastKnownName());
        assertSame(plugin.getPermissionService(), server.getServicesManager().getRegistration(PermissionService.class).getProvider());
        assertSame(plugin.getSubjectMetadataService(), server.getServicesManager().getRegistration(SubjectMetadataService.class).getProvider());
        assertSame(plugin.getGroupService(), server.getServicesManager().getRegistration(GroupService.class).getProvider());
        assertSame(plugin.getPermissionNodeRegistry(), server.getServicesManager().getRegistration(PermissionNodeRegistry.class).getProvider());
        assertSame(plugin.getManualPermissionNodeRegistry(), server.getServicesManager().getRegistration(MutablePermissionNodeRegistry.class).getProvider());
        assertSame(plugin.getPermissionResolver(), server.getServicesManager().getRegistration(PermissionResolver.class).getProvider());
    }

    /**
     * Confirms validate checks manual file edits without replacing active Paper runtime state.
     *
     * @throws Exception when Brigadier command execution or storage validation fails unexpectedly
     */
    @Test
    void validateCommandChecksStorageWithoutReplacingRuntimeBridge() throws Exception {
        PlayerMock admin = server.addPlayer("Admin");
        PlayerMock target = server.addPlayer("Target");
        plugin.getPermissionService().setPermission(admin.getUniqueId(), PermissionNodes.ADMIN_ALL, PermissionValue.TRUE);
        plugin.getPermissionService().setPermission(target.getUniqueId(), "Example.Validate", PermissionValue.FALSE);
        PermissionService activePermissionService = plugin.getPermissionService();
        PermissionResolver activePermissionResolver = plugin.getPermissionResolver();
        CommandDispatcher<CommandSourceStack> dispatcher = new CommandDispatcher<>();
        dispatcher.getRoot().addChild(PaperClutchPermsCommand.create(plugin));
        Path permissionsFile = plugin.getDataFolder().toPath().resolve("permissions.json");

        PermissionServices.jsonFile(permissionsFile).setPermission(target.getUniqueId(), "Example.Validate", PermissionValue.TRUE);

        assertEquals(1, dispatcher.execute("clutchperms validate", new TestCommandSourceStack(admin)));

        assertEquals(Component.text("Validated permissions, subjects, groups, and known nodes from disk."), admin.nextComponentMessage());
        assertSame(activePermissionService, plugin.getPermissionService());
        assertSame(activePermissionResolver, plugin.getPermissionResolver());
        assertSame(activePermissionService, server.getServicesManager().getRegistration(PermissionService.class).getProvider());
        assertEquals(PermissionValue.FALSE, plugin.getPermissionService().getPermission(target.getUniqueId(), "example.validate"));
        assertTrue(target.isPermissionSet("example.validate"));
        assertFalse(target.hasPermission("example.validate"));
    }

    /**
     * Confirms a malformed permissions file fails validate without replacing active Paper runtime state.
     *
     * @throws Exception when file setup fails unexpectedly
     */
    @Test
    void malformedPermissionsFileFailsValidateWithoutReplacingRuntimeBridge() throws Exception {
        PlayerMock admin = server.addPlayer("Admin");
        PlayerMock target = server.addPlayer("Target");
        plugin.getPermissionService().setPermission(admin.getUniqueId(), PermissionNodes.ADMIN_ALL, PermissionValue.TRUE);
        plugin.getPermissionService().setPermission(target.getUniqueId(), "Example.Validate", PermissionValue.TRUE);
        PermissionService activePermissionService = plugin.getPermissionService();
        PermissionResolver activePermissionResolver = plugin.getPermissionResolver();
        CommandDispatcher<CommandSourceStack> dispatcher = new CommandDispatcher<>();
        dispatcher.getRoot().addChild(PaperClutchPermsCommand.create(plugin));
        Path permissionsFile = plugin.getDataFolder().toPath().resolve("permissions.json");

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

        CommandSyntaxException exception = assertThrows(CommandSyntaxException.class, () -> dispatcher.execute("clutchperms validate", new TestCommandSourceStack(admin)));

        assertTrue(exception.getMessage().contains("Failed to validate ClutchPerms storage:"));
        assertSame(activePermissionService, plugin.getPermissionService());
        assertSame(activePermissionResolver, plugin.getPermissionResolver());
        assertSame(activePermissionService, server.getServicesManager().getRegistration(PermissionService.class).getProvider());
        assertTrue(target.isPermissionSet("example.validate"));
        assertTrue(target.hasPermission("example.validate"));
        assertEquals(PermissionValue.TRUE, plugin.getPermissionService().getPermission(target.getUniqueId(), "example.validate"));
    }

    /**
     * Confirms a malformed permissions file fails reload without replacing active Paper runtime state.
     *
     * @throws Exception when file setup fails unexpectedly
     */
    @Test
    void malformedPermissionsFileFailsReloadWithoutReplacingRuntimeBridge() throws Exception {
        PlayerMock admin = server.addPlayer("Admin");
        PlayerMock target = server.addPlayer("Target");
        plugin.getPermissionService().setPermission(admin.getUniqueId(), PermissionNodes.ADMIN_ALL, PermissionValue.TRUE);
        plugin.getPermissionService().setPermission(target.getUniqueId(), "Example.Reload", PermissionValue.TRUE);
        PermissionService activePermissionService = plugin.getPermissionService();
        SubjectMetadataService activeSubjectMetadataService = plugin.getSubjectMetadataService();
        GroupService activeGroupService = plugin.getGroupService();
        PermissionResolver activePermissionResolver = plugin.getPermissionResolver();
        CommandDispatcher<CommandSourceStack> dispatcher = new CommandDispatcher<>();
        dispatcher.getRoot().addChild(PaperClutchPermsCommand.create(plugin));
        Path permissionsFile = plugin.getDataFolder().toPath().resolve("permissions.json");

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

        assertThrows(CommandSyntaxException.class, () -> dispatcher.execute("clutchperms reload", new TestCommandSourceStack(admin)));

        assertSame(activePermissionService, plugin.getPermissionService());
        assertSame(activeSubjectMetadataService, plugin.getSubjectMetadataService());
        assertSame(activeGroupService, plugin.getGroupService());
        assertSame(activePermissionResolver, plugin.getPermissionResolver());
        assertSame(activePermissionService, server.getServicesManager().getRegistration(PermissionService.class).getProvider());
        assertSame(activeGroupService, server.getServicesManager().getRegistration(GroupService.class).getProvider());
        assertSame(activePermissionResolver, server.getServicesManager().getRegistration(PermissionResolver.class).getProvider());
        assertTrue(target.isPermissionSet("example.reload"));
        assertTrue(target.hasPermission("example.reload"));
        assertEquals(PermissionValue.TRUE, plugin.getPermissionService().getPermission(target.getUniqueId(), "example.reload"));
    }

    /**
     * Confirms a malformed groups file fails reload without replacing active Paper runtime state.
     *
     * @throws Exception when file setup fails unexpectedly
     */
    @Test
    void malformedGroupsFileFailsReloadWithoutReplacingRuntimeBridge() throws Exception {
        PlayerMock admin = server.addPlayer("Admin");
        PlayerMock target = server.addPlayer("Target");
        plugin.getPermissionService().setPermission(admin.getUniqueId(), PermissionNodes.ADMIN_ALL, PermissionValue.TRUE);
        plugin.getGroupService().createGroup("staff");
        plugin.getGroupService().setGroupPermission("staff", "Example.GroupReload", PermissionValue.TRUE);
        plugin.getGroupService().addSubjectGroup(target.getUniqueId(), "staff");
        GroupService activeGroupService = plugin.getGroupService();
        PermissionResolver activePermissionResolver = plugin.getPermissionResolver();
        CommandDispatcher<CommandSourceStack> dispatcher = new CommandDispatcher<>();
        dispatcher.getRoot().addChild(PaperClutchPermsCommand.create(plugin));
        Path groupsFile = plugin.getDataFolder().toPath().resolve("groups.json");

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

        assertThrows(CommandSyntaxException.class, () -> dispatcher.execute("clutchperms reload", new TestCommandSourceStack(admin)));

        assertSame(activeGroupService, plugin.getGroupService());
        assertSame(activePermissionResolver, plugin.getPermissionResolver());
        assertSame(activeGroupService, server.getServicesManager().getRegistration(GroupService.class).getProvider());
        assertTrue(target.isPermissionSet("example.groupreload"));
        assertTrue(target.hasPermission("example.groupreload"));
    }

    /**
     * Confirms a malformed known nodes file fails reload without replacing active Paper runtime state.
     *
     * @throws Exception when file setup fails unexpectedly
     */
    @Test
    void malformedNodesFileFailsReloadWithoutReplacingRuntimeBridge() throws Exception {
        PlayerMock admin = server.addPlayer("Admin");
        PlayerMock target = server.addPlayer("Target");
        plugin.getPermissionService().setPermission(admin.getUniqueId(), PermissionNodes.ADMIN_ALL, PermissionValue.TRUE);
        plugin.getPermissionService().setPermission(target.getUniqueId(), "Example.*", PermissionValue.TRUE);
        plugin.getManualPermissionNodeRegistry().addNode("example.active", "Active");
        MutablePermissionNodeRegistry activeManualRegistry = plugin.getManualPermissionNodeRegistry();
        PermissionNodeRegistry activeNodeRegistry = plugin.getPermissionNodeRegistry();
        CommandDispatcher<CommandSourceStack> dispatcher = new CommandDispatcher<>();
        dispatcher.getRoot().addChild(PaperClutchPermsCommand.create(plugin));
        Path nodesFile = plugin.getDataFolder().toPath().resolve("nodes.json");

        Files.writeString(nodesFile, """
                {
                  "version": 1,
                  "nodes": {
                    "example.*": {}
                  }
                }
                """, StandardCharsets.UTF_8);

        assertThrows(CommandSyntaxException.class, () -> dispatcher.execute("clutchperms reload", new TestCommandSourceStack(admin)));

        assertSame(activeManualRegistry, plugin.getManualPermissionNodeRegistry());
        assertSame(activeNodeRegistry, plugin.getPermissionNodeRegistry());
        assertSame(activeNodeRegistry, server.getServicesManager().getRegistration(PermissionNodeRegistry.class).getProvider());
        assertTrue(target.isPermissionSet("example.active"));
        assertTrue(target.hasPermission("example.active"));
    }

    /**
     * Confirms runtime attachments are removed when players quit or the plugin disables.
     */
    @Test
    void runtimeAttachmentsAreRemovedOnQuitAndDisable() {
        PlayerMock player = server.addPlayer("Target");

        plugin.getPermissionService().setPermission(player.getUniqueId(), "Example.Cleanup", PermissionValue.TRUE);
        assertTrue(player.isPermissionSet("example.cleanup"));

        server.getPluginManager().callEvent(new PlayerQuitEvent(player, Component.text("Target left"), QuitReason.DISCONNECTED));
        assertFalse(player.isPermissionSet("example.cleanup"));

        plugin.getPermissionService().setPermission(player.getUniqueId(), "Example.Cleanup", PermissionValue.TRUE);
        assertTrue(player.isPermissionSet("example.cleanup"));

        plugin.onDisable();

        assertFalse(player.isPermissionSet("example.cleanup"));
    }

    private static UUID playerId() {
        return UUID.fromString("00000000-0000-0000-0000-000000000001");
    }

    private record TestCommandSourceStack(CommandSender sender) implements CommandSourceStack {

        @Override
        public Location getLocation() {
            if (sender instanceof Entity entity) {
                return entity.getLocation();
            }
            return new Location(null, 0, 0, 0);
        }

        @Override
        public CommandSender getSender() {
            return sender;
        }

        @Override
        public Entity getExecutor() {
            if (sender instanceof Entity entity) {
                return entity;
            }
            return null;
        }

        @Override
        public CommandSourceStack withLocation(Location location) {
            return this;
        }

        @Override
        public CommandSourceStack withExecutor(Entity executor) {
            return this;
        }
    }
}
