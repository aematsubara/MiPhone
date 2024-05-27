package me.matsubara.miphone.file;

import com.google.common.collect.Lists;
import lombok.Getter;
import lombok.Setter;
import me.matsubara.miphone.MiPhonePlugin;
import me.matsubara.miphone.util.PluginUtils;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.function.UnaryOperator;

@Getter
public class Messages {

    private final MiPhonePlugin plugin;
    private @Setter FileConfiguration configuration;

    private static final String CUSTOM_APPS_SECTION = "custom-apps";

    public Messages(@NotNull MiPhonePlugin plugin) {
        this.plugin = plugin;
        this.plugin.saveResource("messages.yml");
    }

    public void send(CommandSender sender, Message message) {
        send(sender, message, null);
    }

    public void send(CommandSender sender, @NotNull Message message, @Nullable UnaryOperator<String> operator) {
        for (String line : getMessages(message.getPath())) {
            if (!line.isEmpty()) sender.sendMessage(operator != null ? operator.apply(line) : line);
        }
    }

    @SuppressWarnings("unchecked")
    private List<String> getMessages(String path) {
        if (!configuration.contains(path, true)) return Collections.emptyList();

        List<String> messages;

        Object object = configuration.get(path);
        if (object instanceof String string) {
            messages = Lists.newArrayList(string);
        } else if (object instanceof List<?> list) {
            try {
                messages = Lists.newArrayList((List<String>) list);
            } catch (ClassCastException exception) {
                return Collections.emptyList();
            }
        } else return Collections.emptyList();

        return PluginUtils.translate(messages);
    }

    @Getter
    public enum Message {
        RELOADING,
        RELOADED,
        NO_PERMISSION,
        INVALID_COMMAND,
        UNKNOWN_PLAYER,
        ONLY_FROM_PLAYER,
        DEFAULT_COLOR_IF_COLOR_LIST_IS_EMPTY,
        COLOR_LIST_IS_EMPTY,
        RANDOM_COLOR_IF_COLOR_DOES_NOT_EXISTS,
        COLOR_DOES_NOT_EXISTS,
        NO_COLOR_SPECIFIED,
        UNKNOWN_ERROR,
        EMPTY_BATTERY,
        BROKEN,
        ALREADY_CHARGING,
        NOT_YOUR_PHONE,
        CRACKED,
        CHARGER_AT_TOP,
        ONLY_ON_CHARGERS,
        CHARGER_IN_USE,
        PHONE_ALREADY_CHARGING,
        NOT_HOLDING_PHONE(CUSTOM_APPS_SECTION, "not-holding-phone"),
        UNKNOWN_APP(CUSTOM_APPS_SECTION, "unknown-app"),
        DEFAULT_APP(CUSTOM_APPS_SECTION, "default-app"),
        APP_ALREADY_EXISTS(CUSTOM_APPS_SECTION, "app-already-exists"),
        APP_ICON_NOT_FOUND(CUSTOM_APPS_SECTION, "app-icon-not-found"),
        APP_INVALID_COMMAND(CUSTOM_APPS_SECTION, "app-invalid-command"),
        OUT_OF_SPACE(CUSTOM_APPS_SECTION, "out-of-space"),
        APP_ADDED(CUSTOM_APPS_SECTION, "app-added"),
        APP_DELETED(CUSTOM_APPS_SECTION, "app-deleted");

        private final String path;

        Message(String section, String path) {
            this(section + "." + path);
        }

        Message(String path) {
            this.path = path;
        }

        Message() {
            this.path = name().toLowerCase().replace("_", "-");
        }
    }
}