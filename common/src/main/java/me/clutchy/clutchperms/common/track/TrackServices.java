package me.clutchy.clutchperms.common.track;

import java.util.Objects;

import me.clutchy.clutchperms.common.group.GroupService;
import me.clutchy.clutchperms.common.storage.SqliteStore;

/**
 * Creates shared track service implementations used by platform entrypoints.
 */
public final class TrackServices {

    public static TrackService sqlite(SqliteStore store, GroupService groupService) {
        return new SqliteTrackService(Objects.requireNonNull(store, "store"), Objects.requireNonNull(groupService, "groupService"));
    }

    public static TrackService inMemory(GroupService groupService) {
        return new InMemoryTrackService(Objects.requireNonNull(groupService, "groupService"));
    }

    private TrackServices() {
    }
}
