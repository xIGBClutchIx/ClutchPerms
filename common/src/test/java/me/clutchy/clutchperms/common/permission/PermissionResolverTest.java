package me.clutchy.clutchperms.common.permission;

import java.util.List;
import java.util.Map;
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

    private static final UUID SECOND_SUBJECT_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");

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
     * Confirms repeated resolve and effective-permission calls reuse stable cached results.
     */
    @Test
    void repeatedLookupsPopulatePredictableCacheStats() {
        permissionService.setPermission(SUBJECT_ID, "Example.Node", PermissionValue.TRUE);

        assertEquals(new PermissionResolverCacheStats(0, 0, 0), resolver.cacheStats());
        assertEquals(PermissionValue.TRUE, resolver.resolve(SUBJECT_ID, "example.node").value());
        assertEquals(PermissionValue.TRUE, resolver.resolve(SUBJECT_ID, "EXAMPLE.NODE").value());
        assertEquals(new PermissionResolverCacheStats(1, 1, 0), resolver.cacheStats());

        assertEquals(Map.of("example.node", PermissionValue.TRUE), resolver.getEffectivePermissions(SUBJECT_ID));
        assertEquals(Map.of("example.node", PermissionValue.TRUE), resolver.getEffectivePermissions(SUBJECT_ID));
        assertEquals(new PermissionResolverCacheStats(1, 1, 1), resolver.cacheStats());
    }

    /**
     * Confirms direct permission mutations are stale until the changed subject is invalidated.
     */
    @Test
    void directMutationIsVisibleAfterSubjectInvalidation() {
        permissionService.setPermission(SUBJECT_ID, "example.node", PermissionValue.TRUE);
        assertEquals(PermissionValue.TRUE, resolver.resolve(SUBJECT_ID, "example.node").value());

        permissionService.setPermission(SUBJECT_ID, "example.node", PermissionValue.FALSE);

        assertEquals(PermissionValue.TRUE, resolver.resolve(SUBJECT_ID, "example.node").value());
        resolver.invalidateSubject(SUBJECT_ID);
        assertEquals(PermissionValue.FALSE, resolver.resolve(SUBJECT_ID, "example.node").value());
    }

    /**
     * Confirms membership mutations are stale until the changed subject is invalidated.
     */
    @Test
    void membershipMutationIsVisibleAfterSubjectInvalidation() {
        groupService.createGroup("staff");
        groupService.setGroupPermission("staff", "example.node", PermissionValue.TRUE);
        assertEquals(PermissionValue.UNSET, resolver.resolve(SUBJECT_ID, "example.node").value());

        groupService.addSubjectGroup(SUBJECT_ID, "staff");

        assertEquals(PermissionValue.UNSET, resolver.resolve(SUBJECT_ID, "example.node").value());
        resolver.invalidateSubject(SUBJECT_ID);
        assertEquals(PermissionValue.TRUE, resolver.resolve(SUBJECT_ID, "example.node").value());
    }

    /**
     * Confirms group permission changes are visible after clearing the full resolver cache.
     */
    @Test
    void groupMutationIsVisibleAfterFullInvalidation() {
        groupService.createGroup("staff");
        groupService.addSubjectGroup(SUBJECT_ID, "staff");
        assertEquals(PermissionValue.UNSET, resolver.resolve(SUBJECT_ID, "example.node").value());

        groupService.setGroupPermission("staff", "example.node", PermissionValue.TRUE);

        assertEquals(PermissionValue.UNSET, resolver.resolve(SUBJECT_ID, "example.node").value());
        resolver.invalidateAll();
        assertEquals(PermissionValue.TRUE, resolver.resolve(SUBJECT_ID, "example.node").value());
    }

    /**
     * Confirms invalidating one subject leaves other subjects' cached results intact.
     */
    @Test
    void invalidateSubjectOnlyClearsOneSubject() {
        permissionService.setPermission(SUBJECT_ID, "example.node", PermissionValue.TRUE);
        permissionService.setPermission(SECOND_SUBJECT_ID, "example.node", PermissionValue.FALSE);
        assertEquals(PermissionValue.TRUE, resolver.resolve(SUBJECT_ID, "example.node").value());
        assertEquals(PermissionValue.FALSE, resolver.resolve(SECOND_SUBJECT_ID, "example.node").value());
        assertEquals(Map.of("example.node", PermissionValue.TRUE), resolver.getEffectivePermissions(SUBJECT_ID));
        assertEquals(Map.of("example.node", PermissionValue.FALSE), resolver.getEffectivePermissions(SECOND_SUBJECT_ID));

        resolver.invalidateSubject(SUBJECT_ID);

        assertEquals(new PermissionResolverCacheStats(1, 1, 1), resolver.cacheStats());
        assertEquals(PermissionValue.FALSE, resolver.resolve(SECOND_SUBJECT_ID, "example.node").value());
    }

    /**
     * Confirms clearing the full cache removes node and effective-permission snapshots.
     */
    @Test
    void invalidateAllClearsEveryCache() {
        permissionService.setPermission(SUBJECT_ID, "example.node", PermissionValue.TRUE);
        assertEquals(PermissionValue.TRUE, resolver.resolve(SUBJECT_ID, "example.node").value());
        assertEquals(Map.of("example.node", PermissionValue.TRUE), resolver.getEffectivePermissions(SUBJECT_ID));

        resolver.invalidateAll();

        assertEquals(new PermissionResolverCacheStats(0, 0, 0), resolver.cacheStats());
    }

    /**
     * Confirms explanation output reads current services rather than cached resolve results.
     */
    @Test
    void explainIgnoresStaleResolveCache() {
        permissionService.setPermission(SUBJECT_ID, "example.node", PermissionValue.TRUE);
        assertEquals(PermissionValue.TRUE, resolver.resolve(SUBJECT_ID, "example.node").value());

        permissionService.setPermission(SUBJECT_ID, "example.node", PermissionValue.FALSE);

        assertEquals(PermissionValue.TRUE, resolver.resolve(SUBJECT_ID, "example.node").value());
        assertEquals(new PermissionResolution(PermissionValue.FALSE, PermissionResolution.Source.DIRECT, null, "example.node"),
                resolver.explain(SUBJECT_ID, "example.node").resolution());
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
