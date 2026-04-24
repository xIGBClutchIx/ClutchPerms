package me.clutchy.clutchperms.paper;

import java.nio.file.Path;
import java.util.Objects;
import java.util.logging.Level;

import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;

import me.clutchy.clutchperms.common.PermissionService;
import me.clutchy.clutchperms.common.PermissionServices;
import me.clutchy.clutchperms.common.PermissionStorageException;
import me.clutchy.clutchperms.common.command.ClutchPermsCommands;

/**
 * Paper plugin entrypoint that exposes the shared persisted permission service and Brigadier command adapter.
 */
public class ClutchPermsPaperPlugin extends JavaPlugin {

    private static final String PERMISSIONS_FILE_NAME = "permissions.json";

    /**
     * Status message returned by the root command while the project is still in its early scaffold phase.
     */
    public static final String STATUS_MESSAGE = ClutchPermsCommands.STATUS_MESSAGE;

    /**
     * Active permission service instance for the lifecycle of this plugin enable.
     */
    private PermissionService permissionService;

    /**
     * Applies persisted direct assignments to online Paper players.
     */
    private PaperRuntimePermissionBridge runtimePermissionBridge;

    /**
     * Boots the persisted permission service and registers the shared Brigadier command tree.
     */
    @Override
    public void onEnable() {
        Path permissionsFile = getDataFolder().toPath().resolve(PERMISSIONS_FILE_NAME);
        PermissionService storagePermissionService;
        try {
            storagePermissionService = PermissionServices.jsonFile(permissionsFile);
        } catch (PermissionStorageException exception) {
            getLogger().log(Level.SEVERE, "Failed to load ClutchPerms permissions from " + permissionsFile, exception);
            throw new IllegalStateException("Failed to load ClutchPerms permissions", exception);
        }

        runtimePermissionBridge = new PaperRuntimePermissionBridge(this, storagePermissionService);
        permissionService = PermissionServices.observing(storagePermissionService, runtimePermissionBridge::refreshSubject);

        // Register the shared service so other plugins on Paper can discover it.
        getServer().getServicesManager().register(PermissionService.class, permissionService, this, ServicePriority.Normal);
        getServer().getPluginManager().registerEvents(runtimePermissionBridge, this);
        runtimePermissionBridge.refreshOnlinePlayers();

        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS,
                event -> event.registrar().register(PaperClutchPermsCommand.create(this), "Manages ClutchPerms direct permissions"));
    }

    /**
     * Unregisters any services exposed by this plugin when the server disables it.
     */
    @Override
    public void onDisable() {
        getServer().getServicesManager().unregisterAll(this);
        if (runtimePermissionBridge != null) {
            runtimePermissionBridge.close();
            runtimePermissionBridge = null;
        }
        permissionService = null;
    }

    /**
     * Exposes the active permission service instance for callers and tests.
     *
     * @return the active permission service for the current plugin enable
     * @throws NullPointerException if called before the plugin has finished enabling
     */
    public PermissionService getPermissionService() {
        return Objects.requireNonNull(permissionService, "Permission service is not available");
    }

}
