package me.clutchy.clutchperms.common.storage;

import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;

/**
 * Shared atomic file-write helpers for ClutchPerms JSON storage.
 */
public final class StorageFiles {

    /**
     * Writes a live storage file through a temporary file, backing up the previous live file before replacement.
     *
     * @param targetFile live storage file
     * @param kind storage file kind
     * @param writerAction action that writes the new file content
     * @throws IOException when the file cannot be written or replaced
     */
    public static void writeAtomicallyWithBackup(Path targetFile, StorageFileKind kind, StorageWriter writerAction) throws IOException {
        Path normalizedTarget = targetFile.toAbsolutePath().normalize();
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

            StorageBackupService.forFiles(backupRootFor(normalizedTarget), Map.of(kind, normalizedTarget)).backupExistingFile(kind);
            moveAtomically(temporaryFile, normalizedTarget);
            temporaryFile = null;
        } finally {
            if (temporaryFile != null) {
                Files.deleteIfExists(temporaryFile);
            }
        }
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
