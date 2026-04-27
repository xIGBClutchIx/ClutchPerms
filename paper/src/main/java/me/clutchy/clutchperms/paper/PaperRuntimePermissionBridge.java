package me.clutchy.clutchperms.paper;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.function.Supplier;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.server.PluginEnableEvent;
import org.bukkit.permissions.PermissionAttachment;

import me.clutchy.clutchperms.common.permission.PermissionResolver;
import me.clutchy.clutchperms.common.permission.PermissionValue;

/**
 * Applies effective ClutchPerms assignments to online Paper players through plugin-owned permission attachments.
 */
final class PaperRuntimePermissionBridge implements Listener, AutoCloseable {

    private final ClutchPermsPaperPlugin plugin;

    private final Supplier<PermissionResolver> permissionResolverSupplier;

    private final Supplier<Set<String>> knownPermissionNodesSupplier;

    private final Supplier<String> permissionManagerStatusSupplier;

    private final Map<UUID, PermissionAttachment> attachments = new HashMap<>();

    PaperRuntimePermissionBridge(ClutchPermsPaperPlugin plugin, Supplier<PermissionResolver> permissionResolverSupplier, Supplier<Set<String>> knownPermissionNodesSupplier,
            Supplier<String> permissionManagerStatusSupplier) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.permissionResolverSupplier = Objects.requireNonNull(permissionResolverSupplier, "permissionResolverSupplier");
        this.knownPermissionNodesSupplier = Objects.requireNonNull(knownPermissionNodesSupplier, "knownPermissionNodesSupplier");
        this.permissionManagerStatusSupplier = Objects.requireNonNull(permissionManagerStatusSupplier, "permissionManagerStatusSupplier");
    }

    /**
     * Refreshes every player currently online.
     */
    void refreshOnlinePlayers() {
        plugin.getServer().getOnlinePlayers().forEach(this::refreshPlayer);
    }

    /**
     * Describes the active Paper runtime permission bridge state for diagnostics.
     *
     * @return human-readable runtime bridge status
     */
    String status() {
        return "Paper permission attachment bridge active with " + attachments.size() + " attached players; " + permissionManagerStatusSupplier.get();
    }

    /**
     * Refreshes one online subject after a persisted permission mutation.
     *
     * @param subjectId subject UUID that changed
     */
    void refreshSubject(UUID subjectId) {
        Objects.requireNonNull(subjectId, "subjectId");
        if (!plugin.isEnabled()) {
            return;
        }

        if (!plugin.getServer().isPrimaryThread()) {
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                if (plugin.isEnabled()) {
                    refreshSubjectNow(subjectId);
                }
            });
            return;
        }

        refreshSubjectNow(subjectId);
    }

    /**
     * Applies stored permissions when a player joins.
     *
     * @param event player join event
     */
    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        refreshPlayer(event.getPlayer());
    }

    /**
     * Removes ClutchPerms-owned permissions when a player quits.
     *
     * @param event player quit event
     */
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        detachPlayer(event.getPlayer());
    }

    /**
     * Refreshes wildcard expansions after newly enabled plugins register additional Paper permission nodes.
     *
     * @param event plugin enable event
     */
    @EventHandler
    public void onPluginEnable(PluginEnableEvent event) {
        refreshOnlinePlayers();
    }

    /**
     * Removes every active ClutchPerms-owned attachment.
     */
    @Override
    public void close() {
        attachments.keySet().stream().toList().forEach(this::detachSubject);
    }

    private void refreshSubjectNow(UUID subjectId) {
        Player player = plugin.getServer().getPlayer(subjectId);
        if (player == null || !player.isOnline()) {
            return;
        }

        refreshPlayer(player);
    }

    private void refreshPlayer(Player player) {
        UUID subjectId = player.getUniqueId();
        detachPlayer(player);

        PermissionResolver permissionResolver = permissionResolverSupplier.get();
        Map<String, PermissionValue> permissions = new TreeMap<>(permissionResolver.getEffectivePermissions(subjectId));
        for (String knownPermissionNode : knownPermissionNodesSupplier.get()) {
            PermissionValue value = permissionResolver.resolve(subjectId, knownPermissionNode).value();
            if (value != PermissionValue.UNSET) {
                permissions.put(knownPermissionNode, value);
            }
        }

        if (!permissions.isEmpty()) {
            PermissionAttachment attachment = player.addAttachment(plugin);
            permissions.forEach((node, value) -> {
                if (value != PermissionValue.UNSET) {
                    attachment.setPermission(node, value == PermissionValue.TRUE);
                }
            });
            attachments.put(subjectId, attachment);
        }

        player.recalculatePermissions();
        player.updateCommands();
    }

    private void detachSubject(UUID subjectId) {
        PermissionAttachment attachment = attachments.remove(subjectId);
        if (attachment == null) {
            return;
        }

        try {
            attachment.remove();
        } catch (IllegalArgumentException exception) {
            // The attachment may already have been removed by the server while disconnecting.
        }
    }

    private void detachPlayer(Player player) {
        detachSubject(player.getUniqueId());
        player.recalculatePermissions();
    }
}
