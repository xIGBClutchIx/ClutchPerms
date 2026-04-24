package me.clutchy.clutchperms.common;

import java.time.Instant;
import java.util.Collections;
import java.util.Comparator;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Thread-safe in-memory {@link SubjectMetadataService} implementation.
 */
public final class InMemorySubjectMetadataService implements SubjectMetadataService {

    private final ConcurrentMap<UUID, SubjectMetadata> subjects = new ConcurrentHashMap<>();

    /**
     * Creates an empty in-memory metadata service.
     */
    public InMemorySubjectMetadataService() {
    }

    InMemorySubjectMetadataService(Map<UUID, SubjectMetadata> initialSubjects) {
        Objects.requireNonNull(initialSubjects, "initialSubjects");
        initialSubjects.values().forEach(subject -> recordSubject(subject.subjectId(), subject.lastKnownName(), subject.lastSeen()));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void recordSubject(UUID subjectId, String lastKnownName, Instant lastSeen) {
        subjects.put(subjectId, new SubjectMetadata(subjectId, lastKnownName, lastSeen));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Optional<SubjectMetadata> getSubject(UUID subjectId) {
        Objects.requireNonNull(subjectId, "subjectId");
        return Optional.ofNullable(subjects.get(subjectId));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Map<UUID, SubjectMetadata> getSubjects() {
        return snapshot();
    }

    Map<UUID, SubjectMetadata> snapshot() {
        Map<UUID, SubjectMetadata> snapshot = new TreeMap<>(Comparator.comparing(UUID::toString));
        snapshot.putAll(subjects);
        return Collections.unmodifiableMap(snapshot);
    }
}
