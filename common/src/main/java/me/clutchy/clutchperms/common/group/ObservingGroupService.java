package me.clutchy.clutchperms.common.group;

import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

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
}
