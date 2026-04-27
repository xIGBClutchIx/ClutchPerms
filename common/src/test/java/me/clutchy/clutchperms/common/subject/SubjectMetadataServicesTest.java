package me.clutchy.clutchperms.common.subject;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Statement;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import me.clutchy.clutchperms.common.display.DisplayText;
import me.clutchy.clutchperms.common.storage.PermissionStorageException;
import me.clutchy.clutchperms.common.storage.SqliteStore;
import me.clutchy.clutchperms.common.storage.SqliteTestSupport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies SQLite-backed subject metadata loading and persistence.
 */
class SubjectMetadataServicesTest {

    private static final UUID FIRST_SUBJECT = UUID.fromString("00000000-0000-0000-0000-000000000001");

    private static final UUID SECOND_SUBJECT = UUID.fromString("00000000-0000-0000-0000-000000000002");

    private static final Instant FIRST_SEEN = Instant.parse("2026-04-24T12:00:00Z");

    private static final Instant SECOND_SEEN = Instant.parse("2026-04-24T13:00:00Z");

    @TempDir
    private Path temporaryDirectory;

    @Test
    void missingDatabaseCreatesEmptySubjectsSchema() {
        Path databaseFile = SqliteTestSupport.databaseFile(temporaryDirectory);

        try (SqliteStore store = SqliteTestSupport.open(databaseFile)) {
            SubjectMetadataService subjectMetadataService = SubjectMetadataServices.sqlite(store);

            assertEquals(Map.of(), subjectMetadataService.getSubjects());
            assertFalse(subjectMetadataService.getSubject(FIRST_SUBJECT).isPresent());
        }

        assertTrue(Files.exists(databaseFile));
    }

    @Test
    void subjectMetadataRoundTripsThroughSqlite() {
        Path databaseFile = SqliteTestSupport.databaseFile(temporaryDirectory);
        try (SqliteStore store = SqliteTestSupport.open(databaseFile)) {
            SubjectMetadataService subjectMetadataService = SubjectMetadataServices.sqlite(store);
            subjectMetadataService.recordSubject(FIRST_SUBJECT, " Target ", FIRST_SEEN);
            subjectMetadataService.setSubjectPrefix(FIRST_SUBJECT, DisplayText.parse("&7[Admin]"));
            subjectMetadataService.setSubjectSuffix(FIRST_SUBJECT, DisplayText.parse("&f*"));
        }

        try (SqliteStore store = SqliteTestSupport.open(databaseFile)) {
            SubjectMetadataService reloadedSubjectMetadataService = SubjectMetadataServices.sqlite(store);

            assertEquals(new SubjectMetadata(FIRST_SUBJECT, "Target", FIRST_SEEN), reloadedSubjectMetadataService.getSubject(FIRST_SUBJECT).orElseThrow());
            assertEquals("&7[Admin]", reloadedSubjectMetadataService.getSubjectDisplay(FIRST_SUBJECT).prefix().orElseThrow().rawText());
            assertEquals("&f*", reloadedSubjectMetadataService.getSubjectDisplay(FIRST_SUBJECT).suffix().orElseThrow().rawText());
        }
    }

    @Test
    void laterRecordReplacesExistingMetadata() {
        try (SqliteStore store = SqliteTestSupport.openDirectory(temporaryDirectory)) {
            SubjectMetadataService subjectMetadataService = SubjectMetadataServices.sqlite(store);

            subjectMetadataService.recordSubject(FIRST_SUBJECT, "FirstName", FIRST_SEEN);
            subjectMetadataService.recordSubject(FIRST_SUBJECT, "SecondName", SECOND_SEEN);

            assertEquals(new SubjectMetadata(FIRST_SUBJECT, "SecondName", SECOND_SEEN), subjectMetadataService.getSubject(FIRST_SUBJECT).orElseThrow());
            assertEquals(Map.of(FIRST_SUBJECT, new SubjectMetadata(FIRST_SUBJECT, "SecondName", SECOND_SEEN)), subjectMetadataService.getSubjects());
        }
    }

    @Test
    void failedWritesDoNotCommitSubjectMetadataMutations() {
        Path databaseFile = SqliteTestSupport.databaseFile(temporaryDirectory);
        SqliteStore store = SqliteTestSupport.open(databaseFile);
        SubjectMetadataService subjectMetadataService = SubjectMetadataServices.sqlite(store);
        subjectMetadataService.recordSubject(FIRST_SUBJECT, "First", FIRST_SEEN);
        subjectMetadataService.setSubjectPrefix(FIRST_SUBJECT, DisplayText.parse("&7[Old]"));
        store.close();

        assertThrows(PermissionStorageException.class, () -> subjectMetadataService.recordSubject(FIRST_SUBJECT, "Changed", SECOND_SEEN));
        assertSubjectMetadataStatePreserved(subjectMetadataService, databaseFile);

        assertThrows(PermissionStorageException.class, () -> subjectMetadataService.recordSubject(SECOND_SUBJECT, "Second", SECOND_SEEN));
        assertSubjectMetadataStatePreserved(subjectMetadataService, databaseFile);

        assertThrows(PermissionStorageException.class, () -> subjectMetadataService.setSubjectPrefix(FIRST_SUBJECT, DisplayText.parse("&c[New]")));
        assertSubjectMetadataStatePreserved(subjectMetadataService, databaseFile);

        assertThrows(PermissionStorageException.class, () -> subjectMetadataService.clearSubjectPrefix(FIRST_SUBJECT));
        assertSubjectMetadataStatePreserved(subjectMetadataService, databaseFile);

        assertThrows(PermissionStorageException.class, () -> subjectMetadataService.setSubjectSuffix(FIRST_SUBJECT, DisplayText.parse("&f*")));
        assertSubjectMetadataStatePreserved(subjectMetadataService, databaseFile);
    }

    @Test
    void invalidSqliteSubjectRowsFailLoad() {
        Path databaseFile = SqliteTestSupport.databaseFile(temporaryDirectory);
        try (SqliteStore store = SqliteTestSupport.open(databaseFile)) {
            store.write(connection -> {
                try (Statement statement = connection.createStatement()) {
                    statement.executeUpdate(
                            "INSERT INTO subjects (subject_id, last_known_name, last_seen) VALUES ('00000000-0000-0000-0000-000000000001', '   ', '2026-04-24T12:00:00Z')");
                }
            });
        }

        try (SqliteStore store = SqliteTestSupport.open(databaseFile)) {
            assertThrows(PermissionStorageException.class, () -> SubjectMetadataServices.sqlite(store));
        }
    }

    @Test
    void invalidSqliteDisplayRowsFailLoad() {
        Path databaseFile = SqliteTestSupport.databaseFile(temporaryDirectory);
        try (SqliteStore store = SqliteTestSupport.open(databaseFile)) {
            store.write(connection -> {
                try (Statement statement = connection.createStatement()) {
                    statement.executeUpdate("INSERT INTO subject_display (subject_id, prefix, suffix) VALUES ('00000000-0000-0000-0000-000000000001', '§cBad', NULL)");
                }
            });
        }

        try (SqliteStore store = SqliteTestSupport.open(databaseFile)) {
            assertThrows(PermissionStorageException.class, () -> SubjectMetadataServices.sqlite(store));
        }
    }

    private static void assertSubjectMetadataStatePreserved(SubjectMetadataService subjectMetadataService, Path databaseFile) {
        assertSubjectMetadataRuntimeState(subjectMetadataService);
        try (SqliteStore store = SqliteTestSupport.open(databaseFile)) {
            assertSubjectMetadataRuntimeState(SubjectMetadataServices.sqlite(store));
        }
    }

    private static void assertSubjectMetadataRuntimeState(SubjectMetadataService subjectMetadataService) {
        SubjectMetadata firstSubject = new SubjectMetadata(FIRST_SUBJECT, "First", FIRST_SEEN);
        assertEquals(firstSubject, subjectMetadataService.getSubject(FIRST_SUBJECT).orElseThrow());
        assertFalse(subjectMetadataService.getSubject(SECOND_SUBJECT).isPresent());
        assertEquals(Map.of(FIRST_SUBJECT, firstSubject), subjectMetadataService.getSubjects());
        assertEquals("&7[Old]", subjectMetadataService.getSubjectDisplay(FIRST_SUBJECT).prefix().orElseThrow().rawText());
        assertFalse(subjectMetadataService.getSubjectDisplay(FIRST_SUBJECT).suffix().isPresent());
    }
}
