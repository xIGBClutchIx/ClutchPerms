package me.clutchy.clutchperms.neoforge;

import java.util.Objects;

import com.mojang.brigadier.Command;

import me.clutchy.clutchperms.common.InMemoryPermissionService;
import me.clutchy.clutchperms.common.PermissionService;

import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

/**
 * NeoForge mod entrypoint that boots the shared permission service and registers a diagnostic server command.
 */
@Mod(ClutchPermsNeoForgeMod.MOD_ID)
public final class ClutchPermsNeoForgeMod {

    /**
     * Mod identifier used by NeoForge metadata and the annotated entrypoint.
     */
    public static final String MOD_ID = "clutchperms";

    /**
     * Diagnostic message returned by the bootstrap command while the project is still in its early scaffold phase.
     */
    public static final String STATUS_MESSAGE = "ClutchPerms is running with an in-memory permission service.";

    /**
     * Active permission service instance for the current NeoForge server lifecycle.
     */
    private static PermissionService permissionService;

    /**
     * Initializes the shared service and hooks command registration into the NeoForge lifecycle.
     */
    public ClutchPermsNeoForgeMod() {
        permissionService = new InMemoryPermissionService();

        NeoForge.EVENT_BUS.addListener(this::registerCommands);
        NeoForge.EVENT_BUS.addListener(this::onServerStopped);
    }

    private void registerCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("clutchperms").executes(context -> {
            context.getSource().sendSuccess(() -> Component.literal(STATUS_MESSAGE), false);
            return Command.SINGLE_SUCCESS;
        }));
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
