package me.clutchy.clutchperms.common.storage;

import java.nio.file.Path;
import java.util.Objects;

/**
 * Describes one persisted storage backup file.
 *
 * @param kind storage file kind
 * @param fileName backup filename used by restore commands
 * @param path absolute backup path
 */
public record StorageBackup(StorageFileKind kind, String fileName, Path path) {

    /**
     * Creates an immutable backup descriptor.
     */
    public StorageBackup {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(fileName, "fileName");
        path = Objects.requireNonNull(path, "path").toAbsolutePath().normalize();
    }
}
