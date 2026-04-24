package me.clutchy.clutchperms.paper;

import java.nio.file.Path;
import java.util.Objects;
import java.util.logging.Level;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

import me.clutchy.clutchperms.common.PermissionService;
import me.clutchy.clutchperms.common.PermissionServices;
import me.clutchy.clutchperms.common.PermissionStorageException;

/**
 * Paper plugin entrypoint that exposes the shared persisted permission service and a simple diagnostic command.
 */
public class ClutchPermsPaperPlugin extends JavaPlugin implements CommandExecutor {

    private static final String PERMISSIONS_FILE_NAME = "permissions.json";

    /**
     * Diagnostic message returned by the bootstrap command while the project is still in its early scaffold phase.
     */
    public static final String STATUS_MESSAGE = "ClutchPerms is running with a persisted permission service.";

    /**
     * Active permission service instance for the lifecycle of this plugin enable.
     */
    private PermissionService permissionService;

    /**
     * Boots the persisted permission service and registers the diagnostic command.
     */
    @Override
    public void onEnable() {
        Path permissionsFile = getDataFolder().toPath().resolve(PERMISSIONS_FILE_NAME);
        try {
            permissionService = PermissionServices.jsonFile(permissionsFile);
        } catch (PermissionStorageException exception) {
            getLogger().log(Level.SEVERE, "Failed to load ClutchPerms permissions from " + permissionsFile, exception);
            throw new IllegalStateException("Failed to load ClutchPerms permissions", exception);
        }

        // Register the shared service so other plugins on Paper can discover it.
        getServer().getServicesManager().register(PermissionService.class, permissionService, this, ServicePriority.Normal);

        // Fail fast if plugin.yml and the bootstrap code drift out of sync.
        PluginCommand clutchPermsCommand = Objects.requireNonNull(getCommand("clutchperms"), "Missing clutchperms command definition");
        clutchPermsCommand.setExecutor(this);
    }

    /**
     * Unregisters any services exposed by this plugin when the server disables it.
     */
    @Override
    public void onDisable() {
        getServer().getServicesManager().unregisterAll(this);
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

    /**
     * Handles the diagnostic bootstrap command.
     *
     * @param sender command sender receiving the response
     * @param command command being executed
     * @param label alias used to execute the command
     * @param args raw command arguments
     * @return always {@code true} because the command is fully handled here
     */
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        sender.sendMessage(STATUS_MESSAGE);
        return true;
    }
}
