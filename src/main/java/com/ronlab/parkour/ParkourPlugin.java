package com.ronlab.parkour;

import com.ronlab.parkour.config.ParkourKitConfig;
import com.ronlab.parkour.game.ParkourSessionManager;
import com.ronlab.parkour.listener.ParkourLifecycleListener;
import com.ronlab.parkour.listener.ParkourPlayerListener;
import org.bukkit.plugin.java.JavaPlugin;
import org.jspecify.annotations.NullMarked;

/**
 * Main JavaPlugin class for rgaParkour, a native companion plugin for Ronlab Game Assistant.
 */
@NullMarked
public class ParkourPlugin extends JavaPlugin {

    private ParkourKitConfig kitConfig = new ParkourKitConfig();
    private ParkourSessionManager sessionManager = new ParkourSessionManager();

    @Override
    public void onEnable() {
        saveDefaultConfig();

        kitConfig = new ParkourKitConfig();
        kitConfig.loadFromConfig(getConfig(), getLogger());

        sessionManager = new ParkourSessionManager();

        getServer().getPluginManager().registerEvents(new ParkourLifecycleListener(this, sessionManager), this);
        getServer().getPluginManager().registerEvents(new ParkourPlayerListener(sessionManager, kitConfig), this);

        getLogger().info("rgaParkour v" + getDescription().getVersion() + " enabled.");
    }

    @Override
    public void onDisable() {
        if (sessionManager != null) {
            sessionManager.clearAll();
        }
        getLogger().info("rgaParkour disabled.");
    }

    public ParkourKitConfig getKitConfig() {
        return kitConfig;
    }

    public ParkourSessionManager getSessionManager() {
        return sessionManager;
    }
}
