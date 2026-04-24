package me.clutchy.clutchperms.common.permission;

/**
 * Describes the effective permission value for one subject and node.
 *
 * @param value effective value after direct, group, and default resolution
 * @param source source tier that provided the effective value
 * @param groupName group that provided the value, or {@code null} when the source is not group-based
 * @param assignmentNode normalized assignment node that supplied the value, or {@code null} when unset
 */
public record PermissionResolution(PermissionValue value, Source source, String groupName, String assignmentNode) {

    /**
     * Creates immutable resolution details without assignment-node metadata.
     *
     * @param value effective value after direct, group, and default resolution
     * @param source source tier that provided the effective value
     * @param groupName group that provided the value, or {@code null} when the source is not group-based
     */
    public PermissionResolution(PermissionValue value, Source source, String groupName) {
        this(value, source, groupName, null);
    }

    /**
     * Creates immutable resolution details.
     */
    public PermissionResolution {
        value = java.util.Objects.requireNonNull(value, "value");
        source = java.util.Objects.requireNonNull(source, "source");
        if (assignmentNode != null) {
            assignmentNode = PermissionNodes.normalize(assignmentNode);
        }
    }

    /**
     * Effective permission source tier.
     */
    public enum Source {
        /**
         * Explicit direct user assignment.
         */
        DIRECT,

        /**
         * Explicit subject group assignment.
         */
        GROUP,

        /**
         * Implicit default group assignment.
         */
        DEFAULT,

        /**
         * No direct, group, or default assignment.
         */
        UNSET
    }
}
