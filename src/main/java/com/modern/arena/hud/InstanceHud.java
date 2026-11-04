package com.modern.arena.hud;

import org.bukkit.entity.Player;

public interface InstanceHud {

  void showArena(Iterable<Player> players);

  void showDungeon(Iterable<Player> players);

  void hide(Player player);

  default void reload() {
  }
}
