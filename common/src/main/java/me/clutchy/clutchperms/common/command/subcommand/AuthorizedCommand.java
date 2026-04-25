package me.clutchy.clutchperms.common.command.subcommand;

import com.mojang.brigadier.Command;
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
}
