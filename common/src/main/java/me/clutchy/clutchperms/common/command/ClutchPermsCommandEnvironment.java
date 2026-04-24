package me.clutchy.clutchperms.common.command;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

import me.clutchy.clutchperms.common.group.GroupService;
import me.clutchy.clutchperms.common.node.MutablePermissionNodeRegistry;
import me.clutchy.clutchperms.common.node.PermissionNodeRegistries;
import me.clutchy.clutchperms.common.node.PermissionNodeRegistry;
import me.clutchy.clutchperms.common.permission.PermissionResolver;
import me.clutchy.clutchperms.common.permission.PermissionService;
import me.clutchy.clutchperms.common.subject.SubjectMetadataService;

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
     * Returns the group service mutated by command execution.
     *
     * @return active group service
     */
    GroupService groupService();

    /**
     * Returns the merged known permission node registry inspected by command execution.
     *
     * @return active known permission node registry
     */
    default PermissionNodeRegistry permissionNodeRegistry() {
        return PermissionNodeRegistries.builtIn();
    }

    /**
     * Returns the manual known permission node registry mutated by node commands.
     *
     * @return active manual known permission node registry
     */
    default MutablePermissionNodeRegistry manualPermissionNodeRegistry() {
        throw new UnsupportedOperationException("Manual permission node registry is not available for this command environment");
    }

    /**
     * Returns the effective permission resolver used by command authorization and check output.
     *
     * @return active permission resolver
     */
    default PermissionResolver permissionResolver() {
        return new PermissionResolver(permissionService(), groupService());
    }

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
     * Reloads persisted permissions, subject metadata, groups, and known nodes from platform storage.
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
