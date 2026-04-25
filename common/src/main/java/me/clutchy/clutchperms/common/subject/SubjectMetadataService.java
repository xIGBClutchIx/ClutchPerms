package me.clutchy.clutchperms.common.subject;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import me.clutchy.clutchperms.common.display.DisplayProfile;
import me.clutchy.clutchperms.common.display.DisplayText;

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

    /**
     * Looks up direct display values for one subject.
     *
     * @param subjectId unique identifier for the subject
     * @return direct display values, or an empty profile when unset
     */
    DisplayProfile getSubjectDisplay(UUID subjectId);

    /**
     * Lists every subject with direct display values.
     *
     * @return immutable snapshot of direct subject display values keyed by subject UUID
     */
    Map<UUID, DisplayProfile> getSubjectDisplays();

    /**
     * Stores a direct subject prefix.
     *
     * @param subjectId unique identifier for the subject
     * @param prefix prefix to store
     */
    void setSubjectPrefix(UUID subjectId, DisplayText prefix);

    /**
     * Clears a direct subject prefix.
     *
     * @param subjectId unique identifier for the subject
     */
    void clearSubjectPrefix(UUID subjectId);

    /**
     * Stores a direct subject suffix.
     *
     * @param subjectId unique identifier for the subject
     * @param suffix suffix to store
     */
    void setSubjectSuffix(UUID subjectId, DisplayText suffix);

    /**
     * Clears a direct subject suffix.
     *
     * @param subjectId unique identifier for the subject
     */
    void clearSubjectSuffix(UUID subjectId);
}
