package me.clutchy.clutchperms.paper;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.bukkit.permissions.Permissible;
import org.bukkit.permissions.Permission;
import org.bukkit.permissions.PermissionAttachment;
import org.bukkit.permissions.PermissionAttachmentInfo;
import org.bukkit.permissions.PermissionDefault;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

import io.papermc.paper.plugin.PermissionManager;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for the Paper-only permission manager override implementation.
 */
class PaperClutchPermsPermissionManagerTest {

    /**
     * Confirms the override manager starts with the registry state Paper already had.
     */
    @Test
    void seededPermissionsAreVisible() {
        TestPermissionManager seed = new TestPermissionManager();
        Permission permission = new Permission("Example.Seed", PermissionDefault.OP);
        CountingPermissible permissionSubscriber = new CountingPermissible();
        CountingPermissible defaultSubscriber = new CountingPermissible();
        seed.addPermission(permission);
        seed.subscribeToPermission("example.seed", permissionSubscriber);
        seed.subscribeToDefaultPerms(true, defaultSubscriber);

        PaperClutchPermsPermissionManager manager = PaperClutchPermsPermissionManager.seededFrom(seed);

        assertSame(permission, manager.getPermission("example.seed"));
        assertTrue(manager.getPermissions().contains(permission));
        assertTrue(manager.getDefaultPermissions(true).contains(permission));
        assertFalse(manager.getDefaultPermissions(false).contains(permission));
        assertTrue(manager.getPermissionSubscriptions("example.seed").contains(permissionSubscriber));
        assertTrue(manager.getDefaultPermSubscriptions(true).contains(defaultSubscriber));
    }

    /**
     * Confirms registry mutation methods behave like a normal Paper permission registry.
     */
    @Test
    void addRemoveAndClearPermissionsMutateRegistry() {
        PaperClutchPermsPermissionManager manager = PaperClutchPermsPermissionManager.seededFrom(new TestPermissionManager());
        Permission first = new Permission("Example.First", PermissionDefault.TRUE);
        Permission second = new Permission("Example.Second", PermissionDefault.FALSE);

        manager.addPermission(first);
        manager.addPermissions(List.of(second));

        assertSame(first, manager.getPermission("example.first"));
        assertSame(second, manager.getPermission("example.second"));
        assertTrue(manager.getDefaultPermissions(false).contains(first));
        assertTrue(manager.getDefaultPermissions(true).contains(first));

        manager.removePermission("EXAMPLE.FIRST");
        assertNull(manager.getPermission("example.first"));
        assertFalse(manager.getDefaultPermissions(false).contains(first));
        assertFalse(manager.getDefaultPermissions(true).contains(first));

        manager.clearPermissions();
        assertEquals(Set.of(), manager.getPermissions());
        assertEquals(Set.of(), manager.getDefaultPermissions(false));
        assertEquals(Set.of(), manager.getDefaultPermissions(true));
    }

    /**
     * Confirms duplicate names are rejected case-insensitively.
     */
    @Test
    void duplicatePermissionRegistrationFails() {
        PaperClutchPermsPermissionManager manager = PaperClutchPermsPermissionManager.seededFrom(new TestPermissionManager());

        manager.addPermission(new Permission("Example.Node"));

        assertThrows(IllegalArgumentException.class, () -> manager.addPermission(new Permission("example.node")));
        assertThrows(IllegalArgumentException.class, () -> manager.addPermissions(List.of(new Permission("example.other"), new Permission("EXAMPLE.OTHER"))));
    }

    /**
     * Confirms default permission sets update after a registered permission changes default behavior.
     */
    @Test
    void defaultPermissionSetsUpdateAfterRecalculate() {
        MockBukkit.mock();
        try {
            PaperClutchPermsPermissionManager manager = PaperClutchPermsPermissionManager.seededFrom(new TestPermissionManager());
            Permission permission = new Permission("Example.Default", PermissionDefault.OP);

            manager.addPermission(permission);
            assertFalse(manager.getDefaultPermissions(false).contains(permission));
            assertTrue(manager.getDefaultPermissions(true).contains(permission));

            permission.setDefault(PermissionDefault.NOT_OP);
            manager.recalculatePermissionDefaults(permission);

            assertTrue(manager.getDefaultPermissions(false).contains(permission));
            assertFalse(manager.getDefaultPermissions(true).contains(permission));
        } finally {
            MockBukkit.unmock();
        }
    }

    /**
     * Confirms subscribed permissibles recalculate when their registered permissions or defaults change.
     */
    @Test
    void subscriptionsAreTrackedAndRecalculated() {
        PaperClutchPermsPermissionManager manager = PaperClutchPermsPermissionManager.seededFrom(new TestPermissionManager());
        CountingPermissible permissionSubscriber = new CountingPermissible();
        CountingPermissible defaultSubscriber = new CountingPermissible();

        manager.subscribeToPermission("example.node", permissionSubscriber);
        manager.subscribeToDefaultPerms(false, defaultSubscriber);
        manager.addPermission(new Permission("Example.Node", PermissionDefault.TRUE));

        assertTrue(permissionSubscriber.recalculations() > 0);
        assertTrue(defaultSubscriber.recalculations() > 0);
        assertTrue(manager.getPermissionSubscriptions("example.node").contains(permissionSubscriber));
        assertTrue(manager.getDefaultPermSubscriptions(false).contains(defaultSubscriber));

        manager.unsubscribeFromPermission("example.node", permissionSubscriber);
        manager.unsubscribeFromDefaultPerms(false, defaultSubscriber);

        assertFalse(manager.getPermissionSubscriptions("example.node").contains(permissionSubscriber));
        assertFalse(manager.getDefaultPermSubscriptions(false).contains(defaultSubscriber));
    }

    /**
     * Confirms known nodes are deterministic exact permission names only.
     */
    @Test
    void knownPermissionNodesExcludeWildcardAssignmentsAndInvalidNodes() {
        PaperClutchPermsPermissionManager manager = PaperClutchPermsPermissionManager.seededFrom(new TestPermissionManager());

        manager.addPermissions(List.of(new Permission("Example.Node"), new Permission("example.*"), new Permission("*"), new Permission("example.*.bad")));

        assertEquals(Set.of("example.node"), manager.knownPermissionNodes());
    }

    /**
     * Confirms registry mutation callbacks fire only after successful changes.
     */
    @Test
    void registryChangeListenerFiresAfterSuccessfulMutations() {
        PaperClutchPermsPermissionManager manager = PaperClutchPermsPermissionManager.seededFrom(new TestPermissionManager());
        AtomicInteger changes = new AtomicInteger();
        Permission permission = new Permission("Example.Node", PermissionDefault.TRUE);
        manager.setRegistryChangeListener(changes::incrementAndGet);

        manager.addPermission(permission);
        assertEquals(1, changes.get());

        assertThrows(IllegalArgumentException.class, () -> manager.addPermission(new Permission("example.node")));
        assertEquals(1, changes.get());

        manager.recalculatePermissionDefaults(permission);
        assertEquals(2, changes.get());

        manager.removePermission("example.node");
        assertEquals(3, changes.get());

        manager.addPermission(new Permission("Example.Other"));
        manager.clearPermissions();
        assertEquals(5, changes.get());
    }

    /**
     * Confirms shutdown can detach ClutchPerms lifecycle callbacks without disabling the manager itself.
     */
    @Test
    void registryChangeListenerCanBeClearedForShutdown() {
        PaperClutchPermsPermissionManager manager = PaperClutchPermsPermissionManager.seededFrom(new TestPermissionManager());
        AtomicInteger changes = new AtomicInteger();
        Permission afterPermission = new Permission("Example.After");
        manager.setRegistryChangeListener(changes::incrementAndGet);

        manager.addPermission(new Permission("Example.Before"));
        manager.clearRegistryChangeListener();
        manager.addPermission(afterPermission);

        assertEquals(1, changes.get());
        assertSame(afterPermission, manager.getPermission("example.after"));
    }

    private static final class TestPermissionManager implements PermissionManager {

        private final Map<String, Permission> permissions = new TreeMap<>();

        private final Map<String, Set<Permissible>> permissionSubscriptions = new HashMap<>();

        private final Map<Boolean, Set<Permissible>> defaultSubscriptions = Map.of(Boolean.FALSE, new LinkedHashSet<>(), Boolean.TRUE, new LinkedHashSet<>());

        @Override
        public Permission getPermission(String name) {
            return permissions.get(normalize(name));
        }

        @Override
        public void addPermission(Permission permission) {
            permissions.put(normalize(permission.getName()), permission);
        }

        @Override
        public void removePermission(Permission permission) {
            removePermission(permission.getName());
        }

        @Override
        public void removePermission(String name) {
            permissions.remove(normalize(name));
        }

        @Override
        public Set<Permission> getDefaultPermissions(boolean op) {
            Set<Permission> defaultPermissions = new LinkedHashSet<>();
            permissions.values().stream().filter(permission -> permission.getDefault().getValue(op)).forEach(defaultPermissions::add);
            return defaultPermissions;
        }

        @Override
        public void recalculatePermissionDefaults(Permission permission) {
        }

        @Override
        public void subscribeToPermission(String permission, Permissible permissible) {
            permissionSubscriptions.computeIfAbsent(normalize(permission), ignored -> new LinkedHashSet<>()).add(permissible);
        }

        @Override
        public void unsubscribeFromPermission(String permission, Permissible permissible) {
            permissionSubscriptions.getOrDefault(normalize(permission), Set.of()).remove(permissible);
        }

        @Override
        public Set<Permissible> getPermissionSubscriptions(String permission) {
            return Set.copyOf(permissionSubscriptions.getOrDefault(normalize(permission), Set.of()));
        }

        @Override
        public void subscribeToDefaultPerms(boolean op, Permissible permissible) {
            defaultSubscriptions.get(op).add(permissible);
        }

        @Override
        public void unsubscribeFromDefaultPerms(boolean op, Permissible permissible) {
            defaultSubscriptions.get(op).remove(permissible);
        }

        @Override
        public Set<Permissible> getDefaultPermSubscriptions(boolean op) {
            return Set.copyOf(defaultSubscriptions.get(op));
        }

        @Override
        public Set<Permission> getPermissions() {
            return Set.copyOf(permissions.values());
        }

        @Override
        public void addPermissions(List<Permission> permissions) {
            permissions.forEach(this::addPermission);
        }

        @Override
        public void clearPermissions() {
            permissions.clear();
        }

        private static String normalize(String permission) {
            return permission.toLowerCase(Locale.ROOT);
        }
    }

    private static final class CountingPermissible implements Permissible {

        private int recalculations;

        int recalculations() {
            return recalculations;
        }

        @Override
        public boolean isOp() {
            return false;
        }

        @Override
        public void setOp(boolean value) {
        }

        @Override
        public boolean isPermissionSet(String name) {
            return false;
        }

        @Override
        public boolean isPermissionSet(Permission permission) {
            return false;
        }

        @Override
        public boolean hasPermission(String name) {
            return false;
        }

        @Override
        public boolean hasPermission(Permission permission) {
            return false;
        }

        @Override
        public PermissionAttachment addAttachment(Plugin plugin, String name, boolean value) {
            return null;
        }

        @Override
        public PermissionAttachment addAttachment(Plugin plugin) {
            return null;
        }

        @Override
        public void removeAttachment(PermissionAttachment attachment) {
        }

        @Override
        public void recalculatePermissions() {
            recalculations++;
        }

        @Override
        public Set<PermissionAttachmentInfo> getEffectivePermissions() {
            return Set.of();
        }

        @Override
        public PermissionAttachment addAttachment(Plugin plugin, String name, boolean value, int ticks) {
            return null;
        }

        @Override
        public PermissionAttachment addAttachment(Plugin plugin, int ticks) {
            return null;
        }
    }
}
