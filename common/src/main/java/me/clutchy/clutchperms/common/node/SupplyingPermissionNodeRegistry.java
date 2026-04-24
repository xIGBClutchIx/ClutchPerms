package me.clutchy.clutchperms.common.node;

import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Supplier;

/**
 * Registry that adapts a live platform node-name supplier.
 */
final class SupplyingPermissionNodeRegistry implements PermissionNodeRegistry {

    private static final Comparator<KnownPermissionNode> NODE_ORDER = Comparator.comparing(KnownPermissionNode::node).thenComparing(node -> node.source().name());

    private final PermissionNodeSource source;

    private final Supplier<? extends Collection<String>> nodeSupplier;

    SupplyingPermissionNodeRegistry(PermissionNodeSource source, Supplier<? extends Collection<String>> nodeSupplier) {
        this.source = Objects.requireNonNull(source, "source");
        this.nodeSupplier = Objects.requireNonNull(nodeSupplier, "nodeSupplier");
    }

    @Override
    public Set<KnownPermissionNode> getKnownNodes() {
        Set<KnownPermissionNode> nodes = new TreeSet<>(NODE_ORDER);
        for (String node : nodeSupplier.get()) {
            try {
                nodes.add(new KnownPermissionNode(node, "", source));
            } catch (IllegalArgumentException exception) {
                // Platform registries can contain names ClutchPerms cannot safely manage.
            }
        }
        return Collections.unmodifiableSet(nodes);
    }
}
