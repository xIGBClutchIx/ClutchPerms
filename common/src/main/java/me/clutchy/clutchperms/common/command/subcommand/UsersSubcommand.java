package me.clutchy.clutchperms.common.command;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import me.clutchy.clutchperms.common.permission.PermissionNodes;
import me.clutchy.clutchperms.common.subject.SubjectMetadata;

final class UsersSubcommand<S> {

    private final CommandSupport<S> support;

    private final ClutchPermsCommandEnvironment<S> environment;

    UsersSubcommand(CommandSupport<S> support) {
        this.support = support;
        this.environment = support.environment();
    }

    LiteralArgumentBuilder<S> builder() {
        return LiteralArgumentBuilder.<S>literal("users").requires(source -> support.canUseAny(source, PermissionNodes.ADMIN_USERS_LIST, PermissionNodes.ADMIN_USERS_SEARCH))
                .executes(context -> support.executeAuthorized(context, PermissionNodes.ADMIN_USERS_LIST, ignored -> usage(context)))
                .then(LiteralArgumentBuilder.<S>literal("list").requires(source -> support.canUse(source, PermissionNodes.ADMIN_USERS_LIST))
                        .executes(context -> support.executeAuthorized(context, PermissionNodes.ADMIN_USERS_LIST, ignored -> list(context)))
                        .then(RequiredArgumentBuilder.<S, String>argument(CommandArguments.PAGE, StringArgumentType.word())
                                .executes(context -> support.executeAuthorized(context, PermissionNodes.ADMIN_USERS_LIST, ignored -> list(context)))))
                .then(LiteralArgumentBuilder.<S>literal("search").requires(source -> support.canUse(source, PermissionNodes.ADMIN_USERS_SEARCH))
                        .executes(context -> support.executeAuthorized(context, PermissionNodes.ADMIN_USERS_SEARCH, ignored -> searchUsage(context)))
                        .then(RequiredArgumentBuilder.<S, String>argument(CommandArguments.NAME, StringArgumentType.word())
                                .executes(context -> support.executeAuthorized(context, PermissionNodes.ADMIN_USERS_SEARCH, ignored -> search(context)))
                                .then(RequiredArgumentBuilder.<S, String>argument(CommandArguments.PAGE, StringArgumentType.word())
                                        .executes(context -> support.executeAuthorized(context, PermissionNodes.ADMIN_USERS_SEARCH, ignored -> search(context))))))
                .then(RequiredArgumentBuilder.<S, String>argument(CommandArguments.UNKNOWN, StringArgumentType.word())
                        .requires(source -> support.canUse(source, PermissionNodes.ADMIN_USERS_LIST))
                        .executes(context -> support.executeAuthorized(context, PermissionNodes.ADMIN_USERS_LIST, ignored -> unknownUsage(context))));
    }

    private int usage(CommandContext<S> context) {
        return support.sendUsage(context, "Missing users command.", "Search or list stored subject metadata.", CommandCatalogs.usersUsages());
    }

    private int list(CommandContext<S> context) throws CommandSyntaxException {
        Map<UUID, SubjectMetadata> subjects = environment.subjectMetadataService().getSubjects();
        if (subjects.isEmpty()) {
            environment.sendMessage(context.getSource(), CommandLang.usersEmpty());
            return Command.SINGLE_SUCCESS;
        }

        String rootLiteral = support.currentRootLiteral(context);
        List<CommandPaging.PagedRow> rows = subjects.values().stream()
                .sorted(Comparator.comparing(SubjectMetadata::lastKnownName, String.CASE_INSENSITIVE_ORDER).thenComparing(SubjectMetadata::subjectId))
                .map(subject -> new CommandPaging.PagedRow(support.formatting().formatSubjectMetadata(subject),
                        support.formatting().fullCommand(rootLiteral, "user " + subject.subjectId() + " list")))
                .toList();
        support.paging().sendPagedRows(context, "Known users", rows, "users list");
        return Command.SINGLE_SUCCESS;
    }

    private int searchUsage(CommandContext<S> context) {
        return support.sendUsage(context, "Missing user name query.", "Searches stored last-known names.", List.of("users search <name>"));
    }

    private int search(CommandContext<S> context) throws CommandSyntaxException {
        String query = StringArgumentType.getString(context, CommandArguments.NAME).trim();
        String normalizedQuery = query.toLowerCase(Locale.ROOT);
        if (normalizedQuery.isEmpty()) {
            environment.sendMessage(context.getSource(), CommandLang.usersSearchEmpty(query));
            return Command.SINGLE_SUCCESS;
        }

        String rootLiteral = support.currentRootLiteral(context);
        List<CommandPaging.PagedRow> rows = environment.subjectMetadataService().getSubjects().values().stream()
                .filter(subject -> subject.lastKnownName().toLowerCase(Locale.ROOT).contains(normalizedQuery))
                .sorted(Comparator.comparing(SubjectMetadata::lastKnownName, String.CASE_INSENSITIVE_ORDER).thenComparing(SubjectMetadata::subjectId))
                .map(subject -> new CommandPaging.PagedRow(support.formatting().formatSubjectMetadata(subject),
                        support.formatting().fullCommand(rootLiteral, "user " + subject.subjectId() + " list")))
                .toList();
        if (rows.isEmpty()) {
            environment.sendMessage(context.getSource(), CommandLang.usersSearchEmpty(query));
            return Command.SINGLE_SUCCESS;
        }

        support.paging().sendPagedRows(context, "Matched users", rows, "users search " + query);
        return Command.SINGLE_SUCCESS;
    }

    private int unknownUsage(CommandContext<S> context) {
        return support.sendUsage(context, "Unknown users command: " + StringArgumentType.getString(context, CommandArguments.UNKNOWN),
                "User lookup commands search stored subject metadata.", CommandCatalogs.usersUsages());
    }
}
