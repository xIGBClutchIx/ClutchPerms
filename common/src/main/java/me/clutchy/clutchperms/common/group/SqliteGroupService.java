package me.clutchy.clutchperms.common.group;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
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
        commit(candidate);
    }

    @Override
    public synchronized void deleteGroup(String groupName) {
        InMemoryGroupService candidate = copyDelegate();
        candidate.deleteGroup(groupName);
        commit(candidate);
    }

    @Override
    public synchronized void renameGroup(String groupName, String newGroupName) {
        InMemoryGroupService candidate = copyDelegate();
        candidate.renameGroup(groupName, newGroupName);
        commit(candidate);
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
        commit(candidate);
    }

    @Override
    public synchronized void clearGroupPermission(String groupName, String node) {
        InMemoryGroupService candidate = copyDelegate();
        candidate.clearGroupPermission(groupName, node);
        commit(candidate);
    }

    @Override
    public synchronized int clearGroupPermissions(String groupName) {
        InMemoryGroupService candidate = copyDelegate();
        int removedPermissions = candidate.clearGroupPermissions(groupName);
        if (removedPermissions == 0) {
            return 0;
        }
        commit(candidate);
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
        commit(candidate);
    }

    @Override
    public synchronized void clearGroupPrefix(String groupName) {
        InMemoryGroupService candidate = copyDelegate();
        candidate.clearGroupPrefix(groupName);
        commit(candidate);
    }

    @Override
    public synchronized void setGroupSuffix(String groupName, DisplayText suffix) {
        InMemoryGroupService candidate = copyDelegate();
        candidate.setGroupSuffix(groupName, suffix);
        commit(candidate);
    }

    @Override
    public synchronized void clearGroupSuffix(String groupName) {
        InMemoryGroupService candidate = copyDelegate();
        candidate.clearGroupSuffix(groupName);
        commit(candidate);
    }

    @Override
    public synchronized Set<String> getSubjectGroups(UUID subjectId) {
        return delegate.getSubjectGroups(subjectId);
    }

    @Override
    public synchronized void addSubjectGroup(UUID subjectId, String groupName) {
        InMemoryGroupService candidate = copyDelegate();
        candidate.addSubjectGroup(subjectId, groupName);
        commit(candidate);
    }

    @Override
    public synchronized void removeSubjectGroup(UUID subjectId, String groupName) {
        InMemoryGroupService candidate = copyDelegate();
        candidate.removeSubjectGroup(subjectId, groupName);
        commit(candidate);
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
        commit(candidate);
    }

    @Override
    public synchronized void removeGroupParent(String groupName, String parentGroupName) {
        InMemoryGroupService candidate = copyDelegate();
        candidate.removeGroupParent(groupName, parentGroupName);
        commit(candidate);
    }

    private InMemoryGroupService copyDelegate() {
        return new InMemoryGroupService(delegate.groupPermissionsSnapshot(), delegate.groupDisplaysSnapshot(), delegate.groupParentsSnapshot(), delegate.membershipsSnapshot());
    }

    private void commit(InMemoryGroupService candidate) {
        saveGroups(candidate.groupPermissionsSnapshot(), candidate.groupDisplaysSnapshot(), candidate.groupParentsSnapshot(), candidate.membershipsSnapshot());
        delegate = candidate;
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

    private void saveGroups(Map<String, Map<String, PermissionValue>> groupPermissionsSnapshot, Map<String, DisplayProfile> groupDisplaysSnapshot,
            Map<String, Set<String>> groupParentsSnapshot, Map<UUID, Set<String>> membershipsSnapshot) {
        store.write(connection -> {
            executeUpdate(connection.prepareStatement("DELETE FROM memberships"));
            executeUpdate(connection.prepareStatement("DELETE FROM group_parents"));
            executeUpdate(connection.prepareStatement("DELETE FROM group_permissions"));
            executeUpdate(connection.prepareStatement("DELETE FROM groups"));

            try (PreparedStatement insertGroup = connection.prepareStatement("INSERT INTO groups (name, prefix, suffix) VALUES (?, ?, ?)")) {
                for (String groupName : groupPermissionsSnapshot.keySet()) {
                    DisplayProfile display = groupDisplaysSnapshot.getOrDefault(groupName, DisplayProfile.empty());
                    insertGroup.setString(1, groupName);
                    insertGroup.setString(2, display.prefix().map(DisplayText::rawText).orElse(null));
                    insertGroup.setString(3, display.suffix().map(DisplayText::rawText).orElse(null));
                    insertGroup.addBatch();
                }
                insertGroup.executeBatch();
            }

            try (PreparedStatement insertPermission = connection.prepareStatement("INSERT INTO group_permissions (group_name, node, value) VALUES (?, ?, ?)")) {
                for (Map.Entry<String, Map<String, PermissionValue>> groupEntry : groupPermissionsSnapshot.entrySet()) {
                    for (Map.Entry<String, PermissionValue> permissionEntry : groupEntry.getValue().entrySet()) {
                        if (permissionEntry.getValue() == PermissionValue.UNSET) {
                            continue;
                        }
                        insertPermission.setString(1, groupEntry.getKey());
                        insertPermission.setString(2, permissionEntry.getKey());
                        insertPermission.setString(3, permissionEntry.getValue().name());
                        insertPermission.addBatch();
                    }
                }
                insertPermission.executeBatch();
            }

            try (PreparedStatement insertParent = connection.prepareStatement("INSERT INTO group_parents (group_name, parent_name) VALUES (?, ?)")) {
                for (Map.Entry<String, Set<String>> groupEntry : groupParentsSnapshot.entrySet()) {
                    for (String parentName : groupEntry.getValue()) {
                        insertParent.setString(1, groupEntry.getKey());
                        insertParent.setString(2, parentName);
                        insertParent.addBatch();
                    }
                }
                insertParent.executeBatch();
            }

            try (PreparedStatement insertMembership = connection.prepareStatement("INSERT INTO memberships (subject_id, group_name) VALUES (?, ?)")) {
                for (Map.Entry<UUID, Set<String>> membershipEntry : membershipsSnapshot.entrySet()) {
                    for (String groupName : membershipEntry.getValue()) {
                        insertMembership.setString(1, membershipEntry.getKey().toString());
                        insertMembership.setString(2, groupName);
                        insertMembership.addBatch();
                    }
                }
                insertMembership.executeBatch();
            }
        });
    }

    private static void executeUpdate(PreparedStatement statement) throws SQLException {
        try (statement) {
            statement.executeUpdate();
        }
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
