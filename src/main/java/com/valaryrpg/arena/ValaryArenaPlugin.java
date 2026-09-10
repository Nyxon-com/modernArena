package com.valaryrpg.arena;

import com.valaryrpg.arena.arena.ArenaCatalog;
import com.valaryrpg.arena.arena.ArenaListener;
import com.valaryrpg.arena.arena.ArenaQueue;
import com.valaryrpg.arena.arena.ArenaService;
import com.valaryrpg.arena.arena.MythicSpawners;
import com.valaryrpg.arena.arena.WorldCopier;
import com.valaryrpg.arena.command.ArenaCommand;
import com.valaryrpg.arena.command.PartyCommand;
import com.valaryrpg.arena.config.ConfigManager;
import com.valaryrpg.arena.message.MessageService;
import com.valaryrpg.arena.milestone.KillTracker;
import com.valaryrpg.arena.milestone.MilestoneCatalog;
import com.valaryrpg.arena.party.PartyService;
import com.valaryrpg.arena.sound.SoundService;
import com.valaryrpg.arena.storage.KillRepository;
import com.valaryrpg.arena.storage.StorageManager;
import com.valaryrpg.arena.util.ConsoleCommands;
import com.valaryrpg.arena.util.Tasks;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

/** Entry point: YAML, storage, cleanup arena_*, party e istanze. */
public final class ValaryArenaPlugin extends JavaPlugin {

    private ConfigManager configs;
    private MessageService messages;
    private SoundService sounds;
    private Tasks tasks;
    private StorageManager storage;
    private ConsoleCommands console;
    private PartyService parties;
    private ArenaCatalog arenaCatalog;
    private MilestoneCatalog milestoneCatalog;
    private ArenaService arenas;
    private KillTracker kills;

    @Override
    public void onLoad() {
        this.configs = new ConfigManager(this);
        this.configs.loadAll();
        this.messages = new MessageService(this);
        this.sounds = new SoundService(this);
    }

    @Override
    public void onEnable() {
        this.tasks = new Tasks(this);
        this.console = new ConsoleCommands();
        this.storage = new StorageManager(this);
        try {
            this.storage.connect();
        } catch (Exception exception) {
            getLogger().severe("Impossibile avviare lo storage: " + exception.getMessage());
            exception.printStackTrace();
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        this.parties = new PartyService(this);
        this.arenaCatalog = new ArenaCatalog(this);
        this.arenaCatalog.load();
        this.milestoneCatalog = new MilestoneCatalog(this);
        this.milestoneCatalog.load();
        this.arenas = new ArenaService(
                this,
                this.arenaCatalog,
                new ArenaQueue(),
                new WorldCopier(this.tasks),
                new MythicSpawners(this)
        );
        this.kills = new KillTracker(this, new KillRepository(this.storage), this.milestoneCatalog);
        this.parties.onDisband(this.arenas::partyGone);
        this.arenas.cleanup();

        getServer().getPluginManager().registerEvents(new ArenaListener(this, this.arenas, this.kills), this);
        bind("arena", new ArenaCommand(this));
        bind("party", new PartyCommand(this));
        getLogger().info(this.messages.brand("name") + " Arena " + getPluginMeta().getVersion() + " abilitato.");
    }

    @Override
    public void onDisable() {
        if (this.arenas != null) {
            this.arenas.shutdown();
        }
        if (this.storage != null) {
            this.storage.close();
        }
        getLogger().info("ValaryRPG-Arena disabilitato.");
    }

    public boolean reloadAll() {
        try {
            this.configs.loadAll();
            this.messages.reload();
            this.arenaCatalog.load();
            this.milestoneCatalog.load();
            return true;
        } catch (Exception exception) {
            getLogger().severe("Reload fallito: " + exception.getMessage());
            exception.printStackTrace();
            return false;
        }
    }

    public ConfigManager configs() {
        return this.configs;
    }

    public MessageService messages() {
        return this.messages;
    }

    public SoundService sounds() {
        return this.sounds;
    }

    public Tasks tasks() {
        return this.tasks;
    }

    public ConsoleCommands console() {
        return this.console;
    }

    public PartyService parties() {
        return this.parties;
    }

    public ArenaService arenas() {
        return this.arenas;
    }

    private void bind(String name, org.bukkit.command.TabExecutor executor) {
        PluginCommand command = getCommand(name);
        if (command == null) {
            getLogger().severe("Comando mancante in plugin.yml: " + name);
            return;
        }
        command.setExecutor(executor);
        command.setTabCompleter(executor);
        command.setAliases(this.configs.root().getStringList("commands." + name + "-aliases"));
    }
}
