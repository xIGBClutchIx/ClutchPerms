package me.clutchy.clutchperms.common.permission;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import me.clutchy.clutchperms.common.group.GroupService;
import me.clutchy.clutchperms.common.group.InMemoryGroupService;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Verifies effective permission explanation behavior.
 */
class PermissionResolverTest {

    private static final UUID SUBJECT_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    private InMemoryPermissionService permissionService;

    private GroupService groupService;

    private PermissionResolver resolver;

    /**
     * Creates fresh services for each resolver test.
     */
    @BeforeEach
    void setUp() {
        permissionService = new InMemoryPermissionService();
        groupService = new InMemoryGroupService();
        resolver = new PermissionResolver(permissionService, groupService);
    }

    /**
     * Confirms explanation output lists direct, group, and default matches in resolver precedence order.
     */
    @Test
    void explainListsWinnerAndIgnoredMatches() {
        permissionService.setPermission(SUBJECT_ID, "example.*", PermissionValue.TRUE);
        groupService.createGroup("staff");
        groupService.setGroupPermission("staff", "example.node", PermissionValue.FALSE);
        groupService.addSubjectGroup(SUBJECT_ID, "staff");
        groupService.createGroup("default");
        groupService.setGroupPermission("default", "*", PermissionValue.FALSE);

        PermissionExplanation explanation = resolver.explain(SUBJECT_ID, "Example.Node");

        assertEquals("example.node", explanation.node());
        assertEquals(new PermissionResolution(PermissionValue.TRUE, PermissionResolution.Source.DIRECT, null, "example.*"), explanation.resolution());
        assertEquals(List.of(new PermissionExplanation.Match(PermissionResolution.Source.DIRECT, null, 0, "example.*", PermissionValue.TRUE, true),
                new PermissionExplanation.Match(PermissionResolution.Source.GROUP, "staff", 0, "example.node", PermissionValue.FALSE, false),
                new PermissionExplanation.Match(PermissionResolution.Source.DEFAULT, "default", 0, "*", PermissionValue.FALSE, false)), explanation.matches());
    }

    /**
     * Confirms child group wildcard assignments are explained before inherited parent exact assignments.
     */
    @Test
    void explainPreservesGroupDepthBeforeSpecificity() {
        groupService.createGroup("base");
        groupService.setGroupPermission("base", "example.node", PermissionValue.FALSE);
        groupService.createGroup("staff");
        groupService.setGroupPermission("staff", "example.*", PermissionValue.TRUE);
        groupService.addGroupParent("staff", "base");
        groupService.addSubjectGroup(SUBJECT_ID, "staff");

        PermissionExplanation explanation = resolver.explain(SUBJECT_ID, "example.node");

        assertEquals(new PermissionResolution(PermissionValue.TRUE, PermissionResolution.Source.GROUP, "staff", "example.*"), explanation.resolution());
        assertEquals(List.of(new PermissionExplanation.Match(PermissionResolution.Source.GROUP, "staff", 0, "example.*", PermissionValue.TRUE, true),
                new PermissionExplanation.Match(PermissionResolution.Source.GROUP, "base", 1, "example.node", PermissionValue.FALSE, false)), explanation.matches());
    }

    /**
     * Confirms FALSE winners at the same source/depth/specificity are explained before TRUE matches.
     */
    @Test
    void explainOrdersFalseBeforeTrueAtSameRank() {
        groupService.createGroup("allow");
        groupService.setGroupPermission("allow", "example.*", PermissionValue.TRUE);
        groupService.addSubjectGroup(SUBJECT_ID, "allow");
        groupService.createGroup("deny");
        groupService.setGroupPermission("deny", "example.*", PermissionValue.FALSE);
        groupService.addSubjectGroup(SUBJECT_ID, "deny");

        PermissionExplanation explanation = resolver.explain(SUBJECT_ID, "example.node");

        assertEquals(new PermissionResolution(PermissionValue.FALSE, PermissionResolution.Source.GROUP, "deny", "example.*"), explanation.resolution());
        assertEquals(List.of(new PermissionExplanation.Match(PermissionResolution.Source.GROUP, "deny", 0, "example.*", PermissionValue.FALSE, true),
                new PermissionExplanation.Match(PermissionResolution.Source.GROUP, "allow", 0, "example.*", PermissionValue.TRUE, false)), explanation.matches());
    }

    /**
     * Confirms unset explanations report no matching assignments.
     */
    @Test
    void explainReportsNoMatchesForUnsetResolution() {
        PermissionExplanation explanation = resolver.explain(SUBJECT_ID, "missing.node");

        assertEquals(new PermissionResolution(PermissionValue.UNSET, PermissionResolution.Source.UNSET, null), explanation.resolution());
        assertEquals(List.of(), explanation.matches());
    }
}
