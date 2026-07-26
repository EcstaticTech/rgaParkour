package com.ronlab.parkour;

import com.ronlab.parkour.config.ParkourKitConfig;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.StringReader;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ParkourKitConfigTest {

    private ParkourKitConfig config;

    @BeforeEach
    void setUp() {
        config = new ParkourKitConfig();
    }

    @Test
    @DisplayName("Verify default material mappings and game properties")
    void testDefaults() {
        assertTrue(config.isCheckpointMaterial(Material.LIGHT_WEIGHTED_PRESSURE_PLATE));

        assertTrue(config.isFinishMaterial(Material.HEAVY_WEIGHTED_PRESSURE_PLATE));

        assertTrue(config.isFailMaterial(Material.LAVA));
        assertTrue(config.isFailMaterial(Material.WATER));

        assertEquals(-10.0, config.getFallThresholdY());
        assertEquals(300, config.getMaxMatchDurationSeconds());
        assertEquals(1, config.getInvulnerabilitySecondsOnFail());
    }

    @Test
    @DisplayName("Verify YAML material parsing and fallback defaults for unmapped blocks")
    void testYamlParsingAndFallback() {
        String yamlString = """
                parkour-kit:
                  checkpoint-materials:
                    - "DIAMOND_BLOCK"
                    - "INVALID_MATERIAL_NAME"
                  finish-materials:
                    - "EMERALD_BLOCK"
                  fail-materials:
                    - "BEDROCK"
                game:
                  fall-threshold-y: -20.5
                  max-match-duration-seconds: 180
                  invulnerability-seconds-on-fail: 2
                """;

        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(new StringReader(yamlString));
        config.loadFromConfig(yaml, null);

        assertTrue(config.isCheckpointMaterial(Material.DIAMOND_BLOCK));
        assertFalse(config.isCheckpointMaterial(Material.LIGHT_WEIGHTED_PRESSURE_PLATE));

        assertTrue(config.isFinishMaterial(Material.EMERALD_BLOCK));
        assertTrue(config.isFailMaterial(Material.BEDROCK));

        assertEquals(-20.5, config.getFallThresholdY());
        assertEquals(180, config.getMaxMatchDurationSeconds());
        assertEquals(2, config.getInvulnerabilitySecondsOnFail());
    }
}
