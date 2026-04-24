package me.clutchy.clutchperms.common;

import java.util.UUID;

/**
 * Receives group storage mutation notifications so platform bridges can refresh affected runtime permission state.
 */
public interface GroupChangeListener {

    /**
     * Reports that one subject's explicit group memberships changed.
     *
     * @param subjectId subject UUID whose memberships changed
     */
    void subjectGroupsChanged(UUID subjectId);

    /**
     * Reports that group definitions or group permissions changed and every online subject may need a refresh.
     */
    void groupsChanged();
}
