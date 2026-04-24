package me.clutchy.clutchperms.common.node;

/**
 * Mutable registry of manually known permission nodes.
 */
public interface MutablePermissionNodeRegistry extends PermissionNodeRegistry {

    /**
     * Adds or updates one manually known exact permission node with no description.
     *
     * @param node exact permission node
     */
    default void addNode(String node) {
        addNode(node, "");
    }

    /**
     * Adds or updates one manually known exact permission node.
     *
     * @param node exact permission node
     * @param description optional description
     */
    void addNode(String node, String description);

    /**
     * Removes one manually known exact permission node.
     *
     * @param node exact permission node
     */
    void removeNode(String node);
}
