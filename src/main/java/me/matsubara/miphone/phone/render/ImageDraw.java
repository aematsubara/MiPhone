package me.matsubara.miphone.phone.render;

import lombok.Getter;
import lombok.Setter;
import me.matsubara.miphone.phone.Phone;
import me.matsubara.miphone.util.PluginUtils;
import org.bukkit.map.MapCanvas;
import org.bukkit.map.MapPalette;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.image.WritableRaster;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Predicate;

@Getter
@Setter
public final class ImageDraw extends Draw {

    private BufferedImage image;

    private static final float BRIGHTNESS_PERCENTAGE = 0.5f;
    private static final Map<BufferedImage, BufferedImage> BLUR_CACHE = new HashMap<>();
    private static final Map<BufferedImage, BufferedImage> DARK_CACHE = new HashMap<>();

    public ImageDraw(String name, Coord coord, BufferedImage image) {
        this(name, coord, phone -> true, image);
    }

    public ImageDraw(String name, Coord coord, Predicate<Phone> drawCondition, BufferedImage image) {
        super(name, coord, drawCondition);
        this.image = image;
    }

    @SuppressWarnings("deprecation")
    @Override
    public boolean handleRender(Phone phone, @NotNull MapCanvas canvas) {
        if (image == null || !super.handleRender(phone, canvas)) return true;

        // We use a temporary image instead of the original since we don't want always the modified image.
        BufferedImage temp;
        if ((name.equals("background") && phone.isBlocked()) || blurImages(phone)) {
            if (BLUR_CACHE.containsKey(image)) {
                temp = BLUR_CACHE.get(image);
            } else {
                BLUR_CACHE.put(image, temp = PluginUtils.blurImage(image, 5));
            }
        } else {
            temp = image;
        }

        int width = temp.getWidth();
        int height = temp.getHeight();

        if (phone.isReduceBrightness() && !name.equals("layout") && !name.startsWith("crack")) {
            if (DARK_CACHE.containsKey(temp)) {
                temp = DARK_CACHE.get(temp);
            } else {
                BufferedImage darker = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
                Graphics2D graphics = darker.createGraphics();
                graphics.drawImage(temp, 0, 0, null);
                graphics.dispose();

                WritableRaster raster = darker.getRaster();
                int[] pixel = new int[4];

                for (int x = 0; x < raster.getWidth(); x++) {
                    for (int y = 0; y < raster.getHeight(); y++) {
                        raster.getPixel(x, y, pixel);
                        pixel[0] = (int) (pixel[0] * BRIGHTNESS_PERCENTAGE);
                        pixel[1] = (int) (pixel[1] * BRIGHTNESS_PERCENTAGE);
                        pixel[2] = (int) (pixel[2] * BRIGHTNESS_PERCENTAGE);
                        raster.setPixel(x, y, pixel);
                    }
                }

                DARK_CACHE.put(temp, temp = darker);
            }
        }

        int[] pixels = new int[width * height];
        temp.getRGB(0, 0, width, height, pixels, 0, width);

        Byte[] bytes = new Byte[width * height];
        for (int i = 0; i < pixels.length; i++) {
            bytes[i] = (pixels[i] >> 24) == 0x00 ? null : MapPalette.matchColor(new Color(pixels[i], true));
        }

        int tX = (getX() == -1 ? ((128 - width) / 2) : getX()) + extraX;
        int tY = (getY() == -1 ? ((128 - height) / 2) : getY()) + extraY;

        for (int x = 0; x < this.image.getWidth(); x++) {
            for (int y = 0; y < this.image.getHeight(); y++) {
                Byte color = bytes[y * this.image.getWidth() + x];
                if (color != null) canvas.setPixel(tX + x, tY + y, color);
            }
        }

        return true;
    }

    private boolean blurImages(@NotNull Phone phone) {
        return phone.isShowError() && !phone.isBlocked() && !name.equals("layout") && !name.startsWith("error") && !name.startsWith("crack");
    }
}