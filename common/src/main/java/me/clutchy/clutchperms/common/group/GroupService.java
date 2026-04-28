package me.clutchy.clutchperms.common.group;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

import me.clutchy.clutchperms.common.display.DisplayProfile;
import me.clutchy.clutchperms.common.display.DisplayText;
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
     * Protected built-in group that grants every permission only to explicitly assigned subjects.
     */
    String OP_GROUP = "op";

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
     * Renames a group and updates every parent link and explicit membership that references it. The built-in {@link #DEFAULT_GROUP} cannot be renamed or used as the new name.
     *
     * @param groupName group name to rename
     * @param newGroupName new group name to use
     */
    void renameGroup(String groupName, String newGroupName);

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
     * Clears every explicit permission assignment from one group.
     *
     * @param groupName group name to update
     * @return number of explicit assignments removed
     */
    int clearGroupPermissions(String groupName);

    /**
     * Looks up direct display values for a group.
     *
     * @param groupName group name to inspect
     * @return direct group display values
     */
    DisplayProfile getGroupDisplay(String groupName);

    /**
     * Stores a direct group prefix.
     *
     * @param groupName group name to update
     * @param prefix prefix to store
     */
    void setGroupPrefix(String groupName, DisplayText prefix);

    /**
     * Clears a direct group prefix.
     *
     * @param groupName group name to update
     */
    void clearGroupPrefix(String groupName);

    /**
     * Stores a direct group suffix.
     *
     * @param groupName group name to update
     * @param suffix suffix to store
     */
    void setGroupSuffix(String groupName, DisplayText suffix);

    /**
     * Clears a direct group suffix.
     *
     * @param groupName group name to update
     */
    void clearGroupSuffix(String groupName);

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
     * Replaces every explicit group membership stored for one subject.
     *
     * @param subjectId subject UUID to update
     * @param groupNames explicit normalized groups to store
     */
    void setSubjectGroups(UUID subjectId, Set<String> groupNames);

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
