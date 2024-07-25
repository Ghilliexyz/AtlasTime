package com.atlasplugins.atlastime;

import com.atlasplugins.atlastime.commands.CommandRouter;
import com.atlasplugins.atlastime.listeners.onPlayerEvent;
import com.atlasplugins.atlastime.tracker.PlayerTimeTracker;
import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.TimeUnit;

public final class Main extends JavaPlugin {

    public static Main instance;

    // Change chat colors
    public static String color(String string) {
        return ChatColor.translateAlternateColorCodes('&', string);
    }

    // PlaceholderAPI
    private boolean isPlaceholderAPIPresent;

    // Config Stuff
    private FileConfiguration settingsConfig;
    private File settingsConfigFile;

    // Player Time Stuff
    public List<TimeFrame> timeFrames; // Make this public so it's accessible

    // Command Router Stuff
    private CommandRouter commandRouter;

    // Player Time Tracker
    private static PlayerTimeTracker playerTimeTracker;

    @Override
    public void onEnable() {
        // Plugin startup logic
        instance = this;

        // Check if PlaceholderAPI is present on the server
        isPlaceholderAPIPresent = checkForPlaceholderAPI();
        if (isPlaceholderAPIPresent) {
            getLogger().info("PlaceholderAPI found, placeholders will be used.");
        } else {
            getLogger().info("PlaceholderAPI not found, placeholders will not be used.");
        }

        // Load custom configs
        loadSettingsConfig();

        // Initialize PlayerTimeTracker
        playerTimeTracker = new PlayerTimeTracker(this);

        // Load configurations and execution statuses
        playerTimeTracker.loadConfiguration(); // Ensure this is called
        playerTimeTracker.loadExecutionStatus(); // Load execution statuses from the database

        // Register commands
        this.commandRouter = new CommandRouter(this);
        getCommand("atlastime").setExecutor(commandRouter);
        getCommand("atlastime").setTabCompleter(commandRouter);

        // Register events
        getServer().getPluginManager().registerEvents(new onPlayerEvent(this), this);

        long autoCheckPlayerTime = getSettingsConfig().getLong("Time-Checker.Time-Checker-Amount");

        // Schedule a task to check playtime every minute
        Bukkit.getScheduler().runTaskTimer(this, () -> playerTimeTracker.checkAllPlayersPlaytime(), 0L, TimeUnit.MINUTES.toSeconds(autoCheckPlayerTime) * 20L);

        // BStats Info
        int pluginId = 22774; // <-- Replace with the id of your plugin!
        Metrics metrics = new Metrics(this, pluginId);

        // Plugin Startup Message
        Bukkit.getConsoleSender().sendMessage(color("&4---------------------"));
        Bukkit.getConsoleSender().sendMessage(color("&7&l[&c&lAtlas Time&7&l] &e1.0.0"));
        Bukkit.getConsoleSender().sendMessage(color(""));
        Bukkit.getConsoleSender().sendMessage(color("&cMade by _Ghillie"));
        Bukkit.getConsoleSender().sendMessage(color(""));
        Bukkit.getConsoleSender().sendMessage(color("&cPlugin &aEnabled"));
        Bukkit.getConsoleSender().sendMessage(color("&4---------------------"));
    }

    @Override
    public void onDisable() {
        // Plugin shutdown logic

        // Plugin Shutdown Message
        Bukkit.getConsoleSender().sendMessage(color("&4---------------------"));
        Bukkit.getConsoleSender().sendMessage(color("&7&l[&c&lAtlas Time&7&l] &e1.0.0"));
        Bukkit.getConsoleSender().sendMessage(color(""));
        Bukkit.getConsoleSender().sendMessage(color("&cMade by _Ghillie"));
        Bukkit.getConsoleSender().sendMessage(color(""));
        Bukkit.getConsoleSender().sendMessage(color("&cPlugin &4Disabled"));
        Bukkit.getConsoleSender().sendMessage(color("&4---------------------"));
    }

    public String setPlaceholders(Player p, String text) {
        if (checkForPlaceholderAPI()) {
            return PlaceholderAPI.setPlaceholders(p, text);
        } else {
            return text;
        }
    }

    private boolean checkForPlaceholderAPI() {
        Plugin plugin = Bukkit.getPluginManager().getPlugin("PlaceholderAPI");
        return plugin != null && plugin.isEnabled();
    }

    public FileConfiguration getSettingsConfig() {
        return settingsConfig;
    }

    public void saveSettingsConfig() {
        try {
            settingsConfig.save(settingsConfigFile);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void loadSettingsConfig() {
        settingsConfigFile = new File(getDataFolder(), "settings.yml");
        if (!settingsConfigFile.exists()) {
            saveResource("settings.yml", false);
        }
        settingsConfig = YamlConfiguration.loadConfiguration(settingsConfigFile);
    }

    public List<TimeFrame> getTimeFrames() {
        return timeFrames;
    }

    public PlayerTimeTracker getPlayerTimeTracker() {
        return playerTimeTracker;
    }

    public static class TimeFrame {
        private final long playtimeThreshold;
        private final List<String> commands;
        private final Map<UUID, Boolean> executedPlayers;

        public TimeFrame(long playtimeThreshold, List<String> commands) {
            this.playtimeThreshold = playtimeThreshold;
            this.commands = commands;
            this.executedPlayers = new HashMap<>();
        }

        public long getPlaytimeThreshold() {
            return playtimeThreshold;
        }

        public List<String> getCommands() {
            return commands;
        }

        public boolean hasExecuted(UUID playerId) {
            boolean executed = executedPlayers.getOrDefault(playerId, false);
//            Main.instance.getLogger().info("Has executed check for player " + playerId + ": " + executed);
            return executed;
        }

        public void markExecuted(UUID playerId) {
            executedPlayers.put(playerId, true);
//            Main.instance.getLogger().info("Marked as executed for player " + playerId);
        }

    }
}
