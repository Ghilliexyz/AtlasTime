package com.atlasplugins.atlastime.commands.admin;

import com.atlasplugins.atlastime.Main;
import com.atlasplugins.atlastime.commands.AbstractCommand;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Collections;
import java.util.List;

public class ResetDaily extends AbstractCommand{

    private Main main;
    public ResetDaily(Main main) {this.main = main;}

    @Override
    public void execute(JavaPlugin plugin, CommandSender sender, String label, List<String> args) {
        // if all checks, check out then move on to the command

        main.getPlayerDailyPlayTimeTracker().resetDailyPlayTime();
    }

    @Override
    public void complete(JavaPlugin plugin, CommandSender sender, String label, List<String> args, List<String> completions) {

    }

    @Override
    public List<String> getLabels() {
        return Collections.singletonList("resetdaily");
    }

    @Override
    public String getPermission() {
        return "atlastime.resetdaily";  // permission required for help command
    }
}

