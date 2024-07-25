package com.atlasplugins.atlastime.commands;

import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

public abstract class AbstractCommand {
    public abstract void execute(JavaPlugin plugin, CommandSender sender, String label, List<String> args);
    public abstract void complete(JavaPlugin plugin, CommandSender sender, String label, List<String> args, List<String> completions);
    public abstract List<String> getLabels();
    public abstract String getPermission();
}

