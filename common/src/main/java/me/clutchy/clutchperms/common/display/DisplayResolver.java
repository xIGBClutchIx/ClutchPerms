package me.clutchy.clutchperms.common.display;

import java.util.ArrayDeque;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.UUID;

import me.clutchy.clutchperms.common.group.GroupService;
import me.clutchy.clutchperms.common.subject.SubjectMetadataService;

/**
 * Resolves effective user display values from direct subject values, explicit groups, and the implicit default group.
 */
public final class DisplayResolver {

    private final SubjectMetadataService subjectMetadataService;

    private final GroupService groupService;

    /**
     * Creates a display resolver.
     *
     * @param subjectMetadataService subject display source
     * @param groupService group display source
     */
    public DisplayResolver(SubjectMetadataService subjectMetadataService, GroupService groupService) {
        this.subjectMetadataService = Objects.requireNonNull(subjectMetadataService, "subjectMetadataService");
        this.groupService = Objects.requireNonNull(groupService, "groupService");
    }

    /**
     * Resolves the effective prefix for a subject.
     *
     * @param subjectId subject UUID
     * @return display resolution
     */
    public DisplayResolution resolvePrefix(UUID subjectId) {
        return resolve(subjectId, DisplaySlot.PREFIX);
    }

    /**
     * Resolves the effective suffix for a subject.
     *
     * @param subjectId subject UUID
     * @return display resolution
     */
    public DisplayResolution resolveSuffix(UUID subjectId) {
        return resolve(subjectId, DisplaySlot.SUFFIX);
    }

    /**
     * Resolves one effective display slot for a subject.
     *
     * @param subjectId subject UUID
     * @param slot display slot
     * @return display resolution
     */
    public DisplayResolution resolve(UUID subjectId, DisplaySlot slot) {
        Objects.requireNonNull(subjectId, "subjectId");
        Objects.requireNonNull(slot, "slot");

        Optional<DisplayText> directValue = value(subjectMetadataService.getSubjectDisplay(subjectId), slot);
        if (directValue.isPresent()) {
            return new DisplayResolution(slot, directValue, DisplayResolution.Source.DIRECT, null, -1);
        }

        DisplayResolution groupResolution = resolveGroupHierarchy(groupService.getSubjectGroups(subjectId), slot, DisplayResolution.Source.GROUP);
        if (groupResolution.value().isPresent()) {
            return groupResolution;
        }

        if (groupService.hasGroup(GroupService.DEFAULT_GROUP)) {
            DisplayResolution defaultResolution = resolveGroupHierarchy(Set.of(GroupService.DEFAULT_GROUP), slot, DisplayResolution.Source.DEFAULT);
            if (defaultResolution.value().isPresent()) {
                return defaultResolution;
            }
        }

        return new DisplayResolution(slot, Optional.empty(), DisplayResolution.Source.UNSET, null, -1);
    }

    private DisplayResolution resolveGroupHierarchy(Set<String> rootGroups, DisplaySlot slot, DisplayResolution.Source source) {
        Map<String, Integer> groupDepths = collectGroupDepths(rootGroups);
        Map<Integer, Set<String>> groupsByDepth = new TreeMap<>();
        groupDepths.forEach((groupName, depth) -> groupsByDepth.computeIfAbsent(depth, ignored -> new TreeSet<>()).add(groupName));

        for (Map.Entry<Integer, Set<String>> depthEntry : groupsByDepth.entrySet()) {
            for (String groupName : depthEntry.getValue()) {
                Optional<DisplayText> value = value(groupService.getGroupDisplay(groupName), slot);
                if (value.isPresent()) {
                    return new DisplayResolution(slot, value, source, groupName, depthEntry.getKey());
                }
            }
        }

        return new DisplayResolution(slot, Optional.empty(), DisplayResolution.Source.UNSET, null, -1);
    }

    private Map<String, Integer> collectGroupDepths(Set<String> rootGroups) {
        Map<String, Integer> groupDepths = new HashMap<>();
        ArrayDeque<GroupDepth> queue = new ArrayDeque<>();
        rootGroups.stream().filter(groupService::hasGroup).sorted(Comparator.naturalOrder()).forEach(groupName -> queue.add(new GroupDepth(groupName, 0)));

        while (!queue.isEmpty()) {
            GroupDepth groupDepth = queue.removeFirst();
            Integer existingDepth = groupDepths.get(groupDepth.groupName());
            if (existingDepth != null && existingDepth <= groupDepth.depth()) {
                continue;
            }

            groupDepths.put(groupDepth.groupName(), groupDepth.depth());
            groupService.getGroupParents(groupDepth.groupName()).stream().filter(groupService::hasGroup).sorted(Comparator.naturalOrder())
                    .forEach(parentGroupName -> queue.addLast(new GroupDepth(parentGroupName, groupDepth.depth() + 1)));
        }
        return groupDepths;
    }

    private static Optional<DisplayText> value(DisplayProfile profile, DisplaySlot slot) {
        return switch (slot) {
            case PREFIX -> profile.prefix();
            case SUFFIX -> profile.suffix();
        };
    }

    private record GroupDepth(String groupName, int depth) {
    }
}
