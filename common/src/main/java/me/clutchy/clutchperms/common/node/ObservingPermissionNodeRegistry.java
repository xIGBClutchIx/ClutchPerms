package me.clutchy.clutchperms.common.node;

import java.util.Objects;
import java.util.Set;

/**
 * Mutable registry decorator that reports successful node mutations.
 */
final class ObservingPermissionNodeRegistry implements MutablePermissionNodeRegistry {

    private final MutablePermissionNodeRegistry delegate;

    private final PermissionNodeChangeListener listener;

    ObservingPermissionNodeRegistry(MutablePermissionNodeRegistry delegate, PermissionNodeChangeListener listener) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        this.listener = Objects.requireNonNull(listener, "listener");
    }

    @Override
    public Set<KnownPermissionNode> getKnownNodes() {
        return delegate.getKnownNodes();
    }

    @Override
    public void addNode(String node, String description) {
        delegate.addNode(node, description);
        listener.nodesChanged();
    }

    @Override
    public void removeNode(String node) {
        delegate.removeNode(node);
        listener.nodesChanged();
    }
}
