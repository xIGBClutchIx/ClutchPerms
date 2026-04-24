package me.clutchy.clutchperms.common.group;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;

import me.clutchy.clutchperms.common.permission.PermissionNodes;
import me.clutchy.clutchperms.common.permission.PermissionValue;
import me.clutchy.clutchperms.common.storage.PermissionStorageException;

/**
 * JSON-backed {@link GroupService} that persists basic group definitions and direct subject memberships after every mutation.
 */
final class JsonFileGroupService implements GroupService {

    private static final int CURRENT_VERSION = 1;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final Path groupsFile;

    private final InMemoryGroupService delegate;

    JsonFileGroupService(Path groupsFile) {
        this.groupsFile = groupsFile.toAbsolutePath().normalize();
        GroupData groupData = loadGroups();
        this.delegate = new InMemoryGroupService(groupData.groupPermissions(), groupData.memberships());
    }

    @Override
    public synchronized Set<String> getGroups() {
        return delegate.getGroups();
    }

    @Override
    public synchronized boolean hasGroup(String groupName) {
        return delegate.hasGroup(groupName);
    }

    @Override
    public synchronized void createGroup(String groupName) {
        delegate.createGroup(groupName);
        saveGroups();
    }

    @Override
    public synchronized void deleteGroup(String groupName) {
        delegate.deleteGroup(groupName);
        saveGroups();
    }

    @Override
    public synchronized PermissionValue getGroupPermission(String groupName, String node) {
        return delegate.getGroupPermission(groupName, node);
    }

    @Override
    public synchronized Map<String, PermissionValue> getGroupPermissions(String groupName) {
        return delegate.getGroupPermissions(groupName);
    }

    @Override
    public synchronized void setGroupPermission(String groupName, String node, PermissionValue value) {
        delegate.setGroupPermission(groupName, node, value);
        saveGroups();
    }

    @Override
    public synchronized void clearGroupPermission(String groupName, String node) {
        delegate.clearGroupPermission(groupName, node);
        saveGroups();
    }

    @Override
    public synchronized Set<String> getSubjectGroups(UUID subjectId) {
        return delegate.getSubjectGroups(subjectId);
    }

    @Override
    public synchronized void addSubjectGroup(UUID subjectId, String groupName) {
        delegate.addSubjectGroup(subjectId, groupName);
        saveGroups();
    }

    @Override
    public synchronized void removeSubjectGroup(UUID subjectId, String groupName) {
        delegate.removeSubjectGroup(subjectId, groupName);
        saveGroups();
    }

    @Override
    public synchronized Set<UUID> getGroupMembers(String groupName) {
        return delegate.getGroupMembers(groupName);
    }

    private GroupData loadGroups() {
        if (Files.notExists(groupsFile)) {
            return new GroupData(Map.of(), Map.of());
        }

        try (Reader reader = Files.newBufferedReader(groupsFile, StandardCharsets.UTF_8)) {
            JsonElement rootElement = JsonParser.parseReader(reader);
            return parseRoot(rootElement);
        } catch (NoSuchFileException exception) {
            return new GroupData(Map.of(), Map.of());
        } catch (IOException | JsonParseException | IllegalArgumentException exception) {
            throw new PermissionStorageException("Failed to load groups from " + groupsFile, exception);
        }
    }

    private void saveGroups() {
        try {
            Path parentDirectory = groupsFile.getParent();
            if (parentDirectory != null) {
                Files.createDirectories(parentDirectory);
            }

            Path temporaryFile = Files.createTempFile(parentDirectory, groupsFile.getFileName().toString(), ".tmp");
            try {
                try (Writer writer = Files.newBufferedWriter(temporaryFile, StandardCharsets.UTF_8)) {
                    GSON.toJson(toJson(delegate.groupPermissionsSnapshot(), delegate.membershipsSnapshot()), writer);
                    writer.write(System.lineSeparator());
                }

                moveIntoPlace(temporaryFile);
                temporaryFile = null;
            } finally {
                if (temporaryFile != null) {
                    Files.deleteIfExists(temporaryFile);
                }
            }
        } catch (IOException exception) {
            throw new PermissionStorageException("Failed to save groups to " + groupsFile, exception);
        }
    }

    private void moveIntoPlace(Path temporaryFile) throws IOException {
        try {
            Files.move(temporaryFile, groupsFile, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(temporaryFile, groupsFile, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static GroupData parseRoot(JsonElement rootElement) {
        if (rootElement == null || !rootElement.isJsonObject()) {
            throw new IllegalArgumentException("groups root must be an object");
        }

        JsonObject root = rootElement.getAsJsonObject();
        int version = readVersion(root);
        if (version != CURRENT_VERSION) {
            throw new IllegalArgumentException("unsupported groups version " + version);
        }

        JsonElement groupsElement = root.get("groups");
        if (groupsElement == null || !groupsElement.isJsonObject()) {
            throw new IllegalArgumentException("groups must be an object");
        }

        Map<String, Map<String, PermissionValue>> groupPermissions = parseGroups(groupsElement.getAsJsonObject());
        Map<UUID, Set<String>> memberships = parseMemberships(root, groupPermissions.keySet());
        return new GroupData(groupPermissions, memberships);
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

    private static Map<String, Map<String, PermissionValue>> parseGroups(JsonObject groupsElement) {
        Map<String, Map<String, PermissionValue>> groupPermissions = new LinkedHashMap<>();
        for (Map.Entry<String, JsonElement> groupEntry : groupsElement.entrySet()) {
            String groupName = InMemoryGroupService.normalizeGroupName(groupEntry.getKey());
            if (groupPermissions.containsKey(groupName)) {
                throw new IllegalArgumentException("duplicate normalized group " + groupName);
            }
            JsonElement groupElement = groupEntry.getValue();
            if (groupElement == null || !groupElement.isJsonObject()) {
                throw new IllegalArgumentException("group " + groupEntry.getKey() + " must be an object");
            }

            JsonElement permissionsElement = groupElement.getAsJsonObject().get("permissions");
            if (permissionsElement == null || !permissionsElement.isJsonObject()) {
                throw new IllegalArgumentException("permissions for group " + groupName + " must be an object");
            }
            groupPermissions.put(groupName, parseGroupPermissions(groupName, permissionsElement.getAsJsonObject()));
        }
        return groupPermissions;
    }

    private static Map<String, PermissionValue> parseGroupPermissions(String groupName, JsonObject permissionsElement) {
        Map<String, PermissionValue> permissions = new LinkedHashMap<>();
        for (Map.Entry<String, JsonElement> permissionEntry : permissionsElement.entrySet()) {
            String normalizedNode = PermissionNodes.normalize(permissionEntry.getKey());
            permissions.put(normalizedNode, parsePermissionValue(groupName, normalizedNode, permissionEntry.getValue()));
        }
        return permissions;
    }

    private static PermissionValue parsePermissionValue(String groupName, String node, JsonElement valueElement) {
        if (valueElement == null || !valueElement.isJsonPrimitive()) {
            throw new IllegalArgumentException("permission " + node + " for group " + groupName + " must be a string");
        }

        JsonPrimitive valuePrimitive = valueElement.getAsJsonPrimitive();
        if (!valuePrimitive.isString()) {
            throw new IllegalArgumentException("permission " + node + " for group " + groupName + " must be a string");
        }

        String rawValue = valuePrimitive.getAsString();
        if (PermissionValue.TRUE.name().equals(rawValue)) {
            return PermissionValue.TRUE;
        }
        if (PermissionValue.FALSE.name().equals(rawValue)) {
            return PermissionValue.FALSE;
        }
        throw new IllegalArgumentException("permission " + node + " for group " + groupName + " must be TRUE or FALSE");
    }

    private static Map<UUID, Set<String>> parseMemberships(JsonObject root, Set<String> knownGroups) {
        JsonElement membershipsElement = root.get("memberships");
        if (membershipsElement == null || !membershipsElement.isJsonObject()) {
            throw new IllegalArgumentException("memberships must be an object");
        }

        Map<UUID, Set<String>> memberships = new LinkedHashMap<>();
        for (Map.Entry<String, JsonElement> membershipEntry : membershipsElement.getAsJsonObject().entrySet()) {
            UUID subjectId = parseSubjectId(membershipEntry.getKey());
            JsonElement subjectGroupsElement = membershipEntry.getValue();
            if (subjectGroupsElement == null || !subjectGroupsElement.isJsonArray()) {
                throw new IllegalArgumentException("memberships for subject " + membershipEntry.getKey() + " must be an array");
            }

            Set<String> subjectGroups = parseSubjectGroups(membershipEntry.getKey(), subjectGroupsElement.getAsJsonArray(), knownGroups);
            if (!subjectGroups.isEmpty()) {
                memberships.put(subjectId, subjectGroups);
            }
        }
        return memberships;
    }

    private static UUID parseSubjectId(String rawSubjectId) {
        try {
            return UUID.fromString(rawSubjectId);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("invalid subject UUID " + rawSubjectId, exception);
        }
    }

    private static Set<String> parseSubjectGroups(String subjectId, JsonArray subjectGroupsElement, Set<String> knownGroups) {
        Set<String> subjectGroups = new LinkedHashSet<>();
        for (JsonElement groupElement : subjectGroupsElement) {
            if (groupElement == null || !groupElement.isJsonPrimitive() || !groupElement.getAsJsonPrimitive().isString()) {
                throw new IllegalArgumentException("membership group for subject " + subjectId + " must be a string");
            }

            String groupName = InMemoryGroupService.normalizeGroupName(groupElement.getAsString());
            if (GroupService.DEFAULT_GROUP.equals(groupName)) {
                throw new IllegalArgumentException("subject " + subjectId + " cannot explicitly belong to default");
            }
            if (!knownGroups.contains(groupName)) {
                throw new IllegalArgumentException("subject " + subjectId + " references unknown group " + groupName);
            }
            subjectGroups.add(groupName);
        }
        return subjectGroups;
    }

    private static JsonObject toJson(Map<String, Map<String, PermissionValue>> groupPermissionsSnapshot, Map<UUID, Set<String>> membershipsSnapshot) {
        JsonObject root = new JsonObject();
        root.addProperty("version", CURRENT_VERSION);

        JsonObject groups = new JsonObject();
        groupPermissionsSnapshot.forEach((groupName, permissionsSnapshot) -> {
            JsonObject group = new JsonObject();
            JsonObject permissions = new JsonObject();
            permissionsSnapshot.forEach((node, value) -> {
                if (value != PermissionValue.UNSET) {
                    permissions.addProperty(node, value.name());
                }
            });
            group.add("permissions", permissions);
            groups.add(groupName, group);
        });
        root.add("groups", groups);

        JsonObject memberships = new JsonObject();
        membershipsSnapshot.forEach((subjectId, groupsSnapshot) -> {
            JsonArray groupsArray = new JsonArray();
            groupsSnapshot.forEach(groupsArray::add);
            memberships.add(subjectId.toString(), groupsArray);
        });
        root.add("memberships", memberships);
        return root;
    }

    private record GroupData(Map<String, Map<String, PermissionValue>> groupPermissions, Map<UUID, Set<String>> memberships) {
    }
}
