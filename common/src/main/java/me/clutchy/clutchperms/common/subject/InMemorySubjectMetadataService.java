package me.clutchy.clutchperms.common.subject;

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

import me.clutchy.clutchperms.common.display.DisplayProfile;
import me.clutchy.clutchperms.common.display.DisplayText;

/**
 * Thread-safe in-memory {@link SubjectMetadataService} implementation.
 */
public final class InMemorySubjectMetadataService implements SubjectMetadataService {

    private final ConcurrentMap<UUID, SubjectMetadata> subjects = new ConcurrentHashMap<>();

    private final ConcurrentMap<UUID, DisplayProfile> displays = new ConcurrentHashMap<>();

    /**
     * Creates an empty in-memory metadata service.
     */
    public InMemorySubjectMetadataService() {
    }

    InMemorySubjectMetadataService(Map<UUID, SubjectMetadata> initialSubjects) {
        this(initialSubjects, Map.of());
    }

    InMemorySubjectMetadataService(Map<UUID, SubjectMetadata> initialSubjects, Map<UUID, DisplayProfile> initialDisplays) {
        Objects.requireNonNull(initialSubjects, "initialSubjects");
        initialSubjects.values().forEach(subject -> recordSubject(subject.subjectId(), subject.lastKnownName(), subject.lastSeen()));
        Objects.requireNonNull(initialDisplays, "initialDisplays").forEach((subjectId, display) -> {
            if (!display.isEmpty()) {
                displays.put(subjectId, display);
            }
        });
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

    /**
     * {@inheritDoc}
     */
    @Override
    public DisplayProfile getSubjectDisplay(UUID subjectId) {
        Objects.requireNonNull(subjectId, "subjectId");
        return displays.getOrDefault(subjectId, DisplayProfile.empty());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Map<UUID, DisplayProfile> getSubjectDisplays() {
        return displaySnapshot();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setSubjectPrefix(UUID subjectId, DisplayText prefix) {
        Objects.requireNonNull(subjectId, "subjectId");
        Objects.requireNonNull(prefix, "prefix");
        displays.compute(subjectId, (ignored, display) -> Objects.requireNonNullElseGet(display, DisplayProfile::empty).withPrefix(prefix));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void clearSubjectPrefix(UUID subjectId) {
        Objects.requireNonNull(subjectId, "subjectId");
        displays.computeIfPresent(subjectId, (ignored, display) -> {
            DisplayProfile updated = display.withoutPrefix();
            return updated.isEmpty() ? null : updated;
        });
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setSubjectSuffix(UUID subjectId, DisplayText suffix) {
        Objects.requireNonNull(subjectId, "subjectId");
        Objects.requireNonNull(suffix, "suffix");
        displays.compute(subjectId, (ignored, display) -> Objects.requireNonNullElseGet(display, DisplayProfile::empty).withSuffix(suffix));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void clearSubjectSuffix(UUID subjectId) {
        Objects.requireNonNull(subjectId, "subjectId");
        displays.computeIfPresent(subjectId, (ignored, display) -> {
            DisplayProfile updated = display.withoutSuffix();
            return updated.isEmpty() ? null : updated;
        });
    }

    Map<UUID, SubjectMetadata> snapshot() {
        Map<UUID, SubjectMetadata> snapshot = new TreeMap<>(Comparator.comparing(UUID::toString));
        snapshot.putAll(subjects);
        return Collections.unmodifiableMap(snapshot);
    }

    Map<UUID, DisplayProfile> displaySnapshot() {
        Map<UUID, DisplayProfile> snapshot = new TreeMap<>(Comparator.comparing(UUID::toString));
        displays.forEach((subjectId, display) -> {
            if (!display.isEmpty()) {
                snapshot.put(subjectId, display);
            }
        });
        return Collections.unmodifiableMap(snapshot);
    }
}
