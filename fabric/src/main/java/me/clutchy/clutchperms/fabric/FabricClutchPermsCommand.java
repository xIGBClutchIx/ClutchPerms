package me.clutchy.clutchperms.fabric;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

import me.clutchy.clutchperms.common.GroupService;
import me.clutchy.clutchperms.common.PermissionResolver;
import me.clutchy.clutchperms.common.PermissionService;
import me.clutchy.clutchperms.common.SubjectMetadataService;
import me.clutchy.clutchperms.common.command.ClutchPermsCommandEnvironment;
import me.clutchy.clutchperms.common.command.ClutchPermsCommands;
import me.clutchy.clutchperms.common.command.CommandSourceKind;
import me.clutchy.clutchperms.common.command.CommandStatusDiagnostics;
import me.clutchy.clutchperms.common.command.CommandSubject;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * Adapts Fabric command sources to the shared ClutchPerms Brigadier command tree.
 */
final class FabricClutchPermsCommand {

    static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> create(Supplier<PermissionService> permissionService,
            Supplier<SubjectMetadataService> subjectMetadataService, Supplier<GroupService> groupService, Supplier<PermissionResolver> permissionResolver,
            Supplier<CommandStatusDiagnostics> statusDiagnostics, Runnable storageReloader, Runnable runtimePermissionRefresher) {
        return ClutchPermsCommands.builder(new FabricCommandEnvironment(permissionService, subjectMetadataService, groupService, permissionResolver, statusDiagnostics,
                storageReloader, runtimePermissionRefresher));
    }

    private FabricClutchPermsCommand() {
    }

    private record FabricCommandEnvironment(Supplier<PermissionService> permissionServiceSupplier, Supplier<SubjectMetadataService> subjectMetadataServiceSupplier,
            Supplier<GroupService> groupServiceSupplier, Supplier<PermissionResolver> permissionResolverSupplier, Supplier<CommandStatusDiagnostics> statusDiagnosticsSupplier,
            Runnable storageReloader, Runnable runtimePermissionRefresher) implements ClutchPermsCommandEnvironment<CommandSourceStack> {

        @Override
        public PermissionService permissionService() {
            return permissionServiceSupplier.get();
        }

        @Override
        public GroupService groupService() {
            return groupServiceSupplier.get();
        }

        @Override
        public PermissionResolver permissionResolver() {
            return permissionResolverSupplier.get();
        }

        @Override
        public SubjectMetadataService subjectMetadataService() {
            return subjectMetadataServiceSupplier.get();
        }

        @Override
        public CommandStatusDiagnostics statusDiagnostics() {
            return statusDiagnosticsSupplier.get();
        }

        @Override
        public void reloadStorage() {
            storageReloader.run();
        }

        @Override
        public void refreshRuntimePermissions() {
            runtimePermissionRefresher.run();
        }

        @Override
        public CommandSourceKind sourceKind(CommandSourceStack source) {
            if (source.getPlayer() != null) {
                return CommandSourceKind.PLAYER;
            }
            if (isConsoleSource(source)) {
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

        private static boolean isConsoleSource(CommandSourceStack source) {
            String textName = source.getTextName();
            return source.getEntity() == null && ("Server".equals(textName) || "Rcon".equalsIgnoreCase(textName));
        }
    }
}
