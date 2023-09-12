package me.matsubara.miphone.data;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableSet;
import me.matsubara.miphone.util.PluginUtils;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;

import java.util.HashSet;
import java.util.Set;

public enum WeatherType {
    CLEAR,
    CLOUDY,
    PARTLY_CLOUDY, // Not used ATM.
    RAINING,
    RAINING_AND_THUNDERING,
    SHINING,
    SNOWING,
    THUNDERING;  // Not used ATM.

    private static final Set<Biome> FROZEN_OCEAN = createBiomeSet("FROZEN_OCEAN", "DEEP_FROZEN_OCEAN");
    private static final Set<Biome> NO_RAIN = createBiomeSet("DESERT", "SAVANNA");
    private static final Set<Biome> SNOW_AT_120 = createBiomeSet("WINDSWEPT_GRAVELLY_HILLS", "WINDSWEPT_HILLS", "WINDSWEPT_FOREST");
    private static final Set<Biome> SNOW_AT_160 = createBiomeSet("TAIGA", "OLD_GROWTH_SPRUCE_TAIGA");
    private static final Set<Biome> SNOW_AT_200 = createBiomeSet("OLD_GROWTH_PINE_TAIGA");
    private static final Set<Biome> SNOW = createBiomeSet(
            "SNOWY_BEACH",
            "SNOWY_PLAINS",
            "SNOWY_SLOPES",
            "SNOWY_TAIGA",
            "FROZEN_PEAKS",
            "JAGGED_PEAKS",
            "STONY_PEAKS",
            "ICE_SPIKES");

    private static @NotNull @Unmodifiable Set<Biome> createBiomeSet(String @NotNull ... names) {
        // We use this for backward support.
        Set<Biome> biomes = new HashSet<>();
        for (String name : names) {
            Biome biome = PluginUtils.getOrNull(Biome.class, name);
            if (biome != null) biomes.add(biome);
        }
        return ImmutableSet.copyOf(biomes);
    }

    public static WeatherType fromLocation(@NotNull Location location) {
        World world = location.getWorld();
        Preconditions.checkNotNull(world);

        WeatherType dependingOnTime = isDay(world) ? SHINING : CLEAR;
        boolean hasStorm = world.hasStorm();

        Biome biome = world.getBiome(location);
        if (NO_RAIN.contains(biome)) {
            if (hasStorm) return CLOUDY;
            return dependingOnTime;
        }

        if (SNOW.contains(biome)) {
            if (hasStorm) return SNOWING;
            return dependingOnTime;
        }

        int blockX = location.getBlockX(), blockY = location.getBlockY(), blockZ = location.getBlockZ();
        if (FROZEN_OCEAN.contains(biome)
                && hasStorm
                && world.getTemperature(blockX, blockY, blockZ) <= 0.0d) {
            return SNOWING;
        }

        if (isSnowingAtLevel(location, SNOW_AT_120, 113.0d, 128.0d)
                || isSnowingAtLevel(location, SNOW_AT_160, 153.0d, 168.0d)
                || isSnowingAtLevel(location, SNOW_AT_200, 193.0d, 208.0d)) return SNOWING;

        if (hasStorm) {
            return world.isThundering() ? RAINING_AND_THUNDERING : RAINING;
        }

        return dependingOnTime;
    }

    @SuppressWarnings("DataFlowIssue")
    private static boolean isSnowingAtLevel(@NotNull Location location, @NotNull Set<Biome> biomes, double minY, double maxY) {
        double y = location.getY();
        World world = location.getWorld();
        return biomes.contains(world.getBiome(location)) && world.hasStorm() && y >= minY && y <= maxY;
    }

    public static boolean isDay(@NotNull World world) {
        long time = world.getTime();
        return time < 13000 || time > 23000;
    }

    public @NotNull String toConfigPath() {
        return name().toLowerCase().replace("_", "-");
    }
}