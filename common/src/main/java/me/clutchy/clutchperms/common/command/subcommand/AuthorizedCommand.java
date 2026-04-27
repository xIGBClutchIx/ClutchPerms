package me.clutchy.clutchperms.common.command.subcommand;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

/**
 * Runs a command action after checking the required ClutchPerms command permission.
 *
 * @param <S> platform command source type
 */
@FunctionalInterface
public interface AuthorizedCommand<S> {

    /**
     * Executes an authorized command action.
     *
     * @param context Brigadier command context
     * @param requiredPermission exact command permission node
     * @param command command action to run after authorization
     * @return Brigadier command result
     * @throws CommandSyntaxException when argument parsing fails
     */
    int run(CommandContext<S> context, String requiredPermission, Command<S> command) throws CommandSyntaxException;

    /**
     * Returns whether a source can use one exact command permission.
     *
     * @param source command source
     * @param requiredPermission exact command permission node
     * @return true when the source is authorized
     */
    default boolean canUse(S source, String requiredPermission) {
        return true;
    }

    /**
     * Returns whether a source can use any exact command permission in a branch.
     *
     * @param source command source
     * @param requiredPermissions exact command permission nodes
     * @return true when the source is authorized for at least one permission
     */
    default boolean canUseAny(S source, String... requiredPermissions) {
        for (String requiredPermission : requiredPermissions) {
            if (canUse(source, requiredPermission)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Creates a literal hidden from completions unless the source can use the required permission.
     *
     * @param literal command literal
     * @param requiredPermission exact command permission node
     * @return gated literal builder
     */
    default LiteralArgumentBuilder<S> literal(String literal, String requiredPermission) {
        return LiteralArgumentBuilder.<S>literal(literal).requires(source -> canUse(source, requiredPermission));
    }

    /**
     * Adds an exact permission completion gate to an argument builder.
     *
     * @param argument argument builder
     * @param requiredPermission exact command permission node
     * @param <T> argument builder type
     * @return gated argument builder
     */
    default <T extends ArgumentBuilder<S, T>> T requires(T argument, String requiredPermission) {
        return argument.requires(source -> canUse(source, requiredPermission));
    }

    /**
     * Creates a branch literal hidden from completions unless the source can use any child permission.
     *
     * @param literal command literal
     * @param requiredPermissions exact command permission nodes in the branch
     * @return gated literal builder
     */
    default LiteralArgumentBuilder<S> branch(String literal, String... requiredPermissions) {
        return LiteralArgumentBuilder.<S>literal(literal).requires(source -> canUseAny(source, requiredPermissions));
    }
}
