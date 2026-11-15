package dev.magicrecoil.aiming;

import org.bukkit.entity.Player;

/** Optional client FOV effect, separate from real movement attributes and native spyglass scoping. */
public interface ZoomAdapter {
    void zoom(Player player, double factor);
    void clear(Player player);
    ZoomAdapter NONE = new ZoomAdapter() {
        public void zoom(Player player, double factor) {}
        public void clear(Player player) {}
    };
}
