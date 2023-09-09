package me.matsubara.miphone.runnable;

import me.matsubara.miphone.MiPhonePlugin;
import me.matsubara.miphone.file.Config;
import me.matsubara.miphone.file.Messages;
import me.matsubara.miphone.phone.Crack;
import me.matsubara.miphone.phone.Phone;
import me.matsubara.miphone.phone.render.Coord;
import org.apache.commons.lang3.RandomUtils;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Item;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

public class PhoneChecker extends BukkitRunnable {

    private final MiPhonePlugin plugin;

    public PhoneChecker(MiPhonePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void run() {
        handleCharging();
        handleDroppedPhones();
    }

    private void handleCharging() {
        Set<ItemFrame> charging = plugin.getChargingPhones();
        if (charging.isEmpty()) return;

        Iterator<ItemFrame> iterator = charging.iterator();
        while (iterator.hasNext()) {
            ItemFrame frame = iterator.next();

            if (!frame.isValid()) {
                iterator.remove();
                continue;
            }

            PersistentDataContainer container = frame.getPersistentDataContainer();
            if (!container.has(plugin.getWirelessChargerKey(), PersistentDataType.INTEGER)) {
                iterator.remove();
                continue;
            }

            Phone phone = plugin.getPhoneData(frame.getItem());
            if (phone == null) {
                iterator.remove();
                continue;
            }

            phone.increaseBattery(Config.BATTERY_INCREASE_RATE.asInt());
        }
    }

    private void handleDroppedPhones() {
        Map<Item, Integer> phones = plugin.getDroppedPhones();
        if (phones.isEmpty()) return;

        Iterator<Map.Entry<Item, Integer>> iterator = phones.entrySet().iterator();

        while (iterator.hasNext()) {
            Map.Entry<Item, Integer> entry = iterator.next();
            Item item = entry.getKey();
            Integer droppedY = entry.getValue();

            if (!item.isValid()) {
                iterator.remove();
                continue;
            }

            Block droppedAtBlock = item.getLocation().getBlock();

            if (!item.isOnGround()) {
                if (droppedAtBlock.isLiquid() || droppedAtBlock.getType() == Material.COBWEB) {
                    iterator.remove();
                }
                continue;
            }

            iterator.remove();

            // Max of 6 cracks, on per each available.
            Phone phone = plugin.getPhoneData(item.getItemStack());
            if (phone == null) continue;

            // Phone can't be damaged if being charged.
            if (phone.isCharging()) {
                continue;
            }

            Block finalBlock;

            Material droppedAtType = droppedAtBlock.getType();
            if (droppedAtType == Material.BIG_DRIPLEAF
                    || droppedAtType == Material.HONEY_BLOCK
                    || Tag.WOOL_CARPETS.isTagged(droppedAtType)
                    || Tag.BEDS.isTagged(droppedAtType)) {
                finalBlock = droppedAtBlock;
            } else {
                finalBlock = droppedAtBlock.getRelative(BlockFace.DOWN);
            }

            if (plugin.getSafeDropBlocks().contains(finalBlock.getType())) {
                continue;
            }

            int dropDistance = droppedY - droppedAtBlock.getY() + 1;

            int safeDistance = Config.SAFE_PHONE_DROP_DISTANCE.asInt();
            if (dropDistance <= safeDistance) continue;

            double chanceOfCrack = Math.min((dropDistance - safeDistance) * Config.CRACK_CHANCE_PER_DROP_DISTANCE.asDouble(), 1.0d);
            if (Math.random() >= chanceOfCrack) continue;

            which:
            for (int i = 1; i <= 6; i++) {
                for (Crack crack : phone.getCracks()) {
                    if (i == crack.which()) continue which;
                }

                if (!createCrack(phone, i)) continue;

                phone.playSound(null, item.getLocation().clone().add(0.0d, 0.1d, 0.0d), "sounds.crack", Sound.BLOCK_GLASS_BREAK);
                break;
            }
        }
    }

    private boolean createCrack(Phone phone, int which) {
        InputStream crackResource = plugin.getResource("crack/" + which + ".png");
        if (crackResource == null) return false;

        try {
            BufferedImage image = ImageIO.read(crackResource);

            int x = RandomUtils.nextInt(6, 122 - image.getWidth());
            int y = RandomUtils.nextInt(6, 122 - image.getHeight());

            phone.getCracks().add(new Crack(which, new Coord(x, y)));
            phone.setUpdated(false);

            phone.saveConfig();

            Player owner = phone.getOwnerAsPlayer();
            if (owner != null) plugin.getMessages().send(owner, Messages.Message.BROKEN);

            return true;
        } catch (IOException exception) {
            exception.printStackTrace();
            return false;
        }
    }
}