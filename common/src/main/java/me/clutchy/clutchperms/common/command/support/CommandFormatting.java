package me.clutchy.clutchperms.common.command;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import me.clutchy.clutchperms.common.display.DisplayResolution;
import me.clutchy.clutchperms.common.display.DisplaySlot;
import me.clutchy.clutchperms.common.display.DisplayText;
import me.clutchy.clutchperms.common.group.GroupService;
import me.clutchy.clutchperms.common.node.KnownPermissionNode;
import me.clutchy.clutchperms.common.node.PermissionNodeSource;
import me.clutchy.clutchperms.common.permission.PermissionExplanation;
import me.clutchy.clutchperms.common.permission.PermissionResolution;
import me.clutchy.clutchperms.common.storage.StorageBackup;
import me.clutchy.clutchperms.common.subject.SubjectMetadata;

final class CommandFormatting<S> {

    private static final int SUMMARY_VALUE_LIMIT = 5;

    private final ClutchPermsCommandEnvironment<S> environment;

    CommandFormatting(ClutchPermsCommandEnvironment<S> environment) {
        this.environment = environment;
    }

    String formatSubject(CommandSubject subject) {
        return subject.displayName() + " (" + subject.id() + ")";
    }

    String formatSubject(UUID subjectId) {
        String displayName = environment.subjectMetadataService().getSubject(subjectId).map(SubjectMetadata::lastKnownName).orElse(subjectId.toString());
        return displayName + " (" + subjectId + ")";
    }

    String formatSubjectMetadata(SubjectMetadata subject) {
        return subject.lastKnownName() + " (" + subject.subjectId() + ", last seen " + subject.lastSeen() + ")";
    }

    String summarizeSubjectGroups(UUID subjectId) {
        List<String> groups = new ArrayList<>(environment.groupService().getSubjectGroups(subjectId));
        if (environment.groupService().hasGroup(GroupService.DEFAULT_GROUP)) {
            groups.add(GroupService.DEFAULT_GROUP + " (implicit)");
        }
        return summarizeValues(groups);
    }

    String summarizeGroupMembers(Set<UUID> members) {
        return summarizeValues(members.stream().map(this::formatSubject).toList());
    }

    List<String> findChildGroups(String groupName) {
        return environment.groupService().getGroups().stream().filter(group -> !group.equals(groupName))
                .filter(group -> environment.groupService().getGroupParents(group).contains(groupName)).toList();
    }

    TrackSubjectState subjectTrackState(UUID subjectId, String trackName) {
        List<String> trackGroups = environment.trackService().getTrackGroups(trackName);
        List<String> explicitMatches = trackGroups.stream().filter(environment.groupService().getSubjectGroups(subjectId)::contains).toList();
        boolean implicitDefault = explicitMatches.isEmpty() && !trackGroups.isEmpty() && GroupService.DEFAULT_GROUP.equals(trackGroups.getFirst());
        return new TrackSubjectState(trackName, trackGroups, explicitMatches, implicitDefault);
    }

    String formatTrackSubjectState(TrackSubjectState state) {
        if (state.hasConflict()) {
            return "conflict (" + summarizeValues(state.explicitMatches()) + ")";
        }
        if (state.implicitDefault()) {
            return GroupService.DEFAULT_GROUP + " (implicit)";
        }
        if (state.hasExplicitMatch()) {
            return state.currentGroup() + " (#" + (state.currentIndex() + 1) + ")";
        }
        return "unmatched";
    }

    String summarizeSubjectTracks(UUID subjectId) {
        List<String> tracks = environment.trackService().getTracks().stream().sorted(Comparator.naturalOrder()).map(trackName -> {
            TrackSubjectState state = subjectTrackState(subjectId, trackName);
            if (!state.hasPosition() && !state.hasConflict()) {
                return null;
            }
            return trackName + "=" + formatTrackSubjectState(state);
        }).filter(Objects::nonNull).toList();
        return summarizeValues(tracks);
    }

    List<GroupTrackReference> findGroupTrackReferences(String groupName) {
        return environment.trackService().getTracks().stream().sorted(Comparator.naturalOrder()).map(trackName -> {
            List<String> groups = environment.trackService().getTrackGroups(trackName);
            int index = groups.indexOf(groupName);
            if (index < 0) {
                return null;
            }
            return new GroupTrackReference(trackName, index + 1);
        }).filter(Objects::nonNull).toList();
    }

    JsonArray groupTrackReferencesJson(String groupName) {
        JsonArray array = new JsonArray();
        findGroupTrackReferences(groupName).forEach(reference -> {
            JsonObject root = new JsonObject();
            root.addProperty("track", reference.trackName());
            root.addProperty("position", reference.position());
            array.add(root);
        });
        return array;
    }

    Map<String, Integer> trackReferencePositions(JsonArray array) {
        Map<String, Integer> references = new LinkedHashMap<>();
        array.forEach(value -> {
            JsonObject root = value.getAsJsonObject();
            references.put(root.get("track").getAsString(), root.get("position").getAsInt());
        });
        return references;
    }

    void applyGroupTracks(String groupName, Map<String, Integer> desiredTrackPositions) {
        Map<String, Integer> currentTrackPositions = new LinkedHashMap<>();
        findGroupTrackReferences(groupName).forEach(reference -> currentTrackPositions.put(reference.trackName(), reference.position()));

        currentTrackPositions.keySet().stream().filter(trackName -> !desiredTrackPositions.containsKey(trackName)).toList().forEach(trackName -> {
            List<String> groups = new ArrayList<>(environment.trackService().getTrackGroups(trackName));
            groups.remove(groupName);
            environment.trackService().setTrackGroups(trackName, groups);
        });

        desiredTrackPositions.forEach((trackName, position) -> {
            if (!environment.trackService().hasTrack(trackName)) {
                throw new IllegalArgumentException("missing track for group snapshot: " + trackName);
            }
            List<String> groups = new ArrayList<>(environment.trackService().getTrackGroups(trackName));
            groups.remove(groupName);
            if (position < 1 || position > groups.size() + 1) {
                throw new IllegalArgumentException("track position out of range for group snapshot: " + trackName + "#" + position);
            }
            groups.add(position - 1, groupName);
            environment.trackService().setTrackGroups(trackName, groups);
        });
    }

    String summarizeGroupTracks(String groupName) {
        return summarizeValues(findGroupTrackReferences(groupName).stream().map(reference -> reference.trackName() + "#" + reference.position()).toList());
    }

    String summarizeValues(Collection<String> values) {
        List<String> sortedValues = values.stream().sorted(Comparator.comparing((String value) -> value.toLowerCase(Locale.ROOT)).thenComparing(Comparator.naturalOrder()))
                .toList();
        if (sortedValues.isEmpty()) {
            return "none";
        }

        List<String> shownValues = sortedValues.stream().limit(SUMMARY_VALUE_LIMIT).toList();
        String summary = String.join(", ", shownValues);
        int remaining = sortedValues.size() - shownValues.size();
        if (remaining > 0) {
            summary += ", +" + remaining + " more";
        }
        return summary;
    }

    String formatDisplayValue(Optional<DisplayText> value) {
        return value.map(DisplayText::rawText).orElse("unset");
    }

    String formatEffectiveDisplay(DisplayResolution resolution) {
        return resolution.value().map(value -> value.rawText() + " from " + formatDisplaySource(resolution)).orElse("unset");
    }

    String formatKnownNode(KnownPermissionNode node) {
        String formattedNode = node.node() + " [" + node.source().name().toLowerCase(Locale.ROOT).replace('_', '-') + "]";
        if (!node.description().isEmpty()) {
            formattedNode += " - " + node.description();
        }
        return formattedNode;
    }

    void addSubjectDisplayRows(List<CommandPaging.PagedRow> rows, String rootLiteral, CommandSubject subject) {
        addSubjectDisplayRow(rows, rootLiteral, subject, DisplaySlot.PREFIX);
        addSubjectDisplayRow(rows, rootLiteral, subject, DisplaySlot.SUFFIX);
    }

    void addGroupDisplayRows(List<CommandPaging.PagedRow> rows, String rootLiteral, String groupName) {
        addGroupDisplayRow(rows, rootLiteral, groupName, DisplaySlot.PREFIX);
        addGroupDisplayRow(rows, rootLiteral, groupName, DisplaySlot.SUFFIX);
    }

    Optional<DisplayText> subjectDisplayValue(UUID subjectId, DisplaySlot slot) {
        return switch (slot) {
            case PREFIX -> environment.subjectMetadataService().getSubjectDisplay(subjectId).prefix();
            case SUFFIX -> environment.subjectMetadataService().getSubjectDisplay(subjectId).suffix();
        };
    }

    Optional<DisplayText> groupDisplayValue(String groupName, DisplaySlot slot) {
        return switch (slot) {
            case PREFIX -> environment.groupService().getGroupDisplay(groupName).prefix();
            case SUFFIX -> environment.groupService().getGroupDisplay(groupName).suffix();
        };
    }

    CommandPaging.PagedRow backupRow(String rootLiteral, StorageBackup backup, String text) {
        return new CommandPaging.PagedRow(text, fullCommand(rootLiteral, "backup restore " + backup.fileName()));
    }

    String knownNodeCommand(String rootLiteral, KnownPermissionNode node) {
        if (node.source() == PermissionNodeSource.MANUAL) {
            return fullCommand(rootLiteral, "nodes remove " + node.node());
        }
        return fullCommand(rootLiteral, "nodes search " + node.node());
    }

    String fullCommand(String rootLiteral, String command) {
        return "/" + rootLiteral + (command.isBlank() ? "" : " " + command);
    }

    String formatNodeSource(KnownPermissionNode node) {
        return node.source().name().toLowerCase(Locale.ROOT).replace('_', '-');
    }

    String formatResolutionSource(PermissionResolution resolution) {
        return switch (resolution.source()) {
            case DIRECT -> "direct";
            case GROUP -> "group " + resolution.groupName();
            case DEFAULT -> GroupService.DEFAULT_GROUP.equals(resolution.groupName()) ? "default group" : "default group parent " + resolution.groupName();
            case UNSET -> "unset";
        };
    }

    String formatDisplaySource(DisplayResolution resolution) {
        return switch (resolution.source()) {
            case DIRECT -> "direct";
            case GROUP -> "group " + resolution.groupName();
            case DEFAULT -> GroupService.DEFAULT_GROUP.equals(resolution.groupName()) ? "default group" : "default group parent " + resolution.groupName();
            case UNSET -> "unset";
        };
    }

    String formatExplanationSource(PermissionExplanation.Match match) {
        return switch (match.source()) {
            case DIRECT -> "direct";
            case GROUP -> "group " + match.groupName() + " depth " + match.depth();
            case DEFAULT -> GroupService.DEFAULT_GROUP.equals(match.groupName())
                    ? "default group depth " + match.depth()
                    : "default group parent " + match.groupName() + " depth " + match.depth();
            case UNSET -> "unset";
        };
    }

    String normalizeGroupName(String groupName) {
        return groupName.trim().toLowerCase(Locale.ROOT);
    }

    String normalizeTrackName(String trackName) {
        return trackName.trim().toLowerCase(Locale.ROOT);
    }

    private void addSubjectDisplayRow(List<CommandPaging.PagedRow> rows, String rootLiteral, CommandSubject subject, DisplaySlot slot) {
        Optional<DisplayText> directValue = subjectDisplayValue(subject.id(), slot);
        if (directValue.isPresent()) {
            rows.add(new CommandPaging.PagedRow("direct " + slot.label() + " " + directValue.get().rawText(),
                    fullCommand(rootLiteral, "user " + subject.id() + " " + slot.label() + " get")));
        }

        DisplayResolution resolution = environment.displayResolver().resolve(subject.id(), slot);
        if (resolution.value().isPresent()) {
            String source = formatDisplaySource(resolution);
            rows.add(new CommandPaging.PagedRow("effective " + slot.label() + " " + resolution.value().get().rawText() + " from " + source,
                    fullCommand(rootLiteral, "user " + subject.id() + " " + slot.label() + " get")));
        }
    }

    private void addGroupDisplayRow(List<CommandPaging.PagedRow> rows, String rootLiteral, String groupName, DisplaySlot slot) {
        groupDisplayValue(groupName, slot).ifPresent(
                value -> rows.add(new CommandPaging.PagedRow(slot.label() + " " + value.rawText(), fullCommand(rootLiteral, "group " + groupName + " " + slot.label() + " get"))));
    }

    record TrackSubjectState(String trackName, List<String> trackGroups, List<String> explicitMatches, boolean implicitDefault) {

        boolean hasConflict() {
            return explicitMatches.size() > 1;
        }

        boolean hasExplicitMatch() {
            return explicitMatches.size() == 1;
        }

        boolean hasPosition() {
            return hasExplicitMatch() || implicitDefault;
        }

        int currentIndex() {
            if (hasExplicitMatch()) {
                return trackGroups.indexOf(explicitMatches.getFirst());
            }
            return implicitDefault ? 0 : -1;
        }

        String currentGroup() {
            if (hasExplicitMatch()) {
                return explicitMatches.getFirst();
            }
            return implicitDefault ? GroupService.DEFAULT_GROUP : null;
        }
    }

    record GroupTrackReference(String trackName, int position) {
    }
}
