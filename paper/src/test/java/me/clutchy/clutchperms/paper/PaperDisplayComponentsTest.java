package me.clutchy.clutchperms.paper;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import me.clutchy.clutchperms.common.display.DisplayResolver;
import me.clutchy.clutchperms.common.display.DisplayText;
import me.clutchy.clutchperms.common.group.GroupService;
import me.clutchy.clutchperms.common.group.InMemoryGroupService;
import me.clutchy.clutchperms.common.subject.InMemorySubjectMetadataService;
import me.clutchy.clutchperms.common.subject.SubjectMetadataService;

import static org.junit.jupiter.api.Assertions.assertEquals;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

final class PaperDisplayComponentsTest {

    private static final UUID SUBJECT_ID = UUID.fromString("00000000-0000-0000-0000-000000000101");

    private SubjectMetadataService subjectMetadataService;

    private GroupService groupService;

    private DisplayResolver displayResolver;

    @BeforeEach
    void setUp() {
        subjectMetadataService = new InMemorySubjectMetadataService();
        groupService = new InMemoryGroupService();
        displayResolver = new DisplayResolver(subjectMetadataService, groupService);
    }

    @Test
    void directUserDisplayFormatsFullChatLine() {
        subjectMetadataService.setSubjectPrefix(SUBJECT_ID, DisplayText.parse("&c[Direct]"));
        subjectMetadataService.setSubjectSuffix(SUBJECT_ID, DisplayText.parse("&e!"));
        groupService.setGroupPrefix(GroupService.DEFAULT_GROUP, DisplayText.parse("&8[Default]"));
        groupService.setGroupSuffix(GroupService.DEFAULT_GROUP, DisplayText.parse("&7?"));

        assertEquals("[Direct] Target !: hello", chatLineText());
    }

    @Test
    void explicitGroupDisplayFormatsFullChatLine() {
        groupService.createGroup("staff");
        groupService.setGroupPrefix("staff", DisplayText.parse("&a[Staff]"));
        groupService.setGroupSuffix("staff", DisplayText.parse("&f*"));
        groupService.addSubjectGroup(SUBJECT_ID, "staff");
        groupService.setGroupPrefix(GroupService.DEFAULT_GROUP, DisplayText.parse("&8[Default]"));
        groupService.setGroupSuffix(GroupService.DEFAULT_GROUP, DisplayText.parse("&7?"));

        assertEquals("[Staff] Target *: hello", chatLineText());
    }

    @Test
    void defaultGroupDisplayFormatsFullChatLine() {
        groupService.setGroupPrefix(GroupService.DEFAULT_GROUP, DisplayText.parse("&8[Default]"));
        groupService.setGroupSuffix(GroupService.DEFAULT_GROUP, DisplayText.parse("&7?"));

        assertEquals("[Default] Target ?: hello", chatLineText());
    }

    private String chatLineText() {
        Component line = PaperDisplayComponents.chatLine(SUBJECT_ID, Component.text("Target"), Component.text("hello"), displayResolver);
        return PlainTextComponentSerializer.plainText().serialize(line);
    }
}
