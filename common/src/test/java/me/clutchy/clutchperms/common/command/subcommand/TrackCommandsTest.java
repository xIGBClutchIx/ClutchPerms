package me.clutchy.clutchperms.common.command;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.mojang.brigadier.exceptions.CommandSyntaxException;

import me.clutchy.clutchperms.common.config.ClutchPermsBackupConfig;
import me.clutchy.clutchperms.common.config.ClutchPermsCommandConfig;
import me.clutchy.clutchperms.common.config.ClutchPermsConfig;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies shared Brigadier command behavior independent of a server platform.
 */
class TrackCommandsTest extends CommandTestBase {

    /**
     * Confirms help output exposes track commands when help pagination is expanded.
     *
     * @throws CommandSyntaxException when command execution fails unexpectedly
     */
    @Test
    void helpCommandIncludesTrackCommands() throws CommandSyntaxException {
        environment.setConfig(new ClutchPermsConfig(ClutchPermsBackupConfig.defaults(), new ClutchPermsCommandConfig(50, 8)));
        TestSource console = TestSource.console();

        dispatcher.execute("clutchperms help 1", console);
        dispatcher.execute("clutchperms help 2", console);

        assertMessageContains(console, "/clutchperms track list [page]");
        assertMessageContains(console, "/clutchperms track <track> move <group> <position>");
        assertMessageContains(console, "/clutchperms user <target> tracks [page]");
        assertMessageContains(console, "/clutchperms user <target> track promote <track>");
    }

    /**
     * Confirms track CRUD and reordering commands update stored ordered groups and audit the mutation.
     *
     * @throws CommandSyntaxException when command execution fails unexpectedly
     */
    @Test
    void trackCommandsCreateListAndReorderGroups() throws CommandSyntaxException {
        groupService.createGroup("staff");
        groupService.createGroup("moderator");
        groupService.createGroup("admin");
        TestSource console = TestSource.console();

        dispatcher.execute("clutchperms track ranks create", console);
        dispatcher.execute("clutchperms track ranks append default", console);
        dispatcher.execute("clutchperms track ranks append staff", console);
        dispatcher.execute("clutchperms track ranks insert 2 moderator", console);
        dispatcher.execute("clutchperms track ranks move staff 2", console);
        dispatcher.execute("clutchperms track ranks list", console);
        dispatcher.execute("clutchperms track ranks info", console);

        assertEquals(List.of("default", "staff", "moderator"), trackService.getTrackGroups("ranks"));
        assertTrue(console.messages().contains("Created track ranks."));
        assertTrue(console.messages().contains("Added default to the end of track ranks."));
        assertTrue(console.messages().contains("Inserted moderator into track ranks at position 2."));
        assertTrue(console.messages().contains("Moved staff on track ranks to position 2."));
        assertTrue(console.messages().contains("Track ranks (page 1/1):"));
        assertTrue(console.messages().contains("  #1 default"));
        assertTrue(console.messages().contains("  #2 staff"));
        assertTrue(console.messages().contains("  #3 moderator"));
        assertTrue(console.messages().contains("Track ranks:"));
        assertEquals("track.group.move", environment.auditLogService().listNewestFirst().getFirst().action());
    }

    /**
     * Confirms idempotent track mutations warn instead of mutating or auditing again.
     *
     * @throws CommandSyntaxException when command execution fails unexpectedly
     */
    @Test
    void trackMutationNoOpsWarnWithoutAudit() throws CommandSyntaxException {
        groupService.createGroup("staff");
        groupService.createGroup("admin");
        trackService.createTrack("ranks");
        trackService.setTrackGroups("ranks", List.of("default", "staff"));
        TestSource console = TestSource.console();

        dispatcher.execute("clutchperms track ranks create", console);
        dispatcher.execute("clutchperms track ranks rename Ranks", console);
        dispatcher.execute("clutchperms track ranks append admin", console);
        dispatcher.execute("clutchperms track ranks append admin", console);
        dispatcher.execute("clutchperms track ranks move admin 3", console);
        dispatcher.execute("clutchperms track ranks remove admin", console);
        dispatcher.execute("clutchperms track ranks remove admin", console);

        assertEquals(List.of("default", "staff"), trackService.getTrackGroups("ranks"));
        assertEquals(2, environment.auditLogService().listNewestFirst().size());
        assertMessageContains(console, "Track ranks already exists.");
        assertMessageContains(console, "Track ranks is already named ranks.");
        assertMessageContains(console, "Group admin is already on track ranks.");
        assertMessageContains(console, "Group admin is already at position 3 on track ranks.");
        assertMessageContains(console, "Group admin is already absent from track ranks.");
    }

    /**
     * Confirms deleting a track requires confirmation and undo restores the prior ordered definition.
     *
     * @throws CommandSyntaxException when command execution fails unexpectedly
     */
    @Test
    void trackDeleteUsesConfirmationAndUndo() throws CommandSyntaxException {
        groupService.createGroup("staff");
        trackService.createTrack("ranks");
        trackService.setTrackGroups("ranks", List.of("default", "staff"));
        TestSource console = TestSource.console();

        dispatcher.execute("clutchperms track ranks delete", console);
        assertTrue(trackService.hasTrack("ranks"));

        dispatcher.execute("clutchperms track ranks delete", console);

        assertFalse(trackService.hasTrack("ranks"));
        assertEquals(
                List.of("Destructive command confirmation required.", "Repeat this command within 30 seconds to confirm: /clutchperms track ranks delete", "Deleted track ranks."),
                console.messages());

        long deleteEntryId = environment.auditLogService().listNewestFirst().getFirst().id();
        console.messages().clear();
        console.commandMessages().clear();

        dispatcher.execute("clutchperms undo " + deleteEntryId, console);

        assertTrue(trackService.hasTrack("ranks"));
        assertEquals(List.of("default", "staff"), trackService.getTrackGroups("ranks"));
        assertTrue(console.messages().contains("Undid audit history entry " + deleteEntryId + "."));
    }

    /**
     * Confirms promote and demote commands follow ordered track rules for implicit default and explicit groups.
     *
     * @throws CommandSyntaxException when command execution fails unexpectedly
     */
    @Test
    void userTrackPromoteAndDemoteFollowOrdering() throws CommandSyntaxException {
        groupService.createGroup("staff");
        groupService.createGroup("admin");
        trackService.createTrack("ranks");
        trackService.setTrackGroups("ranks", List.of("default", "staff", "admin"));
        trackService.createTrack("staffonly");
        trackService.setTrackGroups("staffonly", List.of("staff", "admin"));
        TestSource console = TestSource.console();

        assertCommandFails("clutchperms user Target track demote ranks", console, "Track operation failed: user is already at the start of track ranks");

        dispatcher.execute("clutchperms user Target track promote ranks", console);
        assertEquals(Set.of("staff"), groupService.getSubjectGroups(TARGET_ID));

        dispatcher.execute("clutchperms user Target tracks", console);
        assertTrue(console.messages().contains("Tracks for Target (00000000-0000-0000-0000-000000000002) (page 1/1):"));
        assertTrue(console.messages().contains("  ranks: staff (#2)"));

        dispatcher.execute("clutchperms user Target track promote ranks", console);
        assertEquals(Set.of("admin"), groupService.getSubjectGroups(TARGET_ID));

        dispatcher.execute("clutchperms user Target track demote ranks", console);
        assertEquals(Set.of("staff"), groupService.getSubjectGroups(TARGET_ID));

        dispatcher.execute("clutchperms user Target track demote ranks", console);
        assertEquals(Set.of(), groupService.getSubjectGroups(TARGET_ID));

        dispatcher.execute("clutchperms user Target track promote staffonly", console);

        assertEquals(Set.of("staff"), groupService.getSubjectGroups(TARGET_ID));
        assertTrue(console.messages().contains("Promoted Target (00000000-0000-0000-0000-000000000002) on track ranks to staff."));
        assertTrue(console.messages().contains("Promoted Target (00000000-0000-0000-0000-000000000002) on track ranks to admin."));
        assertTrue(console.messages().contains("Demoted Target (00000000-0000-0000-0000-000000000002) on track ranks to staff."));
        assertTrue(console.messages().contains("Demoted Target (00000000-0000-0000-0000-000000000002) on track ranks to default."));
        assertTrue(console.messages().contains("Promoted Target (00000000-0000-0000-0000-000000000002) on track staffonly to staff."));
        assertEquals("user.track.promote", environment.auditLogService().listNewestFirst().getFirst().action());
    }

    /**
     * Confirms track promotion fails when the user matches multiple explicit groups on one track.
     */
    @Test
    void userTrackPromoteRejectsConflictingGroups() {
        groupService.createGroup("staff");
        groupService.createGroup("admin");
        trackService.createTrack("ranks");
        trackService.setTrackGroups("ranks", List.of("default", "staff", "admin"));
        groupService.addSubjectGroup(TARGET_ID, "staff");
        groupService.addSubjectGroup(TARGET_ID, "admin");
        TestSource console = TestSource.console();

        assertCommandFails("clutchperms user Target track promote ranks", console, "Track operation failed: user matches multiple explicit groups on track ranks: staff, admin");
        assertEquals(Set.of("staff", "admin"), groupService.getSubjectGroups(TARGET_ID));
    }

}
