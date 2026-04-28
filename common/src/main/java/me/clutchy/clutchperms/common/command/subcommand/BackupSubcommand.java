package me.clutchy.clutchperms.common.command;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;

import me.clutchy.clutchperms.common.permission.PermissionNodes;
import me.clutchy.clutchperms.common.runtime.ScheduledBackupStatus;
import me.clutchy.clutchperms.common.storage.PermissionStorageException;
import me.clutchy.clutchperms.common.storage.StorageBackup;
import me.clutchy.clutchperms.common.storage.StorageFileKind;

final class BackupSubcommand<S> {

    private final CommandSupport<S> support;

    private final ClutchPermsCommandEnvironment<S> environment;

    BackupSubcommand(CommandSupport<S> support) {
        this.support = support;
        this.environment = support.environment();
    }

    LiteralArgumentBuilder<S> builder() {
        return LiteralArgumentBuilder.<S>literal("backup")
                .requires(source -> support.canUseAny(source, PermissionNodes.ADMIN_BACKUP_LIST, PermissionNodes.ADMIN_BACKUP_CREATE, PermissionNodes.ADMIN_BACKUP_RESTORE))
                .executes(context -> support.executeAuthorized(context, PermissionNodes.ADMIN_BACKUP_LIST, ignored -> usage(context)))
                .then(LiteralArgumentBuilder.<S>literal("create").requires(source -> support.canUse(source, PermissionNodes.ADMIN_BACKUP_CREATE))
                        .executes(context -> support.executeAuthorized(context, PermissionNodes.ADMIN_BACKUP_CREATE, ignored -> create(context))))
                .then(LiteralArgumentBuilder.<S>literal("schedule")
                        .requires(source -> support.canUseAny(source, PermissionNodes.ADMIN_BACKUP_LIST, PermissionNodes.ADMIN_BACKUP_CREATE, PermissionNodes.ADMIN_CONFIG_SET))
                        .executes(context -> support.executeAuthorized(context, PermissionNodes.ADMIN_BACKUP_LIST, ignored -> scheduleStatus(context)))
                        .then(LiteralArgumentBuilder.<S>literal("status").requires(source -> support.canUse(source, PermissionNodes.ADMIN_BACKUP_LIST))
                                .executes(context -> support.executeAuthorized(context, PermissionNodes.ADMIN_BACKUP_LIST, ignored -> scheduleStatus(context))))
                        .then(LiteralArgumentBuilder.<S>literal("enable").requires(source -> support.canUse(source, PermissionNodes.ADMIN_CONFIG_SET))
                                .executes(context -> support.executeAuthorized(context, PermissionNodes.ADMIN_CONFIG_SET, ignored -> scheduleEnable(context))))
                        .then(LiteralArgumentBuilder.<S>literal("disable").requires(source -> support.canUse(source, PermissionNodes.ADMIN_CONFIG_SET))
                                .executes(context -> support.executeAuthorized(context, PermissionNodes.ADMIN_CONFIG_SET, ignored -> scheduleDisable(context))))
                        .then(LiteralArgumentBuilder.<S>literal("interval").requires(source -> support.canUse(source, PermissionNodes.ADMIN_CONFIG_SET))
                                .executes(context -> support.executeAuthorized(context, PermissionNodes.ADMIN_CONFIG_SET, ignored -> scheduleIntervalUsage(context)))
                                .then(RequiredArgumentBuilder.<S, String>argument(CommandArguments.CONFIG_VALUE, StringArgumentType.word())
                                        .executes(context -> support.executeAuthorized(context, PermissionNodes.ADMIN_CONFIG_SET, ignored -> scheduleInterval(context)))))
                        .then(LiteralArgumentBuilder.<S>literal("run-now").requires(source -> support.canUse(source, PermissionNodes.ADMIN_BACKUP_CREATE))
                                .executes(context -> support.executeAuthorized(context, PermissionNodes.ADMIN_BACKUP_CREATE, ignored -> scheduleRunNow(context))))
                        .then(RequiredArgumentBuilder.<S, String>argument(CommandArguments.UNKNOWN, StringArgumentType.word())
                                .requires(source -> support.canUse(source, PermissionNodes.ADMIN_BACKUP_LIST))
                                .executes(context -> support.executeAuthorized(context, PermissionNodes.ADMIN_BACKUP_LIST, ignored -> scheduleUnknownUsage(context)))))
                .then(LiteralArgumentBuilder.<S>literal("list").requires(source -> support.canUse(source, PermissionNodes.ADMIN_BACKUP_LIST))
                        .executes(context -> support.executeAuthorized(context, PermissionNodes.ADMIN_BACKUP_LIST, ignored -> list(context)))
                        .then(LiteralArgumentBuilder.<S>literal("page")
                                .executes(context -> support.executeAuthorized(context, PermissionNodes.ADMIN_BACKUP_LIST, ignored -> listPageUsage(context)))
                                .then(RequiredArgumentBuilder.<S, String>argument(CommandArguments.PAGE, StringArgumentType.word())
                                        .executes(context -> support.executeAuthorized(context, PermissionNodes.ADMIN_BACKUP_LIST, ignored -> list(context))))))
                .then(LiteralArgumentBuilder.<S>literal("restore").requires(source -> support.canUse(source, PermissionNodes.ADMIN_BACKUP_RESTORE))
                        .executes(context -> support.executeAuthorized(context, PermissionNodes.ADMIN_BACKUP_RESTORE, ignored -> restoreFileUsage(context)))
                        .then(RequiredArgumentBuilder.<S, String>argument(CommandArguments.BACKUP_FILE, StringArgumentType.word()).suggests(this::suggestBackupFiles)
                                .executes(context -> support.executeAuthorized(context, PermissionNodes.ADMIN_BACKUP_RESTORE, ignored -> restore(context)))))
                .then(RequiredArgumentBuilder.<S, String>argument(CommandArguments.UNKNOWN, StringArgumentType.word())
                        .requires(source -> support.canUse(source, PermissionNodes.ADMIN_BACKUP_LIST))
                        .executes(context -> support.executeAuthorized(context, PermissionNodes.ADMIN_BACKUP_LIST, ignored -> unknownUsage(context))));
    }

    private int usage(CommandContext<S> context) {
        return support.sendUsage(context, "Missing backup command.", "Database backups can be created, listed, or restored by file name.", CommandCatalogs.backupUsages());
    }

    private int list(CommandContext<S> context) throws CommandSyntaxException {
        try {
            List<CommandPaging.PagedRow> rows = environment.storageBackupService().listBackups(StorageFileKind.DATABASE).stream()
                    .map(backup -> support.formatting().backupRow(support.currentRootLiteral(context), backup, backup.fileName())).toList();
            if (rows.isEmpty()) {
                environment.sendMessage(context.getSource(), CommandLang.backupsEmpty());
                return Command.SINGLE_SUCCESS;
            }
            support.paging().sendPagedRows(context, "Backups", rows, "backup list page");
            return Command.SINGLE_SUCCESS;
        } catch (RuntimeException exception) {
            throw support.backupOperationFailed(exception);
        }
    }

    private int create(CommandContext<S> context) throws CommandSyntaxException {
        try {
            StorageBackup backup = environment.storageBackupService().createBackup()
                    .orElseThrow(() -> new PermissionStorageException("Cannot create database backup because database.db does not exist"));
            environment.sendMessage(context.getSource(), CommandLang.backupCreated(backup.fileName()));
            return Command.SINGLE_SUCCESS;
        } catch (RuntimeException exception) {
            throw support.backupOperationFailed(exception);
        }
    }

    private int scheduleStatus(CommandContext<S> context) throws CommandSyntaxException {
        try {
            ScheduledBackupStatus status = environment.scheduledBackupStatus();
            environment.sendMessage(context.getSource(), CommandLang.backupScheduleHeader());
            environment.sendMessage(context.getSource(), CommandLang.backupScheduleEnabled(status.enabled(), status.running()));
            environment.sendMessage(context.getSource(), CommandLang.backupScheduleInterval(status.intervalMinutes()));
            environment.sendMessage(context.getSource(), CommandLang.backupScheduleRunOnStartup(status.runOnStartup()));
            environment.sendMessage(context.getSource(), CommandLang.backupScheduleNextRun(status.nextRun().map(Instant::toString).orElse("none")));
            environment.sendMessage(context.getSource(),
                    CommandLang.backupScheduleLastSuccess(status.lastSuccess().map(Instant::toString).orElse("none"), status.lastBackupFile().orElse("none")));
            environment.sendMessage(context.getSource(),
                    CommandLang.backupScheduleLastFailure(status.lastFailure().map(Instant::toString).orElse("none"), status.lastFailureMessage().orElse("none")));
            return Command.SINGLE_SUCCESS;
        } catch (RuntimeException exception) {
            throw support.backupOperationFailed(exception);
        }
    }

    private int scheduleEnable(CommandContext<S> context) throws CommandSyntaxException {
        return setScheduleEnabled(context, true);
    }

    private int scheduleDisable(CommandContext<S> context) throws CommandSyntaxException {
        return setScheduleEnabled(context, false);
    }

    private int scheduleIntervalUsage(CommandContext<S> context) {
        return support.sendUsage(context, "Missing interval minutes.", "Choose the number of minutes between scheduled database backups.",
                List.of("backup schedule interval <minutes>"));
    }

    private int scheduleInterval(CommandContext<S> context) throws CommandSyntaxException {
        CommandCatalogs.ConfigEntry entry = CommandCatalogs.configEntry("backups.schedule.intervalMinutes");
        String rawValue = StringArgumentType.getString(context, CommandArguments.CONFIG_VALUE);
        String newValue = support.parseConfigValue(context, entry, rawValue);
        return setConfigValue(context, entry, newValue);
    }

    private int scheduleRunNow(CommandContext<S> context) throws CommandSyntaxException {
        try {
            StorageBackup backup = environment.createScheduledBackupNow()
                    .orElseThrow(() -> new PermissionStorageException("Cannot create database backup because database.db does not exist"));
            environment.sendMessage(context.getSource(), CommandLang.backupCreated(backup.fileName()));
            return Command.SINGLE_SUCCESS;
        } catch (RuntimeException exception) {
            throw support.backupOperationFailed(exception);
        }
    }

    private int scheduleUnknownUsage(CommandContext<S> context) {
        return support.sendUsage(context, "Unknown backup schedule command: " + StringArgumentType.getString(context, CommandArguments.UNKNOWN),
                "Scheduled backups can be inspected, toggled, re-timed, or run immediately.", CommandCatalogs.backupScheduleUsages());
    }

    private int listPageUsage(CommandContext<S> context) {
        return support.sendUsage(context, "Missing backup page number.", "Choose a result page number.", List.of("backup list page <page>"));
    }

    private int restoreFileUsage(CommandContext<S> context) {
        return support.sendUsage(context, "Missing backup file.", "Pick a database backup file.", List.of("backup restore <backup-file>"));
    }

    private int restore(CommandContext<S> context) throws CommandSyntaxException {
        String backupFileName = StringArgumentType.getString(context, CommandArguments.BACKUP_FILE);
        List<StorageBackup> backups;
        try {
            backups = environment.storageBackupService().listBackups(StorageFileKind.DATABASE);
        } catch (RuntimeException exception) {
            throw support.backupOperationFailed(exception);
        }
        StorageBackup backup = support.requireKnownBackupFile(context, backupFileName, backups);
        support.validateBackup(StorageFileKind.DATABASE, backup);
        if (!support.audit().confirmDestructiveCommand(context, support.audit().confirmationOperation("backup-restore", backup.fileName()))) {
            return Command.SINGLE_SUCCESS;
        }
        try {
            environment.restoreBackup(StorageFileKind.DATABASE, backupFileName);
        } catch (RuntimeException exception) {
            throw support.backupOperationFailed(exception);
        }

        environment.sendMessage(context.getSource(), CommandLang.backupRestored(backupFileName));
        return Command.SINGLE_SUCCESS;
    }

    private int unknownUsage(CommandContext<S> context) {
        return support.sendUsage(context, "Unknown backup command: " + StringArgumentType.getString(context, CommandArguments.UNKNOWN),
                "Backup supports create, list, and restore commands.", CommandCatalogs.backupUsages());
    }

    private CompletableFuture<Suggestions> suggestBackupFiles(CommandContext<S> context, SuggestionsBuilder builder) {
        String remaining = builder.getRemainingLowerCase();
        try {
            environment.storageBackupService().listBackups().values().stream().flatMap(java.util.Collection::stream).map(StorageBackup::fileName).sorted(Comparator.naturalOrder())
                    .filter(fileName -> fileName.toLowerCase(Locale.ROOT).startsWith(remaining)).forEach(builder::suggest);
        } catch (RuntimeException exception) {
            // Backup filename suggestions depend on a readable backup directory.
        }
        return builder.buildFuture();
    }

    private int setScheduleEnabled(CommandContext<S> context, boolean enabled) throws CommandSyntaxException {
        CommandCatalogs.ConfigEntry entry = CommandCatalogs.configEntry("backups.schedule.enabled");
        return setConfigValue(context, entry, Boolean.toString(enabled));
    }

    private int setConfigValue(CommandContext<S> context, CommandCatalogs.ConfigEntry entry, String newValue) throws CommandSyntaxException {
        String oldValue = entry.value(environment.config());
        if (oldValue.equals(newValue)) {
            environment.sendMessage(context.getSource(), CommandLang.configAlreadySet(entry.key(), oldValue));
            return Command.SINGLE_SUCCESS;
        }
        String beforeJson = support.audit().configSnapshot(environment.config());
        try {
            environment.updateConfig(config -> entry.withValue(config, newValue));
            environment.refreshRuntimePermissions();
        } catch (RuntimeException exception) {
            throw support.configOperationFailed(exception);
        }
        support.audit().recordAudit(context, "config.set", "config", entry.key(), entry.key(), beforeJson, support.audit().configSnapshot(environment.config()), true);
        environment.sendMessage(context.getSource(), CommandLang.configUpdated(entry.key(), oldValue, newValue));
        return Command.SINGLE_SUCCESS;
    }
}
