package me.clutchy.clutchperms.common.command;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.Test;

import com.mojang.brigadier.exceptions.CommandSyntaxException;

import me.clutchy.clutchperms.common.permission.PermissionValue;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Verifies shared Brigadier command behavior independent of a server platform.
 */
class CommandTargetResolverTest extends CommandTestBase {

    /**
     * Confirms an exact online name wins before UUID parsing.
     *
     * @throws CommandSyntaxException when command execution fails unexpectedly
     */
    @Test
    void onlinePlayerTargetResolvesBeforeUuidParsing() throws CommandSyntaxException {
        String uuidLookingName = TARGET_ID.toString();
        environment.addOnlineSubject(uuidLookingName, UUID_NAMED_PLAYER_ID);
        TestSource console = TestSource.console();

        dispatcher.execute("clutchperms user " + uuidLookingName + " set example.node true", console);

        assertEquals(PermissionValue.UNSET, permissionService.getPermission(TARGET_ID, "example.node"));
        assertEquals(PermissionValue.TRUE, permissionService.getPermission(UUID_NAMED_PLAYER_ID, "example.node"));
    }

    /**
     * Confirms an exact online name wins before stored subject metadata.
     *
     * @throws CommandSyntaxException when command execution fails unexpectedly
     */
    @Test
    void onlinePlayerTargetResolvesBeforeStoredMetadata() throws CommandSyntaxException {
        subjectMetadataService.recordSubject(SECOND_TARGET_ID, "Target", FIRST_SEEN);
        TestSource console = TestSource.console();

        dispatcher.execute("clutchperms user Target set example.node true", console);

        assertEquals(PermissionValue.TRUE, permissionService.getPermission(TARGET_ID, "example.node"));
        assertEquals(PermissionValue.UNSET, permissionService.getPermission(SECOND_TARGET_ID, "example.node"));
    }

    /**
     * Confirms stored subject metadata names can be used as offline command targets.
     *
     * @throws CommandSyntaxException when command execution fails unexpectedly
     */
    @Test
    void offlineLastKnownNameTargetResolvesBeforeUuidParsing() throws CommandSyntaxException {
        subjectMetadataService.recordSubject(SECOND_TARGET_ID, "OfflineTarget", FIRST_SEEN);
        TestSource console = TestSource.console();

        dispatcher.execute("clutchperms user offlinetarget set example.node true", console);

        assertEquals(PermissionValue.TRUE, permissionService.getPermission(SECOND_TARGET_ID, "example.node"));
        assertEquals(List.of("Set example.node for OfflineTarget (00000000-0000-0000-0000-000000000004) to TRUE."), console.messages());
    }

    /**
     * Confirms exact cached offline names resolve synchronously and are recorded into subject metadata.
     *
     * @throws CommandSyntaxException when command execution fails unexpectedly
     */
    @Test
    void cachedOfflineNameTargetResolvesAndRecordsMetadata() throws CommandSyntaxException {
        environment.addCachedSubject("CachedTarget", SECOND_TARGET_ID);
        TestSource console = TestSource.console();

        dispatcher.execute("clutchperms user CachedTarget set example.cached true", console);

        assertEquals(PermissionValue.TRUE, permissionService.getPermission(SECOND_TARGET_ID, "example.cached"));
        assertEquals("CachedTarget", subjectMetadataService.getSubject(SECOND_TARGET_ID).orElseThrow().lastKnownName());
        assertEquals(List.of("Set example.cached for CachedTarget (00000000-0000-0000-0000-000000000004) to TRUE."), console.messages());
    }

    /**
     * Confirms unresolved exact offline names queue async lookup, then resume the original command after resolution.
     */
    @Test
    void asyncOfflineNameTargetQueuesAndResumesCommand() {
        CompletableFuture<Optional<CommandSubject>> lookup = environment.addAsyncSubjectLookup("RemoteTarget");
        TestSource console = TestSource.console();

        assertDoesNotThrow(() -> assertEquals(1, dispatcher.execute("clutchperms user RemoteTarget set example.remote true", console)));
        assertEquals(List.of("Resolving offline user target RemoteTarget..."), console.messages());
        assertEquals(PermissionValue.UNSET, permissionService.getPermission(SECOND_TARGET_ID, "example.remote"));

        lookup.complete(Optional.of(new CommandSubject(SECOND_TARGET_ID, "RemoteResolved")));

        assertEquals(PermissionValue.TRUE, permissionService.getPermission(SECOND_TARGET_ID, "example.remote"));
        assertEquals("RemoteResolved", subjectMetadataService.getSubject(SECOND_TARGET_ID).orElseThrow().lastKnownName());
        assertEquals(List.of("Resolving offline user target RemoteTarget...", "Resolved offline user target RemoteTarget as RemoteResolved (00000000-0000-0000-0000-000000000004).",
                "Set example.remote for RemoteResolved (00000000-0000-0000-0000-000000000004) to TRUE."), console.messages());
    }

    /**
     * Confirms async offline lookups report the existing unknown-target feedback when the platform cannot resolve the name.
     */
    @Test
    void asyncOfflineNameTargetReportsUnknownWhenLookupMisses() {
        CompletableFuture<Optional<CommandSubject>> lookup = environment.addAsyncSubjectLookup("MissingTarget");
        TestSource console = TestSource.console();

        assertDoesNotThrow(() -> assertEquals(1, dispatcher.execute("clutchperms user MissingTarget list", console)));
        assertEquals(List.of("Resolving offline user target MissingTarget..."), console.messages());

        lookup.complete(Optional.empty());

        assertMessageContains(console, "Unknown user target: MissingTarget");
        assertMessageContains(console, "Use an exact online name, resolvable offline name, stored last-known name, or UUID.");
        assertMessageContains(console, "Closest online players: Target");
    }

    /**
     * Confirms async offline lookup failures report a styled resolution error without mutating permissions.
     */
    @Test
    void asyncOfflineNameTargetReportsLookupFailure() {
        CompletableFuture<Optional<CommandSubject>> lookup = environment.addAsyncSubjectLookup("BrokenTarget");
        TestSource console = TestSource.console();

        assertDoesNotThrow(() -> assertEquals(1, dispatcher.execute("clutchperms user BrokenTarget set example.broken true", console)));
        lookup.completeExceptionally(new IllegalStateException("lookup blocked"));

        assertEquals(PermissionValue.UNSET, permissionService.getPermission(SECOND_TARGET_ID, "example.broken"));
        assertMessageContains(console, "Failed to resolve offline user target BrokenTarget: lookup blocked");
    }

    /**
     * Confirms destructive confirmations are armed only after async target resolution completes.
     */
    @Test
    void destructiveConfirmationStartsAfterAsyncTargetResolution() {
        permissionService.setPermission(SECOND_TARGET_ID, "example.clear", PermissionValue.TRUE);
        CompletableFuture<Optional<CommandSubject>> lookup = environment.addAsyncSubjectLookup("RemoteClear");
        TestSource console = TestSource.console();

        assertDoesNotThrow(() -> assertEquals(1, dispatcher.execute("clutchperms user RemoteClear clear-all", console)));
        assertEquals(List.of("Resolving offline user target RemoteClear..."), console.messages());
        assertEquals(PermissionValue.TRUE, permissionService.getPermission(SECOND_TARGET_ID, "example.clear"));

        lookup.complete(Optional.of(new CommandSubject(SECOND_TARGET_ID, "RemoteClear")));

        assertMessageContains(console, "Resolved offline user target RemoteClear as RemoteClear (00000000-0000-0000-0000-000000000004).");
        assertMessageContains(console, "Destructive command confirmation required.");
        assertMessageContains(console, "Repeat this command within 30 seconds to confirm: /clutchperms user RemoteClear clear-all");
        assertEquals(PermissionValue.TRUE, permissionService.getPermission(SECOND_TARGET_ID, "example.clear"));

        assertDoesNotThrow(() -> assertEquals(1, dispatcher.execute("clutchperms user RemoteClear clear-all", console)));
        assertEquals(PermissionValue.UNSET, permissionService.getPermission(SECOND_TARGET_ID, "example.clear"));
    }

    /**
     * Confirms UUID targets use stored metadata names in command feedback when available.
     *
     * @throws CommandSyntaxException when command execution fails unexpectedly
     */
    @Test
    void uuidTargetUsesLastKnownNameInCommandFeedback() throws CommandSyntaxException {
        subjectMetadataService.recordSubject(SECOND_TARGET_ID, "OfflineTarget", FIRST_SEEN);
        TestSource console = TestSource.console();

        dispatcher.execute("clutchperms user " + SECOND_TARGET_ID + " set example.node true", console);
        dispatcher.execute("clutchperms user " + SECOND_TARGET_ID + " get example.node", console);

        assertEquals(List.of("Set example.node for OfflineTarget (00000000-0000-0000-0000-000000000004) to TRUE.",
                "OfflineTarget (00000000-0000-0000-0000-000000000004) has example.node = TRUE."), console.messages());
    }

    /**
     * Confirms ambiguous stored subject names fail instead of choosing an arbitrary UUID.
     */
    @Test
    void ambiguousLastKnownNameTargetFails() {
        subjectMetadataService.recordSubject(TARGET_ID, "Duplicate", FIRST_SEEN);
        subjectMetadataService.recordSubject(SECOND_TARGET_ID, "duplicate", SECOND_SEEN);
        TestSource console = TestSource.console();

        assertCommandFails("clutchperms user Duplicate list", console, "Ambiguous known user: Duplicate");

        assertMessageContains(console, "More than one stored subject matches Duplicate.");
        assertMessageContains(console, "  Duplicate (00000000-0000-0000-0000-000000000002, last seen 2026-04-24T12:00:00Z)");
        assertMessageContains(console, "  duplicate (00000000-0000-0000-0000-000000000004, last seen 2026-04-24T13:00:00Z)");
    }

    /**
     * Confirms unknown user targets show close online and stored-name matches.
     */
    @Test
    void unknownUserTargetSuggestsClosestOnlineAndStoredMatches() {
        subjectMetadataService.recordSubject(SECOND_TARGET_ID, "Targe", FIRST_SEEN);
        TestSource console = TestSource.console();

        assertCommandFails("clutchperms user Targt list", console, "Unknown user target: Targt");

        assertMessageContains(console, "Use an exact online name, resolvable offline name, stored last-known name, or UUID.");
        assertMessageContains(console, "Closest online players: Target");
        assertMessageContains(console, "Closest known users: Targe (00000000-0000-0000-0000-000000000004, last seen 2026-04-24T12:00:00Z)");
    }

    /**
     * Confirms info commands reuse existing closest-match feedback for unknown targets.
     */
    @Test
    void infoCommandsUseExistingClosestMatchFeedback() {
        groupService.createGroup("staff");
        TestSource console = TestSource.console();

        assertCommandFails("clutchperms user Targt info", console, "Unknown user target: Targt");
        assertMessageContains(console, "Closest online players: Target");

        assertCommandFails("clutchperms group staf info", console, "Unknown group: staf");
        assertMessageContains(console, "Closest groups: staff");
    }

    /**
     * Confirms bulk clear commands reuse existing closest-match feedback for unknown targets.
     */
    @Test
    void bulkClearCommandsUseExistingClosestMatchFeedback() {
        groupService.createGroup("staff");
        TestSource console = TestSource.console();

        assertCommandFails("clutchperms user Targt clear-all", console, "Unknown user target: Targt");
        assertMessageContains(console, "Closest online players: Target");

        assertCommandFails("clutchperms group staf clear-all", console, "Unknown group: staf");
        assertMessageContains(console, "Closest groups: staff");
    }

    /**
     * Confirms unknown user targets without close matches suggest the users search command.
     */
    @Test
    void unknownUserTargetWithoutMatchesSuggestsSearch() {
        TestSource console = TestSource.console();

        assertCommandFails("clutchperms user CompletelyMissing list", console, "Unknown user target: CompletelyMissing");

        assertMessageContains(console, "No close user matches.");
        assertMessageContains(console, "  /clutchperms users search CompletelyMissing");
    }

    /**
     * Confirms user target suggestions include online players and stored last-known names.
     */
    @Test
    void userTargetSuggestionsIncludeOnlineAndStoredNames() {
        environment.addOnlineSubject("Builder", UUID_NAMED_PLAYER_ID);
        subjectMetadataService.recordSubject(SECOND_TARGET_ID, "OfflineTarget", FIRST_SEEN);

        assertEquals(List.of("Builder", "OfflineTarget", "Target"), suggestionTexts("clutchperms user "));
    }

    /**
     * Confirms user target suggestions filter by typed prefix without requiring exact casing.
     */
    @Test
    void userTargetSuggestionsFilterByTypedPrefixCaseInsensitively() {
        environment.addOnlineSubject("Operator", UUID_NAMED_PLAYER_ID);
        subjectMetadataService.recordSubject(SECOND_TARGET_ID, "OfflineTarget", FIRST_SEEN);

        assertEquals(List.of("OfflineTarget", "Operator"), suggestionTexts("clutchperms user o"));
    }

    /**
     * Confirms user target suggestions are stable and do not repeat the exact same name.
     */
    @Test
    void userTargetSuggestionsAreDeterministicAndAvoidExactDuplicates() {
        environment.addOnlineSubject("Alpha", UUID_NAMED_PLAYER_ID);
        subjectMetadataService.recordSubject(TARGET_ID, "Target", FIRST_SEEN);
        subjectMetadataService.recordSubject(SECOND_TARGET_ID, "alpha", SECOND_SEEN);

        assertEquals(List.of("Alpha", "alpha"), suggestionTexts("clutchperms user a"));
        assertEquals(List.of("Target"), suggestionTexts("clutchperms user t"));
    }

    /**
     * Confirms remotely resolved names are not suggested until one command resolves and records them locally.
     */
    @Test
    void remoteOnlyNamesAreSuggestedOnlyAfterResolutionRecordsMetadata() {
        CompletableFuture<Optional<CommandSubject>> lookup = environment.addAsyncSubjectLookup("RemoteTarget");
        TestSource console = TestSource.console();

        assertEquals(List.of(), suggestionTexts("clutchperms user rem"));
        assertDoesNotThrow(() -> assertEquals(1, dispatcher.execute("clutchperms user RemoteTarget info", console)));

        lookup.complete(Optional.of(new CommandSubject(SECOND_TARGET_ID, "RemoteResolved")));

        assertEquals(List.of("RemoteResolved"), suggestionTexts("clutchperms user rem"));
    }

    /**
     * Confirms unresolved targets fall back to broad add suggestions and empty remove suggestions.
     */
    @Test
    void userGroupSuggestionsHandleUnresolvedTargets() {
        groupService.createGroup("staff");
        subjectMetadataService.recordSubject(TARGET_ID, "Ambiguous", FIRST_SEEN);
        subjectMetadataService.recordSubject(SECOND_TARGET_ID, "ambiguous", SECOND_SEEN);

        assertEquals(List.of("op", "staff"), suggestionTexts("clutchperms user Missing group add "));
        assertEquals(List.of(), suggestionTexts("clutchperms user Missing group remove "));
        assertEquals(List.of("op", "staff"), suggestionTexts("clutchperms user Ambiguous group add "));
        assertEquals(List.of(), suggestionTexts("clutchperms user Ambiguous group remove "));
    }

}
