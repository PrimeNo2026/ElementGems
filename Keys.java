package elementgems;

import org.bukkit.NamespacedKey;
import org.bukkit.plugin.Plugin;

/** Zentrale NamespacedKeys fuer PersistentData. */
public final class Keys {
    public static NamespacedKey GEM;
    public static NamespacedKey SKELETON;
    public static NamespacedKey FIREBALL;
    public static NamespacedKey ARROW;
    public static NamespacedKey DISPLAY;

    private Keys() {}

    public static void init(Plugin plugin) {
        GEM = new NamespacedKey(plugin, "gem_type");
        SKELETON = new NamespacedKey(plugin, "gem_skeleton");
        FIREBALL = new NamespacedKey(plugin, "gem_fireball");
        ARROW = new NamespacedKey(plugin, "gem_arrow");
        DISPLAY = new NamespacedKey(plugin, "gem_display");
    }
}
