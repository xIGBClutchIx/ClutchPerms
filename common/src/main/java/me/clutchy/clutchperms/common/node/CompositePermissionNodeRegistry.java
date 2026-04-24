package me.clutchy.clutchperms.common.node;

import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/**
 * Read-only registry that merges multiple registries.
 */
final class CompositePermissionNodeRegistry implements PermissionNodeRegistry {

    private static final Comparator<KnownPermissionNode> NODE_ORDER = Comparator.comparing(KnownPermissionNode::node).thenComparing(node -> node.source().name());

    private final List<PermissionNodeRegistry> registries;

    CompositePermissionNodeRegistry(Collection<PermissionNodeRegistry> registries) {
        this.registries = List.copyOf(Objects.requireNonNull(registries, "registries"));
    }

    @Override
    public Set<KnownPermissionNode> getKnownNodes() {
        Map<String, KnownPermissionNode> nodes = new LinkedHashMap<>();
        for (PermissionNodeRegistry registry : registries) {
            registry.getKnownNodes().forEach(node -> nodes.putIfAbsent(node.node(), node));
        }

        Set<KnownPermissionNode> snapshot = new TreeSet<>(NODE_ORDER);
        snapshot.addAll(nodes.values());
        return Collections.unmodifiableSet(snapshot);
    }
}
