package me.clutchy.clutchperms.common.node;

import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/**
 * Immutable registry backed by a fixed known-node snapshot.
 */
final class StaticPermissionNodeRegistry implements PermissionNodeRegistry {

    private static final Comparator<KnownPermissionNode> NODE_ORDER = Comparator.comparing(KnownPermissionNode::node).thenComparing(node -> node.source().name());

    private final Set<KnownPermissionNode> nodes;

    StaticPermissionNodeRegistry(Collection<KnownPermissionNode> nodes) {
        Set<KnownPermissionNode> snapshot = new TreeSet<>(NODE_ORDER);
        Objects.requireNonNull(nodes, "nodes").forEach(node -> snapshot.add(new KnownPermissionNode(node.node(), node.description(), node.source())));
        this.nodes = Collections.unmodifiableSet(snapshot);
    }

    @Override
    public Set<KnownPermissionNode> getKnownNodes() {
        return nodes;
    }
}
