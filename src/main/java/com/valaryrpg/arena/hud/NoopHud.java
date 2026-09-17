package com.valaryrpg.arena.hud;

import org.bukkit.entity.Player;

/** Nessun TAB: scoreboard del server resta. */
public final class NoopHud implements InstanceHud {

    @Override
    public void show(Iterable<Player> players) {
    }

    @Override
    public void hide(Player player) {
    }
}
