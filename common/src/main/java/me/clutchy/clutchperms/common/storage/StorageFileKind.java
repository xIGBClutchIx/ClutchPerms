package me.clutchy.clutchperms.common.storage;

import java.util.Arrays;
import java.util.Optional;

/**
 * Identifies the persisted ClutchPerms JSON files that can be backed up and restored.
 */
public enum StorageFileKind {

    /**
     * Direct subject permission assignments.
     */
    PERMISSIONS("permissions", "permissions.json"),

    /**
     * Subject metadata and last-known names.
     */
    SUBJECTS("subjects", "subjects.json"),

    /**
     * Group definitions, permissions, parents, and memberships.
     */
    GROUPS("groups", "groups.json"),

    /**
     * Manual known permission node registry.
     */
    NODES("nodes", "nodes.json");

    private final String token;

    private final String fileName;

    StorageFileKind(String token, String fileName) {
        this.token = token;
        this.fileName = fileName;
    }

    /**
     * Returns the command token and backup directory name.
     *
     * @return stable lowercase token
     */
    public String token() {
        return token;
    }

    /**
     * Returns the live JSON filename for this storage kind.
     *
     * @return live JSON filename
     */
    public String fileName() {
        return fileName;
    }

    /**
     * Resolves a command token to a storage file kind.
     *
     * @param token command token
     * @return matching storage file kind, or empty when unknown
     */
    public static Optional<StorageFileKind> fromToken(String token) {
        return Arrays.stream(values()).filter(kind -> kind.token.equals(token)).findFirst();
    }
}
