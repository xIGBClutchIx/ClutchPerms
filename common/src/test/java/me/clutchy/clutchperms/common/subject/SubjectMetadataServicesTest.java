package me.clutchy.clutchperms.common.subject;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import me.clutchy.clutchperms.common.storage.PermissionStorageException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Verifies JSON-backed subject metadata loading and persistence.
 */
class SubjectMetadataServicesTest {

    private static final UUID FIRST_SUBJECT = UUID.fromString("00000000-0000-0000-0000-000000000001");

    private static final UUID SECOND_SUBJECT = UUID.fromString("00000000-0000-0000-0000-000000000002");

    private static final Instant FIRST_SEEN = Instant.parse("2026-04-24T12:00:00Z");

    private static final Instant SECOND_SEEN = Instant.parse("2026-04-24T13:00:00Z");

    /**
     * Temporary directory used for JSON persistence test files.
     */
    @TempDir
    private Path temporaryDirectory;

    /**
     * Confirms a missing JSON file starts with empty state.
     */
    @Test
    void missingFileLoadsEmptySubjects() {
        Path subjectsFile = temporaryDirectory.resolve("subjects.json");

        SubjectMetadataService subjectMetadataService = SubjectMetadataServices.jsonFile(subjectsFile);

        assertEquals(Map.of(), subjectMetadataService.getSubjects());
        assertFalse(subjectMetadataService.getSubject(FIRST_SUBJECT).isPresent());
        assertFalse(Files.exists(subjectsFile));
    }

    /**
     * Confirms subject metadata survives a reload.
     */
    @Test
    void subjectMetadataRoundTripsThroughJson() {
        Path subjectsFile = temporaryDirectory.resolve("subjects.json");
        SubjectMetadataService subjectMetadataService = SubjectMetadataServices.jsonFile(subjectsFile);

        subjectMetadataService.recordSubject(FIRST_SUBJECT, " Target ", FIRST_SEEN);

        SubjectMetadataService reloadedSubjectMetadataService = SubjectMetadataServices.jsonFile(subjectsFile);

        assertEquals(new SubjectMetadata(FIRST_SUBJECT, "Target", FIRST_SEEN), reloadedSubjectMetadataService.getSubject(FIRST_SUBJECT).orElseThrow());
    }

    /**
     * Confirms saves create parent directories and use deterministic subject ordering.
     *
     * @throws IOException if the persisted file cannot be read
     */
    @Test
    void saveCreatesParentDirectoriesAndWritesDeterministicJson() throws IOException {
        Path subjectsFile = temporaryDirectory.resolve("nested").resolve("data").resolve("subjects.json");
        SubjectMetadataService subjectMetadataService = SubjectMetadataServices.jsonFile(subjectsFile);

        subjectMetadataService.recordSubject(SECOND_SUBJECT, "Second", SECOND_SEEN);
        subjectMetadataService.recordSubject(FIRST_SUBJECT, "First", FIRST_SEEN);

        String persistedJson = Files.readString(subjectsFile).replace("\r\n", "\n");

        assertEquals("""
                {
                  "version": 1,
                  "subjects": {
                    "00000000-0000-0000-0000-000000000001": {
                      "lastKnownName": "First",
                      "lastSeen": "2026-04-24T12:00:00Z"
                    },
                    "00000000-0000-0000-0000-000000000002": {
                      "lastKnownName": "Second",
                      "lastSeen": "2026-04-24T13:00:00Z"
                    }
                  }
                }
                """, persistedJson);
    }

    /**
     * Confirms later observations replace earlier metadata for the same subject.
     */
    @Test
    void laterRecordReplacesExistingMetadata() {
        SubjectMetadataService subjectMetadataService = SubjectMetadataServices.jsonFile(temporaryDirectory.resolve("subjects.json"));

        subjectMetadataService.recordSubject(FIRST_SUBJECT, "FirstName", FIRST_SEEN);
        subjectMetadataService.recordSubject(FIRST_SUBJECT, "SecondName", SECOND_SEEN);

        assertEquals(new SubjectMetadata(FIRST_SUBJECT, "SecondName", SECOND_SEEN), subjectMetadataService.getSubject(FIRST_SUBJECT).orElseThrow());
        assertEquals(Map.of(FIRST_SUBJECT, new SubjectMetadata(FIRST_SUBJECT, "SecondName", SECOND_SEEN)), subjectMetadataService.getSubjects());
    }

    /**
     * Confirms failed JSON saves leave subject metadata runtime state and persisted state unchanged.
     *
     * @throws IOException if test storage setup cannot be written or read
     */
    @Test
    void failedSavesDoNotCommitSubjectMetadataMutations() throws IOException {
        Path subjectsFile = temporaryDirectory.resolve("subjects.json");
        Files.writeString(subjectsFile, """
                {
                  "version": 1,
                  "subjects": {
                    "00000000-0000-0000-0000-000000000001": {
                      "lastKnownName": "First",
                      "lastSeen": "2026-04-24T12:00:00Z"
                    }
                  }
                }
                """);
        SubjectMetadataService subjectMetadataService = SubjectMetadataServices.jsonFile(subjectsFile);
        String persistedJson = Files.readString(subjectsFile);
        blockBackupRoot();

        assertThrows(PermissionStorageException.class, () -> subjectMetadataService.recordSubject(FIRST_SUBJECT, "Changed", SECOND_SEEN));
        assertSubjectMetadataStatePreserved(subjectMetadataService, subjectsFile, persistedJson);

        assertThrows(PermissionStorageException.class, () -> subjectMetadataService.recordSubject(SECOND_SUBJECT, "Second", SECOND_SEEN));
        assertSubjectMetadataStatePreserved(subjectMetadataService, subjectsFile, persistedJson);
    }

    /**
     * Confirms malformed or invalid subject metadata files fail during construction.
     *
     * @throws IOException if the test file cannot be written
     */
    @Test
    void invalidJsonFilesFailLoad() throws IOException {
        assertFailsToLoad("{not-json");
        assertFailsToLoad("""
                {
                  "version": 2,
                  "subjects": {}
                }
                """);
        assertFailsToLoad("""
                {
                  "version": 1,
                  "subjects": {
                    "not-a-uuid": {
                      "lastKnownName": "Target",
                      "lastSeen": "2026-04-24T12:00:00Z"
                    }
                  }
                }
                """);
        assertFailsToLoad("""
                {
                  "version": 1,
                  "subjects": {
                    "00000000-0000-0000-0000-000000000001": {
                      "lastKnownName": "   ",
                      "lastSeen": "2026-04-24T12:00:00Z"
                    }
                  }
                }
                """);
        assertFailsToLoad("""
                {
                  "version": 1,
                  "subjects": {
                    "00000000-0000-0000-0000-000000000001": {
                      "lastKnownName": "Target",
                      "lastSeen": "not-an-instant"
                    }
                  }
                }
                """);
    }

    private void assertFailsToLoad(String json) throws IOException {
        Path subjectsFile = temporaryDirectory.resolve("invalid-subjects.json");
        Files.writeString(subjectsFile, json);

        assertThrows(PermissionStorageException.class, () -> SubjectMetadataServices.jsonFile(subjectsFile));
    }

    private void assertSubjectMetadataStatePreserved(SubjectMetadataService subjectMetadataService, Path subjectsFile, String persistedJson) throws IOException {
        assertSubjectMetadataRuntimeState(subjectMetadataService);
        assertEquals(persistedJson, Files.readString(subjectsFile));
        assertSubjectMetadataRuntimeState(SubjectMetadataServices.jsonFile(subjectsFile));
    }

    private static void assertSubjectMetadataRuntimeState(SubjectMetadataService subjectMetadataService) {
        SubjectMetadata firstSubject = new SubjectMetadata(FIRST_SUBJECT, "First", FIRST_SEEN);
        assertEquals(firstSubject, subjectMetadataService.getSubject(FIRST_SUBJECT).orElseThrow());
        assertFalse(subjectMetadataService.getSubject(SECOND_SUBJECT).isPresent());
        assertEquals(Map.of(FIRST_SUBJECT, firstSubject), subjectMetadataService.getSubjects());
    }

    private void blockBackupRoot() throws IOException {
        Files.writeString(temporaryDirectory.resolve("backups"), "blocked");
    }
}
