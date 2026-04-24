package me.clutchy.clutchperms.common.permission;

import java.util.List;
import java.util.Objects;

/**
 * Explains how a permission node resolved for one subject.
 *
 * @param node normalized node that was checked
 * @param resolution winning effective resolution, or {@link PermissionValue#UNSET} when no assignment matched
 * @param matches matching explicit assignments in resolver precedence order
 */
public record PermissionExplanation(String node, PermissionResolution resolution, List<Match> matches) {

    /**
     * Creates a permission explanation snapshot.
     *
     * @param node node that was checked
     * @param resolution winning effective resolution
     * @param matches matching explicit assignments in resolver precedence order
     */
    public PermissionExplanation {
        node = PermissionNodes.normalize(node);
        resolution = Objects.requireNonNull(resolution, "resolution");
        matches = List.copyOf(matches);
    }

    /**
     * One explicit assignment that matched the checked node.
     *
     * @param source source tier that supplied the assignment
     * @param groupName group that supplied the assignment, or {@code null} for direct assignments
     * @param depth inheritance depth inside the group/default hierarchy
     * @param assignmentNode normalized explicit assignment node that matched
     * @param value explicit assignment value
     * @param winning whether this assignment supplied the final effective value
     */
    public record Match(PermissionResolution.Source source, String groupName, int depth, String assignmentNode, PermissionValue value, boolean winning) {

        /**
         * Creates a matching assignment snapshot.
         *
         * @param source source tier that supplied the assignment
         * @param groupName group that supplied the assignment, or {@code null} for direct assignments
         * @param depth inheritance depth inside the group/default hierarchy
         * @param assignmentNode explicit assignment node that matched
         * @param value explicit assignment value
         * @param winning whether this assignment supplied the final effective value
         */
        public Match {
            source = Objects.requireNonNull(source, "source");
            assignmentNode = PermissionNodes.normalize(assignmentNode);
            value = Objects.requireNonNull(value, "value");
            if (value == PermissionValue.UNSET) {
                throw new IllegalArgumentException("explanation matches must be explicit TRUE or FALSE assignments");
            }
            if (depth < 0) {
                throw new IllegalArgumentException("depth must not be negative");
            }
        }
    }
}
