package com.atlasplugins.atlastime;

import com.atlasplugins.atlastime.commands.CommandRouter;
import com.atlasplugins.atlastime.listeners.onPlayerEvent;
import com.atlasplugins.atlastime.tracker.PlayerDailyPlayTimeTracker;
import com.atlasplugins.atlastime.tracker.PlayerTotalPlayTimeTracker;
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
    private FileConfiguration totalPlayTimeConfig;
    private File totalPlayTimeConfigFile;
    private FileConfiguration dailyPlayTimeConfig;
    private File dailyPlayTimeConfigFile;

    // Player Time Stuff
    public List<TotalPlayTimeFrames> totalPlayTimeFrames; // Make this public so it's accessible
    public List<DailyPlayTimeFrames> dailyPlayTimeFrames; // Make this public so it's accessible

    // Command Router Stuff
    private CommandRouter commandRouter;

    // Player Time Trackers
    private static PlayerTotalPlayTimeTracker playerTotalPlayTimeTracker;
    private static PlayerDailyPlayTimeTracker playerDailyPlayTimeTracker;

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
        loadTotalPlayTimeConfig();
        loadDailyPlayTimeConfig();

        // Initialize Player Trackers
        playerTotalPlayTimeTracker = new PlayerTotalPlayTimeTracker(this);
        playerDailyPlayTimeTracker = new PlayerDailyPlayTimeTracker(this);

        // Load configurations and execution statuses
        playerTotalPlayTimeTracker.loadConfiguration(); // Ensure this is called
        playerTotalPlayTimeTracker.loadExecutionStatus(); // Load execution statuses from the database
        playerDailyPlayTimeTracker.loadConfiguration(); // Ensure this is called
        playerDailyPlayTimeTracker.loadExecutionStatus(); // Load execution statuses from the database

        // Register commands
        this.commandRouter = new CommandRouter(this);
        getCommand("atlastime").setExecutor(commandRouter);
        getCommand("atlastime").setTabCompleter(commandRouter);

        // Register events
        getServer().getPluginManager().registerEvents(new onPlayerEvent(this, playerDailyPlayTimeTracker), this);

        long autoCheckPlayerTime = getSettingsConfig().getLong("Time-Checker.Time-Checker-Amount");

        // Schedule a task to check playtime every minute
        Bukkit.getScheduler().runTaskTimer(this, this::CheckTimeTrackers, 0L, TimeUnit.MINUTES.toSeconds(autoCheckPlayerTime) * 20L);

        // BStats Info
        int pluginId = 22774; // <-- Replace with the id of your plugin!
        Metrics metrics = new Metrics(this, pluginId);

        // Plugin Startup Message
        Bukkit.getConsoleSender().sendMessage(color("&4---------------------"));
        Bukkit.getConsoleSender().sendMessage(color("&7&l[&c&lAtlas Time&7&l] &e1.0.1"));
        Bukkit.getConsoleSender().sendMessage(color(""));
        Bukkit.getConsoleSender().sendMessage(color("&cMade by _Ghillie"));
        Bukkit.getConsoleSender().sendMessage(color(""));
        Bukkit.getConsoleSender().sendMessage(color("&cPlugin &aEnabled"));
        Bukkit.getConsoleSender().sendMessage(color("&4---------------------"));
    }

    @Override
    public void onDisable() {
        // Plugin shutdown logic
//        Bukkit.getScheduler().cancelTasks(this);

        getPlayerTotalPlayTimeTracker().closeConnection();
        getPlayerDailyPlayTimeTracker().closeConnection();

        // Plugin Shutdown Message
        Bukkit.getConsoleSender().sendMessage(color("&4---------------------"));
        Bukkit.getConsoleSender().sendMessage(color("&7&l[&c&lAtlas Time&7&l] &e1.0.1"));
        Bukkit.getConsoleSender().sendMessage(color(""));
        Bukkit.getConsoleSender().sendMessage(color("&cMade by _Ghillie"));
        Bukkit.getConsoleSender().sendMessage(color(""));
        Bukkit.getConsoleSender().sendMessage(color("&cPlugin &4Disabled"));
        Bukkit.getConsoleSender().sendMessage(color("&4---------------------"));
    }

    private void CheckTimeTrackers() {
        playerTotalPlayTimeTracker.checkAllPlayersPlaytime();
        playerDailyPlayTimeTracker.checkAllPlayersPlaytime();
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

    public FileConfiguration getTotalPlayTimeConfig() {
        return totalPlayTimeConfig;
    }

    public FileConfiguration getDailyPlayTimeConfig() {
        return dailyPlayTimeConfig;
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

    public void saveTotalPlayTimeConfig() {
        try {
            totalPlayTimeConfig.save(totalPlayTimeConfigFile);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void loadTotalPlayTimeConfig() {
        totalPlayTimeConfigFile = new File(getDataFolder(), "totalPlayTime.yml");
        if (!totalPlayTimeConfigFile.exists()) {
            saveResource("totalPlayTime.yml", false);
        }
        totalPlayTimeConfig = YamlConfiguration.loadConfiguration(totalPlayTimeConfigFile);
    }

    public void saveDailyPlayTimeConfig() {
        try {
            dailyPlayTimeConfig.save(dailyPlayTimeConfigFile);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void loadDailyPlayTimeConfig() {
        dailyPlayTimeConfigFile = new File(getDataFolder(), "dailyPlayTime.yml");
        if (!dailyPlayTimeConfigFile.exists()) {
            saveResource("dailyPlayTime.yml", false);
        }
        dailyPlayTimeConfig = YamlConfiguration.loadConfiguration(dailyPlayTimeConfigFile);
    }

    public List<TotalPlayTimeFrames> getTotalPlayTimeFrames() {
        return totalPlayTimeFrames;
    }

    public List<DailyPlayTimeFrames> getDailyPlayTimeFrames() {
        return dailyPlayTimeFrames;
    }

    public PlayerTotalPlayTimeTracker getPlayerTotalPlayTimeTracker() {
        return playerTotalPlayTimeTracker;
    }

    public PlayerDailyPlayTimeTracker getPlayerDailyPlayTimeTracker() {
        return playerDailyPlayTimeTracker;
    }

    public static class TotalPlayTimeFrames {
        private final long totalPlaytimeThreshold;
        private final List<String> commands;
        private final Map<UUID, Boolean> executedPlayers;

        public TotalPlayTimeFrames(long playtimeThreshold, List<String> commands) {
            this.totalPlaytimeThreshold = playtimeThreshold;
            this.commands = commands;
            this.executedPlayers = new HashMap<>();
        }

        public long getTotalPlaytimeThreshold() {
            return totalPlaytimeThreshold;
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

    public static class DailyPlayTimeFrames {
        private final long dailyPlaytimeThreshold;
        private final List<String> commands;
        private final Map<UUID, Boolean> executedPlayers;

        public DailyPlayTimeFrames(long playtimeThreshold, List<String> commands) {
            this.dailyPlaytimeThreshold = playtimeThreshold;
            this.commands = commands;
            this.executedPlayers = new HashMap<>();
        }

        public long getDailyPlaytimeThreshold() {
            return dailyPlaytimeThreshold;
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
