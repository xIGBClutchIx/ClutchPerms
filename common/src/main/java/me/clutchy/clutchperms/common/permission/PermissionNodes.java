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
     * Root of the ClutchPerms admin permission namespace. This exact node is not a command grant.
     */
    public static final String ADMIN = "clutchperms.admin";

    /**
     * Wildcard assignment that grants every ClutchPerms admin command.
     */
    public static final String ADMIN_ALL = ADMIN + ".*";

    public static final String ADMIN_HELP = ADMIN + ".help";

    public static final String ADMIN_STATUS = ADMIN + ".status";

    public static final String ADMIN_RELOAD = ADMIN + ".reload";

    public static final String ADMIN_VALIDATE = ADMIN + ".validate";

    public static final String ADMIN_CONFIG_ALL = ADMIN + ".config.*";

    public static final String ADMIN_CONFIG_VIEW = ADMIN + ".config.view";

    public static final String ADMIN_CONFIG_SET = ADMIN + ".config.set";

    public static final String ADMIN_CONFIG_RESET = ADMIN + ".config.reset";

    public static final String ADMIN_BACKUP_LIST = ADMIN + ".backup.list";

    public static final String ADMIN_BACKUP_RESTORE = ADMIN + ".backup.restore";

    public static final String ADMIN_USER_LIST = ADMIN + ".user.list";

    public static final String ADMIN_USER_GET = ADMIN + ".user.get";

    public static final String ADMIN_USER_SET = ADMIN + ".user.set";

    public static final String ADMIN_USER_CLEAR = ADMIN + ".user.clear";

    public static final String ADMIN_USER_CHECK = ADMIN + ".user.check";

    public static final String ADMIN_USER_EXPLAIN = ADMIN + ".user.explain";

    public static final String ADMIN_USER_GROUPS = ADMIN + ".user.groups";

    public static final String ADMIN_USER_GROUP_ADD = ADMIN + ".user.group.add";

    public static final String ADMIN_USER_GROUP_REMOVE = ADMIN + ".user.group.remove";

    public static final String ADMIN_GROUP_LIST = ADMIN + ".group.list";

    public static final String ADMIN_GROUP_CREATE = ADMIN + ".group.create";

    public static final String ADMIN_GROUP_DELETE = ADMIN + ".group.delete";

    public static final String ADMIN_GROUP_VIEW = ADMIN + ".group.view";

    public static final String ADMIN_GROUP_GET = ADMIN + ".group.get";

    public static final String ADMIN_GROUP_SET = ADMIN + ".group.set";

    public static final String ADMIN_GROUP_CLEAR = ADMIN + ".group.clear";

    public static final String ADMIN_GROUP_PARENTS = ADMIN + ".group.parents";

    public static final String ADMIN_GROUP_PARENT_ADD = ADMIN + ".group.parent.add";

    public static final String ADMIN_GROUP_PARENT_REMOVE = ADMIN + ".group.parent.remove";

    public static final String ADMIN_USERS_LIST = ADMIN + ".users.list";

    public static final String ADMIN_USERS_SEARCH = ADMIN + ".users.search";

    public static final String ADMIN_NODES_LIST = ADMIN + ".nodes.list";

    public static final String ADMIN_NODES_SEARCH = ADMIN + ".nodes.search";

    public static final String ADMIN_NODES_ADD = ADMIN + ".nodes.add";

    public static final String ADMIN_NODES_REMOVE = ADMIN + ".nodes.remove";

    private static final List<String> COMMAND_NODES = List.of(ADMIN_HELP, ADMIN_STATUS, ADMIN_RELOAD, ADMIN_VALIDATE, ADMIN_CONFIG_VIEW, ADMIN_CONFIG_SET, ADMIN_CONFIG_RESET,
            ADMIN_BACKUP_LIST, ADMIN_BACKUP_RESTORE, ADMIN_USER_LIST, ADMIN_USER_GET, ADMIN_USER_SET, ADMIN_USER_CLEAR, ADMIN_USER_CHECK, ADMIN_USER_EXPLAIN, ADMIN_USER_GROUPS,
            ADMIN_USER_GROUP_ADD, ADMIN_USER_GROUP_REMOVE, ADMIN_GROUP_LIST, ADMIN_GROUP_CREATE, ADMIN_GROUP_DELETE, ADMIN_GROUP_VIEW, ADMIN_GROUP_GET, ADMIN_GROUP_SET,
            ADMIN_GROUP_CLEAR, ADMIN_GROUP_PARENTS, ADMIN_GROUP_PARENT_ADD, ADMIN_GROUP_PARENT_REMOVE, ADMIN_USERS_LIST, ADMIN_USERS_SEARCH, ADMIN_NODES_LIST, ADMIN_NODES_SEARCH,
            ADMIN_NODES_ADD, ADMIN_NODES_REMOVE);

    private static final List<String> COMMAND_WILDCARD_ASSIGNMENTS = List.of(ADMIN_ALL, ADMIN + ".backup.*", ADMIN + ".user.*", ADMIN + ".user.group.*", ADMIN + ".group.*",
            ADMIN + ".group.parent.*", ADMIN + ".users.*", ADMIN + ".nodes.*", ADMIN_CONFIG_ALL);

    /**
     * Lists exact permission nodes required by ClutchPerms admin commands.
     *
     * @return immutable exact command node list
     */
    public static List<String> commandNodes() {
        return COMMAND_NODES;
    }

    /**
     * Lists useful wildcard assignment nodes for ClutchPerms admin command categories.
     *
     * @return immutable wildcard assignment node list
     */
    public static List<String> commandWildcardAssignments() {
        return COMMAND_WILDCARD_ASSIGNMENTS;
    }

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
