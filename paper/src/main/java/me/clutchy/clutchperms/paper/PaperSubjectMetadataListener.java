package me.clutchy.clutchperms.paper;

import java.time.Instant;
import java.util.Collection;
import java.util.Objects;
import java.util.function.Supplier;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import me.clutchy.clutchperms.common.subject.SubjectMetadataService;

/**
 * Records lightweight subject metadata from Paper player lifecycle events.
 */
final class PaperSubjectMetadataListener implements Listener {

    private final Supplier<SubjectMetadataService> subjectMetadataServiceSupplier;

    PaperSubjectMetadataListener(Supplier<SubjectMetadataService> subjectMetadataServiceSupplier) {
        this.subjectMetadataServiceSupplier = Objects.requireNonNull(subjectMetadataServiceSupplier, "subjectMetadataServiceSupplier");
    }

    @EventHandler
    void onPlayerJoin(PlayerJoinEvent event) {
        recordPlayer(event.getPlayer());
    }

    void recordOnlinePlayers(Collection<? extends Player> players) {
        players.forEach(this::recordPlayer);
    }

    private void recordPlayer(Player player) {
        subjectMetadataServiceSupplier.get().recordSubject(player.getUniqueId(), player.getName(), Instant.now());
    }
}
