package me.clutchy.clutchperms.common.node;

import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Read-only registry of exact permission nodes known to ClutchPerms.
 */
public interface PermissionNodeRegistry {

    /**
     * Lists known exact permission nodes.
     *
     * @return immutable deterministic snapshot
     */
    Set<KnownPermissionNode> getKnownNodes();

    /**
     * Looks up a known exact permission node.
     *
     * @param node node to inspect
     * @return known node descriptor, or empty when unknown
     */
    default Optional<KnownPermissionNode> getKnownNode(String node) {
        String normalizedNode = KnownPermissionNode.normalizeKnownNode(node);
        return getKnownNodes().stream().filter(knownNode -> knownNode.node().equals(normalizedNode)).findFirst();
    }

    /**
     * Lists known exact node names.
     *
     * @return immutable deterministic node-name snapshot
     */
    default Set<String> getKnownNodeNames() {
        return getKnownNodes().stream().map(KnownPermissionNode::node).collect(Collectors.toUnmodifiableSet());
    }
}
