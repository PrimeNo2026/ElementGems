package elementgems;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;

public final class Msg {
    private Msg() {}

    private static Component prefix() {
        return Component.text("[Gems] ", NamedTextColor.GOLD);
    }

    public static void to(CommandSender target, String text, TextColor color) {
        if (target == null) return;
        target.sendMessage(prefix().append(Component.text(text, color)));
    }

    public static void broadcast(String text, TextColor color) {
        if (!ElementGems.get().getConfig().getBoolean("broadcast", true)) return;
        Bukkit.broadcast(prefix().append(Component.text(text, color)));
    }
}
