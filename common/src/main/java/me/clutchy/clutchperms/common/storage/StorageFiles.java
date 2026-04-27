package me.clutchy.clutchperms.common.storage;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Shared atomic file helpers for ClutchPerms storage.
 */
public final class StorageFiles {

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

    private StorageFiles() {
    }
}
