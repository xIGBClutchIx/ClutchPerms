package me.clutchy.clutchperms.common.audit;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import me.clutchy.clutchperms.common.command.CommandSourceKind;
import me.clutchy.clutchperms.common.storage.SqliteStore;

/**
 * SQLite-backed audit log.
 */
final class SqliteAuditLogService implements AuditLogService {

    private final SqliteStore store;

    SqliteAuditLogService(SqliteStore store) {
        this.store = store;
    }

    @Override
    public AuditEntry append(AuditLogRecord record) {
        return store.read(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO audit_log (timestamp, actor_kind, actor_id, actor_name, action, target_type, target_key, target_display, before_json, after_json, source_command, undoable, undone) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    Statement.RETURN_GENERATED_KEYS)) {
                statement.setString(1, record.timestamp().toString());
                statement.setString(2, record.actorKind().name());
                statement.setString(3, record.actorId().map(UUID::toString).orElse(null));
                statement.setString(4, record.actorName().orElse(null));
                statement.setString(5, record.action());
                statement.setString(6, record.targetType());
                statement.setString(7, record.targetKey());
                statement.setString(8, record.targetDisplay());
                statement.setString(9, record.beforeJson());
                statement.setString(10, record.afterJson());
                statement.setString(11, record.sourceCommand());
                statement.setInt(12, record.undoable() ? 1 : 0);
                statement.setInt(13, 0);
                statement.executeUpdate();
                try (ResultSet keys = statement.getGeneratedKeys()) {
                    if (!keys.next()) {
                        throw new IllegalStateException("audit log insert did not return an id");
                    }
                    return get(keys.getLong(1)).orElseThrow();
                }
            }
        });
    }

    @Override
    public Optional<AuditEntry> get(long id) {
        return store.read(connection -> {
            try (PreparedStatement statement = connection.prepareStatement("SELECT * FROM audit_log WHERE id = ?")) {
                statement.setLong(1, id);
                try (ResultSet rows = statement.executeQuery()) {
                    if (!rows.next()) {
                        return Optional.empty();
                    }
                    return Optional.of(readEntry(rows));
                }
            }
        });
    }

    @Override
    public List<AuditEntry> listNewestFirst() {
        return store.read(connection -> {
            List<AuditEntry> entries = new ArrayList<>();
            try (PreparedStatement statement = connection.prepareStatement("SELECT * FROM audit_log ORDER BY id DESC"); ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    entries.add(readEntry(rows));
                }
            }
            return List.copyOf(entries);
        });
    }

    @Override
    public int pruneOlderThan(Instant cutoff) {
        int[] deleted = {0};
        store.write(connection -> {
            try (PreparedStatement statement = connection.prepareStatement("DELETE FROM audit_log WHERE timestamp < ?")) {
                statement.setString(1, cutoff.toString());
                deleted[0] = statement.executeUpdate();
            }
        });
        return deleted[0];
    }

    @Override
    public int pruneBeyondNewest(int retainedCount) {
        if (retainedCount < 0) {
            throw new IllegalArgumentException("retainedCount must be non-negative");
        }
        int[] deleted = {0};
        store.write(connection -> {
            try (PreparedStatement statement = connection.prepareStatement("DELETE FROM audit_log WHERE id NOT IN (SELECT id FROM audit_log ORDER BY id DESC LIMIT ?)")) {
                statement.setInt(1, retainedCount);
                deleted[0] = statement.executeUpdate();
            }
        });
        return deleted[0];
    }

    @Override
    public void markUndone(long id, long undoEntryId, Instant undoneAt, Optional<UUID> actorId, Optional<String> actorName) {
        store.write(connection -> {
            try (PreparedStatement statement = connection
                    .prepareStatement("UPDATE audit_log SET undone = 1, undone_by_entry_id = ?, undone_at = ?, undone_by_actor_id = ?, undone_by_actor_name = ? WHERE id = ?")) {
                statement.setLong(1, undoEntryId);
                statement.setString(2, undoneAt.toString());
                statement.setString(3, actorId.map(UUID::toString).orElse(null));
                statement.setString(4, actorName.orElse(null));
                statement.setLong(5, id);
                if (statement.executeUpdate() == 0) {
                    throw new IllegalArgumentException("unknown audit entry: " + id);
                }
            }
        });
    }

    private static AuditEntry readEntry(ResultSet rows) throws java.sql.SQLException {
        return new AuditEntry(rows.getLong("id"), Instant.parse(rows.getString("timestamp")), CommandSourceKind.valueOf(rows.getString("actor_kind")),
                optionalUuid(rows.getString("actor_id")), Optional.ofNullable(rows.getString("actor_name")), rows.getString("action"), rows.getString("target_type"),
                rows.getString("target_key"), rows.getString("target_display"), rows.getString("before_json"), rows.getString("after_json"), rows.getString("source_command"),
                rows.getInt("undoable") != 0, rows.getInt("undone") != 0, optionalLong(rows.getObject("undone_by_entry_id")), optionalInstant(rows.getString("undone_at")),
                optionalUuid(rows.getString("undone_by_actor_id")), Optional.ofNullable(rows.getString("undone_by_actor_name")));
    }

    private static Optional<UUID> optionalUuid(String rawValue) {
        return rawValue == null ? Optional.empty() : Optional.of(UUID.fromString(rawValue));
    }

    private static Optional<Long> optionalLong(Object rawValue) {
        return rawValue == null ? Optional.empty() : Optional.of(((Number) rawValue).longValue());
    }

    private static Optional<Instant> optionalInstant(String rawValue) {
        return rawValue == null ? Optional.empty() : Optional.of(Instant.parse(rawValue));
    }
}
