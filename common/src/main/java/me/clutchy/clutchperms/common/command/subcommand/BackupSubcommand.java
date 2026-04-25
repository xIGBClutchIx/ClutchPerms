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
import me.clutchy.clutchperms.common.storage.StorageFileKind;

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

        int restoreKindUsage(CommandContext<S> context) throws CommandSyntaxException;

        int restoreFileUsage(CommandContext<S> context) throws CommandSyntaxException;

        int restore(CommandContext<S> context) throws CommandSyntaxException;

        int unknownUsage(CommandContext<S> context) throws CommandSyntaxException;
    }

    public static <S> LiteralArgumentBuilder<S> builder(ClutchPermsCommandEnvironment<S> environment, AuthorizedCommand<S> authorized, Handlers<S> handlers) {
        return LiteralArgumentBuilder.<S>literal("backup").executes(context -> authorized.run(context, PermissionNodes.ADMIN_BACKUP_LIST, handlers::usage))
                .then(LiteralArgumentBuilder.<S>literal("list").executes(context -> authorized.run(context, PermissionNodes.ADMIN_BACKUP_LIST, handlers::list))
                        .then(backupKindArgument(environment).executes(context -> authorized.run(context, PermissionNodes.ADMIN_BACKUP_LIST, handlers::list))))
                .then(LiteralArgumentBuilder.<S>literal("restore").executes(context -> authorized.run(context, PermissionNodes.ADMIN_BACKUP_RESTORE, handlers::restoreKindUsage))
                        .then(backupKindArgument(environment).executes(context -> authorized.run(context, PermissionNodes.ADMIN_BACKUP_RESTORE, handlers::restoreFileUsage))
                                .then(backupFileArgument(environment).executes(context -> authorized.run(context, PermissionNodes.ADMIN_BACKUP_RESTORE, handlers::restore)))))
                .then(CommandArguments.<S>unknown().executes(context -> authorized.run(context, PermissionNodes.ADMIN_BACKUP_LIST, handlers::unknownUsage)));
    }

    private static <S> RequiredArgumentBuilder<S, String> backupKindArgument(ClutchPermsCommandEnvironment<S> environment) {
        return RequiredArgumentBuilder.<S, String>argument(CommandArguments.BACKUP_KIND, StringArgumentType.word())
                .suggests((context, builder) -> suggestBackupKinds(environment, builder));
    }

    private static <S> RequiredArgumentBuilder<S, String> backupFileArgument(ClutchPermsCommandEnvironment<S> environment) {
        return RequiredArgumentBuilder.<S, String>argument(CommandArguments.BACKUP_FILE, StringArgumentType.word())
                .suggests((context, builder) -> suggestBackupFiles(environment, context, builder));
    }

    private static <S> CompletableFuture<Suggestions> suggestBackupKinds(ClutchPermsCommandEnvironment<S> environment, SuggestionsBuilder builder) {
        String remaining = builder.getRemainingLowerCase();
        try {
            environment.storageBackupService().fileKinds().stream().map(StorageFileKind::token).sorted(Comparator.naturalOrder()).filter(token -> token.startsWith(remaining))
                    .forEach(builder::suggest);
        } catch (RuntimeException exception) {
            // Backup suggestions are best-effort when a platform cannot expose storage paths yet.
        }
        return builder.buildFuture();
    }

    private static <S> CompletableFuture<Suggestions> suggestBackupFiles(ClutchPermsCommandEnvironment<S> environment, CommandContext<S> context, SuggestionsBuilder builder) {
        String token = StringArgumentType.getString(context, CommandArguments.BACKUP_KIND);
        StorageFileKind.fromToken(token).ifPresent(kind -> {
            String remaining = builder.getRemainingLowerCase();
            try {
                environment.storageBackupService().listBackups(kind).stream().map(backup -> backup.fileName())
                        .filter(fileName -> fileName.toLowerCase(Locale.ROOT).startsWith(remaining)).forEach(builder::suggest);
            } catch (RuntimeException exception) {
                // Backup filename suggestions depend on a valid file kind and readable backup directory.
            }
        });
        return builder.buildFuture();
    }

    private BackupSubcommand() {
    }
}
