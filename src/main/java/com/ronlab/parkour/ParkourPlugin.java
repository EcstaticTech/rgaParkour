package com.ronlab.parkour;

import com.ronlab.parkour.config.ParkourKitConfig;
import com.ronlab.parkour.game.ParkourScoreboardManager;
import com.ronlab.parkour.game.ParkourSessionManager;
import com.ronlab.parkour.listener.ParkourLifecycleListener;
import com.ronlab.parkour.listener.ParkourPlayerListener;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.jspecify.annotations.NullMarked;

import java.io.File;

/**
 * Main JavaPlugin class for rgaParkour, a native companion plugin for Ronlab Game Assistant.
 */
@NullMarked
public class ParkourPlugin extends JavaPlugin {

    private ParkourKitConfig kitConfig = new ParkourKitConfig();
    private ParkourSessionManager sessionManager = new ParkourSessionManager();
    private ParkourScoreboardManager scoreboardManager = new ParkourScoreboardManager();

    @Override
    public void onEnable() {
        // Ensure both settings.yml and config.yml can be loaded cleanly
        saveResourceIfNotExists("settings.yml");
        saveResourceIfNotExists("config.yml");

        File settingsFile = new File(getDataFolder(), "settings.yml");
        FileConfiguration config;
        if (settingsFile.exists()) {
            config = YamlConfiguration.loadConfiguration(settingsFile);
        } else {
            config = getConfig();
        }

        kitConfig = new ParkourKitConfig();
        kitConfig.loadFromConfig(config, getLogger());

        sessionManager = new ParkourSessionManager();
        scoreboardManager = new ParkourScoreboardManager();
        scoreboardManager.start(this, sessionManager);

        getServer().getPluginManager().registerEvents(new ParkourLifecycleListener(this, sessionManager, scoreboardManager), this);
        getServer().getPluginManager().registerEvents(new ParkourPlayerListener(sessionManager, kitConfig, scoreboardManager), this);

        getLogger().info("rgaParkour v" + getDescription().getVersion() + " enabled.");
    }

    @Override
    public void onDisable() {
        if (scoreboardManager != null) {
            scoreboardManager.stop();
        }
        if (sessionManager != null) {
            sessionManager.clearAll();
        }
        getLogger().info("rgaParkour disabled.");
    }

    public void saveResourceIfNotExists(String resourcePath) {
        File file = new File(getDataFolder(), resourcePath);
        if (!file.exists()) {
            try {
                saveResource(resourcePath, false);
            } catch (Throwable ignored) {
                // Ignore if resource path does not exist in JAR
            }
        }
    }

    public ParkourKitConfig getKitConfig() {
        return kitConfig;
    }

    public ParkourSessionManager getSessionManager() {
        return sessionManager;
    }

    public ParkourScoreboardManager getScoreboardManager() {
        return scoreboardManager;
    }
}
