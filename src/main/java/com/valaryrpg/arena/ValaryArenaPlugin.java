package com.valaryrpg.arena;

import com.valaryrpg.arena.arena.ArenaCatalog;
import com.valaryrpg.arena.arena.ArenaListener;
import com.valaryrpg.arena.arena.ArenaQueue;
import com.valaryrpg.arena.arena.ArenaService;
import com.valaryrpg.arena.arena.MythicSpawners;
import com.valaryrpg.arena.arena.SlimeInstances;
import com.valaryrpg.arena.arena.WorldCopier;
import com.valaryrpg.arena.command.ArenaCommand;
import com.valaryrpg.arena.command.CommandCooldowns;
import com.valaryrpg.arena.command.CommandGuard;
import com.valaryrpg.arena.command.DungeonCommand;
import com.valaryrpg.arena.command.MilestoneCommand;
import com.valaryrpg.arena.command.PartyCommand;
import com.valaryrpg.arena.config.ConfigManager;
import com.valaryrpg.arena.dungeon.DungeonCatalog;
import com.valaryrpg.arena.dungeon.DungeonListener;
import com.valaryrpg.arena.dungeon.DungeonService;
import com.valaryrpg.arena.hud.InstanceHud;
import com.valaryrpg.arena.hud.NoopHud;
import com.valaryrpg.arena.hud.TabScoreboards;
import com.valaryrpg.arena.message.MessageService;
import com.valaryrpg.arena.milestone.KillTracker;
import com.valaryrpg.arena.milestone.MilestoneCatalog;
import com.valaryrpg.arena.milestone.MilestoneGui;
import com.valaryrpg.arena.milestone.MilestoneListener;
import com.valaryrpg.arena.party.PartyService;
import com.valaryrpg.arena.placeholder.ValaryPlaceholders;
import com.valaryrpg.arena.sound.SoundService;
import com.valaryrpg.arena.storage.KillRepository;
import com.valaryrpg.arena.storage.StorageManager;
import com.valaryrpg.arena.util.ConsoleCommands;
import com.valaryrpg.arena.util.Tasks;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

/** Entry point: YAML, storage, cleanup arena_ e dungeon_, party e istanze. */
public final class ValaryArenaPlugin extends JavaPlugin {

    private ConfigManager configs;
    private MessageService messages;
    private SoundService sounds;
    private Tasks tasks;
    private StorageManager storage;
    private ConsoleCommands console;
    private CommandCooldowns cooldowns;
    private PartyService parties;
    private ArenaCatalog arenaCatalog;
    private DungeonCatalog dungeonCatalog;
    private MilestoneCatalog milestoneCatalog;
    private ArenaService arenas;
    private DungeonService dungeons;
    private KillTracker kills;
    private MilestoneGui milestoneGui;
    private ValaryPlaceholders placeholders;
    private InstanceHud hud;

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
        this.cooldowns = new CommandCooldowns(this);
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
        this.dungeonCatalog = new DungeonCatalog(this);
        this.dungeonCatalog.load();
        this.milestoneCatalog = new MilestoneCatalog(this);
        this.milestoneCatalog.load();
        MythicSpawners mythic = new MythicSpawners(this);
        WorldCopier copier = new WorldCopier(this.tasks, this.configs.instances().copyMaxConcurrent);
        SlimeInstances slime = new SlimeInstances(this);
        if (!slime.available()) {
            getLogger().warning("AdvancedSlimePaper assente: arene e dungeon non possono creare istanze.");
        }
        this.arenas = new ArenaService(
                this,
                this.arenaCatalog,
                new ArenaQueue(),
                copier,
                slime,
                mythic
        );
        this.dungeons = new DungeonService(
                this,
                this.dungeonCatalog,
                new ArenaQueue(),
                copier,
                slime,
                mythic
        );
        this.kills = new KillTracker(this, new KillRepository(this.storage), this.milestoneCatalog);
        this.parties.onDisband(party -> {
            this.arenas.partyGone(party);
            this.dungeons.partyGone(party);
        });
        this.parties.onMemberRemoved((party, member) -> {
            this.arenas.partyMemberGone(party, member);
            this.dungeons.partyMemberGone(party, member);
        });
        this.arenas.cleanup();
        this.dungeons.cleanup();

        this.kills.applyClaimRevision();
        this.milestoneGui = new MilestoneGui(this);
        getServer().getPluginManager().registerEvents(
                new ArenaListener(this.arenas, this.kills, this.parties, this.cooldowns),
                this
        );
        getServer().getPluginManager().registerEvents(new DungeonListener(this.dungeons, this.kills), this);
        getServer().getPluginManager().registerEvents(new MilestoneListener(this, this.kills), this);
        getServer().getPluginManager().registerEvents(this.milestoneGui, this);
        getServer().getPluginManager().registerEvents(new CommandGuard(this), this);
        this.kills.catchUpAll();
        this.placeholders = new ValaryPlaceholders(this);
        if (getServer().getPluginManager().getPlugin("PlaceholderAPI") != null) {
            this.placeholders.register();
        }
        this.hud = createHud();
        bind("arena", new ArenaCommand(this));
        bind("dungeon", new DungeonCommand(this));
        bind("party", new PartyCommand(this));
        bind("milestones", new MilestoneCommand(this, this.milestoneGui));
        getLogger().info(this.messages.brand("name") + " Arena " + getPluginMeta().getVersion() + " abilitato.");
    }

    @Override
    public void onDisable() {
        long shutdownTimeout = this.configs == null
                ? 10000L
                : this.configs.storage().getLong("pool.connection-timeout-millis", 10000L);
        if (this.tasks != null) {
            this.tasks.shutdown();
        }
        if (this.kills != null) {
            this.kills.shutdown(shutdownTimeout);
        }
        if (this.arenas != null) {
            this.arenas.shutdown(shutdownTimeout);
        }
        if (this.dungeons != null) {
            this.dungeons.shutdown(shutdownTimeout);
        }
        if (this.parties != null) {
            this.parties.shutdown();
        }
        if (this.cooldowns != null) {
            this.cooldowns.clear();
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
            this.dungeonCatalog.load();
            this.milestoneCatalog.load();
            this.kills.applyClaimRevision();
            this.kills.catchUpAll();
            this.hud().reload();
            reshowHud();
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

    public CommandCooldowns cooldowns() {
        return this.cooldowns;
    }

    public PartyService parties() {
        return this.parties;
    }

    public ArenaService arenas() {
        return this.arenas;
    }

    public DungeonService dungeons() {
        return this.dungeons;
    }

    public ValaryPlaceholders placeholders() {
        return this.placeholders;
    }

    public InstanceHud hud() {
        return this.hud == null ? new NoopHud() : this.hud;
    }

    private void reshowHud() {
        if (this.arenas != null && this.parties != null) {
            for (var instance : this.arenas.activeInstances()) {
                this.hud().showArena(this.parties.onlineMembers(instance.party()));
            }
        }
        if (this.dungeons != null && this.parties != null) {
            for (var instance : this.dungeons.activeInstances()) {
                this.hud().showDungeon(this.parties.onlineMembers(instance.party()));
            }
        }
    }

    private InstanceHud createHud() {
        if (getServer().getPluginManager().getPlugin("TAB") == null) {
            return new NoopHud();
        }
        try {
            Class.forName("me.neznamy.tab.api.TabAPI");
            return new TabScoreboards(this);
        } catch (ClassNotFoundException | NoClassDefFoundError ignored) {
            getLogger().warning("TAB presente ma API non trovata: scoreboard istanza disattivata.");
            return new NoopHud();
        }
    }

    public int loadedInstances() {
        int loaded = 0;
        if (this.arenas != null) {
            loaded += this.arenas.loadedCount();
        }
        if (this.dungeons != null) {
            loaded += this.dungeons.loadedCount();
        }
        return loaded;
    }

    public KillTracker kills() {
        return this.kills;
    }

    public MilestoneGui milestoneGui() {
        return this.milestoneGui;
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
