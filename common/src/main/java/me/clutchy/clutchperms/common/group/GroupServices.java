package me.clutchy.clutchperms.common.group;

import java.util.Objects;

import me.clutchy.clutchperms.common.storage.SqliteStore;

/**
 * Creates shared group service implementations used by platform entrypoints.
 */
public final class GroupServices {

    public static GroupService sqlite(SqliteStore store) {
        return new SqliteGroupService(Objects.requireNonNull(store, "store"));
    }

    /**
     * Wraps a group service and notifies a listener after successful mutations.
     *
     * @param delegate group service that owns storage and reads
     * @param listener listener notified after successful mutations
     * @return observing group service decorator
     */
    public static GroupService observing(GroupService delegate, GroupChangeListener listener) {
        return new ObservingGroupService(delegate, listener);
    }

    private GroupServices() {
    }
}
