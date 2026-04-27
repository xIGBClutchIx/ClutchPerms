package me.clutchy.clutchperms.fabric;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

import me.clutchy.clutchperms.common.audit.AuditLogService;
import me.clutchy.clutchperms.common.audit.AuditLogServices;
import me.clutchy.clutchperms.common.command.ClutchPermsCommandEnvironment;
import me.clutchy.clutchperms.common.command.ClutchPermsCommands;
import me.clutchy.clutchperms.common.command.CommandMessage;
import me.clutchy.clutchperms.common.command.CommandSourceKind;
import me.clutchy.clutchperms.common.command.CommandStatusDiagnostics;
import me.clutchy.clutchperms.common.command.CommandSubject;
import me.clutchy.clutchperms.common.config.ClutchPermsConfig;
import me.clutchy.clutchperms.common.group.GroupService;
import me.clutchy.clutchperms.common.node.MutablePermissionNodeRegistry;
import me.clutchy.clutchperms.common.node.PermissionNodeRegistry;
import me.clutchy.clutchperms.common.permission.PermissionResolver;
import me.clutchy.clutchperms.common.permission.PermissionService;
import me.clutchy.clutchperms.common.storage.StorageBackupService;
import me.clutchy.clutchperms.common.storage.StorageFileKind;
import me.clutchy.clutchperms.common.subject.SubjectMetadataService;

import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
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
        return create(permissionService, subjectMetadataService, groupService, permissionNodeRegistry, manualPermissionNodeRegistry, permissionResolver, statusDiagnostics,
                storageReloader, storageValidator, storageBackupService, ClutchPermsConfig::defaults, updater -> {
                    throw new UnsupportedOperationException("Config updates are not available for this command environment");
                }, AuditLogServices::inMemory, defaultBackupRestorer(storageBackupService, storageReloader, runtimePermissionRefresher), runtimePermissionRefresher,
                ClutchPermsCommands.ROOT_LITERAL);
    }

    static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> create(Supplier<PermissionService> permissionService,
            Supplier<SubjectMetadataService> subjectMetadataService, Supplier<GroupService> groupService, Supplier<PermissionNodeRegistry> permissionNodeRegistry,
            Supplier<MutablePermissionNodeRegistry> manualPermissionNodeRegistry, Supplier<PermissionResolver> permissionResolver,
            Supplier<CommandStatusDiagnostics> statusDiagnostics, Runnable storageReloader, Runnable storageValidator, Supplier<StorageBackupService> storageBackupService,
            Runnable runtimePermissionRefresher, String rootLiteral) {
        return create(permissionService, subjectMetadataService, groupService, permissionNodeRegistry, manualPermissionNodeRegistry, permissionResolver, statusDiagnostics,
                storageReloader, storageValidator, storageBackupService, ClutchPermsConfig::defaults, updater -> {
                    throw new UnsupportedOperationException("Config updates are not available for this command environment");
                }, AuditLogServices::inMemory, defaultBackupRestorer(storageBackupService, storageReloader, runtimePermissionRefresher), runtimePermissionRefresher, rootLiteral);
    }

    static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> create(Supplier<PermissionService> permissionService,
            Supplier<SubjectMetadataService> subjectMetadataService, Supplier<GroupService> groupService, Supplier<PermissionNodeRegistry> permissionNodeRegistry,
            Supplier<MutablePermissionNodeRegistry> manualPermissionNodeRegistry, Supplier<PermissionResolver> permissionResolver,
            Supplier<CommandStatusDiagnostics> statusDiagnostics, Runnable storageReloader, Runnable storageValidator, Supplier<StorageBackupService> storageBackupService,
            Supplier<ClutchPermsConfig> config, Consumer<UnaryOperator<ClutchPermsConfig>> configUpdater, Supplier<AuditLogService> auditLogService,
            Runnable runtimePermissionRefresher, String rootLiteral) {
        return create(permissionService, subjectMetadataService, groupService, permissionNodeRegistry, manualPermissionNodeRegistry, permissionResolver, statusDiagnostics,
                storageReloader, storageValidator, storageBackupService, config, configUpdater, auditLogService,
                defaultBackupRestorer(storageBackupService, storageReloader, runtimePermissionRefresher), runtimePermissionRefresher, rootLiteral);
    }

    static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> create(Supplier<PermissionService> permissionService,
            Supplier<SubjectMetadataService> subjectMetadataService, Supplier<GroupService> groupService, Supplier<PermissionNodeRegistry> permissionNodeRegistry,
            Supplier<MutablePermissionNodeRegistry> manualPermissionNodeRegistry, Supplier<PermissionResolver> permissionResolver,
            Supplier<CommandStatusDiagnostics> statusDiagnostics, Runnable storageReloader, Runnable storageValidator, Supplier<StorageBackupService> storageBackupService,
            Supplier<ClutchPermsConfig> config, Consumer<UnaryOperator<ClutchPermsConfig>> configUpdater, Supplier<AuditLogService> auditLogService,
            BiConsumer<StorageFileKind, String> backupRestorer, Runnable runtimePermissionRefresher, String rootLiteral) {
        return ClutchPermsCommands.builder(new FabricCommandEnvironment(permissionService, subjectMetadataService, groupService, permissionNodeRegistry,
                manualPermissionNodeRegistry, permissionResolver, statusDiagnostics, storageReloader, storageValidator, storageBackupService, config, configUpdater,
                auditLogService, backupRestorer, runtimePermissionRefresher), rootLiteral);
    }

    private FabricClutchPermsCommand() {
    }

    private record FabricCommandEnvironment(Supplier<PermissionService> permissionServiceSupplier, Supplier<SubjectMetadataService> subjectMetadataServiceSupplier,
            Supplier<GroupService> groupServiceSupplier, Supplier<PermissionNodeRegistry> permissionNodeRegistrySupplier,
            Supplier<MutablePermissionNodeRegistry> manualPermissionNodeRegistrySupplier, Supplier<PermissionResolver> permissionResolverSupplier,
            Supplier<CommandStatusDiagnostics> statusDiagnosticsSupplier, Runnable storageReloader, Runnable storageValidator,
            Supplier<StorageBackupService> storageBackupServiceSupplier, Supplier<ClutchPermsConfig> configSupplier, Consumer<UnaryOperator<ClutchPermsConfig>> configUpdater,
            Supplier<AuditLogService> auditLogServiceSupplier, BiConsumer<StorageFileKind, String> backupRestorer,
            Runnable runtimePermissionRefresher) implements ClutchPermsCommandEnvironment<CommandSourceStack> {

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
        public ClutchPermsConfig config() {
            return configSupplier.get();
        }

        @Override
        public void updateConfig(UnaryOperator<ClutchPermsConfig> updater) {
            configUpdater.accept(updater);
        }

        @Override
        public AuditLogService auditLogService() {
            return auditLogServiceSupplier.get();
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
        public void restoreBackup(StorageFileKind kind, String backupFileName) {
            backupRestorer.accept(kind, backupFileName);
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

        @Override
        public void sendMessage(CommandSourceStack source, CommandMessage message) {
            source.sendSuccess(() -> toComponent(message), false);
        }

        private static Component toComponent(CommandMessage message) {
            MutableComponent component = Component.empty();
            for (CommandMessage.Segment segment : message.segments()) {
                MutableComponent part = Component.literal(segment.text()).withStyle(color(segment.color()));
                if (segment.bold()) {
                    part = part.withStyle(style -> style.withBold(true));
                }
                if (segment.click() != null) {
                    part = part.withStyle(style -> style.withClickEvent(toClickEvent(segment.click())));
                }
                if (segment.hover() != null) {
                    part = part.withStyle(style -> style.withHoverEvent(new HoverEvent.ShowText(toComponent(segment.hover()))));
                }
                component.append(part);
            }
            return component;
        }

        private static ClickEvent toClickEvent(CommandMessage.Click click) {
            return switch (click.action()) {
                case SUGGEST_COMMAND -> new ClickEvent.SuggestCommand(click.value());
                case RUN_COMMAND -> new ClickEvent.RunCommand(click.value());
            };
        }

        private static ChatFormatting color(CommandMessage.Color color) {
            return switch (color) {
                case AQUA -> ChatFormatting.AQUA;
                case GREEN -> ChatFormatting.GREEN;
                case RED -> ChatFormatting.RED;
                case YELLOW -> ChatFormatting.YELLOW;
                case GRAY -> ChatFormatting.GRAY;
                case WHITE -> ChatFormatting.WHITE;
            };
        }

        private static boolean isConsoleSource(CommandSourceStack source) {
            String textName = source.getTextName();
            return source.getEntity() == null && ("Server".equals(textName) || "Rcon".equalsIgnoreCase(textName));
        }
    }

    private static BiConsumer<StorageFileKind, String> defaultBackupRestorer(Supplier<StorageBackupService> storageBackupService, Runnable storageReloader,
            Runnable runtimePermissionRefresher) {
        return (kind, backupFileName) -> storageBackupService.get().restoreBackup(kind, backupFileName, () -> {
            storageReloader.run();
            runtimePermissionRefresher.run();
        });
    }
}
