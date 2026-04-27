package me.clutchy.clutchperms.common.command.subcommand;

import java.util.Comparator;
import java.util.Locale;
import java.util.Set;
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
 * Builds the `/clutchperms group` command branch.
 */
public final class GroupSubcommand {

    /**
     * Handlers for group definition, permission, parent, and member command actions.
     *
     * @param <S> platform command source type
     */
    public interface Handlers<S> {

        int rootUsage(CommandContext<S> context) throws CommandSyntaxException;

        int list(CommandContext<S> context) throws CommandSyntaxException;

        int targetUsage(CommandContext<S> context) throws CommandSyntaxException;

        int unknownTargetUsage(CommandContext<S> context) throws CommandSyntaxException;

        int create(CommandContext<S> context) throws CommandSyntaxException;

        int delete(CommandContext<S> context) throws CommandSyntaxException;

        int renameUsage(CommandContext<S> context) throws CommandSyntaxException;

        int rename(CommandContext<S> context) throws CommandSyntaxException;

        int info(CommandContext<S> context) throws CommandSyntaxException;

        int show(CommandContext<S> context) throws CommandSyntaxException;

        int members(CommandContext<S> context) throws CommandSyntaxException;

        int parents(CommandContext<S> context) throws CommandSyntaxException;

        int parentUsage(CommandContext<S> context) throws CommandSyntaxException;

        int parentAddUsage(CommandContext<S> context) throws CommandSyntaxException;

        int parentAdd(CommandContext<S> context) throws CommandSyntaxException;

        int parentRemoveUsage(CommandContext<S> context) throws CommandSyntaxException;

        int parentRemove(CommandContext<S> context) throws CommandSyntaxException;

        int unknownParentUsage(CommandContext<S> context) throws CommandSyntaxException;

        int getUsage(CommandContext<S> context) throws CommandSyntaxException;

        int get(CommandContext<S> context) throws CommandSyntaxException;

        int setUsage(CommandContext<S> context) throws CommandSyntaxException;

        int set(CommandContext<S> context) throws CommandSyntaxException;

        int clearUsage(CommandContext<S> context) throws CommandSyntaxException;

        int clear(CommandContext<S> context) throws CommandSyntaxException;

        int clearAll(CommandContext<S> context) throws CommandSyntaxException;

        int prefixUsage(CommandContext<S> context) throws CommandSyntaxException;

        int prefixGet(CommandContext<S> context) throws CommandSyntaxException;

        int prefixSetUsage(CommandContext<S> context) throws CommandSyntaxException;

        int prefixSet(CommandContext<S> context) throws CommandSyntaxException;

        int prefixClear(CommandContext<S> context) throws CommandSyntaxException;

        int suffixUsage(CommandContext<S> context) throws CommandSyntaxException;

        int suffixGet(CommandContext<S> context) throws CommandSyntaxException;

        int suffixSetUsage(CommandContext<S> context) throws CommandSyntaxException;

        int suffixSet(CommandContext<S> context) throws CommandSyntaxException;

        int suffixClear(CommandContext<S> context) throws CommandSyntaxException;
    }

    public static <S> LiteralArgumentBuilder<S> builder(ClutchPermsCommandEnvironment<S> environment, AuthorizedCommand<S> authorized, Handlers<S> handlers,
            SuggestionProvider<S> permissionNodes, SuggestionProvider<S> permissionAssignment) {
        RequiredArgumentBuilder<S, String> group = groupArgument(environment).executes(context -> authorized.run(context, PermissionNodes.ADMIN_GROUP_VIEW, handlers::targetUsage))
                .requires(source -> authorized.canUseAny(source, groupPermissions()))
                .then(authorized.literal("create", PermissionNodes.ADMIN_GROUP_CREATE)
                        .executes(context -> authorized.run(context, PermissionNodes.ADMIN_GROUP_CREATE, handlers::create)))
                .then(authorized.literal("delete", PermissionNodes.ADMIN_GROUP_DELETE)
                        .executes(context -> authorized.run(context, PermissionNodes.ADMIN_GROUP_DELETE, handlers::delete)))
                .then(renameCommand(authorized, handlers)).then(infoCommand(authorized, handlers))
                .then(authorized.literal("list", PermissionNodes.ADMIN_GROUP_VIEW).executes(context -> authorized.run(context, PermissionNodes.ADMIN_GROUP_VIEW, handlers::show))
                        .then(GroupSubcommand.<S>pageArgument().executes(context -> authorized.run(context, PermissionNodes.ADMIN_GROUP_VIEW, handlers::show))))
                .then(authorized.literal("members", PermissionNodes.ADMIN_GROUP_MEMBERS)
                        .executes(context -> authorized.run(context, PermissionNodes.ADMIN_GROUP_MEMBERS, handlers::members))
                        .then(GroupSubcommand.<S>pageArgument().executes(context -> authorized.run(context, PermissionNodes.ADMIN_GROUP_MEMBERS, handlers::members))))
                .then(authorized.literal("parents", PermissionNodes.ADMIN_GROUP_PARENTS)
                        .executes(context -> authorized.run(context, PermissionNodes.ADMIN_GROUP_PARENTS, handlers::parents))
                        .then(GroupSubcommand.<S>pageArgument().executes(context -> authorized.run(context, PermissionNodes.ADMIN_GROUP_PARENTS, handlers::parents))))
                .then(parentCommand(environment, authorized, handlers)).then(getCommand(authorized, handlers, permissionNodes))
                .then(setCommand(authorized, handlers, permissionAssignment)).then(clearCommand(authorized, handlers, permissionNodes)).then(clearAllCommand(authorized, handlers))
                .then(displayCommand("prefix", authorized, handlers, true)).then(displayCommand("suffix", authorized, handlers, false))
                .then(authorized.requires(CommandArguments.<S>unknown(), PermissionNodes.ADMIN_GROUP_VIEW)
                        .executes(context -> authorized.run(context, PermissionNodes.ADMIN_GROUP_VIEW, handlers::unknownTargetUsage)));

        return authorized.branch("group", groupPermissions()).executes(context -> authorized.run(context, PermissionNodes.ADMIN_GROUP_LIST, handlers::rootUsage))
                .then(authorized.literal("list", PermissionNodes.ADMIN_GROUP_LIST).executes(context -> authorized.run(context, PermissionNodes.ADMIN_GROUP_LIST, handlers::list))
                        .then(GroupSubcommand.<S>pageArgument().executes(context -> authorized.run(context, PermissionNodes.ADMIN_GROUP_LIST, handlers::list))))
                .then(group);
    }

    private static <S> LiteralArgumentBuilder<S> renameCommand(AuthorizedCommand<S> authorized, Handlers<S> handlers) {
        return authorized.literal("rename", PermissionNodes.ADMIN_GROUP_RENAME)
                .executes(context -> authorized.run(context, PermissionNodes.ADMIN_GROUP_RENAME, handlers::renameUsage))
                .then(GroupSubcommand.<S>newGroupArgument().executes(context -> authorized.run(context, PermissionNodes.ADMIN_GROUP_RENAME, handlers::rename)));
    }

    private static <S> LiteralArgumentBuilder<S> infoCommand(AuthorizedCommand<S> authorized, Handlers<S> handlers) {
        return authorized.literal("info", PermissionNodes.ADMIN_GROUP_INFO).executes(context -> authorized.run(context, PermissionNodes.ADMIN_GROUP_INFO, handlers::info));
    }

    private static <S> LiteralArgumentBuilder<S> parentCommand(ClutchPermsCommandEnvironment<S> environment, AuthorizedCommand<S> authorized, Handlers<S> handlers) {
        return authorized.branch("parent", PermissionNodes.ADMIN_GROUP_PARENTS, PermissionNodes.ADMIN_GROUP_PARENT_ADD, PermissionNodes.ADMIN_GROUP_PARENT_REMOVE)
                .executes(context -> authorized.run(context, PermissionNodes.ADMIN_GROUP_PARENTS, handlers::parentUsage))
                .then(authorized.literal("add", PermissionNodes.ADMIN_GROUP_PARENT_ADD)
                        .executes(context -> authorized.run(context, PermissionNodes.ADMIN_GROUP_PARENT_ADD, handlers::parentAddUsage))
                        .then(parentGroupArgument(environment).executes(context -> authorized.run(context, PermissionNodes.ADMIN_GROUP_PARENT_ADD, handlers::parentAdd))))
                .then(authorized.literal("remove", PermissionNodes.ADMIN_GROUP_PARENT_REMOVE)
                        .executes(context -> authorized.run(context, PermissionNodes.ADMIN_GROUP_PARENT_REMOVE, handlers::parentRemoveUsage))
                        .then(parentGroupArgument(environment).executes(context -> authorized.run(context, PermissionNodes.ADMIN_GROUP_PARENT_REMOVE, handlers::parentRemove))))
                .then(authorized.requires(CommandArguments.<S>unknown(), PermissionNodes.ADMIN_GROUP_PARENTS)
                        .executes(context -> authorized.run(context, PermissionNodes.ADMIN_GROUP_PARENTS, handlers::unknownParentUsage)));
    }

    private static <S> LiteralArgumentBuilder<S> getCommand(AuthorizedCommand<S> authorized, Handlers<S> handlers, SuggestionProvider<S> permissionNodes) {
        return authorized.literal("get", PermissionNodes.ADMIN_GROUP_GET).executes(context -> authorized.run(context, PermissionNodes.ADMIN_GROUP_GET, handlers::getUsage))
                .then(nodeArgument(permissionNodes).executes(context -> authorized.run(context, PermissionNodes.ADMIN_GROUP_GET, handlers::get)));
    }

    private static <S> LiteralArgumentBuilder<S> setCommand(AuthorizedCommand<S> authorized, Handlers<S> handlers, SuggestionProvider<S> permissionAssignment) {
        return authorized.literal("set", PermissionNodes.ADMIN_GROUP_SET).executes(context -> authorized.run(context, PermissionNodes.ADMIN_GROUP_SET, handlers::setUsage))
                .then(assignmentArgument(permissionAssignment).executes(context -> authorized.run(context, PermissionNodes.ADMIN_GROUP_SET, handlers::set)));
    }

    private static <S> LiteralArgumentBuilder<S> clearCommand(AuthorizedCommand<S> authorized, Handlers<S> handlers, SuggestionProvider<S> permissionNodes) {
        return authorized.literal("clear", PermissionNodes.ADMIN_GROUP_CLEAR).executes(context -> authorized.run(context, PermissionNodes.ADMIN_GROUP_CLEAR, handlers::clearUsage))
                .then(nodeArgument(permissionNodes).executes(context -> authorized.run(context, PermissionNodes.ADMIN_GROUP_CLEAR, handlers::clear)));
    }

    private static <S> LiteralArgumentBuilder<S> clearAllCommand(AuthorizedCommand<S> authorized, Handlers<S> handlers) {
        return authorized.literal("clear-all", PermissionNodes.ADMIN_GROUP_CLEAR_ALL)
                .executes(context -> authorized.run(context, PermissionNodes.ADMIN_GROUP_CLEAR_ALL, handlers::clearAll));
    }

    private static <S> LiteralArgumentBuilder<S> displayCommand(String literal, AuthorizedCommand<S> authorized, Handlers<S> handlers, boolean prefix) {
        return authorized.branch(literal, PermissionNodes.ADMIN_GROUP_DISPLAY_VIEW, PermissionNodes.ADMIN_GROUP_DISPLAY_SET, PermissionNodes.ADMIN_GROUP_DISPLAY_CLEAR)
                .executes(context -> authorized.run(context, PermissionNodes.ADMIN_GROUP_DISPLAY_VIEW, prefix ? handlers::prefixUsage : handlers::suffixUsage))
                .then(authorized.literal("get", PermissionNodes.ADMIN_GROUP_DISPLAY_VIEW)
                        .executes(context -> authorized.run(context, PermissionNodes.ADMIN_GROUP_DISPLAY_VIEW, prefix ? handlers::prefixGet : handlers::suffixGet)))
                .then(authorized.literal("set", PermissionNodes.ADMIN_GROUP_DISPLAY_SET)
                        .executes(context -> authorized.run(context, PermissionNodes.ADMIN_GROUP_DISPLAY_SET, prefix ? handlers::prefixSetUsage : handlers::suffixSetUsage))
                        .then(GroupSubcommand.<S>displayValueArgument()
                                .executes(context -> authorized.run(context, PermissionNodes.ADMIN_GROUP_DISPLAY_SET, prefix ? handlers::prefixSet : handlers::suffixSet))))
                .then(authorized.literal("clear", PermissionNodes.ADMIN_GROUP_DISPLAY_CLEAR)
                        .executes(context -> authorized.run(context, PermissionNodes.ADMIN_GROUP_DISPLAY_CLEAR, prefix ? handlers::prefixClear : handlers::suffixClear)));
    }

    private static String[] groupPermissions() {
        return new String[]{PermissionNodes.ADMIN_GROUP_LIST, PermissionNodes.ADMIN_GROUP_INFO, PermissionNodes.ADMIN_GROUP_CREATE, PermissionNodes.ADMIN_GROUP_DELETE,
                PermissionNodes.ADMIN_GROUP_RENAME, PermissionNodes.ADMIN_GROUP_VIEW, PermissionNodes.ADMIN_GROUP_GET, PermissionNodes.ADMIN_GROUP_SET,
                PermissionNodes.ADMIN_GROUP_CLEAR, PermissionNodes.ADMIN_GROUP_CLEAR_ALL, PermissionNodes.ADMIN_GROUP_MEMBERS, PermissionNodes.ADMIN_GROUP_PARENTS,
                PermissionNodes.ADMIN_GROUP_PARENT_ADD, PermissionNodes.ADMIN_GROUP_PARENT_REMOVE, PermissionNodes.ADMIN_GROUP_DISPLAY_VIEW,
                PermissionNodes.ADMIN_GROUP_DISPLAY_SET, PermissionNodes.ADMIN_GROUP_DISPLAY_CLEAR};
    }

    private static <S> RequiredArgumentBuilder<S, String> nodeArgument(SuggestionProvider<S> permissionNodes) {
        return RequiredArgumentBuilder.<S, String>argument(CommandArguments.NODE, StringArgumentType.greedyString()).suggests(permissionNodes);
    }

    private static <S> RequiredArgumentBuilder<S, String> assignmentArgument(SuggestionProvider<S> permissionAssignment) {
        return RequiredArgumentBuilder.<S, String>argument(CommandArguments.ASSIGNMENT, StringArgumentType.greedyString()).suggests(permissionAssignment);
    }

    private static <S> RequiredArgumentBuilder<S, String> displayValueArgument() {
        return RequiredArgumentBuilder.argument(CommandArguments.DISPLAY_VALUE, StringArgumentType.greedyString());
    }

    private static <S> RequiredArgumentBuilder<S, String> pageArgument() {
        return RequiredArgumentBuilder.argument(CommandArguments.PAGE, StringArgumentType.word());
    }

    private static <S> RequiredArgumentBuilder<S, String> groupArgument(ClutchPermsCommandEnvironment<S> environment) {
        return RequiredArgumentBuilder.<S, String>argument(CommandArguments.GROUP, StringArgumentType.word()).suggests((context, builder) -> suggestGroups(environment, builder));
    }

    private static <S> RequiredArgumentBuilder<S, String> parentGroupArgument(ClutchPermsCommandEnvironment<S> environment) {
        return RequiredArgumentBuilder.<S, String>argument(CommandArguments.PARENT, StringArgumentType.word())
                .suggests((context, builder) -> suggestParentGroups(environment, context, builder));
    }

    private static <S> RequiredArgumentBuilder<S, String> newGroupArgument() {
        return RequiredArgumentBuilder.argument(CommandArguments.NEW_GROUP, StringArgumentType.word());
    }

    private static <S> CompletableFuture<Suggestions> suggestGroups(ClutchPermsCommandEnvironment<S> environment, SuggestionsBuilder builder) {
        String remaining = builder.getRemaining().toLowerCase(Locale.ROOT);
        environment.groupService().getGroups().stream().sorted(Comparator.naturalOrder()).filter(group -> group.toLowerCase(Locale.ROOT).startsWith(remaining))
                .forEach(builder::suggest);
        return builder.buildFuture();
    }

    private static <S> CompletableFuture<Suggestions> suggestParentGroups(ClutchPermsCommandEnvironment<S> environment, CommandContext<S> context, SuggestionsBuilder builder) {
        String remaining = builder.getRemaining().toLowerCase(Locale.ROOT);
        String normalizedGroupName;
        Set<String> existingParents;
        try {
            normalizedGroupName = StringArgumentType.getString(context, CommandArguments.GROUP).trim().toLowerCase(Locale.ROOT);
            existingParents = environment.groupService().getGroupParents(normalizedGroupName);
        } catch (IllegalArgumentException exception) {
            normalizedGroupName = "";
            existingParents = Set.of();
        }

        String currentGroupName = normalizedGroupName;
        Set<String> currentParents = existingParents;
        if (GroupService.OP_GROUP.equals(currentGroupName)) {
            return builder.buildFuture();
        }
        environment.groupService().getGroups().stream().sorted(Comparator.naturalOrder()).filter(group -> !group.equals(currentGroupName))
                .filter(group -> !GroupService.OP_GROUP.equals(group)).filter(group -> !currentParents.contains(group))
                .filter(group -> group.toLowerCase(Locale.ROOT).startsWith(remaining)).forEach(builder::suggest);
        return builder.buildFuture();
    }

    private GroupSubcommand() {
    }
}
