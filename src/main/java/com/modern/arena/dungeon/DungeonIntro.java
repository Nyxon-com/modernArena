package com.modern.arena.dungeon;

public final class DungeonIntro {

  public final String title;
  public final String subtitle;
  public final String actionbar;
  public final int fadeInTicks;
  public final int stayTicks;
  public final int fadeOutTicks;

  public DungeonIntro(
      String title,
      String subtitle,
      String actionbar,
      int fadeInTicks,
      int stayTicks,
      int fadeOutTicks
  ) {
    this.title = title == null ? "" : title;
    this.subtitle = subtitle == null ? "" : subtitle;
    this.actionbar = actionbar == null ? "" : actionbar;
    this.fadeInTicks = Math.max(0, fadeInTicks);
    this.stayTicks = Math.max(1, stayTicks);
    this.fadeOutTicks = Math.max(0, fadeOutTicks);
  }
}
