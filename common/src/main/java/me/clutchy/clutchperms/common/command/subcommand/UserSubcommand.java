package me.clutchy.clutchperms.common.command.subcommand;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.context.StringRange;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestion;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;

import me.clutchy.clutchperms.common.command.ClutchPermsCommandEnvironment;
import me.clutchy.clutchperms.common.command.CommandArguments;
import me.clutchy.clutchperms.common.command.CommandSubject;
import me.clutchy.clutchperms.common.group.GroupService;
import me.clutchy.clutchperms.common.permission.PermissionNodes;
import me.clutchy.clutchperms.common.subject.SubjectMetadata;

/**
 * Builds the `/clutchperms user` command branch.
 */
public final class UserSubcommand {

    private static final Comparator<String> SUGGESTION_ORDER = Comparator.comparing((String value) -> value.toLowerCase(Locale.ROOT)).thenComparing(Comparator.naturalOrder());

    /**
     * Handlers for direct user permission and membership command actions.
     *
     * @param <S> platform command source type
     */
    public interface Handlers<S> {

        int rootUsage(CommandContext<S> context) throws CommandSyntaxException;

        int targetUsage(CommandContext<S> context) throws CommandSyntaxException;

        int unknownTargetUsage(CommandContext<S> context) throws CommandSyntaxException;

        int info(CommandContext<S> context) throws CommandSyntaxException;

        int list(CommandContext<S> context) throws CommandSyntaxException;

        int getUsage(CommandContext<S> context) throws CommandSyntaxException;

        int get(CommandContext<S> context) throws CommandSyntaxException;

        int setUsage(CommandContext<S> context) throws CommandSyntaxException;

        int set(CommandContext<S> context) throws CommandSyntaxException;

        int clearUsage(CommandContext<S> context) throws CommandSyntaxException;

        int clear(CommandContext<S> context) throws CommandSyntaxException;

        int clearAll(CommandContext<S> context) throws CommandSyntaxException;

        int groups(CommandContext<S> context) throws CommandSyntaxException;

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
                .requires(source -> authorized.canUseAny(source, userPermissions())).suggests((context, builder) -> suggestUserTargets(environment, context.getSource(), builder));

        return authorized.branch("user", userPermissions()).executes(context -> authorized.run(context, PermissionNodes.ADMIN_USER_LIST, handlers::rootUsage))
                .then(target.then(infoCommand(authorized, handlers)).then(listCommand(authorized, handlers)).then(getCommand(authorized, handlers, permissionNodes))
                        .then(setCommand(authorized, handlers, permissionAssignment)).then(clearCommand(authorized, handlers, permissionNodes))
                        .then(clearAllCommand(authorized, handlers)).then(groupsCommand(authorized, handlers)).then(displayCommand("prefix", authorized, handlers, true))
                        .then(displayCommand("suffix", authorized, handlers, false)).then(groupCommand(environment, authorized, handlers))
                        .then(checkCommand(authorized, handlers, permissionNodes)).then(explainCommand(authorized, handlers, permissionNodes))
                        .executes(context -> authorized.run(context, PermissionNodes.ADMIN_USER_LIST, handlers::targetUsage))
                        .then(authorized.requires(CommandArguments.<S>unknown(), PermissionNodes.ADMIN_USER_LIST)
                                .executes(context -> authorized.run(context, PermissionNodes.ADMIN_USER_LIST, handlers::unknownTargetUsage))));
    }

    private static <S> LiteralArgumentBuilder<S> infoCommand(AuthorizedCommand<S> authorized, Handlers<S> handlers) {
        return authorized.literal("info", PermissionNodes.ADMIN_USER_INFO).executes(context -> authorized.run(context, PermissionNodes.ADMIN_USER_INFO, handlers::info));
    }

    private static <S> LiteralArgumentBuilder<S> listCommand(AuthorizedCommand<S> authorized, Handlers<S> handlers) {
        return authorized.literal("list", PermissionNodes.ADMIN_USER_LIST).executes(context -> authorized.run(context, PermissionNodes.ADMIN_USER_LIST, handlers::list))
                .then(UserSubcommand.<S>pageArgument().executes(context -> authorized.run(context, PermissionNodes.ADMIN_USER_LIST, handlers::list)));
    }

    private static <S> LiteralArgumentBuilder<S> getCommand(AuthorizedCommand<S> authorized, Handlers<S> handlers, SuggestionProvider<S> permissionNodes) {
        return authorized.literal("get", PermissionNodes.ADMIN_USER_GET).executes(context -> authorized.run(context, PermissionNodes.ADMIN_USER_GET, handlers::getUsage))
                .then(nodeArgument(permissionNodes).executes(context -> authorized.run(context, PermissionNodes.ADMIN_USER_GET, handlers::get)));
    }

    private static <S> LiteralArgumentBuilder<S> setCommand(AuthorizedCommand<S> authorized, Handlers<S> handlers, SuggestionProvider<S> permissionAssignment) {
        return authorized.literal("set", PermissionNodes.ADMIN_USER_SET).executes(context -> authorized.run(context, PermissionNodes.ADMIN_USER_SET, handlers::setUsage))
                .then(assignmentArgument(permissionAssignment).executes(context -> authorized.run(context, PermissionNodes.ADMIN_USER_SET, handlers::set)));
    }

    private static <S> LiteralArgumentBuilder<S> clearCommand(AuthorizedCommand<S> authorized, Handlers<S> handlers, SuggestionProvider<S> permissionNodes) {
        return authorized.literal("clear", PermissionNodes.ADMIN_USER_CLEAR).executes(context -> authorized.run(context, PermissionNodes.ADMIN_USER_CLEAR, handlers::clearUsage))
                .then(nodeArgument(permissionNodes).executes(context -> authorized.run(context, PermissionNodes.ADMIN_USER_CLEAR, handlers::clear)));
    }

    private static <S> LiteralArgumentBuilder<S> clearAllCommand(AuthorizedCommand<S> authorized, Handlers<S> handlers) {
        return authorized.literal("clear-all", PermissionNodes.ADMIN_USER_CLEAR_ALL)
                .executes(context -> authorized.run(context, PermissionNodes.ADMIN_USER_CLEAR_ALL, handlers::clearAll));
    }

    private static <S> LiteralArgumentBuilder<S> groupsCommand(AuthorizedCommand<S> authorized, Handlers<S> handlers) {
        return authorized.literal("groups", PermissionNodes.ADMIN_USER_GROUPS).executes(context -> authorized.run(context, PermissionNodes.ADMIN_USER_GROUPS, handlers::groups))
                .then(UserSubcommand.<S>pageArgument().executes(context -> authorized.run(context, PermissionNodes.ADMIN_USER_GROUPS, handlers::groups)));
    }

    private static <S> LiteralArgumentBuilder<S> displayCommand(String literal, AuthorizedCommand<S> authorized, Handlers<S> handlers, boolean prefix) {
        return authorized.branch(literal, PermissionNodes.ADMIN_USER_DISPLAY_VIEW, PermissionNodes.ADMIN_USER_DISPLAY_SET, PermissionNodes.ADMIN_USER_DISPLAY_CLEAR)
                .executes(context -> authorized.run(context, PermissionNodes.ADMIN_USER_DISPLAY_VIEW, prefix ? handlers::prefixUsage : handlers::suffixUsage))
                .then(authorized.literal("get", PermissionNodes.ADMIN_USER_DISPLAY_VIEW)
                        .executes(context -> authorized.run(context, PermissionNodes.ADMIN_USER_DISPLAY_VIEW, prefix ? handlers::prefixGet : handlers::suffixGet)))
                .then(authorized.literal("set", PermissionNodes.ADMIN_USER_DISPLAY_SET)
                        .executes(context -> authorized.run(context, PermissionNodes.ADMIN_USER_DISPLAY_SET, prefix ? handlers::prefixSetUsage : handlers::suffixSetUsage))
                        .then(UserSubcommand.<S>displayValueArgument()
                                .executes(context -> authorized.run(context, PermissionNodes.ADMIN_USER_DISPLAY_SET, prefix ? handlers::prefixSet : handlers::suffixSet))))
                .then(authorized.literal("clear", PermissionNodes.ADMIN_USER_DISPLAY_CLEAR)
                        .executes(context -> authorized.run(context, PermissionNodes.ADMIN_USER_DISPLAY_CLEAR, prefix ? handlers::prefixClear : handlers::suffixClear)));
    }

    private static <S> LiteralArgumentBuilder<S> groupCommand(ClutchPermsCommandEnvironment<S> environment, AuthorizedCommand<S> authorized, Handlers<S> handlers) {
        return authorized.branch("group", PermissionNodes.ADMIN_USER_GROUPS, PermissionNodes.ADMIN_USER_GROUP_ADD, PermissionNodes.ADMIN_USER_GROUP_REMOVE)
                .executes(context -> authorized.run(context, PermissionNodes.ADMIN_USER_GROUPS, handlers::groupUsage))
                .then(authorized.literal("add", PermissionNodes.ADMIN_USER_GROUP_ADD)
                        .executes(context -> authorized.run(context, PermissionNodes.ADMIN_USER_GROUP_ADD, handlers::groupAddUsage))
                        .then(groupAddArgument(environment).executes(context -> authorized.run(context, PermissionNodes.ADMIN_USER_GROUP_ADD, handlers::groupAdd))))
                .then(authorized.literal("remove", PermissionNodes.ADMIN_USER_GROUP_REMOVE)
                        .executes(context -> authorized.run(context, PermissionNodes.ADMIN_USER_GROUP_REMOVE, handlers::groupRemoveUsage))
                        .then(groupRemoveArgument(environment).executes(context -> authorized.run(context, PermissionNodes.ADMIN_USER_GROUP_REMOVE, handlers::groupRemove))))
                .then(authorized.requires(CommandArguments.<S>unknown(), PermissionNodes.ADMIN_USER_GROUPS)
                        .executes(context -> authorized.run(context, PermissionNodes.ADMIN_USER_GROUPS, handlers::unknownGroupUsage)));
    }

    private static <S> LiteralArgumentBuilder<S> checkCommand(AuthorizedCommand<S> authorized, Handlers<S> handlers, SuggestionProvider<S> permissionNodes) {
        return authorized.literal("check", PermissionNodes.ADMIN_USER_CHECK).executes(context -> authorized.run(context, PermissionNodes.ADMIN_USER_CHECK, handlers::checkUsage))
                .then(nodeArgument(permissionNodes).executes(context -> authorized.run(context, PermissionNodes.ADMIN_USER_CHECK, handlers::check)));
    }

    private static <S> LiteralArgumentBuilder<S> explainCommand(AuthorizedCommand<S> authorized, Handlers<S> handlers, SuggestionProvider<S> permissionNodes) {
        return authorized.literal("explain", PermissionNodes.ADMIN_USER_EXPLAIN)
                .executes(context -> authorized.run(context, PermissionNodes.ADMIN_USER_EXPLAIN, handlers::explainUsage))
                .then(nodeArgument(permissionNodes).executes(context -> authorized.run(context, PermissionNodes.ADMIN_USER_EXPLAIN, handlers::explain)));
    }

    private static String[] userPermissions() {
        return new String[]{PermissionNodes.ADMIN_USER_INFO, PermissionNodes.ADMIN_USER_LIST, PermissionNodes.ADMIN_USER_GET, PermissionNodes.ADMIN_USER_SET,
                PermissionNodes.ADMIN_USER_CLEAR, PermissionNodes.ADMIN_USER_CLEAR_ALL, PermissionNodes.ADMIN_USER_CHECK, PermissionNodes.ADMIN_USER_EXPLAIN,
                PermissionNodes.ADMIN_USER_GROUPS, PermissionNodes.ADMIN_USER_GROUP_ADD, PermissionNodes.ADMIN_USER_GROUP_REMOVE, PermissionNodes.ADMIN_USER_DISPLAY_VIEW,
                PermissionNodes.ADMIN_USER_DISPLAY_SET, PermissionNodes.ADMIN_USER_DISPLAY_CLEAR};
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

    private static <S> RequiredArgumentBuilder<S, String> groupAddArgument(ClutchPermsCommandEnvironment<S> environment) {
        return RequiredArgumentBuilder.<S, String>argument(CommandArguments.GROUP, StringArgumentType.word())
                .suggests((context, builder) -> suggestAddGroups(environment, context, builder));
    }

    private static <S> RequiredArgumentBuilder<S, String> groupRemoveArgument(ClutchPermsCommandEnvironment<S> environment) {
        return RequiredArgumentBuilder.<S, String>argument(CommandArguments.GROUP, StringArgumentType.word())
                .suggests((context, builder) -> suggestRemoveGroups(environment, context, builder));
    }

    public static <S> CompletableFuture<Suggestions> suggestUserTargets(ClutchPermsCommandEnvironment<S> environment, S source, SuggestionsBuilder builder) {
        String remaining = builder.getRemainingLowerCase();
        StringRange range = StringRange.between(builder.getStart(), builder.getInput().length());
        List<Suggestion> suggestions = Stream
                .concat(environment.onlineSubjectNames(source).stream(), environment.subjectMetadataService().getSubjects().values().stream().map(SubjectMetadata::lastKnownName))
                .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(remaining)).sorted(SUGGESTION_ORDER).distinct().filter(name -> !name.equals(builder.getRemaining()))
                .map(name -> new Suggestion(range, name)).toList();
        return CompletableFuture.completedFuture(new Suggestions(range, suggestions));
    }

    private static <S> CompletableFuture<Suggestions> suggestAddGroups(ClutchPermsCommandEnvironment<S> environment, CommandContext<S> context, SuggestionsBuilder builder) {
        String remaining = builder.getRemainingLowerCase();
        Set<String> assignedGroups = resolveSuggestionSubjectId(environment, context).map(environment.groupService()::getSubjectGroups).orElse(Set.of());
        environment.groupService().getGroups().stream().sorted(Comparator.naturalOrder()).filter(group -> group.toLowerCase(Locale.ROOT).startsWith(remaining))
                .filter(group -> !GroupService.DEFAULT_GROUP.equals(group)).filter(group -> !assignedGroups.contains(group)).forEach(builder::suggest);
        return builder.buildFuture();
    }

    private static <S> CompletableFuture<Suggestions> suggestRemoveGroups(ClutchPermsCommandEnvironment<S> environment, CommandContext<S> context, SuggestionsBuilder builder) {
        Optional<UUID> subjectId = resolveSuggestionSubjectId(environment, context);
        if (subjectId.isEmpty()) {
            return builder.buildFuture();
        }

        String remaining = builder.getRemainingLowerCase();
        environment.groupService().getSubjectGroups(subjectId.get()).stream().sorted(Comparator.naturalOrder())
                .filter(group -> group.toLowerCase(Locale.ROOT).startsWith(remaining)).filter(group -> !GroupService.DEFAULT_GROUP.equals(group)).forEach(builder::suggest);
        return builder.buildFuture();
    }

    private static <S> Optional<UUID> resolveSuggestionSubjectId(ClutchPermsCommandEnvironment<S> environment, CommandContext<S> context) {
        String target = StringArgumentType.getString(context, CommandArguments.TARGET);
        Optional<CommandSubject> onlineSubject = environment.findOnlineSubject(context.getSource(), target);
        if (onlineSubject.isPresent()) {
            return onlineSubject.map(CommandSubject::id);
        }

        List<SubjectMetadata> knownSubjects = findKnownSuggestionSubjects(environment, target);
        if (knownSubjects.size() == 1) {
            return Optional.of(knownSubjects.getFirst().subjectId());
        }
        if (!knownSubjects.isEmpty()) {
            return Optional.empty();
        }

        try {
            return Optional.of(UUID.fromString(target));
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
    }

    private static <S> List<SubjectMetadata> findKnownSuggestionSubjects(ClutchPermsCommandEnvironment<S> environment, String target) {
        String normalizedTarget = target.toLowerCase(Locale.ROOT);
        return environment.subjectMetadataService().getSubjects().values().stream().filter(subject -> subject.lastKnownName().toLowerCase(Locale.ROOT).equals(normalizedTarget))
                .sorted(Comparator.comparing(SubjectMetadata::lastKnownName, String.CASE_INSENSITIVE_ORDER).thenComparing(SubjectMetadata::subjectId)).toList();
    }

    private UserSubcommand() {
    }
}
