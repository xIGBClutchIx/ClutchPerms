package me.clutchy.clutchperms.common.permission;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.UUID;

import me.clutchy.clutchperms.common.group.GroupService;

/**
 * Resolves effective permissions from direct user assignments, explicit groups, and the implicit default group.
 */
public final class PermissionResolver {

    private final PermissionService permissionService;

    private final GroupService groupService;

    /**
     * Creates a resolver over the current direct permission and group services.
     *
     * @param permissionService direct user permission service
     * @param groupService group service
     */
    public PermissionResolver(PermissionService permissionService, GroupService groupService) {
        this.permissionService = Objects.requireNonNull(permissionService, "permissionService");
        this.groupService = Objects.requireNonNull(groupService, "groupService");
    }

    /**
     * Resolves one effective permission value for a subject.
     *
     * @param subjectId subject UUID to resolve
     * @param node permission node to resolve
     * @return effective permission resolution details
     */
    public PermissionResolution resolve(UUID subjectId, String node) {
        Objects.requireNonNull(subjectId, "subjectId");
        String normalizedNode = PermissionNodes.normalize(node);

        ResolvedAssignment directAssignment = resolveAssignments(permissionService.getPermissions(subjectId), normalizedNode);
        if (directAssignment.value() != PermissionValue.UNSET) {
            return new PermissionResolution(directAssignment.value(), PermissionResolution.Source.DIRECT, null, directAssignment.assignmentNode());
        }

        PermissionResolution groupResolution = resolveGroupHierarchy(groupService.getSubjectGroups(subjectId), normalizedNode, PermissionResolution.Source.GROUP);
        if (groupResolution.value() != PermissionValue.UNSET) {
            return groupResolution;
        }

        if (groupService.hasGroup(GroupService.DEFAULT_GROUP)) {
            PermissionResolution defaultResolution = resolveGroupHierarchy(Set.of(GroupService.DEFAULT_GROUP), normalizedNode, PermissionResolution.Source.DEFAULT);
            if (defaultResolution.value() != PermissionValue.UNSET) {
                return defaultResolution;
            }
        }

        return new PermissionResolution(PermissionValue.UNSET, PermissionResolution.Source.UNSET, null);
    }

    /**
     * Resolves whether a subject should be treated as having a permission.
     *
     * @param subjectId subject UUID to resolve
     * @param node permission node to resolve
     * @return {@code true} when the effective value is {@link PermissionValue#TRUE}
     */
    public boolean hasPermission(UUID subjectId, String node) {
        return resolve(subjectId, node).value() == PermissionValue.TRUE;
    }

    /**
     * Explains every explicit assignment that matched a checked permission node.
     *
     * @param subjectId subject UUID to resolve
     * @param node permission node to resolve
     * @return immutable explanation snapshot
     */
    public PermissionExplanation explain(UUID subjectId, String node) {
        Objects.requireNonNull(subjectId, "subjectId");
        String normalizedNode = PermissionNodes.normalize(node);
        List<ExplainedAssignment> assignments = new ArrayList<>();

        collectDirectExplanationAssignments(permissionService.getPermissions(subjectId), normalizedNode, assignments);
        collectGroupExplanationAssignments(groupService.getSubjectGroups(subjectId), normalizedNode, PermissionResolution.Source.GROUP, assignments);
        if (groupService.hasGroup(GroupService.DEFAULT_GROUP)) {
            collectGroupExplanationAssignments(Set.of(GroupService.DEFAULT_GROUP), normalizedNode, PermissionResolution.Source.DEFAULT, assignments);
        }

        PermissionResolution resolution = assignments.isEmpty()
                ? new PermissionResolution(PermissionValue.UNSET, PermissionResolution.Source.UNSET, null)
                : resolutionFrom(assignments.getFirst());
        List<PermissionExplanation.Match> matches = new ArrayList<>();
        for (int index = 0; index < assignments.size(); index++) {
            ExplainedAssignment assignment = assignments.get(index);
            matches.add(
                    new PermissionExplanation.Match(assignment.source(), assignment.groupName(), assignment.depth(), assignment.assignmentNode(), assignment.value(), index == 0));
        }
        return new PermissionExplanation(normalizedNode, resolution, matches);
    }

    /**
     * Lists every effective non-{@link PermissionValue#UNSET} permission for a subject.
     *
     * @param subjectId subject UUID to resolve
     * @return immutable snapshot of effective permission assignments keyed by normalized node
     */
    public Map<String, PermissionValue> getEffectivePermissions(UUID subjectId) {
        Objects.requireNonNull(subjectId, "subjectId");
        Set<String> nodes = new TreeSet<>();
        nodes.addAll(permissionService.getPermissions(subjectId).keySet());

        for (String groupName : groupService.getSubjectGroups(subjectId)) {
            if (groupService.hasGroup(groupName)) {
                collectHierarchyNodes(groupName, nodes, new HashSet<>());
            }
        }

        if (groupService.hasGroup(GroupService.DEFAULT_GROUP)) {
            collectHierarchyNodes(GroupService.DEFAULT_GROUP, nodes, new HashSet<>());
        }

        Map<String, PermissionValue> effectivePermissions = new TreeMap<>();
        for (String node : nodes) {
            PermissionValue value = resolve(subjectId, node).value();
            if (value != PermissionValue.UNSET) {
                effectivePermissions.put(node, value);
            }
        }
        return Map.copyOf(effectivePermissions);
    }

    private PermissionResolution resolveGroupHierarchy(Set<String> rootGroups, String node, PermissionResolution.Source source) {
        Map<String, Integer> groupDepths = collectGroupDepths(rootGroups);
        Map<Integer, Set<String>> groupsByDepth = new TreeMap<>();
        groupDepths.forEach((groupName, depth) -> groupsByDepth.computeIfAbsent(depth, ignored -> new TreeSet<>()).add(groupName));

        for (Set<String> groupNames : groupsByDepth.values()) {
            for (String candidateNode : PermissionNodes.matchingCandidates(node)) {
                String falseGroup = null;
                String trueGroup = null;
                for (String groupName : groupNames) {
                    PermissionValue groupValue = groupService.getGroupPermissions(groupName).getOrDefault(candidateNode, PermissionValue.UNSET);
                    if (groupValue == PermissionValue.FALSE && falseGroup == null) {
                        falseGroup = groupName;
                    } else if (groupValue == PermissionValue.TRUE && trueGroup == null) {
                        trueGroup = groupName;
                    }
                }

                if (falseGroup != null) {
                    return new PermissionResolution(PermissionValue.FALSE, source, falseGroup, candidateNode);
                }
                if (trueGroup != null) {
                    return new PermissionResolution(PermissionValue.TRUE, source, trueGroup, candidateNode);
                }
            }
        }

        return new PermissionResolution(PermissionValue.UNSET, PermissionResolution.Source.UNSET, null);
    }

    private ResolvedAssignment resolveAssignments(Map<String, PermissionValue> assignments, String node) {
        for (String candidateNode : PermissionNodes.matchingCandidates(node)) {
            PermissionValue value = assignments.getOrDefault(candidateNode, PermissionValue.UNSET);
            if (value != PermissionValue.UNSET) {
                return new ResolvedAssignment(value, candidateNode);
            }
        }
        return new ResolvedAssignment(PermissionValue.UNSET, null);
    }

    private void collectDirectExplanationAssignments(Map<String, PermissionValue> assignments, String node, List<ExplainedAssignment> explanationAssignments) {
        for (String candidateNode : PermissionNodes.matchingCandidates(node)) {
            PermissionValue value = assignments.getOrDefault(candidateNode, PermissionValue.UNSET);
            if (value != PermissionValue.UNSET) {
                explanationAssignments.add(new ExplainedAssignment(PermissionResolution.Source.DIRECT, null, 0, candidateNode, value));
            }
        }
    }

    private void collectGroupExplanationAssignments(Set<String> rootGroups, String node, PermissionResolution.Source source, List<ExplainedAssignment> explanationAssignments) {
        Map<String, Integer> groupDepths = collectGroupDepths(rootGroups);
        Map<Integer, Set<String>> groupsByDepth = new TreeMap<>();
        groupDepths.forEach((groupName, depth) -> groupsByDepth.computeIfAbsent(depth, ignored -> new TreeSet<>()).add(groupName));

        for (Map.Entry<Integer, Set<String>> depthEntry : groupsByDepth.entrySet()) {
            int depth = depthEntry.getKey();
            Set<String> groupNames = depthEntry.getValue();
            for (String candidateNode : PermissionNodes.matchingCandidates(node)) {
                collectGroupExplanationAssignmentsByValue(source, explanationAssignments, depth, groupNames, candidateNode, PermissionValue.FALSE);
                collectGroupExplanationAssignmentsByValue(source, explanationAssignments, depth, groupNames, candidateNode, PermissionValue.TRUE);
            }
        }
    }

    private void collectGroupExplanationAssignmentsByValue(PermissionResolution.Source source, List<ExplainedAssignment> explanationAssignments, int depth, Set<String> groupNames,
            String candidateNode, PermissionValue value) {
        for (String groupName : groupNames) {
            PermissionValue groupValue = groupService.getGroupPermissions(groupName).getOrDefault(candidateNode, PermissionValue.UNSET);
            if (groupValue == value) {
                explanationAssignments.add(new ExplainedAssignment(source, groupName, depth, candidateNode, value));
            }
        }
    }

    private PermissionResolution resolutionFrom(ExplainedAssignment assignment) {
        return new PermissionResolution(assignment.value(), assignment.source(), assignment.groupName(), assignment.assignmentNode());
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

    private void collectHierarchyNodes(String groupName, Set<String> nodes, Set<String> visitedGroups) {
        if (!visitedGroups.add(groupName) || !groupService.hasGroup(groupName)) {
            return;
        }

        nodes.addAll(groupService.getGroupPermissions(groupName).keySet());
        groupService.getGroupParents(groupName).forEach(parentGroupName -> collectHierarchyNodes(parentGroupName, nodes, visitedGroups));
    }

    private record GroupDepth(String groupName, int depth) {
    }

    private record ResolvedAssignment(PermissionValue value, String assignmentNode) {
    }

    private record ExplainedAssignment(PermissionResolution.Source source, String groupName, int depth, String assignmentNode, PermissionValue value) {
    }
}
