package me.clutchy.clutchperms.common.group;

import java.util.Collections;
import java.util.Comparator;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import me.clutchy.clutchperms.common.display.DisplayProfile;
import me.clutchy.clutchperms.common.display.DisplayText;
import me.clutchy.clutchperms.common.permission.PermissionNodes;
import me.clutchy.clutchperms.common.permission.PermissionValue;

/**
 * Thread-safe in-memory {@link GroupService} implementation for basic groups and direct subject memberships.
 */
public final class InMemoryGroupService implements GroupService {

    private static final String OP_PERMISSION_NODE = "*";

    private final ConcurrentMap<String, ConcurrentMap<String, PermissionValue>> groupPermissions = new ConcurrentHashMap<>();

    private final ConcurrentMap<String, DisplayProfile> groupDisplays = new ConcurrentHashMap<>();

    private final ConcurrentMap<String, Set<String>> groupParents = new ConcurrentHashMap<>();

    private final ConcurrentMap<UUID, Set<String>> memberships = new ConcurrentHashMap<>();

    /**
     * Creates an empty in-memory group service.
     */
    public InMemoryGroupService() {
        ensureBuiltInGroups();
    }

    InMemoryGroupService(Map<String, Map<String, PermissionValue>> initialGroupPermissions, Map<String, Set<String>> initialGroupParents,
            Map<UUID, Set<String>> initialMemberships) {
        this(initialGroupPermissions, Map.of(), initialGroupParents, initialMemberships);
    }

    InMemoryGroupService(Map<String, Map<String, PermissionValue>> initialGroupPermissions, Map<String, DisplayProfile> initialGroupDisplays,
            Map<String, Set<String>> initialGroupParents, Map<UUID, Set<String>> initialMemberships) {
        this();
        Objects.requireNonNull(initialGroupPermissions, "initialGroupPermissions");
        Objects.requireNonNull(initialGroupDisplays, "initialGroupDisplays");
        Objects.requireNonNull(initialGroupParents, "initialGroupParents");
        Objects.requireNonNull(initialMemberships, "initialMemberships");

        initialGroupPermissions.forEach((groupName, permissions) -> {
            String normalizedGroupName = normalizeGroupName(groupName);
            if (GroupService.OP_GROUP.equals(normalizedGroupName)) {
                validateOpPermissions(permissions);
                return;
            }
            if (!hasGroup(normalizedGroupName)) {
                createGroup(normalizedGroupName);
            }
            permissions.forEach((node, value) -> setGroupPermission(normalizedGroupName, node, value));
        });
        initialGroupDisplays.forEach((groupName, display) -> {
            String normalizedGroupName = normalizeGroupName(groupName);
            if (GroupService.OP_GROUP.equals(normalizedGroupName)) {
                validateOpDisplay(display);
                return;
            }
            if (!hasGroup(normalizedGroupName)) {
                createGroup(normalizedGroupName);
            }
            display.prefix().ifPresent(prefix -> setGroupPrefix(normalizedGroupName, prefix));
            display.suffix().ifPresent(suffix -> setGroupSuffix(normalizedGroupName, suffix));
        });
        initialGroupParents.forEach((groupName, parents) -> parents.forEach(parentGroupName -> addGroupParent(groupName, parentGroupName)));
        initialMemberships.forEach((subjectId, groups) -> groups.forEach(groupName -> addSubjectGroup(subjectId, groupName)));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Set<String> getGroups() {
        return Collections.unmodifiableSet(new TreeSet<>(groupPermissions.keySet()));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean hasGroup(String groupName) {
        return groupPermissions.containsKey(normalizeGroupName(groupName));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void createGroup(String groupName) {
        String normalizedGroupName = normalizeGroupName(groupName);
        if (groupPermissions.putIfAbsent(normalizedGroupName, new ConcurrentHashMap<>()) != null) {
            throw new IllegalArgumentException("group already exists: " + normalizedGroupName);
        }
        groupParents.putIfAbsent(normalizedGroupName, ConcurrentHashMap.newKeySet());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void deleteGroup(String groupName) {
        String normalizedGroupName = normalizeExistingGroupName(groupName);
        if (GroupService.DEFAULT_GROUP.equals(normalizedGroupName)) {
            throw new IllegalArgumentException("default group cannot be deleted");
        }
        if (GroupService.OP_GROUP.equals(normalizedGroupName)) {
            throw new IllegalArgumentException("op group cannot be deleted");
        }
        groupPermissions.remove(normalizedGroupName);
        groupDisplays.remove(normalizedGroupName);
        groupParents.remove(normalizedGroupName);
        groupParents.forEach((ignored, parents) -> parents.remove(normalizedGroupName));
        memberships.forEach((subjectId, groups) -> groups.remove(normalizedGroupName));
        memberships.entrySet().removeIf(entry -> entry.getValue().isEmpty());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void renameGroup(String groupName, String newGroupName) {
        String normalizedGroupName = normalizeExistingGroupName(groupName);
        String normalizedNewGroupName = normalizeGroupName(newGroupName);
        if (GroupService.DEFAULT_GROUP.equals(normalizedGroupName)) {
            throw new IllegalArgumentException("default group cannot be renamed");
        }
        if (GroupService.OP_GROUP.equals(normalizedGroupName)) {
            throw new IllegalArgumentException("op group cannot be renamed");
        }
        if (GroupService.DEFAULT_GROUP.equals(normalizedNewGroupName)) {
            throw new IllegalArgumentException("group cannot be renamed to default");
        }
        if (GroupService.OP_GROUP.equals(normalizedNewGroupName)) {
            throw new IllegalArgumentException("group cannot be renamed to op");
        }
        if (groupPermissions.containsKey(normalizedNewGroupName)) {
            throw new IllegalArgumentException("group already exists: " + normalizedNewGroupName);
        }

        ConcurrentMap<String, PermissionValue> permissions = groupPermissions.remove(normalizedGroupName);
        groupPermissions.put(normalizedNewGroupName, permissions);

        DisplayProfile display = groupDisplays.remove(normalizedGroupName);
        if (display != null && !display.isEmpty()) {
            groupDisplays.put(normalizedNewGroupName, display);
        }

        Set<String> parents = groupParents.remove(normalizedGroupName);
        groupParents.put(normalizedNewGroupName, parents == null ? ConcurrentHashMap.newKeySet() : parents);
        groupParents.forEach((ignored, currentParents) -> {
            if (currentParents.remove(normalizedGroupName)) {
                currentParents.add(normalizedNewGroupName);
            }
        });

        memberships.forEach((ignored, groups) -> {
            if (groups.remove(normalizedGroupName)) {
                groups.add(normalizedNewGroupName);
            }
        });
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public PermissionValue getGroupPermission(String groupName, String node) {
        String normalizedGroupName = normalizeExistingGroupName(groupName);
        String normalizedNode = PermissionNodes.normalize(node);
        return groupPermissions.get(normalizedGroupName).getOrDefault(normalizedNode, PermissionValue.UNSET);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Map<String, PermissionValue> getGroupPermissions(String groupName) {
        String normalizedGroupName = normalizeExistingGroupName(groupName);
        Map<String, PermissionValue> permissions = groupPermissions.get(normalizedGroupName);
        if (permissions.isEmpty()) {
            return Map.of();
        }
        return Collections.unmodifiableMap(new TreeMap<>(permissions));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setGroupPermission(String groupName, String node, PermissionValue value) {
        Objects.requireNonNull(value, "value");
        String normalizedGroupName = normalizeExistingGroupName(groupName);
        String normalizedNode = PermissionNodes.normalize(node);
        rejectOpDefinitionMutation(normalizedGroupName, "op group permissions are protected");
        if (value == PermissionValue.UNSET) {
            clearGroupPermission(normalizedGroupName, normalizedNode);
            return;
        }
        groupPermissions.get(normalizedGroupName).put(normalizedNode, value);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void clearGroupPermission(String groupName, String node) {
        String normalizedGroupName = normalizeExistingGroupName(groupName);
        String normalizedNode = PermissionNodes.normalize(node);
        rejectOpDefinitionMutation(normalizedGroupName, "op group permissions are protected");
        groupPermissions.get(normalizedGroupName).remove(normalizedNode);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int clearGroupPermissions(String groupName) {
        String normalizedGroupName = normalizeExistingGroupName(groupName);
        rejectOpDefinitionMutation(normalizedGroupName, "op group permissions are protected");
        Map<String, PermissionValue> permissions = groupPermissions.get(normalizedGroupName);
        int removedPermissions = permissions.size();
        permissions.clear();
        return removedPermissions;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public DisplayProfile getGroupDisplay(String groupName) {
        String normalizedGroupName = normalizeExistingGroupName(groupName);
        return groupDisplays.getOrDefault(normalizedGroupName, DisplayProfile.empty());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setGroupPrefix(String groupName, DisplayText prefix) {
        Objects.requireNonNull(prefix, "prefix");
        String normalizedGroupName = normalizeExistingGroupName(groupName);
        rejectOpDefinitionMutation(normalizedGroupName, "op group display is protected");
        groupDisplays.compute(normalizedGroupName, (ignored, display) -> Objects.requireNonNullElseGet(display, DisplayProfile::empty).withPrefix(prefix));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void clearGroupPrefix(String groupName) {
        String normalizedGroupName = normalizeExistingGroupName(groupName);
        rejectOpDefinitionMutation(normalizedGroupName, "op group display is protected");
        groupDisplays.computeIfPresent(normalizedGroupName, (ignored, display) -> {
            DisplayProfile updated = display.withoutPrefix();
            return updated.isEmpty() ? null : updated;
        });
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setGroupSuffix(String groupName, DisplayText suffix) {
        Objects.requireNonNull(suffix, "suffix");
        String normalizedGroupName = normalizeExistingGroupName(groupName);
        rejectOpDefinitionMutation(normalizedGroupName, "op group display is protected");
        groupDisplays.compute(normalizedGroupName, (ignored, display) -> Objects.requireNonNullElseGet(display, DisplayProfile::empty).withSuffix(suffix));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void clearGroupSuffix(String groupName) {
        String normalizedGroupName = normalizeExistingGroupName(groupName);
        rejectOpDefinitionMutation(normalizedGroupName, "op group display is protected");
        groupDisplays.computeIfPresent(normalizedGroupName, (ignored, display) -> {
            DisplayProfile updated = display.withoutSuffix();
            return updated.isEmpty() ? null : updated;
        });
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Set<String> getSubjectGroups(UUID subjectId) {
        Objects.requireNonNull(subjectId, "subjectId");
        Set<String> groups = memberships.get(subjectId);
        if (groups == null || groups.isEmpty()) {
            return Set.of();
        }
        return Collections.unmodifiableSet(new TreeSet<>(groups));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void addSubjectGroup(UUID subjectId, String groupName) {
        Objects.requireNonNull(subjectId, "subjectId");
        String normalizedGroupName = normalizeExistingGroupName(groupName);
        rejectDefaultMembership(normalizedGroupName);
        memberships.computeIfAbsent(subjectId, ignored -> ConcurrentHashMap.newKeySet()).add(normalizedGroupName);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void removeSubjectGroup(UUID subjectId, String groupName) {
        Objects.requireNonNull(subjectId, "subjectId");
        String normalizedGroupName = normalizeExistingGroupName(groupName);
        rejectDefaultMembership(normalizedGroupName);
        memberships.computeIfPresent(subjectId, (ignored, groups) -> {
            groups.remove(normalizedGroupName);
            return groups.isEmpty() ? null : groups;
        });
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setSubjectGroups(UUID subjectId, Set<String> groupNames) {
        Objects.requireNonNull(subjectId, "subjectId");
        Objects.requireNonNull(groupNames, "groupNames");
        Set<String> normalizedGroups = new TreeSet<>();
        for (String groupName : groupNames) {
            String normalizedGroupName = normalizeExistingGroupName(groupName);
            rejectDefaultMembership(normalizedGroupName);
            normalizedGroups.add(normalizedGroupName);
        }
        if (normalizedGroups.isEmpty()) {
            memberships.remove(subjectId);
            return;
        }
        Set<String> replacement = ConcurrentHashMap.newKeySet();
        replacement.addAll(normalizedGroups);
        memberships.put(subjectId, replacement);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Set<UUID> getGroupMembers(String groupName) {
        String normalizedGroupName = normalizeExistingGroupName(groupName);
        Set<UUID> members = new TreeSet<>(Comparator.comparing(UUID::toString));
        memberships.forEach((subjectId, groups) -> {
            if (groups.contains(normalizedGroupName)) {
                members.add(subjectId);
            }
        });
        return Collections.unmodifiableSet(members);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Set<String> getGroupParents(String groupName) {
        String normalizedGroupName = normalizeExistingGroupName(groupName);
        Set<String> parents = groupParents.get(normalizedGroupName);
        if (parents == null || parents.isEmpty()) {
            return Set.of();
        }
        return Collections.unmodifiableSet(new TreeSet<>(parents));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void addGroupParent(String groupName, String parentGroupName) {
        String normalizedGroupName = normalizeExistingGroupName(groupName);
        String normalizedParentGroupName = normalizeExistingGroupName(parentGroupName);
        rejectOpInheritanceMutation(normalizedGroupName, normalizedParentGroupName);
        if (normalizedGroupName.equals(normalizedParentGroupName)) {
            throw new IllegalArgumentException("group cannot inherit itself: " + normalizedGroupName);
        }
        if (createsCycle(normalizedGroupName, normalizedParentGroupName)) {
            throw new IllegalArgumentException("group inheritance cycle detected between " + normalizedGroupName + " and " + normalizedParentGroupName);
        }
        groupParents.computeIfAbsent(normalizedGroupName, ignored -> ConcurrentHashMap.newKeySet()).add(normalizedParentGroupName);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void removeGroupParent(String groupName, String parentGroupName) {
        String normalizedGroupName = normalizeExistingGroupName(groupName);
        String normalizedParentGroupName = normalizeExistingGroupName(parentGroupName);
        rejectOpInheritanceMutation(normalizedGroupName, normalizedParentGroupName);
        groupParents.computeIfPresent(normalizedGroupName, (ignored, parents) -> {
            parents.remove(normalizedParentGroupName);
            return parents;
        });
    }

    static String normalizeGroupName(String groupName) {
        String normalizedGroupName = Objects.requireNonNull(groupName, "groupName").trim().toLowerCase(Locale.ROOT);
        if (normalizedGroupName.isEmpty()) {
            throw new IllegalArgumentException("group name must not be blank");
        }
        return normalizedGroupName;
    }

    Map<String, Map<String, PermissionValue>> groupPermissionsSnapshot() {
        Map<String, Map<String, PermissionValue>> snapshot = new TreeMap<>();
        groupPermissions.forEach((groupName, permissions) -> snapshot.put(groupName, Collections.unmodifiableMap(new TreeMap<>(permissions))));
        return Collections.unmodifiableMap(snapshot);
    }

    Map<String, DisplayProfile> groupDisplaysSnapshot() {
        Map<String, DisplayProfile> snapshot = new TreeMap<>();
        groupDisplays.forEach((groupName, display) -> {
            if (!display.isEmpty()) {
                snapshot.put(groupName, display);
            }
        });
        return Collections.unmodifiableMap(snapshot);
    }

    Map<UUID, Set<String>> membershipsSnapshot() {
        Map<UUID, Set<String>> snapshot = new TreeMap<>(Comparator.comparing(UUID::toString));
        memberships.forEach((subjectId, groups) -> {
            if (!groups.isEmpty()) {
                snapshot.put(subjectId, Collections.unmodifiableSet(new TreeSet<>(groups)));
            }
        });
        return Collections.unmodifiableMap(snapshot);
    }

    Map<String, Set<String>> groupParentsSnapshot() {
        Map<String, Set<String>> snapshot = new TreeMap<>();
        groupParents.forEach((groupName, parents) -> {
            if (!parents.isEmpty()) {
                snapshot.put(groupName, Collections.unmodifiableSet(new TreeSet<>(parents)));
            }
        });
        return Collections.unmodifiableMap(snapshot);
    }

    private String normalizeExistingGroupName(String groupName) {
        String normalizedGroupName = normalizeGroupName(groupName);
        if (!groupPermissions.containsKey(normalizedGroupName)) {
            throw new IllegalArgumentException("unknown group: " + normalizedGroupName);
        }
        return normalizedGroupName;
    }

    private void ensureBuiltInGroups() {
        groupPermissions.putIfAbsent(GroupService.DEFAULT_GROUP, new ConcurrentHashMap<>());
        groupDisplays.putIfAbsent(GroupService.DEFAULT_GROUP, DisplayProfile.empty());
        groupParents.putIfAbsent(GroupService.DEFAULT_GROUP, ConcurrentHashMap.newKeySet());
        groupPermissions.computeIfAbsent(GroupService.OP_GROUP, ignored -> new ConcurrentHashMap<>()).put(OP_PERMISSION_NODE, PermissionValue.TRUE);
        groupDisplays.putIfAbsent(GroupService.OP_GROUP, DisplayProfile.empty());
        groupParents.putIfAbsent(GroupService.OP_GROUP, ConcurrentHashMap.newKeySet());
    }

    private static void rejectDefaultMembership(String normalizedGroupName) {
        if (GroupService.DEFAULT_GROUP.equals(normalizedGroupName)) {
            throw new IllegalArgumentException("default group membership is implicit");
        }
    }

    private static void validateOpPermissions(Map<String, PermissionValue> permissions) {
        Map<String, PermissionValue> normalizedPermissions = new TreeMap<>();
        permissions.forEach((node, value) -> normalizedPermissions.put(PermissionNodes.normalize(node), Objects.requireNonNull(value, "value")));
        if (!Map.of(OP_PERMISSION_NODE, PermissionValue.TRUE).equals(normalizedPermissions)) {
            throw new IllegalArgumentException("op group must contain only *=TRUE");
        }
    }

    private static void validateOpDisplay(DisplayProfile display) {
        if (!Objects.requireNonNull(display, "display").isEmpty()) {
            throw new IllegalArgumentException("op group display is protected");
        }
    }

    private static void rejectOpDefinitionMutation(String normalizedGroupName, String message) {
        if (GroupService.OP_GROUP.equals(normalizedGroupName)) {
            throw new IllegalArgumentException(message);
        }
    }

    private static void rejectOpInheritanceMutation(String normalizedGroupName, String normalizedParentGroupName) {
        if (GroupService.OP_GROUP.equals(normalizedGroupName) || GroupService.OP_GROUP.equals(normalizedParentGroupName)) {
            throw new IllegalArgumentException("op group inheritance is protected");
        }
    }

    private boolean createsCycle(String childGroupName, String parentGroupName) {
        Set<String> visited = new TreeSet<>();
        return reachesGroup(parentGroupName, childGroupName, visited);
    }

    private boolean reachesGroup(String currentGroupName, String targetGroupName, Set<String> visited) {
        if (!visited.add(currentGroupName)) {
            return false;
        }
        if (currentGroupName.equals(targetGroupName)) {
            return true;
        }
        Set<String> parents = groupParents.get(currentGroupName);
        if (parents == null || parents.isEmpty()) {
            return false;
        }
        for (String parentGroupName : parents) {
            if (reachesGroup(parentGroupName, targetGroupName, visited)) {
                return true;
            }
        }
        return false;
    }
}
