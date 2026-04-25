package me.clutchy.clutchperms.common.command.subcommand;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import me.clutchy.clutchperms.common.command.CommandArguments;
import me.clutchy.clutchperms.common.permission.PermissionNodes;

/**
 * Builds the `/clutchperms users` command branch.
 */
public final class UsersSubcommand {

    /**
     * Handlers for stored subject metadata command actions.
     *
     * @param <S> platform command source type
     */
    public interface Handlers<S> {

        int usage(CommandContext<S> context) throws CommandSyntaxException;

        int list(CommandContext<S> context) throws CommandSyntaxException;

        int searchUsage(CommandContext<S> context) throws CommandSyntaxException;

        int search(CommandContext<S> context) throws CommandSyntaxException;

        int unknownUsage(CommandContext<S> context) throws CommandSyntaxException;
    }

    public static <S> LiteralArgumentBuilder<S> builder(AuthorizedCommand<S> authorized, Handlers<S> handlers) {
        return LiteralArgumentBuilder.<S>literal("users").executes(context -> authorized.run(context, PermissionNodes.ADMIN_USERS_LIST, handlers::usage))
                .then(LiteralArgumentBuilder.<S>literal("list").executes(context -> authorized.run(context, PermissionNodes.ADMIN_USERS_LIST, handlers::list)))
                .then(LiteralArgumentBuilder.<S>literal("search").executes(context -> authorized.run(context, PermissionNodes.ADMIN_USERS_SEARCH, handlers::searchUsage))
                        .then(RequiredArgumentBuilder.<S, String>argument(CommandArguments.NAME, StringArgumentType.word())
                                .executes(context -> authorized.run(context, PermissionNodes.ADMIN_USERS_SEARCH, handlers::search))))
                .then(CommandArguments.<S>unknown().executes(context -> authorized.run(context, PermissionNodes.ADMIN_USERS_LIST, handlers::unknownUsage)));
    }

    private UsersSubcommand() {
    }
}
