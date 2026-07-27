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

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Encapsulates match state and progression for an active procedural parkour session.
 */
@NullMarked
public class ParkourSession {

    public enum SessionState {
        COUNTDOWN,
        RACING,
        CONCLUDED
    }

    public record BlockPos(int x, int y, int z) {}

    private final String worldName;
    private final List<UUID> activePlayers;
    private final Map<UUID, Location> lastCheckpoints = new ConcurrentHashMap<>();
    private final Map<UUID, Long> finishTimes = new ConcurrentHashMap<>();
    private final Map<UUID, Set<BlockPos>> discoveredCheckpoints = new ConcurrentHashMap<>();
    private final Map<UUID, String> finishedSplitTimes = new ConcurrentHashMap<>();
    private final ParkourKitConfig config;
    private final @Nullable Plugin plugin;

    private volatile SessionState state = SessionState.COUNTDOWN;
    private volatile int countdownRemaining = 3;
    private @Nullable BukkitTask countdownTask;
    private long startTime;
    private @Nullable BukkitTask matchTimer;

    public ParkourSession(String worldName, @Nullable List<UUID> activePlayers, @Nullable ParkourKitConfig config, @Nullable Plugin plugin) {
        this.worldName = worldName;
        this.activePlayers = new CopyOnWriteArrayList<>(activePlayers != null ? activePlayers : Collections.emptyList());
        this.config = config != null ? config : new ParkourKitConfig();
        this.plugin = plugin;
    }

    public void startGame(@Nullable List<Player> players) {
        this.state = SessionState.COUNTDOWN;
        this.countdownRemaining = 3;
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
            countdownTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tickCountdown, 0L, 20L);
        } else {
            // Immediate transition for headless unit test execution without Bukkit scheduler
            startRacingForTest();
        }
    }

    private void tickCountdown() {
        if (state != SessionState.COUNTDOWN) {
            cancelCountdown();
            return;
        }

        if (countdownRemaining > 0) {
            String titleText = switch (countdownRemaining) {
                case 3 -> "§c3";
                case 2 -> "§e2";
                default -> "§a1";
            };
            float pitch = switch (countdownRemaining) {
                case 3 -> 1.0f;
                case 2 -> 1.2f;
                default -> 1.4f;
            };

            for (UUID uuid : activePlayers) {
                try {
                    Player p = Bukkit.getPlayer(uuid);
                    if (p != null && p.isOnline()) {
                        p.sendTitle(titleText, "", 0, 20, 10);
                        p.playSound(p.getLocation(), getNoteSound(), 1.0f, pitch);
                    }
                } catch (Throwable ignored) {
                }
            }
            countdownRemaining--;
        } else {
            cancelCountdown();
            this.state = SessionState.RACING;
            this.startTime = System.currentTimeMillis();

            for (UUID uuid : activePlayers) {
                try {
                    Player p = Bukkit.getPlayer(uuid);
                    if (p != null && p.isOnline()) {
                        p.sendTitle("§b§lGO!", "", 0, 20, 10);
                        p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
                    }
                } catch (Throwable ignored) {
                }
            }

            if (plugin != null && Bukkit.getScheduler() != null) {
                long ticks = config.getMaxMatchDurationSeconds() * 20L;
                matchTimer = Bukkit.getScheduler().runTaskLater(plugin, this::timeoutMatch, ticks);
            }
        }
    }

    public void startRacingForTest() {
        cancelCountdown();
        this.state = SessionState.RACING;
        this.startTime = System.currentTimeMillis();
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

    public boolean recordCheckpoint(@Nullable Player player, @Nullable Location loc) {
        if (player == null || loc == null) return false;
        UUID uuid = player.getUniqueId();
        lastCheckpoints.put(uuid, loc.clone());
        BlockPos pos = new BlockPos(loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
        Set<BlockPos> checkpoints = discoveredCheckpoints.computeIfAbsent(uuid, k -> ConcurrentHashMap.newKeySet());
        boolean isNew = checkpoints.add(pos);
        logDebug("Player " + player.getName() + " recorded checkpoint at " + loc.getBlockX() + ", " + loc.getBlockY() + ", " + loc.getBlockZ() + " (New: " + isNew + ")");
        try {
            player.playSound(loc, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);
        } catch (Throwable ignored) {
            // Safe fallback for headless/unit test execution
        }
        return isNew;
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
        long finishTime = System.currentTimeMillis() - startTime;

        if (finishTimes.putIfAbsent(uuid, finishTime) != null) {
            return;
        }

        long seconds = finishTime / 1000;
        long mins = seconds / 60;
        long secs = seconds % 60;
        String formattedSplit = String.format("%02d:%02d", mins, secs);
        finishedSplitTimes.put(uuid, formattedSplit);

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

    public boolean hasFinished(UUID uuid) {
        return finishTimes.containsKey(uuid);
    }

    public @Nullable String getFormattedFinishTime(UUID uuid) {
        return finishedSplitTimes.get(uuid);
    }

    public Set<BlockPos> getDiscoveredCheckpoints(UUID uuid) {
        Set<BlockPos> set = discoveredCheckpoints.get(uuid);
        return set != null ? Set.copyOf(set) : Collections.emptySet();
    }

    public boolean isSpectator(UUID uuid) {
        return hasFinished(uuid) || !isActivePlayer(uuid);
    }

    public void timeoutMatch() {
        logDebug("Match timed out for world: " + worldName);
        requestSessionConclude("Match Timeout");
    }

    private void requestSessionConclude(String reason) {
        this.state = SessionState.CONCLUDED;
        cancelTimer();
        logDebug("Requesting session conclusion for world: " + worldName + " (Reason: " + reason + ")");
        try {
            RGA rgaInstance = RGA.getInstance();
            if (rgaInstance != null) {
                rgaInstance.requestSessionConclude(worldName, reason, Map.copyOf(finishTimes));
            }
        } catch (Throwable ignored) {
            // Safe fallback when RGA is not initialized in unit tests
        }
    }

    public void cancelCountdown() {
        if (countdownTask != null && !countdownTask.isCancelled()) {
            try {
                countdownTask.cancel();
            } catch (Throwable ignored) {
                // Safe fallback for test execution
            }
            countdownTask = null;
        }
    }

    public void cancelTimer() {
        cancelCountdown();
        if (matchTimer != null && !matchTimer.isCancelled()) {
            try {
                matchTimer.cancel();
            } catch (Throwable ignored) {
                // Safe fallback for test execution
            }
            matchTimer = null;
        }
    }

    public SessionState getState() {
        return state;
    }

    public int getCountdownRemaining() {
        return countdownRemaining;
    }

    private Sound getNoteSound() {
        try {
            return Sound.valueOf("BLOCK_NOTE_BLOCK_PLING");
        } catch (Throwable ignored) {
        }
        try {
            return Sound.valueOf("BLOCK_NOTE_BLOCK_HARP");
        } catch (Throwable ignored) {
        }
        return Sound.ENTITY_EXPERIENCE_ORB_PICKUP;
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
        return List.copyOf(activePlayers);
    }

    public Map<UUID, Location> getLastCheckpoints() {
        return Map.copyOf(lastCheckpoints);
    }

    public Map<UUID, Long> getFinishTimes() {
        return Map.copyOf(finishTimes);
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
