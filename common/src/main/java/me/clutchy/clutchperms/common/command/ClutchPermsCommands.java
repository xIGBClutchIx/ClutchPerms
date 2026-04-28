package me.clutchy.clutchperms.common.command;

import java.time.Clock;
import java.util.List;
import java.util.Objects;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.tree.LiteralCommandNode;

/**
 * Builds the shared Brigadier command tree for ClutchPerms platform adapters.
 */
public final class ClutchPermsCommands {

    /**
     * Root command literal registered by every platform adapter.
     */
    public static final String ROOT_LITERAL = "clutchperms";

    /**
     * Root command aliases registered by every platform adapter.
     */
    public static final List<String> ROOT_ALIASES = List.of("cperms", "perms");

    /**
     * Root command literals registered by every platform adapter, including the primary command and aliases.
     */
    public static final List<String> ROOT_LITERALS = List.of(ROOT_LITERAL, "cperms", "perms");

    /**
     * Health line returned by the status command.
     */
    public static final String STATUS_MESSAGE = CommandLang.STATUS;

    /**
     * Creates the root ClutchPerms command node for a platform source type.
     *
     * @param environment platform command environment
     * @param <S> platform command source type
     * @return built root command node
     */
    public static <S> LiteralCommandNode<S> create(ClutchPermsCommandEnvironment<S> environment) {
        return create(environment, ROOT_LITERAL);
    }

    /**
     * Creates a ClutchPerms command node for one root literal.
     *
     * @param environment platform command environment
     * @param rootLiteral root command literal
     * @param <S> platform command source type
     * @return built root command node
     */
    public static <S> LiteralCommandNode<S> create(ClutchPermsCommandEnvironment<S> environment, String rootLiteral) {
        return new CommandTreeBuilder<>(Objects.requireNonNull(environment, "environment"), rootLiteral).create();
    }

    /**
     * Creates a direct shortcut command that mutates explicit membership in the protected {@code op} group.
     *
     * @param environment platform command environment
     * @param rootLiteral root command literal
     * @param addMembership {@code true} to add the target to {@code op}; {@code false} to remove the target
     * @param requiredPermission permission required for player sources
     * @param <S> platform command source type
     * @return built shortcut command node
     */
    public static <S> LiteralCommandNode<S> createOpGroupShortcut(ClutchPermsCommandEnvironment<S> environment, String rootLiteral, boolean addMembership,
            String requiredPermission) {
        return new CommandTreeBuilder<>(Objects.requireNonNull(environment, "environment"), rootLiteral).createOpGroupShortcut(addMembership,
                Objects.requireNonNull(requiredPermission, "requiredPermission"));
    }

    /**
     * Creates the root ClutchPerms command builder for platform dispatchers that register builders directly.
     *
     * @param environment platform command environment
     * @param <S> platform command source type
     * @return root command builder
     */
    public static <S> LiteralArgumentBuilder<S> builder(ClutchPermsCommandEnvironment<S> environment) {
        return builder(environment, ROOT_LITERAL);
    }

    /**
     * Creates a ClutchPerms command builder for one root literal.
     *
     * @param environment platform command environment
     * @param rootLiteral root command literal
     * @param <S> platform command source type
     * @return root command builder
     */
    public static <S> LiteralArgumentBuilder<S> builder(ClutchPermsCommandEnvironment<S> environment, String rootLiteral) {
        return new CommandTreeBuilder<>(Objects.requireNonNull(environment, "environment"), rootLiteral).builder();
    }

    static void resetDestructiveConfirmationsForTests() {
        CommandAuditSupport.resetDestructiveConfirmationsForTests();
    }

    static void setConfirmationClockForTests(Clock clock) {
        CommandAuditSupport.setConfirmationClockForTests(Objects.requireNonNull(clock, "clock"));
    }

    private ClutchPermsCommands() {
    }
}
