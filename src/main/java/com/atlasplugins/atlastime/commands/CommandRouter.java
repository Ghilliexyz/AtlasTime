package com.atlasplugins.atlastime.commands;

import com.atlasplugins.atlastime.Main;
import com.atlasplugins.atlastime.commands.user.HelpCommand;
import com.atlasplugins.atlastime.commands.user.ReloadCommand;
import com.atlasplugins.atlastime.commands.user.TimeCommand;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public class CommandRouter implements CommandExecutor, TabCompleter {

    private final Map<String, AbstractCommand> commands;

    private final Main main;
    public CommandRouter(Main plugin) {
        this.main = plugin;
        this.commands = new HashMap<>();
        registerCommands();
    }

    private void registerCommands() {
        // Register your commands here
        registerCommand(new HelpCommand(main));
        registerCommand(new ReloadCommand(main));
        registerCommand(new TimeCommand(main));
    }

    private void registerCommand(AbstractCommand command) {
        for (String label : command.getLabels()) {
            commands.put(label.toLowerCase(Locale.ROOT), command);
        }
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length == 0) {
            commands.get("help").execute(main, sender, "", Collections.emptyList());
            return true;
        }

        String search = args[0].toLowerCase(Locale.ROOT);
        AbstractCommand target = commands.get(search);
        if (target == null) {
            // Send unknownCommand Message in chat when called.
            for (String unknownCommand : main.getSettingsConfig().getStringList("Command-Messages.Command-Messages-UnknownCommand")) {
                String withPAPISet = main.setPlaceholders((Player) sender, unknownCommand);
                sender.sendMessage(Main.color(withPAPISet));
            }
            return true;
        }

        if (!(sender instanceof Player)) {
            // Send NotAPlayer Message in chat when called.
            for (String NotAPlayerMessage : main.getSettingsConfig().getStringList("Command-Messages.Command-Messages-NotAPlayer")) {
//                String withPAPISet = main.setPlaceholders(((OfflinePlayer) sender).getPlayer(), NotAPlayerMessage);
                sender.sendMessage(Main.color(NotAPlayerMessage));
            }
            return true;
        }

        String permission = target.getPermission();
        if (permission != null && !permission.isEmpty()) {
            // Check if the sender does not have the permission and is not an operator
            if (!sender.hasPermission(permission) && !sender.isOp()) {
                // Send noPermission Message in chat when called.
                for (String noPermission : main.getSettingsConfig().getStringList("Command-Messages.Command-Messages-NoPermissions")) {
                    String withPAPISet = main.setPlaceholders((Player) sender, noPermission);
                    sender.sendMessage(Main.color(withPAPISet));
                }
                return true;
            }
        }

        target.execute(main, sender, search, Arrays.asList(Arrays.copyOfRange(args, 1, args.length)));
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        List<String> suggestions = new ArrayList<>();
        if (args.length > 1) {
            AbstractCommand target = commands.get(args[0].toLowerCase(Locale.ROOT));
            if (target != null) {
                target.complete(main, sender, args[0].toLowerCase(Locale.ROOT), Arrays.asList(Arrays.copyOfRange(args, 1, args.length)), suggestions);
            }
            return suggestions;
        }

        commands.keySet().stream()
                .filter(cmd -> cmd.startsWith(args[0].toLowerCase(Locale.ROOT)))
                .forEach(suggestions::add);

        return suggestions;
    }
}