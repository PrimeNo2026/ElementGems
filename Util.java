package elementgems;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Tameable;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Vector;

public final class Util {
    private Util() {}

    /** Darf diese Entity von den Faehigkeiten des Besitzers getroffen werden? */
    public static boolean isValidTarget(Player owner, Entity e) {
        if (!(e instanceof LivingEntity le)) return false;
        if (e.equals(owner) || le.isDead() || !le.isValid()) return false;
        if (e instanceof ArmorStand) return false;
        if (e instanceof Player p && p.getGameMode() == GameMode.SPECTATOR) return false;
        if (e.getPersistentDataContainer().has(Keys.SKELETON, PersistentDataType.BYTE)) return false;
        if (e instanceof Tameable t && t.isTamed() && t.getOwner() != null
                && t.getOwner().getUniqueId().equals(owner.getUniqueId())) return false;
        return true;
    }

    /**
     * Magieschaden: ignoriert Ruestung und Schild, wird aber vom Schutz-Zauber (Protection) noch verringert.
     * So sind Gem-Faehigkeiten auch gegen volle Diamantruestung wirksam, toeten aber nicht auf einen Schlag.
     */
    public static void magic(LivingEntity target, double amount, Entity source) {
        if (amount <= 0) return;
        DamageSource ds = DamageSource.builder(DamageType.MAGIC)
                .withCausingEntity(source)
                .withDirectEntity(source)
                .build();
        target.damage(amount, ds);
    }

    /** Stoesst eine Entity vom Punkt "from" weg. */
    public static void push(LivingEntity le, Location from, double strength, double up) {
        Vector dir = le.getLocation().toVector().subtract(from.toVector()).setY(0);
        if (dir.lengthSquared() < 1.0E-4) dir = from.getDirection().setY(0);
        if (dir.lengthSquared() < 1.0E-4) dir = new Vector(1, 0, 0);
        dir.normalize().multiply(strength).setY(up);
        le.setVelocity(dir);
    }
}
