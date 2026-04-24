package me.clutchy.clutchperms.paper;

import java.nio.file.Path;
import java.util.Objects;
import java.util.logging.Level;

import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;

import me.clutchy.clutchperms.common.GroupChangeListener;
import me.clutchy.clutchperms.common.GroupService;
import me.clutchy.clutchperms.common.GroupServices;
import me.clutchy.clutchperms.common.PermissionResolver;
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

    private static final String GROUPS_FILE_NAME = "groups.json";

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
     * Active group service instance for the lifecycle of this plugin enable.
     */
    private GroupService groupService;

    /**
     * Active effective permission resolver for the lifecycle of this plugin enable.
     */
    private PermissionResolver permissionResolver;

    /**
     * Permission assignment storage path for diagnostics.
     */
    private Path permissionsFile;

    /**
     * Subject metadata storage path for diagnostics.
     */
    private Path subjectsFile;

    /**
     * Group storage path for diagnostics.
     */
    private Path groupsFile;

    /**
     * Applies effective persisted assignments to online Paper players.
     */
    private PaperRuntimePermissionBridge runtimePermissionBridge;

    /**
     * Boots the persisted permission service and registers the shared Brigadier command tree.
     */
    @Override
    public void onEnable() {
        permissionsFile = getDataFolder().toPath().resolve(PERMISSIONS_FILE_NAME);
        subjectsFile = getDataFolder().toPath().resolve(SUBJECTS_FILE_NAME);
        groupsFile = getDataFolder().toPath().resolve(GROUPS_FILE_NAME);
        try {
            PermissionService storagePermissionService = loadPermissionService();
            GroupService storageGroupService = loadGroupService();
            subjectMetadataService = loadSubjectMetadataService();
            runtimePermissionBridge = new PaperRuntimePermissionBridge(this, this::getPermissionResolver);
            permissionService = observablePermissionService(storagePermissionService);
            groupService = observableGroupService(storageGroupService);
            permissionResolver = new PermissionResolver(permissionService, groupService);
        } catch (PermissionStorageException exception) {
            getLogger().log(Level.SEVERE, "Failed to load ClutchPerms storage from " + getDataFolder(), exception);
            throw new IllegalStateException("Failed to load ClutchPerms storage", exception);
        }

        PaperSubjectMetadataListener subjectMetadataListener = new PaperSubjectMetadataListener(this::getSubjectMetadataService);

        // Register the shared service so other plugins on Paper can discover it.
        registerServices();
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
        groupService = null;
        permissionResolver = null;
        permissionsFile = null;
        subjectsFile = null;
        groupsFile = null;
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
     * Exposes the active group service instance for callers and tests.
     *
     * @return the active group service for the current plugin enable
     */
    public GroupService getGroupService() {
        return Objects.requireNonNull(groupService, "Group service is not available");
    }

    /**
     * Exposes the active effective permission resolver for callers and tests.
     *
     * @return the active permission resolver for the current plugin enable
     */
    public PermissionResolver getPermissionResolver() {
        return Objects.requireNonNull(permissionResolver, "Permission resolver is not available");
    }

    /**
     * Returns status diagnostics for the shared command tree.
     *
     * @return active command status diagnostics
     */
    CommandStatusDiagnostics getStatusDiagnostics() {
        PaperRuntimePermissionBridge bridge = Objects.requireNonNull(runtimePermissionBridge, "Runtime permission bridge is not available");
        return new CommandStatusDiagnostics(formatPath(Objects.requireNonNull(permissionsFile, "Permissions file is not available")),
                formatPath(Objects.requireNonNull(subjectsFile, "Subjects file is not available")), formatPath(Objects.requireNonNull(groupsFile, "Groups file is not available")),
                bridge.status());
    }

    /**
     * Reloads persisted storage from disk for the shared reload command.
     */
    void reloadStorage() {
        PermissionService reloadedStoragePermissionService = loadPermissionService();
        GroupService reloadedStorageGroupService = loadGroupService();
        SubjectMetadataService reloadedSubjectMetadataService = loadSubjectMetadataService();
        PermissionService reloadedPermissionService = observablePermissionService(reloadedStoragePermissionService);
        GroupService reloadedGroupService = observableGroupService(reloadedStorageGroupService);
        PermissionResolver reloadedPermissionResolver = new PermissionResolver(reloadedPermissionService, reloadedGroupService);

        PermissionService oldPermissionService = permissionService;
        SubjectMetadataService oldSubjectMetadataService = subjectMetadataService;
        GroupService oldGroupService = groupService;
        PermissionResolver oldPermissionResolver = permissionResolver;
        permissionService = reloadedPermissionService;
        groupService = reloadedGroupService;
        permissionResolver = reloadedPermissionResolver;
        subjectMetadataService = reloadedSubjectMetadataService;

        getServer().getServicesManager().unregister(PermissionService.class, oldPermissionService);
        getServer().getServicesManager().unregister(SubjectMetadataService.class, oldSubjectMetadataService);
        getServer().getServicesManager().unregister(GroupService.class, oldGroupService);
        getServer().getServicesManager().unregister(PermissionResolver.class, oldPermissionResolver);
        registerServices();
    }

    /**
     * Refreshes every online player after a reload command replaces storage state.
     */
    void refreshRuntimePermissions() {
        Objects.requireNonNull(runtimePermissionBridge, "Runtime permission bridge is not available").refreshOnlinePlayers();
    }

    private PermissionService loadPermissionService() {
        return PermissionServices.jsonFile(Objects.requireNonNull(permissionsFile, "Permissions file is not available"));
    }

    private SubjectMetadataService loadSubjectMetadataService() {
        return SubjectMetadataServices.jsonFile(Objects.requireNonNull(subjectsFile, "Subjects file is not available"));
    }

    private GroupService loadGroupService() {
        return GroupServices.jsonFile(Objects.requireNonNull(groupsFile, "Groups file is not available"));
    }

    private PermissionService observablePermissionService(PermissionService storagePermissionService) {
        return PermissionServices.observing(storagePermissionService,
                Objects.requireNonNull(runtimePermissionBridge, "Runtime permission bridge is not available")::refreshSubject);
    }

    private GroupService observableGroupService(GroupService storageGroupService) {
        PaperRuntimePermissionBridge bridge = Objects.requireNonNull(runtimePermissionBridge, "Runtime permission bridge is not available");
        return GroupServices.observing(storageGroupService, new GroupChangeListener() {

            @Override
            public void subjectGroupsChanged(java.util.UUID subjectId) {
                bridge.refreshSubject(subjectId);
            }

            @Override
            public void groupsChanged() {
                bridge.refreshOnlinePlayers();
            }
        });
    }

    private void registerServices() {
        getServer().getServicesManager().register(PermissionService.class, getPermissionService(), this, ServicePriority.Normal);
        getServer().getServicesManager().register(SubjectMetadataService.class, getSubjectMetadataService(), this, ServicePriority.Normal);
        getServer().getServicesManager().register(GroupService.class, getGroupService(), this, ServicePriority.Normal);
        getServer().getServicesManager().register(PermissionResolver.class, getPermissionResolver(), this, ServicePriority.Normal);
    }

    private static String formatPath(Path path) {
        return path.toAbsolutePath().normalize().toString();
    }

}
