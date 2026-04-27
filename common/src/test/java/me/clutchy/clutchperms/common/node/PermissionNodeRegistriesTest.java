package me.clutchy.clutchperms.common.node;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import me.clutchy.clutchperms.common.permission.PermissionNodes;
import me.clutchy.clutchperms.common.storage.PermissionStorageException;
import me.clutchy.clutchperms.common.storage.SqliteStore;
import me.clutchy.clutchperms.common.storage.SqliteTestSupport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies known permission node registry implementations.
 */
class PermissionNodeRegistriesTest {

    @TempDir
    private Path temporaryDirectory;

    @Test
    void missingDatabaseCreatesEmptyKnownNodesSchema() {
        Path databaseFile = SqliteTestSupport.databaseFile(temporaryDirectory);

        try (SqliteStore store = SqliteTestSupport.open(databaseFile)) {
            PermissionNodeRegistry registry = PermissionNodeRegistries.sqlite(store);

            assertEquals(Set.of(), registry.getKnownNodes());
        }

        assertTrue(Files.exists(databaseFile));
    }

    @Test
    void knownNodesRoundTripThroughSqlite() {
        Path databaseFile = SqliteTestSupport.databaseFile(temporaryDirectory);
        try (SqliteStore store = SqliteTestSupport.open(databaseFile)) {
            MutablePermissionNodeRegistry registry = PermissionNodeRegistries.sqlite(store);
            registry.addNode(" Example.Fly ", "Allows example flight.");
            registry.addNode("Example.Build");
        }

        try (SqliteStore store = SqliteTestSupport.open(databaseFile)) {
            PermissionNodeRegistry reloadedRegistry = PermissionNodeRegistries.sqlite(store);
            assertEquals(Set.of(new KnownPermissionNode("example.build", "", PermissionNodeSource.MANUAL),
                    new KnownPermissionNode("example.fly", "Allows example flight.", PermissionNodeSource.MANUAL)), reloadedRegistry.getKnownNodes());
        }
    }

    @Test
    void targetedKnownNodeWritesPreserveRowsUnknownToLoadedDelegate() {
        Path databaseFile = SqliteTestSupport.databaseFile(temporaryDirectory);
        try (SqliteStore store = SqliteTestSupport.open(databaseFile)) {
            MutablePermissionNodeRegistry registry = PermissionNodeRegistries.sqlite(store);
            registry.addNode("known.node", "Known");
            store.write(connection -> connection.createStatement().executeUpdate("INSERT INTO known_nodes (node, description) VALUES ('external.node', 'External')"));

            registry.addNode("other.node", "Other");
            registry.removeNode("known.node");
        }

        try (SqliteStore store = SqliteTestSupport.open(databaseFile)) {
            PermissionNodeRegistry reloadedRegistry = PermissionNodeRegistries.sqlite(store);
            assertTrue(reloadedRegistry.getKnownNodes().contains(new KnownPermissionNode("external.node", "External", PermissionNodeSource.MANUAL)));
        }
    }

    @Test
    void failedWritesDoNotCommitNodeRegistryMutations() {
        Path databaseFile = SqliteTestSupport.databaseFile(temporaryDirectory);
        SqliteStore store = SqliteTestSupport.open(databaseFile);
        MutablePermissionNodeRegistry registry = PermissionNodeRegistries.sqlite(store);
        registry.addNode("existing.node", "Existing");
        registry.addNode("remove.node");
        store.close();

        assertThrows(PermissionStorageException.class, () -> registry.addNode("new.node", "New"));
        assertNodeRegistryStatePreserved(registry, databaseFile);

        assertThrows(PermissionStorageException.class, () -> registry.removeNode("remove.node"));
        assertNodeRegistryStatePreserved(registry, databaseFile);
    }

    @Test
    void invalidSqliteKnownNodeRowsFailLoad() {
        Path databaseFile = SqliteTestSupport.databaseFile(temporaryDirectory);
        try (SqliteStore store = SqliteTestSupport.open(databaseFile)) {
            store.write(connection -> {
                try (Statement statement = connection.createStatement()) {
                    statement.executeUpdate("INSERT INTO known_nodes (node, description) VALUES ('example.*', '')");
                }
            });
        }

        try (SqliteStore store = SqliteTestSupport.open(databaseFile)) {
            assertThrows(PermissionStorageException.class, () -> PermissionNodeRegistries.sqlite(store));
        }
    }

    @Test
    void duplicateNormalizedSqliteKnownNodeRowsFailLoad() {
        Path databaseFile = SqliteTestSupport.databaseFile(temporaryDirectory);
        try (SqliteStore store = SqliteTestSupport.open(databaseFile)) {
            store.write(connection -> {
                try (Statement statement = connection.createStatement()) {
                    statement.executeUpdate("INSERT INTO known_nodes (node, description) VALUES ('Example.Node', 'first')");
                    statement.executeUpdate("INSERT INTO known_nodes (node, description) VALUES (' example.node ', 'second')");
                }
            });
        }

        try (SqliteStore store = SqliteTestSupport.open(databaseFile)) {
            assertThrows(PermissionStorageException.class, () -> PermissionNodeRegistries.sqlite(store));
        }
    }

    @Test
    void inMemoryRegistryNormalizesAndRejectsWildcards() {
        MutablePermissionNodeRegistry registry = PermissionNodeRegistries.inMemory();

        registry.addNode(" Example.Node ", " Example description ");

        assertEquals(Set.of(new KnownPermissionNode("example.node", "Example description", PermissionNodeSource.MANUAL)), registry.getKnownNodes());
        assertThrows(IllegalArgumentException.class, () -> registry.addNode("example.*"));
        assertThrows(IllegalArgumentException.class, () -> registry.removeNode("missing.node"));
    }

    @Test
    void compositeRegistryMergesSourcesInPrecedenceOrder() {
        PermissionNodeRegistry builtIn = PermissionNodeRegistries.staticNodes(List.of(new KnownPermissionNode("clutchperms.admin", "Admin", PermissionNodeSource.BUILT_IN)));
        MutablePermissionNodeRegistry manual = PermissionNodeRegistries.inMemory(List.of(new KnownPermissionNode("example.node", "Manual", PermissionNodeSource.MANUAL),
                new KnownPermissionNode("shared.node", "Manual wins", PermissionNodeSource.MANUAL)));
        PermissionNodeRegistry platform = PermissionNodeRegistries.staticNodes(PermissionNodeSource.PLATFORM, List.of("other.node", "shared.node"));

        PermissionNodeRegistry composite = PermissionNodeRegistries.composite(builtIn, manual, platform);

        assertEquals(Set.of(new KnownPermissionNode("clutchperms.admin", "Admin", PermissionNodeSource.BUILT_IN),
                new KnownPermissionNode("example.node", "Manual", PermissionNodeSource.MANUAL), new KnownPermissionNode("other.node", "", PermissionNodeSource.PLATFORM),
                new KnownPermissionNode("shared.node", "Manual wins", PermissionNodeSource.MANUAL)), composite.getKnownNodes());
    }

    @Test
    void builtInRegistryExposesExactCommandPermissionNodes() {
        Set<String> builtInNodes = PermissionNodeRegistries.builtIn().getKnownNodes().stream().map(KnownPermissionNode::node).collect(java.util.stream.Collectors.toSet());

        assertEquals(Set.copyOf(PermissionNodes.commandNodes()), builtInNodes);
        assertFalse(builtInNodes.contains(PermissionNodes.ADMIN));
        assertFalse(builtInNodes.contains(PermissionNodes.ADMIN_ALL));
    }

    @Test
    void observingRegistryReportsSuccessfulMutationsOnly() {
        List<String> events = new ArrayList<>();
        MutablePermissionNodeRegistry registry = PermissionNodeRegistries.observing(PermissionNodeRegistries.inMemory(), () -> events.add("changed"));

        registry.addNode("example.node");
        registry.removeNode("example.node");
        assertThrows(IllegalArgumentException.class, () -> registry.addNode("example.*"));

        assertEquals(Set.of(), registry.getKnownNodes());
        assertEquals(List.of("changed", "changed"), events);
    }

    private static void assertNodeRegistryStatePreserved(PermissionNodeRegistry registry, Path databaseFile) {
        assertNodeRegistryRuntimeState(registry);
        try (SqliteStore store = SqliteTestSupport.open(databaseFile)) {
            assertNodeRegistryRuntimeState(PermissionNodeRegistries.sqlite(store));
        }
    }

    private static void assertNodeRegistryRuntimeState(PermissionNodeRegistry registry) {
        assertEquals(
                Set.of(new KnownPermissionNode("existing.node", "Existing", PermissionNodeSource.MANUAL), new KnownPermissionNode("remove.node", "", PermissionNodeSource.MANUAL)),
                registry.getKnownNodes());
    }
}
