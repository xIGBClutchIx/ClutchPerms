package me.clutchy.clutchperms.fabric;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

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
import me.clutchy.clutchperms.common.storage.StorageBackupService;
import me.clutchy.clutchperms.common.subject.SubjectMetadataService;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * Adapts Fabric command sources to the shared ClutchPerms Brigadier command tree.
 */
final class FabricClutchPermsCommand {

    static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> create(Supplier<PermissionService> permissionService,
            Supplier<SubjectMetadataService> subjectMetadataService, Supplier<GroupService> groupService, Supplier<PermissionNodeRegistry> permissionNodeRegistry,
            Supplier<MutablePermissionNodeRegistry> manualPermissionNodeRegistry, Supplier<PermissionResolver> permissionResolver,
            Supplier<CommandStatusDiagnostics> statusDiagnostics, Runnable storageReloader, Runnable storageValidator, Supplier<StorageBackupService> storageBackupService,
            Runnable runtimePermissionRefresher) {
        return ClutchPermsCommands.builder(new FabricCommandEnvironment(permissionService, subjectMetadataService, groupService, permissionNodeRegistry,
                manualPermissionNodeRegistry, permissionResolver, statusDiagnostics, storageReloader, storageValidator, storageBackupService, runtimePermissionRefresher));
    }

    private FabricClutchPermsCommand() {
    }

    private record FabricCommandEnvironment(Supplier<PermissionService> permissionServiceSupplier, Supplier<SubjectMetadataService> subjectMetadataServiceSupplier,
            Supplier<GroupService> groupServiceSupplier, Supplier<PermissionNodeRegistry> permissionNodeRegistrySupplier,
            Supplier<MutablePermissionNodeRegistry> manualPermissionNodeRegistrySupplier, Supplier<PermissionResolver> permissionResolverSupplier,
            Supplier<CommandStatusDiagnostics> statusDiagnosticsSupplier, Runnable storageReloader, Runnable storageValidator,
            Supplier<StorageBackupService> storageBackupServiceSupplier, Runnable runtimePermissionRefresher) implements ClutchPermsCommandEnvironment<CommandSourceStack> {

        @Override
        public PermissionService permissionService() {
            return permissionServiceSupplier.get();
        }

        @Override
        public GroupService groupService() {
            return groupServiceSupplier.get();
        }

        @Override
        public PermissionNodeRegistry permissionNodeRegistry() {
            return permissionNodeRegistrySupplier.get();
        }

        @Override
        public MutablePermissionNodeRegistry manualPermissionNodeRegistry() {
            return manualPermissionNodeRegistrySupplier.get();
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
        public void validateStorage() {
            storageValidator.run();
        }

        @Override
        public StorageBackupService storageBackupService() {
            return storageBackupServiceSupplier.get();
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
