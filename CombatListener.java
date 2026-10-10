package elementgems;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.GameMode;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Fireball;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityPotionEffectEvent;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.event.entity.ExplosionPrimeEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/** Kampf-Details: Skelette, Immunitaeten, Feuerball, Wasserpfeil. */
public final class CombatListener implements Listener {

    private final ElementGems plugin;

    public CombatListener(ElementGems plugin) {
        this.plugin = plugin;
    }

    private static boolean isGemSkeleton(Entity e) {
        return e.getPersistentDataContainer().has(Keys.SKELETON, PersistentDataType.BYTE);
    }

    // ------------------------------------------------------------ Lila: Skelette greifen jeden ausser den Gem-Besitzer an

    @EventHandler(ignoreCancelled = true)
    public void onTarget(EntityTargetEvent e) {
        if (!isGemSkeleton(e.getEntity())) return;
        if (!(e.getTarget() instanceof Player p) || !GemItems.holds(p, GemType.PURPLE)) return;
        // Statt des Besitzers den naechsten anderen Spieler anvisieren
        Player best = null;
        double bestDist = 16 * 16;
        Location at = e.getEntity().getLocation();
        for (Player o : e.getEntity().getWorld().getPlayers()) {
            if (o.equals(p) || o.isDead()) continue;
            if (o.getGameMode() != GameMode.SURVIVAL && o.getGameMode() != GameMode.ADVENTURE) continue;
            if (GemItems.holds(o, GemType.PURPLE)) continue;
            double d = o.getLocation().distanceSquared(at);
            if (d < bestDist) {
                bestDist = d;
                best = o;
            }
        }
        if (best != null) e.setTarget(best);
        else e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onDamageByEntity(EntityDamageByEntityEvent e) {
        if (!(e.getEntity() instanceof Player victim)) return;
        Entity damager = e.getDamager();
        Entity source = damager;
        if (damager instanceof Projectile pr && pr.getShooter() instanceof Entity shooter) source = shooter;

        // Skelette (Nahkampf und Pfeile) verletzen den Lila-Besitzer nicht
        if (isGemSkeleton(source) && GemItems.holds(victim, GemType.PURPLE)) {
            e.setCancelled(true);
            return;
        }
        // Eigener Feuerball verletzt den Roten-Besitzer nicht
        if (damager instanceof Fireball fb && fb.getPersistentDataContainer().has(Keys.FIREBALL, PersistentDataType.BYTE)
                && victim.equals(fb.getShooter())) {
            e.setCancelled(true);
            return;
        }
        if (e.getCause() == EntityDamageEvent.DamageCause.ENTITY_EXPLOSION && damager.equals(victim)
                && GemItems.holds(victim, GemType.RED)) {
            e.setCancelled(true);
        }
    }

    // ------------------------------------------------------------ Lila: Immunitaet gegen Gift und Wither

    @EventHandler(ignoreCancelled = true)
    public void onPotion(EntityPotionEffectEvent e) {
        if (!(e.getEntity() instanceof Player p)) return;
        PotionEffect ne = e.getNewEffect();
        if (ne == null) return;
        PotionEffectType t = ne.getType();
        if ((t.equals(PotionEffectType.POISON) || t.equals(PotionEffectType.WITHER)) && GemItems.holds(p, GemType.PURPLE)) {
            e.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onDamage(EntityDamageEvent e) {
        if (!(e.getEntity() instanceof Player p)) return;
        EntityDamageEvent.DamageCause c = e.getCause();
        if ((c == EntityDamageEvent.DamageCause.POISON || c == EntityDamageEvent.DamageCause.WITHER)
                && GemItems.holds(p, GemType.PURPLE)) {
            e.setCancelled(true);
        }
    }

    // ------------------------------------------------------------ Rot: Feuerball ohne Block-Schaden

    private static boolean isOurFireball(Entity e) {
        return e != null && e.getPersistentDataContainer().has(Keys.FIREBALL, PersistentDataType.BYTE);
    }

    @EventHandler
    public void onPrime(ExplosionPrimeEvent e) {
        if (isOurFireball(e.getEntity())) e.setFire(false);
    }

    @EventHandler
    public void onExplode(EntityExplodeEvent e) {
        if (isOurFireball(e.getEntity()) && !plugin.getConfig().getBoolean("red.fireball-block-damage", false)) {
            e.blockList().clear();
        }
    }

    // ------------------------------------------------------------ Blau: Wasserpfeil

    @EventHandler
    public void onHit(ProjectileHitEvent e) {
        Projectile pr = e.getEntity();
        if (!pr.getPersistentDataContainer().has(Keys.ARROW, PersistentDataType.BYTE)) return;
        Location l = pr.getLocation();
        pr.getWorld().spawnParticle(Particle.SPLASH, l, 25, 0.3, 0.3, 0.3, 0.2);
        pr.getWorld().playSound(l, Sound.ENTITY_PLAYER_SPLASH, 0.8f, 1.5f);
        if (e.getHitEntity() instanceof LivingEntity le) le.setFireTicks(0); // Wasser loescht
        Bukkit.getScheduler().runTask(plugin, pr::remove); // erst nach dem Schaden entfernen
    }
}
