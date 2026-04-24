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
     * Wraps a permission service and notifies a listener after successful mutations.
     *
     * @param delegate permission service that owns storage and reads
     * @param listener listener notified after successful subject mutations
     * @return observing permission service decorator
     * @throws NullPointerException if {@code delegate} or {@code listener} is {@code null}
     */
    public static PermissionService observing(PermissionService delegate, PermissionChangeListener listener) {
        return new ObservingPermissionService(delegate, listener);
    }

    /**
     * Prevents instantiation of this factory-only utility class.
     */
    private PermissionServices() {
    }
}
