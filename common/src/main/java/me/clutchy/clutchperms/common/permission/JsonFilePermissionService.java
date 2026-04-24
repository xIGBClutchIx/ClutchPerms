package me.clutchy.clutchperms.common.permission;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
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
 * JSON-backed permission service that persists direct subject permission assignments after every mutation.
 */
final class JsonFilePermissionService implements PermissionService {

    private static final int CURRENT_VERSION = 1;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final Path permissionsFile;

    private final InMemoryPermissionService delegate;

    JsonFilePermissionService(Path permissionsFile) {
        this.permissionsFile = permissionsFile.toAbsolutePath().normalize();
        this.delegate = new InMemoryPermissionService(loadPermissions());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public synchronized PermissionValue getPermission(UUID subjectId, String node) {
        return delegate.getPermission(subjectId, node);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public synchronized Map<String, PermissionValue> getPermissions(UUID subjectId) {
        return delegate.getPermissions(subjectId);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public synchronized void setPermission(UUID subjectId, String node, PermissionValue value) {
        delegate.setPermission(subjectId, node, value);
        savePermissions();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public synchronized void clearPermission(UUID subjectId, String node) {
        delegate.clearPermission(subjectId, node);
        savePermissions();
    }

    private Map<UUID, Map<String, PermissionValue>> loadPermissions() {
        if (Files.notExists(permissionsFile)) {
            return Map.of();
        }

        try (Reader reader = Files.newBufferedReader(permissionsFile, StandardCharsets.UTF_8)) {
            JsonElement rootElement = JsonParser.parseReader(reader);
            return parseRoot(rootElement);
        } catch (NoSuchFileException exception) {
            return Map.of();
        } catch (IOException | JsonParseException | IllegalArgumentException exception) {
            throw new PermissionStorageException("Failed to load permissions from " + permissionsFile, exception);
        }
    }

    private void savePermissions() {
        try {
            StorageFiles.writeAtomicallyWithBackup(permissionsFile, StorageFileKind.PERMISSIONS, writer -> {
                GSON.toJson(toJson(delegate.snapshot()), writer);
                writer.write(System.lineSeparator());
            });
        } catch (IOException exception) {
            throw new PermissionStorageException("Failed to save permissions to " + permissionsFile, exception);
        }
    }

    private static Map<UUID, Map<String, PermissionValue>> parseRoot(JsonElement rootElement) {
        if (rootElement == null || !rootElement.isJsonObject()) {
            throw new IllegalArgumentException("permissions root must be an object");
        }

        JsonObject root = rootElement.getAsJsonObject();
        int version = readVersion(root);
        if (version != CURRENT_VERSION) {
            throw new IllegalArgumentException("unsupported permissions version " + version);
        }

        JsonElement subjectsElement = root.get("subjects");
        if (subjectsElement == null || !subjectsElement.isJsonObject()) {
            throw new IllegalArgumentException("subjects must be an object");
        }

        Map<UUID, Map<String, PermissionValue>> permissions = new LinkedHashMap<>();
        for (Map.Entry<String, JsonElement> subjectEntry : subjectsElement.getAsJsonObject().entrySet()) {
            UUID subjectId = parseSubjectId(subjectEntry.getKey());
            JsonElement subjectPermissionsElement = subjectEntry.getValue();
            if (subjectPermissionsElement == null || !subjectPermissionsElement.isJsonObject()) {
                throw new IllegalArgumentException("permissions for subject " + subjectEntry.getKey() + " must be an object");
            }

            Map<String, PermissionValue> subjectPermissions = parseSubjectPermissions(subjectEntry.getKey(), subjectPermissionsElement.getAsJsonObject());
            if (!subjectPermissions.isEmpty()) {
                permissions.put(subjectId, subjectPermissions);
            }
        }

        return permissions;
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

    private static Map<String, PermissionValue> parseSubjectPermissions(String subjectId, JsonObject subjectPermissionsElement) {
        Map<String, PermissionValue> subjectPermissions = new LinkedHashMap<>();
        for (Map.Entry<String, JsonElement> permissionEntry : subjectPermissionsElement.entrySet()) {
            String normalizedNode = PermissionNodes.normalize(permissionEntry.getKey());
            PermissionValue value = parsePermissionValue(subjectId, normalizedNode, permissionEntry.getValue());
            subjectPermissions.put(normalizedNode, value);
        }
        return subjectPermissions;
    }

    private static PermissionValue parsePermissionValue(String subjectId, String node, JsonElement valueElement) {
        if (valueElement == null || !valueElement.isJsonPrimitive()) {
            throw new IllegalArgumentException("permission " + node + " for subject " + subjectId + " must be a string");
        }

        JsonPrimitive valuePrimitive = valueElement.getAsJsonPrimitive();
        if (!valuePrimitive.isString()) {
            throw new IllegalArgumentException("permission " + node + " for subject " + subjectId + " must be a string");
        }

        String rawValue = valuePrimitive.getAsString();
        if (PermissionValue.TRUE.name().equals(rawValue)) {
            return PermissionValue.TRUE;
        }
        if (PermissionValue.FALSE.name().equals(rawValue)) {
            return PermissionValue.FALSE;
        }

        throw new IllegalArgumentException("permission " + node + " for subject " + subjectId + " must be TRUE or FALSE");
    }

    private static JsonObject toJson(Map<UUID, Map<String, PermissionValue>> snapshot) {
        JsonObject root = new JsonObject();
        root.addProperty("version", CURRENT_VERSION);

        JsonObject subjects = new JsonObject();
        snapshot.forEach((subjectId, subjectPermissions) -> {
            JsonObject permissions = new JsonObject();
            subjectPermissions.forEach((node, value) -> {
                if (value != PermissionValue.UNSET) {
                    permissions.addProperty(node, value.name());
                }
            });

            if (permissions.size() > 0) {
                subjects.add(subjectId.toString(), permissions);
            }
        });

        root.add("subjects", subjects);
        return root;
    }
}
