package me.clutchy.clutchperms.fabric;

import java.nio.file.Path;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.mojang.brigadier.Command;

import me.clutchy.clutchperms.common.PermissionService;
import me.clutchy.clutchperms.common.PermissionServices;
import me.clutchy.clutchperms.common.PermissionStorageException;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

/**
 * Fabric mod entrypoint that boots the shared persisted permission service and registers a diagnostic server command.
 */
public final class ClutchPermsFabricMod implements ModInitializer {

    /**
     * Mod identifier used by Fabric metadata and config paths.
     */
    public static final String MOD_ID = "clutchperms";

    private static final Logger LOGGER = LoggerFactory.getLogger(ClutchPermsFabricMod.class);

    /**
     * Diagnostic message returned by the bootstrap command while the project is still in its early scaffold phase.
     */
    public static final String STATUS_MESSAGE = "ClutchPerms is running with a persisted permission service.";

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

        // Register a minimal command that proves the mod is loaded and the shared service is alive.
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(Commands.literal("clutchperms").executes(context -> {
            context.getSource().sendSuccess(() -> Component.literal(STATUS_MESSAGE), false);
            return Command.SINGLE_SUCCESS;
        })));

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
