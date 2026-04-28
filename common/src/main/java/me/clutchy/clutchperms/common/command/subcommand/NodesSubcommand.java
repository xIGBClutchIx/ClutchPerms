package me.clutchy.clutchperms.common.command;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import me.clutchy.clutchperms.common.node.KnownPermissionNode;
import me.clutchy.clutchperms.common.permission.PermissionNodes;

final class NodesSubcommand<S> {

    private final CommandSupport<S> support;

    private final ClutchPermsCommandEnvironment<S> environment;

    NodesSubcommand(CommandSupport<S> support) {
        this.support = support;
        this.environment = support.environment();
    }

    LiteralArgumentBuilder<S> builder() {
        return LiteralArgumentBuilder.<S>literal("nodes")
                .requires(source -> support.canUseAny(source, PermissionNodes.ADMIN_NODES_LIST, PermissionNodes.ADMIN_NODES_SEARCH, PermissionNodes.ADMIN_NODES_ADD,
                        PermissionNodes.ADMIN_NODES_REMOVE))
                .executes(context -> support.executeAuthorized(context, PermissionNodes.ADMIN_NODES_LIST, ignored -> usage(context)))
                .then(LiteralArgumentBuilder.<S>literal("list").requires(source -> support.canUse(source, PermissionNodes.ADMIN_NODES_LIST))
                        .executes(context -> support.executeAuthorized(context, PermissionNodes.ADMIN_NODES_LIST, ignored -> list(context)))
                        .then(RequiredArgumentBuilder.<S, String>argument(CommandArguments.PAGE, StringArgumentType.word())
                                .executes(context -> support.executeAuthorized(context, PermissionNodes.ADMIN_NODES_LIST, ignored -> list(context)))))
                .then(LiteralArgumentBuilder.<S>literal("search").requires(source -> support.canUse(source, PermissionNodes.ADMIN_NODES_SEARCH))
                        .executes(context -> support.executeAuthorized(context, PermissionNodes.ADMIN_NODES_SEARCH, ignored -> searchUsage(context)))
                        .then(RequiredArgumentBuilder.<S, String>argument(CommandArguments.QUERY, StringArgumentType.word())
                                .executes(context -> support.executeAuthorized(context, PermissionNodes.ADMIN_NODES_SEARCH, ignored -> search(context)))
                                .then(RequiredArgumentBuilder.<S, String>argument(CommandArguments.PAGE, StringArgumentType.word())
                                        .executes(context -> support.executeAuthorized(context, PermissionNodes.ADMIN_NODES_SEARCH, ignored -> search(context))))))
                .then(LiteralArgumentBuilder.<S>literal("add").requires(source -> support.canUse(source, PermissionNodes.ADMIN_NODES_ADD))
                        .executes(context -> support.executeAuthorized(context, PermissionNodes.ADMIN_NODES_ADD, ignored -> addUsage(context)))
                        .then(RequiredArgumentBuilder.<S, String>argument(CommandArguments.NODE, StringArgumentType.greedyString())
                                .executes(context -> support.executeAuthorized(context, PermissionNodes.ADMIN_NODES_ADD, ignored -> add(context)))))
                .then(LiteralArgumentBuilder.<S>literal("remove").requires(source -> support.canUse(source, PermissionNodes.ADMIN_NODES_REMOVE))
                        .executes(context -> support.executeAuthorized(context, PermissionNodes.ADMIN_NODES_REMOVE, ignored -> removeUsage(context)))
                        .then(RequiredArgumentBuilder.<S, String>argument(CommandArguments.NODE, StringArgumentType.greedyString())
                                .executes(context -> support.executeAuthorized(context, PermissionNodes.ADMIN_NODES_REMOVE, ignored -> remove(context)))))
                .then(RequiredArgumentBuilder.<S, String>argument(CommandArguments.UNKNOWN, StringArgumentType.word())
                        .requires(source -> support.canUse(source, PermissionNodes.ADMIN_NODES_LIST))
                        .executes(context -> support.executeAuthorized(context, PermissionNodes.ADMIN_NODES_LIST, ignored -> unknownUsage(context))));
    }

    private int usage(CommandContext<S> context) {
        return support.sendUsage(context, "Missing nodes command.", "List, search, add, or remove known permission nodes.", CommandCatalogs.nodesUsages());
    }

    private int list(CommandContext<S> context) throws CommandSyntaxException {
        List<KnownPermissionNode> nodes = environment.permissionNodeRegistry().getKnownNodes().stream().sorted(Comparator.comparing(KnownPermissionNode::node)).toList();
        if (nodes.isEmpty()) {
            environment.sendMessage(context.getSource(), CommandLang.nodesEmpty());
            return Command.SINGLE_SUCCESS;
        }
        String rootLiteral = support.currentRootLiteral(context);
        List<CommandPaging.PagedRow> rows = nodes.stream()
                .map(node -> new CommandPaging.PagedRow(support.formatting().formatKnownNode(node), support.formatting().knownNodeCommand(rootLiteral, node))).toList();
        support.paging().sendPagedRows(context, "Known permission nodes", rows, "nodes list");
        return Command.SINGLE_SUCCESS;
    }

    private int searchUsage(CommandContext<S> context) {
        return support.sendUsage(context, "Missing node search query.", "Provide part of a known node or description.", List.of("nodes search <query>"));
    }

    private int search(CommandContext<S> context) throws CommandSyntaxException {
        String query = StringArgumentType.getString(context, CommandArguments.QUERY).trim();
        String normalizedQuery = query.toLowerCase(Locale.ROOT);
        if (normalizedQuery.isEmpty()) {
            environment.sendMessage(context.getSource(), CommandLang.nodesSearchEmpty(query));
            return Command.SINGLE_SUCCESS;
        }

        List<KnownPermissionNode> nodes = environment.permissionNodeRegistry().getKnownNodes().stream()
                .filter(node -> node.node().contains(normalizedQuery) || node.description().toLowerCase(Locale.ROOT).contains(normalizedQuery))
                .sorted(Comparator.comparing(KnownPermissionNode::node)).toList();
        if (nodes.isEmpty()) {
            environment.sendMessage(context.getSource(), CommandLang.nodesSearchEmpty(query));
            return Command.SINGLE_SUCCESS;
        }

        String rootLiteral = support.currentRootLiteral(context);
        List<CommandPaging.PagedRow> rows = nodes.stream()
                .map(node -> new CommandPaging.PagedRow(support.formatting().formatKnownNode(node), support.formatting().knownNodeCommand(rootLiteral, node))).toList();
        support.paging().sendPagedRows(context, "Matched known permission nodes", rows, "nodes search " + query);
        return Command.SINGLE_SUCCESS;
    }

    private int addUsage(CommandContext<S> context) {
        return support.sendUsage(context, "Missing known permission node.", "Add an exact known permission node and optional description.",
                List.of("nodes add <node> [description]"));
    }

    private int add(CommandContext<S> context) throws CommandSyntaxException {
        String input = StringArgumentType.getString(context, CommandArguments.NODE).trim();
        int nodeEnd = firstWhitespaceIndex(input);
        String node = nodeEnd < 0 ? input : input.substring(0, nodeEnd);
        String description = nodeEnd < 0 ? "" : input.substring(nodeEnd).trim();
        String normalizedNode;
        try {
            normalizedNode = KnownPermissionNode.normalizeKnownNode(node);
            environment.manualPermissionNodeRegistry().addNode(normalizedNode, description);
        } catch (IllegalArgumentException | UnsupportedOperationException exception) {
            throw support.nodeOperationFailed(exception);
        }

        environment.sendMessage(context.getSource(), CommandLang.nodeAdded(normalizedNode));
        return Command.SINGLE_SUCCESS;
    }

    private int removeUsage(CommandContext<S> context) {
        return support.sendUsage(context, "Missing known permission node.", "Remove a manually registered known permission node.", List.of("nodes remove <node>"));
    }

    private int remove(CommandContext<S> context) throws CommandSyntaxException {
        String node = StringArgumentType.getString(context, CommandArguments.NODE);
        String normalizedNode;
        try {
            normalizedNode = KnownPermissionNode.normalizeKnownNode(node);
        } catch (IllegalArgumentException exception) {
            throw support.nodeOperationFailed(exception);
        }
        support.requireManuallyRegisteredNode(context, normalizedNode);
        try {
            environment.manualPermissionNodeRegistry().removeNode(normalizedNode);
        } catch (RuntimeException exception) {
            throw support.nodeOperationFailed(exception);
        }

        environment.sendMessage(context.getSource(), CommandLang.nodeRemoved(normalizedNode));
        return Command.SINGLE_SUCCESS;
    }

    private int unknownUsage(CommandContext<S> context) {
        return support.sendUsage(context, "Unknown nodes command: " + StringArgumentType.getString(context, CommandArguments.UNKNOWN),
                "Node commands manage the manual known-node registry.", CommandCatalogs.nodesUsages());
    }

    private int firstWhitespaceIndex(String value) {
        for (int index = 0; index < value.length(); index++) {
            if (Character.isWhitespace(value.charAt(index))) {
                return index;
            }
        }
        return -1;
    }
}
