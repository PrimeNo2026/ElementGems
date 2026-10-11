package elementgems;

import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;

import java.util.Locale;

public enum GemType {
    RED("red", "Roter Gem", "Rot", NamedTextColor.RED, Material.RED_STAINED_GLASS,
            "Feuerring", "Feuerball", "Feuerresistenz", "ring", "fireball",
            "Besuche alle fünf Nether-Biome"),
    BLUE("blue", "Blauer Gem", "Blau", NamedTextColor.AQUA, Material.BLUE_STAINED_GLASS,
            "Flutwelle", "Wasser-Boost", "Wasseratmung und Delfingunst", "wave", "boost",
            "Erhalte den Fortschritt \"Bring Home the Beacon\""),
    YELLOW("yellow", "Gelber Gem", "Gelb", NamedTextColor.YELLOW, Material.YELLOW_STAINED_GLASS,
            "Blitzschlag", "Teleport", "Eile II und Speed", "lightning", "teleport",
            "Verwandle einen Dorfbewohner mit einem Channeling-Dreizack in eine Hexe"),
    PURPLE("purple", "Lila Gem", "Lila", NamedTextColor.DARK_PURPLE, Material.PURPLE_STAINED_GLASS,
            "Skelett-Krieger beschwören", "Erblinden", "Immun gegen Gift und Wither", "skeletons", "blind",
            "Respawne den Enderdrachen und besiege ihn");

    public final String id;
    public final String displayName;
    public final String colorWord;
    public final NamedTextColor color;
    public final Material glass;
    public final String ability1;
    public final String ability2;
    public final String passive;
    public final String cdKey1;
    public final String cdKey2;
    public final String quest;

    GemType(String id, String displayName, String colorWord, NamedTextColor color, Material glass,
            String ability1, String ability2, String passive, String cdKey1, String cdKey2, String quest) {
        this.id = id;
        this.displayName = displayName;
        this.colorWord = colorWord;
        this.color = color;
        this.glass = glass;
        this.ability1 = ability1;
        this.ability2 = ability2;
        this.passive = passive;
        this.cdKey1 = cdKey1;
        this.cdKey2 = cdKey2;
        this.quest = quest;
    }

    public static GemType fromId(String id) {
        for (GemType g : values()) if (g.id.equals(id)) return g;
        return null;
    }

    /** Akzeptiert englische und deutsche Farbnamen. */
    public static GemType parse(String s) {
        if (s == null) return null;
        switch (s.toLowerCase(Locale.ROOT)) {
            case "red", "rot" -> { return RED; }
            case "blue", "blau" -> { return BLUE; }
            case "yellow", "gelb" -> { return YELLOW; }
            case "purple", "lila", "violett" -> { return PURPLE; }
            default -> { return null; }
        }
    }
}
