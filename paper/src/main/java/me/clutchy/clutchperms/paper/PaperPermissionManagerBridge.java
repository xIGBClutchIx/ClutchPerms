package me.clutchy.clutchperms.paper;

import java.util.Collections;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

import org.bukkit.plugin.PluginManager;

import me.clutchy.clutchperms.common.permission.PermissionNodes;

/**
 * Installs Paper's experimental permission manager override and falls back to the platform registry when unavailable.
 */
final class PaperPermissionManagerBridge implements AutoCloseable {

    private final ClutchPermsPaperPlugin plugin;

    private final PaperClutchPermsPermissionManager permissionManager;

    private final boolean overrideActive;

    private PaperPermissionManagerBridge(ClutchPermsPaperPlugin plugin, PaperClutchPermsPermissionManager permissionManager, boolean overrideActive) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.permissionManager = Objects.requireNonNull(permissionManager, "permissionManager");
        this.overrideActive = overrideActive;
    }

    /**
     * Attempts to install ClutchPerms as Paper's active permission manager.
     *
     * @param plugin Paper plugin instance
     * @return active bridge, possibly in fallback mode
     */
    static PaperPermissionManagerBridge install(ClutchPermsPaperPlugin plugin) {
        Objects.requireNonNull(plugin, "plugin");
        PluginManager pluginManager = plugin.getServer().getPluginManager();
        PaperClutchPermsPermissionManager clutchPermsPermissionManager = PaperClutchPermsPermissionManager.seededFrom(pluginManager);
        try {
            pluginManager.overridePermissionManager(plugin, clutchPermsPermissionManager);
            return new PaperPermissionManagerBridge(plugin, clutchPermsPermissionManager, true);
        } catch (RuntimeException | LinkageError exception) {
            plugin.getLogger().warning("Paper permission manager override is unavailable (" + exception.getClass().getSimpleName() + ": " + exception.getMessage()
                    + "); falling back to registry snapshots");
            return new PaperPermissionManagerBridge(plugin, clutchPermsPermissionManager, false);
        }
    }

    /**
     * Installs a callback fired after successful permission registry mutations when the override is active.
     *
     * @param registryChangeListener callback to run after permission registry changes
     */
    void setRegistryChangeListener(Runnable registryChangeListener) {
        permissionManager.setRegistryChangeListener(registryChangeListener);
    }

    /**
     * Lists exact permission nodes currently known to the Paper registry.
     *
     * @return deterministic exact permission node snapshot
     */
    Set<String> knownPermissionNodes() {
        if (overrideActive) {
            return permissionManager.knownPermissionNodes();
        }

        Set<String> nodes = new TreeSet<>();
        plugin.getServer().getPluginManager().getPermissions().forEach(permission -> {
            String permissionName = permission.getName().toLowerCase(Locale.ROOT);
            try {
                String normalizedNode = PermissionNodes.normalize(permissionName);
                if (!PermissionNodes.isWildcard(normalizedNode)) {
                    nodes.add(normalizedNode);
                }
            } catch (IllegalArgumentException exception) {
                // Ignore external registry names that ClutchPerms would not accept as permission nodes.
            }
        });
        return Collections.unmodifiableSet(nodes);
    }

    /**
     * Describes the Paper permission manager bridge state for diagnostics.
     *
     * @return human-readable manager status
     */
    String status() {
        if (overrideActive) {
            return permissionManager.status();
        }
        return "permission manager override fallback with " + knownPermissionNodes().size() + " known permission nodes";
    }

    /**
     * Returns whether Paper accepted the ClutchPerms permission manager override.
     *
     * @return {@code true} when the override is active
     */
    boolean isOverrideActive() {
        return overrideActive;
    }

    /**
     * Restores Paper's default permission manager when ClutchPerms owns the override.
     */
    @Override
    public void close() {
        if (!overrideActive) {
            return;
        }

        try {
            plugin.getServer().getPluginManager().overridePermissionManager(plugin, null);
        } catch (RuntimeException | LinkageError exception) {
            plugin.getLogger().warning("Failed to restore Paper's default permission manager (" + exception.getClass().getSimpleName() + ": " + exception.getMessage() + ")");
        }
    }
}
