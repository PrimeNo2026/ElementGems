package elementgems;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

public final class GemItems {
    private static final Material BASE = Material.AMETHYST_SHARD;

    private GemItems() {}

    private static Component line(String text, TextColor color) {
        return Component.text(text, color).decoration(TextDecoration.ITALIC, false);
    }

    public static ItemStack create(GemType t) {
        ItemStack item = new ItemStack(BASE);
        ItemMeta m = item.getItemMeta();
        m.displayName(line(t.displayName, t.color).decoration(TextDecoration.BOLD, true));
        List<Component> lore = new ArrayList<>();
        lore.add(line("Rechtsklick: " + t.ability1, NamedTextColor.GRAY));
        lore.add(line("Shift + Rechtsklick: " + t.ability2, NamedTextColor.GRAY));
        lore.add(line("Passiv: " + t.passive, NamedTextColor.GRAY));
        if (t == GemType.BLUE) lore.add(line("Bogen/Armbrust ohne Pfeile: unendlich Wasserpfeile", NamedTextColor.GRAY));
        lore.add(Component.empty());
        lore.add(line("Einzigartig - es gibt nur dieses eine Exemplar", NamedTextColor.DARK_GRAY));
        m.lore(lore);
        NamespacedKey model = NamespacedKey.fromString("elementgems:" + t.id + "_gem");
        if (model != null) m.setItemModel(model);
        m.setMaxStackSize(1);
        m.setEnchantmentGlintOverride(true);
        m.getPersistentDataContainer().set(Keys.GEM, PersistentDataType.STRING, t.id);
        item.setItemMeta(m);
        return item;
    }

    /** Gem-Typ eines Items oder null, wenn es kein Gem ist. */
    public static GemType getType(ItemStack item) {
        if (item == null || item.getType() != BASE || !item.hasItemMeta()) return null;
        String id = item.getItemMeta().getPersistentDataContainer().get(Keys.GEM, PersistentDataType.STRING);
        return id == null ? null : GemType.fromId(id);
    }

    /** Alle Gem-Typen, die der Spieler irgendwo bei sich hat (Inventar, Hotbar, Offhand, Cursor). */
    public static EnumSet<GemType> heldTypes(Player p) {
        EnumSet<GemType> set = EnumSet.noneOf(GemType.class);
        for (ItemStack it : p.getInventory().getContents()) {
            GemType t = getType(it);
            if (t != null) set.add(t);
        }
        GemType off = getType(p.getInventory().getItemInOffHand());
        if (off != null) set.add(off);
        GemType cur = getType(p.getItemOnCursor());
        if (cur != null) set.add(cur);
        return set;
    }

    public static boolean holds(Player p, GemType t) {
        return heldTypes(p).contains(t);
    }

    /** Entfernt alle Gems dieses Typs aus dem Inventar des Spielers. */
    public static void removeFrom(Player p, GemType g) {
        PlayerInventory inv = p.getInventory();
        for (int i = 0; i < inv.getSize(); i++) {
            if (getType(inv.getItem(i)) == g) inv.setItem(i, null);
        }
        if (getType(p.getItemOnCursor()) == g) p.setItemOnCursor(null);
    }
}
