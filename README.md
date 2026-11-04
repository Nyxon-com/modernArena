# modernArena

Arene e dungeon istanziati per Paper, separati da modernCore. Un template unloaded viene copiato in `arena_<boss>_<slot>` o `dungeon_<id>_<slot>`. Poi party, coda, kill e milestone su SQL.

Package `com.modern.arena`. Repo `Nyxon-com/modernArena`.

## Stack

- Paper 1.21.11 (`paper-api` 1.21.11-R0.1-SNAPSHOT, `api-version` 1.21), Java 25, Gradle Shadow, MiniMessage, HikariCP (SQLite / MySQL / MariaDB)
- Il nome in `plugin.yml` è `ValaryRPG-Arena`. Il repository e il package restano `modernArena` / `com.modern.arena`
- Dipendenza obbligatoria: MythicMobs
- Opzionali: modernCore, Vault, PlaceholderAPI, Multiverse-Core, TAB (scoreboard)
- Hologram: `TextDisplay` Paper (niente plugin extra)
- Kit/eco Core: `[console] …` in YAML, mai `compileOnly` sul Core

## Install

1. Metti `modernArena-1.0.0.jar` in `plugins/` (MythicMobs deve essere presente).
2. Template mondo **non caricati** nella world container del server.
3. Sul Core: `worlds.ignore-prefixes: ["arena_", "dungeon_"]`. Multiverse non deve auto-importare quei mondi.
4. Produzione con tante kill: `storage.yml` → `type: mysql` (o `mariadb`). SQLite solo per test.

Build: `gradlew.bat shadowJar` → `build/libs/modernArena-1.0.0.jar`.

## Comandi

| Comando | Cosa fa |
|---------|---------|
| `/arena start <boss>` | Avvia (leader se in party) |
| `/arena list` | Lista arene |
| `/arena leave` | Esci |
| `/arena stats [player] [boss]` | Kill SQL. Altri: `valary.arena.stats.other` |
| `/arena milestones [player]` | Milestone |
| `/arena reload` | Ricarica YAML + cataloghi. Non tocca istanze vive né il DB |
| `/dungeon start <id>` | Avvia dungeon (leader se in party) |
| `/dungeon list` / `leave` | Lista / esci |
| `/party` | create, invite, accept, leave, kick, disband |

Un player non sta in arena e dungeon insieme. Bypass cooldown: `valary.arena.cooldown.bypass`. Bypass comandi bloccati: `valary.arena.command.bypass`.

## YAML

| File | Ruolo |
|------|--------|
| `config.yml` | Sottocomandi, permessi, tetto mondi `instances.max-loaded` (default 16) |
| `lang/messages.yml` | Testi MiniMessage, brand, hologram, `milestone.progress` |
| `sounds/sounds.yml` | Suoni |
| `storage.yml` | DB, `table-prefix: valary_arena_` |
| `modules/arenas.yml` | Boss, spawners Mythic, hologram, cap, cooldown |
| `modules/dungeons.yml` | Dungeon, stanze, pack, wall, intro/outro, leash, holo porta |
| `modules/party.yml` | Timeout invite, max size |
| `modules/milestones.yml` | Soglie per Mythic name o id boss |

Brand (prefix/separator viola `#5F22C3` → `#40099B`) solo in `lang/messages.yml`.

### Arena

- Spawner: clona Mythic con `source-spawner` + `mob:` per kill/milestone.
- Cap `max-bosses` → ferma spawner, poi TP fuori.
- Cooldown rientro stessa arena / altra arena.
- Hologram: warmup, boss uccisi, top danno del boss precedente.

### Dungeon

Stanze in ordine YAML. Stanza 1 = TP spawn. Obiettivo `clear-mobs` o `boss`.

- Ondate: posizioni `mobs.<id>` con `trigger: wave`, poi `waves:` lista (`gate: 3` = 3 da quel punto). Default in `settings.waves`. Glow ultimo minion. `radius` solo boss.
- Spawn safe: `settings.spawn-safe-radius` (default 6) intorno allo spawn stanza 1 — i pack non spawnano lì.
- Clear stanza: pack esausti **e** 0 living. Poi fill `wall` (`open-block`, default air), `outro`, `commands`. `close-block` (default `iron_bars`) si chiude quando tutto il party vivo ha superato la porta (`close-on: pass`). Override per stanza o in `settings.wall`.
- `start-on: radius` — i minion aspettano che un player sia vicino alla porta/`start-radius`. `immediate` = spawn subito.
- Hologram: all’ingresso spawn stanza 1 + `spawn-forward`. Poi `follow-door` vola al wall di ogni stanza.
- Leash / heal-on-leash. Tempo istanza: `duration-seconds` (0 = off); fail senza reward.
- Scoreboard TAB: `scoreboard.arena` e `scoreboard.dungeon` (`enabled: false` per off). PAPI `%valary_time%` `%valary_remaining%` `%valary_wave%`.
- Comandi vietati: `config.yml` `blocked-commands`. Bypass `valary.arena.command.bypass`.

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
  ingresso:
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

## Scala (8 GB, ~20 player)

Tetto totale mondi: `config.yml` → `instances.max-loaded: 16`. 4 slot per boss/dungeon, poi coda. Copy mondo max 1 alla volta.

Heap `-Xmx5G`. Template piccoli. Muri dungeon = cubo stretto. Produzione kill: MySQL.

## Fuori scope

Editor in-game, shop, wave AMA, spectator, party chat, friendly fire, PAPI expansion, dipendenza Java Core, plugin hologram terzi, editor MythicDungeons.

Guida Cursor (flussi Java, hotspot, convenzioni): `AGENTS.md`.

## Licenza

MIT. Copyright (c) 2026 Nyxon-com. Vedi [LICENSE](LICENSE).
