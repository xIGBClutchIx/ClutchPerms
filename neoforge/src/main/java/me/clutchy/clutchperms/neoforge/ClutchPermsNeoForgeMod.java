package me.clutchy.clutchperms.neoforge;

import java.nio.file.Path;
import java.util.Objects;

import org.slf4j.Logger;

import com.mojang.brigadier.Command;
import com.mojang.logging.LogUtils;

import me.clutchy.clutchperms.common.PermissionService;
import me.clutchy.clutchperms.common.PermissionServices;
import me.clutchy.clutchperms.common.PermissionStorageException;

import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

/**
 * NeoForge mod entrypoint that boots the shared persisted permission service and registers a diagnostic server command.
 */
@Mod(ClutchPermsNeoForgeMod.MOD_ID)
public final class ClutchPermsNeoForgeMod {

    /**
     * Mod identifier used by NeoForge metadata and the annotated entrypoint.
     */
    public static final String MOD_ID = "clutchperms";

    private static final Logger LOGGER = LogUtils.getLogger();

    /**
     * Diagnostic message returned by the bootstrap command while the project is still in its early scaffold phase.
     */
    public static final String STATUS_MESSAGE = "ClutchPerms is running with a persisted permission service.";

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
