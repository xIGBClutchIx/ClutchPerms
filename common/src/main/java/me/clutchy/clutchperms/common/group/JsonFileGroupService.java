package me.clutchy.clutchperms.common.group;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
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

import me.clutchy.clutchperms.common.display.DisplayProfile;
import me.clutchy.clutchperms.common.display.DisplayText;
import me.clutchy.clutchperms.common.permission.PermissionNodes;
import me.clutchy.clutchperms.common.permission.PermissionValue;
import me.clutchy.clutchperms.common.storage.PermissionStorageException;
import me.clutchy.clutchperms.common.storage.StorageFileKind;
import me.clutchy.clutchperms.common.storage.StorageFiles;
import me.clutchy.clutchperms.common.storage.StorageWriteOptions;

/**
 * JSON-backed {@link GroupService} that persists basic group definitions and direct subject memberships after every mutation.
 */
final class JsonFileGroupService implements GroupService {

    private static final int CURRENT_VERSION = 1;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final Path groupsFile;

    private final StorageWriteOptions writeOptions;

    private InMemoryGroupService delegate;

    JsonFileGroupService(Path groupsFile) {
        this(groupsFile, StorageWriteOptions.defaults());
    }

    JsonFileGroupService(Path groupsFile, StorageWriteOptions writeOptions) {
        this.groupsFile = groupsFile.toAbsolutePath().normalize();
        this.writeOptions = StorageWriteOptions.defaultIfNull(writeOptions);
        GroupData groupData = loadGroups();
        this.delegate = new InMemoryGroupService(groupData.groupPermissions(), groupData.groupDisplays(), groupData.groupParents(), groupData.memberships());
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
        InMemoryGroupService candidate = copyDelegate();
        candidate.createGroup(groupName);
        commit(candidate);
    }

    @Override
    public synchronized void deleteGroup(String groupName) {
        InMemoryGroupService candidate = copyDelegate();
        candidate.deleteGroup(groupName);
        commit(candidate);
    }

    @Override
    public synchronized void renameGroup(String groupName, String newGroupName) {
        InMemoryGroupService candidate = copyDelegate();
        candidate.renameGroup(groupName, newGroupName);
        commit(candidate);
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
        InMemoryGroupService candidate = copyDelegate();
        candidate.setGroupPermission(groupName, node, value);
        commit(candidate);
    }

    @Override
    public synchronized void clearGroupPermission(String groupName, String node) {
        InMemoryGroupService candidate = copyDelegate();
        candidate.clearGroupPermission(groupName, node);
        commit(candidate);
    }

    @Override
    public synchronized DisplayProfile getGroupDisplay(String groupName) {
        return delegate.getGroupDisplay(groupName);
    }

    @Override
    public synchronized void setGroupPrefix(String groupName, DisplayText prefix) {
        InMemoryGroupService candidate = copyDelegate();
        candidate.setGroupPrefix(groupName, prefix);
        commit(candidate);
    }

    @Override
    public synchronized void clearGroupPrefix(String groupName) {
        InMemoryGroupService candidate = copyDelegate();
        candidate.clearGroupPrefix(groupName);
        commit(candidate);
    }

    @Override
    public synchronized void setGroupSuffix(String groupName, DisplayText suffix) {
        InMemoryGroupService candidate = copyDelegate();
        candidate.setGroupSuffix(groupName, suffix);
        commit(candidate);
    }

    @Override
    public synchronized void clearGroupSuffix(String groupName) {
        InMemoryGroupService candidate = copyDelegate();
        candidate.clearGroupSuffix(groupName);
        commit(candidate);
    }

    @Override
    public synchronized Set<String> getSubjectGroups(UUID subjectId) {
        return delegate.getSubjectGroups(subjectId);
    }

    @Override
    public synchronized void addSubjectGroup(UUID subjectId, String groupName) {
        InMemoryGroupService candidate = copyDelegate();
        candidate.addSubjectGroup(subjectId, groupName);
        commit(candidate);
    }

    @Override
    public synchronized void removeSubjectGroup(UUID subjectId, String groupName) {
        InMemoryGroupService candidate = copyDelegate();
        candidate.removeSubjectGroup(subjectId, groupName);
        commit(candidate);
    }

    @Override
    public synchronized Set<UUID> getGroupMembers(String groupName) {
        return delegate.getGroupMembers(groupName);
    }

    @Override
    public synchronized Set<String> getGroupParents(String groupName) {
        return delegate.getGroupParents(groupName);
    }

    @Override
    public synchronized void addGroupParent(String groupName, String parentGroupName) {
        InMemoryGroupService candidate = copyDelegate();
        candidate.addGroupParent(groupName, parentGroupName);
        commit(candidate);
    }

    @Override
    public synchronized void removeGroupParent(String groupName, String parentGroupName) {
        InMemoryGroupService candidate = copyDelegate();
        candidate.removeGroupParent(groupName, parentGroupName);
        commit(candidate);
    }

    private InMemoryGroupService copyDelegate() {
        return new InMemoryGroupService(delegate.groupPermissionsSnapshot(), delegate.groupDisplaysSnapshot(), delegate.groupParentsSnapshot(), delegate.membershipsSnapshot());
    }

    private void commit(InMemoryGroupService candidate) {
        saveGroups(candidate.groupPermissionsSnapshot(), candidate.groupDisplaysSnapshot(), candidate.groupParentsSnapshot(), candidate.membershipsSnapshot());
        delegate = candidate;
    }

    private GroupData loadGroups() {
        if (Files.notExists(groupsFile)) {
            return new GroupData(Map.of(), Map.of(), Map.of(), Map.of());
        }

        try (Reader reader = Files.newBufferedReader(groupsFile, StandardCharsets.UTF_8)) {
            JsonElement rootElement = JsonParser.parseReader(reader);
            return parseRoot(rootElement);
        } catch (NoSuchFileException exception) {
            return new GroupData(Map.of(), Map.of(), Map.of(), Map.of());
        } catch (IOException | JsonParseException | IllegalArgumentException exception) {
            throw new PermissionStorageException("Failed to load groups from " + groupsFile, exception);
        }
    }

    private void saveGroups(Map<String, Map<String, PermissionValue>> groupPermissionsSnapshot, Map<String, DisplayProfile> groupDisplaysSnapshot,
            Map<String, Set<String>> groupParentsSnapshot, Map<UUID, Set<String>> membershipsSnapshot) {
        try {
            StorageFiles.writeAtomicallyWithBackup(groupsFile, StorageFileKind.GROUPS, writeOptions, writer -> {
                GSON.toJson(toJson(groupPermissionsSnapshot, groupDisplaysSnapshot, groupParentsSnapshot, membershipsSnapshot), writer);
                writer.write(System.lineSeparator());
            });
        } catch (IOException exception) {
            throw new PermissionStorageException("Failed to save groups to " + groupsFile, exception);
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

        ParsedGroups parsedGroups = parseGroups(groupsElement.getAsJsonObject());
        Map<String, Map<String, PermissionValue>> groupPermissions = parsedGroups.groupPermissions();
        Map<String, DisplayProfile> groupDisplays = parsedGroups.groupDisplays();
        Map<String, Set<String>> groupParents = parsedGroups.groupParents();
        validateGroupParents(groupParents, groupPermissions.keySet());
        Map<UUID, Set<String>> memberships = parseMemberships(root, groupPermissions.keySet());
        return new GroupData(groupPermissions, groupDisplays, groupParents, memberships);
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

    private static ParsedGroups parseGroups(JsonObject groupsElement) {
        Map<String, Map<String, PermissionValue>> groupPermissions = new LinkedHashMap<>();
        Map<String, DisplayProfile> groupDisplays = new LinkedHashMap<>();
        Map<String, Set<String>> groupParents = new LinkedHashMap<>();
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
            groupDisplays.put(groupName, parseGroupDisplay(groupName, groupElement.getAsJsonObject()));
            groupParents.put(groupName, parseGroupParents(groupName, groupElement.getAsJsonObject()));
        }
        return new ParsedGroups(groupPermissions, groupDisplays, groupParents);
    }

    private static DisplayProfile parseGroupDisplay(String groupName, JsonObject groupElement) {
        DisplayProfile display = DisplayProfile.empty();
        JsonElement prefixElement = groupElement.get("prefix");
        if (prefixElement != null) {
            display = display.withPrefix(DisplayText.parse(readString(prefixElement, "prefix for group " + groupName)));
        }
        JsonElement suffixElement = groupElement.get("suffix");
        if (suffixElement != null) {
            display = display.withSuffix(DisplayText.parse(readString(suffixElement, "suffix for group " + groupName)));
        }
        return display;
    }

    private static Map<String, PermissionValue> parseGroupPermissions(String groupName, JsonObject permissionsElement) {
        Map<String, PermissionValue> permissions = new LinkedHashMap<>();
        for (Map.Entry<String, JsonElement> permissionEntry : permissionsElement.entrySet()) {
            String normalizedNode = PermissionNodes.normalize(permissionEntry.getKey());
            if (permissions.containsKey(normalizedNode)) {
                throw new IllegalArgumentException("duplicate normalized permission " + normalizedNode + " for group " + groupName);
            }
            permissions.put(normalizedNode, parsePermissionValue(groupName, normalizedNode, permissionEntry.getValue()));
        }
        return permissions;
    }

    private static Set<String> parseGroupParents(String groupName, JsonObject groupElement) {
        JsonElement parentsElement = groupElement.get("parents");
        if (parentsElement == null) {
            return Set.of();
        }
        if (!parentsElement.isJsonArray()) {
            throw new IllegalArgumentException("parents for group " + groupName + " must be an array");
        }

        Set<String> parents = new LinkedHashSet<>();
        for (JsonElement parentElement : parentsElement.getAsJsonArray()) {
            if (parentElement == null || !parentElement.isJsonPrimitive() || !parentElement.getAsJsonPrimitive().isString()) {
                throw new IllegalArgumentException("parent for group " + groupName + " must be a string");
            }

            String parentGroupName = InMemoryGroupService.normalizeGroupName(parentElement.getAsString());
            if (!parents.add(parentGroupName)) {
                throw new IllegalArgumentException("duplicate parent " + parentGroupName + " for group " + groupName);
            }
        }
        return parents;
    }

    private static void validateGroupParents(Map<String, Set<String>> groupParents, Set<String> knownGroups) {
        for (Map.Entry<String, Set<String>> entry : groupParents.entrySet()) {
            String groupName = entry.getKey();
            for (String parentGroupName : entry.getValue()) {
                if (groupName.equals(parentGroupName)) {
                    throw new IllegalArgumentException("group cannot inherit itself: " + groupName);
                }
                if (!knownGroups.contains(parentGroupName)) {
                    throw new IllegalArgumentException("group " + groupName + " references unknown parent " + parentGroupName);
                }
            }
        }

        for (String groupName : knownGroups) {
            validateNoParentCycle(groupName, groupName, groupParents, new LinkedHashSet<>());
        }
    }

    private static void validateNoParentCycle(String rootGroupName, String currentGroupName, Map<String, Set<String>> groupParents, Set<String> visitedGroups) {
        if (!visitedGroups.add(currentGroupName)) {
            return;
        }

        for (String parentGroupName : groupParents.getOrDefault(currentGroupName, Set.of())) {
            if (rootGroupName.equals(parentGroupName)) {
                throw new IllegalArgumentException("group inheritance cycle detected for " + rootGroupName);
            }
            validateNoParentCycle(rootGroupName, parentGroupName, groupParents, visitedGroups);
        }
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

    private static String readString(JsonElement valueElement, String owner) {
        if (valueElement == null || !valueElement.isJsonPrimitive() || !valueElement.getAsJsonPrimitive().isString()) {
            throw new IllegalArgumentException(owner + " must be a string");
        }
        return valueElement.getAsString();
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

    private static JsonObject toJson(Map<String, Map<String, PermissionValue>> groupPermissionsSnapshot, Map<String, DisplayProfile> groupDisplaysSnapshot,
            Map<String, Set<String>> groupParentsSnapshot, Map<UUID, Set<String>> membershipsSnapshot) {
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
            DisplayProfile display = groupDisplaysSnapshot.getOrDefault(groupName, DisplayProfile.empty());
            display.prefix().ifPresent(prefix -> group.addProperty("prefix", prefix.rawText()));
            display.suffix().ifPresent(suffix -> group.addProperty("suffix", suffix.rawText()));
            Set<String> parentsSnapshot = groupParentsSnapshot.getOrDefault(groupName, Set.of());
            if (!parentsSnapshot.isEmpty()) {
                JsonArray parents = new JsonArray();
                parentsSnapshot.forEach(parents::add);
                group.add("parents", parents);
            }
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

    private record ParsedGroups(Map<String, Map<String, PermissionValue>> groupPermissions, Map<String, DisplayProfile> groupDisplays, Map<String, Set<String>> groupParents) {
    }

    private record GroupData(Map<String, Map<String, PermissionValue>> groupPermissions, Map<String, DisplayProfile> groupDisplays, Map<String, Set<String>> groupParents,
            Map<UUID, Set<String>> memberships) {
    }
}
