package me.matsubara.miphone.phone;

import lombok.Getter;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

@Getter
public enum Settings {
    FINGERPRINT_LOCK(SettingType.TOGGLE, true),
    SHOW_IMAGE_NAME_IN_GALLERY(SettingType.TOGGLE, false),
    SOUND(SettingType.TOGGLE, true),
    CHANGE_BACKGROUND,
    STORAGE,
    POWER_OFF;

    private final SettingType type;
    private final SettingValue defaultValue;

    Settings() {
        this(null);
    }

    Settings(Object defaultValue) {
        this(SettingType.OTHER, defaultValue);
    }

    Settings(SettingType type, Object defaultValue) {
        this.type = type;
        this.defaultValue = new SettingValue(defaultValue);
    }

    public @NotNull String toConfigPath() {
        return name().toLowerCase().replace("_", "-");
    }

    @Contract("_ -> new")
    public @NotNull SettingValue getOrDefault(@NotNull Map<Settings, Object> values) {
        return new SettingValue(values.getOrDefault(this, defaultValue.value()));
    }

    public record SettingValue(Object value) {

        public boolean asBoolean() {
            return (boolean) value;
        }
    }

    public enum SettingType {
        TOGGLE,
        OTHER
    }
}