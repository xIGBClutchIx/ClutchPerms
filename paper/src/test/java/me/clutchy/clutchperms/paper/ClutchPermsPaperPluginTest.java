package me.clutchy.clutchperms.paper;

import java.util.Map;
import java.util.UUID;

import org.bukkit.plugin.RegisteredServiceProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import me.clutchy.clutchperms.common.PermissionService;

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
     * Confirms that the shared permission service is exposed through Bukkit's service registry.
     */
    @Test
    void permissionServiceIsRegistered() {
        RegisteredServiceProvider<PermissionService> registration = server.getServicesManager().getRegistration(PermissionService.class);

        assertNotNull(registration);
        assertSame(plugin.getPermissionService(), registration.getProvider());
        assertEquals(Map.of(), registration.getProvider().getPermissions(playerId()));
    }

    /**
     * Confirms that the bootstrap command is registered and sends the expected diagnostic message.
     */
    @Test
    void clutchPermsCommandRespondsWithDiagnosticMessage() {
        PlayerMock player = server.addPlayer();
        player.setOp(true);

        assertTrue(server.dispatchCommand(player, "clutchperms"));
        assertEquals(Component.text(ClutchPermsPaperPlugin.STATUS_MESSAGE), player.nextComponentMessage());
    }

    private static UUID playerId() {
        return UUID.fromString("00000000-0000-0000-0000-000000000001");
    }
}
