package me.clutchy.clutchperms.common.permission;

import java.util.Locale;
import java.util.Objects;

/**
 * Defines shared permission node constants and normalization used across the project.
 */
public final class PermissionNodes {

    /**
     * Administrative node used by the current bootstrap commands on all supported platforms.
     */
    public static final String ADMIN = "clutchperms.admin";

    /**
     * Normalizes permission nodes into the storage and resolution format used by ClutchPerms.
     *
     * @param node raw permission node supplied by the caller
     * @return a trimmed lower-case node suitable for map storage
     * @throws NullPointerException if {@code node} is {@code null}
     * @throws IllegalArgumentException if the normalized node is blank
     */
    public static String normalize(String node) {
        String normalizedNode = Objects.requireNonNull(node, "node").trim().toLowerCase(Locale.ROOT);
        if (normalizedNode.isEmpty()) {
            throw new IllegalArgumentException("node must not be blank");
        }
        return normalizedNode;
    }

    /**
     * Prevents instantiation of this constants-only utility class.
     */
    private PermissionNodes() {
    }
}
