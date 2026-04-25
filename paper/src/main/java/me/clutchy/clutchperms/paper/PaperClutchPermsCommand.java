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
import me.clutchy.clutchperms.common.command.CommandMessage;
import me.clutchy.clutchperms.common.command.CommandSourceKind;
import me.clutchy.clutchperms.common.command.CommandStatusDiagnostics;
import me.clutchy.clutchperms.common.command.CommandSubject;
import me.clutchy.clutchperms.common.group.GroupService;
import me.clutchy.clutchperms.common.node.MutablePermissionNodeRegistry;
import me.clutchy.clutchperms.common.node.PermissionNodeRegistry;
import me.clutchy.clutchperms.common.permission.PermissionResolver;
import me.clutchy.clutchperms.common.permission.PermissionService;
import me.clutchy.clutchperms.common.storage.StorageBackupService;
import me.clutchy.clutchperms.common.subject.SubjectMetadataService;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;

/**
 * Adapts Paper command sources to the shared ClutchPerms Brigadier command tree.
 */
final class PaperClutchPermsCommand {

    static com.mojang.brigadier.tree.LiteralCommandNode<CommandSourceStack> create(ClutchPermsPaperPlugin plugin) {
        return ClutchPermsCommands.create(new PaperCommandEnvironment(plugin));
    }

    static com.mojang.brigadier.tree.LiteralCommandNode<CommandSourceStack> create(ClutchPermsPaperPlugin plugin, String rootLiteral) {
        return ClutchPermsCommands.create(new PaperCommandEnvironment(plugin), rootLiteral);
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
        public void validateStorage() {
            plugin.validateStorage();
        }

        @Override
        public StorageBackupService storageBackupService() {
            return plugin.getStorageBackupService();
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

        @Override
        public void sendMessage(CommandSourceStack source, CommandMessage message) {
            source.getSender().sendMessage(toComponent(message));
        }

        private static Component toComponent(CommandMessage message) {
            Component component = Component.empty();
            for (CommandMessage.Segment segment : message.segments()) {
                Component part = Component.text(segment.text(), color(segment.color()));
                if (segment.bold()) {
                    part = part.decorate(TextDecoration.BOLD);
                }
                component = component.append(part);
            }
            return component;
        }

        private static NamedTextColor color(CommandMessage.Color color) {
            return switch (color) {
                case AQUA -> NamedTextColor.AQUA;
                case GREEN -> NamedTextColor.GREEN;
                case RED -> NamedTextColor.RED;
                case YELLOW -> NamedTextColor.YELLOW;
                case GRAY -> NamedTextColor.GRAY;
                case WHITE -> NamedTextColor.WHITE;
            };
        }
    }
}
