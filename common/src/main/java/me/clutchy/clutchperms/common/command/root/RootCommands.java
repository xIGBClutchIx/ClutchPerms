package me.clutchy.clutchperms.common.command;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import com.google.gson.JsonObject;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import me.clutchy.clutchperms.common.audit.AuditEntry;
import me.clutchy.clutchperms.common.permission.PermissionNodes;
import me.clutchy.clutchperms.common.permission.PermissionResolverCacheStats;

final class RootCommands<S> {

    private static final String DAYS_ARGUMENT = "days";

    private static final String COUNT_ARGUMENT = "count";

    private static final String AUDIT_ID_ARGUMENT = "id";

    private final CommandSupport<S> support;

    private final ClutchPermsCommandEnvironment<S> environment;

    RootCommands(CommandSupport<S> support) {
        this.support = support;
        this.environment = support.environment();
    }

    void attach(LiteralArgumentBuilder<S> root) {
        root.executes(context -> support.executeAuthorized(context, PermissionNodes.ADMIN_HELP, ignored -> sendCommandList(context))).then(helpCommand()).then(statusCommand())
                .then(reloadCommand()).then(validateCommand()).then(historyCommand()).then(undoCommand());
    }

    private LiteralArgumentBuilder<S> helpCommand() {
        return LiteralArgumentBuilder.<S>literal("help").requires(source -> support.canUse(source, PermissionNodes.ADMIN_HELP))
                .executes(context -> support.executeAuthorized(context, PermissionNodes.ADMIN_HELP, ignored -> sendCommandList(context)))
                .then(RequiredArgumentBuilder.<S, String>argument(CommandArguments.PAGE, StringArgumentType.word())
                        .executes(context -> support.executeAuthorized(context, PermissionNodes.ADMIN_HELP, ignored -> sendCommandList(context))));
    }

    private LiteralArgumentBuilder<S> statusCommand() {
        return LiteralArgumentBuilder.<S>literal("status").requires(source -> support.canUse(source, PermissionNodes.ADMIN_STATUS))
                .executes(context -> support.executeAuthorized(context, PermissionNodes.ADMIN_STATUS, ignored -> sendStatus(context)));
    }

    private LiteralArgumentBuilder<S> reloadCommand() {
        return LiteralArgumentBuilder.<S>literal("reload").requires(source -> support.canUse(source, PermissionNodes.ADMIN_RELOAD))
                .executes(context -> support.executeAuthorized(context, PermissionNodes.ADMIN_RELOAD, ignored -> reloadStorage(context)));
    }

    private LiteralArgumentBuilder<S> validateCommand() {
        return LiteralArgumentBuilder.<S>literal("validate").requires(source -> support.canUse(source, PermissionNodes.ADMIN_VALIDATE))
                .executes(context -> support.executeAuthorized(context, PermissionNodes.ADMIN_VALIDATE, ignored -> validateStorage(context)));
    }

    private LiteralArgumentBuilder<S> historyCommand() {
        return LiteralArgumentBuilder.<S>literal("history").requires(source -> support.canUseAny(source, PermissionNodes.ADMIN_HISTORY, PermissionNodes.ADMIN_HISTORY_PRUNE))
                .executes(context -> support.executeAuthorized(context, PermissionNodes.ADMIN_HISTORY, ignored -> listHistory(context)))
                .then(LiteralArgumentBuilder.<S>literal("prune").requires(source -> support.canUse(source, PermissionNodes.ADMIN_HISTORY_PRUNE))
                        .then(LiteralArgumentBuilder.<S>literal("days")
                                .then(RequiredArgumentBuilder.<S, Integer>argument(DAYS_ARGUMENT, IntegerArgumentType.integer(1))
                                        .executes(context -> support.executeAuthorized(context, PermissionNodes.ADMIN_HISTORY_PRUNE, ignored -> pruneHistoryDays(context)))))
                        .then(LiteralArgumentBuilder.<S>literal("count")
                                .then(RequiredArgumentBuilder.<S, Integer>argument(COUNT_ARGUMENT, IntegerArgumentType.integer(1))
                                        .executes(context -> support.executeAuthorized(context, PermissionNodes.ADMIN_HISTORY_PRUNE, ignored -> pruneHistoryCount(context))))))
                .then(RequiredArgumentBuilder.<S, String>argument(CommandArguments.PAGE, StringArgumentType.word())
                        .executes(context -> support.executeAuthorized(context, PermissionNodes.ADMIN_HISTORY, ignored -> listHistory(context))));
    }

    private LiteralArgumentBuilder<S> undoCommand() {
        return LiteralArgumentBuilder.<S>literal("undo").requires(source -> support.canUse(source, PermissionNodes.ADMIN_UNDO))
                .executes(context -> support.executeAuthorized(context, PermissionNodes.ADMIN_UNDO, ignored -> sendUndoUsage(context)))
                .then(RequiredArgumentBuilder.<S, Long>argument(AUDIT_ID_ARGUMENT, LongArgumentType.longArg(1))
                        .executes(context -> support.executeAuthorized(context, PermissionNodes.ADMIN_UNDO, ignored -> support.audit().undoAuditEntry(context))));
    }

    private int sendCommandList(CommandContext<S> context) throws CommandSyntaxException {
        int page = support.paging().requestedPage(context, "help");
        int pageSize = environment.config().commands().helpPageSize();
        int totalPages = CommandPaging.totalPages(CommandCatalogs.COMMAND_HELP.size(), pageSize);
        if (page > totalPages) {
            throw support.feedback(List.of(CommandLang.pageOutOfRange(page), CommandLang.availablePages(totalPages), CommandLang.tryHeader(),
                    CommandLang.suggestion(support.currentRootLiteral(context), "help " + Math.max(1, Math.min(page, totalPages)))));
        }

        environment.sendMessage(context.getSource(), CommandLang.commandListHeader(page, totalPages));
        List<CommandCatalogs.CommandHelpEntry> rows = CommandCatalogs.COMMAND_HELP.subList((page - 1) * pageSize, Math.min(CommandCatalogs.COMMAND_HELP.size(), page * pageSize));
        String rootLiteral = support.currentRootLiteral(context);
        rows.forEach(entry -> environment.sendMessage(context.getSource(), CommandLang.commandHelpEntry(rootLiteral, entry.syntax(), entry.permission(), entry.description())));
        if (totalPages > 1) {
            String previousCommand = page > 1 ? support.formatting().fullCommand(rootLiteral, "help " + (page - 1)) : null;
            String nextCommand = page < totalPages ? support.formatting().fullCommand(rootLiteral, "help " + (page + 1)) : null;
            environment.sendMessage(context.getSource(), CommandLang.pageNavigation(previousCommand, page - 1, page, totalPages, nextCommand, page + 1));
        }
        return Command.SINGLE_SUCCESS;
    }

    private int sendStatus(CommandContext<S> context) {
        CommandStatusDiagnostics diagnostics = environment.statusDiagnostics();
        environment.sendMessage(context.getSource(), CommandLang.status());
        environment.sendMessage(context.getSource(), CommandLang.statusDatabaseFile(diagnostics.databaseFile()));
        environment.sendMessage(context.getSource(), CommandLang.statusConfigFile(diagnostics.configFile()));
        environment.sendMessage(context.getSource(), CommandLang.statusBackupRetention(environment.config().backups().retentionLimit()));
        environment.sendMessage(context.getSource(),
                CommandLang.backupScheduleEnabled(environment.config().backups().schedule().enabled(), environment.scheduledBackupStatus().running()));
        environment.sendMessage(context.getSource(),
                CommandLang.statusAuditRetention(environment.config().audit().enabled(), environment.config().audit().maxAgeDays(), environment.config().audit().maxEntries()));
        environment.sendMessage(context.getSource(),
                CommandLang.statusCommandPageSizes(environment.config().commands().helpPageSize(), environment.config().commands().resultPageSize()));
        environment.sendMessage(context.getSource(), CommandLang.statusChatFormatting(environment.config().chat().enabled()));
        environment.sendMessage(context.getSource(), CommandLang.statusKnownSubjects(environment.subjectMetadataService().getSubjects().size()));
        environment.sendMessage(context.getSource(), CommandLang.statusKnownGroups(environment.groupService().getGroups().size()));
        environment.sendMessage(context.getSource(), CommandLang.statusKnownTracks(environment.trackService().getTracks().size()));
        environment.sendMessage(context.getSource(), CommandLang.statusKnownNodes(environment.permissionNodeRegistry().getKnownNodes().size()));
        PermissionResolverCacheStats cacheStats = environment.permissionResolver().cacheStats();
        environment.sendMessage(context.getSource(), CommandLang.statusResolverCache(cacheStats.subjects(), cacheStats.nodeResults(), cacheStats.effectiveSnapshots()));
        environment.sendMessage(context.getSource(), CommandLang.statusRuntimeBridge(diagnostics.runtimeBridgeStatus()));
        return Command.SINGLE_SUCCESS;
    }

    private int reloadStorage(CommandContext<S> context) throws CommandSyntaxException {
        try {
            environment.reloadStorage();
            environment.refreshRuntimePermissions();
        } catch (RuntimeException exception) {
            throw support.reloadFailed(exception);
        }
        environment.sendMessage(context.getSource(), CommandLang.reloadSuccess());
        return Command.SINGLE_SUCCESS;
    }

    private int validateStorage(CommandContext<S> context) throws CommandSyntaxException {
        try {
            environment.validateStorage();
        } catch (RuntimeException exception) {
            throw support.validateFailed(exception);
        }
        environment.sendMessage(context.getSource(), CommandLang.validateSuccess());
        return Command.SINGLE_SUCCESS;
    }

    private int listHistory(CommandContext<S> context) throws CommandSyntaxException {
        List<AuditEntry> entries = environment.auditLogService().listNewestFirst();
        if (entries.isEmpty()) {
            environment.sendMessage(context.getSource(), CommandLang.historyEmpty());
            return Command.SINGLE_SUCCESS;
        }

        String rootLiteral = support.currentRootLiteral(context);
        List<CommandPaging.PagedRow> rows = entries.stream().map(entry -> support.audit().historyRow(rootLiteral, entry)).toList();
        support.paging().sendPagedRows(context, "History", rows, "history");
        return Command.SINGLE_SUCCESS;
    }

    private int pruneHistoryDays(CommandContext<S> context) throws CommandSyntaxException {
        int days = IntegerArgumentType.getInteger(context, DAYS_ARGUMENT);
        Instant cutoff = Instant.now().minus(Duration.ofDays(days));
        int matchedRows = (int) environment.auditLogService().listNewestFirst().stream().filter(entry -> entry.timestamp().isBefore(cutoff)).count();
        if (matchedRows == 0) {
            environment.sendMessage(context.getSource(), CommandLang.historyPruneEmpty());
            return Command.SINGLE_SUCCESS;
        }
        if (!support.audit().confirmDestructiveCommand(context, support.audit().confirmationOperation("history-prune-days", Integer.toString(days)))) {
            return Command.SINGLE_SUCCESS;
        }

        matchedRows = (int) environment.auditLogService().listNewestFirst().stream().filter(entry -> entry.timestamp().isBefore(cutoff)).count();
        if (matchedRows == 0) {
            environment.sendMessage(context.getSource(), CommandLang.historyPruneEmpty());
            return Command.SINGLE_SUCCESS;
        }
        JsonObject before = new JsonObject();
        before.addProperty("matchedRows", matchedRows);
        JsonObject after = new JsonObject();
        after.addProperty("undoable", false);
        support.audit().appendPruneAudit(context, "history.prune.days", "history-prune-days:" + days, "history prune days " + days, before.toString(), after.toString());
        int deleted = environment.auditLogService().pruneOlderThan(cutoff);
        environment.sendMessage(context.getSource(), CommandLang.historyPruned(deleted));
        return Command.SINGLE_SUCCESS;
    }

    private int pruneHistoryCount(CommandContext<S> context) throws CommandSyntaxException {
        int count = IntegerArgumentType.getInteger(context, COUNT_ARGUMENT);
        int existingRows = environment.auditLogService().listNewestFirst().size();
        int matchedRows = Math.max(0, existingRows + 1 - count);
        if (matchedRows == 0) {
            environment.sendMessage(context.getSource(), CommandLang.historyPruneEmpty());
            return Command.SINGLE_SUCCESS;
        }
        if (!support.audit().confirmDestructiveCommand(context, support.audit().confirmationOperation("history-prune-count", Integer.toString(count)))) {
            return Command.SINGLE_SUCCESS;
        }

        existingRows = environment.auditLogService().listNewestFirst().size();
        matchedRows = Math.max(0, existingRows + 1 - count);
        if (matchedRows == 0) {
            environment.sendMessage(context.getSource(), CommandLang.historyPruneEmpty());
            return Command.SINGLE_SUCCESS;
        }
        JsonObject before = new JsonObject();
        before.addProperty("matchedRows", matchedRows);
        JsonObject after = new JsonObject();
        after.addProperty("undoable", false);
        support.audit().appendPruneAudit(context, "history.prune.count", "history-prune-count:" + count, "history prune count " + count, before.toString(), after.toString());
        int deleted = environment.auditLogService().pruneBeyondNewest(count);
        environment.sendMessage(context.getSource(), CommandLang.historyPruned(deleted));
        return Command.SINGLE_SUCCESS;
    }

    private int sendUndoUsage(CommandContext<S> context) {
        return support.sendUsage(context, "Missing history id.", "Choose an undoable history entry id.", List.of("undo <id>"));
    }
}
