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

            AuditEntry entry = auditLogService.append(new AuditLogRecord(Instant.parse("2026-04-27T12:00:00Z"), CommandSourceKind.CONSOLE, Optional.empty(), Optional.of("console"),
                    "user.permission.set", "user-permissions", "target", "Target", "{\"permissions\":{}}", "{\"permissions\":{\"example.node\":\"TRUE\"}}",
                    "/clutchperms user Target set example.node true", true));

            assertEquals(1, entry.id());
            assertEquals("user.permission.set", auditLogService.get(entry.id()).orElseThrow().action());
            assertEquals(1, auditLogService.listNewestFirst().size());
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
}
