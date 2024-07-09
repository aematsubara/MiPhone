package me.matsubara.miphone.listener;

import com.cryptomorin.xseries.reflection.XReflection;
import com.google.common.base.Preconditions;
import me.matsubara.miphone.MiPhonePlugin;
import me.matsubara.miphone.file.Messages;
import me.matsubara.miphone.phone.Phone;
import me.matsubara.miphone.util.ItemBuilder;
import me.matsubara.miphone.util.PluginUtils;
import me.matsubara.miphone.util.config.ConfigFileUtils;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.Rotation;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.hanging.HangingBreakEvent;
import org.bukkit.event.hanging.HangingPlaceEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.server.MapInitializeEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.MapMeta;
import org.bukkit.map.MapCanvas;
import org.bukkit.map.MapRenderer;
import org.bukkit.map.MapView;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.lang.invoke.MethodHandle;

public final class EventListener implements Listener {

    private final MiPhonePlugin plugin;
    private final BufferedImage wireless;

    private static final MethodHandle RENDER;

    static {
        @SuppressWarnings("deprecation") Class<?> CRAFT_MAP_VIEW_CLAZZ = XReflection.getCraftClass("map.CraftMapView");
        Preconditions.checkNotNull(CRAFT_MAP_VIEW_CLAZZ);

        @SuppressWarnings("deprecation") Class<?> CRAFT_PLAYER_CLAZZ = XReflection.getCraftClass("entity.CraftPlayer");
        Preconditions.checkNotNull(CRAFT_PLAYER_CLAZZ);

        RENDER = PluginUtils.getMethod(CRAFT_MAP_VIEW_CLAZZ, "render", CRAFT_PLAYER_CLAZZ);
    }

    public EventListener(@NotNull MiPhonePlugin plugin) {
        this.plugin = plugin;
        this.wireless = initWireless();
    }

    private @Nullable BufferedImage initWireless() {
        InputStream wireless = plugin.getResource("wireless.png");
        if (wireless == null) return null;

        try {
            return ImageIO.read(wireless);
        } catch (IOException exception) {
            exception.printStackTrace();
            return null;
        }
    }

    @EventHandler
    public void onPlayerItemHeld(@NotNull PlayerItemHeldEvent event) {
        int previousSlot = event.getPreviousSlot();
        if (previousSlot == event.getNewSlot()) return;

        Player player = event.getPlayer();

        ItemStack item = player.getInventory().getItem(previousSlot);
        if (item == null) return;

        // Disable phone when switching item.
        Phone phone = plugin.getPhoneData(item);
        if (phone != null) phone.setEnabled(false);
    }

    @EventHandler
    public void onPlayerInteract(@NotNull PlayerInteractEvent event) {
        // Can only be used in the main hand.
        EquipmentSlot hand = event.getHand();
        if (hand == null || hand == EquipmentSlot.OFF_HAND) return;

        Player player = event.getPlayer();

        ItemStack item = player.getInventory().getItem(hand);
        if (item == null) return;

        Phone phone = plugin.getPhoneData(item);
        if (phone == null) return;

        event.setCancelled(true);
        event.setUseInteractedBlock(Event.Result.DENY);
        event.setUseItemInHand(Event.Result.DENY);

        Action action = event.getAction();
        if (action == Action.LEFT_CLICK_AIR || action == Action.LEFT_CLICK_BLOCK) {
            plugin.handleLeftClick(player, phone);
        } else {
            plugin.handleRightClick(player, phone);
        }
    }

    @EventHandler
    public void onEntitiesLoad(@NotNull EntitiesLoadEvent event) {
        for (Entity entity : event.getEntities()) {
            if (!(entity instanceof ItemFrame frame) || !isFrameCharger(frame)) continue;

            MapView view;
            if (frame.getItem().getItemMeta() instanceof MapMeta meta
                    && (view = meta.getMapView()) != null
                    && plugin.isPhone(view.getId())) {

                Phone phone = plugin.getPhoneData(frame.getItem());
                if (phone != null) {
                    phone.setChargingAt(frame);
                    plugin.getChargingPhones().add(frame);
                }
                continue;
            }

            setChargerAsFrameRenderer(frame);
        }
    }

    @EventHandler
    public void onMapInitialize(@NotNull MapInitializeEvent event) {
        MapView view = event.getMap();
        if (!plugin.isPhone(view.getId())) return;

        try {
            Phone phone = new Phone(null, plugin, view, null);

            view.setScale(MapView.Scale.NORMAL);
            view.getRenderers().forEach(view::removeRenderer);
            view.addRenderer(phone);

            plugin.getPhones().add(phone);
        } catch (IOException exception) {
            exception.printStackTrace();
        }
    }

    @EventHandler
    public void onInventoryClick(@NotNull InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;

        Inventory inventory = event.getClickedInventory();
        if (inventory == null) return;

        // Disable phones when clicked on them.

        Phone inCursor = plugin.getPhoneData(event.getCursor());
        if (inCursor != null) inCursor.setEnabled(false);

        Phone inCurrent = plugin.getPhoneData(event.getCurrentItem());
        if (inCurrent != null) inCurrent.setEnabled(false);
    }

    @EventHandler
    public void onEntityDamageByEntity(@NotNull EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof ItemFrame frame)) return;
        if (!(event.getDamager() instanceof Player player)) return;

        ItemStack item = frame.getItem();

        Phone phone = plugin.getPhoneData(item);
        if (phone != null) {
            event.setCancelled(true);

            // Drop the phone no matter what gamemode the player is because we cancelled the event; the item won't drop.
            player.getWorld().dropItem(frame.getLocation(), reApplyNameToPhone(item));

            phone.setChargingAt(null);

            if (isFrameCharger(frame)) {
                setChargerAsFrameRenderer(frame);
            }
            return;
        }

        if (!isFrameCharger(frame)) return;

        event.setCancelled(true);
        frame.getWorld().dropItem(frame.getLocation(), plugin.getWirelessCharger().getResult());
        frame.remove();
    }

    private ItemStack reApplyNameToPhone(@NotNull ItemStack item) {
        MapView view;
        if (item.getItemMeta() instanceof MapMeta meta && (view = meta.getMapView()) != null) {
            return ConfigFileUtils.getItem(plugin, "phone", null)
                    .setType(Material.FILLED_MAP)
                    .setData(plugin.getPhoneIDKey(), PersistentDataType.INTEGER, view.getId())
                    .setMapView(view)
                    .build();
        }
        return item;
    }

    @EventHandler(ignoreCancelled = true)
    public void onHangingPlace(@NotNull HangingPlaceEvent event) {
        if (!(event.getEntity() instanceof ItemFrame frame)) return;

        ItemStack item = event.getItemStack();
        if (item == null) return;

        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;

        PersistentDataContainer container = meta.getPersistentDataContainer();
        if (!container.has(plugin.getWirelessChargerKey(), PersistentDataType.INTEGER)) return;

        Player player = event.getPlayer();
        if (event.getBlockFace() != BlockFace.UP) {
            event.setCancelled(true);
            if (player != null) plugin.getMessages().send(player, Messages.Message.CHARGER_AT_TOP);
            return;
        }

        // This frame is a wireless charger.
        frame.getPersistentDataContainer().set(plugin.getWirelessChargerKey(), PersistentDataType.INTEGER, 1);

        setChargerAsFrameRenderer(frame);

        EquipmentSlot hand;
        if (player != null
                && player.getGameMode() == GameMode.CREATIVE
                && (hand = event.getHand()) != null) {
            item.setAmount(item.getAmount() - 1);
            player.getInventory().setItem(hand, item);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onHangingBreak(@NotNull HangingBreakEvent event) {
        if (!(event.getEntity() instanceof ItemFrame frame)) return;
        if (!isFrameCharger(frame)) return;

        event.setCancelled(true);

        // If the charger was charging a phone, drop the phone.
        Phone phone = plugin.getPhoneData(frame.getItem());
        if (phone != null) {
            frame.getWorld().dropItem(frame.getLocation(), reApplyNameToPhone(frame.getItem()));
            phone.setChargingAt(null);
        }

        // Finally, remove the frame from the world and drop the wireless charger.
        frame.getWorld().dropItem(frame.getLocation(), plugin.getWirelessCharger().getResult());
        frame.remove();
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlayerInteractEntity(@NotNull PlayerInteractEntityEvent event) {
        if (!(event.getRightClicked() instanceof ItemFrame frame)) return;

        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItem(event.getHand());

        Phone phone = plugin.getPhoneData(item);
        if (phone == null) {
            if (isFrameCharger(frame)) {
                // Prevent rotations.
                event.setCancelled(true);
            }
            return;
        }

        event.setCancelled(true);

        Messages messages = plugin.getMessages();

        if (!isFrameCharger(frame)) {
            messages.send(player, Messages.Message.ONLY_ON_CHARGERS);
            return;
        }

        Phone phoneInFrame = plugin.getPhoneData(frame.getItem());
        if (phoneInFrame != null) {
            messages.send(player, Messages.Message.CHARGER_IN_USE);
            return;
        }

        for (ItemFrame otherFrame : plugin.getChargingPhones()) {
            Phone otherPhone = plugin.getPhoneData(otherFrame.getItem());
            if (otherPhone != null && otherPhone.equals(phone)) {
                messages.send(player, Messages.Message.PHONE_ALREADY_CHARGING);
                return;
            }
        }

        ItemStack temp = item.clone();
        ItemMeta meta = temp.getItemMeta();
        if (meta != null) {
            // So the display name of the phone isn't shown in the frame.
            meta.setDisplayName(null);
            temp.setItemMeta(meta);
        }

        item.setAmount(item.getAmount() - 1);

        // Replace wireless charger image with phone renderer.
        frame.setItem(temp, true);

        phone.setEnabled(false);
        phone.setChargingAt(frame);

        // Here, we need to force the render of the map; otherwise it'll take some extra seconds.
        try {
            if (RENDER != null) RENDER.invoke(phone.getView(), player);
        } catch (Throwable throwable) {
            throwable.printStackTrace();
        }

        plugin.getChargingPhones().add(frame);
    }

    @EventHandler
    public void onPlayerDropItem(@NotNull PlayerDropItemEvent event) {
        Item item = event.getItemDrop();

        // Disable phone when switching item and crack if needed.
        Phone phone = plugin.getPhoneData(item.getItemStack());
        if (phone == null) return;

        phone.setEnabled(false);

        // This phone can't be worst.
        if (phone.enoughCracks()) return;

        plugin.getDroppedPhones().put(item, item.getLocation().getBlockY());
    }

    private boolean isFrameCharger(@NotNull ItemFrame frame) {
        return frame.getPersistentDataContainer().has(plugin.getWirelessChargerKey(), PersistentDataType.INTEGER);
    }

    private void setChargerAsFrameRenderer(@NotNull ItemFrame frame) {
        MapView view = Bukkit.createMap(Bukkit.getWorlds().get(0));
        view.setScale(MapView.Scale.NORMAL);
        view.getRenderers().forEach(view::removeRenderer);
        view.addRenderer(new MapRenderer() {
            private boolean rendered;

            @Override
            public void render(@NotNull MapView map, @NotNull MapCanvas canvas, @NotNull Player player) {
                if (rendered) return;
                if (wireless != null) canvas.drawImage(0, 0, wireless);
                rendered = true;
            }
        });

        // Show wireless charger image.
        frame.setRotation(Rotation.NONE);
        frame.setItem(new ItemBuilder(Material.FILLED_MAP)
                .setMapView(view)
                .setData(plugin.getWirelessChargerKey(), PersistentDataType.INTEGER, 1)
                .build(), true);
    }
}