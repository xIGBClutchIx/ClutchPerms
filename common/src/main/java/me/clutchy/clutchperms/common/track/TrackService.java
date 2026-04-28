package me.clutchy.clutchperms.common.track;

import java.util.List;
import java.util.Set;

/**
 * Stores ordered named group tracks used for promotion and demotion flows.
 */
public interface TrackService {

    /**
     * Lists every defined track.
     *
     * @return immutable snapshot of normalized track names
     */
    Set<String> getTracks();

    /**
     * Checks whether a track exists.
     *
     * @param trackName track name to inspect
     * @return {@code true} when the track exists
     */
    boolean hasTrack(String trackName);

    /**
     * Lists every group on one track from first to last.
     *
     * @param trackName track name to inspect
     * @return immutable ordered snapshot of normalized group names
     */
    List<String> getTrackGroups(String trackName);

    /**
     * Creates one empty track.
     *
     * @param trackName track name to create
     */
    void createTrack(String trackName);

    /**
     * Deletes one track.
     *
     * @param trackName track name to delete
     */
    void deleteTrack(String trackName);

    /**
     * Renames one track.
     *
     * @param trackName current track name
     * @param newTrackName new track name
     */
    void renameTrack(String trackName, String newTrackName);

    /**
     * Replaces the ordered groups stored on one track.
     *
     * @param trackName track name to update
     * @param groupNames ordered group names to store
     */
    void setTrackGroups(String trackName, List<String> groupNames);
}
