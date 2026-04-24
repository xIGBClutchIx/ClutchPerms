package me.clutchy.clutchperms.neoforge;

import java.nio.file.Path;
import java.util.Objects;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import me.clutchy.clutchperms.common.PermissionService;
import me.clutchy.clutchperms.common.PermissionServices;
import me.clutchy.clutchperms.common.PermissionStorageException;

import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.server.permission.PermissionAPI;
import net.neoforged.neoforge.server.permission.events.PermissionGatherEvent;

/**
 * NeoForge mod entrypoint that boots the shared persisted permission service, registers shared Brigadier commands, and contributes a native permission handler.
 */
@Mod(ClutchPermsNeoForgeMod.MOD_ID)
public final class ClutchPermsNeoForgeMod {

    /**
     * Mod identifier used by NeoForge metadata and the annotated entrypoint.
     */
    public static final String MOD_ID = "clutchperms";

    private static final Logger LOGGER = LogUtils.getLogger();

    /**
     * Active permission service instance for the current NeoForge server lifecycle.
     */
    private static PermissionService permissionService;

    /**
     * Initializes the shared persisted service and hooks command registration into the NeoForge lifecycle.
     */
    public ClutchPermsNeoForgeMod() {
        Path permissionsFile = FMLPaths.CONFIGDIR.get().resolve(MOD_ID).resolve("permissions.json");
        try {
            permissionService = PermissionServices.jsonFile(permissionsFile);
        } catch (PermissionStorageException exception) {
            LOGGER.error("Failed to load ClutchPerms permissions from {}", permissionsFile, exception);
            throw new IllegalStateException("Failed to load ClutchPerms permissions", exception);
        }

        NeoForge.EVENT_BUS.addListener(this::registerCommands);
        NeoForge.EVENT_BUS.addListener(this::registerPermissionHandler);
        NeoForge.EVENT_BUS.addListener(this::registerPermissionNodes);
        NeoForge.EVENT_BUS.addListener(this::onServerStarted);
        NeoForge.EVENT_BUS.addListener(this::onServerStopped);
    }

    private void registerCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(NeoForgeClutchPermsCommand.create(permissionService));
    }

    private void registerPermissionHandler(PermissionGatherEvent.Handler event) {
        event.addPermissionHandler(NeoForgeClutchPermsPermissionHandler.IDENTIFIER,
                registeredNodes -> new NeoForgeClutchPermsPermissionHandler(permissionService, registeredNodes));
    }

    private void registerPermissionNodes(PermissionGatherEvent.Nodes event) {
        event.addNodes(NeoForgeClutchPermsPermissionHandler.ADMIN_NODE);
    }

    private void onServerStarted(ServerStartedEvent event) {
        if (NeoForgeClutchPermsPermissionHandler.IDENTIFIER.equals(PermissionAPI.getActivePermissionHandler())) {
            LOGGER.info("ClutchPerms NeoForge runtime permission bridge is active.");
            return;
        }

        LOGGER.info("ClutchPerms NeoForge runtime permission bridge is registered but inactive. Set the NeoForge server permissionHandler config value to {} to activate it.",
                NeoForgeClutchPermsPermissionHandler.IDENTIFIER);
    }

    private void onServerStopped(ServerStoppedEvent event) {
        permissionService = null;
    }

    /**
     * Returns the active permission service instance.
     *
     * @return the service initialized during NeoForge bootstrap
     * @throws NullPointerException if the service is requested before initialization completes
     */
    public static PermissionService getPermissionService() {
        return Objects.requireNonNull(permissionService, "Permission service has not been initialized");
    }
}
