package me.clutchy.clutchperms.common.node;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import me.clutchy.clutchperms.common.permission.PermissionNodes;
import me.clutchy.clutchperms.common.storage.PermissionStorageException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Verifies known permission node registry implementations.
 */
class PermissionNodeRegistriesTest {

    /**
     * Temporary directory used for JSON persistence test files.
     */
    @TempDir
    private Path temporaryDirectory;

    /**
     * Confirms a missing JSON file starts with empty state.
     */
    @Test
    void missingFileLoadsEmptyKnownNodes() {
        Path nodesFile = temporaryDirectory.resolve("nodes.json");

        PermissionNodeRegistry registry = PermissionNodeRegistries.jsonFile(nodesFile);

        assertEquals(Set.of(), registry.getKnownNodes());
        assertFalse(Files.exists(nodesFile));
    }

    /**
     * Confirms manual nodes with and without descriptions survive a reload.
     */
    @Test
    void knownNodesRoundTripThroughJson() {
        Path nodesFile = temporaryDirectory.resolve("nodes.json");
        MutablePermissionNodeRegistry registry = PermissionNodeRegistries.jsonFile(nodesFile);

        registry.addNode(" Example.Fly ", "Allows example flight.");
        registry.addNode("Example.Build");

        PermissionNodeRegistry reloadedRegistry = PermissionNodeRegistries.jsonFile(nodesFile);

        assertEquals(Set.of(new KnownPermissionNode("example.build", "", PermissionNodeSource.MANUAL),
                new KnownPermissionNode("example.fly", "Allows example flight.", PermissionNodeSource.MANUAL)), reloadedRegistry.getKnownNodes());
    }

    /**
     * Confirms saves create parent directories and use deterministic node ordering.
     *
     * @throws IOException if the persisted file cannot be read
     */
    @Test
    void saveCreatesParentDirectoriesAndWritesDeterministicJson() throws IOException {
        Path nodesFile = temporaryDirectory.resolve("nested").resolve("data").resolve("nodes.json");
        MutablePermissionNodeRegistry registry = PermissionNodeRegistries.jsonFile(nodesFile);

        registry.addNode("z.node", "Last");
        registry.addNode("A.Node");
        registry.addNode("b.node", "Middle");

        String persistedJson = Files.readString(nodesFile).replace("\r\n", "\n");

        assertEquals("""
                {
                  "version": 1,
                  "nodes": {
                    "a.node": {},
                    "b.node": {
                      "description": "Middle"
                    },
                    "z.node": {
                      "description": "Last"
                    }
                  }
                }
                """, persistedJson);
    }

    /**
     * Confirms malformed or invalid node registry files fail during construction.
     *
     * @throws IOException if a test file cannot be written
     */
    @Test
    void invalidJsonFilesFailLoad() throws IOException {
        assertFailsToLoad("{not-json");
        assertFailsToLoad("""
                {
                  "version": 2,
                  "nodes": {}
                }
                """);
        assertFailsToLoad("""
                {
                  "version": 1,
                  "nodes": {
                    "   ": {}
                  }
                }
                """);
        assertFailsToLoad("""
                {
                  "version": 1,
                  "nodes": {
                    "example node": {}
                  }
                }
                """);
        assertFailsToLoad("""
                {
                  "version": 1,
                  "nodes": {
                    "example.*": {}
                  }
                }
                """);
        assertFailsToLoad("""
                {
                  "version": 1,
                  "nodes": {
                    "example.node": "description"
                  }
                }
                """);
        assertFailsToLoad("""
                {
                  "version": 1,
                  "nodes": {
                    "example.node": {
                      "description": 1
                    }
                  }
                }
                """);
    }

    /**
     * Confirms in-memory mutations normalize nodes and reject wildcard known nodes.
     */
    @Test
    void inMemoryRegistryNormalizesAndRejectsWildcards() {
        MutablePermissionNodeRegistry registry = PermissionNodeRegistries.inMemory();

        registry.addNode(" Example.Node ", " Example description ");

        assertEquals(Set.of(new KnownPermissionNode("example.node", "Example description", PermissionNodeSource.MANUAL)), registry.getKnownNodes());
        assertThrows(IllegalArgumentException.class, () -> registry.addNode("example.*"));
        assertThrows(IllegalArgumentException.class, () -> registry.removeNode("missing.node"));
    }

    /**
     * Confirms composite registries merge sources deterministically and keep the first descriptor for duplicates.
     */
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

    /**
     * Confirms the built-in registry exposes exact command permission nodes, not wildcard assignments.
     */
    @Test
    void builtInRegistryExposesExactCommandPermissionNodes() {
        Set<String> builtInNodes = PermissionNodeRegistries.builtIn().getKnownNodes().stream().map(KnownPermissionNode::node).collect(java.util.stream.Collectors.toSet());

        assertEquals(Set.copyOf(PermissionNodes.commandNodes()), builtInNodes);
        assertFalse(builtInNodes.contains(PermissionNodes.ADMIN));
        assertFalse(builtInNodes.contains(PermissionNodes.ADMIN_ALL));
    }

    /**
     * Confirms observing registries delegate reads and report successful mutations only.
     */
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

    private void assertFailsToLoad(String json) throws IOException {
        Path nodesFile = temporaryDirectory.resolve("invalid-nodes.json");
        Files.writeString(nodesFile, json);

        assertThrows(PermissionStorageException.class, () -> PermissionNodeRegistries.jsonFile(nodesFile));
    }
}
