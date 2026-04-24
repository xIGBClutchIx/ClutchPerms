package me.clutchy.clutchperms.common.node;

import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Thread-safe in-memory manual known-node registry.
 */
public final class InMemoryPermissionNodeRegistry implements MutablePermissionNodeRegistry {

    private static final Comparator<KnownPermissionNode> NODE_ORDER = Comparator.comparing(KnownPermissionNode::node).thenComparing(node -> node.source().name());

    private final ConcurrentMap<String, KnownPermissionNode> nodes = new ConcurrentHashMap<>();

    /**
     * Creates an empty registry.
     */
    public InMemoryPermissionNodeRegistry() {
    }

    InMemoryPermissionNodeRegistry(Collection<KnownPermissionNode> initialNodes) {
        Objects.requireNonNull(initialNodes, "initialNodes").forEach(node -> nodes.put(node.node(), new KnownPermissionNode(node.node(), node.description(), node.source())));
    }

    @Override
    public Set<KnownPermissionNode> getKnownNodes() {
        Set<KnownPermissionNode> snapshot = new TreeSet<>(NODE_ORDER);
        snapshot.addAll(nodes.values());
        return Collections.unmodifiableSet(snapshot);
    }

    @Override
    public void addNode(String node, String description) {
        KnownPermissionNode knownNode = new KnownPermissionNode(node, description, PermissionNodeSource.MANUAL);
        nodes.put(knownNode.node(), knownNode);
    }

    @Override
    public void removeNode(String node) {
        String normalizedNode = KnownPermissionNode.normalizeKnownNode(node);
        if (nodes.remove(normalizedNode) == null) {
            throw new IllegalArgumentException("known permission node is not manually registered: " + normalizedNode);
        }
    }

}
