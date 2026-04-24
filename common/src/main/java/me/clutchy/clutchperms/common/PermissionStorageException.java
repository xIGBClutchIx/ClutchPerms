package me.clutchy.clutchperms.common;

/**
 * Signals that permission data could not be loaded from or saved to persistent storage.
 */
public final class PermissionStorageException extends RuntimeException {

    /**
     * Creates a new storage exception with the supplied detail message.
     *
     * @param message detail message describing the storage failure
     */
    public PermissionStorageException(String message) {
        super(message);
    }

    /**
     * Creates a new storage exception with the supplied detail message and cause.
     *
     * @param message detail message describing the storage failure
     * @param cause underlying failure that prevented storage access
     */
    public PermissionStorageException(String message, Throwable cause) {
        super(message, cause);
    }
}
