package me.clutchy.clutchperms.fabric;

import java.util.Objects;

import com.mojang.brigadier.Command;

import me.clutchy.clutchperms.common.InMemoryPermissionService;
import me.clutchy.clutchperms.common.PermissionService;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

/**
 * Fabric mod entrypoint that boots the shared permission service and registers a diagnostic server command.
 */
public final class ClutchPermsFabricMod implements ModInitializer {

    /**
     * Diagnostic message returned by the bootstrap command while the project is still in its early scaffold phase.
     */
    public static final String STATUS_MESSAGE = "ClutchPerms is running with an in-memory permission service.";

    /**
     * Active permission service instance for the current Fabric server lifecycle.
     */
    private static PermissionService permissionService;

    /**
     * Initializes the shared service and hooks command registration into the Fabric lifecycle.
     */
    @Override
    public void onInitialize() {
        permissionService = new InMemoryPermissionService();

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
