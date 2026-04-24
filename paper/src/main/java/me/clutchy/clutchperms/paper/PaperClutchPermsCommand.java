package me.clutchy.clutchperms.paper;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.command.RemoteConsoleCommandSender;
import org.bukkit.entity.Player;

import io.papermc.paper.command.brigadier.CommandSourceStack;

import me.clutchy.clutchperms.common.command.ClutchPermsCommandEnvironment;
import me.clutchy.clutchperms.common.command.ClutchPermsCommands;
import me.clutchy.clutchperms.common.command.CommandSourceKind;
import me.clutchy.clutchperms.common.command.CommandStatusDiagnostics;
import me.clutchy.clutchperms.common.command.CommandSubject;
import me.clutchy.clutchperms.common.group.GroupService;
import me.clutchy.clutchperms.common.node.MutablePermissionNodeRegistry;
import me.clutchy.clutchperms.common.node.PermissionNodeRegistry;
import me.clutchy.clutchperms.common.permission.PermissionResolver;
import me.clutchy.clutchperms.common.permission.PermissionService;
import me.clutchy.clutchperms.common.subject.SubjectMetadataService;

/**
 * Adapts Paper command sources to the shared ClutchPerms Brigadier command tree.
 */
final class PaperClutchPermsCommand {

    static com.mojang.brigadier.tree.LiteralCommandNode<CommandSourceStack> create(ClutchPermsPaperPlugin plugin) {
        return ClutchPermsCommands.create(new PaperCommandEnvironment(plugin));
    }

    private PaperClutchPermsCommand() {
    }

    private record PaperCommandEnvironment(ClutchPermsPaperPlugin plugin) implements ClutchPermsCommandEnvironment<CommandSourceStack> {

        @Override
        public PermissionService permissionService() {
            return plugin.getPermissionService();
        }

        @Override
        public GroupService groupService() {
            return plugin.getGroupService();
        }

        @Override
        public PermissionNodeRegistry permissionNodeRegistry() {
            return plugin.getPermissionNodeRegistry();
        }

        @Override
        public MutablePermissionNodeRegistry manualPermissionNodeRegistry() {
            return plugin.getManualPermissionNodeRegistry();
        }

        @Override
        public PermissionResolver permissionResolver() {
            return plugin.getPermissionResolver();
        }

        @Override
        public SubjectMetadataService subjectMetadataService() {
            return plugin.getSubjectMetadataService();
        }

        @Override
        public CommandStatusDiagnostics statusDiagnostics() {
            return plugin.getStatusDiagnostics();
        }

        @Override
        public void reloadStorage() {
            plugin.reloadStorage();
        }

        @Override
        public void refreshRuntimePermissions() {
            plugin.refreshRuntimePermissions();
        }

        @Override
        public CommandSourceKind sourceKind(CommandSourceStack source) {
            CommandSender sender = source.getSender();
            if (sender instanceof Player) {
                return CommandSourceKind.PLAYER;
            }
            if (sender instanceof ConsoleCommandSender || sender instanceof RemoteConsoleCommandSender) {
                return CommandSourceKind.CONSOLE;
            }
            return CommandSourceKind.OTHER;
        }

        @Override
        public Optional<UUID> sourceSubjectId(CommandSourceStack source) {
            if (source.getSender() instanceof Player player) {
                return Optional.of(player.getUniqueId());
            }
            return Optional.empty();
        }

        @Override
        public Optional<CommandSubject> findOnlineSubject(CommandSourceStack source, String target) {
            Player player = plugin.getServer().getPlayerExact(target);
            if (player == null) {
                return Optional.empty();
            }
            return Optional.of(new CommandSubject(player.getUniqueId(), player.getName()));
        }

        @Override
        public Collection<String> onlineSubjectNames(CommandSourceStack source) {
            return plugin.getServer().getOnlinePlayers().stream().map(Player::getName).toList();
        }

        @Override
        public void sendMessage(CommandSourceStack source, String message) {
            source.getSender().sendMessage(message);
        }
    }
}
