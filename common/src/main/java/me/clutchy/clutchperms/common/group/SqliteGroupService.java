package me.clutchy.clutchperms.common.group;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import me.clutchy.clutchperms.common.display.DisplayProfile;
import me.clutchy.clutchperms.common.display.DisplayText;
import me.clutchy.clutchperms.common.permission.PermissionNodes;
import me.clutchy.clutchperms.common.permission.PermissionValue;
import me.clutchy.clutchperms.common.storage.PermissionStorageException;
import me.clutchy.clutchperms.common.storage.SqliteStore;

/**
 * SQLite-backed group service that persists group definitions, permissions, inheritance, and memberships after every mutation.
 */
final class SqliteGroupService implements GroupService {

    private final SqliteStore store;

    private InMemoryGroupService delegate;

    SqliteGroupService(SqliteStore store) {
        this.store = store;
        try {
            GroupData groupData = loadGroups();
            this.delegate = new InMemoryGroupService(groupData.groupPermissions(), groupData.groupDisplays(), groupData.groupParents(), groupData.memberships());
        } catch (PermissionStorageException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new PermissionStorageException("Invalid SQLite group storage in " + store.databaseFile(), exception);
        }
    }

    @Override
    public synchronized Set<String> getGroups() {
        return delegate.getGroups();
    }

    @Override
    public synchronized boolean hasGroup(String groupName) {
        return delegate.hasGroup(groupName);
    }

    @Override
    public synchronized void createGroup(String groupName) {
        InMemoryGroupService candidate = copyDelegate();
        candidate.createGroup(groupName);
        writeGroup(InMemoryGroupService.normalizeGroupName(groupName), candidate.getGroupDisplay(groupName));
        delegate = candidate;
    }

    @Override
    public synchronized void deleteGroup(String groupName) {
        InMemoryGroupService candidate = copyDelegate();
        candidate.deleteGroup(groupName);
        deleteGroupRow(InMemoryGroupService.normalizeGroupName(groupName));
        delegate = candidate;
    }

    @Override
    public synchronized void renameGroup(String groupName, String newGroupName) {
        InMemoryGroupService candidate = copyDelegate();
        candidate.renameGroup(groupName, newGroupName);
        renameGroupRows(InMemoryGroupService.normalizeGroupName(groupName), InMemoryGroupService.normalizeGroupName(newGroupName), candidate.getGroupDisplay(newGroupName));
        delegate = candidate;
    }

    @Override
    public synchronized PermissionValue getGroupPermission(String groupName, String node) {
        return delegate.getGroupPermission(groupName, node);
    }

    @Override
    public synchronized Map<String, PermissionValue> getGroupPermissions(String groupName) {
        return delegate.getGroupPermissions(groupName);
    }

    @Override
    public synchronized void setGroupPermission(String groupName, String node, PermissionValue value) {
        InMemoryGroupService candidate = copyDelegate();
        candidate.setGroupPermission(groupName, node, value);
        writeGroupPermission(InMemoryGroupService.normalizeGroupName(groupName), PermissionNodes.normalize(node), candidate.getGroupPermission(groupName, node));
        delegate = candidate;
    }

    @Override
    public synchronized void clearGroupPermission(String groupName, String node) {
        InMemoryGroupService candidate = copyDelegate();
        candidate.clearGroupPermission(groupName, node);
        deleteGroupPermission(InMemoryGroupService.normalizeGroupName(groupName), PermissionNodes.normalize(node));
        delegate = candidate;
    }

    @Override
    public synchronized int clearGroupPermissions(String groupName) {
        InMemoryGroupService candidate = copyDelegate();
        int removedPermissions = candidate.clearGroupPermissions(groupName);
        if (removedPermissions == 0) {
            return 0;
        }
        deleteGroupPermissions(InMemoryGroupService.normalizeGroupName(groupName));
        delegate = candidate;
        return removedPermissions;
    }

    @Override
    public synchronized DisplayProfile getGroupDisplay(String groupName) {
        return delegate.getGroupDisplay(groupName);
    }

    @Override
    public synchronized void setGroupPrefix(String groupName, DisplayText prefix) {
        InMemoryGroupService candidate = copyDelegate();
        candidate.setGroupPrefix(groupName, prefix);
        writeGroupDisplay(InMemoryGroupService.normalizeGroupName(groupName), candidate.getGroupDisplay(groupName));
        delegate = candidate;
    }

    @Override
    public synchronized void clearGroupPrefix(String groupName) {
        InMemoryGroupService candidate = copyDelegate();
        candidate.clearGroupPrefix(groupName);
        writeGroupDisplay(InMemoryGroupService.normalizeGroupName(groupName), candidate.getGroupDisplay(groupName));
        delegate = candidate;
    }

    @Override
    public synchronized void setGroupSuffix(String groupName, DisplayText suffix) {
        InMemoryGroupService candidate = copyDelegate();
        candidate.setGroupSuffix(groupName, suffix);
        writeGroupDisplay(InMemoryGroupService.normalizeGroupName(groupName), candidate.getGroupDisplay(groupName));
        delegate = candidate;
    }

    @Override
    public synchronized void clearGroupSuffix(String groupName) {
        InMemoryGroupService candidate = copyDelegate();
        candidate.clearGroupSuffix(groupName);
        writeGroupDisplay(InMemoryGroupService.normalizeGroupName(groupName), candidate.getGroupDisplay(groupName));
        delegate = candidate;
    }

    @Override
    public synchronized Set<String> getSubjectGroups(UUID subjectId) {
        return delegate.getSubjectGroups(subjectId);
    }

    @Override
    public synchronized void addSubjectGroup(UUID subjectId, String groupName) {
        InMemoryGroupService candidate = copyDelegate();
        candidate.addSubjectGroup(subjectId, groupName);
        insertMembership(subjectId, InMemoryGroupService.normalizeGroupName(groupName));
        delegate = candidate;
    }

    @Override
    public synchronized void removeSubjectGroup(UUID subjectId, String groupName) {
        InMemoryGroupService candidate = copyDelegate();
        candidate.removeSubjectGroup(subjectId, groupName);
        deleteMembership(subjectId, InMemoryGroupService.normalizeGroupName(groupName));
        delegate = candidate;
    }

    @Override
    public synchronized void setSubjectGroups(UUID subjectId, Set<String> groupNames) {
        Objects.requireNonNull(subjectId, "subjectId");
        InMemoryGroupService candidate = copyDelegate();
        candidate.setSubjectGroups(subjectId, groupNames);
        replaceMemberships(subjectId, candidate.getSubjectGroups(subjectId));
        delegate = candidate;
    }

    @Override
    public synchronized Set<UUID> getGroupMembers(String groupName) {
        return delegate.getGroupMembers(groupName);
    }

    @Override
    public synchronized Set<String> getGroupParents(String groupName) {
        return delegate.getGroupParents(groupName);
    }

    @Override
    public synchronized void addGroupParent(String groupName, String parentGroupName) {
        InMemoryGroupService candidate = copyDelegate();
        candidate.addGroupParent(groupName, parentGroupName);
        insertGroupParent(InMemoryGroupService.normalizeGroupName(groupName), InMemoryGroupService.normalizeGroupName(parentGroupName));
        delegate = candidate;
    }

    @Override
    public synchronized void removeGroupParent(String groupName, String parentGroupName) {
        InMemoryGroupService candidate = copyDelegate();
        candidate.removeGroupParent(groupName, parentGroupName);
        deleteGroupParent(InMemoryGroupService.normalizeGroupName(groupName), InMemoryGroupService.normalizeGroupName(parentGroupName));
        delegate = candidate;
    }

    private InMemoryGroupService copyDelegate() {
        return new InMemoryGroupService(delegate.groupPermissionsSnapshot(), delegate.groupDisplaysSnapshot(), delegate.groupParentsSnapshot(), delegate.membershipsSnapshot());
    }

    private GroupData loadGroups() {
        return store.read(connection -> {
            Map<String, Map<String, PermissionValue>> groupPermissions = new LinkedHashMap<>();
            Map<String, DisplayProfile> groupDisplays = new LinkedHashMap<>();
            try (PreparedStatement statement = connection.prepareStatement("SELECT name, prefix, suffix FROM groups ORDER BY name"); ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    String groupName = InMemoryGroupService.normalizeGroupName(rows.getString("name"));
                    if (groupPermissions.containsKey(groupName)) {
                        throw new PermissionStorageException("Duplicate normalized group name in SQLite groups: " + groupName);
                    }
                    groupPermissions.put(groupName, new LinkedHashMap<>());
                    groupDisplays.put(groupName, readDisplay(rows.getString("prefix"), rows.getString("suffix")));
                }
            }

            try (PreparedStatement statement = connection.prepareStatement("SELECT group_name, node, value FROM group_permissions ORDER BY group_name, node");
                    ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    String groupName = InMemoryGroupService.normalizeGroupName(rows.getString("group_name"));
                    Map<String, PermissionValue> permissions = groupPermissions.get(groupName);
                    if (permissions == null) {
                        throw new PermissionStorageException("Unknown group in SQLite group permissions: " + groupName);
                    }
                    String node = PermissionNodes.normalize(rows.getString("node"));
                    PermissionValue previousValue = permissions.put(node, parseValue(groupName, node, rows.getString("value")));
                    if (previousValue != null) {
                        throw new PermissionStorageException("Duplicate normalized permission node in SQLite group permissions for group " + groupName + ": " + node);
                    }
                }
            }

            Map<String, Set<String>> groupParents = new LinkedHashMap<>();
            try (PreparedStatement statement = connection.prepareStatement("SELECT group_name, parent_name FROM group_parents ORDER BY group_name, parent_name");
                    ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    String groupName = InMemoryGroupService.normalizeGroupName(rows.getString("group_name"));
                    String parentName = InMemoryGroupService.normalizeGroupName(rows.getString("parent_name"));
                    if (!groupPermissions.containsKey(groupName)) {
                        throw new PermissionStorageException("Unknown child group in SQLite group parents: " + groupName);
                    }
                    if (!groupPermissions.containsKey(parentName)) {
                        throw new PermissionStorageException("Unknown parent group in SQLite group parents: " + parentName);
                    }
                    Set<String> parents = groupParents.computeIfAbsent(groupName, ignored -> new LinkedHashSet<>());
                    if (!parents.add(parentName)) {
                        throw new PermissionStorageException("Duplicate normalized parent group in SQLite group parents for group " + groupName + ": " + parentName);
                    }
                }
            }

            Map<UUID, Set<String>> memberships = new LinkedHashMap<>();
            try (PreparedStatement statement = connection.prepareStatement("SELECT subject_id, group_name FROM memberships ORDER BY subject_id, group_name");
                    ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    UUID subjectId = parseSubjectId(rows.getString("subject_id"));
                    String groupName = InMemoryGroupService.normalizeGroupName(rows.getString("group_name"));
                    if (!groupPermissions.containsKey(groupName)) {
                        throw new PermissionStorageException("Unknown group in SQLite memberships: " + groupName);
                    }
                    if (GroupService.DEFAULT_GROUP.equals(groupName)) {
                        throw new PermissionStorageException("SQLite memberships must not store the implicit default group");
                    }
                    Set<String> subjectGroups = memberships.computeIfAbsent(subjectId, ignored -> new LinkedHashSet<>());
                    if (!subjectGroups.add(groupName)) {
                        throw new PermissionStorageException("Duplicate normalized group in SQLite memberships for subject " + subjectId + ": " + groupName);
                    }
                }
            }
            return new GroupData(groupPermissions, groupDisplays, groupParents, memberships);
        });
    }

    private void writeGroup(String groupName, DisplayProfile display) {
        store.write(connection -> {
            try (PreparedStatement statement = connection.prepareStatement("INSERT INTO groups (name, prefix, suffix) VALUES (?, ?, ?)")) {
                statement.setString(1, groupName);
                statement.setString(2, display.prefix().map(DisplayText::rawText).orElse(null));
                statement.setString(3, display.suffix().map(DisplayText::rawText).orElse(null));
                statement.executeUpdate();
            }
        });
    }

    private void deleteGroupRow(String groupName) {
        store.write(connection -> {
            List<String> affectedTracks = new ArrayList<>();
            try (PreparedStatement selectTracks = connection.prepareStatement("SELECT DISTINCT track_name FROM track_groups WHERE group_name = ? ORDER BY track_name")) {
                selectTracks.setString(1, groupName);
                try (ResultSet rows = selectTracks.executeQuery()) {
                    while (rows.next()) {
                        affectedTracks.add(rows.getString("track_name"));
                    }
                }
            }
            try (PreparedStatement statement = connection.prepareStatement("DELETE FROM groups WHERE name = ?")) {
                statement.setString(1, groupName);
                statement.executeUpdate();
            }
            for (String trackName : affectedTracks) {
                compactTrackGroups(connection, trackName);
            }
        });
    }

    private void renameGroupRows(String groupName, String newGroupName, DisplayProfile newDisplay) {
        store.write(connection -> {
            try (PreparedStatement insertGroup = connection.prepareStatement("INSERT INTO groups (name, prefix, suffix) VALUES (?, ?, ?)")) {
                insertGroup.setString(1, newGroupName);
                insertGroup.setString(2, newDisplay.prefix().map(DisplayText::rawText).orElse(null));
                insertGroup.setString(3, newDisplay.suffix().map(DisplayText::rawText).orElse(null));
                insertGroup.executeUpdate();
            }
            try (PreparedStatement updatePermissions = connection.prepareStatement("UPDATE group_permissions SET group_name = ? WHERE group_name = ?")) {
                updatePermissions.setString(1, newGroupName);
                updatePermissions.setString(2, groupName);
                updatePermissions.executeUpdate();
            }
            try (PreparedStatement updateChildParents = connection.prepareStatement("UPDATE group_parents SET group_name = ? WHERE group_name = ?")) {
                updateChildParents.setString(1, newGroupName);
                updateChildParents.setString(2, groupName);
                updateChildParents.executeUpdate();
            }
            try (PreparedStatement updateParentReferences = connection.prepareStatement("UPDATE group_parents SET parent_name = ? WHERE parent_name = ?")) {
                updateParentReferences.setString(1, newGroupName);
                updateParentReferences.setString(2, groupName);
                updateParentReferences.executeUpdate();
            }
            try (PreparedStatement updateMemberships = connection.prepareStatement("UPDATE memberships SET group_name = ? WHERE group_name = ?")) {
                updateMemberships.setString(1, newGroupName);
                updateMemberships.setString(2, groupName);
                updateMemberships.executeUpdate();
            }
            try (PreparedStatement updateTrackGroups = connection.prepareStatement("UPDATE track_groups SET group_name = ? WHERE group_name = ?")) {
                updateTrackGroups.setString(1, newGroupName);
                updateTrackGroups.setString(2, groupName);
                updateTrackGroups.executeUpdate();
            }
            try (PreparedStatement deleteOldGroup = connection.prepareStatement("DELETE FROM groups WHERE name = ?")) {
                deleteOldGroup.setString(1, groupName);
                deleteOldGroup.executeUpdate();
            }
        });
    }

    private void writeGroupPermission(String groupName, String node, PermissionValue value) {
        if (value == PermissionValue.UNSET) {
            deleteGroupPermission(groupName, node);
            return;
        }
        store.write(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO group_permissions (group_name, node, value) VALUES (?, ?, ?) ON CONFLICT(group_name, node) DO UPDATE SET value = excluded.value")) {
                statement.setString(1, groupName);
                statement.setString(2, node);
                statement.setString(3, value.name());
                statement.executeUpdate();
            }
        });
    }

    private void deleteGroupPermission(String groupName, String node) {
        store.write(connection -> {
            try (PreparedStatement statement = connection.prepareStatement("DELETE FROM group_permissions WHERE group_name = ? AND node = ?")) {
                statement.setString(1, groupName);
                statement.setString(2, node);
                statement.executeUpdate();
            }
        });
    }

    private void deleteGroupPermissions(String groupName) {
        store.write(connection -> {
            try (PreparedStatement statement = connection.prepareStatement("DELETE FROM group_permissions WHERE group_name = ?")) {
                statement.setString(1, groupName);
                statement.executeUpdate();
            }
        });
    }

    private void writeGroupDisplay(String groupName, DisplayProfile display) {
        store.write(connection -> {
            try (PreparedStatement statement = connection.prepareStatement("UPDATE groups SET prefix = ?, suffix = ? WHERE name = ?")) {
                statement.setString(1, display.prefix().map(DisplayText::rawText).orElse(null));
                statement.setString(2, display.suffix().map(DisplayText::rawText).orElse(null));
                statement.setString(3, groupName);
                statement.executeUpdate();
            }
        });
    }

    private static void compactTrackGroups(java.sql.Connection connection, String trackName) throws SQLException {
        List<String> groupNames = new ArrayList<>();
        try (PreparedStatement selectGroups = connection.prepareStatement("SELECT group_name FROM track_groups WHERE track_name = ? ORDER BY position")) {
            selectGroups.setString(1, trackName);
            try (ResultSet rows = selectGroups.executeQuery()) {
                while (rows.next()) {
                    groupNames.add(rows.getString("group_name"));
                }
            }
        }
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
    }

    private void insertMembership(UUID subjectId, String groupName) {
        store.write(connection -> {
            try (PreparedStatement statement = connection.prepareStatement("INSERT OR IGNORE INTO memberships (subject_id, group_name) VALUES (?, ?)")) {
                statement.setString(1, subjectId.toString());
                statement.setString(2, groupName);
                statement.executeUpdate();
            }
        });
    }

    private void deleteMembership(UUID subjectId, String groupName) {
        store.write(connection -> {
            try (PreparedStatement statement = connection.prepareStatement("DELETE FROM memberships WHERE subject_id = ? AND group_name = ?")) {
                statement.setString(1, subjectId.toString());
                statement.setString(2, groupName);
                statement.executeUpdate();
            }
        });
    }

    private void replaceMemberships(UUID subjectId, Set<String> groupNames) {
        store.write(connection -> {
            try (PreparedStatement deleteMemberships = connection.prepareStatement("DELETE FROM memberships WHERE subject_id = ?")) {
                deleteMemberships.setString(1, subjectId.toString());
                deleteMemberships.executeUpdate();
            }
            try (PreparedStatement insertMembership = connection.prepareStatement("INSERT INTO memberships (subject_id, group_name) VALUES (?, ?)")) {
                for (String groupName : groupNames) {
                    insertMembership.setString(1, subjectId.toString());
                    insertMembership.setString(2, groupName);
                    insertMembership.addBatch();
                }
                insertMembership.executeBatch();
            }
        });
    }

    private void insertGroupParent(String groupName, String parentGroupName) {
        store.write(connection -> {
            try (PreparedStatement statement = connection.prepareStatement("INSERT OR IGNORE INTO group_parents (group_name, parent_name) VALUES (?, ?)")) {
                statement.setString(1, groupName);
                statement.setString(2, parentGroupName);
                statement.executeUpdate();
            }
        });
    }

    private void deleteGroupParent(String groupName, String parentGroupName) {
        store.write(connection -> {
            try (PreparedStatement statement = connection.prepareStatement("DELETE FROM group_parents WHERE group_name = ? AND parent_name = ?")) {
                statement.setString(1, groupName);
                statement.setString(2, parentGroupName);
                statement.executeUpdate();
            }
        });
    }

    private static DisplayProfile readDisplay(String prefix, String suffix) {
        DisplayProfile display = DisplayProfile.empty();
        if (prefix != null) {
            display = display.withPrefix(DisplayText.parse(prefix));
        }
        if (suffix != null) {
            display = display.withSuffix(DisplayText.parse(suffix));
        }
        return display;
    }

    private static UUID parseSubjectId(String rawSubjectId) {
        try {
            return UUID.fromString(rawSubjectId);
        } catch (IllegalArgumentException exception) {
            throw new PermissionStorageException("Invalid subject UUID in SQLite memberships: " + rawSubjectId, exception);
        }
    }

    private static PermissionValue parseValue(String groupName, String node, String value) throws SQLException {
        try {
            return PermissionValue.valueOf(value);
        } catch (IllegalArgumentException exception) {
            throw new SQLException("Invalid permission value for group " + groupName + " node " + node + ": " + value, exception);
        }
    }

    private record GroupData(Map<String, Map<String, PermissionValue>> groupPermissions, Map<String, DisplayProfile> groupDisplays, Map<String, Set<String>> groupParents,
            Map<UUID, Set<String>> memberships) {
    }
}
