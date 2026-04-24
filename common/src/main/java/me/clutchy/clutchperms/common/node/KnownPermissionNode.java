package me.clutchy.clutchperms.common.node;

import java.util.Objects;

import me.clutchy.clutchperms.common.permission.PermissionNodes;

/**
 * Describes one exact permission node known to ClutchPerms for discovery, diagnostics, and suggestions.
 *
 * @param node normalized exact permission node
 * @param description optional human-readable description
 * @param source source that supplied this known node
 */
public record KnownPermissionNode(String node, String description, PermissionNodeSource source) {

    /**
     * Creates a known node descriptor.
     */
    public KnownPermissionNode {
        node = normalizeKnownNode(node);
        description = Objects.requireNonNull(description, "description").trim();
        source = Objects.requireNonNull(source, "source");
    }

    /**
     * Normalizes and validates an exact known permission node.
     *
     * @param node raw node
     * @return normalized exact node
     */
    public static String normalizeKnownNode(String node) {
        String normalizedNode = PermissionNodes.normalize(node);
        if (PermissionNodes.isWildcard(normalizedNode)) {
            throw new IllegalArgumentException("known permission nodes must be exact nodes");
        }
        return normalizedNode;
    }
}
