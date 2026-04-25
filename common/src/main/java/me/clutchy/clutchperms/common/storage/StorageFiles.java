package me.clutchy.clutchperms.common.storage;

import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.Objects;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;

/**
 * Shared atomic file-write helpers for ClutchPerms JSON storage.
 */
public final class StorageFiles {

    private static final int CURRENT_VERSION = 1;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /**
     * Writes a live storage file through a temporary file, backing up the previous live file before replacement.
     *
     * @param targetFile live storage file
     * @param kind storage file kind
     * @param writerAction action that writes the new file content
     * @throws IOException when the file cannot be written or replaced
     */
    public static void writeAtomicallyWithBackup(Path targetFile, StorageFileKind kind, StorageWriter writerAction) throws IOException {
        writeAtomicallyWithBackup(targetFile, kind, StorageWriteOptions.defaults(), writerAction);
    }

    /**
     * Writes a live storage file through a temporary file, backing up the previous live file before replacement.
     *
     * @param targetFile live storage file
     * @param kind storage file kind
     * @param writeOptions storage write options
     * @param writerAction action that writes the new file content
     * @throws IOException when the file cannot be written or replaced
     */
    public static void writeAtomicallyWithBackup(Path targetFile, StorageFileKind kind, StorageWriteOptions writeOptions, StorageWriter writerAction) throws IOException {
        Path normalizedTarget = targetFile.toAbsolutePath().normalize();
        StorageWriteOptions requiredWriteOptions = StorageWriteOptions.defaultIfNull(writeOptions);
        Path parentDirectory = normalizedTarget.getParent();
        if (parentDirectory != null) {
            Files.createDirectories(parentDirectory);
        }

        Path temporaryFile = parentDirectory == null
                ? Files.createTempFile(normalizedTarget.getFileName().toString(), ".tmp")
                : Files.createTempFile(parentDirectory, normalizedTarget.getFileName().toString(), ".tmp");
        try {
            try (Writer writer = Files.newBufferedWriter(temporaryFile, StandardCharsets.UTF_8)) {
                writerAction.write(writer);
            }

            StorageBackupService.forFiles(backupRootFor(normalizedTarget), Map.of(kind, normalizedTarget), requiredWriteOptions.backupRetentionLimit()).backupExistingFile(kind);
            moveAtomically(temporaryFile, normalizedTarget);
            temporaryFile = null;
        } finally {
            if (temporaryFile != null) {
                Files.deleteIfExists(temporaryFile);
            }
        }
    }

    /**
     * Creates missing storage JSON files with empty versioned content, leaving existing files untouched.
     *
     * @param storageFiles live storage files by kind
     * @throws PermissionStorageException when a missing file cannot be created
     */
    public static void materializeMissingJsonFiles(Map<StorageFileKind, Path> storageFiles) {
        Objects.requireNonNull(storageFiles, "storageFiles");
        storageFiles.forEach(StorageFiles::materializeMissingJsonFile);
    }

    /**
     * Moves a file into place, using an atomic move when the filesystem supports it.
     *
     * @param source source path
     * @param target target path
     * @throws IOException when the move fails
     */
    public static void moveAtomically(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static void materializeMissingJsonFile(StorageFileKind kind, Path targetFile) {
        StorageFileKind requiredKind = Objects.requireNonNull(kind, "kind");
        Path normalizedTarget = Objects.requireNonNull(targetFile, "targetFile").toAbsolutePath().normalize();
        if (Files.exists(normalizedTarget)) {
            return;
        }

        try {
            writeAtomicallyWithBackup(normalizedTarget, requiredKind, writer -> {
                GSON.toJson(emptyJson(requiredKind), writer);
            });
        } catch (NoSuchFileException exception) {
            // Another process may have removed the parent between checks; report the same way as other storage failures.
            throw new PermissionStorageException("Failed to create missing " + requiredKind.token() + " storage file at " + normalizedTarget, exception);
        } catch (IOException exception) {
            throw new PermissionStorageException("Failed to create missing " + requiredKind.token() + " storage file at " + normalizedTarget, exception);
        }
    }

    private static JsonObject emptyJson(StorageFileKind kind) {
        JsonObject root = new JsonObject();
        root.addProperty("version", CURRENT_VERSION);
        switch (kind) {
            case PERMISSIONS -> root.add("subjects", new JsonObject());
            case SUBJECTS -> {
                root.add("subjects", new JsonObject());
                root.add("display", new JsonObject());
            }
            case GROUPS -> {
                JsonObject groups = new JsonObject();
                JsonObject defaultGroup = new JsonObject();
                defaultGroup.add("permissions", new JsonObject());
                groups.add("default", defaultGroup);
                root.add("groups", groups);
                root.add("memberships", new JsonObject());
            }
            case NODES -> root.add("nodes", new JsonObject());
        }
        return root;
    }

    static Path backupRootFor(Path liveFile) {
        Path parentDirectory = liveFile.toAbsolutePath().normalize().getParent();
        if (parentDirectory == null) {
            return Path.of("backups").toAbsolutePath().normalize();
        }
        return parentDirectory.resolve("backups").toAbsolutePath().normalize();
    }

    /**
     * Writes storage content to an open writer.
     */
    @FunctionalInterface
    public interface StorageWriter {

        /**
         * Writes storage content.
         *
         * @param writer open writer
         * @throws IOException when writing fails
         */
        void write(Writer writer) throws IOException;
    }

    private StorageFiles() {
    }
}
