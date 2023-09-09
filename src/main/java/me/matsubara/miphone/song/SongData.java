package me.matsubara.miphone.song;

import com.xxmicloxx.NoteBlockAPI.model.Song;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public record SongData(@Nullable String title, @Nullable String author) {

    public boolean isValid() {
        return title != null && !title.isEmpty() && author != null && !author.isEmpty();
    }

    @Contract("_ -> new")
    public static @NotNull SongData fromSong(@NotNull Song song) {
        return new SongData(song.getTitle(), song.getOriginalAuthor());
    }
}