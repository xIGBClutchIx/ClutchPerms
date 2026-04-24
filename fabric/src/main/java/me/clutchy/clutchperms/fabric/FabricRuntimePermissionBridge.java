package me.clutchy.clutchperms.fabric;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

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

    static void register(PermissionService permissionService) {
        PermissionCheckEvent.EVENT.register((source, permission) -> resolve(permissionService, source, permission));
        OfflinePermissionCheckEvent.EVENT.register((subjectId, permission) -> CompletableFuture.completedFuture(resolve(permissionService, subjectId, permission)));
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

    private static TriState resolve(PermissionService permissionService, UUID subjectId, String permission) {
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
