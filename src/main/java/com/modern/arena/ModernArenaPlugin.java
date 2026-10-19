package com.modern.arena;

import com.modern.arena.arena.ArenaCatalog;
import com.modern.arena.arena.ArenaListener;
import com.modern.arena.arena.ArenaQueue;
import com.modern.arena.arena.ArenaService;
import com.modern.arena.arena.MythicSpawners;
import com.modern.arena.arena.SlimeInstances;
import com.modern.arena.arena.WorldCopier;
import com.modern.arena.command.ArenaCommand;
import com.modern.arena.command.CommandCooldowns;
import com.modern.arena.command.CommandGuard;
import com.modern.arena.command.DungeonCommand;
import com.modern.arena.command.MilestoneCommand;
import com.modern.arena.command.PartyCommand;
import com.modern.arena.config.ConfigManager;
import com.modern.arena.dungeon.DungeonCatalog;
import com.modern.arena.dungeon.DungeonListener;
import com.modern.arena.dungeon.DungeonService;
import com.modern.arena.hud.InstanceHud;
import com.modern.arena.hud.NoopHud;
import com.modern.arena.hud.TabScoreboards;
import com.modern.arena.message.MessageService;
import com.modern.arena.milestone.KillTracker;
import com.modern.arena.milestone.MilestoneCatalog;
import com.modern.arena.milestone.MilestoneGui;
import com.modern.arena.milestone.MilestoneListener;
import com.modern.arena.party.PartyService;
import com.modern.arena.placeholder.ModernPlaceholders;
import com.modern.arena.sound.SoundService;
import com.modern.arena.storage.KillRepository;
import com.modern.arena.storage.StorageManager;
import com.modern.arena.util.ConsoleCommands;
import com.modern.arena.util.Tasks;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

/** Entry point: YAML, storage, cleanup arena_ e dungeon_, party e istanze. */
public final class ModernArenaPlugin extends JavaPlugin {

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
    private ModernPlaceholders placeholders;
    private InstanceHud hud;

    @Override
    public void onLoad() {
        adoptDataFolder("ValaryRPG-Arena");
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
        this.placeholders = new ModernPlaceholders(this);
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
        getLogger().info("modernArena disabilitato.");
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

    public ModernPlaceholders placeholders() {
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

    private void adoptDataFolder(String legacyName) {
        java.io.File target = getDataFolder();
        java.io.File parent = target.getParentFile();
        if (parent == null) {
            return;
        }
        java.io.File legacy = new java.io.File(parent, legacyName);
        if (!legacy.isDirectory()) {
            return;
        }
        if (target.exists()) {
            String[] kids = target.list();
            if (kids != null && kids.length > 0) {
                return;
            }
            if (!target.delete()) {
                return;
            }
        }
        if (!legacy.renameTo(target)) {
            getLogger().warning("Impossibile rinominare " + legacy.getPath() + " in " + target.getPath());
        }
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
