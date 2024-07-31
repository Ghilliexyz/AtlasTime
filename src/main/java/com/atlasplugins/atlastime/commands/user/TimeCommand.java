package com.atlasplugins.atlastime.commands.user;

import com.atlasplugins.atlastime.Main;
import com.atlasplugins.atlastime.commands.AbstractCommand;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Collections;
import java.util.List;

public class TimeCommand extends AbstractCommand {

    private Main main;
    public TimeCommand(Main main) {this.main = main;}

    @Override
    public void execute(JavaPlugin plugin, CommandSender sender, String label, List<String> args) {
        // if all checks, check out then move on to the command

        // Send ConfigReloaded Message in chat when called.
        for (String ConfigReloadedMessage : main.getSettingsConfig().getStringList("Time-Command.Time-Command-Message")) {
            String withPAPISet = main.setPlaceholders((Player) sender, ConfigReloadedMessage);
            sender.sendMessage(Main.color(withPAPISet)
                    .replace("{playerTotalPlayTime}", String.valueOf(main.getPlayerTotalPlayTimeTracker().getPlayTime(((Player) sender).getPlayer())))
                    .replace("{playerDailyPlayTime}", String.valueOf(main.getPlayerDailyPlayTimeTracker().getPlayTime(((Player) sender).getPlayer()))));
        }
    }

    @Override
    public void complete(JavaPlugin plugin, CommandSender sender, String label, List<String> args, List<String> completions) {

    }

    @Override
    public List<String> getLabels() {
        return Collections.singletonList("time");
    }

    @Override
    public String getPermission() {
        return "atlastime.time";  // permission required for help command
    }
}
