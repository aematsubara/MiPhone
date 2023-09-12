package me.matsubara.miphone;

import lombok.Getter;
import lombok.Setter;
import me.matsubara.miphone.command.MainCommand;
import me.matsubara.miphone.file.Config;
import me.matsubara.miphone.file.Messages;
import me.matsubara.miphone.listener.EventListener;
import me.matsubara.miphone.listener.OtherListener;
import me.matsubara.miphone.phone.Phone;
import me.matsubara.miphone.phone.Settings;
import me.matsubara.miphone.phone.render.Draw;
import me.matsubara.miphone.phone.render.TextDraw;
import me.matsubara.miphone.runnable.PhoneChecker;
import me.matsubara.miphone.util.PluginUtils;
import me.matsubara.miphone.util.Shape;
import me.matsubara.miphone.util.config.ConfigFileUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.lang3.tuple.Triple;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Tag;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Item;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.security.CodeSource;
import java.util.List;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Getter
public final class MiPhonePlugin extends JavaPlugin {

    private final List<String> startAnimation = new ArrayList<>();
    private final Set<Phone> phones = new HashSet<>();
    private final Set<Material> safeDropBlocks = new HashSet<>();
    private final Map<String, Color> colors = new LinkedHashMap<>();
    private final NamespacedKey phoneIDKey = new NamespacedKey(this, "phone-id");
    private final NamespacedKey wirelessChargerKey = new NamespacedKey(this, "wireless-charger");
    private final Map<Item, Integer> droppedPhones = new ConcurrentHashMap<>();
    private final Set<ItemFrame> chargingPhones = new HashSet<>();
    private @Setter Shape wirelessCharger;

    private Messages messages;
    private MainCommand mainCommand;

    private static final List<String> IGNORE_SECTIONS = List.of("phone", "wireless-charger", "colors");

    @Override
    public void onEnable() {
        PluginManager manager = getServer().getPluginManager();
        if (manager.getPlugin("ImageryAPI") == null) {
            getLogger().severe("You need ImageryAPI to run this plugin!");
            manager.disablePlugin(this);
            return;
        }

        // Save files.
        saveDefaultConfig();
        saveFiles("background");
        saveFiles("song");
        messages = new Messages(this);
        updateConfigs();

        reloadStartAnimation();
        reloadColors();
        reloadSafeBlocks();

        wirelessCharger = createWirelessCharger();

        manager.registerEvents(new EventListener(this), this);
        manager.registerEvents(new OtherListener(this), this);

        PluginCommand command = getCommand("miphone");
        if (command != null) command.setExecutor(mainCommand = new MainCommand(this));

        new PhoneChecker(this).runTaskTimer(this, 10L, 10L);
    }

    public @NotNull Shape createWirelessCharger() {
        return ConfigFileUtils.createCraftableItem(this, "wireless-charger", wirelessChargerKey, Material.ITEM_FRAME);
    }

    public void updateConfigs() {
        String folder = getDataFolder().getPath();

        ConfigFileUtils.updateConfig(
                this,
                folder,
                "config.yml",
                file -> reloadConfig(),
                file -> saveDefaultConfig(),
                config -> IGNORE_SECTIONS,
                Collections.emptyList());

        ConfigFileUtils.updateConfig(
                this,
                folder,
                "messages.yml",
                file -> messages.setConfiguration(YamlConfiguration.loadConfiguration(file)),
                file -> saveResource("messages.yml"),
                config -> Collections.emptyList(),
                Collections.emptyList());
    }

    @SuppressWarnings("SameParameterValue")
    public void saveResource(String name) {
        File file = new File(getDataFolder(), name);
        if (!file.exists()) saveResource(name, false);
    }

    public void reloadSafeBlocks() {
        safeDropBlocks.clear();

        for (String string : Config.SAFE_DROP_BLOCKS.asStringList()) {
            if (string.startsWith("$")) {
                Tag<Material> tag = Bukkit.getTag(Tag.REGISTRY_BLOCKS, NamespacedKey.minecraft(string.substring(1).toLowerCase()), Material.class);
                if (tag != null) safeDropBlocks.addAll(tag.getValues());
                else getLogger().warning("Can't find tag {" + string + "}!");
                continue;
            }

            Material material = PluginUtils.getOrNull(Material.class, string);
            if (material != null) safeDropBlocks.add(material);
            else getLogger().warning("Can't find material {" + string + "}!");
        }
    }

    public void reloadStartAnimation() {
        startAnimation.clear();

        String word = Config.START_ANIMATION_WORD.asString();
        if (word.isEmpty()) return;

        for (int i = 1; i < word.length() + 1; i++) {
            startAnimation.add(word.substring(0, i));
        }
    }

    public void reloadColors() {
        colors.clear();

        ConfigurationSection section = getConfig().getConfigurationSection("colors");
        if (section == null) return;

        for (String path : section.getKeys(false)) {
            String string = getConfig().getString("colors." + path);
            if (string == null) continue;

            Color color = PluginUtils.getColorFromString(string);
            if (color != null) colors.put(path, color);
        }
    }

    private void saveFiles(String path) {
        if (new File(getDataFolder(), path).isDirectory()) return;

        CodeSource source = getClass().getProtectionDomain().getCodeSource();
        if (source == null) return;

        try {
            ZipInputStream zip = new ZipInputStream(source.getLocation().openStream());

            String folderPath = path + "/";

            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                String name = entry.getName();
                if (name.startsWith(folderPath) && !name.equals(folderPath) && !name.endsWith("/")) {
                    saveResource(name, false);
                }
            }
        } catch (IOException exception) {
            exception.printStackTrace();
        }
    }

    @Override
    public void onDisable() {
        for (Phone phone : phones) {
            phone.saveConfig();
        }
    }

    public boolean isPhone(int id) {
        File phonesFolder = new File(getDataFolder() + File.separator + "phones");
        if (!phonesFolder.isDirectory()) return false;

        File[] phones = phonesFolder.listFiles((directory, name) -> new File(directory, name).isDirectory());
        if (phones == null) return false;

        for (File phone : phones) {
            if (phone.getName().equals(String.valueOf(id))) return true;
        }

        return false;
    }

    public void handleLeftClick(@NotNull Player player, @NotNull Phone phone) {
        if (!phone.isEnabled()) {
            if (phone.outOfBattery()) {
                messages.send(player, Messages.Message.EMPTY_BATTERY);
                return;
            }

            if (phone.getCracks().size() >= 6) {
                messages.send(player, Messages.Message.BROKEN);
                return;
            }

            if (player.isSneaking()) {
                if (phone.isCharging()) {
                    messages.send(player, Messages.Message.ALREADY_CHARGING);
                    return;
                }

                phone.setEnabled(true);
                phone.playClickSound(player);
            }
            return;
        }

        // At this point, the phone is enabled but hasn't started yet; do nothing.
        if (!phone.isAlreadyStarted()) return;

        if (phone.isNoTouchHide()) {
            boolean isShowError = phone.isShowError();
            if (isShowError && player.isSneaking()) {
                // Dismiss error will set the phone as outdated, no need to reset the counter here.
                phone.dismissError();
            } else if (!isShowError) {
                phone.setNoTouchCounter(0);
            }
            phone.playClickSound(player);
            return;
        }

        if ((phone.getCurrentPage() == null || phone.getCurrentPage().equals("main")) && phone.isBlocked()) {
            if (!player.isSneaking()) return;

            if (phone.isFingerprintEnabled() && !player.getUniqueId().equals(phone.getOwner())) {
                messages.send(player, Messages.Message.NOT_YOUR_PHONE);
                phone.setEnabled(false);
                return;
            }

            phone.setBlocked(false);

            Draw time = phone.getDrawFromPage("main", "time");
            if (time instanceof TextDraw text) {
                text.setCoord(Phone.TOP_LEFT_CORNER);
            }

            phone.reOpenPrevious(player, false);

            // This will only happen if the phone was blocked in the background gallery.

            String previousPage;
            if (!phone.getCurrentPage().equals("settings")
                    || (previousPage = phone.getPreviousPage()) == null
                    || !previousPage.equals("main")) {
                phone.setPreviousPage(phone.getPreviousBlockedPage());
                phone.setPreviousButton(phone.getPreviousBlockedButton());
            }

            phone.setUpdated(false);
            return;
        }

        if (!player.isSneaking()) {
            phone.switchButton(true);
            phone.playClickSound(player);
            return;
        }

        String currentButton = phone.getCurrentButton();
        if (phone.isShowError()) {
            phone.dismissError();
        } else if (currentButton.equalsIgnoreCase("camera")) {
            if (phone.canTakePicture()) {
                phone.takePicture(player);
                phone.setUpdated(false);
            } else {
                phone.showErrorDialog(Config.ERROR_NO_SPACE_AVAILABLE.asString());
            }
        } else if (currentButton.equalsIgnoreCase("gallery")) {
            phone.openGallery(player);
        } else if (currentButton.equalsIgnoreCase("music")) {
            phone.openMusic(player);
        } else if (currentButton.equalsIgnoreCase("weather")) {
            phone.setCurrentPage("weather", "weather-type");
        } else if (currentButton.equalsIgnoreCase("weather-type")) {
            phone.resetNoTouch();
        } else if (currentButton.contains("tab-")) {
            int toUse = Integer.parseInt(currentButton.split("-")[1]);
            if (phone.getCurrentPage().equalsIgnoreCase("music")) {
                phone.handleMusic(player, toUse);
            } else {
                // Is settings.
                Triple<Settings, Settings, Settings> settings = phone.getSettings();

                Settings which = toUse == 1 ? settings.getLeft() : toUse == 2 ? settings.getMiddle() : toUse == 3 ? settings.getRight() : null;
                if (which != null && which.getType() == Settings.SettingType.TOGGLE) {
                    Map<Settings, Object> values = phone.getSettingsValues();

                    boolean newValue = !which.getOrDefault(values).asBoolean();
                    values.put(which, newValue);

                    phone.setUpdated(false);
                    phone.saveConfig();

                    if (which != Settings.SOUND || newValue) {
                        phone.playToggleSound(player, newValue);
                    }

                    // Return to prevent playing click sound.
                    return;
                }

                if (which == Settings.CHANGE_BACKGROUND) {
                    phone.setBackgroundGallery(true);
                    phone.openGallery(player);
                } else if (which == Settings.POWER_OFF) {
                    phone.powerOff();
                }
            }
        } else if (currentButton.equalsIgnoreCase("up")) {
            if (phone.getCurrentPage().equalsIgnoreCase("music")) {
                phone.updateSongs((temp, songs) -> Pair.of(temp.getFirstSongIndex() - 1, temp.getThirdSongIndex()));

                // First song, select down button.
                if (phone.getFirstSongIndex() == 0) {
                    phone.setCurrentButton("down");
                }
            } else {
                // Is settings.
                phone.updateSettings((temp, settings) -> Pair.of(temp.getFirstSettingIndex() - 1, temp.getThirdSettingIndex()));

                // First setting, select down button.
                if (phone.getFirstSettingIndex() == 0) {
                    phone.setCurrentButton("down");
                }
            }
        } else if (currentButton.equalsIgnoreCase("down")) {
            if (phone.getCurrentPage().equalsIgnoreCase("music")) {
                phone.updateSongs((temp, songs) -> Pair.of(temp.getFirstSongIndex() + 1, songs.size()));

                // Last song, select up button.
                if (phone.getThirdSongIndex() == phone.getMusic().size() - 1) {
                    phone.setCurrentButton("up");
                }
            } else {
                phone.updateSettings((temp, settings) -> Pair.of(temp.getFirstSettingIndex() + 1, settings.size()));

                // Last setting, select up button.
                if (phone.getThirdSettingIndex() == Settings.values().length - 1) {
                    phone.setCurrentButton("up");
                }
            }
        } else if (currentButton.equalsIgnoreCase("previous")) {
            // The previous image.
            phone.switchCurrentPicture(true);

            // First page, select next button.
            if (phone.getCurrentPictureIndex() == 0) {
                phone.setCurrentButton("next");
            }
        } else if (currentButton.equalsIgnoreCase("next")) {
            // The next image.
            phone.switchCurrentPicture(false);

            // Last page, select previous button.
            if (phone.getCurrentPictureIndex() == phone.getPictures().size() - 1) {
                phone.setCurrentButton("previous");
            }
        } else if (currentButton.equalsIgnoreCase("house") || currentButton.equalsIgnoreCase("checkmark")) {
            phone.setPictureAsBackground(phone.getCurrentPicture(), true);
        } else if (currentButton.equalsIgnoreCase("trash")) {

            String toDelete = phone.getCurrentPicture();
            File pictureFile = new File(phone.getGalleryFolder(), toDelete);

            if (pictureFile.exists()) {
                try {
                    Files.delete(pictureFile.toPath());

                    Map<String, List<BufferedImage>> pictures = phone.reloadPhonePictures();
                    phone.setPictures(pictures);

                    if (pictures.isEmpty()) {
                        phone.setCurrentPage("main", phone.getLastButtons().get("main"));
                    } else {
                        phone.setGalleryModified(true);
                        phone.openGallery(player);
                    }

                } catch (IOException exception) {
                    exception.printStackTrace();
                }
            } else {
                messages.send(player, Messages.Message.UNKNOWN_ERROR);
            }
        } else if (currentButton.equalsIgnoreCase("settings")) {
            phone.openSettings(player);
        } else {
            phone.showErrorDialog(Config.ERROR_APP_NO_FUNCTION.asString());
        }

        phone.playClickSound(player);
    }

    public void handleRightClick(@NotNull Player player, @NotNull Phone phone) {
        if (phone.isNoTouchHide()) {
            if (!phone.isShowError()) phone.resetNoTouch();
            phone.playClickSound(player);
            return;
        }

        if (!phone.isEnabled() || phone.isBlocked()) return;

        phone.playClickSound(player);

        // Go to previous (main) page.
        if (player.isSneaking() && !phone.isShowError()) {
            phone.reOpenPrevious(player, false);
            return;
        }

        phone.switchButton(false);
    }

    public @Nullable Phone getPhoneData(ItemStack item) {
        if (item == null) return null;

        Integer id = getIDFromPhoneItem(item);
        if (id == null) return null;

        for (Phone phone : phones) {
            if (phone.getView().getId() != id) continue;

            // Init phone again.
            if (!item.equals(phone.getItem())) {
                phone.setItem(item);
                phone.setBuilt(true);
            }
            return phone;
        }

        return null;
    }

    public @Nullable Integer getIDFromPhoneItem(@NotNull ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        return meta != null ? meta.getPersistentDataContainer().get(phoneIDKey, PersistentDataType.INTEGER) : null;
    }

    public void createPhone(@NotNull Player player, Color color) {
        // View will be initialized in Phone#build().
        try {
            Phone phone = new Phone(player.getUniqueId(), this, null, color);
            player.getInventory().addItem(phone.build());
            phones.add(phone);
        } catch (IOException exception) {
            exception.printStackTrace();
        }
    }

    @Contract(pure = true)
    public @NotNull String getMusicFolder() {
        return getDataFolder() + File.separator + "song";
    }

    @Contract(pure = true)
    public @NotNull String getBackgroundFolder() {
        return getDataFolder() + File.separator + "background";
    }
}