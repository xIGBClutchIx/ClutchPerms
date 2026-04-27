package me.clutchy.clutchperms.common.node;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.LinkedHashSet;
import java.util.Set;

import me.clutchy.clutchperms.common.storage.PermissionStorageException;
import me.clutchy.clutchperms.common.storage.SqliteStore;

/**
 * SQLite-backed manual known-node registry.
 */
final class SqlitePermissionNodeRegistry implements MutablePermissionNodeRegistry {

    private final SqliteStore store;

    private InMemoryPermissionNodeRegistry delegate;

    SqlitePermissionNodeRegistry(SqliteStore store) {
        this.store = store;
        this.delegate = new InMemoryPermissionNodeRegistry(loadNodes());
    }

    @Override
    public synchronized Set<KnownPermissionNode> getKnownNodes() {
        return delegate.getKnownNodes();
    }

    @Override
    public synchronized void addNode(String node, String description) {
        InMemoryPermissionNodeRegistry candidate = copyDelegate();
        candidate.addNode(node, description);
        commit(candidate);
    }

    @Override
    public synchronized void removeNode(String node) {
        InMemoryPermissionNodeRegistry candidate = copyDelegate();
        candidate.removeNode(node);
        commit(candidate);
    }

    private InMemoryPermissionNodeRegistry copyDelegate() {
        return new InMemoryPermissionNodeRegistry(delegate.getKnownNodes());
    }

    private void commit(InMemoryPermissionNodeRegistry candidate) {
        saveNodes(candidate.getKnownNodes());
        delegate = candidate;
    }

    private Set<KnownPermissionNode> loadNodes() {
        return store.read(connection -> {
            Set<KnownPermissionNode> nodes = new LinkedHashSet<>();
            Set<String> seenNodes = new LinkedHashSet<>();
            try (PreparedStatement statement = connection.prepareStatement("SELECT node, description FROM known_nodes ORDER BY node"); ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    KnownPermissionNode knownNode = new KnownPermissionNode(rows.getString("node"), rows.getString("description"), PermissionNodeSource.MANUAL);
                    if (!seenNodes.add(knownNode.node())) {
                        throw new PermissionStorageException("Duplicate normalized known node in SQLite registry: " + knownNode.node());
                    }
                    nodes.add(knownNode);
                }
            }
            return nodes;
        });
    }

    private void saveNodes(Set<KnownPermissionNode> snapshot) {
        store.write(connection -> {
            try (PreparedStatement deleteStatement = connection.prepareStatement("DELETE FROM known_nodes")) {
                deleteStatement.executeUpdate();
            }
            try (PreparedStatement insertStatement = connection.prepareStatement("INSERT INTO known_nodes (node, description) VALUES (?, ?)")) {
                for (KnownPermissionNode node : snapshot) {
                    if (node.source() != PermissionNodeSource.MANUAL) {
                        continue;
                    }
                    insertStatement.setString(1, node.node());
                    insertStatement.setString(2, node.description());
                    insertStatement.addBatch();
                }
                insertStatement.executeBatch();
            }
        });
    }
}
