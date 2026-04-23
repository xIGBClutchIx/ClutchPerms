package me.clutchy.clutchperms.common;

/**
 * Represents the explicit state of a permission assignment.
 */
public enum PermissionValue {
    /**
     * The permission is explicitly granted.
     */
    TRUE,

    /**
     * The permission is explicitly denied.
     */
    FALSE,

    /**
     * No explicit value is stored for the permission.
     */
    UNSET
}
