package com.valaryrpg.arena.hud;

import org.bukkit.entity.Player;

/** Scoreboard in istanza. TAB se presente, altrimenti no-op. */
public interface InstanceHud {

    void show(Iterable<Player> players);

    void hide(Player player);
}
