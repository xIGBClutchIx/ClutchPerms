package me.clutchy.clutchperms.common.command;

import java.util.List;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

final class CommandPaging<S> {

    private final ClutchPermsCommandEnvironment<S> environment;

    private final CommandSupport<S> support;

    CommandPaging(ClutchPermsCommandEnvironment<S> environment, CommandSupport<S> support) {
        this.environment = environment;
        this.support = support;
    }

    void sendPagedRows(CommandContext<S> context, String title, List<PagedRow> rows, String pageCommand) throws CommandSyntaxException {
        int page = requestedPage(context, pageCommand);
        int pageSize = environment.config().commands().resultPageSize();
        int totalPages = totalPages(rows.size(), pageSize);
        requirePageInRange(context, page, totalPages, pageCommand);

        environment.sendMessage(context.getSource(), CommandLang.listHeader(title, page, totalPages));
        pageItems(rows, page, pageSize).forEach(row -> environment.sendMessage(context.getSource(), CommandLang.listRow(row.text(), row.command())));
        sendPageNavigation(context, pageCommand, page, totalPages);
    }

    void sendInfoRows(CommandContext<S> context, String title, List<PagedRow> rows) {
        environment.sendMessage(context.getSource(), CommandLang.heading(title + ":"));
        rows.forEach(row -> environment.sendMessage(context.getSource(), CommandLang.listRow(row.text(), row.command())));
    }

    int requestedPage(CommandContext<S> context, String pageCommand) throws CommandSyntaxException {
        if (!support.hasArgument(context, CommandArguments.PAGE)) {
            return 1;
        }
        String rawPage = StringArgumentType.getString(context, CommandArguments.PAGE);
        int page;
        try {
            page = Integer.parseInt(rawPage);
        } catch (NumberFormatException exception) {
            throw invalidPageFeedback(context, rawPage, pageCommand);
        }
        if (page < 1) {
            throw invalidPageFeedback(context, rawPage, pageCommand);
        }
        return page;
    }

    private void sendPageNavigation(CommandContext<S> context, String pageCommand, int page, int totalPages) {
        if (totalPages <= 1) {
            return;
        }
        String previousCommand = page > 1 ? support.currentFullCommand(context, pageCommand + " " + (page - 1)) : null;
        String nextCommand = page < totalPages ? support.currentFullCommand(context, pageCommand + " " + (page + 1)) : null;
        environment.sendMessage(context.getSource(), CommandLang.pageNavigation(previousCommand, page - 1, page, totalPages, nextCommand, page + 1));
    }

    private void requirePageInRange(CommandContext<S> context, int page, int totalPages, String pageCommand) throws CommandSyntaxException {
        if (page <= totalPages) {
            return;
        }
        int closestPage = Math.max(1, Math.min(page, totalPages));
        throw support.feedback(List.of(CommandLang.pageOutOfRange(page), CommandLang.availablePages(totalPages), CommandLang.tryHeader(),
                CommandLang.suggestion(support.currentRootLiteral(context), pageCommand + " " + closestPage)));
    }

    private CommandSupport.CommandFeedbackException invalidPageFeedback(CommandContext<S> context, String rawPage, String pageCommand) {
        return support.feedback(List.of(CommandLang.invalidPage(rawPage), CommandLang.pageStartsAtOne(), CommandLang.tryHeader(),
                CommandLang.suggestion(support.currentRootLiteral(context), pageCommand + " 1")));
    }

    static int totalPages(int itemCount, int pageSize) {
        return Math.max(1, (itemCount + pageSize - 1) / pageSize);
    }

    private static <T> List<T> pageItems(List<T> items, int page, int pageSize) {
        int from = (page - 1) * pageSize;
        int to = Math.min(items.size(), from + pageSize);
        return items.subList(from, to);
    }

    record PagedRow(String text, String command) {
    }
}
