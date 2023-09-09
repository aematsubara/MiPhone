package me.matsubara.miphone.listener;

import com.xxmicloxx.NoteBlockAPI.NoteBlockAPI;
import com.xxmicloxx.NoteBlockAPI.event.SongEndEvent;
import com.xxmicloxx.NoteBlockAPI.songplayer.EntitySongPlayer;
import com.xxmicloxx.NoteBlockAPI.songplayer.SongPlayer;
import me.matsubara.miphone.MiPhonePlugin;
import me.matsubara.miphone.phone.Phone;
import me.matsubara.miphone.song.CustomEntitySongPlayer;
import org.bukkit.Material;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryCreativeEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;

public class OtherListener implements Listener {

    private final MiPhonePlugin plugin;

    public OtherListener(MiPhonePlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onInventoryCreative(@NotNull InventoryCreativeEvent event) {
        ItemStack cursor = event.getCursor();
        if (cursor.getType() != Material.FILLED_MAP) return;

        ItemMeta meta = cursor.getItemMeta();
        if (meta == null) return;

        PersistentDataContainer container = meta.getPersistentDataContainer();
        if (!container.has(plugin.getWirelessChargerKey(), PersistentDataType.INTEGER)) return;

        event.setCursor(plugin.getWirelessCharger().getResult());
    }

    @EventHandler
    public void onSongEnd(@NotNull SongEndEvent event) {
        if (!(event.getSongPlayer() instanceof EntitySongPlayer songPlayer)) return;

        for (Phone phone : plugin.getPhones()) {
            if (songPlayer.equals(phone.getSongPlayer())) phone.setCurrentSong(null);
        }
    }

    @EventHandler
    public void onPlayerQuit(@NotNull PlayerQuitEvent event) {
        ArrayList<SongPlayer> songs = NoteBlockAPI.getSongPlayersByPlayer(event.getPlayer());
        if (songs == null) return;

        for (SongPlayer player : songs) {
            if (player instanceof CustomEntitySongPlayer custom) custom.getPhone().setEnabled(false);
        }
    }

    @EventHandler
    public void onPlayerDeath(@NotNull PlayerDeathEvent event) {
        for (ItemStack drop : event.getDrops()) {
            Phone phone = plugin.getPhoneData(drop);
            if (phone != null) phone.setEnabled(false);
        }
    }
}