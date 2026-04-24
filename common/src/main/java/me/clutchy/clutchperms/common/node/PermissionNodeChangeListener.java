package me.clutchy.clutchperms.common.node;

/**
 * Listener notified after successful known-node registry mutations.
 */
@FunctionalInterface
public interface PermissionNodeChangeListener {

    /**
     * Called after known permission nodes change.
     */
    void nodesChanged();
}
