package me.clutchy.clutchperms.forge;

import java.nio.file.Path;
import java.time.Instant;
import java.util.Objects;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import me.clutchy.clutchperms.common.PermissionService;
import me.clutchy.clutchperms.common.PermissionServices;
import me.clutchy.clutchperms.common.PermissionStorageException;
import me.clutchy.clutchperms.common.SubjectMetadataService;
import me.clutchy.clutchperms.common.SubjectMetadataServices;

import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.server.permission.PermissionAPI;
import net.minecraftforge.server.permission.events.PermissionGatherEvent;

/**
 * Forge mod entrypoint that boots the shared persisted permission service, registers shared Brigadier commands, and contributes a native permission handler.
 */
@Mod(ClutchPermsForgeMod.MOD_ID)
public final class ClutchPermsForgeMod {

    /**
     * Mod identifier used by Forge metadata and the annotated entrypoint.
     */
    public static final String MOD_ID = "clutchperms";

    private static final Logger LOGGER = LogUtils.getLogger();

    /**
     * Active permission service instance for the current Forge server lifecycle.
     */
    private static PermissionService permissionService;

    /**
     * Active subject metadata service instance for the current Forge server lifecycle.
     */
    private static SubjectMetadataService subjectMetadataService;

    /**
     * Initializes the shared persisted service and hooks command registration into the Forge lifecycle.
     */
    public ClutchPermsForgeMod() {
        Path permissionsFile = FMLPaths.CONFIGDIR.get().resolve(MOD_ID).resolve("permissions.json");
        Path subjectsFile = FMLPaths.CONFIGDIR.get().resolve(MOD_ID).resolve("subjects.json");
        try {
            permissionService = PermissionServices.jsonFile(permissionsFile);
            subjectMetadataService = SubjectMetadataServices.jsonFile(subjectsFile);
        } catch (PermissionStorageException exception) {
            LOGGER.error("Failed to load ClutchPerms storage from {}", FMLPaths.CONFIGDIR.get().resolve(MOD_ID), exception);
            throw new IllegalStateException("Failed to load ClutchPerms storage", exception);
        }

        RegisterCommandsEvent.BUS.addListener(this::registerCommands);
        PermissionGatherEvent.Handler.BUS.addListener(this::registerPermissionHandler);
        PermissionGatherEvent.Nodes.BUS.addListener(this::registerPermissionNodes);
        ServerStartedEvent.BUS.addListener(this::onServerStarted);
        PlayerEvent.PlayerLoggedInEvent.BUS.addListener(this::onPlayerLoggedIn);
        ServerStoppedEvent.BUS.addListener(this::onServerStopped);
    }

    private void registerCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(ForgeClutchPermsCommand.create(permissionService));
    }

    private void registerPermissionHandler(PermissionGatherEvent.Handler event) {
        event.addPermissionHandler(ForgeClutchPermsPermissionHandler.IDENTIFIER, registeredNodes -> new ForgeClutchPermsPermissionHandler(permissionService, registeredNodes));
    }

    private void registerPermissionNodes(PermissionGatherEvent.Nodes event) {
        event.addNodes(ForgeClutchPermsPermissionHandler.ADMIN_NODE);
    }

    private void onServerStarted(ServerStartedEvent event) {
        event.getServer().getPlayerList().getPlayers().forEach(this::recordSubject);

        if (ForgeClutchPermsPermissionHandler.IDENTIFIER.equals(PermissionAPI.getActivePermissionHandler())) {
            LOGGER.info("ClutchPerms Forge runtime permission bridge is active.");
            return;
        }

        LOGGER.info("ClutchPerms Forge runtime permission bridge is registered but inactive. Set the Forge server permissionHandler config value to {} to activate it.",
                ForgeClutchPermsPermissionHandler.IDENTIFIER);
    }

    private void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            recordSubject(player);
        }
    }

    private void onServerStopped(ServerStoppedEvent event) {
        permissionService = null;
        subjectMetadataService = null;
    }

    /**
     * Returns the active permission service instance.
     *
     * @return the service initialized during Forge bootstrap
     * @throws NullPointerException if the service is requested before initialization completes
     */
    public static PermissionService getPermissionService() {
        return Objects.requireNonNull(permissionService, "Permission service has not been initialized");
    }

    /**
     * Returns the active subject metadata service instance.
     *
     * @return the service initialized during Forge bootstrap
     * @throws NullPointerException if the service is requested before initialization completes
     */
    public static SubjectMetadataService getSubjectMetadataService() {
        return Objects.requireNonNull(subjectMetadataService, "Subject metadata service has not been initialized");
    }

    private void recordSubject(ServerPlayer player) {
        getSubjectMetadataService().recordSubject(player.getUUID(), player.getGameProfile().name(), Instant.now());
    }
}
