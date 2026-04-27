package me.clutchy.clutchperms.common.config;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.Set;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;

import me.clutchy.clutchperms.common.storage.PermissionStorageException;
import me.clutchy.clutchperms.common.storage.StorageFiles;

/**
 * Loads and materializes shared ClutchPerms runtime configuration.
 */
public final class ClutchPermsConfigs {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static final Set<String> ROOT_KEYS = Set.of("version", "backups", "commands", "chat", "paper");

    private static final Set<String> BACKUP_KEYS = Set.of("retentionLimit");

    private static final Set<String> COMMAND_KEYS = Set.of("helpPageSize", "resultPageSize");

    private static final Set<String> CHAT_KEYS = Set.of("enabled");

    private static final Set<String> PAPER_KEYS = Set.of("replaceOpCommands");

    /**
     * Loads runtime configuration from disk, returning defaults when the file is missing.
     *
     * @param configFile config file path
     * @return loaded or default config
     */
    public static ClutchPermsConfig jsonFile(Path configFile) {
        Path normalizedConfigFile = normalize(configFile);
        if (Files.notExists(normalizedConfigFile)) {
            return ClutchPermsConfig.defaults();
        }

        try (Reader reader = Files.newBufferedReader(normalizedConfigFile, StandardCharsets.UTF_8)) {
            return parseRoot(JsonParser.parseReader(reader));
        } catch (NoSuchFileException exception) {
            return ClutchPermsConfig.defaults();
        } catch (IOException | JsonParseException | IllegalArgumentException exception) {
            throw new PermissionStorageException("Failed to load config from " + normalizedConfigFile, exception);
        }
    }

    /**
     * Creates a default config file when it does not already exist.
     *
     * @param configFile config file path
     */
    public static void materializeDefault(Path configFile) {
        Path normalizedConfigFile = normalize(configFile);
        if (Files.exists(normalizedConfigFile)) {
            return;
        }

        Path parentDirectory = normalizedConfigFile.getParent();
        try {
            if (parentDirectory != null) {
                Files.createDirectories(parentDirectory);
            }

            Path temporaryFile = parentDirectory == null
                    ? Files.createTempFile(normalizedConfigFile.getFileName().toString(), ".tmp")
                    : Files.createTempFile(parentDirectory, normalizedConfigFile.getFileName().toString(), ".tmp");
            try {
                try (Writer writer = Files.newBufferedWriter(temporaryFile, StandardCharsets.UTF_8)) {
                    GSON.toJson(toJson(ClutchPermsConfig.defaults()), writer);
                    writer.write(System.lineSeparator());
                }
                if (Files.exists(normalizedConfigFile)) {
                    Files.deleteIfExists(temporaryFile);
                    return;
                }
                StorageFiles.moveAtomically(temporaryFile, normalizedConfigFile);
                temporaryFile = null;
            } finally {
                if (temporaryFile != null) {
                    Files.deleteIfExists(temporaryFile);
                }
            }
        } catch (IOException exception) {
            throw new PermissionStorageException("Failed to create missing config file at " + normalizedConfigFile, exception);
        }
    }

    /**
     * Writes runtime configuration through a temporary file and atomic replacement.
     *
     * @param configFile config file path
     * @param config config to write
     */
    public static void write(Path configFile, ClutchPermsConfig config) {
        Path normalizedConfigFile = normalize(configFile);
        Path parentDirectory = normalizedConfigFile.getParent();
        try {
            if (parentDirectory != null) {
                Files.createDirectories(parentDirectory);
            }

            Path temporaryFile = parentDirectory == null
                    ? Files.createTempFile(normalizedConfigFile.getFileName().toString(), ".tmp")
                    : Files.createTempFile(parentDirectory, normalizedConfigFile.getFileName().toString(), ".tmp");
            try {
                try (Writer writer = Files.newBufferedWriter(temporaryFile, StandardCharsets.UTF_8)) {
                    GSON.toJson(toJson(config), writer);
                    writer.write(System.lineSeparator());
                }
                StorageFiles.moveAtomically(temporaryFile, normalizedConfigFile);
                temporaryFile = null;
            } finally {
                if (temporaryFile != null) {
                    Files.deleteIfExists(temporaryFile);
                }
            }
        } catch (IOException exception) {
            throw new PermissionStorageException("Failed to write config to " + normalizedConfigFile, exception);
        }
    }

    static JsonObject toJson(ClutchPermsConfig config) {
        JsonObject root = new JsonObject();
        root.addProperty("version", ClutchPermsConfig.CURRENT_VERSION);

        JsonObject backups = new JsonObject();
        backups.addProperty("retentionLimit", config.backups().retentionLimit());
        root.add("backups", backups);

        JsonObject commands = new JsonObject();
        commands.addProperty("helpPageSize", config.commands().helpPageSize());
        commands.addProperty("resultPageSize", config.commands().resultPageSize());
        root.add("commands", commands);

        JsonObject chat = new JsonObject();
        chat.addProperty("enabled", config.chat().enabled());
        root.add("chat", chat);

        JsonObject paper = new JsonObject();
        paper.addProperty("replaceOpCommands", config.paper().replaceOpCommands());
        root.add("paper", paper);

        return root;
    }

    private static ClutchPermsConfig parseRoot(JsonElement rootElement) {
        if (rootElement == null || !rootElement.isJsonObject()) {
            throw new IllegalArgumentException("config root must be an object");
        }

        JsonObject root = rootElement.getAsJsonObject();
        rejectUnknownKeys("config", root, ROOT_KEYS);
        int version = readInteger(root, "version", "version");
        if (version != ClutchPermsConfig.CURRENT_VERSION) {
            throw new IllegalArgumentException("unsupported config version " + version);
        }

        JsonObject backups = readObject(root, "backups", "backups");
        rejectUnknownKeys("backups", backups, BACKUP_KEYS);
        JsonObject commands = readObject(root, "commands", "commands");
        rejectUnknownKeys("commands", commands, COMMAND_KEYS);
        ClutchPermsChatConfig chatConfig = ClutchPermsChatConfig.defaults();
        JsonObject chat = readOptionalObject(root, "chat", "chat");
        if (chat != null) {
            rejectUnknownKeys("chat", chat, CHAT_KEYS);
            chatConfig = new ClutchPermsChatConfig(readBoolean(chat, "enabled", "chat.enabled"));
        }
        ClutchPermsPaperConfig paperConfig = ClutchPermsPaperConfig.defaults();
        JsonObject paper = readOptionalObject(root, "paper", "paper");
        if (paper != null) {
            rejectUnknownKeys("paper", paper, PAPER_KEYS);
            paperConfig = new ClutchPermsPaperConfig(readBoolean(paper, "replaceOpCommands", "paper.replaceOpCommands"));
        }

        return new ClutchPermsConfig(new ClutchPermsBackupConfig(readInteger(backups, "retentionLimit", "backups.retentionLimit")),
                new ClutchPermsCommandConfig(readInteger(commands, "helpPageSize", "commands.helpPageSize"), readInteger(commands, "resultPageSize", "commands.resultPageSize")),
                chatConfig, paperConfig);
    }

    private static JsonObject readObject(JsonObject object, String key, String path) {
        JsonElement element = object.get(key);
        if (element == null || !element.isJsonObject()) {
            throw new IllegalArgumentException(path + " must be an object");
        }
        return element.getAsJsonObject();
    }

    private static JsonObject readOptionalObject(JsonObject object, String key, String path) {
        JsonElement element = object.get(key);
        if (element == null) {
            return null;
        }
        if (!element.isJsonObject()) {
            throw new IllegalArgumentException(path + " must be an object");
        }
        return element.getAsJsonObject();
    }

    private static int readInteger(JsonObject object, String key, String path) {
        JsonElement element = object.get(key);
        if (element == null || !element.isJsonPrimitive()) {
            throw new IllegalArgumentException(path + " must be a number");
        }

        JsonPrimitive primitive = element.getAsJsonPrimitive();
        if (!primitive.isNumber()) {
            throw new IllegalArgumentException(path + " must be a number");
        }

        try {
            return Integer.parseInt(primitive.getAsString());
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(path + " must be an integer", exception);
        }
    }

    private static boolean readBoolean(JsonObject object, String key, String path) {
        JsonElement element = object.get(key);
        if (element == null || !element.isJsonPrimitive()) {
            throw new IllegalArgumentException(path + " must be a boolean");
        }

        JsonPrimitive primitive = element.getAsJsonPrimitive();
        if (!primitive.isBoolean()) {
            throw new IllegalArgumentException(path + " must be a boolean");
        }
        return primitive.getAsBoolean();
    }

    private static void rejectUnknownKeys(String path, JsonObject object, Set<String> allowedKeys) {
        for (String key : object.keySet()) {
            if (!allowedKeys.contains(key)) {
                throw new IllegalArgumentException("unknown " + path + " key: " + key);
            }
        }
    }

    private static Path normalize(Path path) {
        return path.toAbsolutePath().normalize();
    }

    private ClutchPermsConfigs() {
    }
}
