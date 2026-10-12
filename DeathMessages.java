package elementgems;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Eigene Todesnachrichten fuer jede Gem-Attacke, auf Deutsch und Englisch.
 * Jeder Spieler sieht die Nachricht in seiner eigenen Client-Sprache (Deutsch oder sonst Englisch).
 * %1$s = Opfer, %2$s = Angreifer.
 */
public final class DeathMessages implements Listener {

    public enum Ability {
        RED_RING(GemType.RED,
                new String[]{
                        "%2$s hat %1$s mit dem Feuerring des Roten Gems eingeäschert.",
                        "%1$s wurde vom Feuerring von %2$s zu Asche verbrannt.",
                        "%1$s kam %2$s und dem Roten Gem zu nahe und ging in Flammen auf."},
                new String[]{
                        "%2$s reduced %1$s to ashes with the Red Gem's fire ring.",
                        "%1$s was scorched by %2$s's ring of fire.",
                        "%1$s got too close to %2$s and the Red Gem and went up in flames."}),
        RED_FIREBALL(GemType.RED,
                new String[]{
                        "%2$s hat %1$s mit einem Feuerball des Roten Gems pulverisiert.",
                        "%1$s fing einen Feuerball von %2$s ab und verglühte.",
                        "%1$s wurde von %2$s mit dem Roten Gem in die Luft gejagt."},
                new String[]{
                        "%2$s blew %1$s away with a fireball from the Red Gem.",
                        "%1$s caught a fireball thrown by %2$s and burned up.",
                        "%1$s was incinerated by %2$s's fireball."}),
        BLUE_ARROW(GemType.BLUE,
                new String[]{
                        "%2$s hat %1$s mit einem Wasserpfeil des Blauen Gems durchbohrt.",
                        "%1$s wurde von einem Wasserpfeil von %2$s getroffen und ging unter.",
                        "%1$s ertrank an Land: Wasserpfeil von %2$s."},
                new String[]{
                        "%2$s skewered %1$s with a water arrow from the Blue Gem.",
                        "%1$s was shot down by %2$s's water arrow.",
                        "%1$s was drowned on dry land by %2$s."}),
        BLUE_WAVE(GemType.BLUE,
                new String[]{
                        "%1$s wurde von der Flutwelle von %2$s weggespült.",
                        "%2$s hat %1$s mit der Flutwelle des Blauen Gems überrollt.",
                        "%1$s konnte der Flutwelle von %2$s nicht entkommen."},
                new String[]{
                        "%1$s was swept away by %2$s's tidal wave.",
                        "%2$s washed %1$s away with the Blue Gem's wave.",
                        "%1$s couldn't escape %2$s's flood."}),
        BLUE_LANDING(GemType.BLUE,
                new String[]{
                        "%1$s stand im Weg, als %2$s mit dem Blauen Gem krachend landete.",
                        "%2$s landete mit einer Wasserwelle auf %1$s.",
                        "%1$s wurde von der Landung von %2$s zerschmettert."},
                new String[]{
                        "%1$s was in the way when %2$s crashed down with the Blue Gem.",
                        "%2$s landed on %1$s in a burst of water.",
                        "%1$s was crushed by %2$s's splashdown."}),
        YELLOW_LIGHTNING(GemType.YELLOW,
                new String[]{
                        "%1$s wurde von %2$s mit dem Blitz des Gelben Gems gegrillt.",
                        "%2$s ließ einen Blitz auf %1$s niederfahren.",
                        "%1$s wurde von %2$s vom Blitz getroffen und erstarrte für immer."},
                new String[]{
                        "%1$s was fried by %2$s's lightning from the Yellow Gem.",
                        "%2$s called down lightning on %1$s.",
                        "%1$s was struck frozen by %2$s's thunderbolt."}),
        PURPLE_SKELETON(GemType.PURPLE,
                new String[]{
                        "%1$s wurde von der Skelett-Armee des Lila Gems in Stücke gehackt.",
                        "%1$s wurde von beschworenen Skeletten des Lila Gems erledigt.",
                        "Die Skelette des Lila Gems haben %1$s zerlegt."},
                new String[]{
                        "%1$s was hacked to pieces by the Purple Gem's skeleton army.",
                        "%1$s was finished off by skeletons summoned by the Purple Gem.",
                        "The Purple Gem's skeletons tore %1$s apart."}),
        PURPLE_BLIND(GemType.PURPLE,
                new String[]{
                        "%1$s wurde von %2$s geblendet und stolperte in den Tod.",
                        "%1$s sah nichts mehr: der Fluch des Lila Gems von %2$s.",
                        "%2$s hat %1$s mit dem Lila Gem in ewige Dunkelheit gestürzt."},
                new String[]{
                        "%1$s was blinded by %2$s and stumbled to their death.",
                        "%1$s never saw it coming thanks to %2$s's Purple Gem.",
                        "%2$s plunged %1$s into eternal darkness with the Purple Gem."});

        final GemType gem;
        final String[] de;
        final String[] en;

        Ability(GemType gem, String[] de, String[] en) {
            this.gem = gem;
            this.de = de;
            this.en = en;
        }
    }

    private record Hit(Ability ability, String attackerName, long at) {}

    private static final Map<UUID, Hit> HITS = new HashMap<>();

    /** Merkt sich, welche Gem-Attacke einen Spieler zuletzt getroffen hat. */
    public static void record(LivingEntity victim, Ability ability, Entity source) {
        if (!(victim instanceof Player)) return;
        long now = System.currentTimeMillis();
        if (HITS.size() > 200) HITS.values().removeIf(h -> now - h.at() > 60_000L);
        String name = source instanceof Player sp ? sp.getName() : null;
        HITS.put(victim.getUniqueId(), new Hit(ability, name, now));
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDeath(PlayerDeathEvent e) {
        Player victim = e.getEntity();
        Hit h = HITS.remove(victim.getUniqueId());
        if (h == null || System.currentTimeMillis() - h.at() > 8000L) return;

        // Nur wenn der Tod wirklich durch Magieschaden oder die Brandwirkung der Attacke kam
        EntityDamageEvent last = victim.getLastDamageCause();
        if (last == null) return;
        EntityDamageEvent.DamageCause c = last.getCause();
        if (c != EntityDamageEvent.DamageCause.MAGIC && c != EntityDamageEvent.DamageCause.FIRE_TICK
                && c != EntityDamageEvent.DamageCause.FIRE) return;

        e.deathMessage(null); // Vanilla-Nachricht unterdruecken
        int idx = ThreadLocalRandom.current().nextInt(h.ability().de.length);
        String killer = h.attackerName() != null ? h.attackerName() : "?";
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            boolean german = "de".equals(viewer.locale().getLanguage());
            viewer.sendMessage(build(h.ability(), german, idx, victim.getName(), killer));
        }
        Bukkit.getConsoleSender().sendMessage(build(h.ability(), false, idx, victim.getName(), killer));
    }

    private static Component build(Ability a, boolean german, int idx, String victim, String killer) {
        String tpl = german ? a.de[idx] : a.en[idx];
        String text = String.format(Locale.ROOT, tpl, victim, killer);
        return Component.text("☠ ", NamedTextColor.DARK_GRAY).append(Component.text(text, a.gem.color));
    }
}
