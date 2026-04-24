package me.clutchy.clutchperms.common;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Immutable metadata captured for a permission subject when the subject is observed by a platform.
 *
 * @param subjectId unique identifier for the subject
 * @param lastKnownName most recent platform-provided display name for the subject
 * @param lastSeen time when the subject was last observed
 */
public record SubjectMetadata(UUID subjectId, String lastKnownName, Instant lastSeen) {

    /**
     * Creates validated subject metadata.
     */
    public SubjectMetadata {
        Objects.requireNonNull(subjectId, "subjectId");
        lastKnownName = Objects.requireNonNull(lastKnownName, "lastKnownName").trim();
        if (lastKnownName.isEmpty()) {
            throw new IllegalArgumentException("lastKnownName must not be blank");
        }
        Objects.requireNonNull(lastSeen, "lastSeen");
    }
}
