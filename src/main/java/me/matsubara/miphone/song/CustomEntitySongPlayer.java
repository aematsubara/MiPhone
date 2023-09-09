package me.matsubara.miphone.song;

import com.xxmicloxx.NoteBlockAPI.model.Song;
import com.xxmicloxx.NoteBlockAPI.songplayer.EntitySongPlayer;
import lombok.Getter;
import me.matsubara.miphone.phone.Phone;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

public class CustomEntitySongPlayer extends EntitySongPlayer {

    private final @Getter Phone phone;

    public CustomEntitySongPlayer(Song song, Phone phone) {
        super(song);
        this.phone = phone;
    }

    @Override
    public void playTick(Player player, int tick) {
        super.playTick(player, tick);

        PlayerInventory inventory = player.getInventory();
        ItemStack held = inventory.getItem(inventory.getHeldItemSlot());

        Phone temp = phone.getPlugin().getPhoneData(held);
        if (temp != null && temp.equals(phone)) return;

        phone.setEnabled(false);
    }
}