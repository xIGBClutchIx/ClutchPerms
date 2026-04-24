package me.clutchy.clutchperms.common;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Stores basic named permission groups and direct subject memberships.
 */
public interface GroupService {

    /**
     * Group applied implicitly to every subject when it exists.
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
     * Deletes a group and removes every explicit membership in that group.
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
}
