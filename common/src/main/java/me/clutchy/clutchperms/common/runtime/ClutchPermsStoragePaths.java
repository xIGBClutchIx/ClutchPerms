package me.clutchy.clutchperms.common.runtime;

import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;

import me.clutchy.clutchperms.common.storage.StorageFileKind;

/**
 * Platform-neutral ClutchPerms JSON storage paths.
 */
public record ClutchPermsStoragePaths(Path permissionsFile, Path subjectsFile, Path groupsFile, Path nodesFile, Path backupRoot) {

    private static final String PERMISSIONS_FILE_NAME = "permissions.json";

    private static final String SUBJECTS_FILE_NAME = "subjects.json";

    private static final String GROUPS_FILE_NAME = "groups.json";

    private static final String NODES_FILE_NAME = "nodes.json";

    /**
     * Creates storage paths under one platform storage directory.
     *
     * @param storageDirectory directory that contains live JSON files and the backups directory
     * @return normalized storage paths
     */
    public static ClutchPermsStoragePaths inDirectory(Path storageDirectory) {
        Path normalizedStorageDirectory = Objects.requireNonNull(storageDirectory, "storageDirectory").toAbsolutePath().normalize();
        return new ClutchPermsStoragePaths(normalizedStorageDirectory.resolve(PERMISSIONS_FILE_NAME), normalizedStorageDirectory.resolve(SUBJECTS_FILE_NAME),
                normalizedStorageDirectory.resolve(GROUPS_FILE_NAME), normalizedStorageDirectory.resolve(NODES_FILE_NAME), normalizedStorageDirectory.resolve("backups"));
    }

    /**
     * Normalizes every storage path.
     */
    public ClutchPermsStoragePaths {
        permissionsFile = normalize(permissionsFile, "permissionsFile");
        subjectsFile = normalize(subjectsFile, "subjectsFile");
        groupsFile = normalize(groupsFile, "groupsFile");
        nodesFile = normalize(nodesFile, "nodesFile");
        backupRoot = normalize(backupRoot, "backupRoot");
    }

    /**
     * Returns live JSON storage files by kind.
     *
     * @return live storage files
     */
    public Map<StorageFileKind, Path> storageFiles() {
        return Map.of(StorageFileKind.PERMISSIONS, permissionsFile, StorageFileKind.SUBJECTS, subjectsFile, StorageFileKind.GROUPS, groupsFile, StorageFileKind.NODES, nodesFile);
    }

    /**
     * Returns the directory containing the live JSON files.
     *
     * @return storage root
     */
    public Path storageRoot() {
        return permissionsFile.getParent();
    }

    private static Path normalize(Path path, String name) {
        return Objects.requireNonNull(path, name).toAbsolutePath().normalize();
    }
}
