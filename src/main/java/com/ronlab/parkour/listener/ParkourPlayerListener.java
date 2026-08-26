package com.ronlab.parkour.listener;

import com.ronlab.parkour.config.ParkourKitConfig;
import com.ronlab.parkour.game.ParkourScoreboardManager;
import com.ronlab.parkour.game.ParkourSession;
import com.ronlab.parkour.game.ParkourSessionManager;
import com.ronlab.rga.RGA;
import com.ronlab.rga.api.RGASessionControl;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.ScoreboardManager;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Listener for player movement mechanics (checkpoints, finish, fall thresholds) and damage suppression.
 */
@NullMarked
public class ParkourPlayerListener implements Listener {

    private final ParkourSessionManager sessionManager;
    private final ParkourKitConfig config;
    private final @Nullable ParkourScoreboardManager scoreboardManager;

    public ParkourPlayerListener(ParkourSessionManager sessionManager, ParkourKitConfig config) {
        this(sessionManager, config, null);
    }

    public ParkourPlayerListener(ParkourSessionManager sessionManager, ParkourKitConfig config, @Nullable ParkourScoreboardManager scoreboardManager) {
        this.sessionManager = sessionManager;
        this.config = config;
        this.scoreboardManager = scoreboardManager;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        // Performance Guard: Enforce block position change
        if (!event.hasChangedBlock()) {
            return;
        }

        Player player = event.getPlayer();
        String worldName = player.getWorld().getName();
        ParkourSession session = sessionManager.getSession(worldName);
        if (session == null || !session.isActivePlayer(player.getUniqueId())) {
            return;
        }

        // Short-circuit completely for finished or spectating players to prevent fall/fluid teleports
        if (session.hasFinished(player.getUniqueId()) || session.isSpectator(player.getUniqueId()) || player.getGameMode() == GameMode.SPECTATOR) {
            return;
        }

        RGASessionControl rgaControl = getRGASessionControl();
        if (rgaControl != null && rgaControl.isSpectator(player)) {
            return;
        }

        // Movement freeze guard during COUNTDOWN (lock X/Y/Z while preserving pitch and yaw)
        if (session.getState() == ParkourSession.SessionState.COUNTDOWN) {
            Location from = event.getFrom();
            Location to = event.getTo();
            Location lockedLoc = from.clone();
            lockedLoc.setYaw(to.getYaw());
            lockedLoc.setPitch(to.getPitch());
            event.setTo(lockedLoc);
            return;
        }

        Location to = event.getTo();

        // 1. Y-Threshold Fall Interception
        if (to.getY() <= config.getFallThresholdY()) {
            session.applyFailEffects(player);
            if (scoreboardManager != null) {
                scoreboardManager.refreshSession(session);
            }
            return;
        }

        Block feetBlock = to.getBlock();
        Block underBlock = feetBlock.getRelative(BlockFace.DOWN);

        Material feetMat = feetBlock.getType();
        Material underMat = underBlock.getType();

        // 2. Fail block material check (e.g., LAVA, WATER)
        if (config.isFailMaterial(feetMat) || config.isFailMaterial(underMat)) {
            session.applyFailEffects(player);
            if (scoreboardManager != null) {
                scoreboardManager.refreshSession(session);
            }
            return;
        }

        // 3. Finish block material check (e.g., HEAVY_WEIGHTED_PRESSURE_PLATE, IRON_PRESSURE_PLATE)
        if (config.isFinishMaterial(feetMat) || config.isFinishMaterial(underMat)) {
            session.handleFinish(player, rgaControl);
            if (scoreboardManager != null) {
                scoreboardManager.refreshSession(session);
            }
            return;
        }

        // 4. Checkpoint block material check (e.g., GOLD_PRESSURE_PLATE, LIGHT_WEIGHTED_PRESSURE_PLATE)
        if (config.isCheckpointMaterial(feetMat) || config.isCheckpointMaterial(underMat)) {
            boolean isNew = session.recordCheckpoint(player, to);
            if (isNew && scoreboardManager != null) {
                scoreboardManager.refreshSession(session);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player player) {
            String worldName = player.getWorld().getName();
            ParkourSession session = sessionManager.getSession(worldName);
            if (session != null && session.isActivePlayer(player.getUniqueId())) {
                event.setCancelled(true);

                // Catch void damage or fatal damage drops to prevent vanilla death routing / default spawn drops
                if (event.getCause() == EntityDamageEvent.DamageCause.VOID || player.getHealth() - event.getFinalDamage() <= 0) {
                    if (!session.hasFinished(player.getUniqueId()) && !session.isSpectator(player.getUniqueId())) {
                        session.applyFailEffects(player);
                        if (scoreboardManager != null) {
                            scoreboardManager.refreshSession(session);
                        }
                    }
                }
            }
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        if (scoreboardManager != null) {
            scoreboardManager.removePlayerBoard(player.getUniqueId());
        }

        String worldName = player.getWorld().getName();
        ParkourSession session = sessionManager.getSession(worldName);
        if (session != null) {
            try {
                if (session.getTeam() != null) {
                    session.getTeam().removeEntry(player.getName());
                }
                ScoreboardManager sm = Bukkit.getScoreboardManager();
                Scoreboard mainBoard = (sm != null) ? sm.getMainScoreboard() : null;
                if (mainBoard != null && player.getScoreboard() == session.getScoreboard()) {
                    player.setScoreboard(mainBoard);
                }
            } catch (Throwable ignored) {
            }
        }
    }

    private @Nullable RGASessionControl getRGASessionControl() {
        try {
            return RGA.getInstance();
        } catch (Throwable ignored) {
            return null;
        }
    }
}
