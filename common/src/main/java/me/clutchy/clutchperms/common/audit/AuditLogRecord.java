package me.clutchy.clutchperms.common.audit;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import me.clutchy.clutchperms.common.command.CommandSourceKind;

/**
 * Data required to append one audit history row.
 */
public record AuditLogRecord(Instant timestamp, CommandSourceKind actorKind, Optional<UUID> actorId, Optional<String> actorName, String action, String targetType, String targetKey,
        String targetDisplay, String beforeJson, String afterJson, String sourceCommand, boolean undoable) {

    /**
     * Validates audit log record fields.
     */
    public AuditLogRecord {
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
    }

    private static String requireText(String value, String name) {
        String text = Objects.requireNonNull(value, name).trim();
        if (text.isEmpty()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return text;
    }
}
