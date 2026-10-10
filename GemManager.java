package elementgems;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Chest;
import org.bukkit.block.data.BlockData;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Zustand der vier einzigartigen Gems.
 * AVAILABLE = Aufgabe offen, OWNED = Gem existiert (Spieler oder Boden),
 * PENDING = Gem wurde zerstoert, Kiste erscheint am naechsten Minecraft-Tag,
 * CHEST = Kiste mit Gem steht in der Welt.
 */
public final class GemManager {

    public enum Status { AVAILABLE, OWNED, PENDING, CHEST }

    private final ElementGems plugin;
    private final File file;
    private final EnumMap<GemType, Status> states = new EnumMap<>(GemType.class);
    private final EnumMap<GemType, int[]> chestPos = new EnumMap<>(GemType.class); // Mitte der Plattform {x, y, z}
    private final Map<UUID, Set<Integer>> redProgress = new HashMap<>();
    private final EnumSet<GemType> spawning = EnumSet.noneOf(GemType.class);
    private final EnumSet<GemType> dayReached = EnumSet.noneOf(GemType.class);
    private final EnumMap<GemType, Long> nextTry = new EnumMap<>(GemType.class);
    private final Random rnd = new Random();
    private int dragonKills = -1;
    private long lastTime = -1;

    public GemManager(ElementGems plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "data.yml");
        load();
    }

    // ------------------------------------------------------------ Persistenz

    private void load() {
        YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
        for (GemType g : GemType.values()) {
            Status s;
            try {
                s = Status.valueOf(y.getString("gems." + g.id + ".status", "AVAILABLE"));
            } catch (IllegalArgumentException ex) {
                s = Status.AVAILABLE;
            }
            states.put(g, s);
            chestPos.put(g, new int[]{y.getInt("gems." + g.id + ".x"), y.getInt("gems." + g.id + ".y"), y.getInt("gems." + g.id + ".z")});
            if (s == Status.PENDING) dayReached.remove(g);
        }
        dragonKills = y.getInt("dragon-kills", -1);
        ConfigurationSection sec = y.getConfigurationSection("progress.red");
        if (sec != null) {
            for (String key : sec.getKeys(false)) {
                try {
                    redProgress.put(UUID.fromString(key), new HashSet<>(y.getIntegerList("progress.red." + key)));
                } catch (IllegalArgumentException ignored) {
                    // kaputter Eintrag
                }
            }
        }
    }

    public void save() {
        YamlConfiguration y = new YamlConfiguration();
        for (GemType g : GemType.values()) {
            int[] p = chestPos.get(g);
            y.set("gems." + g.id + ".status", states.get(g).name());
            y.set("gems." + g.id + ".x", p[0]);
            y.set("gems." + g.id + ".y", p[1]);
            y.set("gems." + g.id + ".z", p[2]);
        }
        y.set("dragon-kills", dragonKills);
        for (Map.Entry<UUID, Set<Integer>> en : redProgress.entrySet()) {
            y.set("progress.red." + en.getKey(), new ArrayList<>(en.getValue()));
        }
        try {
            plugin.getDataFolder().mkdirs();
            y.save(file);
        } catch (IOException ex) {
            plugin.getLogger().severe("data.yml konnte nicht gespeichert werden: " + ex.getMessage());
        }
    }

    // ------------------------------------------------------------ Zugriff

    public Status status(GemType g) {
        return states.get(g);
    }

    public int dragonKills() {
        return dragonKills;
    }

    public void setDragonKills(int n) {
        dragonKills = n;
        save();
    }

    public Set<Integer> redProgress(UUID id) {
        return redProgress.computeIfAbsent(id, k -> new HashSet<>());
    }

    // ------------------------------------------------------------ Laufende Pruefung

    public void start() {
        lastTime = plugin.mainWorld().getTime();
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
    }

    private void tick() {
        World w = plugin.mainWorld();
        long t = w.getTime();
        if (lastTime >= 0 && t < lastTime) { // Zeit ist ueber die Tagesgrenze gesprungen (auch durch Schlafen)
            for (GemType g : GemType.values()) {
                if (states.get(g) == Status.PENDING) dayReached.add(g);
            }
        }
        lastTime = t;

        long now = System.currentTimeMillis();
        for (GemType g : new ArrayList<>(dayReached)) {
            if (states.get(g) != Status.PENDING) {
                dayReached.remove(g);
                continue;
            }
            if (!spawning.contains(g) && now >= nextTry.getOrDefault(g, 0L)) {
                beginSpawn(g, plugin.getConfig().getInt("spawn.respawn-min-distance", 50),
                        plugin.getConfig().getInt("spawn.respawn-max-distance", 500));
            }
        }
        for (GemType g : GemType.values()) {
            if (states.get(g) == Status.CHEST) checkChest(g, w);
        }
    }

    /** Wurde der Gem aus der Kiste genommen? Dann verschwindet die Struktur. */
    private void checkChest(GemType g, World w) {
        int[] p = chestPos.get(g);
        if (!w.isChunkLoaded(p[0] >> 4, p[2] >> 4)) return;
        Block cb = w.getBlockAt(p[0] + 1, p[1] + 1, p[2]);
        if (cb.getType() != Material.CHEST || !(cb.getState() instanceof Chest chest)) {
            plugin.getLogger().warning("Kiste des " + g.displayName + " fehlt (" + p[0] + " " + p[1] + " " + p[2]
                    + "). Der Gem erscheint am naechsten Tag erneut.");
            removeStructure(p, w);
            states.put(g, Status.PENDING);
            save();
            return;
        }
        for (ItemStack it : chest.getBlockInventory().getContents()) {
            if (GemItems.getType(it) == g) return; // Gem liegt noch in der Kiste
        }
        states.put(g, Status.OWNED);
        removeStructure(p, w);
        save();
        Msg.broadcast("Der " + g.displayName + " wurde aus seiner Kiste geborgen!", g.color);
    }

    // ------------------------------------------------------------ Vergabe

    /** Aufgabe geschafft: Gem direkt ins Inventar, falls die Aufgabe offen ist. */
    public boolean award(GemType g, Player pl) {
        if (states.get(g) != Status.AVAILABLE) return false;
        states.put(g, Status.OWNED);
        save();
        giveItem(g, pl);
        Msg.broadcast(pl.getName() + " hat den " + g.displayName + " errungen!", g.color);
        pl.showTitle(Title.title(Component.text(g.displayName, g.color),
                Component.text("Du hast den Gem errungen!", NamedTextColor.GRAY)));
        pl.getWorld().playSound(pl.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
        pl.getWorld().strikeLightningEffect(pl.getLocation());
        return true;
    }

    private void giveItem(GemType g, Player pl) {
        Map<Integer, ItemStack> left = pl.getInventory().addItem(GemItems.create(g));
        for (ItemStack it : left.values()) pl.getWorld().dropItem(pl.getLocation(), it);
        plugin.tracker().refreshSoon(pl);
    }

    /** Der Gem wurde zerstoert (Lava, Void, Feuer, Explosion ...): Kiste erscheint am naechsten Minecraft-Tag. */
    public void onGemDestroyed(GemType g) {
        if (states.get(g) != Status.OWNED) return;
        states.put(g, Status.PENDING);
        dayReached.remove(g);
        save();
        Msg.broadcast("Der " + g.displayName + " wurde zerstoert! Am naechsten Minecraft-Tag erscheint eine Kiste mit ihm.", g.color);
    }

    // ------------------------------------------------------------ Kiste spawnen

    private void beginSpawn(GemType g, int min, int max) {
        if (!spawning.add(g)) return;
        findSite(min, max, 0, site -> {
            spawning.remove(g);
            if (states.get(g) != Status.PENDING) return;
            if (site == null) {
                nextTry.put(g, System.currentTimeMillis() + 30_000L);
                plugin.getLogger().warning("Kein Platz fuer die Kiste des " + g.displayName + " gefunden, neuer Versuch in 30 s.");
                return;
            }
            build(g, site[0], site[1], site[2]);
            chestPos.put(g, site);
            states.put(g, Status.CHEST);
            dayReached.remove(g);
            save();
            Msg.broadcast("Eine Kiste mit dem " + g.displayName + " ist erschienen! Folge dem Lichtstrahl.", g.color);
        });
    }

    private void findSite(int min, int max, int attempt, Consumer<int[]> callback) {
        if (attempt >= 30) {
            callback.accept(null);
            return;
        }
        World w = plugin.mainWorld();
        Location sp = w.getSpawnLocation();
        double ang = rnd.nextDouble() * Math.PI * 2;
        double dist = min + rnd.nextDouble() * Math.max(1, max - min);
        int rx = sp.getBlockX() + (int) Math.round(Math.cos(ang) * dist);
        int rz = sp.getBlockZ() + (int) Math.round(Math.sin(ang) * dist);
        // Mitte des Chunks waehlen, damit die 3x3-Struktur komplett in einem Chunk liegt
        int x = (rx & ~15) + 2 + rnd.nextInt(12);
        int z = (rz & ~15) + 2 + rnd.nextInt(12);
        if (!w.getWorldBorder().isInside(new Location(w, x, 64, z))) {
            findSite(min, max, attempt + 1, callback);
            return;
        }
        w.getChunkAtAsync(x >> 4, z >> 4).thenAccept(chunk ->
                Bukkit.getScheduler().runTask(plugin, () -> {
                    int[] site = evaluate(w, x, z);
                    if (site != null) callback.accept(site);
                    else findSite(min, max, attempt + 1, callback);
                })
        ).exceptionally(ex -> {
            Bukkit.getScheduler().runTask(plugin, () -> findSite(min, max, attempt + 1, callback));
            return null;
        });
    }

    /** Prueft, ob an (x,z) eine flache, trockene Stelle ist. Gibt {x, y, z} zurueck oder null. */
    private int[] evaluate(World w, int x, int z) {
        int[][] offs = {{0, 0}, {1, 1}, {1, -1}, {-1, 1}, {-1, -1}};
        int lo = Integer.MAX_VALUE, hi = Integer.MIN_VALUE, cy = 0;
        for (int[] o : offs) {
            Block b = w.getHighestBlockAt(x + o[0], z + o[1], HeightMap.MOTION_BLOCKING_NO_LEAVES);
            if (b.isLiquid() || !b.getType().isSolid()) return null;
            lo = Math.min(lo, b.getY());
            hi = Math.max(hi, b.getY());
            if (o[0] == 0 && o[1] == 0) cy = b.getY();
        }
        if (hi - lo > 3) return null;
        if (cy < w.getMinHeight() + 10 || cy > w.getMaxHeight() - 30) return null;
        for (GemType other : GemType.values()) {
            if (states.get(other) != Status.CHEST) continue;
            int[] p = chestPos.get(other);
            if (Math.hypot(p[0] - x, p[2] - z) < 40) return null;
        }
        return new int[]{x, cy, z};
    }

    private void build(GemType g, int cx, int cy, int cz) {
        World w = plugin.mainWorld();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                for (int y = cy - 1; y >= cy - 8 && y > w.getMinHeight(); y--) { // Luecken unter der Plattform fuellen
                    Block b = w.getBlockAt(cx + dx, y, cz + dz);
                    if (b.getType().isSolid()) break;
                    b.setType(Material.COBBLESTONE, false);
                }
                w.getBlockAt(cx + dx, cy, cz + dz).setType(Material.IRON_BLOCK, false);
                for (int y = cy + 1; y <= cy + 3; y++) {
                    w.getBlockAt(cx + dx, y, cz + dz).setType(Material.AIR, false);
                }
            }
        }
        for (int y = cy + 4; y < w.getMaxHeight(); y++) { // freie Sicht nach oben fuer den Strahl
            Block b = w.getBlockAt(cx, y, cz);
            if (!b.getType().isAir() && b.getType().isOccluding()) b.setType(Material.AIR, false);
        }
        w.getBlockAt(cx, cy + 1, cz).setType(Material.BEACON, false);
        w.getBlockAt(cx, cy + 2, cz).setType(g.glass, false);

        Block cb = w.getBlockAt(cx + 1, cy + 1, cz);
        cb.setType(Material.CHEST, false);
        BlockData bd = cb.getBlockData();
        if (bd instanceof org.bukkit.block.data.type.Chest cd) {
            cd.setFacing(BlockFace.WEST);
            cb.setBlockData(cd, false);
        }
        if (cb.getState() instanceof Chest chest) {
            chest.getBlockInventory().setItem(13, GemItems.create(g));
        }
        w.strikeLightningEffect(cb.getLocation().add(0.5, 0, 0.5));
    }

    private void removeStructure(int[] p, World w) {
        Block cb = w.getBlockAt(p[0] + 1, p[1] + 1, p[2]);
        if (cb.getType() == Material.CHEST && cb.getState() instanceof Chest chest) {
            Inventory inv = chest.getBlockInventory();
            for (HumanEntity v : new ArrayList<>(inv.getViewers())) v.closeInventory();
            for (ItemStack it : inv.getContents()) {
                if (it == null || it.getType() == Material.AIR) continue;
                if (GemItems.getType(it) != null) continue; // Gems nie droppen
                w.dropItemNaturally(cb.getLocation().add(0.5, 1, 0.5), it);
            }
            inv.clear();
        }
        cb.setType(Material.AIR, false);
        w.getBlockAt(p[0], p[1] + 1, p[2]).setType(Material.AIR, false); // Beacon
        w.getBlockAt(p[0], p[1] + 2, p[2]).setType(Material.AIR, false); // Glas
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                w.getBlockAt(p[0] + dx, p[1], p[2] + dz).setType(Material.STONE_BRICKS, false);
            }
        }
    }

    /** Gehoert dieser Block zu einer aktiven Gem-Struktur? */
    public boolean isStructureBlock(Block b) {
        if (!b.getWorld().equals(plugin.mainWorld())) return false;
        int x = b.getX(), y = b.getY(), z = b.getZ();
        for (GemType g : GemType.values()) {
            if (states.get(g) != Status.CHEST) continue;
            int[] p = chestPos.get(g);
            if (y == p[1] && Math.abs(x - p[0]) <= 1 && Math.abs(z - p[2]) <= 1) return true;
            if (x == p[0] && z == p[2] && (y == p[1] + 1 || y == p[1] + 2)) return true;
            if (x == p[0] + 1 && z == p[2] && y == p[1] + 1) return true;
        }
        return false;
    }

    // ------------------------------------------------------------ Admin

    public String statusText(GemType g) {
        int[] p = chestPos.get(g);
        return switch (states.get(g)) {
            case AVAILABLE -> "Aufgabe offen (" + g.quest + ")";
            case OWNED -> "existiert (bei einem Spieler oder am Boden)";
            case PENDING -> "zerstoert, Kiste erscheint am naechsten Minecraft-Tag";
            case CHEST -> "in Kiste bei " + p[0] + " " + (p[1] + 1) + " " + p[2];
        };
    }

    public void reset(GemType g, CommandSender sender) {
        if (states.get(g) == Status.CHEST) removeStructure(chestPos.get(g), plugin.mainWorld());
        states.put(g, Status.AVAILABLE);
        dayReached.remove(g);
        if (g == GemType.RED) redProgress.clear();
        save();
        for (Player p : Bukkit.getOnlinePlayers()) GemItems.removeFrom(p, g);
        for (World w : Bukkit.getWorlds()) {
            for (Item it : w.getEntitiesByClass(Item.class)) {
                if (GemItems.getType(it.getItemStack()) == g) it.remove();
            }
        }
        Msg.to(sender, g.displayName + " zurueckgesetzt. Die Aufgabe ist wieder offen.", NamedTextColor.GREEN);
    }

    public void give(GemType g, Player target, CommandSender sender) {
        Status s = states.get(g);
        if (s == Status.OWNED || s == Status.CHEST) {
            Msg.to(sender, g.displayName + " existiert bereits (" + statusText(g) + ").", NamedTextColor.RED);
            return;
        }
        states.put(g, Status.OWNED);
        dayReached.remove(g);
        save();
        giveItem(g, target);
        Msg.to(sender, g.displayName + " an " + target.getName() + " gegeben.", NamedTextColor.GREEN);
    }
}
