package me.clutchy.clutchperms.forge;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

import me.clutchy.clutchperms.common.PermissionService;
import me.clutchy.clutchperms.common.command.ClutchPermsCommandEnvironment;
import me.clutchy.clutchperms.common.command.ClutchPermsCommands;
import me.clutchy.clutchperms.common.command.CommandSourceKind;
import me.clutchy.clutchperms.common.command.CommandSubject;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.rcon.RconConsoleSource;

/**
 * Adapts Forge command sources to the shared ClutchPerms Brigadier command tree.
 */
final class ForgeClutchPermsCommand {

    static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> create(PermissionService permissionService) {
        return ClutchPermsCommands.builder(new ForgeCommandEnvironment(permissionService));
    }

    private ForgeClutchPermsCommand() {
    }

    private record ForgeCommandEnvironment(PermissionService permissionService) implements ClutchPermsCommandEnvironment<CommandSourceStack> {

        @Override
        public CommandSourceKind sourceKind(CommandSourceStack source) {
            if (source.getPlayer() != null) {
                return CommandSourceKind.PLAYER;
            }
            if (source.source instanceof MinecraftServer || source.source instanceof RconConsoleSource) {
                return CommandSourceKind.CONSOLE;
            }
            return CommandSourceKind.OTHER;
        }

        @Override
        public Optional<UUID> sourceSubjectId(CommandSourceStack source) {
            ServerPlayer player = source.getPlayer();
            if (player == null) {
                return Optional.empty();
            }
            return Optional.of(player.getUUID());
        }

        @Override
        public Optional<CommandSubject> findOnlineSubject(CommandSourceStack source, String target) {
            ServerPlayer player = source.getServer().getPlayerList().getPlayerByName(target);
            if (player == null) {
                return Optional.empty();
            }
            return Optional.of(new CommandSubject(player.getUUID(), player.getGameProfile().name()));
        }

        @Override
        public Collection<String> onlineSubjectNames(CommandSourceStack source) {
            return source.getOnlinePlayerNames();
        }

        @Override
        public void sendMessage(CommandSourceStack source, String message) {
            source.sendSuccess(() -> Component.literal(message), false);
        }
    }
}
