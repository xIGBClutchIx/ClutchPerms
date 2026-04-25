package me.clutchy.clutchperms.common.command.subcommand;

import java.util.Comparator;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;

import me.clutchy.clutchperms.common.command.ClutchPermsCommandEnvironment;
import me.clutchy.clutchperms.common.command.CommandArguments;
import me.clutchy.clutchperms.common.group.GroupService;
import me.clutchy.clutchperms.common.permission.PermissionNodes;

/**
 * Builds the `/clutchperms user` command branch.
 */
public final class UserSubcommand {

    /**
     * Handlers for direct user permission and membership command actions.
     *
     * @param <S> platform command source type
     */
    public interface Handlers<S> {

        int rootUsage(CommandContext<S> context) throws CommandSyntaxException;

        int targetUsage(CommandContext<S> context) throws CommandSyntaxException;

        int unknownTargetUsage(CommandContext<S> context) throws CommandSyntaxException;

        int list(CommandContext<S> context) throws CommandSyntaxException;

        int getUsage(CommandContext<S> context) throws CommandSyntaxException;

        int get(CommandContext<S> context) throws CommandSyntaxException;

        int setUsage(CommandContext<S> context) throws CommandSyntaxException;

        int set(CommandContext<S> context) throws CommandSyntaxException;

        int clearUsage(CommandContext<S> context) throws CommandSyntaxException;

        int clear(CommandContext<S> context) throws CommandSyntaxException;

        int groups(CommandContext<S> context) throws CommandSyntaxException;

        int groupUsage(CommandContext<S> context) throws CommandSyntaxException;

        int groupAddUsage(CommandContext<S> context) throws CommandSyntaxException;

        int groupAdd(CommandContext<S> context) throws CommandSyntaxException;

        int groupRemoveUsage(CommandContext<S> context) throws CommandSyntaxException;

        int groupRemove(CommandContext<S> context) throws CommandSyntaxException;

        int unknownGroupUsage(CommandContext<S> context) throws CommandSyntaxException;

        int checkUsage(CommandContext<S> context) throws CommandSyntaxException;

        int check(CommandContext<S> context) throws CommandSyntaxException;

        int explainUsage(CommandContext<S> context) throws CommandSyntaxException;

        int explain(CommandContext<S> context) throws CommandSyntaxException;
    }

    public static <S> LiteralArgumentBuilder<S> builder(ClutchPermsCommandEnvironment<S> environment, AuthorizedCommand<S> authorized, Handlers<S> handlers,
            SuggestionProvider<S> permissionNodes, SuggestionProvider<S> permissionAssignment) {
        RequiredArgumentBuilder<S, String> target = RequiredArgumentBuilder.<S, String>argument(CommandArguments.TARGET, StringArgumentType.word())
                .suggests((context, builder) -> suggestOnlineSubjects(environment, context.getSource(), builder));

        return LiteralArgumentBuilder.<S>literal("user").executes(context -> authorized.run(context, PermissionNodes.ADMIN_USER_LIST, handlers::rootUsage))
                .then(target.then(listCommand(authorized, handlers)).then(getCommand(authorized, handlers, permissionNodes))
                        .then(setCommand(authorized, handlers, permissionAssignment)).then(clearCommand(authorized, handlers, permissionNodes))
                        .then(groupsCommand(authorized, handlers)).then(groupCommand(environment, authorized, handlers)).then(checkCommand(authorized, handlers, permissionNodes))
                        .then(explainCommand(authorized, handlers, permissionNodes))
                        .executes(context -> authorized.run(context, PermissionNodes.ADMIN_USER_LIST, handlers::targetUsage))
                        .then(CommandArguments.<S>unknown().executes(context -> authorized.run(context, PermissionNodes.ADMIN_USER_LIST, handlers::unknownTargetUsage))));
    }

    private static <S> LiteralArgumentBuilder<S> listCommand(AuthorizedCommand<S> authorized, Handlers<S> handlers) {
        return LiteralArgumentBuilder.<S>literal("list").executes(context -> authorized.run(context, PermissionNodes.ADMIN_USER_LIST, handlers::list))
                .then(UserSubcommand.<S>pageArgument().executes(context -> authorized.run(context, PermissionNodes.ADMIN_USER_LIST, handlers::list)));
    }

    private static <S> LiteralArgumentBuilder<S> getCommand(AuthorizedCommand<S> authorized, Handlers<S> handlers, SuggestionProvider<S> permissionNodes) {
        return LiteralArgumentBuilder.<S>literal("get").executes(context -> authorized.run(context, PermissionNodes.ADMIN_USER_GET, handlers::getUsage))
                .then(nodeArgument(permissionNodes).executes(context -> authorized.run(context, PermissionNodes.ADMIN_USER_GET, handlers::get)));
    }

    private static <S> LiteralArgumentBuilder<S> setCommand(AuthorizedCommand<S> authorized, Handlers<S> handlers, SuggestionProvider<S> permissionAssignment) {
        return LiteralArgumentBuilder.<S>literal("set").executes(context -> authorized.run(context, PermissionNodes.ADMIN_USER_SET, handlers::setUsage))
                .then(assignmentArgument(permissionAssignment).executes(context -> authorized.run(context, PermissionNodes.ADMIN_USER_SET, handlers::set)));
    }

    private static <S> LiteralArgumentBuilder<S> clearCommand(AuthorizedCommand<S> authorized, Handlers<S> handlers, SuggestionProvider<S> permissionNodes) {
        return LiteralArgumentBuilder.<S>literal("clear").executes(context -> authorized.run(context, PermissionNodes.ADMIN_USER_CLEAR, handlers::clearUsage))
                .then(nodeArgument(permissionNodes).executes(context -> authorized.run(context, PermissionNodes.ADMIN_USER_CLEAR, handlers::clear)));
    }

    private static <S> LiteralArgumentBuilder<S> groupsCommand(AuthorizedCommand<S> authorized, Handlers<S> handlers) {
        return LiteralArgumentBuilder.<S>literal("groups").executes(context -> authorized.run(context, PermissionNodes.ADMIN_USER_GROUPS, handlers::groups))
                .then(UserSubcommand.<S>pageArgument().executes(context -> authorized.run(context, PermissionNodes.ADMIN_USER_GROUPS, handlers::groups)));
    }

    private static <S> LiteralArgumentBuilder<S> groupCommand(ClutchPermsCommandEnvironment<S> environment, AuthorizedCommand<S> authorized, Handlers<S> handlers) {
        return LiteralArgumentBuilder.<S>literal("group").executes(context -> authorized.run(context, PermissionNodes.ADMIN_USER_GROUPS, handlers::groupUsage))
                .then(LiteralArgumentBuilder.<S>literal("add").executes(context -> authorized.run(context, PermissionNodes.ADMIN_USER_GROUP_ADD, handlers::groupAddUsage))
                        .then(groupArgument(environment).executes(context -> authorized.run(context, PermissionNodes.ADMIN_USER_GROUP_ADD, handlers::groupAdd))))
                .then(LiteralArgumentBuilder.<S>literal("remove").executes(context -> authorized.run(context, PermissionNodes.ADMIN_USER_GROUP_REMOVE, handlers::groupRemoveUsage))
                        .then(groupArgument(environment).executes(context -> authorized.run(context, PermissionNodes.ADMIN_USER_GROUP_REMOVE, handlers::groupRemove))))
                .then(CommandArguments.<S>unknown().executes(context -> authorized.run(context, PermissionNodes.ADMIN_USER_GROUPS, handlers::unknownGroupUsage)));
    }

    private static <S> LiteralArgumentBuilder<S> checkCommand(AuthorizedCommand<S> authorized, Handlers<S> handlers, SuggestionProvider<S> permissionNodes) {
        return LiteralArgumentBuilder.<S>literal("check").executes(context -> authorized.run(context, PermissionNodes.ADMIN_USER_CHECK, handlers::checkUsage))
                .then(nodeArgument(permissionNodes).executes(context -> authorized.run(context, PermissionNodes.ADMIN_USER_CHECK, handlers::check)));
    }

    private static <S> LiteralArgumentBuilder<S> explainCommand(AuthorizedCommand<S> authorized, Handlers<S> handlers, SuggestionProvider<S> permissionNodes) {
        return LiteralArgumentBuilder.<S>literal("explain").executes(context -> authorized.run(context, PermissionNodes.ADMIN_USER_EXPLAIN, handlers::explainUsage))
                .then(nodeArgument(permissionNodes).executes(context -> authorized.run(context, PermissionNodes.ADMIN_USER_EXPLAIN, handlers::explain)));
    }

    private static <S> RequiredArgumentBuilder<S, String> nodeArgument(SuggestionProvider<S> permissionNodes) {
        return RequiredArgumentBuilder.<S, String>argument(CommandArguments.NODE, StringArgumentType.greedyString()).suggests(permissionNodes);
    }

    private static <S> RequiredArgumentBuilder<S, String> assignmentArgument(SuggestionProvider<S> permissionAssignment) {
        return RequiredArgumentBuilder.<S, String>argument(CommandArguments.ASSIGNMENT, StringArgumentType.greedyString()).suggests(permissionAssignment);
    }

    private static <S> RequiredArgumentBuilder<S, String> pageArgument() {
        return RequiredArgumentBuilder.argument(CommandArguments.PAGE, StringArgumentType.word());
    }

    private static <S> RequiredArgumentBuilder<S, String> groupArgument(ClutchPermsCommandEnvironment<S> environment) {
        return RequiredArgumentBuilder.<S, String>argument(CommandArguments.GROUP, StringArgumentType.word()).suggests((context, builder) -> suggestGroups(environment, builder));
    }

    private static <S> CompletableFuture<Suggestions> suggestOnlineSubjects(ClutchPermsCommandEnvironment<S> environment, S source, SuggestionsBuilder builder) {
        environment.onlineSubjectNames(source).stream().sorted(Comparator.naturalOrder()).forEach(builder::suggest);
        return builder.buildFuture();
    }

    private static <S> CompletableFuture<Suggestions> suggestGroups(ClutchPermsCommandEnvironment<S> environment, SuggestionsBuilder builder) {
        String remaining = builder.getRemaining().toLowerCase(Locale.ROOT);
        environment.groupService().getGroups().stream().sorted(Comparator.naturalOrder()).filter(group -> group.toLowerCase(Locale.ROOT).startsWith(remaining))
                .filter(group -> !GroupService.DEFAULT_GROUP.equals(group)).forEach(builder::suggest);
        return builder.buildFuture();
    }

    private UserSubcommand() {
    }
}
