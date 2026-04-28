package me.clutchy.clutchperms.common.track;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import me.clutchy.clutchperms.common.group.GroupChangeListener;
import me.clutchy.clutchperms.common.group.GroupService;
import me.clutchy.clutchperms.common.storage.PermissionStorageException;
import me.clutchy.clutchperms.common.storage.SqliteStore;

/**
 * SQLite-backed track service that persists ordered group tracks after every mutation.
 */
final class SqliteTrackService implements TrackService, GroupChangeListener {

    private final SqliteStore store;

    private final GroupService groupService;

    private InMemoryTrackService delegate;

    SqliteTrackService(SqliteStore store, GroupService groupService) {
        this.store = Objects.requireNonNull(store, "store");
        this.groupService = Objects.requireNonNull(groupService, "groupService");
        try {
            this.delegate = new InMemoryTrackService(groupService, loadTracks());
        } catch (PermissionStorageException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new PermissionStorageException("Invalid SQLite track storage in " + store.databaseFile(), exception);
        }
    }

    @Override
    public synchronized Set<String> getTracks() {
        return delegate.getTracks();
    }

    @Override
    public synchronized boolean hasTrack(String trackName) {
        return delegate.hasTrack(trackName);
    }

    @Override
    public synchronized List<String> getTrackGroups(String trackName) {
        return delegate.getTrackGroups(trackName);
    }

    @Override
    public synchronized void createTrack(String trackName) {
        InMemoryTrackService candidate = copyDelegate();
        candidate.createTrack(trackName);
        insertTrack(InMemoryTrackService.normalizeTrackName(trackName));
        delegate = candidate;
    }

    @Override
    public synchronized void deleteTrack(String trackName) {
        InMemoryTrackService candidate = copyDelegate();
        candidate.deleteTrack(trackName);
        deleteTrackRow(InMemoryTrackService.normalizeTrackName(trackName));
        delegate = candidate;
    }

    @Override
    public synchronized void renameTrack(String trackName, String newTrackName) {
        String normalizedTrackName = InMemoryTrackService.normalizeTrackName(trackName);
        String normalizedNewTrackName = InMemoryTrackService.normalizeTrackName(newTrackName);
        InMemoryTrackService candidate = copyDelegate();
        candidate.renameTrack(trackName, newTrackName);
        renameTrackRows(normalizedTrackName, normalizedNewTrackName);
        delegate = candidate;
    }

    @Override
    public synchronized void setTrackGroups(String trackName, List<String> groupNames) {
        String normalizedTrackName = InMemoryTrackService.normalizeTrackName(trackName);
        InMemoryTrackService candidate = copyDelegate();
        candidate.setTrackGroups(trackName, groupNames);
        replaceTrackGroups(normalizedTrackName, candidate.getTrackGroups(normalizedTrackName));
        delegate = candidate;
    }

    @Override
    public synchronized void subjectGroupsChanged(UUID subjectId) {
    }

    @Override
    public synchronized void groupsChanged() {
    }

    @Override
    public synchronized void groupDeleted(String groupName) {
        delegate.groupDeleted(groupName);
    }

    @Override
    public synchronized void groupRenamed(String groupName, String newGroupName) {
        delegate.groupRenamed(groupName, newGroupName);
    }

    private InMemoryTrackService copyDelegate() {
        return new InMemoryTrackService(groupService, delegate.tracksSnapshot());
    }

    private Map<String, List<String>> loadTracks() {
        return store.read(connection -> {
            Map<String, List<String>> tracks = new LinkedHashMap<>();
            try (PreparedStatement statement = connection.prepareStatement("SELECT name FROM tracks ORDER BY name"); ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    String trackName = InMemoryTrackService.normalizeTrackName(rows.getString("name"));
                    if (tracks.putIfAbsent(trackName, new ArrayList<>()) != null) {
                        throw new PermissionStorageException("Duplicate normalized track name in SQLite tracks: " + trackName);
                    }
                }
            }

            Map<String, Integer> expectedPositions = new LinkedHashMap<>();
            try (PreparedStatement statement = connection.prepareStatement("SELECT track_name, position, group_name FROM track_groups ORDER BY track_name, position");
                    ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    String trackName = InMemoryTrackService.normalizeTrackName(rows.getString("track_name"));
                    List<String> groups = tracks.get(trackName);
                    if (groups == null) {
                        throw new PermissionStorageException("Unknown track in SQLite track groups: " + trackName);
                    }
                    int position = rows.getInt("position");
                    int expectedPosition = expectedPositions.merge(trackName, 1, Integer::sum);
                    if (position != expectedPosition) {
                        throw new PermissionStorageException("Non-contiguous track position in SQLite track groups for track " + trackName + ": " + position);
                    }
                    String groupName = normalizeGroupName(rows.getString("group_name"));
                    if (!groupService.hasGroup(groupName)) {
                        throw new PermissionStorageException("Unknown group in SQLite track groups for track " + trackName + ": " + groupName);
                    }
                    groups.add(groupName);
                }
            }
            return tracks;
        });
    }

    private static String normalizeGroupName(String groupName) {
        String normalizedGroupName = Objects.requireNonNull(groupName, "groupName").trim().toLowerCase(java.util.Locale.ROOT);
        if (normalizedGroupName.isEmpty()) {
            throw new IllegalArgumentException("group name must not be blank");
        }
        return normalizedGroupName;
    }

    private void insertTrack(String trackName) {
        store.write(connection -> {
            try (PreparedStatement statement = connection.prepareStatement("INSERT INTO tracks (name) VALUES (?)")) {
                statement.setString(1, trackName);
                statement.executeUpdate();
            }
        });
    }

    private void deleteTrackRow(String trackName) {
        store.write(connection -> {
            try (PreparedStatement statement = connection.prepareStatement("DELETE FROM tracks WHERE name = ?")) {
                statement.setString(1, trackName);
                statement.executeUpdate();
            }
        });
    }

    private void renameTrackRows(String trackName, String newTrackName) {
        store.write(connection -> {
            try (PreparedStatement insertTrack = connection.prepareStatement("INSERT INTO tracks (name) VALUES (?)")) {
                insertTrack.setString(1, newTrackName);
                insertTrack.executeUpdate();
            }
            try (PreparedStatement updateTrackGroups = connection.prepareStatement("UPDATE track_groups SET track_name = ? WHERE track_name = ?")) {
                updateTrackGroups.setString(1, newTrackName);
                updateTrackGroups.setString(2, trackName);
                updateTrackGroups.executeUpdate();
            }
            try (PreparedStatement deleteTrack = connection.prepareStatement("DELETE FROM tracks WHERE name = ?")) {
                deleteTrack.setString(1, trackName);
                deleteTrack.executeUpdate();
            }
        });
    }

    private void replaceTrackGroups(String trackName, List<String> groupNames) {
        store.write(connection -> {
            try (PreparedStatement deleteGroups = connection.prepareStatement("DELETE FROM track_groups WHERE track_name = ?")) {
                deleteGroups.setString(1, trackName);
                deleteGroups.executeUpdate();
            }
            try (PreparedStatement insertGroup = connection.prepareStatement("INSERT INTO track_groups (track_name, position, group_name) VALUES (?, ?, ?)")) {
                int position = 1;
                for (String groupName : groupNames) {
                    insertGroup.setString(1, trackName);
                    insertGroup.setInt(2, position++);
                    insertGroup.setString(3, groupName);
                    insertGroup.addBatch();
                }
                insertGroup.executeBatch();
            }
        });
    }
}
