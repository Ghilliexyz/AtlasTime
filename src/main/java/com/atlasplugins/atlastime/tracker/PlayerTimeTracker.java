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

public class PlayerTimeTracker {

    private Main main;
    private Connection connection;

    public PlayerTimeTracker(Main main) {
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
        connection = DriverManager.getConnection("jdbc:sqlite:" + main.getDataFolder() + "/playtimeRewards.db");
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
                    if (timeFrameIndex >= 0 && timeFrameIndex < main.timeFrames.size()) {
                        Main.TimeFrame timeFrame = main.timeFrames.get(timeFrameIndex);
                        timeFrame.markExecuted(playerUUID); // Load into memory
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

            String playtimeTotal = getPlayTime(player);


            // Convert time components into a total threshold in seconds
//            long threshold = weeks * 604800 + days * 86400 + hours * 3600 + minutes * 60 + seconds;
            List<Main.TimeFrame> timeFrames = main.getTimeFrames();
            int timeFrameIndex = 0;

            for (Main.TimeFrame timeFrame : timeFrames) {
//                main.getLogger().info("Checking TimeFrame " + (timeFrameIndex + 1) + " for player " + player.getName());
//                main.getLogger().info("Player playtime: " + playtimeMinutes + " minutes, Threshold: " + timeFrame.getPlaytimeThreshold());
//                main.getLogger().info("hasExecuted: " + timeFrame.hasExecuted(playerId));

                if(player.getStatistic(Statistic.PLAY_ONE_MINUTE) < timeFrame.getPlaytimeThreshold())
                {
                    saveExecutionStatus(playerId, timeFrameIndex, false);
                }


                // Debugging: print the total threshold
                if (playtimeSeconds >= timeFrame.getPlaytimeThreshold() && !timeFrame.hasExecuted(playerId)) {
//                    main.getLogger().info("Threshold met for TimeFrame " + (timeFrameIndex + 1) + ". Executing commands.");
//                    main.getLogger().info("Commands:" + timeFrame.getCommands());

                    for (String command : timeFrame.getCommands()) {
                        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command.replace("{playerName}", player.getName()));
//                        main.getLogger().info("Executed command for player " + player.getName() + ": " + command);
                    }

                    // Message
                    boolean timeCompletedMessageEnabled = main.getSettingsConfig().isBoolean("Time-Frames.Time-Frame-" + (timeFrameIndex + 1) + ".Time-Frame-Completed-Message-Toggle");
                    if (timeCompletedMessageEnabled) {
                        for (String timeCompletedMessage : main.getSettingsConfig().getStringList("Time-Frames.Time-Frame-" + (timeFrameIndex + 1) + ".Time-Frame-Completed-Message")) {
                            String withPAPISet = main.setPlaceholders(player, timeCompletedMessage);
                            player.sendMessage(Main.color(withPAPISet)
                                    .replace("{playerName}", player.getName())
                                    .replace("{playerPlayTime}", playtimeTotal));
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
                    timeFrame.markExecuted(playerId);
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
        main.timeFrames = new ArrayList<>();
        FileConfiguration config = main.getSettingsConfig();
        int timeFrameIndex = 1;

        // Loop through each time frame in the config
        while (config.contains("Time-Frames.Time-Frame-" + timeFrameIndex)) {
            String timeFrameKey = "Time-Frames.Time-Frame-" + timeFrameIndex;

            // Read the time frame components
            long weeks = config.getLong(timeFrameKey + ".Time-Frame-Weeks");
            long days = config.getLong(timeFrameKey + ".Time-Frame-Days");
            long hours = config.getLong(timeFrameKey + ".Time-Frame-Hours");
            long minutes = config.getLong(timeFrameKey + ".Time-Frame-Minutes");
            long seconds = config.getLong(timeFrameKey + ".Time-Frame-Seconds");

            // Convert time components into a total threshold in seconds
            long threshold = weeks * 604800 + days * 86400 + hours * 3600 + minutes * 60 + seconds;


            // Get the commands and completed message toggle
            List<String> commands = config.getStringList(timeFrameKey + ".Time-Frame-Commands");
            boolean completedMessageToggle = config.getBoolean(timeFrameKey + ".Time-Frame-Completed-Message-Toggle");
            List<String> completedMessage = config.getStringList(timeFrameKey + ".Time-Frame-Completed-Message");

            // Debugging: print the total threshold
            // Create the TimeFrame object and add it to the list
            Main.TimeFrame timeFrame = new Main.TimeFrame(threshold, commands);
            main.timeFrames.add(timeFrame);

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
        int weeks = days / 7;

        String timeFormat = main.getSettingsConfig().getString("Time-Checker.Time-Formatter");

        // reformat the ticks into timer.
        assert timeFormat != null;
        return String.format(timeFormat, weeks, days % 7, hours % 24, minutes % 60, seconds % 60);
    }
}
