package elementgems;

import org.bukkit.GameMode;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Tameable;
import org.bukkit.persistence.PersistentDataType;

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
}
