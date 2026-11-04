package com.modern.arena.hud;

import org.bukkit.entity.Player;

public final class NoopHud implements InstanceHud {

  @Override
  public void showArena(Iterable<Player> players) {
  }

  @Override
  public void showDungeon(Iterable<Player> players) {
  }

  @Override
  public void hide(Player player) {
  }
}
