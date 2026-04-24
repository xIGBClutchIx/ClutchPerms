package me.clutchy.clutchperms.paper;

import java.nio.file.Path;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.logging.Level;

import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;

import me.clutchy.clutchperms.common.command.ClutchPermsCommands;
import me.clutchy.clutchperms.common.command.CommandStatusDiagnostics;
import me.clutchy.clutchperms.common.group.GroupChangeListener;
import me.clutchy.clutchperms.common.group.GroupService;
import me.clutchy.clutchperms.common.group.GroupServices;
import me.clutchy.clutchperms.common.node.KnownPermissionNode;
import me.clutchy.clutchperms.common.node.MutablePermissionNodeRegistry;
import me.clutchy.clutchperms.common.node.PermissionNodeRegistries;
import me.clutchy.clutchperms.common.node.PermissionNodeRegistry;
import me.clutchy.clutchperms.common.node.PermissionNodeSource;
import me.clutchy.clutchperms.common.permission.PermissionResolver;
import me.clutchy.clutchperms.common.permission.PermissionService;
import me.clutchy.clutchperms.common.permission.PermissionServices;
import me.clutchy.clutchperms.common.storage.PermissionStorageException;
import me.clutchy.clutchperms.common.subject.SubjectMetadataService;
import me.clutchy.clutchperms.common.subject.SubjectMetadataServices;

/**
 * Paper plugin entrypoint that exposes the shared persisted permission service and Brigadier command adapter.
 */
public class ClutchPermsPaperPlugin extends JavaPlugin {

    private static final String PERMISSIONS_FILE_NAME = "permissions.json";

    private static final String SUBJECTS_FILE_NAME = "subjects.json";

    private static final String GROUPS_FILE_NAME = "groups.json";

    private static final String NODES_FILE_NAME = "nodes.json";

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
     * Active manual known node registry for the lifecycle of this plugin enable.
     */
    private MutablePermissionNodeRegistry manualPermissionNodeRegistry;

    /**
     * Active merged known node registry for the lifecycle of this plugin enable.
     */
    private PermissionNodeRegistry permissionNodeRegistry;

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
     * Manual known permission node registry storage path for diagnostics.
     */
    private Path nodesFile;

    /**
     * Applies effective persisted assignments to online Paper players.
     */
    private PaperRuntimePermissionBridge runtimePermissionBridge;

    /**
     * Owns Paper's experimental permission manager override when Paper accepts it.
     */
    private PaperPermissionManagerBridge permissionManagerBridge;

    /**
     * Boots the persisted permission service and registers the shared Brigadier command tree.
     */
    @Override
    public void onEnable() {
        permissionsFile = getDataFolder().toPath().resolve(PERMISSIONS_FILE_NAME);
        subjectsFile = getDataFolder().toPath().resolve(SUBJECTS_FILE_NAME);
        groupsFile = getDataFolder().toPath().resolve(GROUPS_FILE_NAME);
        nodesFile = getDataFolder().toPath().resolve(NODES_FILE_NAME);
        try {
            PermissionService storagePermissionService = loadPermissionService();
            GroupService storageGroupService = loadGroupService();
            MutablePermissionNodeRegistry storagePermissionNodeRegistry = loadPermissionNodeRegistry();
            subjectMetadataService = loadSubjectMetadataService();
            permissionManagerBridge = PaperPermissionManagerBridge.install(this);
            runtimePermissionBridge = new PaperRuntimePermissionBridge(this, this::getPermissionResolver, this::getKnownPaperPermissionNodes,
                    this::getPaperPermissionManagerStatus);
            permissionManagerBridge.setRegistryChangeListener(runtimePermissionBridge::refreshOnlinePlayers);
            manualPermissionNodeRegistry = observablePermissionNodeRegistry(storagePermissionNodeRegistry);
            permissionNodeRegistry = createPermissionNodeRegistry(manualPermissionNodeRegistry);
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
        if (permissionManagerBridge != null) {
            permissionManagerBridge.close();
            permissionManagerBridge = null;
        }
        permissionService = null;
        subjectMetadataService = null;
        groupService = null;
        manualPermissionNodeRegistry = null;
        permissionNodeRegistry = null;
        permissionResolver = null;
        permissionsFile = null;
        subjectsFile = null;
        groupsFile = null;
        nodesFile = null;
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
     * Exposes the active manual known node registry for callers and tests.
     *
     * @return the active manual known node registry for the current plugin enable
     */
    public MutablePermissionNodeRegistry getManualPermissionNodeRegistry() {
        return Objects.requireNonNull(manualPermissionNodeRegistry, "Manual permission node registry is not available");
    }

    /**
     * Exposes the active merged known node registry for callers and tests.
     *
     * @return the active known node registry for the current plugin enable
     */
    public PermissionNodeRegistry getPermissionNodeRegistry() {
        return Objects.requireNonNull(permissionNodeRegistry, "Permission node registry is not available");
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
                formatPath(Objects.requireNonNull(nodesFile, "Known nodes file is not available")), bridge.status());
    }

    /**
     * Reloads persisted storage from disk for the shared reload command.
     */
    void reloadStorage() {
        PermissionService reloadedStoragePermissionService = loadPermissionService();
        GroupService reloadedStorageGroupService = loadGroupService();
        MutablePermissionNodeRegistry reloadedStoragePermissionNodeRegistry = loadPermissionNodeRegistry();
        SubjectMetadataService reloadedSubjectMetadataService = loadSubjectMetadataService();
        PermissionService reloadedPermissionService = observablePermissionService(reloadedStoragePermissionService);
        GroupService reloadedGroupService = observableGroupService(reloadedStorageGroupService);
        MutablePermissionNodeRegistry reloadedManualPermissionNodeRegistry = observablePermissionNodeRegistry(reloadedStoragePermissionNodeRegistry);
        PermissionNodeRegistry reloadedPermissionNodeRegistry = createPermissionNodeRegistry(reloadedManualPermissionNodeRegistry);
        PermissionResolver reloadedPermissionResolver = new PermissionResolver(reloadedPermissionService, reloadedGroupService);

        PermissionService oldPermissionService = permissionService;
        SubjectMetadataService oldSubjectMetadataService = subjectMetadataService;
        GroupService oldGroupService = groupService;
        MutablePermissionNodeRegistry oldManualPermissionNodeRegistry = manualPermissionNodeRegistry;
        PermissionNodeRegistry oldPermissionNodeRegistry = permissionNodeRegistry;
        PermissionResolver oldPermissionResolver = permissionResolver;
        permissionService = reloadedPermissionService;
        groupService = reloadedGroupService;
        manualPermissionNodeRegistry = reloadedManualPermissionNodeRegistry;
        permissionNodeRegistry = reloadedPermissionNodeRegistry;
        permissionResolver = reloadedPermissionResolver;
        subjectMetadataService = reloadedSubjectMetadataService;

        getServer().getServicesManager().unregister(PermissionService.class, oldPermissionService);
        getServer().getServicesManager().unregister(SubjectMetadataService.class, oldSubjectMetadataService);
        getServer().getServicesManager().unregister(GroupService.class, oldGroupService);
        getServer().getServicesManager().unregister(MutablePermissionNodeRegistry.class, oldManualPermissionNodeRegistry);
        getServer().getServicesManager().unregister(PermissionNodeRegistry.class, oldPermissionNodeRegistry);
        getServer().getServicesManager().unregister(PermissionResolver.class, oldPermissionResolver);
        registerServices();
    }

    /**
     * Validates persisted storage from disk without replacing active services or runtime state.
     */
    void validateStorage() {
        loadPermissionService();
        loadSubjectMetadataService();
        loadGroupService();
        loadPermissionNodeRegistry();
    }

    /**
     * Refreshes every online player after a reload command replaces storage state.
     */
    void refreshRuntimePermissions() {
        Objects.requireNonNull(runtimePermissionBridge, "Runtime permission bridge is not available").refreshOnlinePlayers();
    }

    /**
     * Returns whether Paper accepted the experimental permission manager override.
     *
     * @return {@code true} when the override is active
     */
    boolean isPaperPermissionManagerOverrideActive() {
        return Objects.requireNonNull(permissionManagerBridge, "Permission manager bridge is not available").isOverrideActive();
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

    private MutablePermissionNodeRegistry loadPermissionNodeRegistry() {
        return PermissionNodeRegistries.jsonFile(Objects.requireNonNull(nodesFile, "Known nodes file is not available"));
    }

    private Set<String> getKnownPaperPermissionNodes() {
        Set<String> nodes = new TreeSet<>();
        Objects.requireNonNull(permissionNodeRegistry, "Permission node registry is not available").getKnownNodes().stream().map(KnownPermissionNode::node).forEach(nodes::add);
        return Set.copyOf(nodes);
    }

    private Set<String> getKnownPaperPlatformPermissionNodes() {
        return Objects.requireNonNull(permissionManagerBridge, "Permission manager bridge is not available").knownPermissionNodes();
    }

    private String getPaperPermissionManagerStatus() {
        return Objects.requireNonNull(permissionManagerBridge, "Permission manager bridge is not available").status();
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

    private MutablePermissionNodeRegistry observablePermissionNodeRegistry(MutablePermissionNodeRegistry storagePermissionNodeRegistry) {
        return PermissionNodeRegistries.observing(storagePermissionNodeRegistry,
                Objects.requireNonNull(runtimePermissionBridge, "Runtime permission bridge is not available")::refreshOnlinePlayers);
    }

    private PermissionNodeRegistry createPermissionNodeRegistry(MutablePermissionNodeRegistry manualRegistry) {
        return PermissionNodeRegistries.composite(PermissionNodeRegistries.builtIn(), manualRegistry,
                PermissionNodeRegistries.supplying(PermissionNodeSource.PLATFORM, this::getKnownPaperPlatformPermissionNodes));
    }

    private void registerServices() {
        getServer().getServicesManager().register(PermissionService.class, getPermissionService(), this, ServicePriority.Normal);
        getServer().getServicesManager().register(SubjectMetadataService.class, getSubjectMetadataService(), this, ServicePriority.Normal);
        getServer().getServicesManager().register(GroupService.class, getGroupService(), this, ServicePriority.Normal);
        getServer().getServicesManager().register(MutablePermissionNodeRegistry.class, getManualPermissionNodeRegistry(), this, ServicePriority.Normal);
        getServer().getServicesManager().register(PermissionNodeRegistry.class, getPermissionNodeRegistry(), this, ServicePriority.Normal);
        getServer().getServicesManager().register(PermissionResolver.class, getPermissionResolver(), this, ServicePriority.Normal);
    }

    private static String formatPath(Path path) {
        return path.toAbsolutePath().normalize().toString();
    }

}
