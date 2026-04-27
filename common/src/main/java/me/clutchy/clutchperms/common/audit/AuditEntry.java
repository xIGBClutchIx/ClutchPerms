package me.clutchy.clutchperms.common.audit;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import me.clutchy.clutchperms.common.command.CommandSourceKind;

/**
 * One persisted command-layer audit history row.
 */
public record AuditEntry(long id, Instant timestamp, CommandSourceKind actorKind, Optional<UUID> actorId, Optional<String> actorName, String action, String targetType,
        String targetKey, String targetDisplay, String beforeJson, String afterJson, String sourceCommand, boolean undoable, boolean undone, Optional<Long> undoneByEntryId,
        Optional<Instant> undoneAt, Optional<UUID> undoneByActorId, Optional<String> undoneByActorName) {

    /**
     * Validates audit entry fields.
     */
    public AuditEntry {
        timestamp = Objects.requireNonNull(timestamp, "timestamp");
        actorKind = Objects.requireNonNull(actorKind, "actorKind");
        actorId = Objects.requireNonNull(actorId, "actorId");
        actorName = Objects.requireNonNull(actorName, "actorName");
        action = requireText(action, "action");
        targetType = requireText(targetType, "targetType");
        targetKey = requireText(targetKey, "targetKey");
        targetDisplay = requireText(targetDisplay, "targetDisplay");
        beforeJson = Objects.requireNonNull(beforeJson, "beforeJson");
        afterJson = Objects.requireNonNull(afterJson, "afterJson");
        sourceCommand = Objects.requireNonNull(sourceCommand, "sourceCommand");
        undoneByEntryId = Objects.requireNonNull(undoneByEntryId, "undoneByEntryId");
        undoneAt = Objects.requireNonNull(undoneAt, "undoneAt");
        undoneByActorId = Objects.requireNonNull(undoneByActorId, "undoneByActorId");
        undoneByActorName = Objects.requireNonNull(undoneByActorName, "undoneByActorName");
    }

    private static String requireText(String value, String name) {
        String text = Objects.requireNonNull(value, name).trim();
        if (text.isEmpty()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return text;
    }
}
