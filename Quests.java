package elementgems;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.boss.DragonBattle;
import org.bukkit.entity.EnderDragon;
import org.bukkit.entity.Player;
import org.bukkit.entity.Trident;
import org.bukkit.entity.Villager;
import org.bukkit.entity.Witch;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityTransformEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.player.PlayerAdvancementDoneEvent;
import net.kyori.adventure.text.format.NamedTextColor;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Die vier Aufgaben, mit denen man die Gems freischaltet. */
public final class Quests implements Listener {

    private record Hit(UUID player, long at) {}

    private static final String[] NETHER_NAMES = {
            "Netherwüste", "Karmesinwald", "Wirrwald", "Seelensandtal", "Basaltdeltas"
    };

    private final ElementGems plugin;
    private final GemManager gm;
    private final Map<UUID, Hit> villagerHits = new HashMap<>();

    public Quests(ElementGems plugin, GemManager gm) {
        this.plugin = plugin;
        this.gm = gm;
    }

    public void start() {
        // Wurde der Drache auf diesem Server schon einmal besiegt? Dann zaehlt jeder weitere Kill als "respawnter Drache".
        if (gm.dragonKills() < 0) {
            int base = 0;
            for (World w : Bukkit.getWorlds()) {
                if (w.getEnvironment() != World.Environment.THE_END) continue;
                DragonBattle b = w.getEnderDragonBattle();
                if (b != null && b.hasBeenPreviouslyKilled()) base = 1;
            }
            gm.setDragonKills(base);
        }
        Bukkit.getScheduler().runTaskTimer(plugin, this::scan, 40L, 20L);
    }

    // ------------------------------------------------------------ LILA: Enderdrache (respawnt) besiegen

    @EventHandler
    public void onDragonDeath(EntityDeathEvent e) {
        if (!(e.getEntity() instanceof EnderDragon dragon)) return;
        boolean respawned = gm.dragonKills() >= 1;
        gm.setDragonKills(Math.max(0, gm.dragonKills()) + 1);
        if (!respawned) return; // der allererste Drache zaehlt nicht

        Player winner = dragon.getKiller();
        if (winner == null) {
            double best = 150 * 150;
            for (Player p : dragon.getWorld().getPlayers()) {
                double dist = p.getLocation().distanceSquared(dragon.getLocation());
                if (dist < best && p.getGameMode() != GameMode.SPECTATOR) {
                    best = dist;
                    winner = p;
                }
            }
        }
        if (winner != null) gm.award(GemType.PURPLE, winner);
    }

    // ------------------------------------------------------------ GELB: Dorfbewohner per Blitz in Hexe verwandeln

    @EventHandler(ignoreCancelled = true)
    public void onTridentHit(ProjectileHitEvent e) {
        if (!(e.getEntity() instanceof Trident trident)) return;
        if (!(trident.getShooter() instanceof Player shooter)) return;
        if (e.getHitEntity() instanceof Villager v) {
            villagerHits.put(v.getUniqueId(), new Hit(shooter.getUniqueId(), System.currentTimeMillis()));
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onTransform(EntityTransformEvent e) {
        if (e.getTransformReason() != EntityTransformEvent.TransformReason.LIGHTNING) return;
        if (!(e.getEntity() instanceof Villager) || !(e.getTransformedEntity() instanceof Witch)) return;
        Hit hit = villagerHits.remove(e.getEntity().getUniqueId());
        if (hit == null || System.currentTimeMillis() - hit.at() > 15_000L) return;
        Player p = Bukkit.getPlayer(hit.player());
        if (p != null) gm.award(GemType.YELLOW, p);
    }

    // ------------------------------------------------------------ BLAU: Beacon-Fortschritt

    @EventHandler
    public void onAdvancement(PlayerAdvancementDoneEvent e) {
        if (e.getAdvancement().getKey().getKey().equals("nether/create_beacon")) {
            gm.award(GemType.BLUE, e.getPlayer());
        }
    }

    // ------------------------------------------------------------ ROT: alle Nether-Biome besuchen

    private static int netherIndex(Biome b) {
        if (Biome.NETHER_WASTES.equals(b)) return 0;
        if (Biome.CRIMSON_FOREST.equals(b)) return 1;
        if (Biome.WARPED_FOREST.equals(b)) return 2;
        if (Biome.SOUL_SAND_VALLEY.equals(b)) return 3;
        if (Biome.BASALT_DELTAS.equals(b)) return 4;
        return -1;
    }

    private void scan() {
        long now = System.currentTimeMillis();
        villagerHits.values().removeIf(h -> now - h.at() > 60_000L);
        if (gm.status(GemType.RED) != GemManager.Status.AVAILABLE) return;

        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.getWorld().getEnvironment() != World.Environment.NETHER) continue;
            if (p.getGameMode() == GameMode.SPECTATOR) continue;
            int idx = netherIndex(p.getLocation().getBlock().getBiome());
            if (idx < 0) continue;
            Set<Integer> seen = gm.redProgress(p.getUniqueId());
            if (!seen.add(idx)) continue;
            gm.save();
            Msg.to(p, "Nether-Biom entdeckt: " + NETHER_NAMES[idx] + " (" + seen.size() + "/5)", NamedTextColor.RED);
            if (seen.size() >= NETHER_NAMES.length) gm.award(GemType.RED, p);
        }
    }
}
