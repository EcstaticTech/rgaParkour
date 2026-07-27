package com.ronlab.parkour.config;

import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;

/**
 * Configuration holder and parser for parkour block roles and game thresholds.
 */
@NullMarked
public class ParkourKitConfig {

    private final Set<Material> checkpointMaterials = new HashSet<>();
    private final Set<Material> finishMaterials = new HashSet<>();
    private final Set<Material> failMaterials = new HashSet<>();

    private double fallThresholdY = -10.0;
    private int maxMatchDurationSeconds = 300;
    private int invulnerabilitySecondsOnFail = 1;

    public ParkourKitConfig() {
        // Default fallback values
        checkpointMaterials.add(Material.LIGHT_WEIGHTED_PRESSURE_PLATE);
        finishMaterials.add(Material.HEAVY_WEIGHTED_PRESSURE_PLATE);
        failMaterials.add(Material.LAVA);
        failMaterials.add(Material.WATER);
    }

    public void loadFromConfig(FileConfiguration config, @Nullable Logger logger) {
        parseMaterials(config.getStringList("parkour-kit.checkpoint-materials"), checkpointMaterials, "checkpoint", logger);
        parseMaterials(config.getStringList("parkour-kit.finish-materials"), finishMaterials, "finish", logger);
        parseMaterials(config.getStringList("parkour-kit.fail-materials"), failMaterials, "fail", logger);

        this.fallThresholdY = config.getDouble("game.fall-threshold-y", -10.0);
        this.maxMatchDurationSeconds = config.getInt("game.max-match-duration-seconds", 300);
        this.invulnerabilitySecondsOnFail = config.getInt("game.invulnerability-seconds-on-fail", 1);
    }

    private void parseMaterials(@Nullable List<String> rawNames, Set<Material> targetSet, String category, @Nullable Logger logger) {
        if (rawNames == null || rawNames.isEmpty()) {
            return;
        }
        Set<Material> parsed = new HashSet<>();
        for (String raw : rawNames) {
            if (raw == null || raw.isBlank()) continue;
            Material mat = resolveMaterialAlias(raw.trim());
            if (mat != null) {
                parsed.add(mat);
            } else if (logger != null) {
                logger.warning("Invalid material '" + raw + "' specified in parkour-kit." + category + "-materials.");
            }
        }
        if (!parsed.isEmpty()) {
            targetSet.clear();
            targetSet.addAll(parsed);
        }
    }

    private @Nullable Material resolveMaterialAlias(String input) {
        String upper = input.toUpperCase().trim();
        if ("GOLD_PRESSURE_PLATE".equals(upper)) {
            return Material.LIGHT_WEIGHTED_PRESSURE_PLATE;
        }
        if ("IRON_PRESSURE_PLATE".equals(upper)) {
            return Material.HEAVY_WEIGHTED_PRESSURE_PLATE;
        }
        return Material.matchMaterial(input);
    }

    public boolean isCheckpointMaterial(@Nullable Material material) {
        return material != null && checkpointMaterials.contains(material);
    }

    public boolean isFinishMaterial(@Nullable Material material) {
        return material != null && finishMaterials.contains(material);
    }

    public boolean isFailMaterial(@Nullable Material material) {
        return material != null && failMaterials.contains(material);
    }

    public Set<Material> getCheckpointMaterials() {
        return Collections.unmodifiableSet(checkpointMaterials);
    }

    public Set<Material> getFinishMaterials() {
        return Collections.unmodifiableSet(finishMaterials);
    }

    public Set<Material> getFailMaterials() {
        return Collections.unmodifiableSet(failMaterials);
    }

    public double getFallThresholdY() {
        return fallThresholdY;
    }

    public int getMaxMatchDurationSeconds() {
        return maxMatchDurationSeconds;
    }

    public int getInvulnerabilitySecondsOnFail() {
        return invulnerabilitySecondsOnFail;
    }
}
