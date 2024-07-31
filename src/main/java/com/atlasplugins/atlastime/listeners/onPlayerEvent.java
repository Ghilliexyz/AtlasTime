package com.atlasplugins.atlastime.listeners;

import com.atlasplugins.atlastime.Main;
import com.atlasplugins.atlastime.tracker.PlayerDailyPlayTimeTracker;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class onPlayerEvent implements Listener {

    private Main main;
    private final PlayerDailyPlayTimeTracker tracker;
    public onPlayerEvent(Main main, PlayerDailyPlayTimeTracker tracker) {
        this.main = main;
        this.tracker = tracker;
    }


    @EventHandler
    private void onPlayerJoin(PlayerJoinEvent e) {
        tracker.handlePlayerLogin(e.getPlayer());

        main.getPlayerTotalPlayTimeTracker().checkAllPlayersPlaytime();
        main.getPlayerDailyPlayTimeTracker().checkAllPlayersPlaytime();
    }

    @EventHandler
    private void onPlayerQuit(PlayerQuitEvent e) {
        tracker.handlePlayerLogout(e.getPlayer());
    }
}
