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

import me.clutchy.clutchperms.common.PermissionNodes;
import me.clutchy.clutchperms.common.PermissionService;
import me.clutchy.clutchperms.common.PermissionServices;
import me.clutchy.clutchperms.common.PermissionValue;
import me.clutchy.clutchperms.common.SubjectMetadata;
import me.clutchy.clutchperms.common.SubjectMetadataService;
import me.clutchy.clutchperms.common.SubjectMetadataServices;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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
        plugin.getPermissionService().setPermission(player.getUniqueId(), PermissionNodes.ADMIN, PermissionValue.TRUE);
        CommandDispatcher<CommandSourceStack> dispatcher = new CommandDispatcher<>();
        dispatcher.getRoot().addChild(PaperClutchPermsCommand.create(plugin));

        assertEquals(1, dispatcher.execute("clutchperms status", new TestCommandSourceStack(player)));
        assertEquals(Component.text(ClutchPermsPaperPlugin.STATUS_MESSAGE), player.nextComponentMessage());
        assertEquals(Component.text("Permissions file: " + plugin.getDataFolder().toPath().resolve("permissions.json").toAbsolutePath().normalize()),
                player.nextComponentMessage());
        assertEquals(Component.text("Subjects file: " + plugin.getDataFolder().toPath().resolve("subjects.json").toAbsolutePath().normalize()), player.nextComponentMessage());
        assertEquals(Component.text("Known subjects: 1"), player.nextComponentMessage());
        assertEquals(Component.text("Runtime bridge: Paper permission attachment bridge active with 1 attached players"), player.nextComponentMessage());
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
     * Confirms command mutations persist to disk and refresh Paper runtime permissions end to end.
     *
     * @throws Exception when Brigadier command execution or storage reload fails unexpectedly
     */
    @Test
    void commandMutationPersistsAndRefreshesRuntimeBridge() throws Exception {
        PlayerMock admin = server.addPlayer("Admin");
        PlayerMock target = server.addPlayer("Target");
        plugin.getPermissionService().setPermission(admin.getUniqueId(), PermissionNodes.ADMIN, PermissionValue.TRUE);
        CommandDispatcher<CommandSourceStack> dispatcher = new CommandDispatcher<>();
        dispatcher.getRoot().addChild(PaperClutchPermsCommand.create(plugin));
        TestCommandSourceStack adminSource = new TestCommandSourceStack(admin);
        Path permissionsFile = plugin.getDataFolder().toPath().resolve("permissions.json");

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
        plugin.getPermissionService().setPermission(admin.getUniqueId(), PermissionNodes.ADMIN, PermissionValue.TRUE);
        CommandDispatcher<CommandSourceStack> dispatcher = new CommandDispatcher<>();
        dispatcher.getRoot().addChild(PaperClutchPermsCommand.create(plugin));
        Path permissionsFile = plugin.getDataFolder().toPath().resolve("permissions.json");
        Path subjectsFile = plugin.getDataFolder().toPath().resolve("subjects.json");

        PermissionServices.jsonFile(permissionsFile).setPermission(target.getUniqueId(), "Example.Reload", PermissionValue.TRUE);
        SubjectMetadataServices.jsonFile(subjectsFile).recordSubject(offlineId, "OfflineReload", Instant.parse("2026-04-24T12:00:00Z"));

        assertEquals(PermissionValue.UNSET, plugin.getPermissionService().getPermission(target.getUniqueId(), "example.reload"));
        assertFalse(target.isPermissionSet("example.reload"));

        assertEquals(1, dispatcher.execute("clutchperms reload", new TestCommandSourceStack(admin)));

        assertEquals(Component.text("Reloaded permissions and subjects from disk."), admin.nextComponentMessage());
        assertEquals(PermissionValue.TRUE, plugin.getPermissionService().getPermission(target.getUniqueId(), "example.reload"));
        assertTrue(target.isPermissionSet("example.reload"));
        assertTrue(target.hasPermission("example.reload"));
        assertEquals("OfflineReload", plugin.getSubjectMetadataService().getSubject(offlineId).orElseThrow().lastKnownName());
        assertSame(plugin.getPermissionService(), server.getServicesManager().getRegistration(PermissionService.class).getProvider());
        assertSame(plugin.getSubjectMetadataService(), server.getServicesManager().getRegistration(SubjectMetadataService.class).getProvider());
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
        plugin.getPermissionService().setPermission(admin.getUniqueId(), PermissionNodes.ADMIN, PermissionValue.TRUE);
        plugin.getPermissionService().setPermission(target.getUniqueId(), "Example.Reload", PermissionValue.TRUE);
        PermissionService activePermissionService = plugin.getPermissionService();
        SubjectMetadataService activeSubjectMetadataService = plugin.getSubjectMetadataService();
        CommandDispatcher<CommandSourceStack> dispatcher = new CommandDispatcher<>();
        dispatcher.getRoot().addChild(PaperClutchPermsCommand.create(plugin));
        Path permissionsFile = plugin.getDataFolder().toPath().resolve("permissions.json");

        Files.writeString(permissionsFile, "{ malformed permissions json", StandardCharsets.UTF_8);

        assertThrows(CommandSyntaxException.class, () -> dispatcher.execute("clutchperms reload", new TestCommandSourceStack(admin)));

        assertSame(activePermissionService, plugin.getPermissionService());
        assertSame(activeSubjectMetadataService, plugin.getSubjectMetadataService());
        assertSame(activePermissionService, server.getServicesManager().getRegistration(PermissionService.class).getProvider());
        assertTrue(target.isPermissionSet("example.reload"));
        assertTrue(target.hasPermission("example.reload"));
        assertEquals(PermissionValue.TRUE, plugin.getPermissionService().getPermission(target.getUniqueId(), "example.reload"));
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
