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
        writeSubject(candidate.getSubject(subjectId).orElseThrow());
        delegate = candidate;
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
        writeSubjectDisplay(subjectId, candidate.getSubjectDisplay(subjectId));
        delegate = candidate;
    }

    @Override
    public synchronized void clearSubjectPrefix(UUID subjectId) {
        InMemorySubjectMetadataService candidate = copyDelegate();
        candidate.clearSubjectPrefix(subjectId);
        writeSubjectDisplay(subjectId, candidate.getSubjectDisplay(subjectId));
        delegate = candidate;
    }

    @Override
    public synchronized void setSubjectSuffix(UUID subjectId, DisplayText suffix) {
        InMemorySubjectMetadataService candidate = copyDelegate();
        candidate.setSubjectSuffix(subjectId, suffix);
        writeSubjectDisplay(subjectId, candidate.getSubjectDisplay(subjectId));
        delegate = candidate;
    }

    @Override
    public synchronized void clearSubjectSuffix(UUID subjectId) {
        InMemorySubjectMetadataService candidate = copyDelegate();
        candidate.clearSubjectSuffix(subjectId);
        writeSubjectDisplay(subjectId, candidate.getSubjectDisplay(subjectId));
        delegate = candidate;
    }

    private InMemorySubjectMetadataService copyDelegate() {
        return new InMemorySubjectMetadataService(delegate.snapshot(), delegate.displaySnapshot());
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

    private void writeSubject(SubjectMetadata subject) {
        store.write(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO subjects (subject_id, last_known_name, last_seen) VALUES (?, ?, ?) ON CONFLICT(subject_id) DO UPDATE SET last_known_name = excluded.last_known_name, last_seen = excluded.last_seen")) {
                statement.setString(1, subject.subjectId().toString());
                statement.setString(2, subject.lastKnownName());
                statement.setString(3, subject.lastSeen().toString());
                statement.executeUpdate();
            }
        });
    }

    private void writeSubjectDisplay(UUID subjectId, DisplayProfile display) {
        if (display.isEmpty()) {
            store.write(connection -> {
                try (PreparedStatement statement = connection.prepareStatement("DELETE FROM subject_display WHERE subject_id = ?")) {
                    statement.setString(1, subjectId.toString());
                    statement.executeUpdate();
                }
            });
            return;
        }
        store.write(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO subject_display (subject_id, prefix, suffix) VALUES (?, ?, ?) ON CONFLICT(subject_id) DO UPDATE SET prefix = excluded.prefix, suffix = excluded.suffix")) {
                statement.setString(1, subjectId.toString());
                statement.setString(2, display.prefix().map(DisplayText::rawText).orElse(null));
                statement.setString(3, display.suffix().map(DisplayText::rawText).orElse(null));
                statement.executeUpdate();
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
