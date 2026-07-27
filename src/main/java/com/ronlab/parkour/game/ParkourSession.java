package com.ronlab.parkour.game;

import com.ronlab.parkour.config.ParkourKitConfig;
import com.ronlab.rga.RGA;
import com.ronlab.rga.api.RGASessionControl;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Encapsulates match state and progression for an active procedural parkour session.
 */
@NullMarked
public class ParkourSession {

    private final String worldName;
    private final List<UUID> activePlayers;
    private final Map<UUID, Location> lastCheckpoints = new HashMap<>();
    private final Map<UUID, Long> finishTimes = new ConcurrentHashMap<>();
    private final ParkourKitConfig config;
    private final @Nullable Plugin plugin;

    private long startTime;
    private @Nullable BukkitTask matchTimer;

    public ParkourSession(String worldName, @Nullable List<UUID> activePlayers, @Nullable ParkourKitConfig config, @Nullable Plugin plugin) {
        this.worldName = worldName;
        this.activePlayers = new ArrayList<>(activePlayers != null ? activePlayers : Collections.emptyList());
        this.config = config != null ? config : new ParkourKitConfig();
        this.plugin = plugin;
    }

    public void startGame(@Nullable List<Player> players) {
        this.startTime = System.currentTimeMillis();
        World sessionWorld = null;
        try {
            if (Bukkit.getServer() != null) {
                sessionWorld = Bukkit.getWorld(worldName);
            }
        } catch (Throwable ignored) {
            // Safe fallback for unit tests
        }

        if (players != null) {
            for (Player player : players) {
                if (player != null && player.isOnline()) {
                    Location targetSpawn = null;
                    if (sessionWorld != null) {
                        try {
                            targetSpawn = sessionWorld.getSpawnLocation();
                        } catch (Throwable ignored) {
                        }
                    }
                    if (targetSpawn == null && player.getWorld() != null) {
                        try {
                            targetSpawn = player.getWorld().getSpawnLocation();
                        } catch (Throwable ignored) {
                        }
                    }

                    Location spawnLocation = (targetSpawn != null)
                            ? targetSpawn.clone()
                            : (player.getLocation() != null ? player.getLocation().clone() : null);

                    if (spawnLocation != null) {
                        this.lastCheckpoints.put(player.getUniqueId(), spawnLocation);
                        logDebug(String.format(
                                "Snapshotted initial spawn location for %s at (%.1f, %.1f, %.1f) in world %s",
                                player.getName(), spawnLocation.getX(), spawnLocation.getY(), spawnLocation.getZ(), worldName
                        ));
                    }
                }
            }
        }

        logDebug("Starting ParkourSession for world: " + worldName + " with " + activePlayers.size() + " active player(s).");

        if (plugin != null && Bukkit.getScheduler() != null) {
            long ticks = config.getMaxMatchDurationSeconds() * 20L;
            matchTimer = Bukkit.getScheduler().runTaskLater(plugin, this::timeoutMatch, ticks);
        }
    }

    public boolean isActivePlayer(UUID uuid) {
        return activePlayers.contains(uuid);
    }

    public @Nullable Location getLastCheckpoint(UUID uuid) {
        Location checkpoint = lastCheckpoints.get(uuid);
        if (checkpoint != null) {
            return checkpoint;
        }
        try {
            if (Bukkit.getServer() != null) {
                World world = Bukkit.getWorld(worldName);
                if (world != null) {
                    return world.getSpawnLocation();
                }
            }
        } catch (Throwable ignored) {
            // Safe fallback for unit tests / missing world context
        }
        return null;
    }

    public void recordCheckpoint(@Nullable Player player, @Nullable Location loc) {
        if (player == null || loc == null) return;
        UUID uuid = player.getUniqueId();
        lastCheckpoints.put(uuid, loc.clone());
        logDebug("Player " + player.getName() + " recorded checkpoint at " + loc.getBlockX() + ", " + loc.getBlockY() + ", " + loc.getBlockZ());
        try {
            player.playSound(loc, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);
        } catch (Throwable ignored) {
            // Safe fallback for headless/unit test execution
        }
    }

    public void handleFail(@Nullable Player player) {
        applyFailEffects(player);
    }

    public void applyFailEffects(@Nullable Player player) {
        if (player == null) return;
        UUID uuid = player.getUniqueId();
        Location checkpoint = getLastCheckpoint(uuid);
        if (checkpoint == null) {
            try {
                checkpoint = player.getLocation();
            } catch (Throwable ignored) {
                // Final fallback
            }
        }

        logDebug("Player " + player.getName() + " failed (Y <= " + config.getFallThresholdY() + " or fluid), resetting to checkpoint.");

        if (checkpoint != null) {
            try {
                player.teleport(checkpoint);
            } catch (Throwable ignored) {
                // Safe fallback for test execution
            }
        }

        int invulnSecs = config.getInvulnerabilitySecondsOnFail();
        if (invulnSecs > 0) {
            try {
                player.setNoDamageTicks(invulnSecs * 20);
                player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, invulnSecs * 20, 255, false, false, true));
            } catch (Throwable ignored) {
                // Safe fallback for test execution
            }
        }

        try {
            Location targetLoc = checkpoint != null ? checkpoint : player.getLocation();
            player.playSound(targetLoc, Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.0f);
        } catch (Throwable ignored) {
            // Safe fallback for test execution
        }
    }

    public void handleFinish(@Nullable Player player, @Nullable RGASessionControl rga) {
        if (player == null) return;
        UUID uuid = player.getUniqueId();
        if (finishTimes.containsKey(uuid)) {
            return;
        }

        long finishTime = System.currentTimeMillis() - startTime;
        finishTimes.put(uuid, finishTime);
        logDebug("Player " + player.getName() + " finished course in " + finishTime + " ms!");

        if (rga != null) {
            try {
                rga.setSpectator(player, true);
            } catch (Throwable ignored) {
                // Safe fallback for test execution
            }
        }

        // Evaluate Party Completion
        if (finishTimes.size() >= activePlayers.size()) {
            requestSessionConclude("Party Completion");
        }
    }

    public void timeoutMatch() {
        logDebug("Match timed out for world: " + worldName);
        requestSessionConclude("Match Timeout");
    }

    private void requestSessionConclude(String reason) {
        cancelTimer();
        logDebug("Requesting session conclusion for world: " + worldName + " (Reason: " + reason + ")");
        try {
            RGA rgaInstance = RGA.getInstance();
            if (rgaInstance != null) {
                rgaInstance.requestSessionConclude(worldName, reason, new HashMap<>(finishTimes));
            }
        } catch (Throwable ignored) {
            // Safe fallback when RGA is not initialized in unit tests
        }
    }

    public void cancelTimer() {
        if (matchTimer != null && !matchTimer.isCancelled()) {
            try {
                matchTimer.cancel();
            } catch (Throwable ignored) {
                // Safe fallback for test execution
            }
            matchTimer = null;
        }
    }

    private void logDebug(String message) {
        if (plugin != null) {
            plugin.getLogger().info("[rgaParkour DEBUG] " + message);
        }
    }

    public String getWorldName() {
        return worldName;
    }

    public List<UUID> getActivePlayers() {
        return Collections.unmodifiableList(activePlayers);
    }

    public Map<UUID, Location> getLastCheckpoints() {
        return Collections.unmodifiableMap(lastCheckpoints);
    }

    public Map<UUID, Long> getFinishTimes() {
        return Collections.unmodifiableMap(finishTimes);
    }

    public long getStartTime() {
        return startTime;
    }

    public void setStartTime(long startTime) {
        this.startTime = startTime;
    }

    public @Nullable BukkitTask getMatchTimer() {
        return matchTimer;
    }

    public ParkourKitConfig getConfig() {
        return config;
    }
}
