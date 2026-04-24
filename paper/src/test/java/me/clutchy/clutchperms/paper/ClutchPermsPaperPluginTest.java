package me.clutchy.clutchperms.paper;

import java.util.Map;
import java.util.UUID;

import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
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
     * Confirms that the Paper adapter executes the shared Brigadier command tree.
     *
     * @throws Exception when Brigadier command execution fails unexpectedly
     */
    @Test
    void clutchPermsCommandRespondsWithStatusMessage() throws Exception {
        PlayerMock player = server.addPlayer("Admin");
        plugin.getPermissionService().setPermission(player.getUniqueId(), PermissionNodes.ADMIN, PermissionValue.TRUE);
        CommandDispatcher<CommandSourceStack> dispatcher = new CommandDispatcher<>();
        dispatcher.getRoot().addChild(PaperClutchPermsCommand.create(plugin));

        assertEquals(1, dispatcher.execute("clutchperms", new TestCommandSourceStack(player)));
        assertEquals(Component.text(ClutchPermsPaperPlugin.STATUS_MESSAGE), player.nextComponentMessage());
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
