package me.clutchy.clutchperms.common.audit;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Stores command-layer audit history.
 */
public interface AuditLogService {

    /**
     * Appends one audit row.
     *
     * @param record row to append
     * @return stored row with generated id
     */
    AuditEntry append(AuditLogRecord record);

    /**
     * Looks up one audit row by id.
     *
     * @param id audit id
     * @return matching row, or empty
     */
    Optional<AuditEntry> get(long id);

    /**
     * Lists audit rows newest first.
     *
     * @return immutable newest-first rows
     */
    List<AuditEntry> listNewestFirst();

    /**
     * Marks an audit row as undone.
     *
     * @param id original audit id
     * @param undoEntryId audit id for the undo operation
     * @param undoneAt undo timestamp
     * @param actorId undo actor UUID, if any
     * @param actorName undo actor display name, if any
     */
    void markUndone(long id, long undoEntryId, Instant undoneAt, Optional<UUID> actorId, Optional<String> actorName);
}
