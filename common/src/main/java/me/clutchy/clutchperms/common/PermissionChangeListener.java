package me.clutchy.clutchperms.common;

import java.util.UUID;

/**
 * Receives notifications after a subject's explicit permissions are mutated.
 */
@FunctionalInterface
public interface PermissionChangeListener {

    /**
     * Handles a successful permission mutation for one subject.
     *
     * @param subjectId unique identifier for the subject that changed
     */
    void permissionChanged(UUID subjectId);
}
