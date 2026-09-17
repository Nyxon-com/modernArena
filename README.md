# ValaryRPG-Arena

Plugin Paper **separato** da ValaryRPG-Core. Arene e dungeon istanziati per **ValaryRPG**: template mondo unloaded → copie `arena_<boss>_<slot>` / `dungeon_<id>_<slot>` → party → coda → kill/milestone SQL.

Non è AdvancedMobArena e non è un modulo Core. Java orchestra, **YAML decide**.

Repo: `Nyxon-com/ValaryRPGarena-instances` (privata). Artifact: `ValaryRPG-Arena`. Package: `com.valaryrpg.arena`.

## Stack

- Paper **1.21.11**, Java **21**, Gradle Shadow, MiniMessage, HikariCP (SQLite / MySQL / MariaDB)
- Hard-depend: **MythicMobs**
- Soft: ValaryRPG-Core, Vault, PlaceholderAPI, Multiverse-Core
- Hologram: `TextDisplay` Paper (niente plugin extra)
- Kit/eco Core: `[console] …` in YAML, mai `compileOnly` sul Core

## Install

1. Metti `ValaryRPG-Arena-1.0.0.jar` in `plugins/` (MythicMobs deve essere presente).
2. Template mondo **non caricati** nella world container del server.
3. Sul Core: `worlds.ignore-prefixes: ["arena_", "dungeon_"]`. Multiverse non deve auto-importare quei mondi.
4. Produzione con tante kill: `storage.yml` → `type: mysql` (o `mariadb`). SQLite solo per test.

Build: `gradlew.bat shadowJar` → `build/libs/ValaryRPG-Arena-1.0.0.jar`.

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

Un player non sta in arena e dungeon insieme. Bypass cooldown arena: `valary.arena.cooldown.bypass`.

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

- Pack Mythic **senza spawner**: `trigger: immediate` o `radius`. `amount` + `times` (default 1).
- Clear stanza: pack esausti **e** 0 living della stanza nel mondo. Poi fill `wall`, `outro` a schermo, `commands` per i vivi (`[console] kit … {player}`).
- Hologram: `follow-door: true` → a ogni porta aperta vola al centro del wall (`fly-ticks`).
- Leash: se il mob supera `leash` blocchi dallo spawn, TP indietro. `heal` / `heal-on-leash` lo cura.

```yaml
settings:
  hologram:
    follow-door: true
    fly-ticks: 18
  leash:
    radius: 24   # 0 = off
    heal: true

# per pack:
leash: 24
heal-on-leash: true
# oppure
leash:
  radius: 32
  heal: true
```

## Scala (8 GB, ~20 player)

Tetto totale mondi: `config.yml` → `instances.max-loaded: 16`. 4 slot per boss/dungeon, poi coda. Copy mondo max 1 alla volta.

Heap `-Xmx5G`. Template piccoli. Muri dungeon = cubo stretto. Produzione kill: MySQL.

## Fuori scope

Editor in-game, shop, wave AMA, spectator, party chat, friendly fire, PAPI expansion, dipendenza Java Core, plugin hologram terzi, editor MythicDungeons.

Guida Cursor (flussi Java, hotspot, convenzioni): `AGENTS.md`.
