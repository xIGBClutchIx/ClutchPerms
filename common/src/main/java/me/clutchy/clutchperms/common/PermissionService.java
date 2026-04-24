package me.clutchy.clutchperms.common;

import java.util.Map;
import java.util.UUID;

/**
 * Defines the minimal cross-platform permission service contract used by the current scaffold.
 */
public interface PermissionService {

    /**
     * Looks up the explicit permission value for a subject and permission node.
     *
     * @param subjectId unique identifier for the subject being queried
     * @param node permission node to resolve
     * @return the stored permission value, or {@link PermissionValue#UNSET} when no explicit value exists
     */
    PermissionValue getPermission(UUID subjectId, String node);

    /**
     * Lists every explicit permission assignment stored for a subject.
     *
     * <p>
     * The returned map is an immutable snapshot keyed by normalized permission node. Permissions without an explicit value are omitted.
     *
     * @param subjectId unique identifier for the subject being inspected
     * @return immutable snapshot of explicit permission assignments for the subject
     */
    Map<String, PermissionValue> getPermissions(UUID subjectId);

    /**
     * Resolves whether a subject should be treated as having a permission.
     *
     * <p>
     * The scaffold only treats {@link PermissionValue#TRUE} as granted. Both {@link PermissionValue#FALSE} and {@link PermissionValue#UNSET} are considered not granted.
     *
     * @param subjectId unique identifier for the subject being queried
     * @param node permission node to resolve
     * @return {@code true} when the stored permission value is {@link PermissionValue#TRUE}
     */
    default boolean hasPermission(UUID subjectId, String node) {
        return getPermission(subjectId, node) == PermissionValue.TRUE;
    }

    /**
     * Stores an explicit permission value for a subject and permission node.
     *
     * @param subjectId unique identifier for the subject being modified
     * @param node permission node to update
     * @param value explicit value to store
     */
    void setPermission(UUID subjectId, String node, PermissionValue value);

    /**
     * Removes any explicit permission value for a subject and permission node.
     *
     * @param subjectId unique identifier for the subject being modified
     * @param node permission node to clear
     */
    void clearPermission(UUID subjectId, String node);
}
