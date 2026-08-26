package com.ronlab.parkour.config;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Utility for parsing spawn vectors from map configurations (e.g. map.yml) into Bukkit Location instances.
 */
@NullMarked
public final class SpawnVectorParser {

    private SpawnVectorParser() {
    }

    /**
     * Attempts to locate and parse `map.yml` inside the given world folder.
     *
     * @param world  The target world
     * @param logger Optional logger for warnings
     * @return List of parsed spawn locations, or empty list if not found or unparseable.
     */
    public static List<Location> loadSpawnVectorsFromWorld(@Nullable World world, @Nullable Logger logger) {
        if (world == null) {
            return Collections.emptyList();
        }

        try {
            File worldFolder = world.getWorldFolder();
            if (worldFolder.exists() && worldFolder.isDirectory()) {
                File mapFile = new File(worldFolder, "map.yml");
                if (mapFile.exists() && mapFile.isFile()) {
                    YamlConfiguration yaml = YamlConfiguration.loadConfiguration(mapFile);
                    List<?> rawList = yaml.getList("spawn-vectors");
                    if (rawList != null && !rawList.isEmpty()) {
                        return parseSpawnLocations(world, rawList, logger);
                    }
                }
            }
        } catch (Throwable t) {
            if (logger != null) {
                logger.warning("Failed to load map.yml for world " + world.getName() + ": " + t.getMessage());
            }
        }

        return Collections.emptyList();
    }

    /**
     * Parses a raw list of coordinates (strings or structured maps) into Bukkit Location instances.
     *
     * @param world   The target world for the locations
     * @param rawList Raw list from YAML configuration
     * @param logger  Optional logger for reporting syntax errors
     * @return List of valid Bukkit Location objects
     */
    public static List<Location> parseSpawnLocations(@Nullable World world, @Nullable List<?> rawList, @Nullable Logger logger) {
        if (rawList == null || rawList.isEmpty()) {
            return Collections.emptyList();
        }

        List<Location> locations = new ArrayList<>();
        for (int i = 0; i < rawList.size(); i++) {
            Object entry = rawList.get(i);
            Location loc = parseSingleLocation(world, entry, logger, i);
            if (loc != null) {
                locations.add(loc);
            }
        }

        return Collections.unmodifiableList(locations);
    }

    /**
     * Parses a single object (String coordinate or Map) into a Location.
     */
    public static @Nullable Location parseSingleLocation(@Nullable World world, @Nullable Object raw, @Nullable Logger logger, int index) {
        if (raw == null) {
            return null;
        }

        if (raw instanceof String str) {
            return parseCoordinateString(world, str, logger, index);
        }

        if (raw instanceof Map<?, ?> map) {
            return parseCoordinateMap(world, map, logger, index);
        }

        if (logger != null) {
            logger.warning(String.format("Invalid spawn-vector entry at index %d: unsupported type %s", index, raw.getClass().getSimpleName()));
        }
        return null;
    }

    /**
     * Parses formatted coordinate strings such as:
     * - "X, Y, Z"
     * - "X, Y, Z, Yaw, Pitch"
     * - "X Y Z"
     */
    public static @Nullable Location parseCoordinateString(@Nullable World world, String coordinateStr, @Nullable Logger logger, int index) {
        String trimmed = coordinateStr.trim();
        if (trimmed.isEmpty()) {
            return null;
        }

        String[] parts = trimmed.contains(",") ? trimmed.split(",") : trimmed.split("\\s+");
        if (parts.length < 3) {
            if (logger != null) {
                logger.warning(String.format("Invalid coordinate format at index %d: '%s' (expected at least X, Y, Z)", index, coordinateStr));
            }
            return null;
        }

        try {
            double x = Double.parseDouble(parts[0].trim());
            double y = Double.parseDouble(parts[1].trim());
            double z = Double.parseDouble(parts[2].trim());
            float yaw = 0.0f;
            float pitch = 0.0f;

            if (parts.length >= 4) {
                yaw = Float.parseFloat(parts[3].trim());
            }
            if (parts.length >= 5) {
                pitch = Float.parseFloat(parts[4].trim());
            }

            return new Location(world, x, y, z, yaw, pitch);
        } catch (NumberFormatException e) {
            if (logger != null) {
                logger.warning(String.format("NumberFormatException parsing coordinate '%s' at index %d: %s", coordinateStr, index, e.getMessage()));
            }
            return null;
        }
    }

    /**
     * Parses structured YAML map nodes such as:
     * { x: 10.5, y: 64.0, z: 20.5, yaw: 90.0, pitch: 0.0 }
     */
    public static @Nullable Location parseCoordinateMap(@Nullable World world, Map<?, ?> map, @Nullable Logger logger, int index) {
        try {
            Object rawX = map.get("x");
            Object rawY = map.get("y");
            Object rawZ = map.get("z");

            if (rawX == null || rawY == null || rawZ == null) {
                if (logger != null) {
                    logger.warning(String.format("Missing required X/Y/Z keys in coordinate map at index %d: %s", index, map));
                }
                return null;
            }

            double x = ((Number) rawX).doubleValue();
            double y = ((Number) rawY).doubleValue();
            double z = ((Number) rawZ).doubleValue();

            float yaw = 0.0f;
            float pitch = 0.0f;

            Object rawYaw = map.get("yaw");
            if (rawYaw instanceof Number numYaw) {
                yaw = numYaw.floatValue();
            }

            Object rawPitch = map.get("pitch");
            if (rawPitch instanceof Number numPitch) {
                pitch = numPitch.floatValue();
            }

            return new Location(world, x, y, z, yaw, pitch);
        } catch (Throwable t) {
            if (logger != null) {
                logger.warning(String.format("Error parsing coordinate map at index %d: %s", index, t.getMessage()));
            }
            return null;
        }
    }
}
