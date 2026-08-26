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
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.ScoreboardManager;
import org.bukkit.scoreboard.Team;
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
    private final int initialPlayerCount;
    private final int timeLimitSeconds;
    private int elapsedSeconds = 0;
    private final List<UUID> activePlayers;
    private final Map<UUID, Location> lastCheckpoints = new ConcurrentHashMap<>();
    private final Map<UUID, Long> finishTimes = new ConcurrentHashMap<>();
    private final Map<UUID, Set<BlockPos>> discoveredCheckpoints = new ConcurrentHashMap<>();
    private final Map<UUID, String> finishedSplitTimes = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> personalFalls = new ConcurrentHashMap<>();
    private final List<Location> spawnVectors = new CopyOnWriteArrayList<>();
    private final ParkourKitConfig config;
    private final @Nullable Plugin plugin;

    private @Nullable Scoreboard sessionScoreboard;
    private @Nullable Team pkRunnersTeam;

    private volatile SessionState state = SessionState.COUNTDOWN;
    private volatile int countdownRemaining = 3;
    private @Nullable BukkitTask countdownTask;
    private long startTime;
    private @Nullable BukkitTask matchTimer;

    public ParkourSession(String worldName, int initialPlayerCount, int timeLimitSeconds) {
        this(worldName, Collections.emptyList(), createConfigWithTimeLimit(timeLimitSeconds), null, Collections.emptyList(), initialPlayerCount, timeLimitSeconds);
    }

    public ParkourSession(String worldName, @Nullable List<UUID> activePlayers, @Nullable ParkourKitConfig config, @Nullable Plugin plugin) {
        this(worldName, activePlayers, config, plugin, Collections.emptyList(), activePlayers != null ? activePlayers.size() : 0, config != null ? config.getTimeLimitSeconds() : 0);
    }

    public ParkourSession(String worldName, @Nullable List<UUID> activePlayers, @Nullable ParkourKitConfig config, @Nullable Plugin plugin, @Nullable List<Location> spawnVectors) {
        this(worldName, activePlayers, config, plugin, spawnVectors, activePlayers != null ? activePlayers.size() : 0, config != null ? config.getTimeLimitSeconds() : 0);
    }

    private ParkourSession(String worldName, @Nullable List<UUID> activePlayers, @Nullable ParkourKitConfig config, @Nullable Plugin plugin, @Nullable List<Location> spawnVectors, int initialPlayerCount, int timeLimitSeconds) {
        this.worldName = worldName;
        this.activePlayers = new CopyOnWriteArrayList<>(activePlayers != null ? activePlayers : Collections.emptyList());
        if (spawnVectors != null && !spawnVectors.isEmpty()) {
            this.spawnVectors.addAll(spawnVectors);
        }
        this.initialPlayerCount = initialPlayerCount > 0 ? initialPlayerCount : this.activePlayers.size();
        this.config = config != null ? config : new ParkourKitConfig();
        this.timeLimitSeconds = timeLimitSeconds;
        this.plugin = plugin;
        initScoreboardAndTeam();
    }

    private void initScoreboardAndTeam() {
        try {
            ScoreboardManager sm = Bukkit.getScoreboardManager();
            if (sm != null) {
                this.sessionScoreboard = sm.getNewScoreboard();
                Team team = this.sessionScoreboard.getTeam("pk_runners");
                if (team == null) {
                    team = this.sessionScoreboard.registerNewTeam("pk_runners");
                }
                team.setOption(Team.Option.COLLISION_RULE, Team.OptionStatus.NEVER);
                team.setCanSeeFriendlyInvisibles(true);
                this.pkRunnersTeam = team;
            }
        } catch (Throwable ignored) {
            // Safe fallback for headless / mock unit test environments
        }
    }

    private static ParkourKitConfig createConfigWithTimeLimit(int timeLimitSeconds) {
        ParkourKitConfig cfg = new ParkourKitConfig();
        org.bukkit.configuration.file.YamlConfiguration yaml = new org.bukkit.configuration.file.YamlConfiguration();
        yaml.set("parkour.time-limit-seconds", timeLimitSeconds);
        cfg.loadFromConfig(yaml, null);
        return cfg;
    }

    public void bindPlayerScoreboardAndTeam(@Nullable Player player) {
        if (player == null || !player.isOnline()) return;
        if (sessionScoreboard != null) {
            try {
                player.setScoreboard(sessionScoreboard);
            } catch (Throwable ignored) {
            }
        }
        if (pkRunnersTeam != null) {
            try {
                pkRunnersTeam.addEntry(player.getName());
            } catch (Throwable ignored) {
            }
        }
    }

    public void cleanupScoreboardAndTeam() {
        if (pkRunnersTeam != null) {
            try {
                pkRunnersTeam.unregister();
            } catch (Throwable ignored) {
            }
            pkRunnersTeam = null;
        }

        if (sessionScoreboard != null) {
            try {
                ScoreboardManager sm = Bukkit.getScoreboardManager();
                Scoreboard mainBoard = (sm != null) ? sm.getMainScoreboard() : null;
                if (mainBoard != null) {
                    for (UUID uuid : activePlayers) {
                        try {
                            Player p = Bukkit.getPlayer(uuid);
                            if (p != null && p.isOnline() && p.getScoreboard() == sessionScoreboard) {
                                p.setScoreboard(mainBoard);
                            }
                        } catch (Throwable ignored) {
                        }
                    }
                }
            } catch (Throwable ignored) {
            }
            sessionScoreboard = null;
        }
    }

    public void setSpawnVectors(List<Location> locations) {
        this.spawnVectors.clear();
        if (locations != null && !locations.isEmpty()) {
            this.spawnVectors.addAll(locations);
        }
    }

    public List<Location> getSpawnVectors() {
        return List.copyOf(spawnVectors);
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
            for (int i = 0; i < players.size(); i++) {
                Player player = players.get(i);
                if (player != null && player.isOnline()) {
                    Location targetSpawn = null;
                    if (!spawnVectors.isEmpty()) {
                        targetSpawn = spawnVectors.get(i % spawnVectors.size());
                    } else if (sessionWorld != null) {
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
                        try {
                            player.teleportAsync(spawnLocation);
                        } catch (Throwable fallback) {
                            try {
                                player.teleport(spawnLocation);
                            } catch (Throwable ignored) {
                            }
                        }
                        logDebug(String.format(
                                "Snapshotted initial spawn location for %s at (%.1f, %.1f, %.1f) in world %s",
                                player.getName(), spawnLocation.getX(), spawnLocation.getY(), spawnLocation.getZ(), worldName
                        ));
                    }

                    bindPlayerScoreboardAndTeam(player);
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
                if (timeLimitSeconds > 0 && initialPlayerCount > 1) {
                    long ticks = timeLimitSeconds * 20L;
                    matchTimer = Bukkit.getScheduler().runTaskLater(plugin, this::timeoutMatch, ticks);
                }
            }
        }
    }

    public void tick() {
        // Bypass time-out checks if disabled (<= 0) or during Solo QA testing (initialPlayerCount == 1)
        if (timeLimitSeconds <= 0 || initialPlayerCount == 1) {
            return;
        }

        elapsedSeconds++;
        if (elapsedSeconds >= timeLimitSeconds) {
            timeoutMatch();
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
        personalFalls.merge(uuid, 1, Integer::sum);

        Location checkpoint = getLastCheckpoint(uuid);
        if (checkpoint == null) {
            try {
                checkpoint = player.getLocation();
            } catch (Throwable ignored) {
                // Final fallback
            }
        }

        logDebug("Player " + player.getName() + " failed (Fall #" + getFallCount(uuid) + ", Y <= " + config.getFallThresholdY() + " or fluid), resetting to checkpoint.");

        if (checkpoint != null) {
            try {
                player.teleportAsync(checkpoint);
            } catch (Throwable fallback) {
                try {
                    player.teleport(checkpoint);
                } catch (Throwable ignored) {
                    // Safe fallback for test execution
                }
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
        // Bypass auto-conclude in Solo QA mode (initialPlayerCount == 1) so win conditions freeze for continuous testing
        if (initialPlayerCount > 1 && finishTimes.size() >= activePlayers.size()) {
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

    public int getFallCount(UUID uuid) {
        return personalFalls.getOrDefault(uuid, 0);
    }

    public Map<UUID, Integer> getPersonalFalls() {
        return Map.copyOf(personalFalls);
    }

    public boolean isSpectator(UUID uuid) {
        return hasFinished(uuid) || !isActivePlayer(uuid);
    }

    public void timeoutMatch() {
        // Bypass time-out checks if disabled (<= 0) or during Solo QA testing (initialPlayerCount == 1)
        if (timeLimitSeconds <= 0 || initialPlayerCount == 1) {
            logDebug("Timeout bypassed due to unlimited match duration (<= 0) or Solo QA mode (initialPlayerCount == 1).");
            return;
        }
        logDebug("Match timed out for world: " + worldName);
        requestSessionConclude("Match Timeout");
    }

    public int getInitialPlayerCount() {
        return initialPlayerCount;
    }

    public int getTimeLimitSeconds() {
        return timeLimitSeconds;
    }

    public int getElapsedSeconds() {
        return elapsedSeconds;
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
        cleanupScoreboardAndTeam();
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

    public @Nullable Scoreboard getScoreboard() {
        return sessionScoreboard;
    }

    public @Nullable Team getTeam() {
        return pkRunnersTeam;
    }
}
