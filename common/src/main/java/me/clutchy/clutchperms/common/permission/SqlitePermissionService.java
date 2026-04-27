package me.clutchy.clutchperms.common.permission;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import me.clutchy.clutchperms.common.storage.PermissionStorageException;
import me.clutchy.clutchperms.common.storage.SqliteStore;

/**
 * SQLite-backed permission service that persists direct subject assignments after every mutation.
 */
final class SqlitePermissionService implements PermissionService {

    private final SqliteStore store;

    private InMemoryPermissionService delegate;

    SqlitePermissionService(SqliteStore store) {
        this.store = store;
        this.delegate = new InMemoryPermissionService(loadPermissions());
    }

    @Override
    public synchronized PermissionValue getPermission(UUID subjectId, String node) {
        return delegate.getPermission(subjectId, node);
    }

    @Override
    public synchronized Map<String, PermissionValue> getPermissions(UUID subjectId) {
        return delegate.getPermissions(subjectId);
    }

    @Override
    public synchronized void setPermission(UUID subjectId, String node, PermissionValue value) {
        InMemoryPermissionService candidate = copyDelegate();
        candidate.setPermission(subjectId, node, value);
        PermissionValue storedValue = candidate.getPermission(subjectId, node);
        writePermission(subjectId, InMemoryPermissionService.normalizeNode(node), storedValue);
        delegate = candidate;
    }

    @Override
    public synchronized void clearPermission(UUID subjectId, String node) {
        InMemoryPermissionService candidate = copyDelegate();
        candidate.clearPermission(subjectId, node);
        deletePermission(subjectId, InMemoryPermissionService.normalizeNode(node));
        delegate = candidate;
    }

    @Override
    public synchronized int clearPermissions(UUID subjectId) {
        InMemoryPermissionService candidate = copyDelegate();
        int removedPermissions = candidate.clearPermissions(subjectId);
        if (removedPermissions == 0) {
            return 0;
        }
        deleteSubjectPermissions(subjectId);
        delegate = candidate;
        return removedPermissions;
    }

    private InMemoryPermissionService copyDelegate() {
        return new InMemoryPermissionService(delegate.snapshot());
    }

    private Map<UUID, Map<String, PermissionValue>> loadPermissions() {
        return store.read(connection -> {
            Map<UUID, Map<String, PermissionValue>> permissions = new LinkedHashMap<>();
            try (PreparedStatement statement = connection.prepareStatement("SELECT subject_id, node, value FROM subject_permissions ORDER BY subject_id, node");
                    ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    UUID subjectId = parseSubjectId(rows.getString("subject_id"));
                    String node = PermissionNodes.normalize(rows.getString("node"));
                    PermissionValue value = parseValue(subjectId, node, rows.getString("value"));
                    PermissionValue previousValue = permissions.computeIfAbsent(subjectId, ignored -> new LinkedHashMap<>()).put(node, value);
                    if (previousValue != null) {
                        throw new PermissionStorageException("Duplicate normalized permission node in SQLite permissions for subject " + subjectId + ": " + node);
                    }
                }
            }
            return permissions;
        });
    }

    private void writePermission(UUID subjectId, String node, PermissionValue value) {
        if (value == PermissionValue.UNSET) {
            deletePermission(subjectId, node);
            return;
        }
        store.write(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO subject_permissions (subject_id, node, value) VALUES (?, ?, ?) ON CONFLICT(subject_id, node) DO UPDATE SET value = excluded.value")) {
                statement.setString(1, subjectId.toString());
                statement.setString(2, node);
                statement.setString(3, value.name());
                statement.executeUpdate();
            }
        });
    }

    private void deletePermission(UUID subjectId, String node) {
        store.write(connection -> {
            try (PreparedStatement statement = connection.prepareStatement("DELETE FROM subject_permissions WHERE subject_id = ? AND node = ?")) {
                statement.setString(1, subjectId.toString());
                statement.setString(2, node);
                statement.executeUpdate();
            }
        });
    }

    private void deleteSubjectPermissions(UUID subjectId) {
        store.write(connection -> {
            try (PreparedStatement statement = connection.prepareStatement("DELETE FROM subject_permissions WHERE subject_id = ?")) {
                statement.setString(1, subjectId.toString());
                statement.executeUpdate();
            }
        });
    }

    private static UUID parseSubjectId(String rawSubjectId) {
        try {
            return UUID.fromString(rawSubjectId);
        } catch (IllegalArgumentException exception) {
            throw new PermissionStorageException("Invalid subject UUID in SQLite permissions: " + rawSubjectId, exception);
        }
    }

    private static PermissionValue parseValue(UUID subjectId, String node, String value) throws SQLException {
        try {
            return PermissionValue.valueOf(value);
        } catch (IllegalArgumentException exception) {
            throw new SQLException("Invalid permission value for subject " + subjectId + " node " + node + ": " + value, exception);
        }
    }
}
