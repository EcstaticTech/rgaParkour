package com.ronlab.parkour.listener;

import com.ronlab.parkour.config.ParkourKitConfig;
import com.ronlab.parkour.game.ParkourSession;
import com.ronlab.parkour.game.ParkourSessionManager;
import com.ronlab.rga.RGA;
import com.ronlab.rga.api.RGASessionControl;
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
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Listener for player movement mechanics (checkpoints, finish, fall thresholds) and damage suppression.
 */
@NullMarked
public class ParkourPlayerListener implements Listener {

    private final ParkourSessionManager sessionManager;
    private final ParkourKitConfig config;

    public ParkourPlayerListener(ParkourSessionManager sessionManager, ParkourKitConfig config) {
        this.sessionManager = sessionManager;
        this.config = config;
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

        // Ignore spectators or players who have already finished
        if (player.getGameMode() == GameMode.SPECTATOR || session.getFinishTimes().containsKey(player.getUniqueId())) {
            return;
        }

        RGASessionControl rgaControl = getRGASessionControl();
        if (rgaControl != null && rgaControl.isSpectator(player)) {
            return;
        }

        Location to = event.getTo();

        // 1. Y-Threshold Fall Interception
        if (to.getY() <= config.getFallThresholdY()) {
            session.applyFailEffects(player);
            return;
        }

        Block feetBlock = to.getBlock();
        Block underBlock = feetBlock.getRelative(BlockFace.DOWN);

        Material feetMat = feetBlock.getType();
        Material underMat = underBlock.getType();

        // 2. Fail block material check (e.g., LAVA, WATER)
        if (config.isFailMaterial(feetMat) || config.isFailMaterial(underMat)) {
            session.applyFailEffects(player);
            return;
        }

        // 3. Finish block material check (e.g., HEAVY_WEIGHTED_PRESSURE_PLATE, IRON_PRESSURE_PLATE)
        if (config.isFinishMaterial(feetMat) || config.isFinishMaterial(underMat)) {
            session.handleFinish(player, rgaControl);
            return;
        }

        // 4. Checkpoint block material check (e.g., GOLD_PRESSURE_PLATE, LIGHT_WEIGHTED_PRESSURE_PLATE)
        if (config.isCheckpointMaterial(feetMat) || config.isCheckpointMaterial(underMat)) {
            session.recordCheckpoint(player, to);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player player) {
            String worldName = player.getWorld().getName();
            if (sessionManager.hasSession(worldName)) {
                event.setCancelled(true);
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
