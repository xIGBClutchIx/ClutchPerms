package me.clutchy.clutchperms.common.audit;

import java.nio.file.Path;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import me.clutchy.clutchperms.common.command.CommandSourceKind;
import me.clutchy.clutchperms.common.storage.SqliteDependencyMode;
import me.clutchy.clutchperms.common.storage.SqliteStore;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies audit log storage.
 */
class AuditLogServicesTest {

    @Test
    void freshSqliteStorageMaterializesAuditLog(@TempDir Path temporaryDirectory) {
        Path databaseFile = temporaryDirectory.resolve("database.db");

        try (SqliteStore store = SqliteStore.open(databaseFile, SqliteDependencyMode.ANY_VISIBLE)) {
            assertTrue(tableExists(store, "audit_log"));
            assertEquals(SqliteStore.CURRENT_SCHEMA_VERSION, schemaVersion(store));
        }
    }

    @Test
    void existingSqliteStorageMaterializesAuditLogWithoutSchemaVersionBump(@TempDir Path temporaryDirectory) {
        Path databaseFile = temporaryDirectory.resolve("database.db");
        try (SqliteStore ignored = SqliteStore.open(databaseFile, SqliteDependencyMode.ANY_VISIBLE)) {
            ignored.write(connection -> {
                try (Statement statement = connection.createStatement()) {
                    statement.executeUpdate("DROP TABLE audit_log");
                }
            });
            assertEquals(SqliteStore.CURRENT_SCHEMA_VERSION, schemaVersion(ignored));
        }

        try (SqliteStore store = SqliteStore.open(databaseFile, SqliteDependencyMode.ANY_VISIBLE)) {
            assertTrue(tableExists(store, "audit_log"));
            assertEquals(SqliteStore.CURRENT_SCHEMA_VERSION, schemaVersion(store));
        }
    }

    @Test
    void auditRowsRoundTripThroughSqlite(@TempDir Path temporaryDirectory) {
        Path databaseFile = temporaryDirectory.resolve("database.db");
        try (SqliteStore store = SqliteStore.open(databaseFile, SqliteDependencyMode.ANY_VISIBLE)) {
            AuditLogService auditLogService = AuditLogServices.sqlite(store);

            AuditEntry entry = auditLogService.append(record(Instant.parse("2026-04-27T12:00:00Z"), "user.permission.set"));

            assertEquals(1, entry.id());
            assertEquals("user.permission.set", auditLogService.get(entry.id()).orElseThrow().action());
            assertEquals(1, auditLogService.listNewestFirst().size());
        }
    }

    @Test
    void auditPruneDeletesOlderRowsInMemory() {
        AuditLogService auditLogService = AuditLogServices.inMemory();
        auditLogService.append(record(Instant.parse("2026-01-01T00:00:00Z"), "old"));
        auditLogService.append(record(Instant.parse("2026-04-01T00:00:00Z"), "new"));

        assertEquals(1, auditLogService.pruneOlderThan(Instant.parse("2026-02-01T00:00:00Z")));

        assertEquals(1, auditLogService.listNewestFirst().size());
        assertEquals("new", auditLogService.listNewestFirst().getFirst().action());
    }

    @Test
    void auditPruneKeepsNewestRowsInMemory() {
        AuditLogService auditLogService = AuditLogServices.inMemory();
        auditLogService.append(record(Instant.parse("2026-01-01T00:00:00Z"), "first"));
        auditLogService.append(record(Instant.parse("2026-01-02T00:00:00Z"), "second"));
        auditLogService.append(record(Instant.parse("2026-01-03T00:00:00Z"), "third"));

        assertEquals(1, auditLogService.pruneBeyondNewest(2));

        assertEquals(2, auditLogService.listNewestFirst().size());
        assertEquals("third", auditLogService.listNewestFirst().get(0).action());
        assertEquals("second", auditLogService.listNewestFirst().get(1).action());
    }

    @Test
    void auditPruneDeletesOlderRowsThroughSqlite(@TempDir Path temporaryDirectory) {
        Path databaseFile = temporaryDirectory.resolve("database.db");
        try (SqliteStore store = SqliteStore.open(databaseFile, SqliteDependencyMode.ANY_VISIBLE)) {
            AuditLogService auditLogService = AuditLogServices.sqlite(store);
            auditLogService.append(record(Instant.parse("2026-01-01T00:00:00Z"), "old"));
            auditLogService.append(record(Instant.parse("2026-04-01T00:00:00Z"), "new"));

            assertEquals(1, auditLogService.pruneOlderThan(Instant.parse("2026-02-01T00:00:00Z")));

            assertEquals(1, auditLogService.listNewestFirst().size());
            assertEquals("new", auditLogService.listNewestFirst().getFirst().action());
        }
    }

    @Test
    void auditPruneKeepsNewestRowsThroughSqlite(@TempDir Path temporaryDirectory) {
        Path databaseFile = temporaryDirectory.resolve("database.db");
        try (SqliteStore store = SqliteStore.open(databaseFile, SqliteDependencyMode.ANY_VISIBLE)) {
            AuditLogService auditLogService = AuditLogServices.sqlite(store);
            auditLogService.append(record(Instant.parse("2026-01-01T00:00:00Z"), "first"));
            auditLogService.append(record(Instant.parse("2026-01-02T00:00:00Z"), "second"));
            auditLogService.append(record(Instant.parse("2026-01-03T00:00:00Z"), "third"));

            assertEquals(1, auditLogService.pruneBeyondNewest(2));

            assertEquals(2, auditLogService.listNewestFirst().size());
            assertEquals("third", auditLogService.listNewestFirst().get(0).action());
            assertEquals("second", auditLogService.listNewestFirst().get(1).action());
        }
    }

    private static boolean tableExists(SqliteStore store, String tableName) {
        return store.read(connection -> {
            try (Statement statement = connection.createStatement();
                    ResultSet rows = statement.executeQuery("SELECT name FROM sqlite_master WHERE type = 'table' AND name = '" + tableName + "'")) {
                return rows.next();
            }
        });
    }

    private static int schemaVersion(SqliteStore store) {
        return store.read(connection -> {
            try (Statement statement = connection.createStatement(); ResultSet rows = statement.executeQuery("SELECT version FROM schema_version")) {
                rows.next();
                return rows.getInt(1);
            }
        });
    }

    private static AuditLogRecord record(Instant timestamp, String action) {
        return new AuditLogRecord(timestamp, CommandSourceKind.CONSOLE, Optional.empty(), Optional.of("console"), action, "user-permissions", "target", "Target",
                "{\"permissions\":{}}", "{\"permissions\":{\"example.node\":\"TRUE\"}}", "/clutchperms user Target set example.node true", true);
    }
}
