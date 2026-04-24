package me.clutchy.clutchperms.common.permission;

import java.util.ArrayList;
import java.util.List;
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
        if (normalizedNode.chars().anyMatch(Character::isWhitespace)) {
            throw new IllegalArgumentException("node must not contain whitespace");
        }
        validateWildcardPlacement(normalizedNode);
        return normalizedNode;
    }

    /**
     * Lists normalized assignment nodes that can resolve the supplied node, ordered from most specific to broadest.
     *
     * @param node raw permission node being resolved
     * @return immutable list of matching assignment candidates
     */
    public static List<String> matchingCandidates(String node) {
        String normalizedNode = normalize(node);
        List<String> candidates = new ArrayList<>();
        candidates.add(normalizedNode);

        if (normalizedNode.equals("*")) {
            return List.copyOf(candidates);
        }

        if (isWildcard(normalizedNode)) {
            candidates.add("*");
            return List.copyOf(candidates);
        }

        int separatorIndex = normalizedNode.lastIndexOf('.');
        while (separatorIndex > 0) {
            candidates.add(normalizedNode.substring(0, separatorIndex) + ".*");
            separatorIndex = normalizedNode.lastIndexOf('.', separatorIndex - 1);
        }
        candidates.add("*");
        return List.copyOf(candidates);
    }

    /**
     * Checks whether a normalized permission node is a supported terminal wildcard assignment.
     *
     * @param normalizedNode normalized permission node
     * @return {@code true} when the node is {@code *} or ends with {@code .*}
     */
    public static boolean isWildcard(String normalizedNode) {
        return normalizedNode.equals("*") || normalizedNode.endsWith(".*");
    }

    private static void validateWildcardPlacement(String normalizedNode) {
        int wildcardIndex = normalizedNode.indexOf('*');
        if (wildcardIndex < 0) {
            return;
        }

        if (normalizedNode.equals("*")) {
            return;
        }

        if (normalizedNode.endsWith(".*") && wildcardIndex == normalizedNode.length() - 1 && normalizedNode.length() > 2) {
            return;
        }

        throw new IllegalArgumentException("wildcard permission nodes must be '*' or terminal 'prefix.*'");
    }

    /**
     * Prevents instantiation of this constants-only utility class.
     */
    private PermissionNodes() {
    }
}
