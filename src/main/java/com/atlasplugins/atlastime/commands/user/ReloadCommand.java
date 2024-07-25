package com.atlasplugins.atlastime.commands.user;

import com.atlasplugins.atlastime.Main;
import com.atlasplugins.atlastime.commands.AbstractCommand;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Collections;
import java.util.List;

public class ReloadCommand extends AbstractCommand {

    private final Main main;
    public ReloadCommand(Main main) {this.main = main;}

    @Override
    public void execute(JavaPlugin plugin, CommandSender sender, String label, List<String> args) {

        // Reload Settings config
        main.loadSettingsConfig();

        // Send ConfigReloaded Message in chat when called.
        for (String ConfigReloadedMessage : main.getSettingsConfig().getStringList("Command-Messages.Command-Messages-ReloadConfig-ConfigReloaded")) {
            String withPAPISet = main.setPlaceholders((Player) sender, ConfigReloadedMessage);
            sender.sendMessage(Main.color(withPAPISet));
        }
    }

    @Override
    public void complete(JavaPlugin plugin, CommandSender sender, String label, List<String> args, List<String> completions) {

    }

    @Override
    public List<String> getLabels() {
        return Collections.singletonList("reload");
    }

    @Override
    public String getPermission() {
        return "atlastime.reload";  // permission required for help command
    }
}

