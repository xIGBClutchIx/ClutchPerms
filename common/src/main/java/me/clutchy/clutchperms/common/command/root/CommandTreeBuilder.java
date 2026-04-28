package me.clutchy.clutchperms.common.command;

import java.util.Objects;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.tree.LiteralCommandNode;

final class CommandTreeBuilder<S> {

    private final CommandSupport<S> support;

    CommandTreeBuilder(ClutchPermsCommandEnvironment<S> environment, String rootLiteral) {
        this.support = new CommandSupport<>(Objects.requireNonNull(environment, "environment"), normalizeRootLiteral(rootLiteral));
    }

    LiteralArgumentBuilder<S> builder() {
        LiteralArgumentBuilder<S> root = LiteralArgumentBuilder.literal(support.rootLiteral());
        new RootCommands<>(support).attach(root);
        root.then(new ConfigSubcommand<>(support).builder());
        root.then(new BackupSubcommand<>(support).builder());
        root.then(new UserSubcommand<>(support).builder());
        root.then(new GroupSubcommand<>(support).builder());
        root.then(new TrackSubcommand<>(support).builder());
        root.then(new UsersSubcommand<>(support).builder());
        root.then(new NodesSubcommand<>(support).builder());
        return root;
    }

    LiteralCommandNode<S> create() {
        return builder().build();
    }

    LiteralCommandNode<S> createOpGroupShortcut(boolean addMembership, String requiredPermission) {
        UserSubcommand<S> userCommands = new UserSubcommand<>(support);
        return LiteralArgumentBuilder.<S>literal(support.rootLiteral()).requires(source -> support.canUse(source, requiredPermission))
                .executes(context -> support.executeAuthorized(context, requiredPermission, ignored -> userCommands.opShortcutUsage(context)))
                .then(RequiredArgumentBuilder.<S, String>argument(CommandArguments.TARGET, StringArgumentType.word())
                        .suggests((context, builder) -> support.targets().suggestUserTargets(context.getSource(), builder))
                        .executes(context -> support.executeAuthorizedForSubject(context, requiredPermission,
                                (source, subject) -> userCommands.mutateOpGroupMembership(context, subject, addMembership))))
                .build();
    }

    static String normalizeRootLiteral(String rootLiteral) {
        String literal = Objects.requireNonNull(rootLiteral, "rootLiteral").trim();
        if (literal.isEmpty()) {
            throw new IllegalArgumentException("root literal must not be blank");
        }
        return literal;
    }
}
