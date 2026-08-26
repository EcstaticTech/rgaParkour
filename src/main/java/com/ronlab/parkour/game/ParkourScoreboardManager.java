package com.ronlab.parkour.game;

import fr.mrmicky.fastboard.adventure.FastBoard;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages dynamic, packet-based sidebar scoreboards for active parkour sessions using FastBoard.
 * Operates on a high-frequency (2-tick / 100ms) update loop for smooth time rendering and isolated scoreboards.
 */
@NullMarked
public class ParkourScoreboardManager {

    private final Map<UUID, FastBoard> boards = new ConcurrentHashMap<>();
    private final Map<UUID, String> playerNames = new ConcurrentHashMap<>();
    private @Nullable BukkitTask updateTask;

    public void start(Plugin plugin, ParkourSessionManager sessionManager) {
        stop();
        if (Bukkit.getScheduler() != null) {
            // High-frequency tick updater running every 2 ticks (100ms) to update elapsed time without component flicker
            this.updateTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> tickTitlesAndLines(sessionManager), 2L, 2L);
        }
    }

    public void stop() {
        if (updateTask != null && !updateTask.isCancelled()) {
            try {
                updateTask.cancel();
            } catch (Throwable ignored) {
            }
            updateTask = null;
        }
        clearAll();
    }

    public void registerPlayerName(UUID uuid, String name) {
        if (uuid != null && name != null) {
            playerNames.put(uuid, name);
        }
    }

    public void setupPlayerBoard(Player player, ParkourSession session) {
        if (player == null || !player.isOnline()) return;
        UUID uuid = player.getUniqueId();
        registerPlayerName(uuid, player.getName());
        removePlayerBoard(uuid);

        // Bind isolated scoreboard and collision team
        session.bindPlayerScoreboardAndTeam(player);

        try {
            FastBoard board = new FastBoard(player);
            boards.put(uuid, board);
            // Push baseline dummy frames synchronously during initialization
            refreshPlayerBoard(player, board, session);
        } catch (Throwable ignored) {
            // Safe fallback for headless / mock test environments
        }
    }

    public void removePlayerBoard(UUID uuid) {
        FastBoard board = boards.remove(uuid);
        if (board != null) {
            try {
                if (!board.isDeleted()) {
                    board.delete();
                }
            } catch (Throwable ignored) {
            }
        }
    }

    public void removeSessionScoreboards(ParkourSession session) {
        for (UUID uuid : session.getActivePlayers()) {
            removePlayerBoard(uuid);
        }
    }

    public void refreshSession(ParkourSession session) {
        Component title = buildTitleComponent(session);

        for (UUID uuid : session.getActivePlayers()) {
            FastBoard board = boards.get(uuid);
            if (board != null && !board.isDeleted()) {
                try {
                    List<Component> playerLines = buildLinesForPlayer(uuid, session);
                    board.updateTitle(title);
                    board.updateLines(playerLines);
                } catch (Throwable ignored) {
                }
            }
        }
    }

    private void refreshPlayerBoard(Player player, FastBoard board, ParkourSession session) {
        try {
            Component title = buildTitleComponent(session);
            List<Component> playerLines = buildLinesForPlayer(player.getUniqueId(), session);
            board.updateTitle(title);
            board.updateLines(playerLines);
        } catch (Throwable ignored) {
        }
    }

    private void tickTitlesAndLines(ParkourSessionManager sessionManager) {
        for (ParkourSession session : sessionManager.getActiveSessions().values()) {
            refreshSession(session);
        }
    }

    public Component buildTitleComponent(ParkourSession session) {
        if (session.getState() == ParkourSession.SessionState.COUNTDOWN) {
            String countdownStr = String.format("00:0%d", Math.max(0, session.getCountdownRemaining()));
            return LegacyComponentSerializer.legacySection().deserialize("§b§lSTARTING §7(" + countdownStr + ")");
        }

        if (session.getState() == ParkourSession.SessionState.CONCLUDED) {
            return LegacyComponentSerializer.legacySection().deserialize("§b§lPARKOUR RACE §7(FINISHED)");
        }

        long elapsedSeconds = Math.max(0, (System.currentTimeMillis() - session.getStartTime()) / 1000);
        long mins = elapsedSeconds / 60;
        long secs = elapsedSeconds % 60;
        String formattedTime = String.format("%02d:%02d", mins, secs);
        return LegacyComponentSerializer.legacySection().deserialize("§b§lPARKOUR RACE §7(" + formattedTime + ")");
    }

    /**
     * Builds per-player scoreboard lines displaying personal metrics (Checkpoints, Falls) and standings.
     */
    public List<Component> buildLinesForPlayer(UUID targetUuid, ParkourSession session) {
        List<Component> lines = new ArrayList<>();
        lines.add(LegacyComponentSerializer.legacySection().deserialize("§7------------------------"));

        // Personal Metrics
        int personalCheckpoints = session.getDiscoveredCheckpoints(targetUuid).size();
        int personalFalls = session.getFallCount(targetUuid);
        lines.add(LegacyComponentSerializer.legacySection().deserialize("§eCheckpoints: §f" + personalCheckpoints));
        lines.add(LegacyComponentSerializer.legacySection().deserialize("§eFalls: §f" + personalFalls));
        lines.add(LegacyComponentSerializer.legacySection().deserialize("§7------------------------"));

        // Match Standings
        for (UUID uuid : session.getActivePlayers()) {
            Player p = null;
            try {
                p = Bukkit.getPlayer(uuid);
            } catch (Throwable ignored) {
            }

            String rawName = (p != null) ? p.getName() : playerNames.getOrDefault(uuid, "Player_" + uuid.toString().substring(0, 4));
            String name = truncateName(rawName, 14);

            if (session.hasFinished(uuid)) {
                String splitTime = session.getFormattedFinishTime(uuid);
                if (splitTime == null) splitTime = "✔";
                lines.add(LegacyComponentSerializer.legacySection().deserialize("§a" + name + " §7" + splitTime + " §a✔"));
            } else if (session.isSpectator(uuid) || (p != null && isRGASpectator(p))) {
                lines.add(LegacyComponentSerializer.legacySection().deserialize("§7" + name + " §8SPECTATING"));
            } else {
                int checkpoints = session.getDiscoveredCheckpoints(uuid).size();
                lines.add(LegacyComponentSerializer.legacySection().deserialize("§f" + name + " §e" + checkpoints));
            }
        }
        return lines;
    }

    /**
     * Builds generic leaderboard lines for active players in the session.
     */
    public List<Component> buildLeaderboardLines(ParkourSession session) {
        List<Component> lines = new ArrayList<>();
        lines.add(LegacyComponentSerializer.legacySection().deserialize("§7------------------------"));

        for (UUID uuid : session.getActivePlayers()) {
            Player p = null;
            try {
                p = Bukkit.getPlayer(uuid);
            } catch (Throwable ignored) {
            }

            String rawName = (p != null) ? p.getName() : playerNames.getOrDefault(uuid, "Player_" + uuid.toString().substring(0, 4));
            String name = truncateName(rawName, 14);

            if (session.hasFinished(uuid)) {
                String splitTime = session.getFormattedFinishTime(uuid);
                if (splitTime == null) splitTime = "✔";
                lines.add(LegacyComponentSerializer.legacySection().deserialize("§a" + name + " §7" + splitTime + " §a✔"));
            } else if (session.isSpectator(uuid) || (p != null && isRGASpectator(p))) {
                lines.add(LegacyComponentSerializer.legacySection().deserialize("§7" + name + " §8SPECTATING"));
            } else {
                int checkpoints = session.getDiscoveredCheckpoints(uuid).size();
                lines.add(LegacyComponentSerializer.legacySection().deserialize("§f" + name + " §e" + checkpoints));
            }
        }
        return lines;
    }

    private boolean isRGASpectator(Player player) {
        try {
            com.ronlab.rga.api.RGASessionControl rga = com.ronlab.rga.RGA.getInstance();
            return rga != null && rga.isSpectator(player);
        } catch (Throwable ignored) {
            return false;
        }
    }

    public String truncateName(String name, int maxLength) {
        if (name == null) return "Unknown";
        if (name.length() <= maxLength) return name;
        return name.substring(0, maxLength);
    }

    public void clearAll() {
        for (FastBoard board : boards.values()) {
            try {
                if (!board.isDeleted()) {
                    board.delete();
                }
            } catch (Throwable ignored) {
            }
        }
        boards.clear();
    }

    public Map<UUID, FastBoard> getBoards() {
        return boards;
    }
}
