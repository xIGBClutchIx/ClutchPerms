package me.clutchy.clutchperms.common.node;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;

import me.clutchy.clutchperms.common.storage.PermissionStorageException;

/**
 * JSON-backed manual known-node registry.
 */
final class JsonFilePermissionNodeRegistry implements MutablePermissionNodeRegistry {

    private static final int CURRENT_VERSION = 1;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final Path nodesFile;

    private final InMemoryPermissionNodeRegistry delegate;

    JsonFilePermissionNodeRegistry(Path nodesFile) {
        this.nodesFile = nodesFile.toAbsolutePath().normalize();
        this.delegate = new InMemoryPermissionNodeRegistry(loadNodes());
    }

    @Override
    public synchronized Set<KnownPermissionNode> getKnownNodes() {
        return delegate.getKnownNodes();
    }

    @Override
    public synchronized void addNode(String node, String description) {
        delegate.addNode(node, description);
        saveNodes();
    }

    @Override
    public synchronized void removeNode(String node) {
        delegate.removeNode(node);
        saveNodes();
    }

    private Set<KnownPermissionNode> loadNodes() {
        if (Files.notExists(nodesFile)) {
            return Set.of();
        }

        try (Reader reader = Files.newBufferedReader(nodesFile, StandardCharsets.UTF_8)) {
            JsonElement rootElement = JsonParser.parseReader(reader);
            return parseRoot(rootElement);
        } catch (NoSuchFileException exception) {
            return Set.of();
        } catch (IOException | JsonParseException | IllegalArgumentException exception) {
            throw new PermissionStorageException("Failed to load known permission nodes from " + nodesFile, exception);
        }
    }

    private void saveNodes() {
        try {
            Path parentDirectory = nodesFile.getParent();
            if (parentDirectory != null) {
                Files.createDirectories(parentDirectory);
            }

            Path temporaryFile = Files.createTempFile(parentDirectory, nodesFile.getFileName().toString(), ".tmp");
            try {
                try (Writer writer = Files.newBufferedWriter(temporaryFile, StandardCharsets.UTF_8)) {
                    GSON.toJson(toJson(delegate.getKnownNodes()), writer);
                    writer.write(System.lineSeparator());
                }

                moveIntoPlace(temporaryFile);
                temporaryFile = null;
            } finally {
                if (temporaryFile != null) {
                    Files.deleteIfExists(temporaryFile);
                }
            }
        } catch (IOException exception) {
            throw new PermissionStorageException("Failed to save known permission nodes to " + nodesFile, exception);
        }
    }

    private void moveIntoPlace(Path temporaryFile) throws IOException {
        try {
            Files.move(temporaryFile, nodesFile, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(temporaryFile, nodesFile, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static Set<KnownPermissionNode> parseRoot(JsonElement rootElement) {
        if (rootElement == null || !rootElement.isJsonObject()) {
            throw new IllegalArgumentException("known permission nodes root must be an object");
        }

        JsonObject root = rootElement.getAsJsonObject();
        int version = readVersion(root);
        if (version != CURRENT_VERSION) {
            throw new IllegalArgumentException("unsupported known permission nodes version " + version);
        }

        JsonElement nodesElement = root.get("nodes");
        if (nodesElement == null || !nodesElement.isJsonObject()) {
            throw new IllegalArgumentException("nodes must be an object");
        }

        Set<KnownPermissionNode> nodes = new LinkedHashSet<>();
        Set<String> normalizedNodes = new LinkedHashSet<>();
        for (Map.Entry<String, JsonElement> nodeEntry : nodesElement.getAsJsonObject().entrySet()) {
            String normalizedNode = KnownPermissionNode.normalizeKnownNode(nodeEntry.getKey());
            if (!normalizedNodes.add(normalizedNode)) {
                throw new IllegalArgumentException("duplicate normalized known permission node " + normalizedNode);
            }
            JsonElement nodeElement = nodeEntry.getValue();
            if (nodeElement == null || !nodeElement.isJsonObject()) {
                throw new IllegalArgumentException("known permission node " + nodeEntry.getKey() + " must be an object");
            }
            nodes.add(new KnownPermissionNode(normalizedNode, readDescription(normalizedNode, nodeElement.getAsJsonObject()), PermissionNodeSource.MANUAL));
        }
        return nodes;
    }

    private static int readVersion(JsonObject root) {
        JsonElement versionElement = root.get("version");
        if (versionElement == null || !versionElement.isJsonPrimitive()) {
            throw new IllegalArgumentException("version must be a number");
        }

        JsonPrimitive versionPrimitive = versionElement.getAsJsonPrimitive();
        if (!versionPrimitive.isNumber()) {
            throw new IllegalArgumentException("version must be a number");
        }

        try {
            return Integer.parseInt(versionPrimitive.getAsString());
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("version must be an integer", exception);
        }
    }

    private static String readDescription(String node, JsonObject nodeElement) {
        JsonElement descriptionElement = nodeElement.get("description");
        if (descriptionElement == null) {
            return "";
        }
        if (!descriptionElement.isJsonPrimitive()) {
            throw new IllegalArgumentException("description for known permission node " + node + " must be a string");
        }
        JsonPrimitive descriptionPrimitive = descriptionElement.getAsJsonPrimitive();
        if (!descriptionPrimitive.isString()) {
            throw new IllegalArgumentException("description for known permission node " + node + " must be a string");
        }
        return descriptionPrimitive.getAsString();
    }

    private static JsonObject toJson(Set<KnownPermissionNode> snapshot) {
        JsonObject root = new JsonObject();
        root.addProperty("version", CURRENT_VERSION);

        JsonObject nodes = new JsonObject();
        Map<String, KnownPermissionNode> sortedNodes = new TreeMap<>();
        snapshot.forEach(node -> sortedNodes.put(node.node(), node));
        sortedNodes.forEach((nodeName, node) -> {
            JsonObject nodeObject = new JsonObject();
            if (!node.description().isEmpty()) {
                nodeObject.addProperty("description", node.description());
            }
            nodes.add(nodeName, nodeObject);
        });
        root.add("nodes", nodes);
        return root;
    }
}
