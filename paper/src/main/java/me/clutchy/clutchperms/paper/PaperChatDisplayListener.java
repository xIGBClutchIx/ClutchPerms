package me.clutchy.clutchperms.paper;

import java.util.Objects;
import java.util.function.Supplier;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

import io.papermc.paper.event.player.AsyncChatEvent;

import me.clutchy.clutchperms.common.display.DisplayResolver;

/**
 * Applies ClutchPerms display prefixes and suffixes to Paper chat.
 */
final class PaperChatDisplayListener implements Listener {

    private final Supplier<DisplayResolver> displayResolverSupplier;

    PaperChatDisplayListener(Supplier<DisplayResolver> displayResolverSupplier) {
        this.displayResolverSupplier = Objects.requireNonNull(displayResolverSupplier, "displayResolverSupplier");
    }

    @EventHandler
    void onAsyncChat(AsyncChatEvent event) {
        event.renderer(
                (source, sourceDisplayName, message, viewer) -> PaperDisplayComponents.chatLine(source.getUniqueId(), sourceDisplayName, message, displayResolverSupplier.get()));
    }
}
