package me.clutchy.clutchperms.common.subject;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.time.DateTimeException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;

import me.clutchy.clutchperms.common.storage.PermissionStorageException;
import me.clutchy.clutchperms.common.storage.StorageFileKind;
import me.clutchy.clutchperms.common.storage.StorageFiles;

/**
 * JSON-backed subject metadata service that persists the latest known subject name and last-seen time.
 */
final class JsonFileSubjectMetadataService implements SubjectMetadataService {

    private static final int CURRENT_VERSION = 1;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final Path subjectsFile;

    private final InMemorySubjectMetadataService delegate;

    JsonFileSubjectMetadataService(Path subjectsFile) {
        this.subjectsFile = subjectsFile.toAbsolutePath().normalize();
        this.delegate = new InMemorySubjectMetadataService(loadSubjects());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public synchronized void recordSubject(UUID subjectId, String lastKnownName, Instant lastSeen) {
        delegate.recordSubject(subjectId, lastKnownName, lastSeen);
        saveSubjects();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public synchronized Optional<SubjectMetadata> getSubject(UUID subjectId) {
        return delegate.getSubject(subjectId);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public synchronized Map<UUID, SubjectMetadata> getSubjects() {
        return delegate.getSubjects();
    }

    private Map<UUID, SubjectMetadata> loadSubjects() {
        if (Files.notExists(subjectsFile)) {
            return Map.of();
        }

        try (Reader reader = Files.newBufferedReader(subjectsFile, StandardCharsets.UTF_8)) {
            JsonElement rootElement = JsonParser.parseReader(reader);
            return parseRoot(rootElement);
        } catch (NoSuchFileException exception) {
            return Map.of();
        } catch (IOException | JsonParseException | IllegalArgumentException | DateTimeException exception) {
            throw new PermissionStorageException("Failed to load subject metadata from " + subjectsFile, exception);
        }
    }

    private void saveSubjects() {
        try {
            StorageFiles.writeAtomicallyWithBackup(subjectsFile, StorageFileKind.SUBJECTS, writer -> {
                GSON.toJson(toJson(delegate.snapshot()), writer);
                writer.write(System.lineSeparator());
            });
        } catch (IOException exception) {
            throw new PermissionStorageException("Failed to save subject metadata to " + subjectsFile, exception);
        }
    }

    private static Map<UUID, SubjectMetadata> parseRoot(JsonElement rootElement) {
        if (rootElement == null || !rootElement.isJsonObject()) {
            throw new IllegalArgumentException("subjects root must be an object");
        }

        JsonObject root = rootElement.getAsJsonObject();
        int version = readVersion(root);
        if (version != CURRENT_VERSION) {
            throw new IllegalArgumentException("unsupported subject metadata version " + version);
        }

        JsonElement subjectsElement = root.get("subjects");
        if (subjectsElement == null || !subjectsElement.isJsonObject()) {
            throw new IllegalArgumentException("subjects must be an object");
        }

        Map<UUID, SubjectMetadata> subjects = new LinkedHashMap<>();
        for (Map.Entry<String, JsonElement> subjectEntry : subjectsElement.getAsJsonObject().entrySet()) {
            UUID subjectId = parseSubjectId(subjectEntry.getKey());
            JsonElement metadataElement = subjectEntry.getValue();
            if (metadataElement == null || !metadataElement.isJsonObject()) {
                throw new IllegalArgumentException("metadata for subject " + subjectEntry.getKey() + " must be an object");
            }

            subjects.put(subjectId, parseSubject(subjectId, metadataElement.getAsJsonObject()));
        }

        return subjects;
    }

    private static int readVersion(JsonObject root) {
        JsonElement versionElement = root.get("version");
        if (versionElement == null || !versionElement.isJsonPrimitive()) {
            throw new IllegalArgumentException("version must be a number");
        }

        JsonPrimitive versionPrimitive = versionElement.getAsJsonPrimitive();
        if (!versionPrimitive.isNumber()) {
            throw new IllegalArgumentException("version must be a number");
        }

        try {
            return Integer.parseInt(versionPrimitive.getAsString());
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("version must be an integer", exception);
        }
    }

    private static UUID parseSubjectId(String rawSubjectId) {
        try {
            return UUID.fromString(rawSubjectId);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("invalid subject UUID " + rawSubjectId, exception);
        }
    }

    private static SubjectMetadata parseSubject(UUID subjectId, JsonObject metadataElement) {
        String lastKnownName = readString(metadataElement, "lastKnownName", "metadata for subject " + subjectId);
        Instant lastSeen = Instant.parse(readString(metadataElement, "lastSeen", "metadata for subject " + subjectId));
        return new SubjectMetadata(subjectId, lastKnownName, lastSeen);
    }

    private static String readString(JsonObject object, String property, String owner) {
        JsonElement element = object.get(property);
        if (element == null || !element.isJsonPrimitive()) {
            throw new IllegalArgumentException(owner + " must include string " + property);
        }

        JsonPrimitive primitive = element.getAsJsonPrimitive();
        if (!primitive.isString()) {
            throw new IllegalArgumentException(owner + " must include string " + property);
        }

        return primitive.getAsString();
    }

    private static JsonObject toJson(Map<UUID, SubjectMetadata> snapshot) {
        JsonObject root = new JsonObject();
        root.addProperty("version", CURRENT_VERSION);

        JsonObject subjects = new JsonObject();
        snapshot.forEach((subjectId, metadata) -> {
            JsonObject subject = new JsonObject();
            subject.addProperty("lastKnownName", metadata.lastKnownName());
            subject.addProperty("lastSeen", metadata.lastSeen().toString());
            subjects.add(subjectId.toString(), subject);
        });

        root.add("subjects", subjects);
        return root;
    }
}
