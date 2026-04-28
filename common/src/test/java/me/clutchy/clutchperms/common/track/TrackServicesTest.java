package me.clutchy.clutchperms.common.track;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Statement;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import me.clutchy.clutchperms.common.group.GroupChangeListener;
import me.clutchy.clutchperms.common.group.GroupService;
import me.clutchy.clutchperms.common.group.GroupServices;
import me.clutchy.clutchperms.common.group.InMemoryGroupService;
import me.clutchy.clutchperms.common.storage.PermissionStorageException;
import me.clutchy.clutchperms.common.storage.SqliteStore;
import me.clutchy.clutchperms.common.storage.SqliteTestSupport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies track storage loading, persistence, validation, and group-reference maintenance.
 */
class TrackServicesTest {

    @TempDir
    private Path temporaryDirectory;

    @Test
    void missingDatabaseCreatesEmptyTrackSchema() {
        Path databaseFile = SqliteTestSupport.databaseFile(temporaryDirectory);

        try (SqliteStore store = SqliteTestSupport.open(databaseFile)) {
            GroupService groupService = GroupServices.sqlite(store);
            TrackService trackService = TrackServices.sqlite(store, groupService);

            assertEquals(Set.of(), trackService.getTracks());
        }

        assertTrue(Files.exists(databaseFile));
    }

    @Test
    void tracksRoundTripThroughSqlite() {
        Path databaseFile = SqliteTestSupport.databaseFile(temporaryDirectory);
        try (SqliteStore store = SqliteTestSupport.open(databaseFile)) {
            GroupService groupService = GroupServices.sqlite(store);
            TrackService trackService = TrackServices.sqlite(store, groupService);
            groupService.createGroup("staff");
            groupService.createGroup("admin");
            trackService.createTrack("Ranks");
            trackService.setTrackGroups("Ranks", List.of("default", "staff", "admin"));
        }

        try (SqliteStore store = SqliteTestSupport.open(databaseFile)) {
            GroupService groupService = GroupServices.sqlite(store);
            TrackService trackService = TrackServices.sqlite(store, groupService);

            assertEquals(Set.of("ranks"), trackService.getTracks());
            assertEquals(List.of("default", "staff", "admin"), trackService.getTrackGroups("ranks"));
        }
    }

    @Test
    void trackMutationsRejectInvalidDefinitions() {
        GroupService groupService = new InMemoryGroupService();
        TrackService trackService = TrackServices.inMemory(groupService);
        groupService.createGroup("staff");
        groupService.createGroup("admin");
        trackService.createTrack("ranks");

        assertThrows(IllegalArgumentException.class, () -> trackService.setTrackGroups("ranks", List.of("staff", "default")));
        assertThrows(IllegalArgumentException.class, () -> trackService.setTrackGroups("ranks", List.of("default", "op")));
        assertThrows(IllegalArgumentException.class, () -> trackService.setTrackGroups("ranks", List.of("default", "staff", "staff")));
        assertThrows(IllegalArgumentException.class, () -> trackService.setTrackGroups("ranks", List.of("default", "missing")));
    }

    @Test
    void groupRenameAndDeleteKeepTracksConsistent() {
        Path databaseFile = SqliteTestSupport.databaseFile(temporaryDirectory);
        try (SqliteStore store = SqliteTestSupport.open(databaseFile)) {
            GroupService storageGroupService = GroupServices.sqlite(store);
            TrackService trackService = TrackServices.sqlite(store, storageGroupService);
            GroupService groupService = GroupServices.observing(storageGroupService, forwardingTrackListener(trackService));

            groupService.createGroup("staff");
            groupService.createGroup("admin");
            groupService.createGroup("vip");
            trackService.createTrack("ranks");
            trackService.setTrackGroups("ranks", List.of("default", "staff", "admin", "vip"));

            groupService.renameGroup("admin", "moderator");
            assertEquals(List.of("default", "staff", "moderator", "vip"), trackService.getTrackGroups("ranks"));

            groupService.deleteGroup("staff");
            assertEquals(List.of("default", "moderator", "vip"), trackService.getTrackGroups("ranks"));
        }

        try (SqliteStore store = SqliteTestSupport.open(databaseFile)) {
            GroupService groupService = GroupServices.sqlite(store);
            TrackService trackService = TrackServices.sqlite(store, groupService);

            assertEquals(List.of("default", "moderator", "vip"), trackService.getTrackGroups("ranks"));
        }
    }

    @Test
    void invalidSqliteTrackRowsFailLoad() {
        assertInvalidTrackDatabase("INSERT INTO tracks (name) VALUES ('Ranks')", "INSERT INTO tracks (name) VALUES (' ranks ')");
        assertInvalidTrackDatabase("INSERT INTO tracks (name) VALUES ('ranks')", "INSERT INTO track_groups (track_name, position, group_name) VALUES ('ranks', 1, 'staff')",
                "INSERT INTO track_groups (track_name, position, group_name) VALUES ('ranks', 2, 'default')");
        assertInvalidTrackDatabase("INSERT INTO tracks (name) VALUES ('ranks')", "INSERT INTO track_groups (track_name, position, group_name) VALUES ('ranks', 1, 'op')");
        assertInvalidTrackDatabase("INSERT INTO tracks (name) VALUES ('ranks')", "INSERT INTO track_groups (track_name, position, group_name) VALUES ('ranks', 2, 'staff')");
    }

    private void assertInvalidTrackDatabase(String... statements) {
        Path databaseFile = temporaryDirectory.resolve(UUID.randomUUID().toString()).resolve("database.db");
        try (SqliteStore store = SqliteTestSupport.open(databaseFile)) {
            GroupService groupService = GroupServices.sqlite(store);
            groupService.createGroup("staff");
            TrackServices.sqlite(store, groupService);
            store.write(connection -> {
                try (Statement statement = connection.createStatement()) {
                    for (String sql : statements) {
                        statement.executeUpdate(sql);
                    }
                }
            });
        }

        try (SqliteStore store = SqliteTestSupport.open(databaseFile)) {
            GroupService groupService = GroupServices.sqlite(store);
            assertThrows(PermissionStorageException.class, () -> TrackServices.sqlite(store, groupService));
        }
    }

    private static GroupChangeListener forwardingTrackListener(TrackService trackService) {
        return new GroupChangeListener() {

            @Override
            public void subjectGroupsChanged(UUID subjectId) {
            }

            @Override
            public void groupsChanged() {
            }

            @Override
            public void groupDeleted(String groupName) {
                if (trackService instanceof GroupChangeListener listener) {
                    listener.groupDeleted(groupName);
                }
            }

            @Override
            public void groupRenamed(String groupName, String newGroupName) {
                if (trackService instanceof GroupChangeListener listener) {
                    listener.groupRenamed(groupName, newGroupName);
                }
            }
        };
    }
}
