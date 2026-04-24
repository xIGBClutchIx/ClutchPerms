package me.clutchy.clutchperms.common.command;

import java.util.Objects;
import java.util.UUID;

/**
 * Identifies a permission subject resolved from a command target.
 *
 * @param id stable UUID used by the permission service
 * @param displayName human-readable subject label used in command responses
 */
public record CommandSubject(UUID id, String displayName) {

    /**
     * Creates a command subject with a UUID and display name.
     *
     * @param id stable UUID used by the permission service
     * @param displayName human-readable subject label used in command responses
     */
    public CommandSubject {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(displayName, "displayName");
    }
}
