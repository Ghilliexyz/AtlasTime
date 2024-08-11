package com.atlasplugins.atlastime.tracker;

import com.atlasplugins.atlastime.Main;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.File;
import java.sql.*;
import java.util.*;
import java.util.Date;
import java.util.concurrent.TimeUnit;

public class PlayerDailyPlayTimeTracker implements Listener {
    private Main main;
    private Connection connection;
    private Map<UUID, Long> playerLoginTimes = new HashMap<>();

    public PlayerDailyPlayTimeTracker(Main main) {
        this.main = main;
        try {
            openConnection();
            createTable();
            loadExecutionStatus();
            scheduleDailyReset();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private void openConnection() throws SQLException {
        if (connection != null && !connection.isClosed()) {
            return;
        }
        connection = DriverManager.getConnection("jdbc:sqlite:" + main.getDataFolder() + "/DailyPlayTime.db");
    }

    private void createTable() throws SQLException {
        String createTableSQL = "CREATE TABLE IF NOT EXISTS player_data (" +
                "uuid TEXT, " +
                "date DATE, " +
                "playtime_ticks INTEGER, " +
                "time_frame_index INTEGER, " +
                "executed BOOLEAN, " +
                "PRIMARY KEY (uuid, time_frame_index))";
        try (PreparedStatement pstmt = connection.prepareStatement(createTableSQL)) {
            pstmt.executeUpdate();
        }
    }

    public void saveExecutionStatus(UUID playerId, long playtimeTodayTicks, int timeFrameIndex, boolean executed) {
        String insertOrUpdateSQL = "INSERT OR REPLACE INTO player_data (uuid, date, playtime_ticks, time_frame_index, executed) VALUES (?, ?, ?, ?, ?)";
        try (PreparedStatement pstmt = connection.prepareStatement(insertOrUpdateSQL)) {
            pstmt.setString(1, playerId.toString());
            pstmt.setDate(2, new java.sql.Date(System.currentTimeMillis())); // Store current date
            pstmt.setLong(3, playtimeTodayTicks);
            pstmt.setInt(4, timeFrameIndex);
            pstmt.setBoolean(5, executed);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void loadExecutionStatus() {
        String selectSQL = "SELECT * FROM player_data";
        try (PreparedStatement pstmt = connection.prepareStatement(selectSQL);
             ResultSet rs = pstmt.executeQuery()) {

            while (rs.next()) {
                UUID playerUUID = UUID.fromString(rs.getString("uuid"));
                Date date = rs.getDate("date");
                long playtimeTicks = rs.getLong("playtime_ticks");
                int timeFrameIndex = rs.getInt("time_frame_index");
                boolean executed = rs.getBoolean("executed");

                // Handle the retrieved data
                if(executed){
                    if(main.dailyPlayTimeFrames == null) return;
                    if(timeFrameIndex >= 0 && timeFrameIndex < main.dailyPlayTimeFrames.size()){
                        Main.DailyPlayTimeFrames dailyPlayTimeFrames = main.dailyPlayTimeFrames.get(timeFrameIndex);
                        dailyPlayTimeFrames.markExecuted(playerUUID);
                    }
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void checkAllPlayersPlaytime() {
        boolean enableDailyRewards = main.getSettingsConfig().getBoolean("Time-Trackers.DailyPlayTime-Tracker");
        if(enableDailyRewards) {
            for (Player player : Bukkit.getOnlinePlayers()) {
                UUID playerId = player.getUniqueId();

                UpdatePlayerTimer(player);

                // Retrieve playtime ticks from the database
                long playtimeTicks = getPlaytimeTicksFromDatabase(playerId);

                // Convert playtimeTicks to seconds
                int playtimeSeconds = (int) (playtimeTicks / 20);

                String playtimeDaily = getPlayTime(player); // Your existing method to format playtime

                List<Main.DailyPlayTimeFrames> dailyPlayTimeFrames = main.getDailyPlayTimeFrames();
                int timeFrameIndex = 0;

                for (Main.DailyPlayTimeFrames timeFrames : dailyPlayTimeFrames) {
//                    main.getLogger().info("----------------Daily--------------------------");
//                    main.getLogger().info("Checking TimeFrame " + (timeFrameIndex + 1) + " for player " + player.getName());
//                    main.getLogger().info("Player playtime: " + playtimeSeconds + " Seconds, Threshold: " + timeFrames.getDailyPlaytimeThreshold());
//                    main.getLogger().info("hasExecuted: " + timeFrames.hasExecuted(playerId));

                    if (playtimeSeconds >= timeFrames.getDailyPlaytimeThreshold() && !timeFrames.hasExecuted(playerId)) {
//                    main.getLogger().info("Threshold met for TimeFrame " + (timeFrameIndex + 1) + ". Executing commands.");
//                    main.getLogger().info("Commands:" + timeFrame.getCommands());

                        for (String command : timeFrames.getCommands()) {
                            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command.replace("{playerName}", player.getName()));
//                            main.getLogger().info("----------------Daily--------------------------");
//                            main.getLogger().info("Commands-" + timeFrameIndex + ": " + command);
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
                        timeFrames.markExecuted(playerId);
                        saveExecutionStatus(playerId, playtimeTicks, timeFrameIndex, true);
                    }
                    timeFrameIndex++;
                }
            }
        }
    }

    private long getPlaytimeTicksFromDatabase(UUID playerId) {
        String selectSQL = "SELECT playtime_ticks FROM player_data WHERE uuid = ?";
        String querySQL = "SELECT playtime_ticks FROM player_data WHERE uuid = ? AND date = ?";
        long playtimeTicks = 0;

//        main.getLogger().warning("start playtimeTicks: " + playtimeTicks);

        try (PreparedStatement selectPstmt = connection.prepareStatement(selectSQL)) {
            selectPstmt.setString(1, playerId.toString());
            try (ResultSet rs = selectPstmt.executeQuery()) {
                if (rs.next()) {
                    playtimeTicks = rs.getLong("playtime_ticks");
//                    main.getLogger().warning("get playtimeTicks: " + playtimeTicks);
                } else {
                    main.getLogger().warning("No results found for player " + playerId);
                }
            }
        } catch (SQLException e) {
            main.getLogger().severe("Failed to retrieve playtime for player " + playerId + ": " + e.getMessage());
            e.printStackTrace();
        }

//        main.getLogger().warning("return playtimeTicks: " + playtimeTicks);
        return playtimeTicks;
    }

    public void loadConfiguration() {
        // Initialize timeFrames list
        main.dailyPlayTimeFrames = new ArrayList<>();
        FileConfiguration config = main.getDailyPlayTimeConfig();
        int timeFrameIndex = 1;

        // Loop through each time frame in the config
        while (config.contains("DailyPlayTime-Frames.DailyPlayTime-Frame-" + timeFrameIndex)) {
            String timeFrameKey = "DailyPlayTime-Frames.DailyPlayTime-Frame-" + timeFrameIndex;

            // Read the time frame components
            long weeks = config.getLong(timeFrameKey + ".DailyPlayTime-Frame-Weeks");
            long days = config.getLong(timeFrameKey + ".DailyPlayTime-Frame-Days");
            long hours = config.getLong(timeFrameKey + ".DailyPlayTime-Frame-Hours");
            long minutes = config.getLong(timeFrameKey + ".DailyPlayTime-Frame-Minutes");
            long seconds = config.getLong(timeFrameKey + ".DailyPlayTime-Frame-Seconds");

            long threshold = weeks * 604800 + days * 86400 + hours * 3600 + minutes * 60 + seconds;

            List<String> commands = config.getStringList(timeFrameKey + ".DailyPlayTime-Frame-Commands");

            // Debugging: print the total threshold
            // Create the TimeFrame object and add it to the list
            Main.DailyPlayTimeFrames dailyPlayTimeFrames = new Main.DailyPlayTimeFrames(threshold, commands);
            main.dailyPlayTimeFrames.add(dailyPlayTimeFrames);

//            main.getLogger().info("----------------Daily--------------------------");
//            main.getLogger().info("Threshold-" + timeFrameIndex + ": " + threshold);
//            main.getLogger().info("Commands-" + timeFrameIndex + ": " + commands);

            timeFrameIndex++;
        }
    }

    private void scheduleDailyReset() {
        long initialDelay = calculateDelayToMidnightUTC();
        long period = TimeUnit.DAYS.toMillis(1);

        new BukkitRunnable() {
            @Override
            public void run() {
                resetDailyPlayTime();
            }
        }.runTaskTimer(main, initialDelay / 50, period / 50); // Convert milliseconds to ticks
    }

    private long calculateDelayToMidnightUTC() {
        long now = System.currentTimeMillis();
        Calendar calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        calendar.setTimeInMillis(now);
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);

        long midnight = calendar.getTimeInMillis() + (24 * 60 * 60 * 1000); // 24 hours in milliseconds
        return midnight - now;
    }

    public void resetDailyPlayTime() {

        checkAllPlayersPlaytime();

        resetDaily();

        checkAllPlayersPlaytime();

        // Send Time-Command-Message Message in chat when called.
        if(main.getSettingsConfig().getBoolean("TimeDaily-Info.TimeDaily-Message-Toggle")) {
            for (String resetDailyPlayTimeMessage : main.getSettingsConfig().getStringList("TimeDaily-Info.TimeDaily-Message")) {
//            String withPAPISet = main.setPlaceholders((Player) sender, resetDailyPlayTimeMessage);
                main.getServer().broadcastMessage(Main.color(resetDailyPlayTimeMessage));
            }
        }
    }

    public void handlePlayerLogin(Player player) {
        playerLoginTimes.put(player.getUniqueId(), System.currentTimeMillis());
    }

    public void handlePlayerLogout(Player player) {
        UpdatePlayerTimer(player);
    }

    public void UpdatePlayerTimer(Player player) {
        UUID playerId = player.getUniqueId();

        // Update the player's total daily playtime in the database
        long playtimeTicks = getPlayerTime(player);
        updateDailyPlaytime(playerId, playtimeTicks);
    }

    private long getPlayerTime(Player player) {
        UUID playerId = player.getUniqueId();
        long loginTime = playerLoginTimes.getOrDefault(playerId, System.currentTimeMillis());
        long logoutTime = System.currentTimeMillis();
        return (logoutTime - loginTime) / 50; // Convert milliseconds to ticks
    }

    private void updateDailyPlaytime(UUID playerId, long sessionPlaytimeTicks) {
        playerLoginTimes.remove(playerId);

        String selectSQL = "SELECT playtime_ticks FROM player_data WHERE uuid = ?";
        String updateSQL = "UPDATE player_data SET playtime_ticks = ?, date = ? WHERE uuid = ?";
        String insertSQL = "INSERT INTO player_data (uuid, date, playtime_ticks) VALUES (?, ?, ?)";

        try {
            // Retrieve current total playtime
            long totalPlaytimeTicks = 0;
            boolean rowExists = false;

            try (PreparedStatement selectPstmt = connection.prepareStatement(selectSQL)) {
                selectPstmt.setString(1, playerId.toString());
                try (ResultSet rs = selectPstmt.executeQuery()) {
                    if (rs.next()) {
                        totalPlaytimeTicks = rs.getLong("playtime_ticks");
                        rowExists = true;
                    }
                }
            }

            // Update total playtime
            totalPlaytimeTicks += sessionPlaytimeTicks;

            if (rowExists) {
                // If the row exists, update it
                try (PreparedStatement updatePstmt = connection.prepareStatement(updateSQL)) {
                    updatePstmt.setLong(1, totalPlaytimeTicks);
                    updatePstmt.setTimestamp(2, new Timestamp(System.currentTimeMillis()));
                    updatePstmt.setString(3, playerId.toString());
                    updatePstmt.executeUpdate();
                }
            } else {
                // If the row does not exist, insert a new one
                try (PreparedStatement insertPstmt = connection.prepareStatement(insertSQL)) {
                    insertPstmt.setString(1, playerId.toString());
                    insertPstmt.setTimestamp(2, new Timestamp(System.currentTimeMillis()));
                    insertPstmt.setLong(3, totalPlaytimeTicks);
                    insertPstmt.executeUpdate();
                }
            }
        } catch (SQLException e) {
            main.getLogger().severe("Failed to update playtime for player " + playerId + ": " + e.getMessage());
        }

        playerLoginTimes.put(playerId, System.currentTimeMillis());
    }


    private void resetDaily(){

        playerLoginTimes.clear();

        closeConnection();

        // Step 1: Delete the database file
        File dbFile = new File(main.getDataFolder(), "DailyPlayTime.db");
        if (dbFile.exists()) {
            if (dbFile.delete()) {
                main.getLogger().info("Database file deleted successfully.");
            } else {
                main.getLogger().severe("Failed to delete the database file.");
                return;  // Abort if the file deletion fails
            }
        }

        // Step 2: Recreate the database and table
        try {
            openConnection();  // Reopen the connection (will create a new database file)
            createTable();     // Recreate the table structure
            loadExecutionStatus();
        } catch (SQLException e) {
            main.getLogger().severe("Failed to recreate the database and table: " + e.getMessage());
            e.printStackTrace();
            return;
        }

        // Step 3: Re-add players who are currently online
        String insertPlayerDataSQL = "INSERT OR REPLACE INTO player_data (uuid, date, playtime_ticks, time_frame_index, executed) VALUES (?, ?, ?, ?, ?)";

        for (Player player : Bukkit.getOnlinePlayers()) {
            UUID playerId = player.getUniqueId();

            playerLoginTimes.put(player.getUniqueId(), System.currentTimeMillis());

            try (PreparedStatement insertPstmt = connection.prepareStatement(insertPlayerDataSQL)) {
                insertPstmt.setString(1, playerId.toString());
                insertPstmt.setDate(2, new java.sql.Date(System.currentTimeMillis())); // Store current date
                insertPstmt.setLong(3, getPlayerTime(player));  // Get the player's current session playtime in ticks
                insertPstmt.setInt(4, 0);   // Reset time_frame_index to 0
                insertPstmt.setBoolean(5, false);  // Set executed to false
                insertPstmt.executeUpdate();
            } catch (SQLException e) {
                main.getLogger().severe("Failed to re-add player " + playerId + ": " + e.getMessage());
                e.printStackTrace();
            }
        }

        List<Main.DailyPlayTimeFrames> dailyPlayTimeFrames = main.getDailyPlayTimeFrames();

        for (Main.DailyPlayTimeFrames timeFrames : dailyPlayTimeFrames) {
            if(!dailyPlayTimeFrames.isEmpty())
            {
                timeFrames.Reset();
            }
        }

        main.getLogger().info("Database reset complete and online players have been re-added.");
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
        int ticks = (int) getPlaytimeTicksFromDatabase(p.getUniqueId());
        int seconds = ticks / 20;
        int minutes = seconds / 60;
        int hours = minutes / 60;
        int days = hours / 24;
        int years = days / 365;

        days = days % 365;
        int months = days / 30;
        days = days % 30;
        int weeks = days / 7;
        days = days % 7;

        timeFormats = main.getSettingsConfig().getStringList("Time-Checker.Time-Formatter");
        List<String> formats = getTimeFormats();
        StringBuilder timeString = new StringBuilder();

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

        return Main.color(timeString.toString().trim());
    }
}
