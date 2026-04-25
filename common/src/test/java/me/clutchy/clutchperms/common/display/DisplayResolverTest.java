package me.clutchy.clutchperms.common.display;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import me.clutchy.clutchperms.common.group.GroupService;
import me.clutchy.clutchperms.common.group.InMemoryGroupService;
import me.clutchy.clutchperms.common.subject.InMemorySubjectMetadataService;
import me.clutchy.clutchperms.common.subject.SubjectMetadataService;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Verifies effective display resolution from users, groups, parent groups, and default.
 */
class DisplayResolverTest {

    private static final UUID SUBJECT_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    private static final UUID DEFAULT_ONLY_SUBJECT_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");

    private SubjectMetadataService subjectMetadataService;

    private GroupService groupService;

    private DisplayResolver resolver;

    @BeforeEach
    void setUp() {
        subjectMetadataService = new InMemorySubjectMetadataService();
        groupService = new InMemoryGroupService();
        resolver = new DisplayResolver(subjectMetadataService, groupService);
    }

    @Test
    void directUserDisplayBeatsGroupsAndDefault() {
        subjectMetadataService.setSubjectPrefix(SUBJECT_ID, DisplayText.parse("&c[Direct]"));
        groupService.setGroupPrefix("default", DisplayText.parse("&7[Default]"));
        groupService.createGroup("staff");
        groupService.setGroupPrefix("staff", DisplayText.parse("&a[Staff]"));
        groupService.addSubjectGroup(SUBJECT_ID, "staff");

        DisplayResolution resolution = resolver.resolvePrefix(SUBJECT_ID);

        assertEquals("&c[Direct]", resolution.value().orElseThrow().rawText());
        assertEquals(DisplayResolution.Source.DIRECT, resolution.source());
    }

    @Test
    void explicitGroupHierarchyBeatsDefaultAndUsesNearestDepthThenGroupName() {
        groupService.setGroupPrefix("default", DisplayText.parse("&7[Default]"));
        groupService.createGroup("zeta");
        groupService.createGroup("alpha");
        groupService.createGroup("parent");
        groupService.setGroupPrefix("zeta", DisplayText.parse("&6[Zeta]"));
        groupService.setGroupPrefix("alpha", DisplayText.parse("&a[Alpha]"));
        groupService.setGroupSuffix("parent", DisplayText.parse("&9[Parent]"));
        groupService.addGroupParent("alpha", "parent");
        groupService.addSubjectGroup(SUBJECT_ID, "zeta");
        groupService.addSubjectGroup(SUBJECT_ID, "alpha");

        DisplayResolution prefix = resolver.resolvePrefix(SUBJECT_ID);
        DisplayResolution suffix = resolver.resolveSuffix(SUBJECT_ID);

        assertEquals("&a[Alpha]", prefix.value().orElseThrow().rawText());
        assertEquals(DisplayResolution.Source.GROUP, prefix.source());
        assertEquals("alpha", prefix.groupName());
        assertEquals(0, prefix.depth());
        assertEquals("&9[Parent]", suffix.value().orElseThrow().rawText());
        assertEquals("parent", suffix.groupName());
        assertEquals(1, suffix.depth());
    }

    @Test
    void defaultGroupHierarchyAppliesWhenNoDirectOrExplicitGroupValueExists() {
        groupService.createGroup("default-parent");
        groupService.setGroupSuffix("default-parent", DisplayText.parse("&8[DefaultParent]"));
        groupService.addGroupParent("default", "default-parent");

        DisplayResolution suffix = resolver.resolveSuffix(DEFAULT_ONLY_SUBJECT_ID);

        assertEquals("&8[DefaultParent]", suffix.value().orElseThrow().rawText());
        assertEquals(DisplayResolution.Source.DEFAULT, suffix.source());
        assertEquals("default-parent", suffix.groupName());
        assertEquals(1, suffix.depth());
    }

    @Test
    void unsetDisplayReportsUnsetSource() {
        DisplayResolution prefix = resolver.resolvePrefix(SUBJECT_ID);

        assertFalse(prefix.value().isPresent());
        assertEquals(DisplayResolution.Source.UNSET, prefix.source());
        assertEquals(-1, prefix.depth());
    }
}
