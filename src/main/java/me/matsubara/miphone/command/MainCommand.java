package me.matsubara.miphone.command;

import me.matsubara.miphone.MiPhonePlugin;
import me.matsubara.miphone.file.Config;
import me.matsubara.miphone.file.Messages;
import me.matsubara.miphone.phone.CustomApp;
import me.matsubara.miphone.phone.Phone;
import me.matsubara.miphone.phone.render.Coord;
import me.matsubara.miphone.phone.render.Draw;
import me.matsubara.miphone.phone.render.ImageDraw;
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

import javax.imageio.ImageIO;
import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Predicate;
import java.util.stream.Stream;

public record MainCommand(MiPhonePlugin plugin) implements CommandExecutor, TabCompleter {

    private static final List<String> COMMAND_ARGS = List.of("give-phone", "give-wireless-charger", "reload", "create-app", "delete-app");

    private static final List<String> HELP = Stream.of(
            "&8----------------------------------------",
            "&6&lMiPhone &f&oCommands &c(optional) <required>",
            "&e/mip give-phone (color) (player) &f- &7Gives a new phone.",
            "&e/mip give-wireless-charger (player) &f- &7Gives a wireless charger.",
            "&e/mip create-app <name> <icon> /<command> &f- &7Create a new app.",
            "&e/mip delete-app <name> &f- &7Deletes an app.",
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
        if (noArgs || (!args[0].equalsIgnoreCase("create-app") && args.length > 3) || !COMMAND_ARGS.contains(args[0].toLowerCase())) {
            if (noArgs) HELP.forEach(player::sendMessage);
            else messages.send(player, Messages.Message.INVALID_COMMAND);
            return true;
        }

        if (args[0].equalsIgnoreCase("delete-app")) {
            if (args.length < 2) {
                messages.send(player, Messages.Message.INVALID_COMMAND);
                return true;
            }

            Phone phone = getPhone(player);
            if (phone == null) return true;

            String appName = args[1];
            List<String> buttons = phone.getPageButtons("main");
            if (!buttons.contains(appName)) {
                messages.send(player, Messages.Message.UNKNOWN_APP);
                return true;
            }

            if (Phone.DEFAULT_MAIN_APPS.contains(appName)) {
                messages.send(player, Messages.Message.DEFAULT_APP);
                return true;
            }

            buttons.remove(appName);
            phone.getDraws().remove("main", phone.getDrawFromPage("main", appName));
            phone.getCustomMainApps().remove(appName);
            phone.setUpdated(false);

            // Re-order the buttons.
            for (Draw draw : phone.getDraws().get("main")) {
                String drawName = draw.getName();
                if (!buttons.contains(drawName)) continue;
                draw.setCoord(() -> phone.getMainCoords().get(buttons.indexOf(drawName)));
            }

            if ("main".equals(phone.getCurrentPage()) && appName.equals(phone.getCurrentButton())) {
                phone.setCurrentButton(buttons.get(buttons.size() - 1));
            }

            phone.saveConfig();

            messages.send(player, Messages.Message.APP_DELETED);
            return true;
        }

        if (args[0].equalsIgnoreCase("create-app")) {
            if (args.length < 4) {
                messages.send(player, Messages.Message.INVALID_COMMAND);
                return true;
            }

            Phone phone = getPhone(player);
            if (phone == null) return true;

            // Shouldn't be null.
            List<String> buttons = phone.getPageButtons("main");
            if (buttons == null) return true;

            String appName = args[1];
            if (buttons.contains(appName)) {
                messages.send(player, Messages.Message.APP_ALREADY_EXISTS);
                return true;
            }

            String[] data = args[2].split(":");
            if (data.length != 2) {
                messages.send(player, Messages.Message.APP_ICON_NOT_FOUND);
                return true;
            }

            String iconPath = "icon/standard/" + data[0] + "/" + data[1];

            InputStream app = plugin.getResource(iconPath);
            if (app == null || !plugin.getAvailableIcons().contains(data[0] + ":" + data[1])) {
                messages.send(player, Messages.Message.APP_ICON_NOT_FOUND);
                return true;
            }

            if (!args[3].startsWith("/")) {
                messages.send(player, Messages.Message.APP_INVALID_COMMAND);
                return true;
            }

            StringBuilder commandTarget = new StringBuilder();
            for (int i = 3; i < args.length; i++) {
                commandTarget.append(args[i]);
                if (i != args.length - 1) {
                    commandTarget.append(" ");
                }
            }

            if (buttons.size() >= phone.getMainCoords().size()) {
                messages.send(player, Messages.Message.OUT_OF_SPACE);
                return true;
            }

            buttons.add(appName);

            int i = buttons.indexOf(appName);

            Coord coord = phone.getMainCoords().get(i);
            String button = buttons.get(i);

            try {
                phone.addDraw("main", new ImageDraw(button, coord, Predicate.not(Phone::isBlocked), ImageIO.read(app)));
            } catch (IOException exception) {
                messages.send(player, Messages.Message.APP_ICON_NOT_FOUND);
                return true;
            }

            phone.setUpdated(false);
            phone.getCustomMainApps().put(button, new CustomApp(commandTarget.toString(), iconPath));
            phone.saveConfig();

            messages.send(player, Messages.Message.APP_ADDED, string -> string.replace("%command%", commandTarget));
            return true;
        }

        if (getItemCommand(player, args, "give-wireless-charger", plugin.getWirelessCharger().getResult())) return true;

        if (args[0].equalsIgnoreCase("reload")) {
            if (notAllowed(player, "miphone.reload")) return true;
            messages.send(player, Messages.Message.RELOADING);

            CompletableFuture.runAsync(plugin::updateConfigs).thenRun(() -> plugin.getServer().getScheduler().runTask(plugin, () -> {
                plugin.reloadStartAnimation();
                plugin.reloadColors();
                plugin.reloadSafeBlocks();
                plugin.reloadAvailableIcons();

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
            messages.send(player, Messages.Message.INVALID_COMMAND);
            return true;
        }

        if (notAllowed(player, "miphone.give-phone".replaceFirst("-", args.length > 2 ? ".other." : "."))) return true;

        // Colours are empty, and we can't use default colour.
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
            messages.send(player, Messages.Message.UNKNOWN_PLAYER);
            return true;
        }

        plugin.createPhone(target, color);
        return true;
    }

    private @Nullable Phone getPhone(@NotNull Player player) {
        Messages messages = plugin.getMessages();

        Phone phone = plugin.getPhoneData(player.getInventory().getItemInMainHand());
        if (phone == null) {
            messages.send(player, Messages.Message.NOT_HOLDING_PHONE);
            return null;
        }

        if (phone.isFingerprintEnabled() && !player.getUniqueId().equals(phone.getOwner())) {
            messages.send(player, Messages.Message.NOT_YOUR_PHONE);
            return null;
        }

        return phone;
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
        if (!(sender instanceof Player player)) return Collections.emptyList();

        // Return subcommands.
        if (args.length == 1) {
            return complete(args, 0, COMMAND_ARGS);
        }

        // Return phone colours.
        if (args.length == 2 && args[0].equalsIgnoreCase("give-phone")) {
            return complete(args, 1, new ArrayList<>(plugin.getColors().keySet()));
        }

        // Nothing.
        if (args.length == 2 && args[0].equalsIgnoreCase("create-app")) {
            return complete(args, 1, List.of("<name>"));
        }

        // Return list of players.
        if ((args.length == 2 && args[0].equalsIgnoreCase("give-wireless-charger"))
                || (args.length == 3 && args[0].equalsIgnoreCase("give-phone") && plugin.getColors().containsKey(args[1]))) {
            return null;
        }

        Phone phone = plugin.getPhoneData(player.getInventory().getItemInMainHand());
        if (phone == null) return Collections.emptyList();

        if (args.length == 2 && args[0].equalsIgnoreCase("delete-app")) {
            ArrayList<String> list = new ArrayList<>(phone.getPageButtons("main"));
            list.removeAll(Phone.DEFAULT_MAIN_APPS);
            if (list.isEmpty()) {
                list.add("<empty>");
            }
            return complete(args, 1, list);
        }

        // Return avaiable standard icons.
        if (args.length == 3
                && args[0].equalsIgnoreCase("create-app")
                && !phone.getPageButtons("main").contains(args[1])) {
            return complete(args, 2, plugin.getAvailableIcons());
        }

        // Return a simple command format.
        String[] fileData;
        if (args.length == 4
                && args[0].equalsIgnoreCase("create-app")
                && !phone.getPageButtons("main").contains(args[1])
                && (fileData = args[2].split(":")).length == 2) {
            File icon = new File(plugin.createFolderPath(plugin.getDataFolder().toString(), "icon", "standard", fileData[0]), fileData[1]);
            if (icon.exists() && icon.isFile()) {
                return complete(args, 3, List.of("/<command>"));
            }
        }

        return Collections.emptyList();
    }

    @SuppressWarnings("SameParameterValue")
    private @NotNull List<String> complete(String @NotNull [] args, int arg, List<String> complete) {
        return StringUtil.copyPartialMatches(args[arg], complete, new ArrayList<>());
    }
}