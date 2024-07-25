package com.atlasplugins.atlastime.commands.user;

import com.atlasplugins.atlastime.Main;
import com.atlasplugins.atlastime.commands.AbstractCommand;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Collections;
import java.util.List;

public class HelpCommand extends AbstractCommand {

    private Main main;
    public HelpCommand(Main main) {this.main = main;}

    @Override
    public void execute(JavaPlugin plugin, CommandSender sender, String label, List<String> args) {
        // if all checks, check out then move on to the command
        sender.sendMessage(Main.color("&c&m&l--------------&f&l [&x&F&F&3&C&3&C&lA&x&F&F&5&4&3&F&lT&x&F&E&6&C&4&2&lL&x&F&E&8&4&4&5&lA&x&F&D&9&C&4&8&lS &x&F&D&B&3&4&B&lT&x&F&C&C&B&4&E&lI&x&F&C&E&3&5&1&lM&x&F&B&F&B&5&4&lE&f&l] &c&m&l---------------"));
        sender.sendMessage(Main.color(""));
        sender.sendMessage(Main.color("&c● &7Reload command: &c/atime reload"));
        sender.sendMessage(Main.color("&c● &7reloads the Atlas Time configs"));
        sender.sendMessage(Main.color(""));
        sender.sendMessage(Main.color("&c&m&l-----------------------------------------"));
    }

    @Override
    public void complete(JavaPlugin plugin, CommandSender sender, String label, List<String> args, List<String> completions) {

    }

    @Override
    public List<String> getLabels() {
        return Collections.singletonList("help");
    }

    @Override
    public String getPermission() {
        return "atlastime.help";  // permission required for help command
    }
}


