package me.clutchy.clutchperms.common.audit;

import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * In-memory audit log for tests and non-persistent command environments.
 */
final class InMemoryAuditLogService implements AuditLogService {

    private final Map<Long, AuditEntry> entries = new LinkedHashMap<>();

    private long nextId = 1;

    @Override
    public synchronized AuditEntry append(AuditLogRecord record) {
        long id = nextId++;
        AuditEntry entry = new AuditEntry(id, record.timestamp(), record.actorKind(), record.actorId(), record.actorName(), record.action(), record.targetType(),
                record.targetKey(), record.targetDisplay(), record.beforeJson(), record.afterJson(), record.sourceCommand(), record.undoable(), false, Optional.empty(),
                Optional.empty(), Optional.empty(), Optional.empty());
        entries.put(id, entry);
        return entry;
    }

    @Override
    public synchronized Optional<AuditEntry> get(long id) {
        return Optional.ofNullable(entries.get(id));
    }

    @Override
    public synchronized List<AuditEntry> listNewestFirst() {
        return entries.values().stream().sorted(Comparator.comparingLong(AuditEntry::id).reversed()).toList();
    }

    @Override
    public synchronized int pruneOlderThan(Instant cutoff) {
        List<Long> prunedIds = entries.values().stream().filter(entry -> entry.timestamp().isBefore(cutoff)).map(AuditEntry::id).toList();
        prunedIds.forEach(entries::remove);
        return prunedIds.size();
    }

    @Override
    public synchronized int pruneBeyondNewest(int retainedCount) {
        if (retainedCount < 0) {
            throw new IllegalArgumentException("retainedCount must be non-negative");
        }
        List<Long> prunedIds = entries.values().stream().sorted(Comparator.comparingLong(AuditEntry::id).reversed()).skip(retainedCount).map(AuditEntry::id).toList();
        prunedIds.forEach(entries::remove);
        return prunedIds.size();
    }

    @Override
    public synchronized void markUndone(long id, long undoEntryId, Instant undoneAt, Optional<UUID> actorId, Optional<String> actorName) {
        AuditEntry current = entries.get(id);
        if (current == null) {
            throw new IllegalArgumentException("unknown audit entry: " + id);
        }
        entries.put(id,
                new AuditEntry(current.id(), current.timestamp(), current.actorKind(), current.actorId(), current.actorName(), current.action(), current.targetType(),
                        current.targetKey(), current.targetDisplay(), current.beforeJson(), current.afterJson(), current.sourceCommand(), current.undoable(), true,
                        Optional.of(undoEntryId), Optional.of(undoneAt), actorId, actorName));
    }
}
