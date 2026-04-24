package me.clutchy.clutchperms.common;

import java.nio.file.Path;
import java.util.Objects;

/**
 * Creates shared permission service implementations used by platform entrypoints.
 */
public final class PermissionServices {

    /**
     * Creates a permission service backed by a JSON file.
     *
     * <p>
     * The service loads the file during construction, treats a missing file as empty state, and saves after every mutation.
     *
     * @param permissionsFile JSON file used to store direct permission assignments
     * @return permission service backed by the supplied JSON file
     * @throws NullPointerException if {@code permissionsFile} is {@code null}
     * @throws PermissionStorageException if existing permission data cannot be loaded
     */
    public static PermissionService jsonFile(Path permissionsFile) {
        return new JsonFilePermissionService(Objects.requireNonNull(permissionsFile, "permissionsFile"));
    }

    /**
     * Prevents instantiation of this factory-only utility class.
     */
    private PermissionServices() {
    }
}
