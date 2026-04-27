package me.clutchy.clutchperms.common.command;

import java.nio.file.Path;
import java.util.Collection;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.UnaryOperator;

import me.clutchy.clutchperms.common.audit.AuditLogService;
import me.clutchy.clutchperms.common.audit.AuditLogServices;
import me.clutchy.clutchperms.common.config.ClutchPermsConfig;
import me.clutchy.clutchperms.common.display.DisplayResolver;
import me.clutchy.clutchperms.common.group.GroupService;
import me.clutchy.clutchperms.common.group.GroupServices;
import me.clutchy.clutchperms.common.node.MutablePermissionNodeRegistry;
import me.clutchy.clutchperms.common.node.PermissionNodeRegistries;
import me.clutchy.clutchperms.common.node.PermissionNodeRegistry;
import me.clutchy.clutchperms.common.permission.PermissionResolver;
import me.clutchy.clutchperms.common.permission.PermissionService;
import me.clutchy.clutchperms.common.permission.PermissionServices;
import me.clutchy.clutchperms.common.runtime.ScheduledBackupService;
import me.clutchy.clutchperms.common.runtime.ScheduledBackupStatus;
import me.clutchy.clutchperms.common.storage.SqliteDependencyMode;
import me.clutchy.clutchperms.common.storage.SqliteStore;
import me.clutchy.clutchperms.common.storage.StorageBackup;
import me.clutchy.clutchperms.common.storage.StorageBackupService;
import me.clutchy.clutchperms.common.storage.StorageFileKind;
import me.clutchy.clutchperms.common.subject.SubjectMetadataService;
import me.clutchy.clutchperms.common.subject.SubjectMetadataServices;

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
     * Returns the effective display resolver used by chat and display commands.
     *
     * @return active display resolver
     */
    default DisplayResolver displayResolver() {
        return new DisplayResolver(subjectMetadataService(), groupService());
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
     * Returns the active runtime config used by shared commands.
     *
     * @return active runtime config
     */
    default ClutchPermsConfig config() {
        return ClutchPermsConfig.defaults();
    }

    /**
     * Returns command audit history storage.
     *
     * @return audit log service
     */
    default AuditLogService auditLogService() {
        return AuditLogServices.inMemory();
    }

    /**
     * Updates the active runtime config and applies it through the platform storage lifecycle.
     *
     * @param updater config updater
     */
    default void updateConfig(UnaryOperator<ClutchPermsConfig> updater) {
        throw new UnsupportedOperationException("Config updates are not available for this command environment");
    }

    /**
     * Reloads persisted permissions, subject metadata, groups, and known nodes from platform storage.
     */
    default void reloadStorage() {
        throw new UnsupportedOperationException("Reload is not available for this command environment");
    }

    /**
     * Validates persisted permissions, subject metadata, groups, and known nodes from platform storage without applying them.
     */
    default void validateStorage() {
        throw new UnsupportedOperationException("Storage validation is not available for this command environment");
    }

    /**
     * Returns the backup service used by backup list and restore commands.
     *
     * @return active storage backup service
     */
    default StorageBackupService storageBackupService() {
        throw new UnsupportedOperationException("Storage backups are not available for this command environment");
    }

    /**
     * Returns scheduled backup runner status.
     *
     * @return scheduled backup status
     */
    default ScheduledBackupStatus scheduledBackupStatus() {
        return new ScheduledBackupStatus(config().backups().schedule().enabled(), config().backups().schedule().intervalMinutes(), config().backups().schedule().runOnStartup(),
                false, Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty());
    }

    /**
     * Returns the scheduled backup runner used by backup schedule commands.
     *
     * @return scheduled backup runner
     */
    default ScheduledBackupService scheduledBackupService() {
        throw new UnsupportedOperationException("Scheduled backups are not available for this command environment");
    }

    /**
     * Creates an immediate backup through the scheduler when available, so schedule status reflects run-now activity.
     *
     * @return created backup, or empty when the live database is missing
     */
    default Optional<StorageBackup> createScheduledBackupNow() {
        try {
            return scheduledBackupService().runNow();
        } catch (UnsupportedOperationException exception) {
            return storageBackupService().createBackup();
        }
    }

    /**
     * Validates one selected database backup file before it replaces live storage.
     *
     * @param kind selected storage file kind
     * @param backupFile selected backup path
     */
    default void validateBackup(StorageFileKind kind, Path backupFile) {
        if (Objects.requireNonNull(kind, "kind") != StorageFileKind.DATABASE) {
            throw new IllegalArgumentException("unsupported backup kind: " + kind.token());
        }
        try (SqliteStore store = SqliteStore.openExisting(Objects.requireNonNull(backupFile, "backupFile"), SqliteDependencyMode.ANY_VISIBLE)) {
            PermissionServices.sqlite(store);
            SubjectMetadataServices.sqlite(store);
            GroupServices.sqlite(store);
            PermissionNodeRegistries.sqlite(store);
        }
    }

    /**
     * Restores one selected database backup file.
     *
     * @param kind selected storage file kind
     * @param backupFileName selected backup filename
     */
    default void restoreBackup(StorageFileKind kind, String backupFileName) {
        storageBackupService().restoreBackup(kind, backupFileName, () -> {
            reloadStorage();
            refreshRuntimePermissions();
        });
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

    /**
     * Sends a styled command response to the platform source.
     *
     * @param source platform command source
     * @param message styled command message
     */
    default void sendMessage(S source, CommandMessage message) {
        sendMessage(source, message.plainText());
    }
}
