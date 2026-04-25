package me.clutchy.clutchperms.common.group;

import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import me.clutchy.clutchperms.common.display.DisplayProfile;
import me.clutchy.clutchperms.common.display.DisplayText;
import me.clutchy.clutchperms.common.permission.PermissionValue;

/**
 * {@link GroupService} decorator that reports successful group and membership mutations.
 */
final class ObservingGroupService implements GroupService {

    private final GroupService delegate;

    private final GroupChangeListener listener;

    ObservingGroupService(GroupService delegate, GroupChangeListener listener) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        this.listener = Objects.requireNonNull(listener, "listener");
    }

    @Override
    public Set<String> getGroups() {
        return delegate.getGroups();
    }

    @Override
    public boolean hasGroup(String groupName) {
        return delegate.hasGroup(groupName);
    }

    @Override
    public void createGroup(String groupName) {
        delegate.createGroup(groupName);
        listener.groupsChanged();
    }

    @Override
    public void deleteGroup(String groupName) {
        delegate.deleteGroup(groupName);
        listener.groupsChanged();
    }

    @Override
    public void renameGroup(String groupName, String newGroupName) {
        delegate.renameGroup(groupName, newGroupName);
        listener.groupsChanged();
    }

    @Override
    public PermissionValue getGroupPermission(String groupName, String node) {
        return delegate.getGroupPermission(groupName, node);
    }

    @Override
    public Map<String, PermissionValue> getGroupPermissions(String groupName) {
        return delegate.getGroupPermissions(groupName);
    }

    @Override
    public void setGroupPermission(String groupName, String node, PermissionValue value) {
        delegate.setGroupPermission(groupName, node, value);
        listener.groupsChanged();
    }

    @Override
    public void clearGroupPermission(String groupName, String node) {
        delegate.clearGroupPermission(groupName, node);
        listener.groupsChanged();
    }

    @Override
    public int clearGroupPermissions(String groupName) {
        int removedPermissions = delegate.clearGroupPermissions(groupName);
        if (removedPermissions > 0) {
            listener.groupsChanged();
        }
        return removedPermissions;
    }

    @Override
    public DisplayProfile getGroupDisplay(String groupName) {
        return delegate.getGroupDisplay(groupName);
    }

    @Override
    public void setGroupPrefix(String groupName, DisplayText prefix) {
        delegate.setGroupPrefix(groupName, prefix);
        listener.groupsChanged();
    }

    @Override
    public void clearGroupPrefix(String groupName) {
        delegate.clearGroupPrefix(groupName);
        listener.groupsChanged();
    }

    @Override
    public void setGroupSuffix(String groupName, DisplayText suffix) {
        delegate.setGroupSuffix(groupName, suffix);
        listener.groupsChanged();
    }

    @Override
    public void clearGroupSuffix(String groupName) {
        delegate.clearGroupSuffix(groupName);
        listener.groupsChanged();
    }

    @Override
    public Set<String> getSubjectGroups(UUID subjectId) {
        return delegate.getSubjectGroups(subjectId);
    }

    @Override
    public void addSubjectGroup(UUID subjectId, String groupName) {
        delegate.addSubjectGroup(subjectId, groupName);
        listener.subjectGroupsChanged(subjectId);
    }

    @Override
    public void removeSubjectGroup(UUID subjectId, String groupName) {
        delegate.removeSubjectGroup(subjectId, groupName);
        listener.subjectGroupsChanged(subjectId);
    }

    @Override
    public Set<UUID> getGroupMembers(String groupName) {
        return delegate.getGroupMembers(groupName);
    }

    @Override
    public Set<String> getGroupParents(String groupName) {
        return delegate.getGroupParents(groupName);
    }

    @Override
    public void addGroupParent(String groupName, String parentGroupName) {
        delegate.addGroupParent(groupName, parentGroupName);
        listener.groupsChanged();
    }

    @Override
    public void removeGroupParent(String groupName, String parentGroupName) {
        delegate.removeGroupParent(groupName, parentGroupName);
        listener.groupsChanged();
    }
}
