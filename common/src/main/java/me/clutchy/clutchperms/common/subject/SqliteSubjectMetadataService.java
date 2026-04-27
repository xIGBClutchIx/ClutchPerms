package me.clutchy.clutchperms.common.subject;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.DateTimeException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import me.clutchy.clutchperms.common.display.DisplayProfile;
import me.clutchy.clutchperms.common.display.DisplayText;
import me.clutchy.clutchperms.common.storage.PermissionStorageException;
import me.clutchy.clutchperms.common.storage.SqliteStore;

/**
 * SQLite-backed subject metadata service.
 */
final class SqliteSubjectMetadataService implements SubjectMetadataService {

    private final SqliteStore store;

    private InMemorySubjectMetadataService delegate;

    SqliteSubjectMetadataService(SqliteStore store) {
        this.store = store;
        SubjectData subjectData = loadSubjects();
        this.delegate = new InMemorySubjectMetadataService(subjectData.subjects(), subjectData.displays());
    }

    @Override
    public synchronized void recordSubject(UUID subjectId, String lastKnownName, Instant lastSeen) {
        InMemorySubjectMetadataService candidate = copyDelegate();
        candidate.recordSubject(subjectId, lastKnownName, lastSeen);
        commit(candidate);
    }

    @Override
    public synchronized Optional<SubjectMetadata> getSubject(UUID subjectId) {
        return delegate.getSubject(subjectId);
    }

    @Override
    public synchronized Map<UUID, SubjectMetadata> getSubjects() {
        return delegate.getSubjects();
    }

    @Override
    public synchronized DisplayProfile getSubjectDisplay(UUID subjectId) {
        return delegate.getSubjectDisplay(subjectId);
    }

    @Override
    public synchronized Map<UUID, DisplayProfile> getSubjectDisplays() {
        return delegate.getSubjectDisplays();
    }

    @Override
    public synchronized void setSubjectPrefix(UUID subjectId, DisplayText prefix) {
        InMemorySubjectMetadataService candidate = copyDelegate();
        candidate.setSubjectPrefix(subjectId, prefix);
        commit(candidate);
    }

    @Override
    public synchronized void clearSubjectPrefix(UUID subjectId) {
        InMemorySubjectMetadataService candidate = copyDelegate();
        candidate.clearSubjectPrefix(subjectId);
        commit(candidate);
    }

    @Override
    public synchronized void setSubjectSuffix(UUID subjectId, DisplayText suffix) {
        InMemorySubjectMetadataService candidate = copyDelegate();
        candidate.setSubjectSuffix(subjectId, suffix);
        commit(candidate);
    }

    @Override
    public synchronized void clearSubjectSuffix(UUID subjectId) {
        InMemorySubjectMetadataService candidate = copyDelegate();
        candidate.clearSubjectSuffix(subjectId);
        commit(candidate);
    }

    private InMemorySubjectMetadataService copyDelegate() {
        return new InMemorySubjectMetadataService(delegate.snapshot(), delegate.displaySnapshot());
    }

    private void commit(InMemorySubjectMetadataService candidate) {
        saveSubjects(candidate.snapshot(), candidate.displaySnapshot());
        delegate = candidate;
    }

    private SubjectData loadSubjects() {
        return store.read(connection -> {
            Map<UUID, SubjectMetadata> subjects = new LinkedHashMap<>();
            try (PreparedStatement statement = connection.prepareStatement("SELECT subject_id, last_known_name, last_seen FROM subjects ORDER BY subject_id");
                    ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    UUID subjectId = parseSubjectId(rows.getString("subject_id"));
                    SubjectMetadata previousSubject = subjects.put(subjectId,
                            new SubjectMetadata(subjectId, rows.getString("last_known_name"), parseInstant(subjectId, rows.getString("last_seen"))));
                    if (previousSubject != null) {
                        throw new PermissionStorageException("Duplicate normalized subject UUID in SQLite subjects: " + subjectId);
                    }
                }
            }

            Map<UUID, DisplayProfile> displays = new LinkedHashMap<>();
            try (PreparedStatement statement = connection.prepareStatement("SELECT subject_id, prefix, suffix FROM subject_display ORDER BY subject_id");
                    ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    UUID subjectId = parseSubjectId(rows.getString("subject_id"));
                    DisplayProfile display = readDisplay(rows.getString("prefix"), rows.getString("suffix"));
                    if (!display.isEmpty()) {
                        DisplayProfile previousDisplay = displays.put(subjectId, display);
                        if (previousDisplay != null) {
                            throw new PermissionStorageException("Duplicate normalized subject display UUID in SQLite subjects: " + subjectId);
                        }
                    }
                }
            }
            return new SubjectData(subjects, displays);
        });
    }

    private void saveSubjects(Map<UUID, SubjectMetadata> subjects, Map<UUID, DisplayProfile> displays) {
        store.write(connection -> {
            try (PreparedStatement deleteDisplays = connection.prepareStatement("DELETE FROM subject_display");
                    PreparedStatement deleteSubjects = connection.prepareStatement("DELETE FROM subjects")) {
                deleteDisplays.executeUpdate();
                deleteSubjects.executeUpdate();
            }
            try (PreparedStatement insertSubject = connection.prepareStatement("INSERT INTO subjects (subject_id, last_known_name, last_seen) VALUES (?, ?, ?)")) {
                for (SubjectMetadata subject : subjects.values()) {
                    insertSubject.setString(1, subject.subjectId().toString());
                    insertSubject.setString(2, subject.lastKnownName());
                    insertSubject.setString(3, subject.lastSeen().toString());
                    insertSubject.addBatch();
                }
                insertSubject.executeBatch();
            }
            try (PreparedStatement insertDisplay = connection.prepareStatement("INSERT INTO subject_display (subject_id, prefix, suffix) VALUES (?, ?, ?)")) {
                for (Map.Entry<UUID, DisplayProfile> entry : displays.entrySet()) {
                    insertDisplay.setString(1, entry.getKey().toString());
                    insertDisplay.setString(2, entry.getValue().prefix().map(DisplayText::rawText).orElse(null));
                    insertDisplay.setString(3, entry.getValue().suffix().map(DisplayText::rawText).orElse(null));
                    insertDisplay.addBatch();
                }
                insertDisplay.executeBatch();
            }
        });
    }

    private static UUID parseSubjectId(String rawSubjectId) {
        try {
            return UUID.fromString(rawSubjectId);
        } catch (IllegalArgumentException exception) {
            throw new PermissionStorageException("Invalid subject UUID in SQLite subjects: " + rawSubjectId, exception);
        }
    }

    private static Instant parseInstant(UUID subjectId, String rawInstant) throws SQLException {
        try {
            return Instant.parse(rawInstant);
        } catch (DateTimeException exception) {
            throw new SQLException("Invalid lastSeen for subject " + subjectId + ": " + rawInstant, exception);
        }
    }

    private static DisplayProfile readDisplay(String prefix, String suffix) {
        DisplayProfile display = DisplayProfile.empty();
        if (prefix != null) {
            display = display.withPrefix(DisplayText.parse(prefix));
        }
        if (suffix != null) {
            display = display.withSuffix(DisplayText.parse(suffix));
        }
        return display;
    }

    private record SubjectData(Map<UUID, SubjectMetadata> subjects, Map<UUID, DisplayProfile> displays) {
    }
}
