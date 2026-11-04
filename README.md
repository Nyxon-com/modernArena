# modernArena

Instanced arenas and dungeons for Paper, separate from modernCore. An unloaded template is copied to `arena_<boss>_<slot>` or `dungeon_<id>_<slot>`. Then party, queue, kills, and milestones in SQL.

Package `com.modern.arena`. Repository `Nyxon-com/modernArena`.

## Stack

- Paper 1.21.11 (`paper-api` 1.21.11-R0.1-SNAPSHOT, `api-version` 1.21), Java 25, Gradle Shadow, MiniMessage, HikariCP (SQLite / MySQL / MariaDB)
- The name in `plugin.yml` is still `ValaryRPG-Arena`. The repository and the package stay `modernArena` / `com.modern.arena`
- Required: MythicMobs
- Optional: modernCore, Vault, PlaceholderAPI, Multiverse-Core, TAB (scoreboard)
- Holograms: Paper `TextDisplay` (no extra plugin)
- Core kits and economy: `[console] …` in YAML, never a `compileOnly` on Core

## Install

1. Put `modernArena-1.0.0.jar` in `plugins/` (MythicMobs must be present).
2. World templates stay **unloaded** in the server world container.
3. On Core: `worlds.ignore-prefixes: ["arena_", "dungeon_"]`. Multiverse must not auto-import those worlds.
4. Production with a lot of kills: `storage.yml` → `type: mysql` (or `mariadb`). SQLite is for tests.

Build: `gradlew.bat shadowJar` → `build/libs/modernArena-1.0.0.jar`.

## Commands

| Command | What it does |
|---------|---------|
| `/arena start <boss>` | Starts it (the leader, if you are in a party) |
| `/arena list` | Lists arenas |
| `/arena leave` | Leave |
| `/arena stats [player] [boss]` | SQL kills. Other players: `valary.arena.stats.other` |
| `/arena milestones [player]` | Milestones |
| `/arena reload` | Reloads YAML and catalogs. Does not touch live instances or the database |
| `/dungeon start <id>` | Starts a dungeon (the leader, if you are in a party) |
| `/dungeon list` / `leave` | List / leave |
| `/party` | create, invite, accept, leave, kick, disband |

A player is never in an arena and a dungeon at the same time. Cooldown bypass: `valary.arena.cooldown.bypass`. Blocked-command bypass: `valary.arena.command.bypass`.

## YAML

| File | Role |
|------|--------|
| `config.yml` | Subcommands, permissions, world cap `instances.max-loaded` (default 16) |
| `lang/messages.yml` | MiniMessage text, brand, holograms, `milestone.progress` |
| `sounds/sounds.yml` | Sounds |
| `storage.yml` | Database, `table-prefix: valary_arena_` |
| `modules/arenas.yml` | Bosses, Mythic spawners, holograms, cap, cooldown |
| `modules/dungeons.yml` | Dungeons, rooms, packs, walls, intro/outro, leash, door hologram |
| `modules/party.yml` | Invite timeout, max size |
| `modules/milestones.yml` | Thresholds by Mythic name or boss id |

Brand (purple prefix and separator `#5F22C3` → `#40099B`) lives only in `lang/messages.yml`.

### Arena

- Spawners: clone a Mythic spawner with `source-spawner` plus `mob:` for kills and milestones.
- Cap `max-bosses` → stop the spawner, then teleport out.
- Cooldown for re-entering the same arena, and for entering another one.
- Holograms: warmup, bosses killed, top damage on the previous boss.

### Dungeon

Rooms follow the YAML order. Room 1 is the teleport spawn. The objective is `clear-mobs` or `boss`.

- Waves: positions under `mobs.<id>` with `trigger: wave`, then a `waves:` list (`gate: 3` means 3 from that point). Defaults in `settings.waves`. The last minion glows. `radius` is for the boss only.
- Safe spawn: `settings.spawn-safe-radius` (default 6) around room 1's spawn. Packs do not spawn there.
- Room clear: packs exhausted **and** 0 living. Then fill `wall` (`open-block`, default air), `outro`, `commands`. `close-block` (default `iron_bars`) closes once every living party member has passed the door (`close-on: pass`). Override per room or in `settings.wall`.
- `start-on: radius` — minions wait until a player is near the door / `start-radius`. `immediate` spawns at once.
- Hologram: at room 1's spawn plus `spawn-forward`. Then `follow-door` flies to each room's wall.
- Leash / heal-on-leash. Instance time: `duration-seconds` (`0` = off); failure gives no reward.
- TAB scoreboard: `scoreboard.arena` and `scoreboard.dungeon` (`enabled: false` to turn off). PlaceholderAPI `%valary_time%` `%valary_remaining%` `%valary_wave%`.
- Forbidden commands: `config.yml` `blocked-commands`. Bypass `valary.arena.command.bypass`.

```yaml
settings:
  wall:
    open-block: air
    close-block: iron_bars
    open-on: clear
    close-on: pass
  waves:
    delay-seconds: 3
    require-clear: true
    highlight-last: true
rooms:
  entrance:
    wall:
      from: {x: 8, y: 64, z: 4}
      to: {x: 8, y: 66, z: 6}
      open-block: air
      close-block: iron_bars
      close-faces: [north, south]
    mobs:
      gate:
        trigger: wave
        mob: TerraMinion
        x: 4.5
        y: 64.0
        z: 4.5
      left:
        trigger: wave
        mob: TerraMinion
        x: -4.5
        y: 64.0
        z: 4.5
    waves:
      - gate: 3
        left: 2
      - gate: 4
        left: 1
```

## Scale (8 GB, about 20 players)

Total world cap: `config.yml` → `instances.max-loaded: 16`. 4 slots per boss or dungeon, then a queue. World copy runs one at a time.

Heap `-Xmx5G`. Keep templates small. Dungeon walls are a tight cuboid. Production kills: MySQL.

## Out of scope

In-game editor, shop, AMA waves, spectator, party chat, friendly fire, a PlaceholderAPI expansion, a Java dependency on Core, third-party hologram plugins, a MythicDungeons editor.

Cursor guide (Java flows, hotspots, conventions): `AGENTS.md`.

## License

MIT. Copyright (c) 2026 Nyxon-com. See [LICENSE](LICENSE).
