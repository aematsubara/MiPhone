package me.matsubara.miphone.file;

import me.matsubara.miphone.MiPhonePlugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public enum Config {
    SAFE_DROP_BLOCKS,
    SAFE_PHONE_DROP_DISTANCE,
    CRACK_CHANCE_PER_DROP_DISTANCE,
    FEW_SONGS_IN_PLAYER,
    ERROR_APP_NO_FUNCTION("error-dialog.no-function"),
    ERROR_NO_SPACE_AVAILABLE("error-dialog.no-space-available"),
    ERROR_PROCESSING_PICTURE("error-dialog.processing-picture"),
    ERROR_EMPTY_DEFAULT_BACKGROUNDS("error-dialog.empty-default-backgrounds"),
    ERROR_EMPTY_GALLERY("error-dialog.empty-gallery"),
    ERROR_EMPTY_MUSIC("error-dialog.empty-music"),
    ERROR_IMAGE_IS_ALREADY_THE_BACKGROUND("error-dialog.image-is-already-the-background"),
    DEFAULT_COLOR_IF_COLOR_LIST_IS_EMPTY,
    RANDOM_COLOR_IF_COLOR_DOES_NOT_EXISTS,
    START_ANIMATION_ENABLED("start-animation.enabled"),
    START_ANIMATION_WORD("start-animation.word"),
    START_ANIMATION_COUNT("start-animation.count"),
    DATE_FORMAT_FULL("date-format.full"),
    DATE_FORMAT_HOUR("date-format.hour"),
    DATE_FORMAT_PICTURE("date-format.picture"),
    BATTERY_MULTIPLIER("battery.multiplier"),
    BATTERY_INCREASE_RATE("battery.increase-rate"),
    BATTERY_DECREASE_RATE("battery.decrease-rate"),
    BATTERY_EXTRA_DECREASE_PICTURE("battery.extra.decrease-picture"),
    STORAGE_MAX_CAPACITY("storage.max-capacity"),
    STORAGE_PICTURE_SIZE_MULTIPLIER("storage.picture-size-multiplier");

    private final String path;
    private final MiPhonePlugin plugin = JavaPlugin.getPlugin(MiPhonePlugin.class);

    Config() {
        this.path = name().toLowerCase().replace("_", "-");
    }

    Config(String path) {
        this.path = path;
    }

    public boolean asBool() {
        return plugin.getConfig().getBoolean(path);
    }

    public int asInt() {
        return plugin.getConfig().getInt(path);
    }

    public String asString() {
        return plugin.getConfig().getString(path);
    }

    public String asString(String defaultValue) {
        return plugin.getConfig().getString(path, defaultValue);
    }

    public double asDouble() {
        return plugin.getConfig().getDouble(path);
    }

    public long asLong() {
        return plugin.getConfig().getLong(path);
    }

    public float asFloat() {
        return (float) asDouble();
    }

    public @NotNull List<String> asStringList() {
        return plugin.getConfig().getStringList(path);
    }
}