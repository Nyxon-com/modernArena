package com.valaryrpg.arena.hud;

import com.valaryrpg.arena.ValaryArenaPlugin;
import me.neznamy.tab.api.TabAPI;
import me.neznamy.tab.api.TabPlayer;
import me.neznamy.tab.api.scoreboard.Scoreboard;
import me.neznamy.tab.api.scoreboard.ScoreboardManager;
import org.bukkit.entity.Player;

/** Scoreboard TAB unica arena/dungeon. Soft-depend: se TAB manca, no-op. */
public final class TabScoreboards implements InstanceHud {

    private final ValaryArenaPlugin plugin;
    private Scoreboard board;

    public TabScoreboards(ValaryArenaPlugin plugin) {
        this.plugin = plugin;
    }

    public void show(Iterable<Player> players) {
        ScoreboardManager manager = manager();
        if (manager == null) {
            return;
        }
        Scoreboard scoreboard = board(manager);
        if (scoreboard == null) {
            return;
        }
        for (Player player : players) {
            TabPlayer tab = TabAPI.getInstance().getPlayer(player.getUniqueId());
            if (tab != null) {
                manager.showScoreboard(tab, scoreboard);
            }
        }
    }

    public void hide(Player player) {
        ScoreboardManager manager = manager();
        if (manager == null || player == null) {
            return;
        }
        TabPlayer tab = TabAPI.getInstance().getPlayer(player.getUniqueId());
        if (tab != null) {
            manager.resetScoreboard(tab);
        }
    }

    private Scoreboard board(ScoreboardManager manager) {
        if (this.board != null) {
            return this.board;
        }
        try {
            this.board = manager.createScoreboard(
                    "valary_hud",
                    this.plugin.messages().raw("scoreboard.title"),
                    this.plugin.messages().list("scoreboard.lines")
            );
            return this.board;
        } catch (Exception exception) {
            this.plugin.getLogger().warning("TAB scoreboard non creata: " + exception.getMessage());
            return null;
        }
    }

    private ScoreboardManager manager() {
        if (this.plugin.getServer().getPluginManager().getPlugin("TAB") == null) {
            return null;
        }
        try {
            return TabAPI.getInstance().getScoreboardManager();
        } catch (NoClassDefFoundError | Exception ignored) {
            return null;
        }
    }
}
