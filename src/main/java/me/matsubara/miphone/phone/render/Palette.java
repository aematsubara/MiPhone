package me.matsubara.miphone.phone.render;

import lombok.Getter;
import org.bukkit.map.MapPalette;

import java.awt.*;

@SuppressWarnings("deprecation")
@Getter
public enum Palette {
    LIGHT_GREEN(MapPalette.LIGHT_GREEN),
    LIGHT_BROWN(MapPalette.LIGHT_BROWN),
    GRAY_1(MapPalette.GRAY_1),
    RED(MapPalette.RED),
    PALE_BLUE(MapPalette.PALE_BLUE),
    GRAY_2(MapPalette.GRAY_2),
    DARK_GREEN(MapPalette.DARK_GREEN),
    WHITE(MapPalette.WHITE),
    LIGHT_GRAY(MapPalette.LIGHT_GRAY),
    BROWN(MapPalette.BROWN),
    DARK_GRAY(MapPalette.DARK_GRAY),
    BLUE(MapPalette.BLUE),
    DARK_BROWN(MapPalette.DARK_BROWN),
    YELLOW(Color.YELLOW),
    BLACK(Color.BLACK),
    ORANGE(new Color(208, 128, 34));

    private final byte data;
    private final Color javaColor;
    private final String toString;

    Palette(Color color) {
        this(MapPalette.matchColor(color));
    }

    Palette(byte data) {
        this.data = data;
        this.javaColor = MapPalette.getColor(data);
        this.toString = "§" + data + ";";
    }

    @Override
    public String toString() {
        return toString;
    }
}