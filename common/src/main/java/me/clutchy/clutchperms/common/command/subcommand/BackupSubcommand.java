package me.clutchy.clutchperms.common.command.subcommand;

import java.util.Comparator;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;

import me.clutchy.clutchperms.common.command.ClutchPermsCommandEnvironment;
import me.clutchy.clutchperms.common.command.CommandArguments;
import me.clutchy.clutchperms.common.permission.PermissionNodes;

/**
 * Builds the `/clutchperms backup` command branch.
 */
public final class BackupSubcommand {

    /**
     * Handlers for backup command actions.
     *
     * @param <S> platform command source type
     */
    public interface Handlers<S> {

        int usage(CommandContext<S> context) throws CommandSyntaxException;

        int list(CommandContext<S> context) throws CommandSyntaxException;

        int create(CommandContext<S> context) throws CommandSyntaxException;

        int scheduleStatus(CommandContext<S> context) throws CommandSyntaxException;

        int scheduleEnable(CommandContext<S> context) throws CommandSyntaxException;

        int scheduleDisable(CommandContext<S> context) throws CommandSyntaxException;

        int scheduleIntervalUsage(CommandContext<S> context) throws CommandSyntaxException;

        int scheduleInterval(CommandContext<S> context) throws CommandSyntaxException;

        int scheduleRunNow(CommandContext<S> context) throws CommandSyntaxException;

        int scheduleUnknownUsage(CommandContext<S> context) throws CommandSyntaxException;

        int listPageUsage(CommandContext<S> context) throws CommandSyntaxException;

        int restoreFileUsage(CommandContext<S> context) throws CommandSyntaxException;

        int restore(CommandContext<S> context) throws CommandSyntaxException;

        int unknownUsage(CommandContext<S> context) throws CommandSyntaxException;
    }

    public static <S> LiteralArgumentBuilder<S> builder(ClutchPermsCommandEnvironment<S> environment, AuthorizedCommand<S> authorized, Handlers<S> handlers) {
        return authorized.branch("backup", PermissionNodes.ADMIN_BACKUP_LIST, PermissionNodes.ADMIN_BACKUP_CREATE, PermissionNodes.ADMIN_BACKUP_RESTORE)
                .executes(context -> authorized.run(context, PermissionNodes.ADMIN_BACKUP_LIST, handlers::usage))
                .then(authorized.literal("create", PermissionNodes.ADMIN_BACKUP_CREATE)
                        .executes(context -> authorized.run(context, PermissionNodes.ADMIN_BACKUP_CREATE, handlers::create)))
                .then(authorized.branch("schedule", PermissionNodes.ADMIN_BACKUP_LIST, PermissionNodes.ADMIN_BACKUP_CREATE, PermissionNodes.ADMIN_CONFIG_SET)
                        .executes(context -> authorized.run(context, PermissionNodes.ADMIN_BACKUP_LIST, handlers::scheduleStatus))
                        .then(authorized.literal("status", PermissionNodes.ADMIN_BACKUP_LIST)
                                .executes(context -> authorized.run(context, PermissionNodes.ADMIN_BACKUP_LIST, handlers::scheduleStatus)))
                        .then(authorized.literal("enable", PermissionNodes.ADMIN_CONFIG_SET)
                                .executes(context -> authorized.run(context, PermissionNodes.ADMIN_CONFIG_SET, handlers::scheduleEnable)))
                        .then(authorized.literal("disable", PermissionNodes.ADMIN_CONFIG_SET)
                                .executes(context -> authorized.run(context, PermissionNodes.ADMIN_CONFIG_SET, handlers::scheduleDisable)))
                        .then(authorized.literal("interval", PermissionNodes.ADMIN_CONFIG_SET)
                                .executes(context -> authorized.run(context, PermissionNodes.ADMIN_CONFIG_SET, handlers::scheduleIntervalUsage))
                                .then(BackupSubcommand.<S>intervalArgument()
                                        .executes(context -> authorized.run(context, PermissionNodes.ADMIN_CONFIG_SET, handlers::scheduleInterval))))
                        .then(authorized.literal("run-now", PermissionNodes.ADMIN_BACKUP_CREATE)
                                .executes(context -> authorized.run(context, PermissionNodes.ADMIN_BACKUP_CREATE, handlers::scheduleRunNow)))
                        .then(authorized.requires(CommandArguments.<S>unknown(), PermissionNodes.ADMIN_BACKUP_LIST)
                                .executes(context -> authorized.run(context, PermissionNodes.ADMIN_BACKUP_LIST, handlers::scheduleUnknownUsage))))
                .then(authorized.literal("list", PermissionNodes.ADMIN_BACKUP_LIST).executes(context -> authorized.run(context, PermissionNodes.ADMIN_BACKUP_LIST, handlers::list))
                        .then(authorized.literal("page", PermissionNodes.ADMIN_BACKUP_LIST)
                                .executes(context -> authorized.run(context, PermissionNodes.ADMIN_BACKUP_LIST, handlers::listPageUsage))
                                .then(BackupSubcommand.<S>pageArgument().executes(context -> authorized.run(context, PermissionNodes.ADMIN_BACKUP_LIST, handlers::list)))))
                .then(authorized.literal("restore", PermissionNodes.ADMIN_BACKUP_RESTORE)
                        .executes(context -> authorized.run(context, PermissionNodes.ADMIN_BACKUP_RESTORE, handlers::restoreFileUsage))
                        .then(backupFileArgument(environment).executes(context -> authorized.run(context, PermissionNodes.ADMIN_BACKUP_RESTORE, handlers::restore))))
                .then(authorized.requires(CommandArguments.<S>unknown(), PermissionNodes.ADMIN_BACKUP_LIST)
                        .executes(context -> authorized.run(context, PermissionNodes.ADMIN_BACKUP_LIST, handlers::unknownUsage)));
    }

    private static <S> RequiredArgumentBuilder<S, String> backupFileArgument(ClutchPermsCommandEnvironment<S> environment) {
        return RequiredArgumentBuilder.<S, String>argument(CommandArguments.BACKUP_FILE, StringArgumentType.word())
                .suggests((context, builder) -> suggestBackupFiles(environment, context, builder));
    }

    private static <S> RequiredArgumentBuilder<S, String> pageArgument() {
        return RequiredArgumentBuilder.argument(CommandArguments.PAGE, StringArgumentType.word());
    }

    private static <S> RequiredArgumentBuilder<S, String> intervalArgument() {
        return RequiredArgumentBuilder.argument(CommandArguments.CONFIG_VALUE, StringArgumentType.word());
    }

    private static <S> CompletableFuture<Suggestions> suggestBackupFiles(ClutchPermsCommandEnvironment<S> environment, CommandContext<S> context, SuggestionsBuilder builder) {
        String remaining = builder.getRemainingLowerCase();
        try {
            environment.storageBackupService().listBackups().values().stream().flatMap(java.util.Collection::stream).map(backup -> backup.fileName())
                    .sorted(Comparator.naturalOrder()).filter(fileName -> fileName.toLowerCase(Locale.ROOT).startsWith(remaining)).forEach(builder::suggest);
        } catch (RuntimeException exception) {
            // Backup filename suggestions depend on a readable backup directory.
        }
        return builder.buildFuture();
    }

    private BackupSubcommand() {
    }
}
