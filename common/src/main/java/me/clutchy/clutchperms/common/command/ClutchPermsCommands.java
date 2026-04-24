package me.clutchy.clutchperms.common.command;

import java.util.Comparator;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.LiteralMessage;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.mojang.brigadier.tree.LiteralCommandNode;

import me.clutchy.clutchperms.common.PermissionNodes;
import me.clutchy.clutchperms.common.PermissionValue;

/**
 * Builds the shared Brigadier command tree for ClutchPerms platform adapters.
 */
public final class ClutchPermsCommands {

    /**
     * Root command literal registered by every platform adapter.
     */
    public static final String ROOT_LITERAL = "clutchperms";

    /**
     * Status message returned by the root command.
     */
    public static final String STATUS_MESSAGE = "ClutchPerms is running with a persisted permission service.";

    private static final String TARGET_ARGUMENT = "target";

    private static final String NODE_ARGUMENT = "node";

    private static final String VALUE_ARGUMENT = "value";

    private static final SimpleCommandExceptionType NO_PERMISSION = new SimpleCommandExceptionType(new LiteralMessage("You do not have permission to use ClutchPerms commands."));

    private static final SimpleCommandExceptionType OTHER_SOURCE_DENIED = new SimpleCommandExceptionType(
            new LiteralMessage("Only players and console sources can use ClutchPerms commands."));

    private static final DynamicCommandExceptionType UNKNOWN_TARGET = new DynamicCommandExceptionType(
            target -> new LiteralMessage("Unknown online player or invalid UUID: " + target));

    private static final DynamicCommandExceptionType INVALID_NODE = new DynamicCommandExceptionType(node -> new LiteralMessage("Invalid permission node: " + node));

    /**
     * Creates the root ClutchPerms command node for a platform source type.
     *
     * @param environment platform command environment
     * @param <S> platform command source type
     * @return built root command node
     */
    public static <S> LiteralCommandNode<S> create(ClutchPermsCommandEnvironment<S> environment) {
        return builder(environment).build();
    }

    /**
     * Creates the root ClutchPerms command builder for platform dispatchers that register builders directly.
     *
     * @param environment platform command environment
     * @param <S> platform command source type
     * @return root command builder
     */
    public static <S> LiteralArgumentBuilder<S> builder(ClutchPermsCommandEnvironment<S> environment) {
        Objects.requireNonNull(environment, "environment");

        return LiteralArgumentBuilder.<S>literal(ROOT_LITERAL).executes(context -> executeAuthorized(environment, context, source -> {
            environment.sendMessage(source, STATUS_MESSAGE);
            return Command.SINGLE_SUCCESS;
        })).then(userCommand(environment));
    }

    private static <S> LiteralArgumentBuilder<S> userCommand(ClutchPermsCommandEnvironment<S> environment) {
        RequiredArgumentBuilder<S, String> target = RequiredArgumentBuilder.<S, String>argument(TARGET_ARGUMENT, StringArgumentType.word())
                .suggests((context, builder) -> suggestOnlineSubjects(environment, context.getSource(), builder));

        return LiteralArgumentBuilder.<S>literal("user")
                .then(target.then(listCommand(environment)).then(getCommand(environment)).then(setCommand(environment)).then(clearCommand(environment)));
    }

    private static <S> LiteralArgumentBuilder<S> listCommand(ClutchPermsCommandEnvironment<S> environment) {
        return LiteralArgumentBuilder.<S>literal("list").executes(context -> executeAuthorized(environment, context, source -> listPermissions(environment, context)));
    }

    private static <S> LiteralArgumentBuilder<S> getCommand(ClutchPermsCommandEnvironment<S> environment) {
        return LiteralArgumentBuilder.<S>literal("get")
                .then(ClutchPermsCommands.<S>nodeArgument().executes(context -> executeAuthorized(environment, context, source -> getPermission(environment, context))));
    }

    private static <S> LiteralArgumentBuilder<S> setCommand(ClutchPermsCommandEnvironment<S> environment) {
        return LiteralArgumentBuilder.<S>literal("set")
                .then(ClutchPermsCommands.<S>nodeArgument().then(RequiredArgumentBuilder.<S, Boolean>argument(VALUE_ARGUMENT, BoolArgumentType.bool())
                        .executes(context -> executeAuthorized(environment, context, source -> setPermission(environment, context)))));
    }

    private static <S> LiteralArgumentBuilder<S> clearCommand(ClutchPermsCommandEnvironment<S> environment) {
        return LiteralArgumentBuilder.<S>literal("clear")
                .then(ClutchPermsCommands.<S>nodeArgument().executes(context -> executeAuthorized(environment, context, source -> clearPermission(environment, context))));
    }

    private static <S> RequiredArgumentBuilder<S, String> nodeArgument() {
        return RequiredArgumentBuilder.argument(NODE_ARGUMENT, StringArgumentType.word());
    }

    private static <S> int executeAuthorized(ClutchPermsCommandEnvironment<S> environment, CommandContext<S> context, CommandAction<S> action) throws CommandSyntaxException {
        S source = context.getSource();
        if (!canUse(environment, source)) {
            if (environment.sourceKind(source) == CommandSourceKind.OTHER) {
                throw OTHER_SOURCE_DENIED.create();
            }
            throw NO_PERMISSION.create();
        }

        return action.run(source);
    }

    private static <S> boolean canUse(ClutchPermsCommandEnvironment<S> environment, S source) {
        CommandSourceKind sourceKind = environment.sourceKind(source);
        if (sourceKind == CommandSourceKind.CONSOLE) {
            return true;
        }
        if (sourceKind != CommandSourceKind.PLAYER) {
            return false;
        }

        Optional<UUID> subjectId = environment.sourceSubjectId(source);
        return subjectId.isPresent() && environment.permissionService().hasPermission(subjectId.get(), PermissionNodes.ADMIN);
    }

    private static <S> int listPermissions(ClutchPermsCommandEnvironment<S> environment, CommandContext<S> context) throws CommandSyntaxException {
        CommandSubject subject = resolveSubject(environment, context);
        Map<String, PermissionValue> permissions = environment.permissionService().getPermissions(subject.id());
        if (permissions.isEmpty()) {
            environment.sendMessage(context.getSource(), "No permissions set for " + formatSubject(subject) + ".");
            return Command.SINGLE_SUCCESS;
        }

        String assignments = permissions.entrySet().stream().sorted(Map.Entry.comparingByKey()).map(entry -> entry.getKey() + "=" + entry.getValue().name())
                .collect(Collectors.joining(", "));
        environment.sendMessage(context.getSource(), "Permissions for " + formatSubject(subject) + ": " + assignments);
        return Command.SINGLE_SUCCESS;
    }

    private static <S> int getPermission(ClutchPermsCommandEnvironment<S> environment, CommandContext<S> context) throws CommandSyntaxException {
        CommandSubject subject = resolveSubject(environment, context);
        String node = getNode(context);
        PermissionValue value = environment.permissionService().getPermission(subject.id(), node);

        environment.sendMessage(context.getSource(), formatSubject(subject) + " has " + node + " = " + value.name() + ".");
        return Command.SINGLE_SUCCESS;
    }

    private static <S> int setPermission(ClutchPermsCommandEnvironment<S> environment, CommandContext<S> context) throws CommandSyntaxException {
        CommandSubject subject = resolveSubject(environment, context);
        String node = getNode(context);
        PermissionValue value = BoolArgumentType.getBool(context, VALUE_ARGUMENT) ? PermissionValue.TRUE : PermissionValue.FALSE;

        environment.permissionService().setPermission(subject.id(), node, value);
        environment.sendMessage(context.getSource(), "Set " + node + " for " + formatSubject(subject) + " to " + value.name() + ".");
        return Command.SINGLE_SUCCESS;
    }

    private static <S> int clearPermission(ClutchPermsCommandEnvironment<S> environment, CommandContext<S> context) throws CommandSyntaxException {
        CommandSubject subject = resolveSubject(environment, context);
        String node = getNode(context);

        environment.permissionService().clearPermission(subject.id(), node);
        environment.sendMessage(context.getSource(), "Cleared " + node + " for " + formatSubject(subject) + ".");
        return Command.SINGLE_SUCCESS;
    }

    private static <S> CommandSubject resolveSubject(ClutchPermsCommandEnvironment<S> environment, CommandContext<S> context) throws CommandSyntaxException {
        String target = StringArgumentType.getString(context, TARGET_ARGUMENT);
        Optional<CommandSubject> onlineSubject = environment.findOnlineSubject(context.getSource(), target);
        if (onlineSubject.isPresent()) {
            return onlineSubject.get();
        }

        try {
            UUID subjectId = UUID.fromString(target);
            return new CommandSubject(subjectId, subjectId.toString());
        } catch (IllegalArgumentException exception) {
            throw UNKNOWN_TARGET.create(target);
        }
    }

    private static <S> String getNode(CommandContext<S> context) throws CommandSyntaxException {
        String node = StringArgumentType.getString(context, NODE_ARGUMENT);
        if (node.trim().isEmpty()) {
            throw INVALID_NODE.create(node);
        }
        return node;
    }

    private static <S> CompletableFuture<Suggestions> suggestOnlineSubjects(ClutchPermsCommandEnvironment<S> environment, S source, SuggestionsBuilder builder) {
        environment.onlineSubjectNames(source).stream().sorted(Comparator.naturalOrder()).forEach(builder::suggest);
        return builder.buildFuture();
    }

    private static String formatSubject(CommandSubject subject) {
        return subject.displayName() + " (" + subject.id() + ")";
    }

    private ClutchPermsCommands() {
    }

    @FunctionalInterface
    private interface CommandAction<S> {

        int run(S source) throws CommandSyntaxException;
    }
}
