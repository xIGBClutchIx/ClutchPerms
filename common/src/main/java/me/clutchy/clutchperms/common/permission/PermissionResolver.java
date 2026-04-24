package me.clutchy.clutchperms.common.permission;

import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.UUID;

import me.clutchy.clutchperms.common.group.GroupService;

/**
 * Resolves effective permissions from direct user assignments, explicit groups, and the implicit default group.
 */
public final class PermissionResolver {

    private final PermissionService permissionService;

    private final GroupService groupService;

    /**
     * Creates a resolver over the current direct permission and group services.
     *
     * @param permissionService direct user permission service
     * @param groupService group service
     */
    public PermissionResolver(PermissionService permissionService, GroupService groupService) {
        this.permissionService = Objects.requireNonNull(permissionService, "permissionService");
        this.groupService = Objects.requireNonNull(groupService, "groupService");
    }

    /**
     * Resolves one effective permission value for a subject.
     *
     * @param subjectId subject UUID to resolve
     * @param node permission node to resolve
     * @return effective permission resolution details
     */
    public PermissionResolution resolve(UUID subjectId, String node) {
        Objects.requireNonNull(subjectId, "subjectId");

        PermissionValue directValue = permissionService.getPermission(subjectId, node);
        if (directValue != PermissionValue.UNSET) {
            return new PermissionResolution(directValue, PermissionResolution.Source.DIRECT, null);
        }

        PermissionResolution groupResolution = resolveSubjectGroups(subjectId, node);
        if (groupResolution.value() != PermissionValue.UNSET) {
            return groupResolution;
        }

        if (groupService.hasGroup(GroupService.DEFAULT_GROUP)) {
            PermissionValue defaultValue = groupService.getGroupPermission(GroupService.DEFAULT_GROUP, node);
            if (defaultValue != PermissionValue.UNSET) {
                return new PermissionResolution(defaultValue, PermissionResolution.Source.DEFAULT, GroupService.DEFAULT_GROUP);
            }
        }

        return new PermissionResolution(PermissionValue.UNSET, PermissionResolution.Source.UNSET, null);
    }

    /**
     * Resolves whether a subject should be treated as having a permission.
     *
     * @param subjectId subject UUID to resolve
     * @param node permission node to resolve
     * @return {@code true} when the effective value is {@link PermissionValue#TRUE}
     */
    public boolean hasPermission(UUID subjectId, String node) {
        return resolve(subjectId, node).value() == PermissionValue.TRUE;
    }

    /**
     * Lists every effective non-{@link PermissionValue#UNSET} permission for a subject.
     *
     * @param subjectId subject UUID to resolve
     * @return immutable snapshot of effective permission assignments keyed by normalized node
     */
    public Map<String, PermissionValue> getEffectivePermissions(UUID subjectId) {
        Objects.requireNonNull(subjectId, "subjectId");
        Set<String> nodes = new TreeSet<>();
        nodes.addAll(permissionService.getPermissions(subjectId).keySet());

        for (String groupName : groupService.getSubjectGroups(subjectId)) {
            if (groupService.hasGroup(groupName)) {
                nodes.addAll(groupService.getGroupPermissions(groupName).keySet());
            }
        }

        if (groupService.hasGroup(GroupService.DEFAULT_GROUP)) {
            nodes.addAll(groupService.getGroupPermissions(GroupService.DEFAULT_GROUP).keySet());
        }

        Map<String, PermissionValue> effectivePermissions = new TreeMap<>();
        for (String node : nodes) {
            PermissionValue value = resolve(subjectId, node).value();
            if (value != PermissionValue.UNSET) {
                effectivePermissions.put(node, value);
            }
        }
        return Map.copyOf(effectivePermissions);
    }

    private PermissionResolution resolveSubjectGroups(UUID subjectId, String node) {
        String trueGroup = null;
        for (String groupName : groupService.getSubjectGroups(subjectId)) {
            if (!groupService.hasGroup(groupName)) {
                continue;
            }

            PermissionValue groupValue = groupService.getGroupPermission(groupName, node);
            if (groupValue == PermissionValue.FALSE) {
                return new PermissionResolution(PermissionValue.FALSE, PermissionResolution.Source.GROUP, groupName);
            }
            if (groupValue == PermissionValue.TRUE && trueGroup == null) {
                trueGroup = groupName;
            }
        }

        if (trueGroup != null) {
            return new PermissionResolution(PermissionValue.TRUE, PermissionResolution.Source.GROUP, trueGroup);
        }

        return new PermissionResolution(PermissionValue.UNSET, PermissionResolution.Source.UNSET, null);
    }
}
