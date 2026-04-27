package me.clutchy.clutchperms.paper;

import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.function.UnaryOperator;
import java.util.logging.Level;

import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;

import me.clutchy.clutchperms.common.command.ClutchPermsCommands;
import me.clutchy.clutchperms.common.command.CommandStatusDiagnostics;
import me.clutchy.clutchperms.common.config.ClutchPermsConfig;
import me.clutchy.clutchperms.common.display.DisplayResolver;
import me.clutchy.clutchperms.common.group.GroupService;
import me.clutchy.clutchperms.common.node.KnownPermissionNode;
import me.clutchy.clutchperms.common.node.MutablePermissionNodeRegistry;
import me.clutchy.clutchperms.common.node.PermissionNodeRegistries;
import me.clutchy.clutchperms.common.node.PermissionNodeRegistry;
import me.clutchy.clutchperms.common.node.PermissionNodeSource;
import me.clutchy.clutchperms.common.permission.PermissionResolver;
import me.clutchy.clutchperms.common.permission.PermissionService;
import me.clutchy.clutchperms.common.runtime.ClutchPermsRuntime;
import me.clutchy.clutchperms.common.runtime.ClutchPermsRuntimeHooks;
import me.clutchy.clutchperms.common.runtime.ClutchPermsRuntimeServices;
import me.clutchy.clutchperms.common.runtime.ClutchPermsStoragePaths;
import me.clutchy.clutchperms.common.storage.PermissionStorageException;
import me.clutchy.clutchperms.common.storage.SqliteDependencyMode;
import me.clutchy.clutchperms.common.storage.StorageBackupService;
import me.clutchy.clutchperms.common.storage.StorageFileKind;
import me.clutchy.clutchperms.common.subject.SubjectMetadataService;

/**
 * Paper plugin entrypoint that exposes the shared persisted permission service and Brigadier command adapter.
 */
public class ClutchPermsPaperPlugin extends JavaPlugin {

    /**
     * Health line returned by the shared status command.
     */
    public static final String STATUS_MESSAGE = ClutchPermsCommands.STATUS_MESSAGE;

    /**
     * Active shared storage runtime for this plugin enable.
     */
    private ClutchPermsRuntime runtime;

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
        permissionManagerBridge = PaperPermissionManagerBridge.install(this);
        runtimePermissionBridge = new PaperRuntimePermissionBridge(this, this::getPermissionResolver, this::getKnownPaperPermissionNodes, this::getPaperPermissionManagerStatus);
        permissionManagerBridge.setRegistryChangeListener(runtimePermissionBridge::refreshOnlinePlayers);
        runtime = new ClutchPermsRuntime(ClutchPermsStoragePaths.inDirectory(getDataFolder().toPath()),
                () -> PermissionNodeRegistries.supplying(PermissionNodeSource.PLATFORM, this::getKnownPaperPlatformPermissionNodes),
                new ClutchPermsRuntimeHooks(this::refreshRuntimeSubject, this::refreshRuntimePermissions), SqliteDependencyMode.PAPER_BUILT_IN_SQLITE);
        logStorageLoadStart();
        try {
            runtime.reload();
            logStorageLoadSuccess();
        } catch (PermissionStorageException exception) {
            getLogger().log(Level.SEVERE, "Failed to load ClutchPerms storage from " + getDataFolder(), exception);
            closeRuntimeBridges();
            runtime = null;
            throw new IllegalStateException("Failed to load ClutchPerms storage", exception);
        }

        PaperSubjectMetadataListener subjectMetadataListener = new PaperSubjectMetadataListener(this::getSubjectMetadataService);

        // Register the shared service so other plugins on Paper can discover it.
        registerServices();
        getServer().getPluginManager().registerEvents(runtimePermissionBridge, this);
        getServer().getPluginManager().registerEvents(subjectMetadataListener, this);
        getServer().getPluginManager().registerEvents(new PaperChatDisplayListener(this::getClutchPermsConfig, this::getDisplayResolver), this);
        runtimePermissionBridge.refreshOnlinePlayers();
        subjectMetadataListener.recordOnlinePlayers(getServer().getOnlinePlayers());

        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event -> ClutchPermsCommands.ROOT_LITERALS
                .forEach(rootLiteral -> event.registrar().register(PaperClutchPermsCommand.create(this, rootLiteral), "Manages ClutchPerms direct permissions")));
    }

    /**
     * Unregisters any services exposed by this plugin when the server disables it.
     */
    @Override
    public void onDisable() {
        getServer().getServicesManager().unregisterAll(this);
        closeRuntimeBridges();
        if (runtime != null) {
            runtime.clear();
            runtime = null;
        }
    }

    /**
     * Exposes the active permission service instance for callers and tests.
     *
     * @return the active permission service for the current plugin enable
     * @throws NullPointerException if called before the plugin has finished enabling
     */
    public PermissionService getPermissionService() {
        return getRuntime().permissionService();
    }

    /**
     * Exposes the active subject metadata service instance for callers and tests.
     *
     * @return the active subject metadata service for the current plugin enable
     * @throws NullPointerException if called before the plugin has finished enabling
     */
    public SubjectMetadataService getSubjectMetadataService() {
        return getRuntime().subjectMetadataService();
    }

    /**
     * Exposes the active group service instance for callers and tests.
     *
     * @return the active group service for the current plugin enable
     */
    public GroupService getGroupService() {
        return getRuntime().groupService();
    }

    /**
     * Exposes the active manual known node registry for callers and tests.
     *
     * @return the active manual known node registry for the current plugin enable
     */
    public MutablePermissionNodeRegistry getManualPermissionNodeRegistry() {
        return getRuntime().manualPermissionNodeRegistry();
    }

    /**
     * Exposes the active merged known node registry for callers and tests.
     *
     * @return the active known node registry for the current plugin enable
     */
    public PermissionNodeRegistry getPermissionNodeRegistry() {
        return getRuntime().permissionNodeRegistry();
    }

    /**
     * Exposes the active effective permission resolver for callers and tests.
     *
     * @return the active permission resolver for the current plugin enable
     */
    public PermissionResolver getPermissionResolver() {
        return getRuntime().permissionResolver();
    }

    /**
     * Exposes the active display resolver for Paper chat formatting.
     *
     * @return active display resolver
     */
    DisplayResolver getDisplayResolver() {
        return getRuntime().displayResolver();
    }

    /**
     * Exposes the active runtime config for command adapters and tests.
     *
     * @return active runtime config
     */
    ClutchPermsConfig getClutchPermsConfig() {
        return getRuntime().config();
    }

    /**
     * Returns status diagnostics for the shared command tree.
     *
     * @return active command status diagnostics
     */
    CommandStatusDiagnostics getStatusDiagnostics() {
        PaperRuntimePermissionBridge bridge = Objects.requireNonNull(runtimePermissionBridge, "Runtime permission bridge is not available");
        return getRuntime().statusDiagnostics(bridge.status());
    }

    /**
     * Reloads persisted storage from disk for the shared reload command.
     */
    void reloadStorage() {
        logStorageLoadStart();
        try {
            ClutchPermsRuntimeServices previousServices = getRuntime().reload();
            unregisterServices(previousServices);
            registerServices();
            logStorageLoadSuccess();
        } catch (RuntimeException exception) {
            getLogger().log(Level.SEVERE, "Failed to load ClutchPerms storage from " + getDataFolder(), exception);
            throw exception;
        }
    }

    /**
     * Updates runtime config and replaces active storage services through the shared runtime.
     *
     * @param updater config updater
     */
    void updateConfig(UnaryOperator<ClutchPermsConfig> updater) {
        logStorageLoadStart();
        try {
            ClutchPermsRuntimeServices previousServices = getRuntime().updateConfig(updater);
            if (previousServices != null) {
                unregisterServices(previousServices);
                registerServices();
            }
            logStorageLoadSuccess();
        } catch (RuntimeException exception) {
            getLogger().log(Level.SEVERE, "Failed to update ClutchPerms config from " + getDataFolder(), exception);
            throw exception;
        }
    }

    /**
     * Validates persisted storage from disk without replacing active services or runtime state.
     */
    void validateStorage() {
        getRuntime().validate();
    }

    /**
     * Returns the backup service used by shared backup commands.
     *
     * @return active storage backup service
     */
    StorageBackupService getStorageBackupService() {
        return getRuntime().storageBackupService();
    }

    /**
     * Restores one database backup after closing the active SQLite pool.
     *
     * @param kind selected storage kind
     * @param backupFileName backup filename
     */
    void restoreBackup(StorageFileKind kind, String backupFileName) {
        logStorageLoadStart();
        try {
            ClutchPermsRuntimeServices previousServices = getRuntime().restoreBackup(kind, backupFileName);
            unregisterServices(previousServices);
            registerServices();
            refreshRuntimePermissions();
            logStorageLoadSuccess();
        } catch (RuntimeException exception) {
            getLogger().log(Level.SEVERE, "Failed to restore ClutchPerms database backup " + backupFileName + " from " + getDataFolder(), exception);
            throw exception;
        }
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

    private Set<String> getKnownPaperPermissionNodes() {
        Set<String> nodes = new TreeSet<>();
        getPermissionNodeRegistry().getKnownNodes().stream().map(KnownPermissionNode::node).forEach(nodes::add);
        return Set.copyOf(nodes);
    }

    private Set<String> getKnownPaperPlatformPermissionNodes() {
        return Objects.requireNonNull(permissionManagerBridge, "Permission manager bridge is not available").knownPermissionNodes();
    }

    private String getPaperPermissionManagerStatus() {
        return Objects.requireNonNull(permissionManagerBridge, "Permission manager bridge is not available").status();
    }

    private ClutchPermsRuntime getRuntime() {
        return Objects.requireNonNull(runtime, "ClutchPerms runtime is not available");
    }

    private void refreshRuntimeSubject(UUID subjectId) {
        PaperRuntimePermissionBridge bridge = runtimePermissionBridge;
        if (bridge != null) {
            bridge.refreshSubject(subjectId);
        }
    }

    private void registerServices() {
        getServer().getServicesManager().register(PermissionService.class, getPermissionService(), this, ServicePriority.Normal);
        getServer().getServicesManager().register(SubjectMetadataService.class, getSubjectMetadataService(), this, ServicePriority.Normal);
        getServer().getServicesManager().register(GroupService.class, getGroupService(), this, ServicePriority.Normal);
        getServer().getServicesManager().register(MutablePermissionNodeRegistry.class, getManualPermissionNodeRegistry(), this, ServicePriority.Normal);
        getServer().getServicesManager().register(PermissionNodeRegistry.class, getPermissionNodeRegistry(), this, ServicePriority.Normal);
        getServer().getServicesManager().register(PermissionResolver.class, getPermissionResolver(), this, ServicePriority.Normal);
    }

    private void unregisterServices(ClutchPermsRuntimeServices services) {
        if (services == null) {
            return;
        }

        getServer().getServicesManager().unregister(PermissionService.class, services.permissionService());
        getServer().getServicesManager().unregister(SubjectMetadataService.class, services.subjectMetadataService());
        getServer().getServicesManager().unregister(GroupService.class, services.groupService());
        getServer().getServicesManager().unregister(MutablePermissionNodeRegistry.class, services.manualPermissionNodeRegistry());
        getServer().getServicesManager().unregister(PermissionNodeRegistry.class, services.permissionNodeRegistry());
        getServer().getServicesManager().unregister(PermissionResolver.class, services.permissionResolver());
    }

    private void closeRuntimeBridges() {
        if (runtimePermissionBridge != null) {
            runtimePermissionBridge.close();
            runtimePermissionBridge = null;
        }
        if (permissionManagerBridge != null) {
            permissionManagerBridge.close();
            permissionManagerBridge = null;
        }
    }

    private void logStorageLoadStart() {
        getLogger().fine("ClutchPerms database file: " + getRuntime().storagePaths().databaseFile());
    }

    private void logStorageLoadSuccess() {
        getLogger().info("Loaded ClutchPerms storage from " + getRuntime().storagePaths().storageRoot() + ": " + getSubjectMetadataService().getSubjects().size()
                + " known subjects, " + getGroupService().getGroups().size() + " groups, " + getManualPermissionNodeRegistry().getKnownNodes().size() + " manual known nodes, "
                + getPermissionNodeRegistry().getKnownNodes().size() + " total known nodes.");
    }

}
