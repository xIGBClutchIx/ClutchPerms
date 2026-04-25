package me.clutchy.clutchperms.common.group;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

import me.clutchy.clutchperms.common.permission.PermissionValue;

/**
 * Stores basic named permission groups and direct subject memberships.
 */
public interface GroupService {

    /**
     * Built-in group applied implicitly to every subject.
     */
    String DEFAULT_GROUP = "default";

    /**
     * Lists every defined group.
     *
     * @return immutable snapshot of normalized group names
     */
    Set<String> getGroups();

    /**
     * Checks whether a group exists.
     *
     * @param groupName group name to inspect
     * @return {@code true} when the group exists
     */
    boolean hasGroup(String groupName);

    /**
     * Creates a group.
     *
     * @param groupName group name to create
     */
    void createGroup(String groupName);

    /**
     * Deletes a group and removes every explicit membership in that group. The built-in {@link #DEFAULT_GROUP} cannot be deleted.
     *
     * @param groupName group name to delete
     */
    void deleteGroup(String groupName);

    /**
     * Looks up one explicit group permission assignment.
     *
     * @param groupName group name to inspect
     * @param node permission node to inspect
     * @return stored permission value, or {@link PermissionValue#UNSET} when absent
     */
    PermissionValue getGroupPermission(String groupName, String node);

    /**
     * Lists every explicit permission assignment stored on a group.
     *
     * @param groupName group name to inspect
     * @return immutable snapshot of normalized permission assignments
     */
    Map<String, PermissionValue> getGroupPermissions(String groupName);

    /**
     * Stores an explicit group permission assignment.
     *
     * @param groupName group name to update
     * @param node permission node to update
     * @param value permission value to store
     */
    void setGroupPermission(String groupName, String node, PermissionValue value);

    /**
     * Clears one explicit group permission assignment.
     *
     * @param groupName group name to update
     * @param node permission node to clear
     */
    void clearGroupPermission(String groupName, String node);

    /**
     * Lists explicit groups assigned to one subject.
     *
     * @param subjectId subject UUID to inspect
     * @return immutable snapshot of normalized group names
     */
    Set<String> getSubjectGroups(UUID subjectId);

    /**
     * Adds one explicit subject membership.
     *
     * @param subjectId subject UUID to update
     * @param groupName group name to assign
     */
    void addSubjectGroup(UUID subjectId, String groupName);

    /**
     * Removes one explicit subject membership.
     *
     * @param subjectId subject UUID to update
     * @param groupName group name to remove
     */
    void removeSubjectGroup(UUID subjectId, String groupName);

    /**
     * Lists subjects explicitly assigned to one group.
     *
     * @param groupName group name to inspect
     * @return immutable snapshot of subject UUIDs
     */
    Set<UUID> getGroupMembers(String groupName);

    /**
     * Lists direct parent groups inherited by one group.
     *
     * @param groupName group name to inspect
     * @return immutable snapshot of normalized parent group names
     */
    Set<String> getGroupParents(String groupName);

    /**
     * Adds one direct parent group link.
     *
     * @param groupName group name that will inherit from the parent
     * @param parentGroupName parent group to inherit
     */
    void addGroupParent(String groupName, String parentGroupName);

    /**
     * Removes one direct parent group link.
     *
     * @param groupName group name to update
     * @param parentGroupName parent group to remove
     */
    void removeGroupParent(String groupName, String parentGroupName);
}
