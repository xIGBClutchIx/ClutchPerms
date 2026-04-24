package me.clutchy.clutchperms.common;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Stores lightweight metadata for subjects known to ClutchPerms.
 */
public interface SubjectMetadataService {

    /**
     * Records the latest known metadata for a subject.
     *
     * @param subjectId unique identifier for the subject
     * @param lastKnownName latest platform-provided subject name
     * @param lastSeen time when the subject was observed
     */
    void recordSubject(UUID subjectId, String lastKnownName, Instant lastSeen);

    /**
     * Looks up metadata for one subject.
     *
     * @param subjectId unique identifier for the subject
     * @return stored subject metadata, or empty when the subject has not been recorded
     */
    Optional<SubjectMetadata> getSubject(UUID subjectId);

    /**
     * Lists every known subject.
     *
     * @return immutable snapshot of known subject metadata keyed by subject UUID
     */
    Map<UUID, SubjectMetadata> getSubjects();
}
