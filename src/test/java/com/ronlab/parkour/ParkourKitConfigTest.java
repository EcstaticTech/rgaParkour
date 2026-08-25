package com.ronlab.parkour;

import com.ronlab.parkour.config.ParkourKitConfig;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.StringReader;

import static org.junit.jupiter.api.Assertions.*;

class ParkourKitConfigTest {

    private ParkourKitConfig config;

    @BeforeEach
    void setUp() {
        config = new ParkourKitConfig();
    }

    @Test
    @DisplayName("Verify default material mappings and game properties (rga-api:1.13.1 compliant)")
    void testDefaults() {
        assertTrue(config.isCheckpointMaterial(Material.LIGHT_WEIGHTED_PRESSURE_PLATE));

        assertTrue(config.isFinishMaterial(Material.HEAVY_WEIGHTED_PRESSURE_PLATE));

        assertTrue(config.isFailMaterial(Material.LAVA));
        assertTrue(config.isFailMaterial(Material.WATER));

        assertEquals(-60.0, config.getFallThresholdY());
        assertEquals(0, config.getTimeLimitSeconds());
        assertEquals(0, config.getMaxMatchDurationSeconds());
        assertEquals(1, config.getInvulnerabilitySecondsOnFail());
    }

    @Test
    @DisplayName("Verify YAML material parsing and parkour section compliant schema")
    void testYamlParsingAndFallback() {
        String yamlString = """
                parkour:
                  time-limit-seconds: 120
                  fall-threshold-y: -15.0
                parkour-kit:
                  checkpoint-materials:
                    - "DIAMOND_BLOCK"
                    - "GOLD_PRESSURE_PLATE"
                  finish-materials:
                    - "EMERALD_BLOCK"
                  fail-materials:
                    - "BEDROCK"
                game:
                  invulnerability-seconds-on-fail: 2
                """;

        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(new StringReader(yamlString));
        config.loadFromConfig(yaml, null);

        assertTrue(config.isCheckpointMaterial(Material.DIAMOND_BLOCK));
        assertTrue(config.isCheckpointMaterial(Material.LIGHT_WEIGHTED_PRESSURE_PLATE));

        assertTrue(config.isFinishMaterial(Material.EMERALD_BLOCK));
        assertTrue(config.isFailMaterial(Material.BEDROCK));

        assertEquals(-15.0, config.getFallThresholdY());
        assertEquals(120, config.getTimeLimitSeconds());
        assertEquals(120, config.getMaxMatchDurationSeconds());
        assertEquals(2, config.getInvulnerabilitySecondsOnFail());
    }
}
