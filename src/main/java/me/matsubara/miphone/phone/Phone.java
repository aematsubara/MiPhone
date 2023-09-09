package me.matsubara.miphone.phone;

import com.google.common.collect.*;
import com.xxmicloxx.NoteBlockAPI.model.FadeType;
import com.xxmicloxx.NoteBlockAPI.model.RepeatMode;
import com.xxmicloxx.NoteBlockAPI.songplayer.EntitySongPlayer;
import com.xxmicloxx.NoteBlockAPI.songplayer.Fade;
import com.xxmicloxx.NoteBlockAPI.utils.NBSDecoder;
import dev.jensderuiter.minecraft_imagery.Constants;
import dev.jensderuiter.minecraft_imagery.image.ImageCapture;
import dev.jensderuiter.minecraft_imagery.image.ImageCaptureOptions;
import lombok.Getter;
import lombok.Setter;
import me.matsubara.miphone.MiPhonePlugin;
import me.matsubara.miphone.file.Config;
import me.matsubara.miphone.phone.render.*;
import me.matsubara.miphone.song.CustomEntitySongPlayer;
import me.matsubara.miphone.song.SongData;
import me.matsubara.miphone.util.PluginUtils;
import me.matsubara.miphone.util.config.ConfigFileUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.text.WordUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.lang3.tuple.Triple;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.map.MapCanvas;
import org.bukkit.map.MapRenderer;
import org.bukkit.map.MapView;
import org.bukkit.map.MapView.Scale;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Getter
@Setter
public final class Phone extends MapRenderer {

    private final MiPhonePlugin plugin;
    private final Multimap<String, Draw> draws = MultimapBuilder
            .linkedHashKeys()
            .arrayListValues()
            .build();

    private UUID owner;
    private Color phoneColor;
    private int currentBackgroundIndex;
    private final List<BufferedImage> backgrounds = new ArrayList<>();

    private final List<Crack> cracks = new ArrayList<>();
    private final List<Coord> mainCoords;
    private final BufferedImage disabledBackground = createColorBackground(new Color(25, 25, 25));

    private File file;
    private FileConfiguration configuration;

    private MapView view;
    private ItemStack item;
    private EntitySongPlayer songPlayer;
    private ItemFrame chargingAt;

    private int battery = getMaxBattery();
    private int pictureTakenCount;
    private int noTouchCounter;
    private int startAnimationIndex;
    private int animationCount;
    private int lastBatterySaveAt;

    private boolean updated;
    private boolean enabled;
    private boolean blocked = true;
    private boolean takingPicture;
    private boolean built;
    private boolean showError;
    private boolean alreadyStarted;
    private boolean isBackgroundGallery;
    private boolean isBackgroundImageFromGallery;
    private boolean isNoTouchHide;
    private boolean reduceBrightness;
    private boolean galleryModified;

    private String currentPage;
    private String currentButton;
    private String currentPicture;
    private String currentSong;
    private String currentBattery;
    private String currentTime;
    private String currentDate;
    private String errorMessage;
    private String previousPage;
    private String previousButton;
    private String previousBlockedPage;
    private String previousBlockedButton;
    private String emptyMusicIcon, emptyMusicText;
    private String tempCurrentPicture;
    private String backgroundImageName;

    private Map<String, List<BufferedImage>> pictures;
    private Map<String, List<BufferedImage>> tempPictures;
    private Map<String, File> music;
    private Map<String, String> lastButtons = new HashMap<>();
    private EnumMap<Settings, Object> settingsValues = new EnumMap<>(Settings.class);

    private Triple<String, String, String> songs;
    private Triple<Settings, Settings, Settings> settings;

    private static final String DEFAULT_PAGE = "main";
    private static final Predicate<Phone> TOUCH_DRAW = temp -> (temp.getNoTouchCounter() / 4) <= 5;
    private static final Predicate<Phone> NEXT_DRAW = temp -> {
        int imageIndex = temp.getCurrentPictureIndex();
        return imageIndex != -1 && imageIndex < temp.getPictures().size() - 1;
    };

    private static final Set<String> NO_TOUCH_PAGES = ImmutableSet.of("gallery");
    private static final List<String> IGNORE_MOVE_BUTTONS = ImmutableList.of("previous", "next", "up", "down");
    private static final Map<String, List<String>> PAGE_BUTTONS = ImmutableMap.of(
            "main", ImmutableList.of(
                    "camera",
                    "gallery",
                    "settings",
                    "music"),
            "music", ImmutableList.of(
                    "up",
                    "tab-1",
                    "tab-2",
                    "tab-3",
                    "down"),
            "settings", ImmutableList.of(
                    "up",
                    "tab-1",
                    "tab-2",
                    "tab-3",
                    "down"),
            "gallery", ImmutableList.of(
                    "previous",
                    "house",
                    "trash",
                    "next"),
            "backgrounds", ImmutableList.of(
                    "previous",
                    "checkmark",
                    "next"));

    private static final List<String> BACKGROUND_GALLERY_BUTTONS = createGalleryButtonsByType(true);
    private static final List<String> GALLERY_BUTTONS = createGalleryButtonsByType(false);

    // Default apps (buttons) coordinates for main page.
    private static final int[] X_COORDINATES = {11, 39, 67, 95};
    private static final int[] Y_COORDINATES = {31, 59, 87};

    // Draw time/image counter.
    public static final Coord TOP_LEFT_CORNER = new Coord(8, 9);
    private static final Coord MID_CORNER = new Coord(-1, TOP_LEFT_CORNER.y() + 9);

    // Enabled phone (background) related.
    private static final int[] BACKGROUND_SIZE = {116, 116};
    private static final Coord BACKGROUND_COORD = new Coord(6, 6);

    // Gallery related.
    private static final Coord PREVIOUS_BUTTON_COORD = new Coord(8, 62);
    private static final Coord NEXT_BUTTON_COORD = new Coord(105, 62);
    private static final int GALLERY_Y = 98;

    // Music related.
    private static final Coord UP_MUSIC_COORD = new Coord(56, 7);
    private static final Coord DOWN_MUSIC_COORD = new Coord(56, 106);

    // Error.
    private static final Coord ERROR_COORD = new Coord(27, 37);

    private static final int TAB_BUTTONS_X = 10;
    private static final int[] TAB_BUTTONS_Y = {25, 52, 79};
    private static final int REDUCE_BRIGHTNESS_AFTER = 25;
    private static final int BLOCK_AFTER = 5;
    private static final Color BACKGROUND_COLOR_DEFAULT = Color.DARK_GRAY;
    private static final Color DEFAULT_PHONE_COLOR = new Color(39, 41, 42);
    private static final Coord FINGERPRINT_COORD = new Coord(56, 99);

    private static final Supplier<Color> GALLERY_COLOR = Palette.DARK_GRAY::getJavaColor;
    private static final Supplier<Color> MAIN_COLOR = Palette.WHITE::getJavaColor;
    private static final Supplier<Color> SETTINGS_COLOR = Palette.DARK_GRAY::getJavaColor;
    private static final Supplier<Color> PLAY_COLOR = Palette.WHITE::getJavaColor;
    private static final Supplier<Color> ERROR_COLOR = Palette.WHITE::getJavaColor;

    private static final Map<String, SongData> SONG_CACHE = new ConcurrentHashMap<>();
    private static final Map<String, BufferedImage> CRACK_CACHE = new HashMap<>();

    public Phone(@Nullable UUID owner, @NotNull MiPhonePlugin plugin, @Nullable MapView view, @Nullable Color phoneColor) throws IOException {
        ImageIO.setUseCache(false);

        this.plugin = plugin;
        this.owner = owner;
        this.view = view;
        this.mainCoords = PluginUtils.generateCoords(getPageButtons("main").size(), 24, 24, 8, 128, 0);
        this.phoneColor = phoneColor != null ? phoneColor : DEFAULT_PHONE_COLOR;

        // Set default page and button.
        setCurrentPage(DEFAULT_PAGE, getPageButtons(DEFAULT_PAGE).get(0));

        // Draw background for the main, play & settings pages.
        ImageDraw mainBackDraw = new ImageDraw("background", BACKGROUND_COORD, null);
        addDraw("main", mainBackDraw);

        initFiles();
        createDefaultBackgroundIfNeeded();

        addDraw("music", new ImageDraw("background", BACKGROUND_COORD, createColorBackground(BACKGROUND_COLOR_DEFAULT)));
        addDraw("settings", new ImageDraw("background", BACKGROUND_COORD, createColorBackground(Color.LIGHT_GRAY)));

        // Draw core parts.
        resetLayout();
        addDraw("core", new ImageDraw("disabled",
                BACKGROUND_COORD,
                Predicate.not(Phone::isEnabled).or(Predicate.not(Phone::isAlreadyStarted)),
                disabledBackground));
        addDraw("core", new TextDraw("start",
                new Coord(-1, -1),
                Phone::playStartAnimation,
                MAIN_COLOR,
                this::getStartAnimationFrame));
        addDraw("core", new TextDraw("charging",
                new Coord(-1, -1),
                Phone::isCharging,
                Palette.LIGHT_GREEN::getJavaColor,
                this::getBatteryFormatted));

        // Draw first picture frame (empty).
        addDraw("gallery", new ImageDraw("picture", BACKGROUND_COORD, null));

        // Draw gallery buttons (if possible).
        addSpecialButton("gallery", "previous", PREVIOUS_BUTTON_COORD, TOUCH_DRAW.and(temp -> temp.getCurrentPictureIndex() > 0));
        addSpecialButton("gallery", "next", NEXT_BUTTON_COORD, TOUCH_DRAW.and(NEXT_DRAW));

        // Draw play buttons (if possible).
        addSpecialButton("music", "up", UP_MUSIC_COORD, temp -> temp.getFirstSongIndex() > 0);
        addSpecialButton("music", "down", DOWN_MUSIC_COORD, temp -> {
            int lastIndex = temp.getThirdSongIndex();
            return lastIndex != -1 && lastIndex < temp.getMusic().size() - 1;
        });

        // Draw settings buttons (if possible).
        addSpecialButton("settings", "up", UP_MUSIC_COORD, temp -> temp.getFirstSettingIndex() > 0);
        addSpecialButton("settings", "down", DOWN_MUSIC_COORD, temp -> {
            int lastIndex = temp.getThirdSettingIndex();
            return lastIndex != -1 && lastIndex < Settings.values().length - 1;
        });

        // Draw gallery labels.
        addDraw("gallery", new TextDraw("counter", TOP_LEFT_CORNER, TOUCH_DRAW.and(Predicate.not(Phone::isShowError)), GALLERY_COLOR, this::getImageCounter));
        addDraw("gallery", new TextDraw("name",
                new Coord(-1, TOP_LEFT_CORNER.y()),
                TOUCH_DRAW.and(temp -> !temp.isShowError() && !temp.isBackgroundGallery() && temp.getBoolSetting(Settings.SHOW_IMAGE_NAME_IN_GALLERY)),
                GALLERY_COLOR,
                this::getCurrentPicture,
                9,
                55));

        // Add main, music & gallery pages apps.
        reloadButtons();

        // Draw main page draws.
        Predicate<Phone> ifNotErrorOrBlocked = Predicate.not(Phone::isShowError).or(Phone::isBlocked);
        addDraw("main", new TextDraw("time", TOP_LEFT_CORNER, ifNotErrorOrBlocked, MAIN_COLOR, this::getTimeFormatted));
        addDraw("main", new TextDraw("battery", new Coord(-1, TOP_LEFT_CORNER.y()), ifNotErrorOrBlocked, this::getBatteryColor, this::getBatteryFormatted));
        addDraw("main", new TextDraw("date", MID_CORNER.offset(0, 9), Phone::isBlocked, MAIN_COLOR, this::getDateFormatted));

        // Show selected button in main page.
        List<String> mainButtons = getPageButtons("main");
        Draw lastButton = getDrawFromPage("main", mainButtons.get(mainButtons.size() - 1));
        if (lastButton != null) {
            addDraw("main", new TextDraw("selected",
                    new Coord(-1, lastButton.getY() + 27),
                    TOUCH_DRAW.and(Predicate.not(Phone::isBlocked)).and(Predicate.not(Phone::isShowError)),
                    MAIN_COLOR,
                    this::getCurrentButtonFormatted));
        }

        InputStream fingerprint = plugin.getResource("icon/fingerprint.png");
        if (fingerprint != null) {
            addDraw("main", new ImageDraw("fingerprint",
                    FINGERPRINT_COORD,
                    Phone::showFingerprint,
                    PluginUtils.replaceImageColors(ImageIO.read(fingerprint), Color.BLACK, MAIN_COLOR.get())));
        }

        // Error related.
        InputStream error = plugin.getResource("error.png");
        if (error != null) {
            BufferedImage errorImage = ImageIO.read(error);
            Predicate<Phone> errorPredicate = Predicate.not(Phone::isBlocked).and(Phone::isEnabled).and(Phone::isShowError);
            addDraw("core", new ImageDraw("error", ERROR_COORD, errorPredicate, errorImage));
            addDraw("core", new ImageDraw("error-selector", ERROR_COORD, errorPredicate, PluginUtils.createSelector(errorImage, Color.WHITE)));
            addDraw("core", new TextDraw("error-message",
                    new Coord(-1, ERROR_COORD.y() + 30),
                    errorPredicate,
                    ERROR_COLOR,
                    this::getErrorMessage,
                    11,
                    31));
        }

        // Draw settings information (AFTER settings buttons).
        for (String name : getPageButtons("settings")) {
            if (IGNORE_MOVE_BUTTONS.contains(name)) continue;

            Draw button = getDrawFromPage("settings", name);
            if (button == null) continue;

            int which = Integer.parseInt(name.split("-")[1]) - 1;

            addDraw("settings", new TextDraw(name + "-setting",
                    button.getCoord().offset(7, 8),
                    () -> getSettingColor(which),
                    () -> getSettingData(which),
                    18,
                    95));
        }

        // Draw music information (AFTER music buttons).
        for (String name : getPageButtons("music")) {
            if (IGNORE_MOVE_BUTTONS.contains(name)) continue;

            Draw button = getDrawFromPage("music", name);
            if (button == null) continue;

            int which = Integer.parseInt(name.split("-")[1]) - 1;

            addDraw("music", new TextDraw(name + "-artist",
                    button.getCoord().offset(7, 4),
                    () -> getSongColor(which),
                    () -> getSongInformation(which, true),
                    16,
                    95));

            addDraw("music", new TextDraw(name + "-song",
                    button.getCoord().offset(7, 13),
                    () -> getSongColor(which),
                    () -> getSongInformation(which, false),
                    16,
                    95));

            // Show alert dialog if music length is lower than 3.

            Coord coord = new Coord(mainCoords.get(1).x(), button.getY());

            InputStream alert = plugin.getResource("icon/flat/alert.png");
            if (alert == null) continue;

            Predicate<Phone> predicate = button.getDrawCondition().negate();
            addDraw("music", new ImageDraw(name + "-empty-icon", coord, predicate, ImageIO.read(alert)));
            addDraw("music", new TextDraw(name + "-empty-text",
                    coord.offset(-coord.x() - 1, 26),
                    predicate,
                    PLAY_COLOR,
                    Config.FEW_SONGS_IN_PLAYER::asString,
                    16,
                    95));
        }
    }

    private String getStartAnimationFrame() {
        return plugin.getStartAnimation().get(startAnimationIndex);
    }

    private boolean playStartAnimation() {
        return enabled && !alreadyStarted && Config.START_ANIMATION_ENABLED.asBool();
    }

    public boolean isCharging() {
        Phone temp;
        return chargingAt != null
                && chargingAt.isValid()
                && (temp = plugin.getPhoneData(chargingAt.getItem())) != null
                && temp.equals(this);
    }

    private Color getBatteryColor() {
        return getBatteryPercentage() <= 15 ? Palette.RED.getJavaColor() : MAIN_COLOR.get();
    }

    private void resetLayout() {
        try {
            InputStream layout = plugin.getResource("layout.png");
            if (layout == null) return;

            BufferedImage filtered = PluginUtils.replaceImageColors(ImageIO.read(layout), DEFAULT_PHONE_COLOR, phoneColor);
            if (getDrawFromPage("core", "layout") instanceof ImageDraw image) {
                image.setImage(filtered);
            } else {
                addDraw("core", new ImageDraw("layout", new Coord(0, 0), filtered));
            }
        } catch (IOException exception) {
            exception.printStackTrace();
        }
    }

    private boolean showFingerprint() {
        return blocked && isFingerprintEnabled();
    }

    public boolean isFingerprintEnabled() {
        return owner != null && getBoolSetting(Settings.FINGERPRINT_LOCK);
    }

    @Contract(pure = true)
    public @NotNull String getBatteryFormatted() {
        return getBatteryPercentage() + "%";
    }

    public int getBatteryPercentage() {
        return Math.max(1, (int) Math.ceil((double) battery / Config.BATTERY_MULTIPLIER.asInt()));
    }

    public @NotNull String getDateFormatted() {
        return LocalDateTime.now().format(DateTimeFormatter.ofPattern(Config.DATE_FORMAT_FULL.asString()));
    }

    public String getCurrentButtonFormatted() {
        if (currentButton == null || !getPageButtons("main").contains(currentButton)) return "";
        return plugin.getConfig().getString("apps." + currentButton + ".name", "");
    }

    private String getSongInformation(int which, boolean artist) {
        Pair<Integer, String> data = getSongData(which);
        if (data == null) return "";

        SongData song = SONG_CACHE.get(data.getValue());
        if (song == null) return "";

        return artist ? (data.getKey() + 1) + ". " + song.author() : song.title();
    }

    private @NotNull Color getSongColor(int which) {
        Pair<Integer, String> data = getSongData(which);
        if (data == null) return PLAY_COLOR.get();

        return data.getValue().equals(currentSong) ? Palette.LIGHT_GREEN.getJavaColor() : PLAY_COLOR.get();
    }

    private @Nullable Pair<Integer, String> getSongData(int which) {
        if (music == null || music.isEmpty() || songs == null) return null;

        ArrayList<String> musics = new ArrayList<>(music.keySet());
        if (which > musics.size() - 1) return null;

        int index = which == 0 ? getFirstSongIndex() : which == 1 ? getSecondSongIndex() : which == 2 ? getThirdSongIndex() : -1;
        if (index == -1) return null;

        return Pair.of(index, musics.get(index));
    }

    private @Nullable Settings getSetting(int which) {
        if (which > Settings.values().length - 1) return null;

        int finalWhich = which == 0 ? getFirstSettingIndex() : which == 1 ? getSecondSettingIndex() : which == 2 ? getThirdSettingIndex() : -1;
        if (finalWhich == -1) return null;

        return Settings.values()[finalWhich];
    }

    private Color getSettingColor(int which) {
        Settings setting = getSetting(which);
        if (setting != Settings.STORAGE) return SETTINGS_COLOR.get();

        double percentage = getUsedPercentage();
        return (percentage < 50.0d ? Palette.LIGHT_GREEN : percentage < 85.0d ? Palette.ORANGE : Palette.RED).getJavaColor();
    }

    @SuppressWarnings("deprecation")
    private String getSettingData(int which) {
        Settings setting = getSetting(which);
        if (setting == null) return "";

        String settingName = plugin.getConfig().getString(
                "settings-display." + setting.toConfigPath(),
                WordUtils.capitalizeFully(setting.name().replace("_", " ")));

        if (setting != Settings.STORAGE) return settingName;

        long size = getUsedSpace();
        long maxSize = getMaxSpace();

        return settingName
                .replace("%used-space%", PluginUtils.toReadableFileSize(size))
                .replace("%max-space%", PluginUtils.toReadableFileSize(maxSize))
                .replace("%used-space-percentage%", PluginUtils.DECIMAL_FORMAT.format(getUsedPercentage()));
    }

    public long getUsedSpace() {
        File gallery = new File(getGalleryFolder());
        File[] images;
        if (gallery.isDirectory() && (images = gallery.listFiles((directory, name) -> new File(directory, name).isFile() && name.endsWith(".png"))) != null) {
            return Arrays.stream(images)
                    .mapToLong(value -> value.length() * Config.STORAGE_PICTURE_SIZE_MULTIPLIER.asLong())
                    .sum();
        }
        return 0;
    }

    public boolean canTakePicture() {
        return getUsedSpace() + 1024 * Config.STORAGE_PICTURE_SIZE_MULTIPLIER.asLong() < getMaxSpace();
    }

    public long getMaxSpace() {
        // If we can't parse, max space is 256MB.
        return PluginUtils.readableToFileSize(Config.STORAGE_MAX_CAPACITY.asString(), 268435456L);
    }

    public double getUsedPercentage() {
        return ((double) getUsedSpace() / getMaxSpace()) * 100;
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    private void initFiles() {
        if (view == null) return;

        file = new File(getPhoneFolder(), "data.yml");
        if (!file.exists()) {
            try {
                file.getParentFile().mkdirs();
                file.createNewFile();
            } catch (IOException exception) {
                exception.printStackTrace();
            }
        }

        reloadConfig();
    }

    public void saveConfig() {
        try {
            configuration.set("owner", owner != null ? owner.toString() : null);
            configuration.set("battery", battery);
            configuration.set("phone-color", colorToString(phoneColor));
            // Save all booleans.
            for (Settings setting : Settings.values()) {
                if (setting.getType() != Settings.SettingType.TOGGLE) continue;
                saveSetting(setting, Settings.SettingValue::asBoolean);
            }
            configuration.set("cracks", cracks.stream().map(Crack::toString).toList());
            configuration.set("background.name", backgroundImageName);
            configuration.set("background.from", isBackgroundImageFromGallery ? "GALLERY" : "DEFAULTS");

            configuration.save(file);
        } catch (IOException exception) {
            exception.printStackTrace();
        }
    }

    private String colorToString(@NotNull Color color) {
        return "%s, %s, %s".formatted(color.getRed(), color.getGreen(), color.getBlue());
    }

    private void saveSetting(@NotNull Settings setting, @NotNull Function<Settings.SettingValue, Object> mapper) {
        configuration.set("settings." + setting.toConfigPath(), mapper.apply(setting.getOrDefault(settingsValues)));
    }

    public void reloadConfig() {
        try {
            configuration = new YamlConfiguration();
            configuration.load(file);
            update();
        } catch (IOException | InvalidConfigurationException exception) {
            exception.printStackTrace();
        }
    }

    private void update() {
        // Init basic phone data.
        String owner = configuration.getString("owner");
        if (owner != null) this.owner = UUID.fromString(owner);

        battery = configuration.getInt("battery", battery);

        if (configuration.contains("phone-color")) {

            Color temp = PluginUtils.getColorFromString(configuration.getString("phone-color", colorToString(DEFAULT_PHONE_COLOR)));
            if (temp != null) {
                phoneColor = temp;
                resetLayout();
            }
        }

        // Init all booleans.
        settingsValues.clear();
        for (Settings setting : Settings.values()) {
            if (setting.getType() != Settings.SettingType.TOGGLE) continue;
            initBooleanSettings(setting);
        }

        // Init cracks.
        cracks.clear();
        for (String crack : configuration.getStringList("cracks")) {
            String[] data = PluginUtils.splitData(crack);
            if (data.length != 3) continue;

            Coord coord = new Coord(Integer.parseInt(data[1]), Integer.parseInt(data[1]));
            cracks.add(new Crack(Integer.parseInt(data[0]), coord));
        }

        String backgroundName = configuration.getString("background.name"), from = configuration.getString("background.from");
        if (backgroundName == null || from == null) return;

        isBackgroundImageFromGallery = from.equalsIgnoreCase("GALLERY");

        File temp = new File(isBackgroundImageFromGallery ? getGalleryFolder() : plugin.getBackgroundFolder(), backgroundName);
        if (!temp.exists()) {
            // We do this because if the image is from gallery, it may exist in "pictures", but not in the real directory.
            backgroundImageName = null;
            createDefaultBackgroundIfNeeded();
            return;
        }

        setPictureAsBackground(backgroundName, false);
        createDefaultBackgroundIfNeeded();
    }

    private void createDefaultBackgroundIfNeeded() {
        boolean nullBackground = backgroundImageName == null;
        if (nullBackground) {
            backgrounds.clear();
            backgrounds.add(createDefaultFlatBackground());
            isBackgroundImageFromGallery = false;
        }

        BufferedImage firstImage = backgrounds.get(0);
        if (getDrawFromPage("main", "background") instanceof ImageDraw image) {
            image.setImage(firstImage);
        }

        // If this is null, then this phone is new and will be saved after creation.
        if (configuration != null && nullBackground) saveConfig();
    }

    private void initBooleanSettings(Settings setting) {
        // Init boolean settings (toggle) with their default values.
        settingsValues.put(setting, configuration.getBoolean("settings." + setting.toConfigPath(), setting.getDefaultValue().asBoolean()));
    }

    private @NotNull String getImageCounter() {
        return (getCurrentPictureIndex() + 1) + "/" + (pictures != null ? pictures.size() : -1);
    }

    private void addSpecialButton(String page, String which, Coord coord, Predicate<Phone> drawCondition) throws IOException {
        InputStream icon = plugin.getResource("icon/" + which + ".png");
        if (icon == null) return;

        addDraw(page, new ImageDraw(which, coord, drawCondition, ImageIO.read(icon)));
    }

    private boolean shouldForceUpdate() {
        // If the current page has any text display, we need to update it frecuently.
        return forceChargingUpdate() || (enabled && alreadyStarted && ((showError && !blocked)
                || reduceBrightness
                // Main is a special case, we need it here to update the selected app name.
                || ((NO_TOUCH_PAGES.contains(currentPage) || currentPage.equals("main")) && TOUCH_DRAW.test(this) == isNoTouchHide)
                || (currentPage.equals("main") && forceBatteryUpdate() || outOfBattery())
                || (currentPage.equals("main") && (currentTime == null || !currentTime.equals(getTimeFormatted())))
                || (currentPage.equals("main") && (currentDate == null || !currentDate.equals(getDateFormatted())) && isBlocked())
                || draws.get(currentPage).stream().anyMatch(draw -> draw instanceof TextDraw text && text.requiresAnimation() && text.canBeDrawn(this))));
    }

    private boolean forceChargingUpdate() {
        return isCharging() && forceBatteryUpdate();
    }

    private boolean forceBatteryUpdate() {
        return currentBattery == null || !currentBattery.equals(getBatteryFormatted());
    }

    public boolean outOfBattery() {
        return battery <= 0;
    }

    @Override
    public void render(@NotNull MapView view, @NotNull MapCanvas canvas, @NotNull Player player) {
        boolean enabledAndStarted = enabled && alreadyStarted;

        if (enabledAndStarted && !isPlayingSong()) noTouchCounter++;

        handleBrightness();

        // Only reducing battery if the phone is enabled.
        if (enabledAndStarted) reduceBattery(Config.BATTERY_DECREASE_RATE.asInt());

        boolean previousUpdated = updated;

        // Here, "updated" value may change.
        handleBackground();

        if (updated && !shouldForceUpdate()) return;

        if (!updated && !previousUpdated) {
            noTouchCounter = 0;
        }

        isNoTouchHide = !TOUCH_DRAW.test(this);
        updated = true;

        currentBattery = getBatteryFormatted();
        currentTime = getTimeFormatted();
        currentDate = getDateFormatted();

        // No more battery, power off.
        if (enabled && outOfBattery()) {
            powerOff();
        }

        // Add current page draws (if enabled) and THEN core.
        if (enabled && alreadyStarted) handleRenders(canvas, draws.get(currentPage));
        handleRenders(canvas, draws.get("core"));

        Draw draw;
        if ((!enabled || !alreadyStarted)
                || (showError && !blocked)
                || currentButton == null
                || (!((draw = getDrawFromPage(currentPage, currentButton)) instanceof ImageDraw image) || !draw.canBeDrawn(this))) {
            showCracks(canvas);
            return;
        }

        // TODO: App selector, cracks & toggle settings aren't saved in any page draws (therefore, they're not being cached). These ones needs to be handled differently.
        new ImageDraw("app-selector",
                draw.getCoord(),
                PluginUtils.createSelector(image.getImage(), getSelectorColor())).handleRender(this, canvas);

        showCracks(canvas);
    }

    private Color getSelectorColor() {
        if (currentPage.equals("settings")) {
            return Color.BLACK;
        }

        if (currentPage.equals("gallery")
                && (currentButton.equals("checkmark") || currentButton.equals("house"))
                && currentPicture != null
                && currentPicture.equals(backgroundImageName)) {
            return Palette.LIGHT_GREEN.getJavaColor();
        }

        if (currentButton != null && currentButton.equals("camera")) {
            return (takingPicture || !canTakePicture() ? Palette.RED : Palette.LIGHT_GREEN).getJavaColor();
        }

        return Color.WHITE;
    }

    private boolean isPlayingSong() {
        return songPlayer != null && songPlayer.isPlaying();
    }

    private void handleBrightness() {
        if (!enabled || !alreadyStarted || isPlayingSong()) return;

        if (updated && noTouchCounter >= (REDUCE_BRIGHTNESS_AFTER + BLOCK_AFTER) * 4) {
            reduceBrightness = false;
            noTouchCounter = 0;
            setEnabled(false);
        } else if (updated && noTouchCounter >= REDUCE_BRIGHTNESS_AFTER * 4) {
            if (!reduceBrightness) reduceBrightness = true;
        } else {
            reduceBrightness = false;
        }
    }

    private void handleBackground() {
        if (!enabled) return;

        // For now, only background gallery show animated backgrounds.
        if (!currentPage.equals("main") && (!currentPage.equals("gallery") || !isBackgroundGallery)) {
            currentBackgroundIndex = 0;
            return;
        }

        if (!alreadyStarted) {
            requestUpdate();

            if (++startAnimationIndex > plugin.getStartAnimation().size() - 1) {
                startAnimationIndex = 0;
                animationCount++;
            }

            if (!Config.START_ANIMATION_ENABLED.asBool() || animationCount >= Config.START_ANIMATION_COUNT.asInt()) {
                alreadyStarted = true;
            }

            return;
        }

        if (currentPage.equals("main") && usingAnimatedBackground()) {
            BufferedImage current = backgrounds.get(currentBackgroundIndex);

            // At this point, this draw shouldn't be null.
            ImageDraw background = (ImageDraw) getDrawFromPage("main", "background");
            if (background == null) return;

            background.setImage(current);
            requestUpdate();

            if (!showError && ++currentBackgroundIndex > backgrounds.size() - 1) {
                currentBackgroundIndex = 0;
            }

            return;
        }

        if (!currentPage.equals("gallery")
                || !isBackgroundGallery
                || pictures == null
                || currentPicture == null
                || pictures.get(currentPicture).size() <= 1) return;

        BufferedImage current = pictures.get(currentPicture).get(currentBackgroundIndex);

        // At this point, this draw shouldn't be null.
        ImageDraw background = (ImageDraw) getDrawFromPage("gallery", "picture");
        if (background == null) return;

        background.setImage(current);
        requestUpdate();

        if (!showError && ++currentBackgroundIndex > pictures.get(currentPicture).size() - 1) {
            currentBackgroundIndex = 0;
        }
    }

    public @Nullable Player getOwnerAsPlayer() {
        return owner != null ? Bukkit.getPlayer(owner) : null;
    }

    private void showCracks(MapCanvas canvas) {
        for (Crack crack : cracks) {
            String crackName = "crack/" + crack.which() + ".png";

            BufferedImage crackImage;

            BufferedImage temp = CRACK_CACHE.get(crackName);
            if (temp != null) {
                crackImage = temp;
            } else {
                CRACK_CACHE.put(crackName, crackImage = loadCrackImage(crackName));
            }

            new ImageDraw("crack-" + crack.which(), crack.coord(), crackImage).handleRender(this, canvas);
        }
    }

    private @Nullable BufferedImage loadCrackImage(String crackName) {
        InputStream crackStream = plugin.getResource(crackName);
        if (crackStream == null) return null;
        try {
            return ImageIO.read(crackStream);
        } catch (IOException exception) {
            exception.printStackTrace();
            return null;
        }
    }

    private void handleSettings(@NotNull Draw current, MapCanvas canvas) throws IOException {
        int which = Integer.parseInt(current.getName().split("-")[1]) - 1;
        if (which > Settings.values().length - 1) return;

        int finalWhich = which == 0 ? getFirstSettingIndex() : which == 1 ? getSecondSettingIndex() : which == 2 ? getThirdSettingIndex() : -1;
        if (finalWhich == -1) return;

        Settings setting = Settings.values()[finalWhich];
        if (setting.getType() != Settings.SettingType.TOGGLE) return;

        InputStream onStream = plugin.getResource("icon/" + (getBoolSetting(setting) ? "on" : "off") + ".png");
        if (onStream == null) return;

        new ImageDraw(setting.toConfigPath() + "-toggle", current.getCoord().offset(86, 7), ImageIO.read(onStream)).handleRender(this, canvas);
    }

    public int getCurrentPictureIndex() {
        if (pictures == null || currentPicture == null) return -1;
        return new ArrayList<>(pictures.keySet()).indexOf(currentPicture);
    }

    public int getFirstSongIndex() {
        return music == null ? -1 : getIndex(music.keySet(), songs, Triple::getLeft);
    }

    public int getSecondSongIndex() {
        return music == null ? -1 : getIndex(music.keySet(), songs, Triple::getMiddle);
    }

    public int getThirdSongIndex() {
        return music == null ? -1 : getIndex(music.keySet(), songs, Triple::getRight);
    }

    public int getFirstSettingIndex() {
        return settings == null ? -1 : ArrayUtils.indexOf(Settings.values(), settings.getLeft());
    }

    public int getSecondSettingIndex() {
        return settings == null ? -1 : ArrayUtils.indexOf(Settings.values(), settings.getMiddle());
    }

    public int getThirdSettingIndex() {
        return settings == null ? -1 : ArrayUtils.indexOf(Settings.values(), settings.getRight());
    }

    private int getIndex(Collection<?> collection, Triple<?, ?, ?> triple, Function<Triple<?, ?, ?>, ?> getter) {
        Object which;
        if (triple == null || (which = getter.apply(triple)) == null) return -1;
        return new ArrayList<>(collection).indexOf(which);
    }

    private void handleRenders(MapCanvas canvas, @NotNull Collection<Draw> draws) {
        for (Draw draw : draws) {
            String name = draw.getName();
            if (!draw.handleRender(this, canvas) && draw instanceof TextDraw text) {
                plugin.getLogger().warning("The line for the map \"" + text.getMessage() + "\" contains invalid characters!");
                continue;
            }

            if (!name.contains("tab") || name.endsWith("setting") || !currentPage.equals("settings")) continue;

            try {
                handleSettings(draw, canvas);
            } catch (IOException exception) {
                exception.printStackTrace();
            }
        }
    }

    public void addDraw(String page, @NotNull Draw draw) {
        draws.put(page, draw);
    }

    public @Nullable ItemStack build() {
        if (built) return null;

        view = Bukkit.createMap(Bukkit.getWorlds().get(0));
        view.setScale(Scale.NORMAL);
        view.getRenderers().forEach(view::removeRenderer);
        view.addRenderer(this);

        built = (item = ConfigFileUtils.getItem(plugin, "phone", null)
                .setType(Material.FILLED_MAP)
                .setData(plugin.getPhoneIDKey(), PersistentDataType.INTEGER, view.getId())
                .setMapView(view)
                .build()) != null;

        // Save after building view too.
        initFiles();
        saveConfig();

        return item;
    }

    public boolean getBoolSetting(@NotNull Settings setting) {
        return setting.getType() == Settings.SettingType.TOGGLE && setting.getOrDefault(settingsValues).asBoolean();
    }

    public void playClickSound(Player player) {
        playSound(player, player.getLocation(), "sounds.click", Sound.BLOCK_LEVER_CLICK);
    }

    public void playToggleSound(Player player, boolean on) {
        playSound(
                player,
                player.getLocation(),
                "sounds.toggle-button." + (on ? "on" : "off"),
                on ? Sound.BLOCK_WOODEN_BUTTON_CLICK_ON : Sound.BLOCK_WOODEN_BUTTON_CLICK_OFF);
    }

    public void playSound(@Nullable Player player, Location at, String path, Sound defaultSound) {
        if (!getBoolSetting(Settings.SOUND)) return;

        String soundData = plugin.getConfig().getString(path, defaultSound.name());
        String[] data = PluginUtils.splitData(soundData);

        Sound sound;
        float volume = 0.5f, pitch = 1.0f;
        if (data.length == 1) {
            sound = PluginUtils.getOrDefault(Sound.class, soundData, defaultSound);
        } else if (data.length == 2) {
            sound = PluginUtils.getOrDefault(Sound.class, data[0], defaultSound);
            volume = Float.parseFloat(data[1]);
        } else if (data.length == 3) {
            sound = PluginUtils.getOrDefault(Sound.class, data[0], defaultSound);
            volume = Float.parseFloat(data[1]);
            pitch = Float.parseFloat(data[2]);
        } else return;

        if (player != null) player.playSound(at, sound, volume, pitch);
        else if (at.getWorld() != null) at.getWorld().playSound(at, sound, volume, pitch);
    }

    public void takePicture(@NotNull Player player) {
        if (isProcessingPicture()) return;

        takingPicture = true;

        List<Player> players = player.getWorld().getPlayers();
        players.remove(player);

        ImageCapture capture = new ImageCapture(
                player.getEyeLocation().clone(),
                players,
                ImageCaptureOptions.builder()
                        .width(BACKGROUND_SIZE[0])
                        .height(BACKGROUND_SIZE[1])
                        .fov(1.5f)
                        .dayLightCycleAware(true)
                        .excludedBlocks(Constants.EXCLUDED_BLOCKS)
                        .blocks(Constants.BLOCKS)
                        .build());

        new BukkitRunnable() {

            @SuppressWarnings("ResultOfMethodCallIgnored")
            @Override
            public void run() {
                try {
                    BufferedImage image = capture.render();

                    String time = LocalDateTime.now().format(DateTimeFormatter.ofPattern(Config.DATE_FORMAT_PICTURE.asString()));

                    File output = new File(getGalleryFolder(), time + ".png");
                    if (!output.exists()) output.mkdirs();

                    ImageIO.write(image, "PNG", output);

                    int currentIndex = getCurrentPictureIndex();
                    boolean isBeforeLast = currentIndex < (pictures = reloadPhonePictures()).size() - 1 && currentIndex > pictures.size() - 3;

                    if (lastButtons.containsKey("gallery") && currentPicture != null && isBeforeLast) {
                        lastButtons.put("gallery", "next");
                    }

                    // If "previousPage" isn't null then it's safe to assume that "previousButton" isn't null either.
                    if (previousPage != null && previousPage.equals("gallery") && isBeforeLast) {
                        previousButton = "next";
                    }

                    // Taking a picture takes extra battery.
                    reduceBattery(Config.BATTERY_EXTRA_DECREASE_PICTURE.asInt());

                    takingPicture = false;

                    if (currentButton != null && currentButton.equals("camera")) {
                        requestUpdate();
                    }
                } catch (IOException exception) {
                    exception.printStackTrace();
                }
            }
        }.runTaskAsynchronously(plugin);
    }

    public void increaseBattery(int increase) {
        battery = Math.min(battery + increase, getMaxBattery());
        saveBattery();
    }

    public void reduceBattery(int reduce) {
        battery = Math.max(0, battery - reduce);
        saveBattery();
    }

    private void saveBattery() {
        // Save the battery in config every 5%.
        int percentage = getBatteryPercentage();
        if (percentage % 5 != 0 || percentage == 100 || percentage == lastBatterySaveAt) return;

        lastBatterySaveAt = percentage;
        saveConfig();
    }

    public @Nullable Draw getDrawFromPage(String page, String button) {
        for (Draw draw : draws.get(page)) {
            if (draw.getName().equals(button)) return draw;
        }
        return null;
    }

    public void setCurrentPage(String currentPage, String currentButton) {
        if ((this.currentPage != null && this.currentPage.equals(currentPage) && (!this.currentPage.equals("main") || !blocked)) || currentPage.equals("core"))
            return;

        if (this.currentPage != null && this.currentButton != null && (!isBackgroundGallery || currentPage.equals("gallery"))) {
            if (getPageButtons(this.currentPage).contains(this.currentButton)) {
                lastButtons.put(previousPage = this.currentPage, previousButton = this.currentButton);
            }
        }

        this.currentPage = currentPage;

        if (currentPage.equals("music")) {
            emptyMusicIcon = emptyMusicText = null;
        }

        // Set back previous pictures.
        if (currentPage.equals("settings") && isBackgroundGallery) {
            isBackgroundGallery = false;
            pictures = tempPictures;
            currentPicture = tempCurrentPicture;

            previousPage = "main";
            previousButton = lastButtons.getOrDefault("main", getPageButtons("main").get(0));

            // Render previous gallery picture.
            if (currentPicture != null && pictures != null) {
                Draw draw = getDrawFromPage("gallery", "picture");
                if (draw instanceof ImageDraw image) {
                    image.setImage(pictures.get(currentPicture).get(0));
                }
            }
        }

        setCurrentButton(currentButton);
        requestUpdate();
    }

    public void setEnabled(boolean enabled) {
        if (this.enabled == enabled) return;

        this.enabled = enabled;

        if (enabled) {
            boolean alreadyChanged = false;

            Draw time = getDrawFromPage("main", "time");
            if (time instanceof TextDraw text) {
                if (text.getCoord().equals(MID_CORNER)) {
                    alreadyChanged = true;
                } else {
                    text.setCoord(MID_CORNER);
                }
            }

            if (!alreadyChanged) {
                previousBlockedPage = previousPage;
                previousBlockedButton = previousButton;
                setCurrentPage("main", null);
            }
        } else blocked = true;

        stopCurrentSong(false);

        requestUpdate();
    }

    public void setCurrentButton(String currentButton) {
        if (currentButton == null || (this.currentButton != null && this.currentButton.equals(currentButton))) return;

        this.currentButton = currentButton;
        requestUpdate();
    }

    public void setCurrentPicture(String currentPicture) {
        if (this.currentPicture != null && this.currentPicture.equals(currentPicture)) return;

        this.currentPicture = currentPicture;
        requestUpdate();
    }

    private void requestUpdate() {
        updated = false;
    }

    public @NotNull Map<String, File> reloadPhoneMusic() {
        File musicFolder = new File(plugin.getMusicFolder());
        if (!musicFolder.isDirectory()) return Collections.emptyMap();

        File[] nbsSongs = musicFolder.listFiles((directory, name) -> new File(directory, name).isFile() && name.endsWith(".nbs"));
        if (nbsSongs == null) return Collections.emptyMap();

        return Arrays.stream(nbsSongs)
                .filter(file -> {
                    SongData data = SONG_CACHE.get(file.getName());
                    return data != null ? data.isValid() : createSongCache(file);
                })
                .map((Function<File, Map.Entry<String, File>>) file -> new AbstractMap.SimpleEntry<>(file.getName(), file))
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        (previousValue, newValue) -> previousValue,
                        LinkedHashMap::new));
    }

    private boolean createSongCache(File file) {
        try {
            FileInputStream stream = new FileInputStream(file);

            SongData data;
            SONG_CACHE.put(file.getName(), data = SongData.fromSong(NBSDecoder.parse(stream)));

            stream.close();
            return data.isValid();
        } catch (IOException exception) {
            exception.printStackTrace();
            return false;
        }
    }

    private @Nullable Map.Entry<String, List<BufferedImage>> getImages(@NotNull File file) {
        List<BufferedImage> temp = new ArrayList<>();

        if (file.isDirectory()) {
            File[] folderImages = file.listFiles((directory, name) -> new File(directory, name).isFile() && name.endsWith(".png"));
            if (folderImages == null) return null;

            for (File img : folderImages) {
                BufferedImage image = PluginUtils.from128to116Pixels(img);
                if (image != null) temp.add(image);
            }
        } else {
            BufferedImage image = PluginUtils.from128to116Pixels(file);
            if (image != null) temp.add(image);
        }

        return new AbstractMap.SimpleEntry<>(file.getName(), temp);
    }

    public @NotNull Map<String, List<BufferedImage>> reloadPhonePictures() {
        File gallery = new File(isBackgroundGallery ? plugin.getBackgroundFolder() : getGalleryFolder());
        if (!gallery.isDirectory()) return Collections.emptyMap();

        File[] images = gallery.listFiles();
        if (images == null) return Collections.emptyMap();

        return Arrays.stream(images)
                .map(this::getImages).filter(entry -> entry != null && !entry.getValue().isEmpty())
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        (previousValue, newValue) -> previousValue,
                        LinkedHashMap::new));
    }

    public void reloadButtons() {
        try {
            reloadMainPageButtons();
            reloadTabPageButtons("music");
            reloadTabPageButtons("settings");
            reloadGalleryPageButtons(true);
            reloadGalleryPageButtons(false);
        } catch (IOException exception) {
            exception.printStackTrace();
        }
    }

    private void reloadMainPageButtons() throws IOException {
        List<String> buttons = getPageButtons("main");
        if (buttons == null) return;

        if (buttons.size() > mainCoords.size()) {
            plugin.getLogger().info("Some apps were left out because of space!");
        }

        for (int i = 0; i < mainCoords.size(); i++) {
            if (i == buttons.size()) break;

            Coord coord = mainCoords.get(i);
            String button = buttons.get(i);

            String color = plugin.getConfig().getString("apps." + button + ".color", "black");

            InputStream app = plugin.getResource("icon/standard/" + button + "/" + color + ".png");
            if (app == null) {
                plugin.getLogger().warning("Can't find {" + button + "} image!");
                continue;
            }

            addDraw("main", new ImageDraw(button, coord, Predicate.not(Phone::isBlocked), ImageIO.read(app)));
        }
    }

    private void reloadTabPageButtons(@NotNull String page) throws IOException {
        boolean isPlay = page.equals("music");

        InputStream app = plugin.getResource("icon/standard/tab/" + (isPlay ? "black" : "white") + ".png");
        if (app == null) {
            plugin.getLogger().warning("Can't find {tab} image!");
            return;
        }

        BufferedImage image = ImageIO.read(app);
        Function<Phone, Integer> sizeGetter = temp -> (isPlay ? temp.getMusic().size() : Settings.values().length);

        IntStream.rangeClosed(1, 3).forEach(value -> addTabPageButton(page, image, value, sizeGetter));
    }

    private void addTabPageButton(String page, BufferedImage image, int which, Function<Phone, Integer> sizeGetter) {
        addDraw(page, new ImageDraw("tab-" + which, new Coord(TAB_BUTTONS_X, TAB_BUTTONS_Y[which - 1]), temp -> sizeGetter.apply(temp) >= which, image));
    }

    private void reloadGalleryPageButtons(boolean isBackgroundGallery) {
        List<String> temp = isBackgroundGallery ? BACKGROUND_GALLERY_BUTTONS : GALLERY_BUTTONS;
        List<Coord> coords = PluginUtils.generateCoords(temp.size(), 24, 24, 14, 128, 0).stream().map(coord -> coord.offset(0, -coord.y() + 98)).toList();

        for (String button : temp) {
            Coord coord = coords.get(temp.indexOf(button));
            addGalleryPageButton(coord, button, isBackgroundGallery);
        }
    }

    private void addGalleryPageButton(Coord coord, @NotNull String button, boolean isBackgroundGallery) {
        InputStream app = plugin.getResource("icon/flat/" + button + ".png");
        if (app == null) {
            plugin.getLogger().warning("Can't find {" + button + "} image!");
            return;
        }

        try {
            addDraw("gallery", new ImageDraw(button, coord, TOUCH_DRAW.and(isBackgroundGallery ? Phone::isBackgroundGallery : Predicate.not(Phone::isBackgroundGallery)), ImageIO.read(app)));
        } catch (IOException exception) {
            exception.printStackTrace();
        }
    }

    public @NotNull BufferedImage createDefaultFlatBackground() {
        BufferedImage image = getRandomBackground();
        return image != null ? image : createColorBackground(BACKGROUND_COLOR_DEFAULT);
    }

    private @Nullable BufferedImage getRandomBackground() {
        File flatBackgroundDir = new File(plugin.getDataFolder() + File.separator + "background");
        if (!flatBackgroundDir.isDirectory()) return null;

        File[] backgrounds = flatBackgroundDir.listFiles((directory, name) -> new File(directory, name).isFile() && name.endsWith(".png"));
        if (backgrounds == null) return null;

        File background = backgrounds[ThreadLocalRandom.current().nextInt(backgrounds.length)];
        backgroundImageName = background.getName();

        return PluginUtils.from128to116Pixels(background);
    }

    private @NotNull BufferedImage createColorBackground(Color color) {
        BufferedImage background = new BufferedImage(BACKGROUND_SIZE[0], BACKGROUND_SIZE[1], BufferedImage.TYPE_INT_ARGB);

        Graphics2D graphics = background.createGraphics();
        graphics.setColor(color);
        graphics.fillRect(0, 0, background.getWidth(), background.getHeight());
        graphics.dispose();

        return background;
    }

    public void switchButton(boolean left) {
        if (showError) return;

        String newButton = switchDataOption(getPageButtons(currentPage).toArray(String[]::new), currentButton, left, currentPage.equals("main"));

        Draw button = getDrawFromPage(currentPage, newButton);
        if (button != null && !button.canBeDrawn(this)) return;

        setCurrentButton(newButton);
    }

    public String switchDataOption(String[] data, String current, boolean left, boolean restart) {
        int index = ArrayUtils.indexOf(data, current) + (left ? -1 : 1);
        return data[left && index < 0 ? (restart ? data.length - 1 : 0) : !left && index > data.length - 1 ? (restart ? 0 : data.length - 1) : index];
    }

    public void switchCurrentPicture(boolean left) {
        String picture = switchDataOption(pictures.keySet().toArray(String[]::new), currentPicture, left, false);
        updateCurrentPicture(picture);
    }

    public void updateCurrentPicture(String newImage) {
        // Update picture frame.
        Draw picture = getDrawFromPage("gallery", "picture");
        if (picture instanceof ImageDraw image) image.setImage(pictures.get(newImage).get(0));

        setCurrentPicture(newImage);
    }

    public void setCurrentSong(String currentSong) {
        if (this.currentSong != null && this.currentSong.equals(currentSong)) return;

        this.currentSong = currentSong;
        requestUpdate();
    }

    private @NotNull String getPhoneFolder() {
        return plugin.getDataFolder() + File.separator + "phones" + File.separator + view.getId();
    }

    public @NotNull String getGalleryFolder() {
        return getPhoneFolder() + File.separator + "gallery";
    }

    private boolean isProcessingPicture() {
        if (!takingPicture || isBackgroundGallery) return false;
        showErrorDialog(Config.ERROR_PROCESSING_PICTURE.asString());
        return true;
    }

    @Contract(pure = true)
    private @NotNull String getEmptyGalleryMessage() {
        return (isBackgroundGallery ? Config.ERROR_EMPTY_DEFAULT_BACKGROUNDS : Config.ERROR_EMPTY_GALLERY).asString();
    }

    public void openGallery(Player player) {
        if (isProcessingPicture()) return;

        if (isBackgroundGallery) {
            tempPictures = pictures;
            tempCurrentPicture = currentPicture;
        }

        Map<String, List<BufferedImage>> temp = pictures;
        boolean safeSave;
        if (isEmpty(pictures = reloadPhonePictures(), getEmptyGalleryMessage())) {

            if (isBackgroundGallery) {
                isBackgroundGallery = false;
                pictures = tempPictures;
                currentPicture = tempCurrentPicture;
            }

            return;
        }
        if ((safeSave = !galleryModified && (temp == null || PluginUtils.sameGalleryPictures(temp, pictures)))
                && canReOpen(player, "gallery")) return;

        galleryModified = false;

        if (safeSave && currentPicture != null && lastButtons.containsKey("gallery") && !isBackgroundGallery) {
            setCurrentPage("gallery", lastButtons.get("gallery"));
            return;
        }

        // The first image.
        String currentPicture = pictures.keySet().iterator().next();
        updateCurrentPicture(currentPicture);

        String previousPage = currentPage;
        setCurrentPage("gallery", "next");

        // If we're re-opening the gallery when we are already in the gallery, setCurrentPage() won't work as expected.
        if (previousPage.equals("gallery") && !isBackgroundGallery && pictures.size() > 1) {
            setCurrentButton("next");
            return;
        }

        // If we can't show the next image button, then we select the first hotbar button.
        // Here we use !NEXT_DRAW.test(this) instead of !next.canBeDrawn(this) because NO_TOUCH will return false.
        Draw next = getDrawFromPage("gallery", "next");
        if (next != null && !NEXT_DRAW.test(this)) {
            setCurrentButton((isBackgroundGallery ? BACKGROUND_GALLERY_BUTTONS : GALLERY_BUTTONS).get(0));
        }
    }

    public void dismissError() {
        if (getDrawFromPage("main", "error-message") instanceof TextDraw text) {
            // So the animation starts at 0 when opening again.
            text.setPreviousMessage(null);
        }

        showError = false;
        requestUpdate();
    }

    public void showErrorDialog(String errorMessage) {
        this.errorMessage = errorMessage;
        showError = true;
        requestUpdate();
    }

    private boolean isEmpty(@NotNull Map<?, ?> map, String errorMessage) {
        if (!map.isEmpty()) return false;

        if (isShowError()) {
            dismissError();
        } else {
            showErrorDialog(errorMessage);
        }

        return true;
    }

    private boolean canReOpen(Player player, String which) {
        // If was open, open again with previous button.
        if (previousPage != null && previousButton != null && previousPage.equals(which) && (!which.equals("gallery") || !isBackgroundGallery)) {
            reOpenPrevious(player, true);
            return true;
        }

        return false;
    }

    public void openMusic(Player player) {
        Map<String, File> temp = music;
        boolean safeSave;
        if (isEmpty(music = reloadPhoneMusic(), Config.ERROR_EMPTY_MUSIC.asString())) return;
        if ((safeSave = (temp == null || temp.equals(music))) && canReOpen(player, "music")) return;

        if (safeSave && songs != null && lastButtons.containsKey("music")) {
            setCurrentPage("music", lastButtons.get("music"));
            return;
        }

        String first = null, second = null, third = null;

        for (String next : music.keySet()) {
            if (first == null) first = next;
            else if (second == null) second = next;
            else third = next;
            if (first != null && second != null && third != null) break;
        }

        songs = Triple.of(first, second, third);

        setCurrentPage("music", "tab-1");
    }

    public void openSettings(Player player) {
        if (canReOpen(player, "settings")) return;

        if (lastButtons.containsKey("settings")) {
            setCurrentPage("settings", lastButtons.get("settings"));
            return;
        }

        Settings first = null, second = null, third = null;

        for (Settings next : Settings.values()) {
            if (first == null) first = next;
            else if (second == null) second = next;
            else third = next;
            if (first != null && second != null && third != null) break;
        }

        settings = Triple.of(first, second, third);
        updateSettingsTextLength();

        setCurrentPage("settings", "tab-1");
    }

    public boolean enoughCracks() {
        return cracks.size() >= 6;
    }

    private void updateSettingsTextLength() {
        // Reduce space for toggle buttons.
        for (String button : getPageButtons("settings")) {
            if (IGNORE_MOVE_BUTTONS.contains(button)) continue;

            Draw draw = getDrawFromPage("settings", button + "-setting");
            if (!(draw instanceof TextDraw text)) continue;

            int which = Integer.parseInt(button.split("-")[1]) - 1;

            Settings setting = getSetting(which);
            if (setting == null) continue;

            text.setPreviousMessage(null);

            boolean smaller = setting.getType() == Settings.SettingType.TOGGLE;
            text.setMaxLength(smaller ? 13 : 16);
            text.setMaxWidth(smaller ? 82 : 95);
        }
    }

    public void reOpenPrevious(Player player, boolean force) {
        String lastPage = previousPage;
        if (lastPage == null) return;

        // Special cases, where songs/images can be deleted at any time.
        boolean isPlay;
        if (!force && ((isPlay = lastPage.equals("music")) || lastPage.equals("gallery"))) {
            if (isPlay) openMusic(player);
            else openGallery(player);
            return;
        }

        previousPage = null;

        String lastButton = previousButton;
        if (lastButton == null) return;
        previousButton = null;

        // So every animation is started from 0.
        draws.get(lastPage).stream().filter(draw -> draw instanceof TextDraw).map(draw -> (TextDraw) draw).forEach(textDraw -> textDraw.setPreviousMessage(null));

        if (!getPageButtons(lastPage).contains(lastButton)) {
            lastButton = lastButtons.get(lastPage);
        }

        setCurrentPage(lastPage, lastButton);
    }

    public void updateSongs(@NotNull BiFunction<Phone, ArrayList<String>, Pair<Integer, Integer>> function) {
        String first = null, second = null, third = null;

        ArrayList<String> temp = new ArrayList<>(music.keySet());

        Pair<Integer, Integer> range = function.apply(this, temp);
        for (String next : temp.subList(range.getLeft(), range.getRight())) {
            if (first == null) first = next;
            else if (second == null) second = next;
            else third = next;

            if (first != null && second != null && third != null) break;
        }

        songs = Triple.of(first, second, third);
        requestUpdate();
    }

    public void updateSettings(@NotNull BiFunction<Phone, ArrayList<Settings>, Pair<Integer, Integer>> function) {
        Settings first = null, second = null, third = null;

        ArrayList<Settings> temp = new ArrayList<>(Arrays.asList(Settings.values()));
        Pair<Integer, Integer> range = function.apply(this, temp);

        for (Settings next : temp.subList(range.getLeft(), range.getRight())) {
            if (first == null) first = next;
            else if (second == null) second = next;
            else third = next;

            if (first != null && second != null && third != null) break;
        }

        settings = Triple.of(first, second, third);
        updateSettingsTextLength();

        requestUpdate();
    }

    public void setPictureAsBackground(String picture, boolean isGalleryClick) {
        if (showError) {
            dismissError();
            return;
        }

        if (picture != null && picture.equals(backgroundImageName)) {
            if (isGalleryClick) showErrorDialog(Config.ERROR_IMAGE_IS_ALREADY_THE_BACKGROUND.asString());
            return;
        }

        backgroundImageName = picture;

        ImageDraw background = (ImageDraw) getDrawFromPage("main", "background");
        if (background != null) {
            backgrounds.clear();

            List<BufferedImage> images = pictures == null ? null : pictures.get(picture);
            if (images == null && (images = tryToFindBackground(isGalleryClick, picture)) == null) return;

            if (images.isEmpty()) {
                backgroundImageName = null;
                return;
            }

            backgrounds.addAll(images);

            background.setImage(backgrounds.get(0));
            updated = false;
        }

        isBackgroundImageFromGallery = isGalleryClick ? !isBackgroundGallery : isBackgroundImageFromGallery;

        // We don't want to save in power off, it's already saved.
        if (isGalleryClick) saveConfig();
    }

    private @Nullable List<BufferedImage> tryToFindBackground(boolean isGalleryClick, String picture) {
        if (isGalleryClick) {
            backgroundImageName = null;
            plugin.getLogger().severe("Couldn't find the background {" + picture + "}!");
            return null;
        }

        // This case shouldn't happen at all since we checked this on update(); it's here just in case.
        File temp = new File(isBackgroundImageFromGallery ? getGalleryFolder() : plugin.getBackgroundFolder(), picture);
        if (!temp.exists()) {
            backgroundImageName = null;
            return null;
        }

        List<BufferedImage> images = new ArrayList<>();

        if (temp.isDirectory()) {
            File[] files = temp.listFiles();
            if (files == null) {
                backgroundImageName = null;
                return null;
            }

            for (File image : files) {
                BufferedImage tempImage = PluginUtils.from128to116Pixels(image);
                if (tempImage != null) images.add(tempImage);
            }

            return images;
        }

        BufferedImage tempImage = PluginUtils.from128to116Pixels(temp);
        if (tempImage != null) images.add(tempImage);

        return images;
    }

    public boolean isNoTouchHide() {
        return isNoTouchHide && NO_TOUCH_PAGES.contains(currentPage);
    }

    private @NotNull String getTimeFormatted() {
        return LocalDateTime.now().format(DateTimeFormatter.ofPattern(Config.DATE_FORMAT_HOUR.asString()));
    }

    public boolean usingAnimatedBackground() {
        return backgrounds.size() > 1;
    }

    public List<String> getPageButtons(@NotNull String page) {
        return PAGE_BUTTONS.get(page.equals("gallery") && isBackgroundGallery ? "backgrounds" : page);
    }

    public void powerOff() {
        // Save data BEFORE turning off.
        saveConfig();

        // General.
        enabled = false;
        alreadyStarted = false;
        blocked = true;
        showError = false;
        isNoTouchHide = false;
        takingPicture = false;
        noTouchCounter = 0;
        currentBackgroundIndex = 0;
        startAnimationIndex = animationCount = 0;
        updated = true;
        reduceBrightness = false;
        lastButtons.clear();
        errorMessage = null;
        currentPage = currentButton = previousPage = previousButton = previousBlockedPage = previousBlockedButton = null;

        // Gallery.
        pictures = tempPictures = null;
        currentPicture = tempCurrentPicture = null;
        isBackgroundGallery = false;
        galleryModified = false;

        // Music.
        music = null;
        currentSong = null;
        songs = null;
        emptyMusicIcon = emptyMusicText = null;
        stopCurrentSong(false);

        // Main page.
        backgroundImageName = null;
        isBackgroundImageFromGallery = false;
        backgrounds.clear();
        currentBattery = currentTime = currentDate = null;

        // Reset text animations.
        draws.values().stream()
                .filter(draw -> draw instanceof TextDraw)
                .map(draw -> (TextDraw) draw)
                .forEach(textDraw -> textDraw.setPreviousMessage(null));

        // Set default page and button.
        setCurrentPage(DEFAULT_PAGE, getPageButtons(DEFAULT_PAGE).get(0));

        // Load data after turning off.
        reloadConfig();
    }

    private @Nullable String stopCurrentSong(boolean hasNext) {
        if (songPlayer == null) return null;

        String stopped = songPlayer.getSong().getPath().getName();

        songPlayer.setPlaying(false);
        songPlayer.destroy();
        songPlayer.getPlayerUUIDs().forEach(songPlayer::removePlayer);
        songPlayer = null;

        if (!hasNext) setCurrentSong(null);

        return stopped;
    }

    public void handleMusic(Player player, int toPlay) {
        String stopped = stopCurrentSong(true);

        String which = toPlay == 1 ? songs.getLeft() : toPlay == 2 ? songs.getMiddle() : toPlay == 3 ? songs.getRight() : null;
        if (which == null) return;

        // Don't play stopped song.
        if (stopped != null && stopped.equals(which)) {
            setCurrentSong(null);
            return;
        }

        songPlayer = new CustomEntitySongPlayer(NBSDecoder.parse(new File(plugin.getMusicFolder(), which)), this);
        songPlayer.setEntity(player);
        songPlayer.setDistance(10);
        songPlayer.addPlayer(player);
        songPlayer.setRepeatMode(RepeatMode.NO);
        songPlayer.setRandom(false);
        songPlayer.setPlaying(true, new Fade(FadeType.LINEAR, 20));

        setCurrentSong(which);
    }

    public void setChargingAt(ItemFrame chargingAt) {
        this.chargingAt = chargingAt;
        requestUpdate();
    }

    private int getMaxBattery() {
        return 100 * Config.BATTERY_MULTIPLIER.asInt();
    }

    private static List<String> createGalleryButtonsByType(boolean isBackgroundGallery) {
        return PAGE_BUTTONS.get(isBackgroundGallery ? "backgrounds" : "gallery").stream()
                .filter(button -> !IGNORE_MOVE_BUTTONS.contains(button))
                .toList();
    }
}