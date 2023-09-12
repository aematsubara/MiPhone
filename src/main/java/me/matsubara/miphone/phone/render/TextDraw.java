package me.matsubara.miphone.phone.render;

import lombok.Getter;
import lombok.Setter;
import me.matsubara.miphone.phone.Phone;
import me.matsubara.miphone.util.PluginUtils;
import org.bukkit.map.MapCanvas;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Predicate;
import java.util.function.Supplier;

@Getter
@Setter
public final class TextDraw extends Draw {

    private final Supplier<Color> color;
    private final Supplier<String> message;
    private final Graphics2D dummyGraphics = new BufferedImage(128, 128, BufferedImage.TYPE_INT_ARGB).createGraphics();
    private Font font = new Font("", Font.PLAIN, 11);
    private int maxLength;
    private int maxWidth;
    private Color withSelector;

    private @Setter String previousMessage;
    private int startIndex;
    private int endIndex;
    private int lastDelay;

    private static final int MIDDLE_Y = (128 - 8) / 2;
    private static final Map<String, BufferedImage> CACHE = new HashMap<>();

    public TextDraw(String name, Coord coord, Predicate<Phone> drawCondition, Supplier<Color> color, Supplier<String> message) {
        this(name, coord, drawCondition, color, message, -1, -1);
    }

    public TextDraw(String name, Coord coord, Supplier<Color> color, Supplier<String> message, int maxLength, int maxWidth) {
        this(name, coord, phone -> true, color, message, maxLength, maxWidth);
    }

    public TextDraw(String name, Coord coord, Predicate<Phone> drawCondition, Supplier<Color> color, Supplier<String> message, int maxLength, int maxWidth) {
        super(name, coord, drawCondition);
        this.color = color;
        this.message = message;
        this.maxLength = maxLength;
        this.maxWidth = maxWidth;
    }

    @Override
    public boolean handleRender(Phone phone, @NotNull MapCanvas canvas) {
        if (!super.handleRender(phone, canvas)) return true;

        String message = this.message.get();
        if (message.isEmpty()) return true;

        // Init so the animation isn't delayed.
        if (!message.equals(previousMessage)) {
            previousMessage = message;
            reset();
        }

        if (requiresAnimation()) {
            if (endIndex > message.length()) {
                // Stay 1 second at the last animation display.
                if (++lastDelay == 4) reset();
                else {
                    startIndex--;
                    endIndex--;
                }
            }

            if (startIndex == 0) {
                startIndex = getPrefixLength(phone, message);
            }

            String prefix = getPrefix(phone, message);
            String currentText = prefix + message.substring(startIndex, endIndex);

            message = createAnimatedText(currentText, message.length());

            if (!phone.isShowError() || name.equals("error-message")) {
                startIndex++;
                endIndex++;
            }
        }

        Color color = this.color.get();
        boolean createBorder = !phone.isBlocked() && phone.isShowError() && !name.equals("error-message");
        String key = (createBorder ? "*" : "") + createKey(message, coord, color);

        BufferedImage image;
        if (CACHE.containsKey(key)) {
            image = CACHE.get(key);
        } else {
            image = new BufferedImage(128, 128, BufferedImage.TYPE_INT_ARGB);

            if (createBorder) {
                color = color.brighter();
            }

            Graphics2D graphics = image.createGraphics();
            graphics.setFont(font);
            graphics.setColor(color);

            int x = (getX(phone) == -1 ? (int) ((128 - getStringWidth(message)) / 2) : getX(phone)) + extraX;
            int y = (getY() == -1 ? MIDDLE_Y : getY()) + extraY;

            // We need to add an offset because the coords were tracked with the default minecraft font.
            graphics.drawString(message, x, y + 7);
            graphics.dispose();

            if (createBorder || withSelector != null) {
                graphics = image.createGraphics();
                graphics.drawImage(PluginUtils.createSelector(image, withSelector != null ? withSelector : color), 0, 0, null);
                graphics.dispose();
            }

            CACHE.put(key, image);
        }

        new ImageDraw("text", new Coord(0, 0), image).handleRender(phone, canvas);
        return true;
    }

    public TextDraw withSelector(Color withSelector) {
        this.withSelector = withSelector;
        return this;
    }

    private @NotNull String createKey(@NotNull String message, @NotNull Coord coord, @NotNull Color color) {
        int x = coord.x(), y = coord.y(), red = color.getRed(), green = color.getGreen(), blue = color.getBlue();
        return "{msg[" + message + "]coord[x=" + x + ",y=" + y + "],color[r=" + red + ",g=" + green + ",b=" + blue + "]}";
    }

    private double getStringWidth(String string) {
        dummyGraphics.setFont(font);
        return dummyGraphics.getFontMetrics().getStringBounds(string, dummyGraphics).getWidth();
    }

    public boolean requiresAnimation() {
        // Only animate if text width is really long.
        String message;
        return maxLength != -1 && maxWidth != -1 && (message = this.message.get()) != null && getStringWidth(message) >= maxWidth;
    }

    private void reset() {
        startIndex = lastDelay = 0;
        endIndex = maxLength;
    }

    public int getX(Phone phone) {
        if (!name.equals("battery")) return getX();
        return (int) (128 - getStringWidth(phone.getBatteryFormatted()) - 7);
    }

    private static int getPrefixLength(@NotNull Phone phone, @NotNull String text) {
        String currentPage = phone.getCurrentPage();
        if (currentPage.equals("music")) {
            return getFixedLength(text, "^\\d+\\.\\s.*", ". ");
        }
        return 0;
    }

    @SuppressWarnings("SameParameterValue")
    private static int getFixedLength(@NotNull String text, String regex, String indexOf) {
        if (!text.matches(regex)) return 0;
        return text.indexOf(indexOf) + indexOf.length();
    }

    private static @NotNull String getPrefix(Phone phone, String text) {
        int prefixEndIndex = getPrefixLength(phone, text);
        return text.substring(0, prefixEndIndex);
    }

    private static @NotNull String createAnimatedText(@NotNull String text, int totalWidth) {
        int visibleChars = Math.min(text.length(), totalWidth);

        StringBuilder animatedText = new StringBuilder();
        for (int i = 0; i < visibleChars; i++) {
            animatedText.append(text.charAt(i));
        }

        return animatedText.toString();
    }
}