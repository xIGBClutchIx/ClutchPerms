package me.clutchy.clutchperms.fabric;

import java.nio.file.Path;
import java.time.Instant;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import me.clutchy.clutchperms.common.PermissionService;
import me.clutchy.clutchperms.common.PermissionServices;
import me.clutchy.clutchperms.common.PermissionStorageException;
import me.clutchy.clutchperms.common.SubjectMetadataService;
import me.clutchy.clutchperms.common.SubjectMetadataServices;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.level.ServerPlayer;

/**
 * Fabric mod entrypoint that boots the shared persisted permission service, registers shared Brigadier commands, and exposes assignments to fabric-permissions-api.
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
     * Active subject metadata service instance for the current Fabric server lifecycle.
     */
    private static SubjectMetadataService subjectMetadataService;

    /**
     * Initializes the shared persisted service and hooks command registration into the Fabric lifecycle.
     */
    @Override
    public void onInitialize() {
        Path permissionsFile = FabricLoader.getInstance().getConfigDir().resolve(MOD_ID).resolve("permissions.json");
        Path subjectsFile = FabricLoader.getInstance().getConfigDir().resolve(MOD_ID).resolve("subjects.json");
        try {
            permissionService = PermissionServices.jsonFile(permissionsFile);
            subjectMetadataService = SubjectMetadataServices.jsonFile(subjectsFile);
        } catch (PermissionStorageException exception) {
            LOGGER.error("Failed to load ClutchPerms storage from {}", FabricLoader.getInstance().getConfigDir().resolve(MOD_ID), exception);
            throw new IllegalStateException("Failed to load ClutchPerms storage", exception);
        }

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(FabricClutchPermsCommand.create(permissionService)));
        FabricRuntimePermissionBridge.register(permissionService);
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> recordSubject(handler.getPlayer()));
        ServerLifecycleEvents.SERVER_STARTED.register(server -> server.getPlayerList().getPlayers().forEach(ClutchPermsFabricMod::recordSubject));

        // Clear the static reference when the server stops so stale state is not retained.
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            permissionService = null;
            subjectMetadataService = null;
        });
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

    /**
     * Returns the active subject metadata service instance.
     *
     * @return the service initialized during Fabric bootstrap
     * @throws NullPointerException if the service is requested before initialization completes
     */
    public static SubjectMetadataService getSubjectMetadataService() {
        return Objects.requireNonNull(subjectMetadataService, "Subject metadata service has not been initialized");
    }

    private static void recordSubject(ServerPlayer player) {
        getSubjectMetadataService().recordSubject(player.getUUID(), player.getGameProfile().name(), Instant.now());
    }
}
