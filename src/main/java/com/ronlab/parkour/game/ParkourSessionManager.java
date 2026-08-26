package com.ronlab.parkour.game;

import com.ronlab.parkour.config.ParkourKitConfig;
import org.bukkit.Location;
import org.bukkit.plugin.Plugin;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages active parkour sessions keyed by world name.
 */
@NullMarked
public class ParkourSessionManager {

    private final Map<String, ParkourSession> activeSessions = new ConcurrentHashMap<>();

    public ParkourSession createSession(String worldName, @Nullable List<UUID> activePlayers, @Nullable ParkourKitConfig config, @Nullable Plugin plugin) {
        return createSession(worldName, activePlayers, config, plugin, null);
    }

    public ParkourSession createSession(String worldName, @Nullable List<UUID> activePlayers, @Nullable ParkourKitConfig config, @Nullable Plugin plugin, @Nullable List<Location> spawnVectors) {
        if (worldName == null || worldName.isBlank()) {
            throw new IllegalArgumentException("World name cannot be null or empty");
        }
        ParkourSession session = new ParkourSession(worldName, activePlayers, config, plugin, spawnVectors);
        activeSessions.put(worldName, session);
        return session;
    }

    public @Nullable ParkourSession getSession(@Nullable String worldName) {
        if (worldName == null) return null;
        return activeSessions.get(worldName);
    }

    public boolean hasSession(@Nullable String worldName) {
        return worldName != null && activeSessions.containsKey(worldName);
    }

    public @Nullable ParkourSession removeSession(@Nullable String worldName) {
        if (worldName == null) return null;
        ParkourSession session = activeSessions.remove(worldName);
        if (session != null) {
            session.cancelTimer();
        }
        return session;
    }

    public void clearAll() {
        for (ParkourSession session : activeSessions.values()) {
            session.cancelTimer();
        }
        activeSessions.clear();
    }

    public Map<String, ParkourSession> getActiveSessions() {
        return Collections.unmodifiableMap(activeSessions);
    }
}
