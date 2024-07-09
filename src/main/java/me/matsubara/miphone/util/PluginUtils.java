package me.matsubara.miphone.util;

import com.cryptomorin.xseries.reflection.XReflection;
import com.google.common.base.Preconditions;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import me.matsubara.miphone.phone.render.Coord;
import net.md_5.bungee.api.ChatColor;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.RandomUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Validate;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.map.MapPalette;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.image.LookupOp;
import java.awt.image.LookupTable;
import java.io.File;
import java.io.IOException;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.text.DecimalFormat;
import java.text.ParseException;
import java.util.List;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@SuppressWarnings("unused")
public class PluginUtils {

    private static final Pattern PATTERN = Pattern.compile("&(#[\\da-fA-F]{6})");
    public static final DecimalFormat DECIMAL_FORMAT = new DecimalFormat("#,##0.#");
    private static final String[] SIZE_UNITS = new String[]{"B", "KB", "MB", "GB", "TB", "PB", "EB", "ZB", "YB"};
    private static final Map<String, org.bukkit.Color> COLORS_BY_NAME = new HashMap<>();
    private static final org.bukkit.Color[] COLORS;
    public static final Color[] DAY_COLOR;

    private static final MethodHandle SET_PROFILE;
    private static final MethodHandle PROFILE;

    private static final MethodHandles.Lookup LOOKUP = MethodHandles.lookup();

    static {
        for (Field field : org.bukkit.Color.class.getDeclaredFields()) {
            if (!field.getType().equals(org.bukkit.Color.class)) continue;

            try {
                COLORS_BY_NAME.put(field.getName(), (org.bukkit.Color) field.get(null));
            } catch (IllegalAccessException ignored) {
            }
        }

        COLORS = COLORS_BY_NAME.values().toArray(new org.bukkit.Color[0]);

        DAY_COLOR = new Color[24];
        for (int i = 0; i < 24; i++) {
            DAY_COLOR[i] = getInterpolatedColor(i * 1000);
        }

        @SuppressWarnings("deprecation") Class<?> craftMetaSkull = XReflection.getCraftClass("inventory.CraftMetaSkull");
        Preconditions.checkNotNull(craftMetaSkull);

        SET_PROFILE = getMethod(craftMetaSkull, "setProfile", GameProfile.class);
        PROFILE = getFieldSetter(craftMetaSkull, "profile");
    }

    public static MethodHandle getFieldGetter(Class<?> clazz, String name) {
        return getField(clazz, name, true);
    }

    public static MethodHandle getFieldSetter(Class<?> clazz, String name) {
        return getField(clazz, name, false);
    }

    public static @Nullable MethodHandle getField(@NotNull Class<?> clazz, String name, boolean isGetter) {
        try {
            Field field = clazz.getDeclaredField(name);
            field.setAccessible(true);

            if (isGetter) return LOOKUP.unreflectGetter(field);
            return LOOKUP.unreflectSetter(field);
        } catch (ReflectiveOperationException exception) {
            exception.printStackTrace();
            return null;
        }
    }

    public static @Nullable MethodHandle getConstructor(@NotNull Class<?> clazz, Class<?>... parameterTypes) {
        try {
            Constructor<?> constructor = clazz.getDeclaredConstructor(parameterTypes);
            constructor.setAccessible(true);

            return LOOKUP.unreflectConstructor(constructor);
        } catch (ReflectiveOperationException exception) {
            exception.printStackTrace();
            return null;
        }
    }

    public static @Nullable MethodHandle getMethod(@NotNull Class<?> refc, String name, Class<?> parameterTypes) {
        MethodHandles.Lookup LOOKUP = MethodHandles.lookup();
        try {
            Method method = refc.getDeclaredMethod(name, parameterTypes);
            method.setAccessible(true);
            return LOOKUP.unreflect(method);
        } catch (ReflectiveOperationException exception) {
            exception.printStackTrace();
            return null;
        }
    }

    public static void applySkin(SkullMeta meta, String texture, boolean isUrl) {
        applySkin(meta, UUID.randomUUID(), texture, isUrl);
    }

    public static void applySkin(SkullMeta meta, UUID uuid, String texture, boolean isUrl) {
        GameProfile profile = new GameProfile(uuid, null);

        String textureValue = texture;
        if (isUrl) {
            textureValue = "http://textures.minecraft.net/texture/" + textureValue;
            byte[] encodedData = Base64.getEncoder().encode(String.format("{textures:{SKIN:{url:\"%s\"}}}", textureValue).getBytes());
            textureValue = new String(encodedData);
        }

        profile.getProperties().put("textures", new Property("textures", textureValue));

        try {
            // If the serialized profile field isn't set, ItemStack#isSimilar() and ItemStack#equals() throw an error.
            (SET_PROFILE == null ? PROFILE : SET_PROFILE).invoke(meta, profile);
        } catch (Throwable throwable) {
            throwable.printStackTrace();
        }
    }

    public static @NotNull String translate(String message) {
        Preconditions.checkArgument(message != null, "Message can't be null.");

        Matcher matcher = PATTERN.matcher(ChatColor.translateAlternateColorCodes('&', message));
        StringBuilder buffer = new StringBuilder();

        while (matcher.find()) {
            matcher.appendReplacement(buffer, ChatColor.of(matcher.group(1)).toString());
        }

        return matcher.appendTail(buffer).toString();
    }

    public static @NotNull List<String> translate(@NotNull List<String> messages) {
        messages.replaceAll(PluginUtils::translate);
        return messages;
    }

    public static String[] splitData(String string) {
        String[] split = StringUtils.split(StringUtils.deleteWhitespace(string), ',');
        if (split.length == 0) split = StringUtils.split(string, ' ');
        return split;
    }

    public static <T extends Enum<T>> T getRandomFromEnum(@NotNull Class<T> clazz) {
        T[] constants = clazz.getEnumConstants();
        return constants[RandomUtils.nextInt(0, constants.length)];
    }

    public static <T extends Enum<T>> T getOrEitherRandomOrNull(Class<T> clazz, @NotNull String name) {
        if (name.equalsIgnoreCase("$RANDOM")) return getRandomFromEnum(clazz);
        return getOrNull(clazz, name);
    }

    public static <T extends Enum<T>> T getOrNull(Class<T> clazz, String name) {
        return getOrDefault(clazz, name, null);
    }

    public static <T extends Enum<T>> T getOrDefault(Class<T> clazz, String name, T defaultValue) {
        try {
            return Enum.valueOf(clazz, name);
        } catch (IllegalArgumentException exception) {
            return defaultValue;
        }
    }

    public static int getRangedAmount(@NotNull String string) {
        String[] data = string.split("-");
        if (data.length == 1) {
            try {
                return Integer.parseInt(data[0]);
            } catch (IllegalArgumentException ignored) {
            }
        } else if (data.length == 2) {
            try {
                int min = Integer.parseInt(data[0]);
                int max = Integer.parseInt(data[1]);
                return RandomUtils.nextInt(min, max + 1);
            } catch (IllegalArgumentException ignored) {
            }
        }
        return 1;
    }

    public static org.bukkit.Color getRandomColor() {
        return COLORS[RandomUtils.nextInt(0, COLORS.length)];
    }

    public static org.bukkit.Color getColor(@NotNull String string) {
        if (string.equalsIgnoreCase("$RANDOM")) return getRandomColor();

        if (string.matches(PATTERN.pattern())) {
            Color temp = ChatColor.of(string.substring(1)).getColor();
            return org.bukkit.Color.fromRGB(temp.getRed(), temp.getGreen(), temp.getBlue());
        }

        return COLORS_BY_NAME.get(string);
    }

    public static @NotNull BufferedImage blurImage(@NotNull BufferedImage image, int intensity) {
        BufferedImage result = new BufferedImage(image.getWidth(), image.getHeight(), image.getType());
        int height = image.getHeight();
        int width = image.getWidth();

        for (int i = 0; i < intensity; i++) {
            for (int band = 0; band < image.getRaster().getNumBands(); band++) {
                for (int x = 0; x < width; x++) {
                    for (int y = 0; y < height; y++) {
                        int totalNeighbors = 0;
                        int newPixel = 0;

                        if ((image.getRGB(x, y) >> 24) == 0x00) {
                            result.setRGB(x, y, image.getRGB(x, y));
                            continue;
                        }

                        for (int xO = -1; xO <= 1; xO++) {
                            for (int yO = -1; yO <= 1; yO++) {
                                int neighborX = x + xO;
                                int neighborY = y + yO;

                                if (neighborX >= 0 && neighborX < width && neighborY >= 0 && neighborY < height) {
                                    if ((image.getRGB(neighborX, neighborY) >> 24) != 0x00) {
                                        newPixel += image.getRaster().getSample(neighborX, neighborY, band);
                                        totalNeighbors++;
                                    }
                                }
                            }
                        }

                        if (totalNeighbors > 0) {
                            newPixel = (int) (newPixel / (double) totalNeighbors + 0.5d);
                        }

                        result.getRaster().setSample(x, y, band, newPixel);
                    }
                }
            }
            image = result;
        }

        return result;
    }

    public static @NotNull BufferedImage createSelector(@NotNull BufferedImage image, Color color) {
        int width = image.getWidth();
        int height = image.getHeight();

        int[] pixels = new int[width * height];
        image.getRGB(0, 0, width, height, pixels, 0, width);

        int[] newPixels = new int[width * height];
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                int pixel = pixels[y * width + x];

                // Transparent, ignore.
                if ((pixel >> 24) == 0x00) {
                    continue;
                }

                // If the current pixel has color, we check of the neighbors.
                for (int xO = -1; xO <= 1; xO++) {
                    for (int yO = -1; yO <= 1; yO++) {
                        if (xO != 0 && yO != 0) continue;

                        int newX = x + xO;
                        int newY = y + yO;

                        int targetPixel = image.getRGB(newX, newY);
                        if ((targetPixel >> 24) == 0x00) {
                            newPixels[newY * width + newX] = color.getRGB();
                        }
                    }
                }
            }
        }

        BufferedImage newImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        newImage.setRGB(0, 0, width, height, newPixels, 0, width);
        return newImage;
    }

    public static @Nullable BufferedImage from128to116Pixels(File file) {
        if (file == null) return null;

        try {
            BufferedImage image = ImageIO.read(file);
            if (image == null) return null;

            // Convert 128x128 images to 116x116, so they can fit inside the canvas.

            int width = image.getWidth(), height = image.getHeight();
            if (width == 116 && height == 116) return image;

            // Resize to 128x128.
            if (width != 128 || height != 128) {
                image = MapPalette.resizeImage(image);
            }

            BufferedImage fixed = new BufferedImage(116, 116, BufferedImage.TYPE_INT_ARGB);
            int fixedWidth = fixed.getWidth(), fixedHeight = fixed.getHeight();

            int[] pixels = new int[fixedWidth * fixedHeight];
            image.getRGB(6, 6, fixedWidth, fixedHeight, pixels, 0, fixedWidth);

            fixed.setRGB(0, 0, fixedWidth, fixedHeight, pixels, 0, fixedWidth);
            return fixed;
        } catch (IOException exception) {
            exception.printStackTrace();
            return null;
        }
    }

    public static @NotNull List<Coord> generateCoords(int amount, int width, int height, int space, int max, int yOffset) {
        Validate.isTrue(width == height && width % 2 == 0 && space % 2 == 0, "Invalid arguments for coords generator!");

        int requiredSize = amount * (width + space) - space;
        while (requiredSize > max || (requiredSize + width) > max) {
            requiredSize -= (width + space);
        }

        int start = (max - requiredSize) / 2;

        Set<Integer> startPoints = new LinkedHashSet<>();
        for (int i = 0; i < requiredSize / width; i++) {
            startPoints.add(start);
            int temp = start;
            start = start + width + space;
            if (start > max) start = temp;
        }

        List<Coord> coords = new ArrayList<>();
        int requiredIt = startPoints.size();

        List<Integer> temp = new ArrayList<>(startPoints);
        for (int i = 0; i < requiredIt; i++) {
            for (int j = 0; j < requiredIt; j++) {
                Integer x = temp.get(i);
                Integer y = temp.get(j);
                coords.add(new Coord(x, y + yOffset));
            }
        }

        coords.sort(Comparator.comparingInt(Coord::y));
        return coords;
    }

    public static @NotNull String toReadableFileSize(long size) {
        if (size <= 0) return "0" + SIZE_UNITS[0];
        int digitGroups = (int) (Math.log10(size) / Math.log10(1024));
        return DECIMAL_FORMAT.format(size / Math.pow(1024, digitGroups)) + SIZE_UNITS[digitGroups];
    }

    public static long readableToFileSize(@NotNull String size, long defaultValue) {
        String unit = size.replaceAll("[^A-Za-z]", "");
        String unformatted = size.replaceAll("[^0-9,.]", "");

        int indexOfUnit = ArrayUtils.indexOf(SIZE_UNITS, unit);
        long temp = (long) Math.pow(1024, indexOfUnit);

        try {
            long finalSize = (temp * DECIMAL_FORMAT.parse(unformatted).longValue());
            return finalSize < 0 ? finalSize - 1 : finalSize;
        } catch (ParseException exception) {
            return defaultValue;
        }
    }

    public static BufferedImage replaceImageColors(BufferedImage image, Color from, Color to) {
        return new LookupOp(new LookupTable(0, 4) {
            @Override
            public int[] lookupPixel(int[] src, int[] dest) {
                if (dest == null) {
                    dest = new int[src.length];
                }

                int[] newColor = (Arrays.equals(src, colorToArray(from)) ? colorToArray(to) : src);
                System.arraycopy(newColor, 0, dest, 0, newColor.length);

                return dest;
            }

            @Contract("_ -> new")
            private int @NotNull [] colorToArray(@NotNull Color color) {
                return new int[]{color.getRed(), color.getGreen(), color.getBlue(), color.getAlpha()};
            }
        }, null).filter(image, null);
    }

    @Contract(pure = true)
    public static boolean sameImageCollections(@NotNull Collection<List<BufferedImage>> first, Collection<List<BufferedImage>> second) {
        for (List<BufferedImage> firstImages : first) {
            boolean found = false;
            for (List<BufferedImage> secondImages : second) {
                if (sameImages(firstImages, secondImages)) {
                    found = true;
                    break;
                }
            }
            if (!found) return false;
        }
        return true;
    }

    @Contract(pure = true)
    public static boolean sameImages(@NotNull List<BufferedImage> first, List<BufferedImage> second) {
        for (BufferedImage firstImage : first) {
            boolean found = false;
            for (BufferedImage secondImage : second) {
                if (sameImage(firstImage, secondImage)) {
                    found = true;
                    break;
                }
            }
            if (!found) return false;
        }
        return true;
    }

    @Contract(pure = true)
    public static boolean sameKeys(@NotNull Set<String> first, Set<String> second) {
        for (String firstKey : first) {
            boolean found = false;
            for (String secondKey : second) {
                if (firstKey.equals(secondKey)) {
                    found = true;
                    break;
                }
            }
            if (!found) return false;
        }
        return true;
    }

    public static boolean sameImage(@NotNull BufferedImage first, @NotNull BufferedImage second) {
        if (first.getWidth() != second.getWidth() || first.getHeight() != second.getHeight()) return false;

        for (int x = 0; x < first.getWidth(); x++) {
            for (int y = 0; y < first.getHeight(); y++) {
                if (first.getRGB(x, y) != second.getRGB(x, y)) return false;
            }
        }

        return true;
    }

    public static boolean sameGalleryPictures(@NotNull Map<String, List<BufferedImage>> first, @NotNull Map<String, List<BufferedImage>> second) {
        return sameKeys(first.keySet(), second.keySet()) && sameKeys(second.keySet(), first.keySet()) && sameImageCollections(first.values(), second.values());
    }

    public static @Nullable Color getColorFromString(@NotNull String string) {
        if (string.matches(PATTERN.pattern())) {
            Color temp = ChatColor.of(string.substring(1)).getColor();
            if (temp != null) return temp;
        }

        String[] data = PluginUtils.splitData(string);
        if (data.length != 3) return null;

        return new Color(
                getValidColorFromString(data[0]),
                getValidColorFromString(data[1]),
                getValidColorFromString(data[2]));
    }

    private static int getValidColorFromString(String string) {
        return Math.min(Integer.parseInt(string), 255);
    }

    public static @NotNull Color getInterpolatedColor(int ticks) {
        Color dawn = new Color(59, 102, 150);
        Color noon = new Color(61, 138, 224);
        Color dusk = new Color(30, 63, 98);
        Color midnight = new Color(13, 26, 38);
        return lerpColor(dawn, noon, dusk, midnight, (double) ticks / 24000.0d);
    }

    @Contract("_, _, _, _, _ -> new")
    public static @NotNull Color lerpColor(@NotNull Color first, @NotNull Color second, @NotNull Color third, @NotNull Color fourth, double fractionOfDay) {
        int fRed = first.getRed(), fGreen = first.getGreen(), fBlue = first.getBlue();
        int sRed = second.getRed(), sGreen = second.getGreen(), sBlue = second.getBlue();
        int tRed = third.getRed(), tGreen = third.getGreen(), tBlue = third.getBlue();
        int foRed = fourth.getRed(), foGreen = fourth.getGreen(), foBlue = fourth.getBlue();

        if (fractionOfDay < 0.25d) {
            double normalized = fractionOfDay * 4.0d;
            int red = (int) (fRed + (sRed - fRed) * normalized);
            int green = (int) (fGreen + (sGreen - fGreen) * normalized);
            int blue = (int) (fBlue + (sBlue - fBlue) * normalized);
            return new Color(red, green, blue);
        }

        if (fractionOfDay < 0.5d) {
            double normalizedT = (fractionOfDay - 0.25d) * 4.0d;
            int red = (int) (sRed + (tRed - sRed) * normalizedT);
            int green = (int) (sGreen + (tGreen - sGreen) * normalizedT);
            int blue = (int) (sBlue + (tBlue - sBlue) * normalizedT);
            return new Color(red, green, blue);
        }

        if (fractionOfDay < 0.75d) {
            double normalizedT = (fractionOfDay - 0.5d) * 4.0d;
            int red = (int) (tRed + (foRed - tRed) * normalizedT);
            int green = (int) (tGreen + (foGreen - tGreen) * normalizedT);
            int blue = (int) (tBlue + (foBlue - tBlue) * normalizedT);
            return new Color(red, green, blue);
        }

        double normalized = (fractionOfDay - 0.75d) * 4.0d;
        int red = (int) (foRed + (fRed - foRed) * normalized);
        int green = (int) (foGreen + (fGreen - foGreen) * normalized);
        int blue = (int) (foBlue + (fBlue - foBlue) * normalized);
        return new Color(red, green, blue);
    }
}