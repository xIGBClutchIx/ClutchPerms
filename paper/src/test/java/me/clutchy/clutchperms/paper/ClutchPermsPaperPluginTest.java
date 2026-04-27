package me.clutchy.clutchperms.paper;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Statement;
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

import me.clutchy.clutchperms.common.command.ClutchPermsCommands;
import me.clutchy.clutchperms.common.config.ClutchPermsConfig;
import me.clutchy.clutchperms.common.display.DisplayText;
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
import me.clutchy.clutchperms.common.storage.SqliteDependencyMode;
import me.clutchy.clutchperms.common.storage.SqliteStore;
import me.clutchy.clutchperms.common.storage.StorageBackup;
import me.clutchy.clutchperms.common.storage.StorageFileKind;
import me.clutchy.clutchperms.common.subject.SubjectMetadata;
import me.clutchy.clutchperms.common.subject.SubjectMetadataService;
import me.clutchy.clutchperms.common.subject.SubjectMetadataServices;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

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

        assertTrue(Files.exists(dataFolder.resolve("database.db")));
        assertTrue(Files.exists(dataFolder.resolve("config.json")));
        assertEquals(ClutchPermsConfig.defaults(), plugin.getClutchPermsConfig());
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
        assertNotNull(server.getPluginManager().getPermission(PermissionNodes.ADMIN_CONFIG_ALL));
        assertNotNull(server.getPluginManager().getPermission(PermissionNodes.ADMIN_STATUS));
        assertNotNull(server.getPluginManager().getPermission(PermissionNodes.ADMIN_CONFIG_VIEW));
        assertNotNull(server.getPluginManager().getPermission(PermissionNodes.ADMIN_CONFIG_SET));
        assertNotNull(server.getPluginManager().getPermission(PermissionNodes.ADMIN_CONFIG_RESET));
        assertNotNull(server.getPluginManager().getPermission(PermissionNodes.ADMIN_USER_SET));
        assertNotNull(server.getPluginManager().getPermission(PermissionNodes.ADMIN_USER_DISPLAY_SET));
        assertNotNull(server.getPluginManager().getPermission(PermissionNodes.ADMIN_GROUP_MEMBERS));
        assertNotNull(server.getPluginManager().getPermission(PermissionNodes.ADMIN_GROUP_DISPLAY_SET));
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
        assertNextMessage(player, ClutchPermsPaperPlugin.STATUS_MESSAGE);
        assertNextMessage(player, "Database file: " + plugin.getDataFolder().toPath().resolve("database.db").toAbsolutePath().normalize());
        assertNextMessage(player, "Config file: " + plugin.getDataFolder().toPath().resolve("config.json").toAbsolutePath().normalize());
        assertNextMessage(player, "Backup retention: newest 10 database backups.");
        assertNextMessage(player, "Command page sizes: help 7, lists 8.");
        assertNextMessage(player, "Chat formatting: enabled.");
        assertNextMessage(player, "Known subjects: 1");
        assertNextMessage(player, "Known groups: 2");
        assertNextMessage(player, "Known permission nodes: " + plugin.getPermissionNodeRegistry().getKnownNodes().size());
        PermissionResolverCacheStats cacheStats = plugin.getPermissionResolver().cacheStats();
        assertNextMessage(player, "Resolver cache: " + cacheStats.subjects() + " subjects, " + cacheStats.nodeResults() + " node results, " + cacheStats.effectiveSnapshots()
                + " effective snapshots.");
        assertNextMessage(player, "Runtime bridge: " + plugin.getStatusDiagnostics().runtimeBridgeStatus());
    }

    /**
     * Confirms the Paper adapter executes status through every shared root command alias.
     *
     * @throws Exception when Brigadier command execution fails unexpectedly
     */
    @Test
    void commandAliasesRespondWithStatusDiagnostics() throws Exception {
        PlayerMock cpermsAdmin = server.addPlayer("CpermsAdmin");
        PlayerMock permsAdmin = server.addPlayer("PermsAdmin");
        plugin.getPermissionService().setPermission(cpermsAdmin.getUniqueId(), PermissionNodes.ADMIN_ALL, PermissionValue.TRUE);
        plugin.getPermissionService().setPermission(permsAdmin.getUniqueId(), PermissionNodes.ADMIN_ALL, PermissionValue.TRUE);
        CommandDispatcher<CommandSourceStack> dispatcher = dispatcherWithAllCommandRoots();

        assertEquals(1, dispatcher.execute("cperms status", new TestCommandSourceStack(cpermsAdmin)));
        assertNextMessage(cpermsAdmin, ClutchPermsCommands.STATUS_MESSAGE);

        assertEquals(1, dispatcher.execute("perms status", new TestCommandSourceStack(permsAdmin)));
        assertNextMessage(permsAdmin, ClutchPermsCommands.STATUS_MESSAGE);
    }

    /**
     * Confirms Paper renders shared command interactions as native Adventure click and hover events.
     *
     * @throws Exception when Brigadier command execution fails unexpectedly
     */
    @Test
    void helpOutputRendersClickAndHoverComponents() throws Exception {
        PlayerMock player = server.addPlayer("Admin");
        plugin.getPermissionService().setPermission(player.getUniqueId(), PermissionNodes.ADMIN_ALL, PermissionValue.TRUE);
        CommandDispatcher<CommandSourceStack> dispatcher = dispatcherWithAllCommandRoots();

        assertEquals(1, dispatcher.execute("clutchperms", new TestCommandSourceStack(player)));

        assertNextMessage(player, "ClutchPerms commands (page 1/7):");
        Component firstCommand = player.nextComponentMessage();
        assertEquals("/clutchperms help [page]", PlainTextComponentSerializer.plainText().serialize(firstCommand));
        assertComponentClick(firstCommand, ClickEvent.Action.SUGGEST_COMMAND, "/clutchperms help [page]");
        assertComponentHover(firstCommand);

        for (int index = 0; index < 6; index++) {
            player.nextComponentMessage();
        }
        Component navigation = player.nextComponentMessage();
        assertEquals("Page 1/7 | Next >", PlainTextComponentSerializer.plainText().serialize(navigation));
        assertComponentClick(navigation, ClickEvent.Action.RUN_COMMAND, "/clutchperms help 2");
        assertComponentHover(navigation);
    }

    /**
     * Confirms Paper chat display components render the effective prefix and suffix as a full chat line.
     */
    @Test
    void paperChatDisplayFormatsFullChatLine() {
        PlayerMock player = server.addPlayer("Target");
        plugin.getSubjectMetadataService().setSubjectPrefix(player.getUniqueId(), DisplayText.parse("&7[Admin]"));
        plugin.getSubjectMetadataService().setSubjectSuffix(player.getUniqueId(), DisplayText.parse("&e*"));

        Component line = PaperDisplayComponents.chatLine(player.getUniqueId(), Component.text(player.getName()), Component.text("hello"), plugin.getDisplayResolver());

        assertEquals("[Admin] Target *: hello", PlainTextComponentSerializer.plainText().serialize(line));
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

        assertCommandUnavailable(dispatcher, "clutchperms status", player);
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
     * Confirms Paper /op and /deop mutate only explicit membership in the protected ClutchPerms op group.
     *
     * @throws Exception when Brigadier command execution fails unexpectedly
     */
    @Test
    void paperOpAndDeopCommandsMutateClutchPermsOpGroupOnly() throws Exception {
        PlayerMock target = server.addPlayer("Target");
        CommandDispatcher<CommandSourceStack> dispatcher = dispatcherWithAllCommandRoots();

        assertFalse(target.isOp());
        assertFalse(plugin.getGroupService().getSubjectGroups(target.getUniqueId()).contains(GroupService.OP_GROUP));
        assertEquals(PermissionValue.UNSET, plugin.getPermissionResolver().resolve(target.getUniqueId(), "*").value());

        assertEquals(1, dispatcher.execute("op Target", new TestCommandSourceStack(server.getConsoleSender())));

        assertFalse(target.isOp());
        assertTrue(plugin.getGroupService().getSubjectGroups(target.getUniqueId()).contains(GroupService.OP_GROUP));
        assertEquals(PermissionValue.TRUE, plugin.getPermissionResolver().resolve(target.getUniqueId(), "*").value());
        assertTrue(target.isPermissionSet("*"));
        assertTrue(target.hasPermission("*"));

        assertEquals(1, dispatcher.execute("deop Target", new TestCommandSourceStack(server.getConsoleSender())));

        assertFalse(target.isOp());
        assertFalse(plugin.getGroupService().getSubjectGroups(target.getUniqueId()).contains(GroupService.OP_GROUP));
        assertEquals(PermissionValue.UNSET, plugin.getPermissionResolver().resolve(target.getUniqueId(), "*").value());
        assertFalse(target.isPermissionSet("*"));
    }

    /**
     * Confirms Paper /op and /deop use ClutchPerms admin permissions for player sources.
     *
     * @throws Exception when Brigadier command execution fails unexpectedly
     */
    @Test
    void paperOpAndDeopCommandsRequireClutchPermsPermissionsForPlayers() throws Exception {
        PlayerMock admin = server.addPlayer("Admin");
        PlayerMock target = server.addPlayer("Target");
        CommandDispatcher<CommandSourceStack> dispatcher = dispatcherWithAllCommandRoots();

        assertCommandUnavailable(dispatcher, "op Target", admin);
        assertCommandUnavailable(dispatcher, "deop Target", admin);

        plugin.getPermissionService().setPermission(admin.getUniqueId(), PermissionNodes.ADMIN_USER_GROUP_ADD, PermissionValue.TRUE);
        assertEquals(1, dispatcher.execute("op Target", new TestCommandSourceStack(admin)));
        assertTrue(plugin.getGroupService().getSubjectGroups(target.getUniqueId()).contains(GroupService.OP_GROUP));
        assertCommandUnavailable(dispatcher, "deop Target", admin);

        plugin.getPermissionService().setPermission(admin.getUniqueId(), PermissionNodes.ADMIN_USER_GROUP_REMOVE, PermissionValue.TRUE);
        assertEquals(1, dispatcher.execute("deop Target", new TestCommandSourceStack(admin)));
        assertFalse(plugin.getGroupService().getSubjectGroups(target.getUniqueId()).contains(GroupService.OP_GROUP));
    }

    /**
     * Confirms Paper /op target resolution reuses ClutchPerms unknown and ambiguous target feedback.
     *
     * @throws Exception when Brigadier command execution fails unexpectedly
     */
    @Test
    void paperOpCommandUsesClutchPermsTargetFeedback() throws Exception {
        PlayerMock admin = server.addPlayer("Admin");
        PlayerMock secondAdmin = server.addPlayer("SecondAdmin");
        plugin.getPermissionService().setPermission(admin.getUniqueId(), PermissionNodes.ADMIN_USER_GROUP_ADD, PermissionValue.TRUE);
        plugin.getPermissionService().setPermission(secondAdmin.getUniqueId(), PermissionNodes.ADMIN_USER_GROUP_ADD, PermissionValue.TRUE);
        plugin.getSubjectMetadataService().recordSubject(UUID.fromString("00000000-0000-0000-0000-000000000101"), "Ambiguous", Instant.parse("2026-04-25T12:00:00Z"));
        plugin.getSubjectMetadataService().recordSubject(UUID.fromString("00000000-0000-0000-0000-000000000102"), "Ambiguous", Instant.parse("2026-04-25T12:01:00Z"));
        CommandDispatcher<CommandSourceStack> dispatcher = dispatcherWithAllCommandRoots();

        assertCommandFails(dispatcher, "op Missing", admin, "Unknown user target: Missing");
        assertCommandFails(dispatcher, "op Ambiguous", secondAdmin, "Ambiguous known user: Ambiguous");
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
        assertEquals(1, dispatcher.execute("clutchperms user Target set example.smoke true", adminSource));
        assertEquals(PermissionValue.TRUE, persistedPermissionValue(target.getUniqueId(), "example.smoke"));
        assertTrue(target.isPermissionSet("example.smoke"));
        assertTrue(target.hasPermission("example.smoke"));

        assertEquals(1, dispatcher.execute("clutchperms user Target set example.smoke false", adminSource));
        assertEquals(PermissionValue.FALSE, persistedPermissionValue(target.getUniqueId(), "example.smoke"));
        assertTrue(target.isPermissionSet("example.smoke"));
        assertFalse(target.hasPermission("example.smoke"));

        assertEquals(1, dispatcher.execute("clutchperms user Target clear example.smoke", adminSource));
        assertEquals(PermissionValue.UNSET, persistedPermissionValue(target.getUniqueId(), "example.smoke"));
        assertFalse(target.isPermissionSet("example.smoke"));
        assertFalse(target.hasPermission("example.smoke"));

        assertEquals(1, dispatcher.execute("clutchperms group admin create", adminSource));
        assertEquals(1, dispatcher.execute("clutchperms group admin set example.group true", adminSource));
        assertEquals(1, dispatcher.execute("clutchperms user Target group add admin", adminSource));
        assertEquals(PermissionValue.TRUE, persistedGroupPermissionValue("admin", "example.group"));
        assertTrue(target.isPermissionSet("example.group"));
        assertTrue(target.hasPermission("example.group"));

        assertEquals(1, dispatcher.execute("clutchperms group base create", adminSource));
        assertEquals(1, dispatcher.execute("clutchperms group base set example.inherited true", adminSource));
        assertEquals(1, dispatcher.execute("clutchperms group admin parent add base", adminSource));
        assertEquals(PermissionValue.TRUE, persistedGroupPermissionValue("base", "example.inherited"));
        assertTrue(persistedGroupParents("admin").contains("base"));
        assertTrue(target.isPermissionSet("example.inherited"));
        assertTrue(target.hasPermission("example.inherited"));

        assertEquals(1, dispatcher.execute("clutchperms user Target set wildcard.* false", adminSource));
        assertEquals(PermissionValue.FALSE, persistedPermissionValue(target.getUniqueId(), "wildcard.*"));
        assertTrue(target.isPermissionSet("wildcard.*"));
        assertFalse(target.hasPermission("wildcard.*"));

        assertEquals(1, dispatcher.execute("clutchperms nodes add wildcard.node Paper smoke node", adminSource));
        assertTrue(persistedKnownNode("wildcard.node"));
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

        assertEquals(1, dispatcher.execute("clutchperms user Target set example.backup true", adminSource));
        assertTrue(target.hasPermission("example.backup"));
        assertEquals(1, dispatcher.execute("clutchperms backup create", adminSource));
        assertEquals(1, dispatcher.execute("clutchperms user Target set example.backup false", adminSource));
        assertFalse(target.hasPermission("example.backup"));

        StorageBackup backup = plugin.getStorageBackupService().listBackups(StorageFileKind.DATABASE).stream()
                .filter(candidate -> persistedPermissionValue(candidate.path(), target.getUniqueId(), "example.backup") == PermissionValue.TRUE).findFirst()
                .orElseThrow(() -> new AssertionError("Expected a permissions backup containing example.backup=true"));
        assertEquals(PermissionValue.TRUE, persistedPermissionValue(backup.path(), target.getUniqueId(), "example.backup"));

        assertEquals(1, dispatcher.execute("clutchperms backup restore " + backup.fileName(), adminSource));

        assertEquals(PermissionValue.TRUE, persistedPermissionValue(target.getUniqueId(), "example.backup"));
        assertEquals(PermissionValue.TRUE, plugin.getPermissionService().getPermission(target.getUniqueId(), "example.backup"));
        assertTrue(target.isPermissionSet("example.backup"));
        assertTrue(target.hasPermission("example.backup"));
    }

    /**
     * Confirms malformed backups fail validation before replacing live storage and preserve active Paper runtime state.
     *
     * @throws Exception when file setup fails unexpectedly
     */
    @Test
    void malformedBackupRestoreFailsValidationAndPreservesRuntimeBridge() throws Exception {
        PlayerMock admin = server.addPlayer("Admin");
        PlayerMock target = server.addPlayer("Target");
        plugin.getPermissionService().setPermission(admin.getUniqueId(), PermissionNodes.ADMIN_ALL, PermissionValue.TRUE);
        plugin.getPermissionService().setPermission(target.getUniqueId(), "Example.Backup", PermissionValue.TRUE);
        CommandDispatcher<CommandSourceStack> dispatcher = new CommandDispatcher<>();
        dispatcher.getRoot().addChild(PaperClutchPermsCommand.create(plugin));
        PermissionValue liveBeforeRestore = persistedPermissionValue(target.getUniqueId(), "example.backup");
        Path backupDirectory = plugin.getDataFolder().toPath().resolve("backups").resolve("database");
        Files.createDirectories(backupDirectory);
        String backupFileName = "database-20260424-120000000.db";
        Files.writeString(backupDirectory.resolve(backupFileName), "not sqlite", StandardCharsets.UTF_8);

        assertCommandFails(dispatcher, "clutchperms backup restore " + backupFileName, admin, "Backup operation failed: Failed to validate database backup " + backupFileName);

        assertEquals(liveBeforeRestore, persistedPermissionValue(target.getUniqueId(), "example.backup"));
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
        try (SqliteStore store = openStore()) {
            PermissionServices.sqlite(store).setPermission(target.getUniqueId(), "Example.Reload", PermissionValue.TRUE);
            SubjectMetadataServices.sqlite(store).recordSubject(offlineId, "OfflineReload", Instant.parse("2026-04-24T12:00:00Z"));
            GroupService groupStorage = GroupServices.sqlite(store);
            groupStorage.createGroup("staff");
            groupStorage.setGroupPermission("staff", "Example.GroupReload", PermissionValue.TRUE);
            groupStorage.addSubjectGroup(target.getUniqueId(), "staff");
            PermissionNodeRegistries.sqlite(store).addNode("example.reloadknown", "Reloaded known node");
            PermissionServices.sqlite(store).setPermission(target.getUniqueId(), "Example.*", PermissionValue.TRUE);
        }

        assertEquals(PermissionValue.UNSET, plugin.getPermissionService().getPermission(target.getUniqueId(), "example.reload"));
        assertFalse(target.isPermissionSet("example.reload"));
        assertFalse(target.isPermissionSet("example.groupreload"));

        assertEquals(1, dispatcher.execute("clutchperms reload", new TestCommandSourceStack(admin)));

        assertNextMessage(admin, "Reloaded config and database storage from disk.");
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
     * Confirms config command updates reload Paper services and active config.
     *
     * @throws Exception when Brigadier command execution fails unexpectedly
     */
    @Test
    void configSetReloadsStorageAndServiceRegistrations() throws Exception {
        PlayerMock admin = server.addPlayer("Admin");
        PlayerMock target = server.addPlayer("Target");
        plugin.getPermissionService().setPermission(admin.getUniqueId(), PermissionNodes.ADMIN_ALL, PermissionValue.TRUE);
        plugin.getPermissionService().setPermission(target.getUniqueId(), "Example.ConfigSet", PermissionValue.TRUE);
        PermissionService activePermissionService = plugin.getPermissionService();
        CommandDispatcher<CommandSourceStack> dispatcher = new CommandDispatcher<>();
        dispatcher.getRoot().addChild(PaperClutchPermsCommand.create(plugin));

        assertEquals(1, dispatcher.execute("clutchperms config set chat.enabled false", new TestCommandSourceStack(admin)));

        assertNextMessage(admin, "Updated config chat.enabled: true -> false. Runtime reloaded.");
        assertEquals(false, plugin.getClutchPermsConfig().chat().enabled());
        assertNotSame(activePermissionService, plugin.getPermissionService());
        assertSame(plugin.getPermissionService(), server.getServicesManager().getRegistration(PermissionService.class).getProvider());
        assertTrue(target.isPermissionSet("example.configset"));
        assertTrue(target.hasPermission("example.configset"));
    }

    /**
     * Confirms the Paper op command replacement flag is exposed through shared config commands.
     *
     * @throws Exception when Brigadier command execution fails unexpectedly
     */
    @Test
    void paperOpCommandReplacementCanBeDisabledInConfig() throws Exception {
        PlayerMock admin = server.addPlayer("Admin");
        plugin.getPermissionService().setPermission(admin.getUniqueId(), PermissionNodes.ADMIN_CONFIG_SET, PermissionValue.TRUE);
        CommandDispatcher<CommandSourceStack> dispatcher = new CommandDispatcher<>();
        dispatcher.getRoot().addChild(PaperClutchPermsCommand.create(plugin));

        assertEquals(1, dispatcher.execute("clutchperms config set paper.replaceOpCommands false", new TestCommandSourceStack(admin)));

        assertNextMessage(admin, "Updated config paper.replaceOpCommands: true -> false. Runtime reloaded.");
        assertEquals(false, plugin.getClutchPermsConfig().paper().replaceOpCommands());
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
        try (SqliteStore store = openStore()) {
            PermissionServices.sqlite(store).setPermission(target.getUniqueId(), "Example.Validate", PermissionValue.TRUE);
        }

        assertEquals(1, dispatcher.execute("clutchperms validate", new TestCommandSourceStack(admin)));

        assertNextMessage(admin, "Validated config and database storage from disk.");
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
        insertInvalidPermissionRow();

        assertCommandFails(dispatcher, "clutchperms validate", admin, "Failed to validate ClutchPerms storage:");

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
        insertInvalidPermissionRow();

        assertCommandFails(dispatcher, "clutchperms reload", admin, "Failed to reload ClutchPerms storage:");

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
     * Confirms a malformed config file fails reload without replacing active Paper runtime state.
     *
     * @throws Exception when file setup fails unexpectedly
     */
    @Test
    void malformedConfigFileFailsReloadWithoutReplacingRuntimeBridge() throws Exception {
        PlayerMock admin = server.addPlayer("Admin");
        PlayerMock target = server.addPlayer("Target");
        plugin.getPermissionService().setPermission(admin.getUniqueId(), PermissionNodes.ADMIN_ALL, PermissionValue.TRUE);
        plugin.getPermissionService().setPermission(target.getUniqueId(), "Example.ConfigReload", PermissionValue.TRUE);
        PermissionService activePermissionService = plugin.getPermissionService();
        PermissionResolver activePermissionResolver = plugin.getPermissionResolver();
        ClutchPermsConfig activeConfig = plugin.getClutchPermsConfig();
        CommandDispatcher<CommandSourceStack> dispatcher = new CommandDispatcher<>();
        dispatcher.getRoot().addChild(PaperClutchPermsCommand.create(plugin));
        Path configFile = plugin.getDataFolder().toPath().resolve("config.json");

        Files.writeString(configFile, "{not-json", StandardCharsets.UTF_8);

        assertCommandFails(dispatcher, "clutchperms reload", admin, "Failed to reload ClutchPerms storage:");

        assertSame(activePermissionService, plugin.getPermissionService());
        assertSame(activePermissionResolver, plugin.getPermissionResolver());
        assertEquals(activeConfig, plugin.getClutchPermsConfig());
        assertSame(activePermissionService, server.getServicesManager().getRegistration(PermissionService.class).getProvider());
        assertTrue(target.isPermissionSet("example.configreload"));
        assertTrue(target.hasPermission("example.configreload"));
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
        try (SqliteStore store = openStore()) {
            store.write(connection -> {
                try (Statement statement = connection.createStatement()) {
                    statement.executeUpdate("INSERT INTO group_parents (group_name, parent_name) VALUES ('staff', 'staff')");
                }
            });
        }

        assertCommandFails(dispatcher, "clutchperms reload", admin, "Failed to reload ClutchPerms storage:");

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
        try (SqliteStore store = openStore()) {
            store.write(connection -> {
                try (Statement statement = connection.createStatement()) {
                    statement.executeUpdate("INSERT INTO known_nodes (node, description) VALUES ('example.*', '')");
                }
            });
        }

        assertCommandFails(dispatcher, "clutchperms reload", admin, "Failed to reload ClutchPerms storage:");

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

    private SqliteStore openStore() {
        return SqliteStore.open(plugin.getDataFolder().toPath().resolve("database.db"), SqliteDependencyMode.ANY_VISIBLE);
    }

    private PermissionValue persistedPermissionValue(UUID subjectId, String node) {
        return persistedPermissionValue(plugin.getDataFolder().toPath().resolve("database.db"), subjectId, node);
    }

    private static PermissionValue persistedPermissionValue(Path databaseFile, UUID subjectId, String node) {
        try (SqliteStore store = SqliteStore.open(databaseFile, SqliteDependencyMode.ANY_VISIBLE)) {
            return PermissionServices.sqlite(store).getPermission(subjectId, node);
        }
    }

    private PermissionValue persistedGroupPermissionValue(String groupName, String node) {
        try (SqliteStore store = openStore()) {
            return GroupServices.sqlite(store).getGroupPermission(groupName, node);
        }
    }

    private java.util.Set<String> persistedGroupParents(String groupName) {
        try (SqliteStore store = openStore()) {
            return GroupServices.sqlite(store).getGroupParents(groupName);
        }
    }

    private boolean persistedKnownNode(String node) {
        try (SqliteStore store = openStore()) {
            return PermissionNodeRegistries.sqlite(store).getKnownNode(node).isPresent();
        }
    }

    private void insertInvalidPermissionRow() {
        try (SqliteStore store = openStore()) {
            store.write(connection -> {
                try (Statement statement = connection.createStatement()) {
                    statement.executeUpdate("INSERT INTO subject_permissions (subject_id, node, value) VALUES ('00000000-0000-0000-0000-000000000001', 'example.*.bad', 'TRUE')");
                }
            });
        }
    }

    private CommandDispatcher<CommandSourceStack> dispatcherWithAllCommandRoots() {
        CommandDispatcher<CommandSourceStack> dispatcher = new CommandDispatcher<>();
        ClutchPermsCommands.ROOT_LITERALS.forEach(rootLiteral -> dispatcher.getRoot().addChild(PaperClutchPermsCommand.create(plugin, rootLiteral)));
        dispatcher.getRoot().addChild(PaperClutchPermsCommand.createOp(plugin));
        dispatcher.getRoot().addChild(PaperClutchPermsCommand.createDeop(plugin));
        return dispatcher;
    }

    private static void assertCommandFails(CommandDispatcher<CommandSourceStack> dispatcher, String command, PlayerMock player, String expectedMessage) {
        try {
            assertEquals(0, dispatcher.execute(command, new TestCommandSourceStack(player)));
        } catch (CommandSyntaxException exception) {
            throw new AssertionError("Expected styled command failure for " + command, exception);
        }
        assertNextMessageContains(player, expectedMessage);
    }

    private static void assertCommandUnavailable(CommandDispatcher<CommandSourceStack> dispatcher, String command, PlayerMock player) {
        assertThrows(CommandSyntaxException.class, () -> dispatcher.execute(command, new TestCommandSourceStack(player)));
    }

    private static void assertNextMessage(PlayerMock player, String expectedMessage) {
        assertEquals(expectedMessage, PlainTextComponentSerializer.plainText().serialize(player.nextComponentMessage()));
    }

    private static void assertNextMessageContains(PlayerMock player, String expectedMessage) {
        String message = PlainTextComponentSerializer.plainText().serialize(player.nextComponentMessage());
        assertTrue(message.contains(expectedMessage), () -> "Expected message to contain <" + expectedMessage + "> but was <" + message + ">");
    }

    private static void assertComponentClick(Component component, ClickEvent.Action action, String value) {
        assertTrue(hasClick(component, action, value), () -> "Expected click " + action + " " + value + " in " + component);
    }

    private static void assertComponentHover(Component component) {
        assertTrue(hasHover(component), () -> "Expected hover text in " + component);
    }

    private static boolean hasClick(Component component, ClickEvent.Action action, String value) {
        ClickEvent clickEvent = component.clickEvent();
        if (clickEvent != null && clickEvent.action() == action && value.equals(clickEvent.value())) {
            return true;
        }
        return component.children().stream().anyMatch(child -> hasClick(child, action, value));
    }

    private static boolean hasHover(Component component) {
        if (component.hoverEvent() != null) {
            return true;
        }
        return component.children().stream().anyMatch(ClutchPermsPaperPluginTest::hasHover);
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
