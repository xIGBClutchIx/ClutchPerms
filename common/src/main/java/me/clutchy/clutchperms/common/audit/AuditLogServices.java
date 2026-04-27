package me.clutchy.clutchperms.common.audit;

import me.clutchy.clutchperms.common.storage.SqliteStore;

/**
 * Audit log service factories.
 */
public final class AuditLogServices {

    /**
     * Creates an in-memory audit log.
     *
     * @return in-memory audit log
     */
    public static AuditLogService inMemory() {
        return new InMemoryAuditLogService();
    }

    /**
     * Creates a SQLite-backed audit log.
     *
     * @param store SQLite store
     * @return SQLite-backed audit log
     */
    public static AuditLogService sqlite(SqliteStore store) {
        return new SqliteAuditLogService(store);
    }

    private AuditLogServices() {
    }
}
