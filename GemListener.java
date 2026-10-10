package elementgems;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Allay;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.ItemSpawnEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.inventory.InventoryPickupItemEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BundleMeta;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * Einzigartigkeit der Gem-Items: nur im Spielerinventar lagerbar, beim Tod normal gedroppt,
 * Zerstoerung (Lava, Void, Feuer, Explosion ...) wird erkannt.
 */
public final class GemListener implements Listener {

    /** Merkt sich Gem-Items am Boden samt letztem bekanntem Chunk. */
    private static final class Rec {
        Item item;
        GemType type;
        World world;
        int cx, cz;
    }

    private final ElementGems plugin;
    private final Map<UUID, Rec> tracked = new HashMap<>();

    public GemListener(ElementGems plugin) {
        this.plugin = plugin;
    }

    public void start() {
        for (World w : Bukkit.getWorlds()) {
            for (Item it : w.getEntitiesByClass(Item.class)) track(it);
        }
        Bukkit.getScheduler().runTaskTimer(plugin, this::audit, 20L, 20L);
    }

    private void track(Item item) {
        GemType t = GemItems.getType(item.getItemStack());
        if (t == null) return;
        item.setUnlimitedLifetime(true); // kein Despawn nach 5 Minuten
        item.setGlowing(true);
        Rec r = new Rec();
        r.item = item;
        r.type = t;
        r.world = item.getWorld();
        r.cx = item.getLocation().getBlockX() >> 4;
        r.cz = item.getLocation().getBlockZ() >> 4;
        tracked.put(item.getUniqueId(), r);
    }

    @EventHandler
    public void onItemSpawn(ItemSpawnEvent e) {
        track(e.getEntity());
    }

    @EventHandler
    public void onEntitiesLoad(EntitiesLoadEvent e) {
        for (Entity en : e.getEntities()) {
            if (en instanceof Item it) track(it);
        }
    }

    /** Verschwindet ein Gem-Item, wird geprueft, ob es zerstoert wurde. */
    private void audit() {
        Iterator<Rec> it = tracked.values().iterator();
        while (it.hasNext()) {
            Rec r = it.next();
            if (r.item.isValid()) {
                r.world = r.item.getWorld();
                r.cx = r.item.getLocation().getBlockX() >> 4;
                r.cz = r.item.getLocation().getBlockZ() >> 4;
                continue;
            }
            it.remove();
            if (!r.world.isChunkLoaded(r.cx, r.cz)) continue; // Chunk wurde nur entladen
            final GemType type = r.type;
            Bukkit.getScheduler().runTaskLater(plugin, () -> checkLost(type), 10L);
        }
    }

    /** Gem ist nur dann zerstoert, wenn ihn weder ein Spieler hat noch ein Item in der Welt liegt. */
    private void checkLost(GemType type) {
        if (plugin.manager().status(type) != GemManager.Status.OWNED) return;
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (GemItems.holds(p, type)) return;
        }
        for (World w : Bukkit.getWorlds()) {
            for (Item i : w.getEntitiesByClass(Item.class)) {
                if (GemItems.getType(i.getItemStack()) == type) return;
            }
        }
        plugin.manager().onGemDestroyed(type);
    }

    @EventHandler(ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent e) {
        if (GemItems.getType(e.getItem().getItemStack()) == null) return;
        if (e.getEntity() instanceof Player p) {
            plugin.tracker().refreshSoon(p);
        } else {
            e.setCancelled(true); // Mobs duerfen keine Gems aufheben
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onHopperPickup(InventoryPickupItemEvent e) {
        if (GemItems.getType(e.getItem().getItemStack()) != null) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onHopperMove(InventoryMoveItemEvent e) {
        if (GemItems.getType(e.getItem()) != null) e.setCancelled(true);
    }

    @EventHandler
    public void onDrop(PlayerDropItemEvent e) {
        plugin.tracker().refreshSoon(e.getPlayer());
    }

    // ------------------------------------------------------------ Gems nur im Spielerinventar

    private static boolean isGem(ItemStack s) {
        return GemItems.getType(s) != null;
    }

    private static boolean isBundle(ItemStack s) {
        return s != null && s.hasItemMeta() && s.getItemMeta() instanceof BundleMeta;
    }

    @EventHandler(ignoreCancelled = true)
    public void onClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        Inventory top = e.getView().getTopInventory();
        Inventory clicked = e.getClickedInventory();
        ItemStack cursor = e.getCursor();
        ItemStack current = e.getCurrentItem();

        // Nicht in Bundles stecken
        if ((isGem(cursor) && isBundle(current)) || (isGem(current) && isBundle(cursor))) {
            e.setCancelled(true);
            return;
        }
        if (clicked != null && clicked == top) {
            if (isGem(cursor)) {
                e.setCancelled(true);
                return;
            }
            if (e.getClick() == ClickType.NUMBER_KEY && e.getHotbarButton() >= 0
                    && isGem(p.getInventory().getItem(e.getHotbarButton()))) {
                e.setCancelled(true);
                return;
            }
            if (e.getClick() == ClickType.SWAP_OFFHAND && isGem(p.getInventory().getItemInOffHand())) {
                e.setCancelled(true);
            }
        } else if (clicked != null && e.isShiftClick() && isGem(current) && top.getType() != InventoryType.CRAFTING) {
            e.setCancelled(true); // Shift-Klick wuerde den Gem in den geoeffneten Behaelter schieben
        }
        plugin.tracker().refreshSoon(p);
    }

    @EventHandler(ignoreCancelled = true)
    public void onDrag(InventoryDragEvent e) {
        if (!isGem(e.getOldCursor())) return;
        int topSize = e.getView().getTopInventory().getSize();
        for (int raw : e.getRawSlots()) {
            if (raw < topSize) {
                e.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onInteractEntity(PlayerInteractEntityEvent e) {
        Entity t = e.getRightClicked();
        if (!(t instanceof ItemFrame || t instanceof ArmorStand || t instanceof Allay)) return;
        if (isGem(e.getPlayer().getInventory().getItem(e.getHand()))) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onArmorStand(PlayerArmorStandManipulateEvent e) {
        if (isGem(e.getPlayerItem())) e.setCancelled(true);
    }

    // ------------------------------------------------------------ Join / Quit / Respawn

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        plugin.tracker().refreshSoon(e.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        plugin.tracker().onQuit(e.getPlayer());
    }

    @EventHandler
    public void onDeath(org.bukkit.event.entity.PlayerDeathEvent e) {
        plugin.tracker().refreshSoon(e.getEntity()); // Gems fallen normal zu Boden
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent e) {
        plugin.tracker().refreshSoon(e.getPlayer());
    }
}
