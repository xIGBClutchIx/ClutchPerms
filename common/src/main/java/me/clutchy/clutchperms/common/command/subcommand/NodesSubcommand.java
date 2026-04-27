package me.clutchy.clutchperms.common.command.subcommand;

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
import me.clutchy.clutchperms.common.node.KnownPermissionNode;
import me.clutchy.clutchperms.common.permission.PermissionNodes;

/**
 * Builds the `/clutchperms nodes` command branch.
 */
public final class NodesSubcommand {

    /**
     * Handlers for known permission node command actions.
     *
     * @param <S> platform command source type
     */
    public interface Handlers<S> {

        int usage(CommandContext<S> context) throws CommandSyntaxException;

        int list(CommandContext<S> context) throws CommandSyntaxException;

        int searchUsage(CommandContext<S> context) throws CommandSyntaxException;

        int search(CommandContext<S> context) throws CommandSyntaxException;

        int addUsage(CommandContext<S> context) throws CommandSyntaxException;

        int add(CommandContext<S> context) throws CommandSyntaxException;

        int removeUsage(CommandContext<S> context) throws CommandSyntaxException;

        int remove(CommandContext<S> context) throws CommandSyntaxException;

        int unknownUsage(CommandContext<S> context) throws CommandSyntaxException;
    }

    public static <S> LiteralArgumentBuilder<S> builder(ClutchPermsCommandEnvironment<S> environment, AuthorizedCommand<S> authorized, Handlers<S> handlers) {
        return authorized.branch("nodes", PermissionNodes.ADMIN_NODES_LIST, PermissionNodes.ADMIN_NODES_SEARCH, PermissionNodes.ADMIN_NODES_ADD, PermissionNodes.ADMIN_NODES_REMOVE)
                .executes(context -> authorized.run(context, PermissionNodes.ADMIN_NODES_LIST, handlers::usage))
                .then(authorized.literal("list", PermissionNodes.ADMIN_NODES_LIST).executes(context -> authorized.run(context, PermissionNodes.ADMIN_NODES_LIST, handlers::list))
                        .then(NodesSubcommand.<S>pageArgument().executes(context -> authorized.run(context, PermissionNodes.ADMIN_NODES_LIST, handlers::list))))
                .then(authorized.literal("search", PermissionNodes.ADMIN_NODES_SEARCH)
                        .executes(context -> authorized.run(context, PermissionNodes.ADMIN_NODES_SEARCH, handlers::searchUsage))
                        .then(RequiredArgumentBuilder.<S, String>argument(CommandArguments.QUERY, StringArgumentType.word())
                                .executes(context -> authorized.run(context, PermissionNodes.ADMIN_NODES_SEARCH, handlers::search))
                                .then(NodesSubcommand.<S>pageArgument().executes(context -> authorized.run(context, PermissionNodes.ADMIN_NODES_SEARCH, handlers::search)))))
                .then(authorized.literal("add", PermissionNodes.ADMIN_NODES_ADD).executes(context -> authorized.run(context, PermissionNodes.ADMIN_NODES_ADD, handlers::addUsage))
                        .then(nodeArgument(environment).executes(context -> authorized.run(context, PermissionNodes.ADMIN_NODES_ADD, handlers::add))))
                .then(authorized.literal("remove", PermissionNodes.ADMIN_NODES_REMOVE)
                        .executes(context -> authorized.run(context, PermissionNodes.ADMIN_NODES_REMOVE, handlers::removeUsage))
                        .then(nodeArgument(environment).executes(context -> authorized.run(context, PermissionNodes.ADMIN_NODES_REMOVE, handlers::remove))))
                .then(authorized.requires(CommandArguments.<S>unknown(), PermissionNodes.ADMIN_NODES_LIST)
                        .executes(context -> authorized.run(context, PermissionNodes.ADMIN_NODES_LIST, handlers::unknownUsage)));
    }

    private static <S> RequiredArgumentBuilder<S, String> nodeArgument(ClutchPermsCommandEnvironment<S> environment) {
        return RequiredArgumentBuilder.<S, String>argument(CommandArguments.NODE, StringArgumentType.greedyString())
                .suggests((context, builder) -> suggestKnownNodes(environment, builder));
    }

    private static <S> RequiredArgumentBuilder<S, String> pageArgument() {
        return RequiredArgumentBuilder.argument(CommandArguments.PAGE, StringArgumentType.word());
    }

    private static <S> CompletableFuture<Suggestions> suggestKnownNodes(ClutchPermsCommandEnvironment<S> environment, SuggestionsBuilder builder) {
        String remaining = builder.getRemainingLowerCase();
        environment.permissionNodeRegistry().getKnownNodes().stream().map(KnownPermissionNode::node).filter(node -> node.startsWith(remaining)).sorted().forEach(builder::suggest);
        return builder.buildFuture();
    }

    private NodesSubcommand() {
    }
}
