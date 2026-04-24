package me.clutchy.clutchperms.common.node;

/**
 * Identifies where a known permission node came from.
 */
public enum PermissionNodeSource {

    /**
     * Node built into ClutchPerms itself.
     */
    BUILT_IN,

    /**
     * Node manually registered by an administrator in ClutchPerms storage.
     */
    MANUAL,

    /**
     * Node discovered from the active platform permission registry.
     */
    PLATFORM
}
