package me.clutchy.clutchperms.fabric.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import me.clutchy.clutchperms.fabric.ClutchPermsFabricMod;

import net.minecraft.network.chat.ChatType;
import net.minecraft.network.chat.PlayerChatMessage;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;

/**
 * Replaces vanilla Fabric chat broadcast lines with ClutchPerms display-formatted system components.
 */
@Mixin(PlayerList.class)
public abstract class PlayerListChatMixin {

    @Inject(method = "broadcastChatMessage(Lnet/minecraft/network/chat/PlayerChatMessage;Lnet/minecraft/server/level/ServerPlayer;Lnet/minecraft/network/chat/ChatType$Bound;)V", at = @At("HEAD"), cancellable = true)
    private void clutchperms$formatChat(PlayerChatMessage message, ServerPlayer sender, ChatType.Bound boundChatType, CallbackInfo callbackInfo) {
        ClutchPermsFabricMod.formatChatMessage(sender, message.decoratedContent()).ifPresent(formattedMessage -> {
            ((PlayerList) (Object) this).broadcastSystemMessage(formattedMessage, false);
            callbackInfo.cancel();
        });
    }
}
