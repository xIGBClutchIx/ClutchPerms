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
import me.clutchy.clutchperms.common.SubjectMetadataService;
import me.clutchy.clutchperms.common.SubjectMetadataServices;
import me.clutchy.clutchperms.common.command.ClutchPermsCommands;
import me.clutchy.clutchperms.common.command.CommandStatusDiagnostics;

/**
 * Paper plugin entrypoint that exposes the shared persisted permission service and Brigadier command adapter.
 */
public class ClutchPermsPaperPlugin extends JavaPlugin {

    private static final String PERMISSIONS_FILE_NAME = "permissions.json";

    private static final String SUBJECTS_FILE_NAME = "subjects.json";

    /**
     * Health line returned by the shared status command.
     */
    public static final String STATUS_MESSAGE = ClutchPermsCommands.STATUS_MESSAGE;

    /**
     * Active permission service instance for the lifecycle of this plugin enable.
     */
    private PermissionService permissionService;

    /**
     * Active subject metadata service instance for the lifecycle of this plugin enable.
     */
    private SubjectMetadataService subjectMetadataService;

    /**
     * Permission assignment storage path for diagnostics.
     */
    private Path permissionsFile;

    /**
     * Subject metadata storage path for diagnostics.
     */
    private Path subjectsFile;

    /**
     * Applies persisted direct assignments to online Paper players.
     */
    private PaperRuntimePermissionBridge runtimePermissionBridge;

    /**
     * Boots the persisted permission service and registers the shared Brigadier command tree.
     */
    @Override
    public void onEnable() {
        permissionsFile = getDataFolder().toPath().resolve(PERMISSIONS_FILE_NAME);
        subjectsFile = getDataFolder().toPath().resolve(SUBJECTS_FILE_NAME);
        PermissionService storagePermissionService;
        try {
            storagePermissionService = PermissionServices.jsonFile(permissionsFile);
            subjectMetadataService = SubjectMetadataServices.jsonFile(subjectsFile);
        } catch (PermissionStorageException exception) {
            getLogger().log(Level.SEVERE, "Failed to load ClutchPerms storage from " + getDataFolder(), exception);
            throw new IllegalStateException("Failed to load ClutchPerms storage", exception);
        }

        runtimePermissionBridge = new PaperRuntimePermissionBridge(this, storagePermissionService);
        permissionService = PermissionServices.observing(storagePermissionService, runtimePermissionBridge::refreshSubject);
        PaperSubjectMetadataListener subjectMetadataListener = new PaperSubjectMetadataListener(subjectMetadataService);

        // Register the shared service so other plugins on Paper can discover it.
        getServer().getServicesManager().register(PermissionService.class, permissionService, this, ServicePriority.Normal);
        getServer().getServicesManager().register(SubjectMetadataService.class, subjectMetadataService, this, ServicePriority.Normal);
        getServer().getPluginManager().registerEvents(runtimePermissionBridge, this);
        getServer().getPluginManager().registerEvents(subjectMetadataListener, this);
        runtimePermissionBridge.refreshOnlinePlayers();
        subjectMetadataListener.recordOnlinePlayers(getServer().getOnlinePlayers());

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
        subjectMetadataService = null;
        permissionsFile = null;
        subjectsFile = null;
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
     * Exposes the active subject metadata service instance for callers and tests.
     *
     * @return the active subject metadata service for the current plugin enable
     * @throws NullPointerException if called before the plugin has finished enabling
     */
    public SubjectMetadataService getSubjectMetadataService() {
        return Objects.requireNonNull(subjectMetadataService, "Subject metadata service is not available");
    }

    /**
     * Returns status diagnostics for the shared command tree.
     *
     * @return active command status diagnostics
     */
    CommandStatusDiagnostics getStatusDiagnostics() {
        PaperRuntimePermissionBridge bridge = Objects.requireNonNull(runtimePermissionBridge, "Runtime permission bridge is not available");
        return new CommandStatusDiagnostics(formatPath(Objects.requireNonNull(permissionsFile, "Permissions file is not available")),
                formatPath(Objects.requireNonNull(subjectsFile, "Subjects file is not available")), bridge.status());
    }

    private static String formatPath(Path path) {
        return path.toAbsolutePath().normalize().toString();
    }

}
