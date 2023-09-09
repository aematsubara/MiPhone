package me.matsubara.miphone.phone;

import me.matsubara.miphone.phone.render.Coord;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

public record Crack(int which, Coord coord) {

    @Contract(pure = true)
    @Override
    public @NotNull String toString() {
        return which + ", " + coord;
    }
}