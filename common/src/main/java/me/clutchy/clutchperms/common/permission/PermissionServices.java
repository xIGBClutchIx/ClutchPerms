package me.clutchy.clutchperms.common.permission;

import java.util.Objects;

import me.clutchy.clutchperms.common.storage.SqliteStore;

/**
 * Creates shared permission service implementations used by platform entrypoints.
 */
public final class PermissionServices {

    public static PermissionService sqlite(SqliteStore store) {
        return new SqlitePermissionService(Objects.requireNonNull(store, "store"));
    }

    /**
     * Wraps a permission service and notifies a listener after successful mutations.
     *
     * @param delegate permission service that owns storage and reads
     * @param listener listener notified after successful subject mutations
     * @return observing permission service decorator
     * @throws NullPointerException if {@code delegate} or {@code listener} is {@code null}
     */
    public static PermissionService observing(PermissionService delegate, PermissionChangeListener listener) {
        return new ObservingPermissionService(delegate, listener);
    }

    /**
     * Prevents instantiation of this factory-only utility class.
     */
    private PermissionServices() {
    }
}
