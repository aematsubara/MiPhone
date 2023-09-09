package me.matsubara.miphone.phone.render;

import lombok.Getter;
import lombok.Setter;
import me.matsubara.miphone.phone.Phone;
import org.bukkit.map.MapCanvas;

import java.util.function.Predicate;

@Getter
@Setter
public abstract class Draw {

    protected final String name;
    protected Coord coord;
    protected final Predicate<Phone> drawCondition;

    public Draw(String name, Coord coord, Predicate<Phone> drawCondition) {
        this.name = name;
        this.coord = coord;
        this.drawCondition = drawCondition;
    }

    public int getX() {
        return coord.x();
    }

    public int getY() {
        return coord.y();
    }

    public boolean canBeDrawn(Phone phone) {
        return drawCondition.test(phone);
    }

    public boolean handleRender(Phone phone, MapCanvas canvas) {
        return drawCondition.and(this::handlePlay).test(phone);
    }

    private boolean handlePlay(Phone phone) {
        if (!name.contains("-empty-")) return true;

        if (name.endsWith("icon")) {
            String emptyIcon = phone.getEmptyMusicIcon();
            if (emptyIcon == null) {
                phone.setEmptyMusicIcon(name);
                return true;
            } else {
                return emptyIcon.equals(name);
            }
        }

        String emptyText = phone.getEmptyMusicText();
        if (emptyText == null) {
            phone.setEmptyMusicText(name);
            return true;
        }

        return emptyText.equals(name);
    }
}