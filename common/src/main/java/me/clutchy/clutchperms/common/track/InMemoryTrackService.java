package me.clutchy.clutchperms.common.track;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import me.clutchy.clutchperms.common.group.GroupChangeListener;
import me.clutchy.clutchperms.common.group.GroupService;
/**
 * Thread-safe in-memory {@link TrackService} implementation backed by normalized ordered group names.
 */
final class InMemoryTrackService implements TrackService, GroupChangeListener {

    private final GroupService groupService;

    private final ConcurrentMap<String, List<String>> tracks = new ConcurrentHashMap<>();

    InMemoryTrackService(GroupService groupService) {
        this(groupService, Map.of());
    }

    InMemoryTrackService(GroupService groupService, Map<String, List<String>> initialTracks) {
        this.groupService = Objects.requireNonNull(groupService, "groupService");
        Objects.requireNonNull(initialTracks, "initialTracks").forEach((trackName, groupNames) -> {
            String normalizedTrackName = normalizeTrackName(trackName);
            validateTrackGroups(normalizedTrackName, groupNames);
            tracks.put(normalizedTrackName, List.copyOf(normalizeTrackGroups(groupNames)));
        });
    }

    @Override
    public Set<String> getTracks() {
        return Collections.unmodifiableSet(new TreeSet<>(tracks.keySet()));
    }

    @Override
    public boolean hasTrack(String trackName) {
        return tracks.containsKey(normalizeTrackName(trackName));
    }

    @Override
    public List<String> getTrackGroups(String trackName) {
        String normalizedTrackName = normalizeExistingTrackName(trackName);
        return Collections.unmodifiableList(new ArrayList<>(tracks.get(normalizedTrackName)));
    }

    @Override
    public void createTrack(String trackName) {
        String normalizedTrackName = normalizeTrackName(trackName);
        if (tracks.putIfAbsent(normalizedTrackName, List.of()) != null) {
            throw new IllegalArgumentException("track already exists: " + normalizedTrackName);
        }
    }

    @Override
    public void deleteTrack(String trackName) {
        tracks.remove(normalizeExistingTrackName(trackName));
    }

    @Override
    public void renameTrack(String trackName, String newTrackName) {
        String normalizedTrackName = normalizeExistingTrackName(trackName);
        String normalizedNewTrackName = normalizeTrackName(newTrackName);
        if (tracks.containsKey(normalizedNewTrackName)) {
            throw new IllegalArgumentException("track already exists: " + normalizedNewTrackName);
        }
        List<String> groups = tracks.remove(normalizedTrackName);
        tracks.put(normalizedNewTrackName, groups);
    }

    @Override
    public void setTrackGroups(String trackName, List<String> groupNames) {
        String normalizedTrackName = normalizeExistingTrackName(trackName);
        validateTrackGroups(normalizedTrackName, groupNames);
        tracks.put(normalizedTrackName, List.copyOf(normalizeTrackGroups(groupNames)));
    }

    Map<String, List<String>> tracksSnapshot() {
        Map<String, List<String>> snapshot = new TreeMap<>();
        tracks.forEach((trackName, groupNames) -> snapshot.put(trackName, List.copyOf(groupNames)));
        return Collections.unmodifiableMap(snapshot);
    }

    @Override
    public void subjectGroupsChanged(java.util.UUID subjectId) {
    }

    @Override
    public void groupsChanged() {
    }

    @Override
    public void groupDeleted(String groupName) {
        String normalizedGroupName = normalizeGroupName(groupName);
        tracks.replaceAll((ignored, groupNames) -> groupNames.stream().filter(group -> !group.equals(normalizedGroupName)).toList());
    }

    @Override
    public void groupRenamed(String groupName, String newGroupName) {
        String normalizedGroupName = normalizeGroupName(groupName);
        String normalizedNewGroupName = normalizeGroupName(newGroupName);
        tracks.replaceAll((ignored, groupNames) -> groupNames.stream().map(group -> group.equals(normalizedGroupName) ? normalizedNewGroupName : group).toList());
    }

    static String normalizeTrackName(String trackName) {
        String normalizedTrackName = Objects.requireNonNull(trackName, "trackName").trim().toLowerCase(Locale.ROOT);
        if (normalizedTrackName.isEmpty()) {
            throw new IllegalArgumentException("track name must not be blank");
        }
        return normalizedTrackName;
    }

    private String normalizeExistingTrackName(String trackName) {
        String normalizedTrackName = normalizeTrackName(trackName);
        if (!tracks.containsKey(normalizedTrackName)) {
            throw new IllegalArgumentException("unknown track: " + normalizedTrackName);
        }
        return normalizedTrackName;
    }

    private void validateTrackGroups(String trackName, List<String> groupNames) {
        List<String> normalizedTrackGroups = normalizeTrackGroups(groupNames);
        Set<String> seenGroups = new LinkedHashSet<>();
        for (int index = 0; index < normalizedTrackGroups.size(); index++) {
            String groupName = normalizedTrackGroups.get(index);
            if (!groupService.hasGroup(groupName)) {
                throw new IllegalArgumentException("unknown group on track " + trackName + ": " + groupName);
            }
            if (GroupService.OP_GROUP.equals(groupName)) {
                throw new IllegalArgumentException("op group cannot be added to tracks");
            }
            if (GroupService.DEFAULT_GROUP.equals(groupName) && index != 0) {
                throw new IllegalArgumentException("default group may only appear first on a track");
            }
            if (!seenGroups.add(groupName)) {
                throw new IllegalArgumentException("duplicate group on track " + trackName + ": " + groupName);
            }
        }
    }

    private static List<String> normalizeTrackGroups(List<String> groupNames) {
        Objects.requireNonNull(groupNames, "groupNames");
        List<String> normalizedTrackGroups = new ArrayList<>(groupNames.size());
        for (String groupName : groupNames) {
            normalizedTrackGroups.add(normalizeGroupName(groupName));
        }
        return normalizedTrackGroups;
    }

    private static String normalizeGroupName(String groupName) {
        String normalizedGroupName = Objects.requireNonNull(groupName, "groupName").trim().toLowerCase(Locale.ROOT);
        if (normalizedGroupName.isEmpty()) {
            throw new IllegalArgumentException("group name must not be blank");
        }
        return normalizedGroupName;
    }
}
