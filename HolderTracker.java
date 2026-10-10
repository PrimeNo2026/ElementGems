package elementgems;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Erfasst, wer welche Gems im Inventar hat: Passiv-Effekte und die schwebenden Gems ueber dem Kopf.
 */
public final class HolderTracker {

    private final ElementGems plugin;
    private final Map<UUID, EnumSet<GemType>> held = new HashMap<>();
    private final Map<UUID, EnumMap<GemType, ItemDisplay>> displays = new HashMap<>();
    private final Set<UUID> queued = new HashSet<>();

    public HolderTracker(ElementGems plugin) {
        this.plugin = plugin;
    }

    public void start() {
        // Reste aus frueheren Laeufen entfernen
        for (World w : Bukkit.getWorlds()) {
            for (ItemDisplay d : w.getEntitiesByClass(ItemDisplay.class)) {
                if (d.getPersistentDataContainer().has(Keys.DISPLAY, PersistentDataType.BYTE)) d.remove();
            }
        }
        Bukkit.getScheduler().runTaskTimer(plugin, this::scanAll, 10L, 10L);
        Bukkit.getScheduler().runTaskTimer(plugin, this::follow, 1L, 1L);
    }

    public void stop() {
        for (EnumMap<GemType, ItemDisplay> m : displays.values()) {
            for (ItemDisplay d : m.values()) d.remove();
        }
        displays.clear();
        held.clear();
    }

    /** Scan im naechsten Tick (nach Pickup, Drop, Inventarklick, Join ...). */
    public void refreshSoon(Player p) {
        UUID id = p.getUniqueId();
        if (!queued.add(id)) return;
        Bukkit.getScheduler().runTask(plugin, () -> {
            queued.remove(id);
            if (p.isOnline()) scan(p);
        });
    }

    public void onQuit(Player p) {
        removeDisplays(p.getUniqueId());
        held.remove(p.getUniqueId());
    }

    private void removeDisplays(UUID id) {
        EnumMap<GemType, ItemDisplay> m = displays.remove(id);
        if (m == null) return;
        for (ItemDisplay d : m.values()) d.remove();
    }

    private void scanAll() {
        for (Player p : Bukkit.getOnlinePlayers()) scan(p);
        Iterator<UUID> it = displays.keySet().iterator();
        while (it.hasNext()) {
            UUID id = it.next();
            if (Bukkit.getPlayer(id) == null) {
                for (ItemDisplay d : displays.get(id).values()) d.remove();
                it.remove();
            }
        }
    }

    private void scan(Player p) {
        UUID id = p.getUniqueId();
        boolean inactive = p.isDead() || p.getGameMode() == GameMode.SPECTATOR;
        EnumSet<GemType> now = inactive ? EnumSet.noneOf(GemType.class) : GemItems.heldTypes(p);
        EnumSet<GemType> prev = held.getOrDefault(id, EnumSet.noneOf(GemType.class));
        for (GemType g : prev) if (!now.contains(g)) clearPassive(p, g);
        for (GemType g : now) applyPassive(p, g);
        held.put(id, now);
        syncDisplays(p, p.isInvisible() ? EnumSet.noneOf(GemType.class) : now);
    }

    // ------------------------------------------------------------ Passive Effekte

    private PotionEffectType passiveType(GemType g) {
        return switch (g) {
            case RED -> PotionEffectType.FIRE_RESISTANCE;
            case BLUE -> PotionEffectType.WATER_BREATHING;
            case YELLOW -> PotionEffectType.HASTE;
            case PURPLE -> null;
        };
    }

    private int passiveAmp(GemType g) {
        return g == GemType.YELLOW ? Math.max(0, plugin.getConfig().getInt("yellow.haste-level", 2) - 1) : 0;
    }

    private void applyPassive(Player p, GemType g) {
        if (g == GemType.PURPLE) {
            p.removePotionEffect(PotionEffectType.POISON);
            p.removePotionEffect(PotionEffectType.WITHER);
            return;
        }
        PotionEffectType type = passiveType(g);
        int amp = passiveAmp(g);
        PotionEffect cur = p.getPotionEffect(type);
        // Staerkere oder laenger laufende fremde Effekte nicht ueberschreiben
        if (cur != null && cur.getDuration() >= 0 && (cur.getAmplifier() > amp
                || (cur.getAmplifier() == amp && cur.getDuration() > 60))) return;
        if (cur != null && cur.getDuration() < 0 && cur.getAmplifier() >= amp) return; // unendlicher Effekt
        p.addPotionEffect(new PotionEffect(type, 100, amp, true, false, true));
    }

    private void clearPassive(Player p, GemType g) {
        PotionEffectType type = passiveType(g);
        if (type == null) return;
        PotionEffect cur = p.getPotionEffect(type);
        if (cur != null && cur.isAmbient() && !cur.hasParticles() && cur.getAmplifier() == passiveAmp(g)) {
            p.removePotionEffect(type);
        }
    }

    // ------------------------------------------------------------ Schwebende Gems

    private Location displayLocation(Player p) {
        double off = plugin.getConfig().getDouble("display.height-offset", 0.65);
        Location l = p.getLocation().add(0, p.getHeight() + off, 0);
        l.setYaw(0f);
        l.setPitch(0f);
        return l;
    }

    private void syncDisplays(Player p, EnumSet<GemType> want) {
        UUID id = p.getUniqueId();
        EnumMap<GemType, ItemDisplay> map = displays.computeIfAbsent(id, k -> new EnumMap<>(GemType.class));
        boolean changed = false;

        Iterator<Map.Entry<GemType, ItemDisplay>> it = map.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<GemType, ItemDisplay> en = it.next();
            if (!want.contains(en.getKey()) || !en.getValue().isValid()) {
                en.getValue().remove();
                it.remove();
                changed = true;
            }
        }
        for (GemType g : want) {
            if (!map.containsKey(g)) {
                map.put(g, spawnDisplay(p, g));
                changed = true;
            }
        }
        if (changed) layout(map);
        if (map.isEmpty()) displays.remove(id);
    }

    private ItemDisplay spawnDisplay(Player p, GemType g) {
        return p.getWorld().spawn(displayLocation(p), ItemDisplay.class, d -> {
            d.setItemStack(GemItems.create(g));
            d.setBillboard(Display.Billboard.CENTER);
            d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
            d.setBrightness(new Display.Brightness(15, 15));
            d.setTeleportDuration(2);
            d.setPersistent(false);
            d.setInvulnerable(true);
            d.setGravity(false);
            d.getPersistentDataContainer().set(Keys.DISPLAY, PersistentDataType.BYTE, (byte) 1);
        });
    }

    /** Mehrere Gems nebeneinander anordnen. */
    private void layout(EnumMap<GemType, ItemDisplay> map) {
        float scale = (float) plugin.getConfig().getDouble("display.scale", 0.55);
        int n = map.size();
        int i = 0;
        for (ItemDisplay d : map.values()) {
            float x = (float) ((i - (n - 1) / 2.0) * 0.5);
            d.setTransformation(new Transformation(new Vector3f(x, 0f, 0f), new AxisAngle4f(),
                    new Vector3f(scale, scale, scale), new AxisAngle4f()));
            i++;
        }
    }

    private void follow() {
        if (displays.isEmpty()) return;
        for (Map.Entry<UUID, EnumMap<GemType, ItemDisplay>> en : displays.entrySet()) {
            Player p = Bukkit.getPlayer(en.getKey());
            if (p == null) continue;
            Location l = displayLocation(p);
            for (ItemDisplay d : en.getValue().values()) {
                if (d.isValid()) d.teleport(l);
            }
        }
    }
}
