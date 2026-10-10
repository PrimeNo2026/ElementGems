package elementgems;

import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** /gems status | reset <Farbe> | give <Farbe> [Spieler]  (nur Admins, fuer Notfaelle und Tests) */
public final class Commands implements CommandExecutor, TabCompleter {

    private final GemManager gm;

    public Commands(GemManager gm) {
        this.gm = gm;
    }

    @Override
    public boolean onCommand(CommandSender s, Command c, String label, String[] a) {
        if (!s.hasPermission("elementgems.admin")) {
            Msg.to(s, "Dafuer hast du keine Berechtigung.", NamedTextColor.RED);
            return true;
        }
        if (a.length == 0) {
            Msg.to(s, "/gems status | reset <Farbe> | give <Farbe> [Spieler]", NamedTextColor.YELLOW);
            return true;
        }
        switch (a[0].toLowerCase(Locale.ROOT)) {
            case "status" -> {
                for (GemType g : GemType.values()) Msg.to(s, g.displayName + ": " + gm.statusText(g), g.color);
            }
            case "reset" -> {
                if (a.length < 2) {
                    Msg.to(s, "Nutzung: /gems reset <red|blue|yellow|purple|all>", NamedTextColor.YELLOW);
                } else if (a[1].equalsIgnoreCase("all")) {
                    for (GemType g : GemType.values()) gm.reset(g, s);
                } else {
                    GemType g = GemType.parse(a[1]);
                    if (g == null) Msg.to(s, "Unbekannte Farbe.", NamedTextColor.RED);
                    else gm.reset(g, s);
                }
            }
            case "give" -> {
                if (a.length < 2) {
                    Msg.to(s, "Nutzung: /gems give <Farbe> [Spieler]", NamedTextColor.YELLOW);
                    return true;
                }
                GemType g = GemType.parse(a[1]);
                if (g == null) {
                    Msg.to(s, "Unbekannte Farbe.", NamedTextColor.RED);
                    return true;
                }
                Player target = a.length >= 3 ? Bukkit.getPlayerExact(a[2]) : (s instanceof Player p ? p : null);
                if (target == null) Msg.to(s, "Spieler nicht gefunden.", NamedTextColor.RED);
                else gm.give(g, target, s);
            }
            default -> Msg.to(s, "/gems status | reset <Farbe> | give <Farbe> [Spieler]", NamedTextColor.YELLOW);
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command c, String label, String[] a) {
        List<String> out = new ArrayList<>();
        if (a.length == 1) {
            for (String o : new String[]{"status", "reset", "give"}) if (o.startsWith(a[0].toLowerCase(Locale.ROOT))) out.add(o);
        } else if (a.length == 2 && (a[0].equalsIgnoreCase("reset") || a[0].equalsIgnoreCase("give"))) {
            for (GemType g : GemType.values()) out.add(g.id);
            if (a[0].equalsIgnoreCase("reset")) out.add("all");
            out.removeIf(o -> !o.startsWith(a[1].toLowerCase(Locale.ROOT)));
        } else if (a.length == 3 && a[0].equalsIgnoreCase("give")) {
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.getName().toLowerCase(Locale.ROOT).startsWith(a[2].toLowerCase(Locale.ROOT))) out.add(p.getName());
            }
        }
        return out;
    }
}
