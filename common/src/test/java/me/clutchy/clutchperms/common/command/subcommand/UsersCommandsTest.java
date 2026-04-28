package me.clutchy.clutchperms.common.command;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.mojang.brigadier.exceptions.CommandSyntaxException;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Verifies shared Brigadier command behavior independent of a server platform.
 */
class UsersCommandsTest extends CommandTestBase {

    /**
     * Confirms the users list command reports an empty metadata store clearly.
     *
     * @throws CommandSyntaxException when command execution fails unexpectedly
     */
    @Test
    void usersListReportsNoKnownUsers() throws CommandSyntaxException {
        TestSource console = TestSource.console();

        dispatcher.execute("clutchperms users list", console);

        assertEquals(List.of("No known users."), console.messages());
    }

    /**
     * Confirms the users list command reports known users in stable name order.
     *
     * @throws CommandSyntaxException when command execution fails unexpectedly
     */
    @Test
    void usersListReportsKnownUsers() throws CommandSyntaxException {
        subjectMetadataService.recordSubject(TARGET_ID, "Zed", SECOND_SEEN);
        subjectMetadataService.recordSubject(SECOND_TARGET_ID, "Alpha", FIRST_SEEN);
        TestSource console = TestSource.console();

        dispatcher.execute("clutchperms users list", console);

        assertEquals(List.of("Known users (page 1/1):", "  Alpha (00000000-0000-0000-0000-000000000004, last seen 2026-04-24T12:00:00Z)",
                "  Zed (00000000-0000-0000-0000-000000000002, last seen 2026-04-24T13:00:00Z)"), console.messages());
    }

    /**
     * Confirms the users search command matches names case-insensitively.
     *
     * @throws CommandSyntaxException when command execution fails unexpectedly
     */
    @Test
    void usersSearchReportsMatchingKnownUsers() throws CommandSyntaxException {
        subjectMetadataService.recordSubject(TARGET_ID, "Target", FIRST_SEEN);
        subjectMetadataService.recordSubject(SECOND_TARGET_ID, "Other", SECOND_SEEN);
        TestSource console = TestSource.console();

        dispatcher.execute("clutchperms users search tar", console);

        assertEquals(List.of("Matched users (page 1/1):", "  Target (00000000-0000-0000-0000-000000000002, last seen 2026-04-24T12:00:00Z)"), console.messages());
    }

    /**
     * Confirms the users search command reports no-match searches clearly.
     *
     * @throws CommandSyntaxException when command execution fails unexpectedly
     */
    @Test
    void usersSearchReportsNoMatches() throws CommandSyntaxException {
        subjectMetadataService.recordSubject(TARGET_ID, "Target", FIRST_SEEN);
        TestSource console = TestSource.console();

        dispatcher.execute("clutchperms users search Missing", console);

        assertEquals(List.of("No users matched Missing."), console.messages());
    }

}
