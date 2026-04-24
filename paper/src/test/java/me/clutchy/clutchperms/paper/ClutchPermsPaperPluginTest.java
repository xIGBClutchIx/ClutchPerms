package me.clutchy.clutchperms.paper;

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

import io.papermc.paper.command.brigadier.CommandSourceStack;

import me.clutchy.clutchperms.common.PermissionNodes;
import me.clutchy.clutchperms.common.PermissionService;
import me.clutchy.clutchperms.common.PermissionValue;
import me.clutchy.clutchperms.common.SubjectMetadata;
import me.clutchy.clutchperms.common.SubjectMetadataService;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
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
