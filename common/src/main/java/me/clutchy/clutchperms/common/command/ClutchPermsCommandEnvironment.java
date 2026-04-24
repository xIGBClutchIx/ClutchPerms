package me.clutchy.clutchperms.common.command;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

import me.clutchy.clutchperms.common.PermissionService;
import me.clutchy.clutchperms.common.SubjectMetadataService;

/**
 * Adapts platform-specific Brigadier command sources to the shared ClutchPerms command tree.
 *
 * @param <S> platform command source type
 */
public interface ClutchPermsCommandEnvironment<S> {

    /**
     * Returns the permission service mutated by command execution.
     *
     * @return active permission service
     */
    PermissionService permissionService();

    /**
     * Returns the subject metadata service inspected by command execution.
     *
     * @return active subject metadata service
     */
    SubjectMetadataService subjectMetadataService();

    /**
     * Returns platform-provided diagnostics for the status command.
     *
     * @return active status diagnostics
     */
    CommandStatusDiagnostics statusDiagnostics();

    /**
     * Reloads persisted permissions and subject metadata from platform storage.
     */
    default void reloadStorage() {
        throw new UnsupportedOperationException("Reload is not available for this command environment");
    }

    /**
     * Refreshes every online runtime permission subject after storage reload.
     */
    default void refreshRuntimePermissions() {
    }

    /**
     * Classifies a platform source for command authorization.
     *
     * @param source platform command source
     * @return command source kind
     */
    CommandSourceKind sourceKind(S source);

    /**
     * Returns the player subject UUID for player command sources.
     *
     * @param source platform command source
     * @return player UUID, or empty for non-player sources
     */
    Optional<UUID> sourceSubjectId(S source);

    /**
     * Resolves an exact online player name to a permission subject.
     *
     * @param source platform command source
     * @param target command target text
     * @return resolved online subject, or empty when the name is not online
     */
    Optional<CommandSubject> findOnlineSubject(S source, String target);

    /**
     * Lists exact online subject names for command suggestions.
     *
     * @param source platform command source
     * @return online subject names
     */
    Collection<String> onlineSubjectNames(S source);

    /**
     * Sends a plain command response to the platform source.
     *
     * @param source platform command source
     * @param message plain text message
     */
    void sendMessage(S source, String message);
}
