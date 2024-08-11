package com.atlasplugins.atlastime.tracker;

import com.atlasplugins.atlastime.Main;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.Statistic;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class PlayerTotalPlayTimeTracker {

    private Main main;
    private Connection connection;

    public PlayerTotalPlayTimeTracker(Main main) {
        this.main = main;
        try {
            openConnection();
            createTable();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
    /**
     * Opens a connection to the SQLite database.
     *
     * @throws SQLException If a database access error occurs.
     */
    private void openConnection() throws SQLException {
        if (connection != null && !connection.isClosed()) {
            return;
        }
        connection = DriverManager.getConnection("jdbc:sqlite:" + main.getDataFolder() + "/TotalPlayTime.db");
    }

    /**
     * Creates the player data table if it does not exist.
     *
     * @throws SQLException If a database access error occurs.
     */
    private void createTable() throws SQLException {
        String createTableSQL = "CREATE TABLE IF NOT EXISTS execution_status (uuid TEXT, time_frame_index INTEGER, executed BOOLEAN, PRIMARY KEY (uuid, time_frame_index))";
        try (PreparedStatement pstmt = connection.prepareStatement(createTableSQL)) {
            pstmt.executeUpdate();
        }
    }

    public void saveExecutionStatus(UUID playerId, int timeFrameIndex, boolean executed) {
        String insertOrUpdateSQL = "INSERT OR REPLACE INTO execution_status (uuid, time_frame_index, executed) VALUES (?, ?, ?)";
        try (PreparedStatement pstmt = connection.prepareStatement(insertOrUpdateSQL)) {
            pstmt.setString(1, playerId.toString());
            pstmt.setInt(2, timeFrameIndex);
            pstmt.setBoolean(3, executed);
            pstmt.executeUpdate(); // No need for manual commit
//            main.getLogger().info("Saving execution status: PlayerID=" + playerId + ", TimeFrameIndex=" + timeFrameIndex + ", Executed=" + executed);
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void loadExecutionStatus() {
        String selectSQL = "SELECT * FROM execution_status";
        try (PreparedStatement pstmt = connection.prepareStatement(selectSQL);
             ResultSet rs = pstmt.executeQuery()) {

            while (rs.next()) {
                UUID playerUUID = UUID.fromString(rs.getString("uuid"));
                int timeFrameIndex = rs.getInt("time_frame_index");
                boolean executed = rs.getBoolean("executed");

                // Handle the retrieved data
                if (executed) {
                    if(main.totalPlayTimeFrames == null) return;
                    if (timeFrameIndex >= 0 && timeFrameIndex < main.totalPlayTimeFrames.size()) {
                        Main.TotalPlayTimeFrames totalPlayTimeFrames = main.totalPlayTimeFrames.get(timeFrameIndex);
                        totalPlayTimeFrames.markExecuted(playerUUID); // Load into memory
                    }
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void checkAllPlayersPlaytime() {
        boolean enableTotalRewards = main.getSettingsConfig().getBoolean("Time-Trackers.TotalPlayTime-Tracker");
        if(enableTotalRewards) {
            for (Player player : Bukkit.getOnlinePlayers()) {

                UUID playerId = player.getUniqueId();

                int playtimeSeconds = player.getStatistic(Statistic.PLAY_ONE_MINUTE) / 20; // convert ticks to seconds
                int playtimeMinutes = player.getStatistic(Statistic.PLAY_ONE_MINUTE) / (20 * 60); // convert ticks to minutes
                int playtimeHours = player.getStatistic(Statistic.PLAY_ONE_MINUTE) / (20 * 60 * 60); // convert ticks to hours
                int playtimeDays = player.getStatistic(Statistic.PLAY_ONE_MINUTE) / (20 * 60 * 60 * 24); // convert ticks to days
                int playtimeWeeks = player.getStatistic(Statistic.PLAY_ONE_MINUTE) / (20 * 60 * 60 * 24 * 7); // convert ticks to weeks

                String playtimeTotal = getPlayTime(player);

                // Convert time components into a total threshold in seconds
//            long threshold = weeks * 604800 + days * 86400 + hours * 3600 + minutes * 60 + seconds;
                List<Main.TotalPlayTimeFrames> totalPlayTimeFrames = main.getTotalPlayTimeFrames();
                int timeFrameIndex = 0;

                for (Main.TotalPlayTimeFrames TimeFrames : totalPlayTimeFrames) {
//                main.getLogger().info("Checking TimeFrame " + (timeFrameIndex + 1) + " for player " + player.getName());
//                main.getLogger().info("Player playtime: " + playtimeMinutes + " minutes, Threshold: " + TimeFrames.getTotalPlaytimeThreshold());
//                main.getLogger().info("hasExecuted: " + TimeFrames.hasExecuted(playerId));

                    // Debugging: print the total threshold
                    if (playtimeSeconds >= TimeFrames.getTotalPlaytimeThreshold() && !TimeFrames.hasExecuted(playerId)) {
//                    main.getLogger().info("Threshold met for TimeFrame " + (timeFrameIndex + 1) + ". Executing commands.");
//                    main.getLogger().info("Commands:" + timeFrame.getCommands());

                        for (String command : TimeFrames.getCommands()) {
                            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command.replace("{playerName}", player.getName()));
//                        main.getLogger().info("Executed command for player " + player.getName() + ": " + command);
                        }

                        // Message
                        boolean timeCompletedMessageEnabled = main.getTotalPlayTimeConfig().isBoolean("TotalPlayTime-Frames.TotalPlayTime-Frame-" + (timeFrameIndex + 1) + ".TotalPlayTime-Frame-Completed-Message-Toggle");
                        if (timeCompletedMessageEnabled) {
                            for (String timeCompletedMessage : main.getTotalPlayTimeConfig().getStringList("TotalPlayTime-Frames.TotalPlayTime-Frame-" + (timeFrameIndex + 1) + ".TotalPlayTime-Frame-Completed-Message")) {
                                String withPAPISet = main.setPlaceholders(player, timeCompletedMessage);
                                player.sendMessage(Main.color(withPAPISet)
                                        .replace("{playerName}", player.getName())
                                        .replace("{playerTotalPlayTime}", playtimeTotal));
                            }
                        }

                        // Sound
                        Sound timeCompletedSound = Sound.valueOf(main.getSettingsConfig().getString("TimeSounds.Time-Completed-Sound"));
                        float timeCompletedVolume = (float) main.getSettingsConfig().getDouble("TimeSounds.Time-Completed-Volume");
                        float timeCompletedPitch = (float) main.getSettingsConfig().getDouble("TimeSounds.Time-Completed-Pitch");

                        boolean isCompletedSoundEnabled = main.getSettingsConfig().getBoolean("TimeSounds.Time-Completed-Sound-Toggle");
                        if (isCompletedSoundEnabled) {
                            player.playSound(player.getLocation(), timeCompletedSound, timeCompletedVolume, timeCompletedPitch);
                        }

                        // Mark as executed and save in database
                        TimeFrames.markExecuted(playerId);
                        saveExecutionStatus(playerId, timeFrameIndex, true);
                    }
                    timeFrameIndex++;
                }
            }
        }
    }

    public void loadConfiguration() {
        // Initialize timeFrames list
        main.totalPlayTimeFrames = new ArrayList<>();
        FileConfiguration config = main.getTotalPlayTimeConfig();
        int timeFrameIndex = 1;

        // Loop through each time frame in the config
        while (config.contains("TotalPlayTime-Frames.TotalPlayTime-Frame-" + timeFrameIndex)) {
            String timeFrameKey = "TotalPlayTime-Frames.TotalPlayTime-Frame-" + timeFrameIndex;

            // Read the time frame components
            long weeks = config.getLong(timeFrameKey + ".TotalPlayTime-Frame-Weeks");
            long days = config.getLong(timeFrameKey + ".TotalPlayTime-Frame-Days");
            long hours = config.getLong(timeFrameKey + ".TotalPlayTime-Frame-Hours");
            long minutes = config.getLong(timeFrameKey + ".TotalPlayTime-Frame-Minutes");
            long seconds = config.getLong(timeFrameKey + ".TotalPlayTime-Frame-Seconds");

            // Convert time components into a total threshold in seconds
            long threshold = weeks * 604800 + days * 86400 + hours * 3600 + minutes * 60 + seconds;

            // Get the commands and completed message toggle
            List<String> commands = config.getStringList(timeFrameKey + ".TotalPlayTime-Frame-Commands");

            // Debugging: print the total threshold
            // Create the TimeFrame object and add it to the list
            Main.TotalPlayTimeFrames totalPlayTimeFrames = new Main.TotalPlayTimeFrames(threshold, commands);
            main.totalPlayTimeFrames.add(totalPlayTimeFrames);

//            main.getLogger().info("----------------Total--------------------------");
//            main.getLogger().info("Threshold-" + timeFrameIndex + ": " + threshold);
//            main.getLogger().info("Commands-" + timeFrameIndex + ": " + commands);

            timeFrameIndex++;
        }
    }

    public void closeConnection() {
        if (connection != null) {
            try {
                connection.close();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
    }

    private List<String> timeFormats;

    public List<String> getTimeFormats() {
        return timeFormats;
    }

    public String getPlayTime(Player p) {
        // Calculator for time since last death.
        int ticks = p.getStatistic(Statistic.PLAY_ONE_MINUTE);
        int seconds = ticks / 20;
        int minutes = seconds / 60;
        int hours = minutes / 60;
        int days = hours / 24;
        int years = days / 365;

        // Adjust days to exclude complete years
        days = days % 365;

        // Calculate months
        int months = days / 30;

        // Adjust days to exclude complete months and weeks
        days = days % 30;
        int weeks = days / 7;

        // Recalculate days to exclude complete weeks
        days = days % 7;

        timeFormats = main.getSettingsConfig().getStringList("Time-Checker.Time-Formatter");

        // Get format strings from TimeFormatter
        List<String> formats = getTimeFormats();

        // StringBuilder to build the dynamic format string
        StringBuilder timeString = new StringBuilder();

        // Format each time component if it exists in the formats list
        if (years > 0 && formats.size() > 0) {
            timeString.append(String.format(formats.get(0), years));
        }
        if (months > 0 && formats.size() > 1) {
            timeString.append(String.format(formats.get(1), months));
        }
        if (weeks > 0 && formats.size() > 2) {
            timeString.append(String.format(formats.get(2), weeks));
        }
        if (days > 0 && formats.size() > 3) {
            timeString.append(String.format(formats.get(3), days));
        }
        if (hours % 24 > 0 && formats.size() > 4) {
            timeString.append(String.format(formats.get(4), hours % 24));
        }
        if (minutes % 60 > 0 && formats.size() > 5) {
            timeString.append(String.format(formats.get(5), minutes % 60));
        }
        if (seconds % 60 >= 0 && formats.size() > 6) {
            timeString.append(String.format(formats.get(6), seconds % 60));
        }

        // Remove trailing space if any
        return Main.color(timeString.toString().trim());
    }
}
