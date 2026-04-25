package me.clutchy.clutchperms.common.group;

import java.nio.file.Path;
import java.util.Objects;

import me.clutchy.clutchperms.common.storage.StorageWriteOptions;

/**
 * Creates shared group service implementations used by platform entrypoints.
 */
public final class GroupServices {

    /**
     * Creates a group service backed by a JSON file.
     *
     * @param groupsFile JSON file used to store groups and direct subject memberships
     * @return group service backed by the supplied JSON file
     * @throws NullPointerException if {@code groupsFile} is {@code null}
     * @throws PermissionStorageException if existing group data cannot be loaded
     */
    public static GroupService jsonFile(Path groupsFile) {
        return jsonFile(groupsFile, StorageWriteOptions.defaults());
    }

    /**
     * Creates a group service backed by a JSON file.
     *
     * @param groupsFile JSON file used to store groups and direct subject memberships
     * @param writeOptions storage write options used for future mutations
     * @return group service backed by the supplied JSON file
     * @throws NullPointerException if {@code groupsFile} is {@code null}
     * @throws PermissionStorageException if existing group data cannot be loaded
     */
    public static GroupService jsonFile(Path groupsFile, StorageWriteOptions writeOptions) {
        return new JsonFileGroupService(Objects.requireNonNull(groupsFile, "groupsFile"), StorageWriteOptions.defaultIfNull(writeOptions));
    }

    /**
     * Wraps a group service and notifies a listener after successful mutations.
     *
     * @param delegate group service that owns storage and reads
     * @param listener listener notified after successful mutations
     * @return observing group service decorator
     */
    public static GroupService observing(GroupService delegate, GroupChangeListener listener) {
        return new ObservingGroupService(delegate, listener);
    }

    private GroupServices() {
    }
}
