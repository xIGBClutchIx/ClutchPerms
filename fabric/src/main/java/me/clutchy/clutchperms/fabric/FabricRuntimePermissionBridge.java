package me.clutchy.clutchperms.fabric;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

import me.clutchy.clutchperms.common.PermissionService;
import me.clutchy.clutchperms.common.PermissionValue;
import me.lucko.fabric.api.permissions.v0.OfflinePermissionCheckEvent;
import me.lucko.fabric.api.permissions.v0.PermissionCheckEvent;

import net.fabricmc.fabric.api.util.TriState;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.server.level.ServerPlayer;

/**
 * Provides ClutchPerms direct assignments to mods that query Fabric's community permissions API.
 */
final class FabricRuntimePermissionBridge {

    static void register(Supplier<PermissionService> permissionServiceSupplier) {
        PermissionCheckEvent.EVENT.register((source, permission) -> resolve(permissionServiceSupplier.get(), source, permission));
        OfflinePermissionCheckEvent.EVENT.register((subjectId, permission) -> CompletableFuture.completedFuture(resolve(permissionServiceSupplier.get(), subjectId, permission)));
    }

    private FabricRuntimePermissionBridge() {
    }

    private static TriState resolve(PermissionService permissionService, SharedSuggestionProvider source, String permission) {
        if (!(source instanceof CommandSourceStack commandSource)) {
            return TriState.DEFAULT;
        }

        ServerPlayer player = commandSource.getPlayer();
        if (player == null) {
            return TriState.DEFAULT;
        }

        return resolve(permissionService, player.getUUID(), permission);
    }

    static TriState resolve(PermissionService permissionService, UUID subjectId, String permission) {
        PermissionValue value;
        try {
            value = permissionService.getPermission(subjectId, permission);
        } catch (IllegalArgumentException exception) {
            return TriState.DEFAULT;
        }

        return switch (value) {
            case TRUE -> TriState.TRUE;
            case FALSE -> TriState.FALSE;
            case UNSET -> TriState.DEFAULT;
        };
    }
}
