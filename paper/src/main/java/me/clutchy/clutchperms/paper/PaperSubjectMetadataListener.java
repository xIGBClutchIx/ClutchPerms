package me.clutchy.clutchperms.paper;

import java.time.Instant;
import java.util.Collection;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import me.clutchy.clutchperms.common.SubjectMetadataService;

/**
 * Records lightweight subject metadata from Paper player lifecycle events.
 */
final class PaperSubjectMetadataListener implements Listener {

    private final SubjectMetadataService subjectMetadataService;

    PaperSubjectMetadataListener(SubjectMetadataService subjectMetadataService) {
        this.subjectMetadataService = subjectMetadataService;
    }

    @EventHandler
    void onPlayerJoin(PlayerJoinEvent event) {
        recordPlayer(event.getPlayer());
    }

    void recordOnlinePlayers(Collection<? extends Player> players) {
        players.forEach(this::recordPlayer);
    }

    private void recordPlayer(Player player) {
        subjectMetadataService.recordSubject(player.getUniqueId(), player.getName(), Instant.now());
    }
}
