package com.atlasplugins.atlastime.commands.admin;

import com.atlasplugins.atlastime.Main;
import com.atlasplugins.atlastime.commands.AbstractCommand;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Collections;
import java.util.List;

public class ForceCheck extends AbstractCommand{

    private Main main;
    public ForceCheck(Main main) {this.main = main;}

    @Override
    public void execute(JavaPlugin plugin, CommandSender sender, String label, List<String> args) {
        // if all checks, check out then move on to the command

        main.getPlayerDailyPlayTimeTracker().checkAllPlayersPlaytime();
        main.getPlayerTotalPlayTimeTracker().checkAllPlayersPlaytime();

        // Send ForceCheck-Command-Message Message in chat when called.
        for (String forceCheckMessage : main.getSettingsConfig().getStringList("ForceTime-Command.ForceTime-Command-Message")) {
//            String withPAPISet = main.setPlaceholders((Player) sender, forceCheckMessage);
            sender.sendMessage(Main.color(forceCheckMessage));
        }
    }

    @Override
    public void complete(JavaPlugin plugin, CommandSender sender, String label, List<String> args, List<String> completions) {

    }

    @Override
    public List<String> getLabels() {
        return Collections.singletonList("forcecheck");
    }

    @Override
    public String getPermission() {
        return "atlastime.forcecheck";  // permission required for help command
    }
}

