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

import me.clutchy.clutchperms.common.display.DisplayProfile;
import me.clutchy.clutchperms.common.display.DisplayText;
import me.clutchy.clutchperms.common.storage.PermissionStorageException;
import me.clutchy.clutchperms.common.storage.StorageFileKind;
import me.clutchy.clutchperms.common.storage.StorageFiles;
import me.clutchy.clutchperms.common.storage.StorageWriteOptions;

/**
 * JSON-backed subject metadata service that persists the latest known subject name and last-seen time.
 */
final class JsonFileSubjectMetadataService implements SubjectMetadataService {

    private static final int CURRENT_VERSION = 1;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final Path subjectsFile;

    private final StorageWriteOptions writeOptions;

    private InMemorySubjectMetadataService delegate;

    JsonFileSubjectMetadataService(Path subjectsFile) {
        this(subjectsFile, StorageWriteOptions.defaults());
    }

    JsonFileSubjectMetadataService(Path subjectsFile, StorageWriteOptions writeOptions) {
        this.subjectsFile = subjectsFile.toAbsolutePath().normalize();
        this.writeOptions = StorageWriteOptions.defaultIfNull(writeOptions);
        SubjectData subjectData = loadSubjects();
        this.delegate = new InMemorySubjectMetadataService(subjectData.subjects(), subjectData.displays());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public synchronized void recordSubject(UUID subjectId, String lastKnownName, Instant lastSeen) {
        InMemorySubjectMetadataService candidate = copyDelegate();
        candidate.recordSubject(subjectId, lastKnownName, lastSeen);
        saveSubjects(candidate.snapshot(), candidate.displaySnapshot());
        delegate = candidate;
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
        saveSubjects(candidate.snapshot(), candidate.displaySnapshot());
        delegate = candidate;
    }

    @Override
    public synchronized void clearSubjectPrefix(UUID subjectId) {
        InMemorySubjectMetadataService candidate = copyDelegate();
        candidate.clearSubjectPrefix(subjectId);
        saveSubjects(candidate.snapshot(), candidate.displaySnapshot());
        delegate = candidate;
    }

    @Override
    public synchronized void setSubjectSuffix(UUID subjectId, DisplayText suffix) {
        InMemorySubjectMetadataService candidate = copyDelegate();
        candidate.setSubjectSuffix(subjectId, suffix);
        saveSubjects(candidate.snapshot(), candidate.displaySnapshot());
        delegate = candidate;
    }

    @Override
    public synchronized void clearSubjectSuffix(UUID subjectId) {
        InMemorySubjectMetadataService candidate = copyDelegate();
        candidate.clearSubjectSuffix(subjectId);
        saveSubjects(candidate.snapshot(), candidate.displaySnapshot());
        delegate = candidate;
    }

    private SubjectData loadSubjects() {
        if (Files.notExists(subjectsFile)) {
            return new SubjectData(Map.of(), Map.of());
        }

        try (Reader reader = Files.newBufferedReader(subjectsFile, StandardCharsets.UTF_8)) {
            JsonElement rootElement = JsonParser.parseReader(reader);
            return parseRoot(rootElement);
        } catch (NoSuchFileException exception) {
            return new SubjectData(Map.of(), Map.of());
        } catch (IOException | JsonParseException | IllegalArgumentException | DateTimeException exception) {
            throw new PermissionStorageException("Failed to load subject metadata from " + subjectsFile, exception);
        }
    }

    private InMemorySubjectMetadataService copyDelegate() {
        return new InMemorySubjectMetadataService(delegate.snapshot(), delegate.displaySnapshot());
    }

    private void saveSubjects(Map<UUID, SubjectMetadata> snapshot, Map<UUID, DisplayProfile> displaySnapshot) {
        try {
            StorageFiles.writeAtomicallyWithBackup(subjectsFile, StorageFileKind.SUBJECTS, writeOptions, writer -> {
                GSON.toJson(toJson(snapshot, displaySnapshot), writer);
                writer.write(System.lineSeparator());
            });
        } catch (IOException exception) {
            throw new PermissionStorageException("Failed to save subject metadata to " + subjectsFile, exception);
        }
    }

    private static SubjectData parseRoot(JsonElement rootElement) {
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

        return new SubjectData(subjects, parseDisplays(root));
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

    private static Map<UUID, DisplayProfile> parseDisplays(JsonObject root) {
        JsonElement displaysElement = root.get("display");
        if (displaysElement == null) {
            return Map.of();
        }
        if (!displaysElement.isJsonObject()) {
            throw new IllegalArgumentException("display must be an object");
        }

        Map<UUID, DisplayProfile> displays = new LinkedHashMap<>();
        for (Map.Entry<String, JsonElement> displayEntry : displaysElement.getAsJsonObject().entrySet()) {
            UUID subjectId = parseSubjectId(displayEntry.getKey());
            JsonElement displayElement = displayEntry.getValue();
            if (displayElement == null || !displayElement.isJsonObject()) {
                throw new IllegalArgumentException("display for subject " + displayEntry.getKey() + " must be an object");
            }

            DisplayProfile display = parseDisplayProfile("display for subject " + subjectId, displayElement.getAsJsonObject());
            if (!display.isEmpty()) {
                displays.put(subjectId, display);
            }
        }
        return displays;
    }

    private static DisplayProfile parseDisplayProfile(String owner, JsonObject displayElement) {
        DisplayProfile display = DisplayProfile.empty();
        JsonElement prefixElement = displayElement.get("prefix");
        if (prefixElement != null) {
            display = display.withPrefix(DisplayText.parse(readString(prefixElement, owner, "prefix")));
        }
        JsonElement suffixElement = displayElement.get("suffix");
        if (suffixElement != null) {
            display = display.withSuffix(DisplayText.parse(readString(suffixElement, owner, "suffix")));
        }
        return display;
    }

    private static String readString(JsonObject object, String property, String owner) {
        JsonElement element = object.get(property);
        return readString(element, owner, property);
    }

    private static String readString(JsonElement element, String owner, String property) {
        if (element == null || !element.isJsonPrimitive()) {
            throw new IllegalArgumentException(owner + " must include string " + property);
        }

        JsonPrimitive primitive = element.getAsJsonPrimitive();
        if (!primitive.isString()) {
            throw new IllegalArgumentException(owner + " must include string " + property);
        }

        return primitive.getAsString();
    }

    private static JsonObject toJson(Map<UUID, SubjectMetadata> snapshot, Map<UUID, DisplayProfile> displaySnapshot) {
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
        JsonObject display = new JsonObject();
        displaySnapshot.forEach((subjectId, profile) -> {
            JsonObject subjectDisplay = new JsonObject();
            profile.prefix().ifPresent(prefix -> subjectDisplay.addProperty("prefix", prefix.rawText()));
            profile.suffix().ifPresent(suffix -> subjectDisplay.addProperty("suffix", suffix.rawText()));
            display.add(subjectId.toString(), subjectDisplay);
        });
        root.add("display", display);
        return root;
    }

    private record SubjectData(Map<UUID, SubjectMetadata> subjects, Map<UUID, DisplayProfile> displays) {
    }
}
