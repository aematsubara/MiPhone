package me.matsubara.miphone.command;

import me.matsubara.miphone.MiPhonePlugin;
import me.matsubara.miphone.file.Config;
import me.matsubara.miphone.file.Messages;
import me.matsubara.miphone.util.PluginUtils;
import org.apache.commons.lang3.RandomUtils;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

public record MainCommand(MiPhonePlugin plugin) implements CommandExecutor, TabCompleter {

    private static final List<String> COMMAND_ARGS = List.of("give-phone", "give-wireless-charger", "reload");

    private static final List<String> HELP = Stream.of(
            "&8----------------------------------------",
            "&6&lMiPhone &f&oCommands &c(optional) <required>",
            "&e/mip give-phone (color) (player) &f- &7Gives a new phone.",
            "&e/mip give-wireless-charger (player) &f- &7Gives a wireless charger.",
            "&e/mip reload &f- &7Reload config files.",
            "&8----------------------------------------").map(PluginUtils::translate).toList();

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (notAllowed(sender, "miphone.help")) return true;

        Messages messages = plugin.getMessages();

        if (!(sender instanceof Player player)) {
            messages.send(sender, Messages.Message.ONLY_FROM_PLAYER);
            return true;
        }

        boolean noArgs = args.length == 0;
        if (noArgs || args.length > 3 || !COMMAND_ARGS.contains(args[0].toLowerCase())) {
            if (noArgs) HELP.forEach(sender::sendMessage);
            else messages.send(sender, Messages.Message.INVALID_COMMAND);
            return true;
        }

        if (getItemCommand(sender, args, "give-wireless-charger", plugin.getWirelessCharger().getResult())) return true;

        if (args[0].equalsIgnoreCase("reload")) {
            if (notAllowed(sender, "miphone.reload")) return true;
            messages.send(player, Messages.Message.RELOADING);

            CompletableFuture.runAsync(plugin::updateConfigs).thenRun(() -> plugin.getServer().getScheduler().runTask(plugin, () -> {
                plugin.reloadStartAnimation();
                plugin.reloadColors();
                plugin.reloadSafeBlocks();

                plugin.getPhones().forEach(phone -> {
                    // Reload phones and update them if necessary.
                    phone.reloadConfig();
                    phone.setUpdated(false);
                });

                Bukkit.removeRecipe(plugin.getWirelessCharger().getKey());
                plugin.setWirelessCharger(plugin.createWirelessCharger());

                messages.send(player, Messages.Message.RELOADED);
            }));
            return true;
        }

        if (!args[0].equalsIgnoreCase("give-phone")) {
            messages.send(sender, Messages.Message.INVALID_COMMAND);
            return true;
        }

        if (notAllowed(sender, "miphone.give-phone".replaceFirst("-", args.length > 2 ? ".other." : "."))) return true;

        // Colors is empty, and we can't use default color.
        boolean isEmpty = plugin.getColors().isEmpty();
        if (isEmpty && !Config.DEFAULT_COLOR_IF_COLOR_LIST_IS_EMPTY.asBool()) {
            messages.send(player, Messages.Message.COLOR_LIST_IS_EMPTY);
            return true;
        }

        Color color = isEmpty ? null : getPhoneColor(player, args);
        if (!isEmpty && color == null) return true;

        if (isEmpty) messages.send(player, Messages.Message.DEFAULT_COLOR_IF_COLOR_LIST_IS_EMPTY);

        // Target will be null only of Bukkit.getPlayer() is null.
        Player target = args.length > 2 ? Bukkit.getPlayer(args[2]) : player;
        if (target == null) {
            messages.send(sender, Messages.Message.UNKNOWN_PLAYER);
            return true;
        }

        plugin.createPhone(target, color);
        return true;
    }

    private @Nullable Color getPhoneColor(Player player, String @NotNull [] args) {
        Messages messages = plugin.getMessages();

        if (args.length < 2) {
            messages.send(player, Messages.Message.NO_COLOR_SPECIFIED);
            return getRandomColorFromConfig();
        }

        Color color = plugin.getColors().get(args[1].toLowerCase());
        if (color != null) return color;

        if (Config.RANDOM_COLOR_IF_COLOR_DOES_NOT_EXISTS.asBool()) {
            messages.send(player, Messages.Message.RANDOM_COLOR_IF_COLOR_DOES_NOT_EXISTS);
            return getRandomColorFromConfig();
        }

        messages.send(player, Messages.Message.COLOR_DOES_NOT_EXISTS);
        return null;
    }

    @SuppressWarnings("SameParameterValue")
    private boolean getItemCommand(CommandSender sender, String @NotNull [] args, String itemGetter, ItemStack item) {
        if (!args[0].equalsIgnoreCase(itemGetter)) return false;

        boolean isOther = args.length > 1;

        Player target = isOther ? Bukkit.getPlayer(args[1]) : sender instanceof Player ? (Player) sender : null;
        if (notAllowed(sender, "miphone." + itemGetter
                .replaceFirst("-", isOther ? ".other." : ".")
                .replace("-", ""))) return true;

        if (target != null) target.getInventory().addItem(item);
        else plugin.getMessages().send(sender, Messages.Message.UNKNOWN_PLAYER);

        return true;
    }

    private boolean notAllowed(@NotNull CommandSender sender, String permission) {
        if (sender.hasPermission(permission)) return false;
        plugin.getMessages().send(sender, Messages.Message.NO_PERMISSION);
        return true;
    }

    private Color getRandomColorFromConfig() {
        Map<String, Color> colors = plugin.getColors();
        return colors.get(new ArrayList<>(colors.keySet()).get(RandomUtils.nextInt(0, colors.size())));
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String @NotNull [] args) {
        // Return subcommands.
        if (args.length == 1) {
            return complete(args, 0, COMMAND_ARGS);
        }

        // Return phone colors.
        if (args.length == 2 && args[0].equalsIgnoreCase("give-phone")) {
            return complete(args, 1, new ArrayList<>(plugin.getColors().keySet()));
        }

        // Return list of players.
        if ((args.length == 2 && args[0].equalsIgnoreCase("give-wireless-charger"))
                || (args.length == 3 && args[0].equalsIgnoreCase("give-phone") && plugin.getColors().containsKey(args[1]))) {
            return null;
        }

        return Collections.emptyList();
    }

    @SuppressWarnings("SameParameterValue")
    private @NotNull List<String> complete(String @NotNull [] args, int arg, List<String> complete) {
        return StringUtil.copyPartialMatches(args[arg], complete, new ArrayList<>());
    }
}