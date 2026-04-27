package me.clutchy.clutchperms.common.node;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

import me.clutchy.clutchperms.common.permission.PermissionNodes;
import me.clutchy.clutchperms.common.storage.SqliteStore;

/**
 * Factory methods for known permission node registries.
 */
public final class PermissionNodeRegistries {

    public static MutablePermissionNodeRegistry sqlite(SqliteStore store) {
        return new SqlitePermissionNodeRegistry(Objects.requireNonNull(store, "store"));
    }

    /**
     * Creates an empty in-memory manual node registry.
     *
     * @return mutable in-memory registry
     */
    public static MutablePermissionNodeRegistry inMemory() {
        return new InMemoryPermissionNodeRegistry();
    }

    /**
     * Creates an in-memory registry from a known-node snapshot.
     *
     * @param nodes initial known nodes
     * @return mutable in-memory registry
     */
    public static MutablePermissionNodeRegistry inMemory(Collection<KnownPermissionNode> nodes) {
        return new InMemoryPermissionNodeRegistry(nodes);
    }

    /**
     * Creates an immutable registry from known nodes.
     *
     * @param nodes known nodes
     * @return immutable registry
     */
    public static PermissionNodeRegistry staticNodes(Collection<KnownPermissionNode> nodes) {
        return new StaticPermissionNodeRegistry(nodes);
    }

    /**
     * Creates an immutable registry from exact node names and one source.
     *
     * @param source known-node source
     * @param nodes exact node names
     * @return immutable registry
     */
    public static PermissionNodeRegistry staticNodes(PermissionNodeSource source, Collection<String> nodes) {
        Objects.requireNonNull(source, "source");
        return new StaticPermissionNodeRegistry(Objects.requireNonNull(nodes, "nodes").stream().map(node -> new KnownPermissionNode(node, "", source)).toList());
    }

    /**
     * Creates a live registry from platform-supplied exact node names.
     *
     * @param source known-node source
     * @param nodeSupplier supplier of node names
     * @return live registry
     */
    public static PermissionNodeRegistry supplying(PermissionNodeSource source, Supplier<? extends Collection<String>> nodeSupplier) {
        return new SupplyingPermissionNodeRegistry(source, nodeSupplier);
    }

    /**
     * Merges multiple registries, keeping the first descriptor for duplicate nodes.
     *
     * @param registries registries in precedence order
     * @return merged read-only registry
     */
    public static PermissionNodeRegistry composite(PermissionNodeRegistry... registries) {
        return composite(Arrays.asList(registries));
    }

    /**
     * Merges multiple registries, keeping the first descriptor for duplicate nodes.
     *
     * @param registries registries in precedence order
     * @return merged read-only registry
     */
    public static PermissionNodeRegistry composite(Collection<PermissionNodeRegistry> registries) {
        return new CompositePermissionNodeRegistry(registries);
    }

    /**
     * Wraps a mutable registry and notifies a listener after successful mutations.
     *
     * @param delegate registry that owns storage and reads
     * @param listener listener notified after successful mutations
     * @return observing registry decorator
     */
    public static MutablePermissionNodeRegistry observing(MutablePermissionNodeRegistry delegate, PermissionNodeChangeListener listener) {
        return new ObservingPermissionNodeRegistry(delegate, listener);
    }

    /**
     * Creates the built-in ClutchPerms registry.
     *
     * @return built-in known nodes
     */
    public static PermissionNodeRegistry builtIn() {
        List<KnownPermissionNode> nodes = new ArrayList<>();
        for (String node : PermissionNodes.commandNodes()) {
            nodes.add(new KnownPermissionNode(node, "Allows the matching ClutchPerms admin command.", PermissionNodeSource.BUILT_IN));
        }
        return staticNodes(nodes);
    }

    private PermissionNodeRegistries() {
    }
}
