//made with love by nyxon

package com.modern.arena.arena;

import com.modern.arena.ModernArenaPlugin;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.TextDisplay;

import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class ArenaHolograms {

  private final ModernArenaPlugin plugin;
  private final MythicSpawners mythic;
  private final ArenaCatalog catalog;

  public ArenaHolograms(ModernArenaPlugin plugin, MythicSpawners mythic, ArenaCatalog catalog) {
    this.plugin = plugin;
    this.mythic = mythic;
    this.catalog = catalog;
  }

  public void spawn(ArenaInstance instance, ArenaDefinition definition, World world) {
    remove(instance);
    if (!definition.hologram.enabled) {
      return;
    }
    Location location = definition.hologram.point != null
        ? definition.hologram.point.to(world)
        : definition.spawn.to(world).add(0, definition.hologram.offsetY, 0);
    TextDisplay display = world.spawn(location, TextDisplay.class, entity -> {
      entity.setBillboard(Display.Billboard.CENTER);
      entity.setAlignment(TextDisplay.TextAlignment.CENTER);
      entity.setDefaultBackground(false);
      entity.setShadowed(true);
      entity.setSeeThrough(false);
      entity.setPersistent(false);
      entity.setGravity(false);
      entity.setInvulnerable(true);
      entity.addScoreboardTag("valary_arena_holo");
    });
    instance.hologram(display);
    refresh(instance, definition);
    instance.hologramTask(this.plugin.tasks().repeat(
        () -> refresh(instance, definition),
        definition.hologram.updateTicks,
        definition.hologram.updateTicks
    ));
  }

  public void refresh(ArenaInstance instance, ArenaDefinition definition) {
    if (instance.state() != SlotState.ACTIVE) {
      return;
    }
    TextDisplay display = instance.hologram();
    if (display == null || display.isDead()) {
      return;
    }
    String warmup = warmup(instance, definition);
    String fingerprint = warmup
        + "|" + instance.bossesKilled()
        + "|" + instance.previousTopDamager()
        + "|" + formatDamage(instance.previousTopDamage());
    if (fingerprint.equals(instance.hologramFingerprint())) {
      return;
    }
    instance.hologramFingerprint(fingerprint);
    display.text(lines(instance, definition, warmup));
  }

  public void remove(ArenaInstance instance) {
    if (instance.hologramTask() != null) {
      instance.hologramTask().cancel();
      instance.hologramTask(null);
    }
    TextDisplay display = instance.hologram();
    if (display != null) {
      display.remove();
      instance.hologram(null);
    }
  }

  private Component lines(ArenaInstance instance, ArenaDefinition definition, String warmup) {
    Map<String, String> placeholders = Map.of(
        "boss", definition.displayName == null || definition.displayName.isEmpty()
            ? definition.id : definition.displayName,
        "warmup", warmup,
        "boss-kills", Integer.toString(instance.bossesKilled()),
        "max-bosses", definition.maxBosses > 0
            ? Integer.toString(definition.maxBosses)
            : this.plugin.messages().raw("hologram.unlimited"),
        "top-damager", instance.previousTopDamager() == null || instance.previousTopDamager().isEmpty()
            ? this.plugin.messages().raw("hologram.none")
            : instance.previousTopDamager(),
        "top-damage", instance.previousTopDamage() <= 0
            ? this.plugin.messages().raw("hologram.none")
            : formatDamage(instance.previousTopDamage())
    );
    List<String> raw = this.plugin.messages().list("hologram.lines");
    Component result = Component.empty();
    for (int index = 0; index < raw.size(); index++) {
      if (index > 0) {
        result = result.append(Component.newline());
      }
      result = result.append(this.plugin.messages().mini(raw.get(index), placeholders));
    }
    return result;
  }

  private String warmup(ArenaInstance instance, ArenaDefinition definition) {
    if (instance.spawnersHalted()) {
      return this.plugin.messages().apply(this.plugin.messages().raw("hologram.warmup-done"), Map.of());
    }
    if (aliveTracked(instance, definition) > 0) {
      return this.plugin.messages().apply(this.plugin.messages().raw("hologram.warmup-combat"), Map.of());
    }
    int seconds = this.mythic.waitSeconds(instance.spawnerNames());
    if (seconds > 0) {
      return this.plugin.messages().apply(
          this.plugin.messages().raw("hologram.warmup-wait"),
          Map.of("seconds", Integer.toString(seconds))
      );
    }
    return this.plugin.messages().apply(this.plugin.messages().raw("hologram.warmup-ready"), Map.of());
  }

  private int aliveTracked(ArenaInstance instance, ArenaDefinition definition) {
    World world = instance.hologram() == null ? null : instance.hologram().getWorld();
    if (world == null) {
      return 0;
    }
    return this.mythic.countInWorld(world, name -> this.catalog.tracksMob(definition, name));
  }

  private String formatDamage(double damage) {
    if (Math.abs(damage - Math.rint(damage)) < 0.05D) {
      return Integer.toString((int) Math.rint(damage));
    }
    return String.format(Locale.ROOT, "%.1f", damage);
  }
}
