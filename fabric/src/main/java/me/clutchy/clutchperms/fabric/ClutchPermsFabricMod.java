package me.clutchy.clutchperms.fabric;

import java.nio.file.Path;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import me.clutchy.clutchperms.common.PermissionService;
import me.clutchy.clutchperms.common.PermissionServices;
import me.clutchy.clutchperms.common.PermissionStorageException;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.loader.api.FabricLoader;

/**
 * Fabric mod entrypoint that boots the shared persisted permission service and registers shared Brigadier commands.
 */
public final class ClutchPermsFabricMod implements ModInitializer {

    /**
     * Mod identifier used by Fabric metadata and config paths.
     */
    public static final String MOD_ID = "clutchperms";

    private static final Logger LOGGER = LoggerFactory.getLogger(ClutchPermsFabricMod.class);

    /**
     * Active permission service instance for the current Fabric server lifecycle.
     */
    private static PermissionService permissionService;

    /**
     * Initializes the shared persisted service and hooks command registration into the Fabric lifecycle.
     */
    @Override
    public void onInitialize() {
        Path permissionsFile = FabricLoader.getInstance().getConfigDir().resolve(MOD_ID).resolve("permissions.json");
        try {
            permissionService = PermissionServices.jsonFile(permissionsFile);
        } catch (PermissionStorageException exception) {
            LOGGER.error("Failed to load ClutchPerms permissions from {}", permissionsFile, exception);
            throw new IllegalStateException("Failed to load ClutchPerms permissions", exception);
        }

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(FabricClutchPermsCommand.create(permissionService)));

        // Clear the static reference when the server stops so stale state is not retained.
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> permissionService = null);
    }

    /**
     * Returns the active permission service instance.
     *
     * @return the service initialized during Fabric bootstrap
     * @throws NullPointerException if the service is requested before initialization completes
     */
    public static PermissionService getPermissionService() {
        return Objects.requireNonNull(permissionService, "Permission service has not been initialized");
    }
}
