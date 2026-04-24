package me.clutchy.clutchperms.fabric;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

import me.clutchy.clutchperms.common.permission.PermissionResolver;
import me.clutchy.clutchperms.common.permission.PermissionValue;
import me.lucko.fabric.api.permissions.v0.OfflinePermissionCheckEvent;
import me.lucko.fabric.api.permissions.v0.PermissionCheckEvent;

import net.fabricmc.fabric.api.util.TriState;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.server.level.ServerPlayer;

/**
 * Provides ClutchPerms effective assignments to mods that query Fabric's community permissions API.
 */
final class FabricRuntimePermissionBridge {

    static void register(Supplier<PermissionResolver> permissionResolverSupplier) {
        PermissionCheckEvent.EVENT.register((source, permission) -> resolve(permissionResolverSupplier.get(), source, permission));
        OfflinePermissionCheckEvent.EVENT.register((subjectId, permission) -> CompletableFuture.completedFuture(resolve(permissionResolverSupplier.get(), subjectId, permission)));
    }

    private FabricRuntimePermissionBridge() {
    }

    private static TriState resolve(PermissionResolver permissionResolver, SharedSuggestionProvider source, String permission) {
        if (!(source instanceof CommandSourceStack commandSource)) {
            return TriState.DEFAULT;
        }

        ServerPlayer player = commandSource.getPlayer();
        if (player == null) {
            return TriState.DEFAULT;
        }

        return resolve(permissionResolver, player.getUUID(), permission);
    }

    static TriState resolve(PermissionResolver permissionResolver, UUID subjectId, String permission) {
        PermissionValue value;
        try {
            value = permissionResolver.resolve(subjectId, permission).value();
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
