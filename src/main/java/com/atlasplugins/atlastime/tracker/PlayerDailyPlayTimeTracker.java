package com.atlasplugins.atlastime.tracker;

import com.atlasplugins.atlastime.Main;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.Statistic;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitRunnable;

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
                "uuid TEXT PRIMARY KEY, " +
                "date DATE, " +
                "playtime_ticks INTEGER, " +
                "time_frame_index INTEGER, " +
                "executed BOOLEAN)";
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
        for (Player player : Bukkit.getOnlinePlayers()) {
            UUID playerId = player.getUniqueId();

            // Retrieve playtime ticks from the database
            long playtimeTicks = getPlaytimeTicksFromDatabase(playerId);

            // Convert playtimeTicks to seconds
            int playtimeSeconds = (int) (playtimeTicks / 20);

            String playtimeDaily = getPlayTime(player); // Your existing method to format playtime

            List<Main.DailyPlayTimeFrames> dailyPlayTimeFrames = main.getDailyPlayTimeFrames();
            int timeFrameIndex = 0;

            for (Main.DailyPlayTimeFrames timeFrames : dailyPlayTimeFrames) {
                main.getLogger().info("Checking TimeFrame " + (timeFrameIndex + 1) + " for player " + player.getName());
                main.getLogger().info("Player playtime: " + playtimeSeconds + " Seconds, Threshold: " + timeFrames.getDailyPlaytimeThreshold());
                main.getLogger().info("hasExecuted: " + timeFrames.hasExecuted(playerId));

                if (playtimeSeconds >= timeFrames.getDailyPlaytimeThreshold() && !timeFrames.hasExecuted(playerId)) {
//                    main.getLogger().info("Threshold met for TimeFrame " + (timeFrameIndex + 1) + ". Executing commands.");
//                    main.getLogger().info("Commands:" + timeFrame.getCommands());

                    for (String command : timeFrames.getCommands()) {
                        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command.replace("{playerName}", player.getName()));
                        main.getLogger().info("----------------Daily--------------------------");
                        main.getLogger().info("Commands-" + timeFrameIndex + ": " + command);
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

    private long getPlaytimeTicksFromDatabase(UUID playerId) {
        String querySQL = "SELECT playtime_ticks FROM player_data WHERE uuid = ? AND date = ?";
        long playtimeTicks = 0;

        try (PreparedStatement pstmt = connection.prepareStatement(querySQL)) {
            pstmt.setString(1, playerId.toString());
            pstmt.setDate(2, new java.sql.Date(System.currentTimeMillis())); // Use current date for daily records

            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    playtimeTicks = rs.getLong("playtime_ticks");
                }
            }
        } catch (SQLException e) {
            main.getLogger().severe("Failed to retrieve playtime for player " + playerId + ": " + e.getMessage());
            e.printStackTrace();
        }

        return playtimeTicks;
    }

    public void loadConfiguration() {
        main.dailyPlayTimeFrames = new ArrayList<>();
        FileConfiguration config = main.getDailyPlayTimeConfig();
        int timeFrameIndex = 1;

        while (config.contains("DailyPlayTime-Frames.DailyPlayTime-Frame-" + timeFrameIndex)) {
            String timeFrameKey = "DailyPlayTime-Frames.DailyPlayTime-Frame-" + timeFrameIndex;

            long weeks = config.getLong(timeFrameKey + ".DailyPlayTime-Frame-Weeks");
            long days = config.getLong(timeFrameKey + ".DailyPlayTime-Frame-Days");
            long hours = config.getLong(timeFrameKey + ".DailyPlayTime-Frame-Hours");
            long minutes = config.getLong(timeFrameKey + ".DailyPlayTime-Frame-Minutes");
            long seconds = config.getLong(timeFrameKey + ".DailyPlayTime-Frame-Seconds");

            long threshold = weeks * 604800 + days * 86400 + hours * 3600 + minutes * 60 + seconds;

            List<String> commands = config.getStringList(timeFrameKey + ".DailyPlayTime-Frame-Commands");

            Main.DailyPlayTimeFrames dailyPlayTimeFrames = new Main.DailyPlayTimeFrames(threshold, commands);
            main.dailyPlayTimeFrames.add(dailyPlayTimeFrames);

            main.getLogger().info("----------------Daily--------------------------");
            main.getLogger().info("Threshold-" + timeFrameIndex + ": " + threshold);
            main.getLogger().info("Commands-" + timeFrameIndex + ": " + commands);

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

    private void resetDailyPlayTime() {
        checkAllPlayersPlaytime();
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent e) {
        handlePlayerLogin(e.getPlayer());
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent e) {
        handlePlayerLogout(e.getPlayer());
    }

    public void handlePlayerLogin(Player player) {
        playerLoginTimes.put(player.getUniqueId(), System.currentTimeMillis());
    }

    public void handlePlayerLogout(Player player) {
        UpdatePlayerTimer(player);

        playerLoginTimes.remove(player.getUniqueId());
    }

    private void UpdatePlayerTimer(Player player)
    {
        UUID playerId = player.getUniqueId();

        // Update the player's total daily playtime in the database
        updateDailyPlaytime(playerId, getPlayerTime(player));
    }

    private long getPlayerTime(Player player) {
        UUID playerId = player.getUniqueId();
        long loginTime = playerLoginTimes.getOrDefault(playerId, System.currentTimeMillis());
        long logoutTime = System.currentTimeMillis();
        return (logoutTime - loginTime) / 50;
    }

    // Example refined error handling in updateDailyPlaytime method
    private void updateDailyPlaytime(UUID playerId, long playtimeToday) {
        long playtimeTodayTicks = playtimeToday * 20; // Convert seconds to ticks

        String updateSQL = "INSERT OR REPLACE INTO player_data (uuid, date, playtime_ticks) VALUES (?, ?, ?)";
        try (PreparedStatement pstmt = connection.prepareStatement(updateSQL)) {
            pstmt.setString(1, playerId.toString());
            pstmt.setDate(2, new java.sql.Date(System.currentTimeMillis())); // Consider using Timestamp if time precision is needed
            pstmt.setLong(3, playtimeTodayTicks);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            main.getLogger().severe("Failed to update playtime for player " + playerId + ": " + e.getMessage());
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
        int ticks = p.getStatistic(Statistic.PLAY_ONE_MINUTE);
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
        if (seconds % 60 > 0 && formats.size() > 6) {
            timeString.append(String.format(formats.get(6), seconds % 60));
        }

        return Main.color(timeString.toString().trim());
    }
}
