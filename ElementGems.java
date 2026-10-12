package elementgems;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

public final class ElementGems extends JavaPlugin {

    private static ElementGems instance;
    private GemManager manager;
    private HolderTracker tracker;
    private Abilities abilities;

    public static ElementGems get() {
        return instance;
    }

    public GemManager manager() {
        return manager;
    }

    public HolderTracker tracker() {
        return tracker;
    }

    public Abilities abilities() {
        return abilities;
    }

    /** Die Hauptwelt (Oberwelt), nach der sich der Tag-Wechsel richtet. */
    public World mainWorld() {
        return Bukkit.getWorlds().get(0);
    }

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();
        Keys.init(this);

        manager = new GemManager(this);
        tracker = new HolderTracker(this);
        GemListener gemListener = new GemListener(this);
        Quests quests = new Quests(this, manager);

        PluginManager pm = getServer().getPluginManager();
        abilities = new Abilities(this);
        pm.registerEvents(abilities, this);
        pm.registerEvents(gemListener, this);
        pm.registerEvents(new CombatListener(this), this);
        pm.registerEvents(quests, this);
        pm.registerEvents(new DeathMessages(), this);
        pm.registerEvents(new StructureListener(manager), this);

        PluginCommand gems = getCommand("gems");
        if (gems != null) {
            Commands cmd = new Commands(manager);
            gems.setExecutor(cmd);
            gems.setTabCompleter(cmd);
        }

        manager.start();
        tracker.start();
        gemListener.start();
        quests.start();
        getLogger().info("ElementGems aktiviert.");
    }

    @Override
    public void onDisable() {
        if (tracker != null) tracker.stop();
        if (manager != null) manager.save();
    }
}
