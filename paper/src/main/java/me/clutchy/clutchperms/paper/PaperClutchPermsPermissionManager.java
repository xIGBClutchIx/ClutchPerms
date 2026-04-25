package me.clutchy.clutchperms.paper;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import org.bukkit.permissions.Permissible;
import org.bukkit.permissions.Permission;

import io.papermc.paper.plugin.PermissionManager;

import me.clutchy.clutchperms.common.permission.PermissionNodes;

/**
 * Paper permission registry owned by ClutchPerms when Paper's experimental permission manager override is active.
 */
final class PaperClutchPermsPermissionManager implements PermissionManager {

    private final Map<String, Permission> permissions = new TreeMap<>();

    private final Map<Boolean, Set<Permission>> defaultPermissions = Map.of(Boolean.FALSE, new LinkedHashSet<>(), Boolean.TRUE, new LinkedHashSet<>());

    private final Map<String, Set<Permissible>> permissionSubscriptions = new HashMap<>();

    private final Map<Boolean, Set<Permissible>> defaultSubscriptions = Map.of(Boolean.FALSE, new LinkedHashSet<>(), Boolean.TRUE, new LinkedHashSet<>());

    private Runnable registryChangeListener = () -> {
    };

    private PaperClutchPermsPermissionManager() {
    }

    /**
     * Creates a ClutchPerms permission manager seeded from the currently active Paper permission manager.
     *
     * @param source current Paper permission manager
     * @return seeded ClutchPerms manager
     */
    static PaperClutchPermsPermissionManager seededFrom(PermissionManager source) {
        Objects.requireNonNull(source, "source");
        PaperClutchPermsPermissionManager manager = new PaperClutchPermsPermissionManager();
        source.getPermissions().forEach(manager::putSeedPermission);
        source.getDefaultPermissions(false).forEach(permission -> manager.putSeedDefaultPermission(false, permission));
        source.getDefaultPermissions(true).forEach(permission -> manager.putSeedDefaultPermission(true, permission));
        manager.permissions.keySet()
                .forEach(permissionName -> manager.permissionSubscriptions.put(permissionName, new LinkedHashSet<>(source.getPermissionSubscriptions(permissionName))));
        manager.defaultSubscriptions.get(false).addAll(source.getDefaultPermSubscriptions(false));
        manager.defaultSubscriptions.get(true).addAll(source.getDefaultPermSubscriptions(true));
        return manager;
    }

    /**
     * Installs a callback fired after successful registry mutations.
     *
     * @param registryChangeListener callback to run after the permission registry changes
     */
    void setRegistryChangeListener(Runnable registryChangeListener) {
        this.registryChangeListener = Objects.requireNonNull(registryChangeListener, "registryChangeListener");
    }

    /**
     * Removes the registry change callback installed by the plugin lifecycle.
     */
    void clearRegistryChangeListener() {
        registryChangeListener = () -> {
        };
    }

    /**
     * Lists exact registered permission names known to Paper.
     *
     * @return deterministic exact permission node snapshot
     */
    Set<String> knownPermissionNodes() {
        Set<String> nodes = new TreeSet<>();
        permissions.keySet().forEach(permissionName -> {
            String normalizedNode = normalizeKnownPermissionNode(permissionName);
            if (normalizedNode != null) {
                nodes.add(normalizedNode);
            }
        });
        return Collections.unmodifiableSet(nodes);
    }

    /**
     * Describes the active registry state for diagnostics.
     *
     * @return human-readable manager status
     */
    String status() {
        return "permission manager override active with " + knownPermissionNodes().size() + " known permission nodes";
    }

    @Override
    public Permission getPermission(String name) {
        return permissions.get(normalizePermissionName(name));
    }

    @Override
    public void addPermission(Permission permission) {
        Objects.requireNonNull(permission, "permission");
        String permissionName = normalizePermissionName(permission.getName());
        if (permissions.containsKey(permissionName)) {
            throw new IllegalArgumentException("Permission " + permission.getName() + " is already defined");
        }

        permissions.put(permissionName, permission);
        recalculatePermissionDefaults(permission, false);
        dirtyPermissionSubscribers(permissionName);
        registryChanged();
    }

    @Override
    public void addPermissions(List<Permission> newPermissions) {
        Objects.requireNonNull(newPermissions, "newPermissions");
        Map<String, Permission> normalizedPermissions = new TreeMap<>();
        for (Permission permission : newPermissions) {
            Objects.requireNonNull(permission, "permission");
            String permissionName = normalizePermissionName(permission.getName());
            if (permissions.containsKey(permissionName) || normalizedPermissions.put(permissionName, permission) != null) {
                throw new IllegalArgumentException("Permission " + permission.getName() + " is already defined");
            }
        }

        permissions.putAll(normalizedPermissions);
        normalizedPermissions.values().forEach(permission -> recalculatePermissionDefaults(permission, false));
        normalizedPermissions.keySet().forEach(this::dirtyPermissionSubscribers);
        registryChanged();
    }

    @Override
    public void removePermission(Permission permission) {
        Objects.requireNonNull(permission, "permission");
        removePermission(permission.getName());
    }

    @Override
    public void removePermission(String name) {
        String permissionName = normalizePermissionName(name);
        Permission removedPermission = permissions.remove(permissionName);
        if (removedPermission == null) {
            return;
        }

        defaultPermissions.values().forEach(defaultSet -> defaultSet.remove(removedPermission));
        dirtyPermissionSubscribers(permissionName);
        dirtyDefaultSubscribers();
        registryChanged();
    }

    @Override
    public Set<Permission> getDefaultPermissions(boolean op) {
        return Set.copyOf(defaultPermissions.get(op));
    }

    @Override
    public void recalculatePermissionDefaults(Permission permission) {
        recalculatePermissionDefaults(permission, true);
    }

    private void recalculatePermissionDefaults(Permission permission, boolean notifyRegistryChanged) {
        Objects.requireNonNull(permission, "permission");
        String permissionName = normalizePermissionName(permission.getName());
        Permission registeredPermission = permissions.get(permissionName);
        if (registeredPermission == null) {
            return;
        }

        defaultPermissions.values().forEach(defaultSet -> defaultSet.remove(registeredPermission));
        if (registeredPermission.getDefault().getValue(false)) {
            defaultPermissions.get(false).add(registeredPermission);
        }
        if (registeredPermission.getDefault().getValue(true)) {
            defaultPermissions.get(true).add(registeredPermission);
        }

        dirtyPermissionSubscribers(permissionName);
        dirtyDefaultSubscribers();
        if (notifyRegistryChanged) {
            registryChanged();
        }
    }

    @Override
    public void subscribeToPermission(String permission, Permissible permissible) {
        Objects.requireNonNull(permissible, "permissible");
        permissionSubscriptions.computeIfAbsent(normalizePermissionName(permission), ignored -> new LinkedHashSet<>()).add(permissible);
    }

    @Override
    public void unsubscribeFromPermission(String permission, Permissible permissible) {
        Objects.requireNonNull(permissible, "permissible");
        Set<Permissible> permissibles = permissionSubscriptions.get(normalizePermissionName(permission));
        if (permissibles == null) {
            return;
        }
        permissibles.remove(permissible);
        if (permissibles.isEmpty()) {
            permissionSubscriptions.remove(normalizePermissionName(permission));
        }
    }

    @Override
    public Set<Permissible> getPermissionSubscriptions(String permission) {
        return Set.copyOf(permissionSubscriptions.getOrDefault(normalizePermissionName(permission), Set.of()));
    }

    @Override
    public void subscribeToDefaultPerms(boolean op, Permissible permissible) {
        Objects.requireNonNull(permissible, "permissible");
        defaultSubscriptions.get(op).add(permissible);
    }

    @Override
    public void unsubscribeFromDefaultPerms(boolean op, Permissible permissible) {
        Objects.requireNonNull(permissible, "permissible");
        defaultSubscriptions.get(op).remove(permissible);
    }

    @Override
    public Set<Permissible> getDefaultPermSubscriptions(boolean op) {
        return Set.copyOf(defaultSubscriptions.get(op));
    }

    @Override
    public Set<Permission> getPermissions() {
        return Collections.unmodifiableSet(new LinkedHashSet<>(permissions.values()));
    }

    @Override
    public void clearPermissions() {
        if (permissions.isEmpty()) {
            return;
        }

        Set<Permissible> permissiblesToRecalculate = new LinkedHashSet<>();
        permissionSubscriptions.values().forEach(permissiblesToRecalculate::addAll);
        defaultSubscriptions.values().forEach(permissiblesToRecalculate::addAll);
        permissions.clear();
        defaultPermissions.values().forEach(Set::clear);
        recalculate(permissiblesToRecalculate);
        registryChanged();
    }

    private void putSeedPermission(Permission permission) {
        String permissionName = normalizePermissionName(permission.getName());
        permissions.put(permissionName, permission);
        recalculateSeedDefault(permission);
    }

    private void putSeedDefaultPermission(boolean op, Permission permission) {
        String permissionName = normalizePermissionName(permission.getName());
        Permission registeredPermission = permissions.computeIfAbsent(permissionName, ignored -> permission);
        defaultPermissions.get(op).add(registeredPermission);
    }

    private void recalculateSeedDefault(Permission permission) {
        if (permission.getDefault().getValue(false)) {
            defaultPermissions.get(false).add(permission);
        }
        if (permission.getDefault().getValue(true)) {
            defaultPermissions.get(true).add(permission);
        }
    }

    private void dirtyPermissionSubscribers(String permissionName) {
        recalculate(permissionSubscriptions.getOrDefault(permissionName, Set.of()));
    }

    private void dirtyDefaultSubscribers() {
        Set<Permissible> permissiblesToRecalculate = new LinkedHashSet<>();
        defaultSubscriptions.values().forEach(permissiblesToRecalculate::addAll);
        recalculate(permissiblesToRecalculate);
    }

    private void recalculate(Collection<Permissible> permissibles) {
        List<Permissible> permissiblesToRecalculate = new ArrayList<>(new LinkedHashSet<>(permissibles));
        permissiblesToRecalculate.forEach(Permissible::recalculatePermissions);
    }

    private void registryChanged() {
        registryChangeListener.run();
    }

    private static String normalizePermissionName(String permissionName) {
        String normalizedPermissionName = Objects.requireNonNull(permissionName, "permissionName").toLowerCase(Locale.ROOT);
        if (normalizedPermissionName.isBlank()) {
            throw new IllegalArgumentException("permission name must not be blank");
        }
        return normalizedPermissionName;
    }

    private static String normalizeKnownPermissionNode(String permissionName) {
        try {
            String normalizedNode = PermissionNodes.normalize(permissionName);
            if (PermissionNodes.isWildcard(normalizedNode)) {
                return null;
            }
            return normalizedNode;
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }
}
