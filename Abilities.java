package elementgems;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.FluidCollisionMode;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LargeFireball;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Skeleton;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.CrossbowMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/** Ausloesung und Umsetzung aller aktiven Faehigkeiten. */
public final class Abilities implements Listener {

    private static final int SLOT_PRIMARY = 0;
    private static final int SLOT_SECONDARY = 1;
    private static final int SLOT_WEAPON = 2; // Blau: Wasserpfeile mit Bogen/Armbrust

    private final ElementGems plugin;
    /** Abklingzeit-Ende (ms) je Gem und Faehigkeit. Gem-gebunden, nicht spielergebunden. */
    private final long[][] cooldownEnd = new long[GemType.values().length][3];
    private final Map<UUID, Long> paralyzed = new HashMap<>();

    public Abilities(ElementGems plugin) {
        this.plugin = plugin;
    }

    private double d(String path, double def) {
        return plugin.getConfig().getDouble(path, def);
    }

    // ------------------------------------------------------------ Ausloesung

    @EventHandler
    public void onInteract(PlayerInteractEvent e) {
        Action a = e.getAction();
        if (a != Action.RIGHT_CLICK_AIR && a != Action.RIGHT_CLICK_BLOCK) return;
        EquipmentSlot hand = e.getHand();
        if (hand == null) return;
        Player p = e.getPlayer();
        ItemStack used = e.getItem();

        // Bogen/Armbrust: Wasserpfeile, wenn man keine eigene Munition hat
        if (used != null && (used.getType() == Material.BOW || used.getType() == Material.CROSSBOW)) {
            handleWeapon(e, p, used, hand);
            return;
        }

        GemType gem = GemItems.getType(used);
        if (gem == null) return;
        // Ist in der Haupthand ein Gem, hat dieser Vorrang vor der Offhand
        if (hand == EquipmentSlot.OFF_HAND && GemItems.getType(p.getInventory().getItemInMainHand()) != null) return;
        if (p.getGameMode() == GameMode.SPECTATOR) return;

        Block clicked = e.getClickedBlock();
        if (a == Action.RIGHT_CLICK_BLOCK && clicked != null) {
            Material m = clicked.getType();
            // Gems duerfen nirgends eingelagert werden
            if (m == Material.DECORATED_POT || m.name().endsWith("_SHELF")) {
                e.setCancelled(true);
                return;
            }
            // Kisten, Tueren usw. normal bedienen, ausser man schleicht
            if (!p.isSneaking() && m.isInteractable()) return;
        }
        use(p, gem, p.isSneaking());
    }

    private boolean cooling(Player p, GemType gem, int slot, String name) {
        long now = System.currentTimeMillis();
        long end = cooldownEnd[gem.ordinal()][slot];
        if (now < end) {
            p.sendActionBar(Component.text(String.format(Locale.GERMANY, "%s: noch %.1f s", name, (end - now) / 1000.0),
                    NamedTextColor.GRAY));
            return true;
        }
        return false;
    }

    private void startCooldown(GemType gem, int slot, String key) {
        double cd = d("cooldowns." + gem.id + "." + key, 10.0);
        cooldownEnd[gem.ordinal()][slot] = System.currentTimeMillis() + (long) (cd * 1000);
    }

    private void use(Player p, GemType gem, boolean secondary) {
        int slot = secondary ? SLOT_SECONDARY : SLOT_PRIMARY;
        if (cooling(p, gem, slot, secondary ? gem.ability2 : gem.ability1)) return;
        boolean ok = switch (gem) {
            case RED -> secondary ? fireball(p) : ring(p);
            case BLUE -> secondary ? boost(p) : wave(p);
            case YELLOW -> secondary ? teleport(p) : lightning(p);
            case PURPLE -> secondary ? blind(p) : skeletons(p);
        };
        if (ok) startCooldown(gem, slot, secondary ? gem.cdKey2 : gem.cdKey1);
    }

    private void fail(Player p, String text) {
        p.sendActionBar(Component.text(text, NamedTextColor.RED));
    }

    // ------------------------------------------------------------ ROT

    private boolean ring(Player p) {
        final double r = d("red.ring-radius", 6.0);
        double dmg = d("red.ring-damage", 9.0);
        double kb = d("red.ring-knockback", 1.9);
        int fire = (int) (d("red.ring-fire-seconds", 5.0) * 20);
        final World w = p.getWorld();
        final Location c = p.getLocation();

        for (Entity en : w.getNearbyEntities(c, r, 2.5, r)) {
            if (!Util.isValidTarget(p, en)) continue;
            LivingEntity le = (LivingEntity) en;
            double dx = le.getLocation().getX() - c.getX();
            double dz = le.getLocation().getZ() - c.getZ();
            if (dx * dx + dz * dz > r * r) continue;
            Util.magic(le, dmg, p, DeathMessages.Ability.RED_RING);
            if (fire > 0) le.setFireTicks(Math.max(le.getFireTicks(), fire));
            Util.push(le, c, kb, 0.45);
        }

        w.playSound(c, Sound.ITEM_FIRECHARGE_USE, 1.2f, 0.8f);
        w.playSound(c, Sound.ENTITY_BLAZE_SHOOT, 1.0f, 0.7f);
        new BukkitRunnable() {
            int t = 0;

            @Override
            public void run() {
                double rad = 0.8 + (r - 0.8) * (t / 7.0);
                int n = (int) Math.max(24, rad * 14);
                for (int i = 0; i < n; i++) {
                    double ang = 2 * Math.PI * i / n;
                    w.spawnParticle(Particle.FLAME, c.getX() + Math.cos(ang) * rad, c.getY() + 0.2,
                            c.getZ() + Math.sin(ang) * rad, 1, 0, 0.05, 0, 0.01);
                }
                if (++t > 7) cancel();
            }
        }.runTaskTimer(plugin, 0L, 1L);
        return true;
    }

    private boolean fireball(Player p) {
        Vector dir = p.getEyeLocation().getDirection();
        LargeFireball fb = p.launchProjectile(LargeFireball.class, dir.clone().multiply(1.0));
        fb.setYield(0f); // keine Vanilla-Explosion, den Schaden macht fireballImpact()
        fb.setIsIncendiary(false);
        fb.getPersistentDataContainer().set(Keys.FIREBALL, PersistentDataType.BYTE, (byte) 1);
        p.getWorld().playSound(p.getLocation(), Sound.ENTITY_GHAST_SHOOT, 1.0f, 1.0f);
        return true;
    }

    /** Einschlag des Feuerballs: Flaechenschaden mit Magieschaden, setzt Ziele in Brand. */
    public void fireballImpact(Projectile fb) {
        if (!(fb.getShooter() instanceof Player owner)) return;
        Location l = fb.getLocation();
        World w = l.getWorld();
        double r = d("red.fireball-radius", 4.0);
        double raw = d("red.fireball-damage", 12.0);
        int fire = (int) (d("red.fireball-fire-seconds", 6.0) * 20);

        w.spawnParticle(Particle.EXPLOSION, l, 1);
        w.spawnParticle(Particle.FLAME, l, 50, 1.0, 0.8, 1.0, 0.1);
        w.playSound(l, Sound.ENTITY_GENERIC_EXPLODE, 1.2f, 1.0f);
        for (Entity en : w.getNearbyEntities(l, r, r, r)) {
            if (!Util.isValidTarget(owner, en)) continue;
            LivingEntity le = (LivingEntity) en;
            double dist = le.getLocation().add(0, le.getHeight() / 2.0, 0).distance(l);
            if (dist > r) continue;
            double factor = 1.0 - 0.3 * (dist / r); // im Zentrum 100 %, am Rand 70 %
            Util.magic(le, raw * factor, owner, DeathMessages.Ability.RED_FIREBALL);
            le.setFireTicks(Math.max(le.getFireTicks(), fire));
            Util.push(le, l, 0.9, 0.35);
        }
    }

    // ------------------------------------------------------------ BLAU

    /** Rechtsklick mit Bogen/Armbrust ohne eigene Pfeile: schiesst einen Wasserpfeil. */
    private void handleWeapon(PlayerInteractEvent e, Player p, ItemStack weapon, EquipmentSlot hand) {
        if (!GemItems.holds(p, GemType.BLUE)) return;
        GameMode gm = p.getGameMode();
        if (gm == GameMode.SPECTATOR || gm == GameMode.CREATIVE) return;
        if (hand == EquipmentSlot.OFF_HAND) {
            Material main = p.getInventory().getItemInMainHand().getType();
            if (main == Material.BOW || main == Material.CROSSBOW) return;
        }
        boolean crossbow = weapon.getType() == Material.CROSSBOW;
        // Geladene Armbrust schiesst normal
        if (crossbow && weapon.getItemMeta() instanceof CrossbowMeta cm && cm.hasChargedProjectiles()) return;
        // Mit eigener Munition funktioniert alles wie in Vanilla
        if (hasAmmo(p, crossbow)) return;
        Block clicked = e.getClickedBlock();
        if (e.getAction() == Action.RIGHT_CLICK_BLOCK && clicked != null && !p.isSneaking()
                && clicked.getType().isInteractable()) return;

        if (cooling(p, GemType.BLUE, SLOT_WEAPON, "Wasserpfeil")) return;
        shootWaterArrow(p, weapon, crossbow);
        startCooldown(GemType.BLUE, SLOT_WEAPON, crossbow ? "crossbow" : "bow");
    }

    private boolean hasAmmo(Player p, boolean crossbow) {
        for (ItemStack it : p.getInventory().getContents()) {
            if (isAmmo(it, crossbow)) return true;
        }
        return isAmmo(p.getInventory().getItemInOffHand(), crossbow);
    }

    private boolean isAmmo(ItemStack it, boolean crossbow) {
        if (it == null) return false;
        Material m = it.getType();
        if (m == Material.ARROW || m == Material.SPECTRAL_ARROW || m == Material.TIPPED_ARROW) return true;
        return crossbow && m == Material.FIREWORK_ROCKET;
    }

    private void shootWaterArrow(Player p, ItemStack weapon, boolean crossbow) {
        double speed = crossbow ? d("blue.crossbow-speed", 3.2) : d("blue.bow-speed", 3.0);
        double raw = crossbow ? d("blue.crossbow-damage", 11.0) : d("blue.bow-damage", 8.0);
        raw *= 1.0 + 0.1 * weapon.getEnchantmentLevel(Enchantment.POWER);

        Vector dir = p.getEyeLocation().getDirection();
        final Arrow ar = p.launchProjectile(Arrow.class, dir.clone().multiply(speed));
        ar.setPickupStatus(AbstractArrow.PickupStatus.DISALLOWED);
        ar.setDamage(0.0); // den Schaden macht waterArrowHit() als Magieschaden
        ar.setColor(Color.fromRGB(0x2F7BFF));
        ar.getPersistentDataContainer().set(Keys.ARROW, PersistentDataType.DOUBLE, raw);
        if (weapon.getEnchantmentLevel(Enchantment.FLAME) > 0) ar.setFireTicks(100);
        p.getWorld().playSound(p.getLocation(), Sound.ENTITY_ARROW_SHOOT, 1.0f, 1.4f);
        new BukkitRunnable() {
            int t = 0;

            @Override
            public void run() {
                if (!ar.isValid() || ar.isInBlock() || ++t > 100) {
                    cancel();
                    return;
                }
                ar.getWorld().spawnParticle(Particle.SPLASH, ar.getLocation(), 6, 0.08, 0.08, 0.08, 0.05);
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }

    /** Wasserpfeil trifft: Magieschaden, loescht Feuer, verlangsamt kurz. */
    public void waterArrowHit(Projectile pr, Entity hit) {
        Double raw = pr.getPersistentDataContainer().get(Keys.ARROW, PersistentDataType.DOUBLE);
        if (raw == null) return;
        Location l = pr.getLocation();
        l.getWorld().spawnParticle(Particle.SPLASH, l, 25, 0.3, 0.3, 0.3, 0.2);
        l.getWorld().playSound(l, Sound.ENTITY_PLAYER_SPLASH, 0.8f, 1.5f);
        if (pr.getShooter() instanceof Player shooter && hit != null && Util.isValidTarget(shooter, hit)) {
            LivingEntity le = (LivingEntity) hit;
            Util.magic(le, raw, shooter, DeathMessages.Ability.BLUE_ARROW);
            le.setFireTicks(0);
            le.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS,
                    (int) (d("blue.arrow-slow-seconds", 2.0) * 20), 0, false, true, true));
        }
        Bukkit.getScheduler().runTask(plugin, pr::remove); // erst nach dem Treffer entfernen
    }

    /** Rechtsklick mit dem Gem: Flutwelle stoesst Gegner weg, verlangsamt und verletzt sie. */
    private boolean wave(Player p) {
        final double r = d("blue.wave-radius", 5.5);
        double dmg = d("blue.wave-damage", 6.0);
        double kb = d("blue.wave-knockback", 2.0);
        int slow = (int) (d("blue.wave-slow-seconds", 3.0) * 20);
        final World w = p.getWorld();
        final Location c = p.getLocation();
        for (Entity en : w.getNearbyEntities(c, r, 2.5, r)) {
            if (!Util.isValidTarget(p, en)) continue;
            LivingEntity le = (LivingEntity) en;
            double dx = le.getLocation().getX() - c.getX();
            double dz = le.getLocation().getZ() - c.getZ();
            if (dx * dx + dz * dz > r * r) continue;
            Util.magic(le, dmg, p, DeathMessages.Ability.BLUE_WAVE);
            le.setFireTicks(0);
            le.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, slow, 1, false, true, true));
            Util.push(le, c, kb, 0.5);
        }
        w.playSound(c, Sound.ENTITY_PLAYER_SPLASH, 1.3f, 0.7f);
        w.playSound(c, Sound.ENTITY_GENERIC_SPLASH, 1.0f, 0.8f);
        new BukkitRunnable() {
            int t = 0;

            @Override
            public void run() {
                double rad = 0.8 + (r - 0.8) * (t / 7.0);
                int n = (int) Math.max(24, rad * 14);
                for (int i = 0; i < n; i++) {
                    double ang = 2 * Math.PI * i / n;
                    w.spawnParticle(Particle.SPLASH, c.getX() + Math.cos(ang) * rad, c.getY() + 0.4,
                            c.getZ() + Math.sin(ang) * rad, 2, 0.1, 0.2, 0.1, 0.05);
                }
                if (++t > 7) cancel();
            }
        }.runTaskTimer(plugin, 0L, 1L);
        return true;
    }

    private boolean boost(Player p) {
        Vector dir = p.getLocation().getDirection().setY(0);
        if (dir.lengthSquared() < 1.0E-4) dir = new Vector(0, 0, 1);
        dir.normalize().multiply(d("blue.boost-forward", 1.8)).setY(d("blue.boost-up", 0.75));
        p.setVelocity(dir);
        p.setFallDistance(0f);
        final World w = p.getWorld();
        w.playSound(p.getLocation(), Sound.ENTITY_DOLPHIN_JUMP, 1.0f, 1.0f);
        w.spawnParticle(Particle.SPLASH, p.getLocation().add(0, 0.2, 0), 40, 0.4, 0.1, 0.4, 0.2);

        final int speedTicks = (int) (d("blue.landing-speed-seconds", 6.0) * 20);
        final int amp = Math.max(0, (int) d("blue.landing-speed-level", 3.0) - 1);
        new BukkitRunnable() {
            int age = 0;
            boolean left = false;

            @Override
            public void run() {
                age++;
                if (!p.isOnline() || p.isDead() || age > 100) {
                    cancel();
                    return;
                }
                if (!p.isOnGround()) {
                    left = true;
                    if (p.isInWater()) {
                        cancel();
                        return;
                    }
                    p.getWorld().spawnParticle(Particle.SPLASH, p.getLocation().add(0, 0.5, 0), 6, 0.25, 0.25, 0.25, 0.1);
                } else if (left) {
                    p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, speedTicks, amp, false, true, true));
                    landingShockwave(p);
                    cancel();
                } else if (age > 10) {
                    cancel(); // Boost wurde blockiert, nie abgehoben
                }
            }
        }.runTaskTimer(plugin, 2L, 1L);
        return true;
    }

    /** Beim Landen nach dem Boost: Wasserwelle um den Spieler. */
    private void landingShockwave(Player p) {
        double r = d("blue.landing-radius", 4.0);
        double dmg = d("blue.landing-damage", 8.0);
        World w = p.getWorld();
        Location c = p.getLocation();
        for (Entity en : w.getNearbyEntities(c, r, 2.5, r)) {
            if (!Util.isValidTarget(p, en)) continue;
            LivingEntity le = (LivingEntity) en;
            Util.magic(le, dmg, p, DeathMessages.Ability.BLUE_LANDING);
            le.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 60, 1, false, true, true));
            Util.push(le, c, 1.1, 0.4);
        }
        w.spawnParticle(Particle.CLOUD, c.clone().add(0, 0.1, 0), 25, 0.5, 0.05, 0.5, 0.08);
        w.spawnParticle(Particle.SPLASH, c.clone().add(0, 0.3, 0), 60, 1.2, 0.2, 1.2, 0.2);
        w.playSound(c, Sound.ENTITY_PLAYER_SPLASH_HIGH_SPEED, 1.2f, 0.8f);
    }

    // ------------------------------------------------------------ GELB

    private boolean lightning(Player p) {
        double range = d("yellow.lightning-range", 40.0);
        double radius = d("yellow.lightning-radius", 3.5);
        double dmg = d("yellow.lightning-damage", 12.0);
        int ticks = (int) (d("yellow.paralyze-seconds", 2.0) * 20);
        World w = p.getWorld();
        Location eye = p.getEyeLocation();
        RayTraceResult r = w.rayTrace(eye, eye.getDirection(), range, FluidCollisionMode.NEVER, true, 0.4,
                en -> Util.isValidTarget(p, en));
        if (r == null) {
            fail(p, "Kein Ziel in Reichweite");
            return false;
        }
        Location loc = r.getHitPosition().toLocation(w);
        w.strikeLightningEffect(loc); // nur Optik, setzt nichts in Brand
        for (Entity en : w.getNearbyEntities(loc, radius, radius, radius)) {
            if (!Util.isValidTarget(p, en)) continue;
            LivingEntity le = (LivingEntity) en;
            Util.magic(le, dmg, p, DeathMessages.Ability.YELLOW_LIGHTNING);
            paralyze(le, ticks);
        }
        return true;
    }

    private void paralyze(final LivingEntity le, final int ticks) {
        le.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, ticks, 10, false, false, true));
        if (le instanceof Player pl) paralyzed.put(pl.getUniqueId(), System.currentTimeMillis() + ticks * 50L);
        new BukkitRunnable() {
            int t = 0;

            @Override
            public void run() {
                if (!le.isValid() || t >= ticks) {
                    cancel();
                    return;
                }
                le.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, le.getLocation().add(0, le.getHeight() / 2, 0),
                        8, 0.35, 0.5, 0.35, 0.1);
                t += 3;
            }
        }.runTaskTimer(plugin, 0L, 3L);
    }

    /** Paralysierte Spieler koennen sich nicht bewegen und nicht springen. */
    @EventHandler(ignoreCancelled = true)
    public void onMove(PlayerMoveEvent e) {
        if (paralyzed.isEmpty()) return;
        Long until = paralyzed.get(e.getPlayer().getUniqueId());
        if (until == null) return;
        if (System.currentTimeMillis() > until) {
            paralyzed.remove(e.getPlayer().getUniqueId());
            return;
        }
        Location from = e.getFrom();
        Location to = e.getTo();
        if (to == null) return;
        if (from.getX() != to.getX() || from.getZ() != to.getZ() || to.getY() > from.getY()) {
            Location fixed = from.clone();
            fixed.setY(Math.min(from.getY(), to.getY()));
            fixed.setYaw(to.getYaw());
            fixed.setPitch(to.getPitch());
            e.setTo(fixed);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        paralyzed.remove(e.getPlayer().getUniqueId());
    }

    private boolean teleport(Player p) {
        double dist = d("yellow.teleport-distance", 35.0);
        World w = p.getWorld();
        Location eye = p.getEyeLocation();
        Vector dir = eye.getDirection();
        RayTraceResult rt = w.rayTraceBlocks(eye, dir, dist, FluidCollisionMode.NEVER, true);
        double max = rt == null ? dist : Math.max(0, rt.getHitPosition().distance(eye.toVector()) - 0.6);
        Location target = null;
        for (double dd = max; dd >= 1.0; dd -= 0.5) {
            Location feet = eye.clone().add(dir.clone().multiply(dd)).subtract(0, 1.62, 0);
            if (isSafe(feet)) {
                target = feet;
                break;
            }
        }
        if (target == null) {
            fail(p, "Kein Platz zum Teleportieren");
            return false;
        }
        Location from = p.getLocation();
        target.setYaw(from.getYaw());
        target.setPitch(from.getPitch());
        w.spawnParticle(Particle.PORTAL, from.clone().add(0, 1, 0), 50, 0.3, 0.6, 0.3, 0.5);
        w.playSound(from, Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.2f);
        p.teleport(target);
        p.setFallDistance(0f);
        w.spawnParticle(Particle.PORTAL, target.clone().add(0, 1, 0), 50, 0.3, 0.6, 0.3, 0.5);
        w.playSound(target, Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.2f);
        return true;
    }

    private boolean isSafe(Location feet) {
        World w = feet.getWorld();
        if (!w.getWorldBorder().isInside(feet)) return false;
        if (feet.getY() < w.getMinHeight() + 1 || feet.getY() > w.getMaxHeight() - 2) return false;
        Block f = feet.getBlock();
        Block h = f.getRelative(BlockFace.UP);
        if (!f.isPassable() || !h.isPassable()) return false;
        Material fm = f.getType(), hm = h.getType(), bm = f.getRelative(BlockFace.DOWN).getType();
        return fm != Material.LAVA && hm != Material.LAVA && bm != Material.LAVA
                && fm != Material.FIRE && hm != Material.FIRE
                && fm != Material.SOUL_FIRE && hm != Material.SOUL_FIRE
                && bm != Material.MAGMA_BLOCK;
    }

    // ------------------------------------------------------------ LILA

    private boolean skeletons(Player p) {
        int count = (int) d("purple.skeleton-count", 4.0);
        int max = (int) d("purple.skeleton-max", 8.0);
        int alive = countSkeletons();
        if (alive >= max) {
            fail(p, "Maximale Anzahl Skelette erreicht (" + max + ")");
            return false;
        }
        int n = Math.min(count, max - alive);
        World w = p.getWorld();
        Location base = p.getLocation();
        double offset = Math.random() * Math.PI * 2;
        for (int i = 0; i < n; i++) {
            double ang = offset + 2 * Math.PI * i / n;
            Location l = base.clone().add(Math.cos(ang) * 2.0, 0, Math.sin(ang) * 2.0);
            if (!l.getBlock().isPassable() || !l.clone().add(0, 1, 0).getBlock().isPassable()) l = base.clone();
            final Skeleton sk = w.spawn(l, Skeleton.class, this::prepareSkeleton);
            // Nochmal im naechsten Tick, falls der Server dem Skelett beim Spawn doch einen Bogen gibt
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (sk.isValid()) prepareSkeleton(sk);
            });
            w.spawnParticle(Particle.SMOKE, l.clone().add(0, 1, 0), 20, 0.3, 0.5, 0.3, 0.02);
        }
        w.playSound(base, Sound.ENTITY_EVOKER_CAST_SPELL, 1.0f, 0.8f);
        return true;
    }

    /** Nahkampf-Skelett: kein Bogen, Schwert, Lederhelm gegen Sonnenlicht, schnell und zaeh. */
    private void prepareSkeleton(Skeleton s) {
        s.getPersistentDataContainer().set(Keys.SKELETON, PersistentDataType.BYTE, (byte) 1);
        ItemStack helmet = new ItemStack(Material.LEATHER_HELMET);
        LeatherArmorMeta lm = (LeatherArmorMeta) helmet.getItemMeta();
        lm.setColor(Color.fromRGB(0x7B3FE4));
        lm.setUnbreakable(true);
        helmet.setItemMeta(lm);
        s.getEquipment().setHelmet(helmet);
        s.getEquipment().setHelmetDropChance(0f);
        s.getEquipment().setItemInMainHand(new ItemStack(Material.IRON_SWORD)); // ersetzt den Bogen, Skelett kaempft im Nahkampf
        s.getEquipment().setItemInMainHandDropChance(0f);
        s.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 1_000_000, 0, false, false, false));
        s.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 1_000_000, 0, false, false, false));
        s.setPersistent(true);
        s.setRemoveWhenFarAway(false);
    }

    private int countSkeletons() {
        int n = 0;
        for (World w : Bukkit.getWorlds()) {
            for (Skeleton s : w.getEntitiesByClass(Skeleton.class)) {
                if (s.getPersistentDataContainer().has(Keys.SKELETON, PersistentDataType.BYTE)) n++;
            }
        }
        return n;
    }

    private boolean blind(Player p) {
        final double r = d("purple.blind-radius", 10.0);
        double dmg = d("purple.blind-damage", 5.0);
        int blindTicks = (int) (d("purple.blind-seconds", 8.0) * 20);
        int slowTicks = (int) (d("purple.blind-slow-seconds", 4.0) * 20);
        final World w = p.getWorld();
        final Location c = p.getLocation();
        for (Entity en : w.getNearbyEntities(c, r, r, r)) {
            if (!Util.isValidTarget(p, en)) continue;
            LivingEntity le = (LivingEntity) en;
            if (le.getLocation().distanceSquared(c) > r * r) continue;
            Util.magic(le, dmg, p, DeathMessages.Ability.PURPLE_BLIND);
            le.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, blindTicks, 0, false, true, true));
            le.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, slowTicks, 0, false, true, true));
        }
        final Particle.DustOptions dust = new Particle.DustOptions(Color.fromRGB(0x8A2BE2), 1.6f);
        w.playSound(c, Sound.ENTITY_EVOKER_CAST_SPELL, 1.2f, 0.6f);
        new BukkitRunnable() {
            int t = 0;

            @Override
            public void run() {
                double rad = 0.8 + (r - 0.8) * (t / 7.0);
                int n = (int) Math.max(24, rad * 14);
                for (int i = 0; i < n; i++) {
                    double ang = 2 * Math.PI * i / n;
                    w.spawnParticle(Particle.DUST, c.getX() + Math.cos(ang) * rad, c.getY() + 1.0,
                            c.getZ() + Math.sin(ang) * rad, 1, 0, 0.1, 0, 0, dust);
                }
                if (++t > 7) cancel();
            }
        }.runTaskTimer(plugin, 0L, 1L);
        return true;
    }
}
