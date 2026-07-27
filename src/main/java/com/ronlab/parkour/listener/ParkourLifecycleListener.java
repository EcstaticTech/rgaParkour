package com.ronlab.parkour.listener;

import com.ronlab.parkour.ParkourPlugin;
import com.ronlab.parkour.game.ParkourSession;
import com.ronlab.parkour.game.ParkourSessionManager;
import com.ronlab.rga.api.event.MinigameConcludeEvent;
import com.ronlab.rga.api.event.MinigameStartEvent;
import com.ronlab.rga.api.model.MinigameId;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Listener for RGA minigame lifecycle events (Start & Conclude).
 */
@NullMarked
public class ParkourLifecycleListener implements Listener {

    private final ParkourPlugin plugin;
    private final ParkourSessionManager sessionManager;

    public ParkourLifecycleListener(ParkourPlugin plugin, ParkourSessionManager sessionManager) {
        this.plugin = plugin;
        this.sessionManager = sessionManager;
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onMinigameStart(MinigameStartEvent event) {
        if (!isParkourMinigame(event.getMinigameId())) {
            return;
        }

        plugin.getLogger().info("[rgaParkour DEBUG] Received MinigameStartEvent for world: " + event.getWorldName());

        String worldName = event.getWorldName();
        List<UUID> playerUuids = event.getPlayerUuids();

        ParkourSession session = sessionManager.createSession(worldName, playerUuids, plugin.getKitConfig(), plugin);

        List<Player> playersInWorld = new ArrayList<>();
        World world = Bukkit.getWorld(worldName);
        if (world != null) {
            for (UUID uuid : playerUuids) {
                Player player = world.getPlayers().stream()
                        .filter(p -> p.getUniqueId().equals(uuid))
                        .findFirst()
                        .orElse(Bukkit.getPlayer(uuid));
                if (player != null && player.isOnline()) {
                    playersInWorld.add(player);
                }
            }
        }

        session.startGame(playersInWorld);
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onMinigameConclude(MinigameConcludeEvent event) {
        String worldName = event.getWorldName();
        ParkourSession session = sessionManager.getSession(worldName);
        if (session == null) {
            return;
        }

        plugin.getLogger().info("[rgaParkour DEBUG] Received MinigameConcludeEvent for world: " + worldName);

        // Populate scores with finish times
        session.getFinishTimes().forEach((uuid, finishTime) -> {
            event.getScores().putIfAbsent(uuid, finishTime);
        });

        session.cancelTimer();
        sessionManager.removeSession(worldName);
    }

    private boolean isParkourMinigame(@Nullable String rawMinigameId) {
        if (rawMinigameId == null || rawMinigameId.isBlank()) {
            return false;
        }
        try {
            MinigameId parsed = MinigameId.parse(rawMinigameId);
            if ("parkour".equalsIgnoreCase(parsed.key())) {
                return true;
            }
        } catch (Throwable ignored) {
            // Fallback for unparseable raw strings
        }
        String normalized = rawMinigameId.toLowerCase().replace("_", "").replace("-", "").trim();
        return normalized.endsWith("parkour") || normalized.equalsIgnoreCase("parkour");
    }
}
