package me.clutchy.clutchperms.common;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Permission service decorator that reports successful mutations to a listener.
 */
final class ObservingPermissionService implements PermissionService {

    private final PermissionService delegate;

    private final PermissionChangeListener listener;

    ObservingPermissionService(PermissionService delegate, PermissionChangeListener listener) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        this.listener = Objects.requireNonNull(listener, "listener");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public PermissionValue getPermission(UUID subjectId, String node) {
        return delegate.getPermission(subjectId, node);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Map<String, PermissionValue> getPermissions(UUID subjectId) {
        return delegate.getPermissions(subjectId);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setPermission(UUID subjectId, String node, PermissionValue value) {
        delegate.setPermission(subjectId, node, value);
        listener.permissionChanged(subjectId);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void clearPermission(UUID subjectId, String node) {
        delegate.clearPermission(subjectId, node);
        listener.permissionChanged(subjectId);
    }
}
