package com.atlasplugins.atlastime.listeners;

import com.atlasplugins.atlastime.Main;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

public class onPlayerEvent implements Listener {

    private Main main;
    public onPlayerEvent(Main main) {this.main = main;}

    @EventHandler
    private void onPlayerJoin(PlayerJoinEvent e) {
        main.getPlayerTimeTracker().checkAllPlayersPlaytime();
    }
}
