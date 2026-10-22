//made with love by nyxon

package com.modern.arena.hud;

import com.modern.arena.ModernArenaPlugin;
import com.modern.arena.placeholder.ModernPlaceholders;
import me.neznamy.tab.api.TabAPI;
import me.neznamy.tab.api.TabPlayer;
import me.neznamy.tab.api.placeholder.PlaceholderManager;
import me.neznamy.tab.api.scoreboard.Scoreboard;
import me.neznamy.tab.api.scoreboard.ScoreboardManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.List;

public final class TabScoreboards implements InstanceHud {

  private static final List<String> KEYS = List.of(
      "name", "time", "remaining", "room", "wave", "waves", "type"
  );

  private final ModernArenaPlugin plugin;
  private Scoreboard arenaBoard;
  private Scoreboard dungeonBoard;
  private boolean placeholdersBound;

  public TabScoreboards(ModernArenaPlugin plugin) {
    this.plugin = plugin;
    bindPlaceholders();
  }

  @Override
  public void showArena(Iterable<Player> players) {
    show(players, arenaBoard(), "scoreboard.arena.enabled");
  }

  @Override
  public void showDungeon(Iterable<Player> players) {
    show(players, dungeonBoard(), "scoreboard.dungeon.enabled");
  }

  @Override
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

  @Override
  public void reload() {
    drop(this.arenaBoard);
    drop(this.dungeonBoard);
    this.arenaBoard = null;
    this.dungeonBoard = null;
    bindPlaceholders();
  }

  private void show(Iterable<Player> players, Scoreboard scoreboard, String enabledPath) {
    if (!this.plugin.configs().messages().getBoolean(enabledPath, true) || scoreboard == null) {
      return;
    }
    ScoreboardManager manager = manager();
    if (manager == null) {
      return;
    }
    for (Player player : players) {
      TabPlayer tab = TabAPI.getInstance().getPlayer(player.getUniqueId());
      if (tab != null) {
        manager.showScoreboard(tab, scoreboard);
      }
    }
  }

  private Scoreboard arenaBoard() {
    if (this.arenaBoard != null) {
      return this.arenaBoard;
    }
    this.arenaBoard = create("valary_arena", "scoreboard.arena.title", "scoreboard.arena.lines", "scoreboard.title");
    return this.arenaBoard;
  }

  private Scoreboard dungeonBoard() {
    if (this.dungeonBoard != null) {
      return this.dungeonBoard;
    }
    this.dungeonBoard = create(
        "valary_dungeon",
        "scoreboard.dungeon.title",
        "scoreboard.dungeon.lines",
        "scoreboard.title"
    );
    return this.dungeonBoard;
  }

  private Scoreboard create(String id, String titlePath, String linesPath, String titleFallback) {
    ScoreboardManager manager = manager();
    if (manager == null) {
      return null;
    }
    String title = this.plugin.messages().raw(titlePath);
    if (title == null || title.isBlank()) {
      title = this.plugin.messages().raw(titleFallback);
    }
    List<String> lines = this.plugin.messages().list(linesPath);
    if (lines.isEmpty()) {
      lines = this.plugin.messages().list("scoreboard.lines");
    }
    try {
      return manager.createScoreboard(id, title, lines);
    } catch (Exception exception) {
      this.plugin.getLogger().warning("TAB scoreboard non creata: " + exception.getMessage());
      return null;
    }
  }

  private void drop(Scoreboard board) {
    if (board == null) {
      return;
    }
    ScoreboardManager manager = manager();
    if (manager == null) {
      return;
    }
    try {
      manager.removeScoreboard(board);
    } catch (IllegalArgumentException ignored) {
    }
  }

  private void bindPlaceholders() {
    if (this.placeholdersBound) {
      return;
    }
    try {
      PlaceholderManager placeholders = TabAPI.getInstance().getPlaceholderManager();
      if (placeholders == null) {
        return;
      }
      for (String key : KEYS) {
        placeholders.registerPlayerPlaceholder("%valary_" + key + "%", 200, tab -> value(tab, key));
      }
      this.placeholdersBound = true;
    } catch (NoClassDefFoundError | Exception exception) {
      this.plugin.getLogger().warning("TAB placeholder non registrati: " + exception.getMessage());
    }
  }

  private String value(TabPlayer tab, String key) {
    ModernPlaceholders placeholders = this.plugin.placeholders();
    if (placeholders == null || tab == null) {
      return "";
    }
    Player player = Bukkit.getPlayer(tab.getUniqueId());
    if (player == null) {
      return "";
    }
    String resolved = placeholders.onRequest(player, key);
    return resolved == null ? "" : resolved;
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
