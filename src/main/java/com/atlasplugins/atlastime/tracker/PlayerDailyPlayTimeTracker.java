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

public class PlayerDailyPlayTimeTracker {
    private Main main;
    private Connection connection;

    public PlayerDailyPlayTimeTracker(Main main) {
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
        connection = DriverManager.getConnection("jdbc:sqlite:" + main.getDataFolder() + "/DailyPlayTime.db");
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

                if (executed) {
                    if (timeFrameIndex >= 0 && timeFrameIndex < main.dailyPlayTimeFrames.size()) {
                        Main.DailyPlayTimeFrames dailyPlayTimeFrames = main.dailyPlayTimeFrames.get(timeFrameIndex);
                        dailyPlayTimeFrames.markExecuted(playerUUID); // Load into memory
                    }
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void checkAllPlayersPlaytime() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            UUID playerId = player.getUniqueId();
            int playtimeSeconds = player.getStatistic(Statistic.PLAY_ONE_MINUTE) / 20; // convert ticks to seconds
            int playtimeMinutes = player.getStatistic(Statistic.PLAY_ONE_MINUTE) / (20 * 60); // convert ticks to minutes
            int playtimeHours = player.getStatistic(Statistic.PLAY_ONE_MINUTE) / (20 * 60 * 60); // convert ticks to hours
            int playtimeDays = player.getStatistic(Statistic.PLAY_ONE_MINUTE) / (20 * 60 * 60 * 24); // convert ticks to days
            int playtimeWeeks = player.getStatistic(Statistic.PLAY_ONE_MINUTE) / (20 * 60 * 60 * 24 * 7); // convert ticks to weeks

            String playtimeDaily = getPlayTime(player);

            // Convert time components into a daily threshold in seconds
//            long threshold = weeks * 604800 + days * 86400 + hours * 3600 + minutes * 60 + seconds;
            List<Main.DailyPlayTimeFrames> dailyPlayTimeFrames = main.getDailyPlayTimeFrames();
            int timeFrameIndex = 0;

            for (Main.DailyPlayTimeFrames TimeFrames : dailyPlayTimeFrames) {
                main.getLogger().info("Checking TimeFrame " + (timeFrameIndex + 1) + " for player " + player.getName());
                main.getLogger().info("Player playtime: " + playtimeMinutes + " minutes, Threshold: " + TimeFrames.getPlaytimeThreshold());
                main.getLogger().info("hasExecuted: " + TimeFrames.hasExecuted(playerId));

                if(player.getStatistic(Statistic.PLAY_ONE_MINUTE) < TimeFrames.getPlaytimeThreshold())
                {
                    saveExecutionStatus(playerId, timeFrameIndex, false);
                }


                // Debugging: print the daily threshold
                if (playtimeSeconds >= TimeFrames.getPlaytimeThreshold() && !TimeFrames.hasExecuted(playerId)) {
//                    main.getLogger().info("Threshold met for TimeFrame " + (timeFrameIndex + 1) + ". Executing commands.");
//                    main.getLogger().info("Commands:" + timeFrame.getCommands());

                    for (String command : TimeFrames.getCommands()) {
                        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command.replace("{playerName}", player.getName()));
//                        main.getLogger().info("Executed command for player " + player.getName() + ": " + command);
                    }

                    // Message
                    boolean timeCompletedMessageEnabled = main.getDailyPlayTimeConfig().isBoolean("DailyPlayTime-Frames.DailyPlayTime-Frame-" + (timeFrameIndex + 1) + ".DailyPlayTime-Frame-Completed-Message-Toggle");
                    if (timeCompletedMessageEnabled) {
                        for (String timeCompletedMessage : main.getDailyPlayTimeConfig().getStringList("DailyPlayTime-Frames.DailyPlayTime-Frame-" + (timeFrameIndex + 1) + ".DailyPlayTime-Frame-Completed-Message")) {
                            String withPAPISet = main.setPlaceholders(player, timeCompletedMessage);
                            player.sendMessage(Main.color(withPAPISet)
                                    .replace("{playerName}", player.getName())
                                    .replace("{playerDailyPlayTime}", playtimeDaily));
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
//                    main.getLogger().info("Marked TimeFrame " + (timeFrameIndex + 1) + " as executed for player " + player.getName());
                }
//                else {
//                    main.getLogger().info("Threshold not met or already executed for TimeFrame " + (timeFrameIndex + 1));
//                }
                timeFrameIndex++;
            }
        }
    }

    public void loadConfiguration() {
        // Initialize timeFrames list
        main.dailyPlayTimeFrames = new ArrayList<>();
        FileConfiguration config = main.getDailyPlayTimeConfig();
        int timeFrameIndex = 1;
        main.getLogger().info("I WAS HERE");

        // Loop through each time frame in the config
        while (config.contains("DailyPlayTime-Frames.DailyPlayTime-Frame-" + timeFrameIndex)) {
            String timeFrameKey = "DailyPlayTime-Frames.DailyPlayTime-Frame-" + timeFrameIndex;

            // Read the time frame components
            long weeks = config.getLong(timeFrameKey + ".Time-Frame-Weeks");
            long days = config.getLong(timeFrameKey + ".Time-Frame-Days");
            long hours = config.getLong(timeFrameKey + ".Time-Frame-Hours");
            long minutes = config.getLong(timeFrameKey + ".Time-Frame-Minutes");
            long seconds = config.getLong(timeFrameKey + ".Time-Frame-Seconds");

            // Convert time components into a daily threshold in seconds
            long threshold = weeks * 604800 + days * 86400 + hours * 3600 + minutes * 60 + seconds;


            // Get the commands and completed message toggle
            List<String> commands = config.getStringList(timeFrameKey + ".DailyPlayTime-Frame-Commands");

            // Debugging: print the daily threshold
            // Create the TimeFrame object and add it to the list
            Main.DailyPlayTimeFrames dailyPlayTimeFrames = new Main.DailyPlayTimeFrames(threshold, commands);
            main.dailyPlayTimeFrames.add(dailyPlayTimeFrames);

            main.getLogger().info("------------------------------------------------");
            main.getLogger().info("Threshold-" + timeFrameIndex + ": " + threshold);
            main.getLogger().info("Commands-" + timeFrameIndex + ": " + commands);

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

        // StringBuilder to build the dynamic format string
        StringBuilder timeString = new StringBuilder();
        String timeFormat = main.getSettingsConfig().getString("Time-Checker.Time-Formatter");

        // Reformat the ticks into timer.
        if (years > 0) {
            timeString.append(String.format("%d years ", years));
        }
        if (months > 0) {
            timeString.append(String.format("%d months ", months));
        }
        if (weeks > 0) {
            timeString.append(String.format("%d weeks ", weeks));
        }
        if (days > 0) {
            timeString.append(String.format("%d days ", days));
        }
        if (hours % 24 > 0) {
            timeString.append(String.format("%d hours ", hours % 24));
        }
        if (minutes % 60 > 0) {
            timeString.append(String.format("%d min ", minutes % 60));
        }
        if (seconds % 60 > 0) {
            timeString.append(String.format("%d sec", seconds % 60));
        }

        // Remove trailing space if any
        return timeString.toString().trim();
    }
}
