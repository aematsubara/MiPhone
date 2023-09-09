package me.matsubara.miphone.phone.render;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

public record Coord(int x, int y) {

    @Contract("_, _ -> new")
    public @NotNull Coord offset(int x, int y) {
        return new Coord(this.x + x, this.y + y);
    }

    @Contract(pure = true)
    @Override
    public @NotNull String toString() {
        return x + ", " + y;
    }
}