# ValaryArena — wiki Cursor

Plugin Paper **separato** da ValaryRPG-Core. Server **ValaryRPG**, viola `#5F22C3`→`#40099B` + nero.
Non è AdvancedMobArena: template mondo → istanze `arena_<boss>_<slot>` → party → coda → kill/milestone SQL → hologram → cap boss → cooldown ingresso.

Repo: `Nyxon-com/ValaryRPGarena-instances` (privata). Artifact: `ValaryRPG-Arena`. Package: `com.valaryrpg.arena`.

## Stack

Paper 1.21.11, Java 21, Gradle Shadow, MiniMessage, HikariCP (SQLite / MySQL / MariaDB).
Hard-depend: **MythicMobs**. Soft: ValaryRPG-Core, Vault, PlaceholderAPI, Multiverse-Core.
Mai `compileOnly` sul Core: kit/eco = `[console] …` in YAML.
Hologram = `TextDisplay` Paper, niente plugin extra.

## Brand (solo YAML)

In `lang/messages.yml` → `brand.*`. Mai hardcodare in Java.

- Nome: `ValaryRPG`
- Prefix: `<gradient:#5F22C3:#40099B><bold>ValaryRPG</bold></gradient> <dark_gray>»</dark_gray> `
- Separator: `<bold><strikethrough><gradient:#5F22C3:#40099B>                                                          </gradient></strikethrough></bold>`

`MessageService` sostituisce `{prefix}` `{separator}` `{brand}`.

## YAML (Java orchestra, YAML decide)

| File | Ruolo |
|------|--------|
| `config.yml` | Nomi sottocomandi, alias, permessi |
| `lang/messages.yml` | Testi MiniMessage, linee hologram |
| `sounds/sounds.yml` | Chiavi suono |
| `storage.yml` | DB, `table-prefix: valary_arena_` |
| `modules/arenas.yml` | Slot, coda, leave, boss, spawners, hologram, max-bosses, cooldown ingresso |
| `modules/party.yml` | `invite-timeout-seconds`, `max-size` |
| `modules/milestones.yml` | Soglie per Mythic internal name **o** id boss |

Testi, permessi, suoni, delay, coord, cap, nomi spawner: **solo YAML**. Se manca un testo, aggiungi la chiave, non un sistema nuovo.

### `modules/arenas.yml` — chiavi vive

- `settings.instances-directory` — copie in `plugins/ValaryRPG-Arena/instances/`
- `settings.max-instances-per-boss` (default 4), `max-instances-global` (0 = no cap)
- `settings.max-bosses` — cap kill boss per istanza (0 = illimitato). Override: `bosses.<id>.max-bosses`
- `settings.boss-cap-leave-ticks` — attesa poi TP fuori (min 1)
- `settings.entry-cooldown-seconds` — rientro **stessa** arena dopo uscita (0 = off)
- `settings.between-cooldown-seconds` — ingresso in un’**altra** arena (0 = off)
- `settings.hologram` — `enabled`, `update-ticks`, `offset-y`; per boss: `bosses.<id>.hologram` con xyz opzionali
- `bosses.<id>.spawners.*.source-spawner` — clona lo spawner Mythic
- `bosses.<id>.spawners.*.mob` — internal name Mythic per kill/milestone anche con `source-spawner`

### Comandi `/arena`

`start`, `list`, `leave`, `stats [player] [boss]`, `milestones [player]`, `reload`.
Vedere altri player: permesso `valary.arena.stats.other`.
Bypass cooldown: `valary.arena.cooldown.bypass`.

### Suoni

`party-invite`, `arena-start`, `arena-leave`, `arena-queue`, `milestone`, `command-denied`, `arena-cooldown`, `arena-boss-cap`.

## Package Java

```
com.valaryrpg.arena
  ValaryArenaPlugin          enable: config → storage → catalog → cleanup → comandi
  config/                    YamlFile, ConfigManager
  message/                   MessageService
  sound/                     SoundService
  storage/                   StorageManager, KillRepository  (query uuid, o lista kill per uuid)
  util/                      Tasks (async/sync/later/repeat), ConsoleCommands ([console])
  command/                   ArenaCommand, PartyCommand, CommandCooldowns
  party/                     Party, PartyService
  arena/                     Catalog, Service, Queue, Instance, WorldCopier,
                             MythicSpawners, Listener, Holograms, Cooldowns
  milestone/                 MilestoneCatalog, KillTracker
```

Riaggancia `Tasks`, `messages()`, `sounds()`, `console()`, `storage`. Non duplicare helper.

## Flussi (non uscire da qui)

### Start — `/arena start <boss>` (leader)

1. Solo o con party: se ha party, solo il leader avvia e parte tutto il party.
2. Cooldown ingresso: stessa arena = `entry-cooldown-seconds`; arena diversa = `between-cooldown-seconds` (se 0, usa entry). Bypass YAML.
3. Slot libero 1..`max-instances-per-boss` e cap globale se > 0. `COPYING` non è libero.
4. Pieno → coda RAM per quel boss (`ArenaQueue.enqueue`). Niente poll. Cooldown blocca anche la coda.
5. Template **unloaded**, copy **async** (`WorldCopier`, esclude `uid.dat` / `session.lock`) in `plugins/ValaryRPG-Arena/instances/`.
6. Sync: `createWorld` → clona spawner Mythic (`source-spawner`) → TP party → `ACTIVE` → hologram TextDisplay se enabled.
7. Timer durata solo se `duration-seconds` > 0 e solo su `ACTIVE`.
8. Dopo reload Mythic, spawner delle istanze `ACTIVE` ricreati (skip se `spawnersHalted`).

### Leave / morte / cap

Morte = eliminato, respawn fuori. 0 vivi → chiude dopo il respawn degli eliminati.
Quit, `/arena leave`, world-change, party leave/disband = fuori.
0 player → TP leave (`leave.command` default `spawn`) → hologram off → despawn + deregistra spawner → **unloadWorld prima di cancellare la cartella** → delete async (solo se il World non usa più quella path) → dequeue.

`max-bosses` raggiunto → ferma spawner → messaggio/suono → dopo `boss-cap-leave-ticks` TP tutti fuori e unload.

Niente `PlayerMoveEvent`. Timer hologram solo su slot `ACTIVE`.

### Mondo / cartella (Windows)

Cercare il World per **cartella** (`world.getWorldFolder()`), non solo per nome (`/` vs `\`).
Mai cancellare la directory se un World è ancora caricato. Disable: unload sync, **niente delete**; cleanup cartelle al prossimo enable.

### Enable / disable

Enable: `cleanup()` unloada mondi `arena_*` / cartelle instance, spawners Mythic prefisso, coda vuota. Finché il cleanup disco non finisce, start rifiutati (`errors.arena-cleaning`).
Disable: `Tasks.shutdown()` poi unload mondi istanza sync, niente task delete post-disable. Svuota party, code, cooldown comando, mapping, lock SQL. `ArenaCooldowns` ingresso si azzera al disable (RAM).

### Party — `/party`

create / invite / accept / leave / kick / disband.
Un player = un party. Leader leave → passa leader; ultimo → disband. Party in coda che si scioglie → fuori coda (`partyGone`).

### Kill / milestone (per player)

Morte Mythic **nel mondo istanza**. Se il mob è tracciato (id boss o `spawners.*.mob`, case-insensitive) ogni membro **vivo** prende +1 sul proprio contatore (chiave = chiave `milestones.yml` se match, senno id boss).
Altrimenti solo il killer.
SQL async `(player_uuid, mob_id)`. Milestone se `kills >= soglia` e claim atomico. Niente SQL sul main, niente API Java Core.
`/arena stats` e `/arena milestones` leggono SQL async.

### Hologram (solo ACTIVE)

`ArenaHolograms`: warmup spawner Mythic, boss uccisi in questa istanza, top danno del boss **precedente**.
Danno da `EntityDamageByEntityEvent` (melee, projectile, tameable). Linee in `lang/messages.yml` → `hologram.lines`.

## Come cambiare le cose

- **Nuovo boss**: `modules/arenas.yml` → `bosses.<id>`. Template nella world container, **non caricato**. `source-spawner` + `mob:` = internal name Mythic.
- **Milestone**: `milestones.yml` → `mobs.<id>.<soglia>`. L’id deve matchare boss id o Mythic name (`mob:`).
- **Holo / cap / cooldown**: YAML arenas + testi `hologram.*` / `errors.entry-cooldown` / `errors.between-cooldown` / `arena.boss-cap`.
- **Reload**: `/arena reload` ricarica YAML + cataloghi. Non tocca istanze vive né il DB.

## Fuori scope

Editor in-game, shop, wave AMA, spectator, party chat, friendly fire, PAPI expansion, dipendenza Java Core, plugin hologram terzi.

## Core / Multiverse (non questo JAR)

Sul Core: `worlds.ignore-prefixes: ["arena_"]`.
Multiverse: non auto-importare `arena_*`. Mondi creati con Bukkit.

## Convenzioni

1. Classi autoesplicative, javadoc di una riga.
2. Copy/delete mondo async. Load/unload, TP, Mythic **sync**.
3. YAML boss/spawner/milestone/holo in RAM al load/reload. Zero `getConfigurationSection` in timer/kill.
4. Dopo una feature: commit + push su `Nyxon-com/ValaryRPGarena-instances`. Mai AI nel messaggio.
5. Non compilare se non chiesto. Build: `gradlew.bat shadowJar` → `build/libs/ValaryRPG-Arena-1.0.0.jar`.
6. Tocca solo ciò che è stato chiesto. Niente README extra, test, refactor “mentre ci sei”.
