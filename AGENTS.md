# ValaryArena — guida Cursor

Plugin Paper **separato** da ValaryRPG-Core. Server **ValaryRPG**, viola `#5F22C3`→`#40099B` + nero.
Non è AdvancedMobArena: template mondo → istanze `arena_<boss>_<slot>` → party → coda → kill/milestone SQL.

Repo: `Nyxon-com/ValaryRPGarena-instances` (privata). Artifact: `ValaryRPG-Arena`. Package: `com.valaryrpg.arena`.

## Stack

Paper 1.21.11, Java 21, Gradle Shadow, MiniMessage, HikariCP (SQLite / MySQL / MariaDB).
Hard-depend: **MythicMobs**. Soft: ValaryRPG-Core, Vault, PlaceholderAPI, Multiverse-Core.
Mai `compileOnly` sul Core: kit/eco = `[console] …` in YAML.

## Brand (solo YAML)

In `lang/messages.yml` → `brand.*`. Mai hardcodare in Java.

- Nome: `ValaryRPG`
- Prefix: `<gradient:#5F22C3:#40099B><bold>ValaryRPG</bold></gradient> <dark_gray>»</dark_gray> `
- Separator: `<bold><strikethrough><gradient:#5F22C3:#40099B>                                                          </gradient></strikethrough></bold>`

`MessageService` sostituisce `{prefix}` `{separator}` `{brand}`.

## YAML (Java orchestra, YAML decide)

| File | Ruolo |
|------|--------|
| `config.yml` | Nomi sottocomandi, alias, permessi, cooldown |
| `lang/messages.yml` | Testi MiniMessage |
| `sounds/sounds.yml` | Chiavi suono (`party-invite`, `arena-start`, `arena-leave`, `arena-queue`, `milestone`, `command-denied`) |
| `storage.yml` | DB, `table-prefix: valary_arena_` |
| `modules/arenas.yml` | Cap slot, coda, leave, boss, template, spawn, spawners |
| `modules/party.yml` | `invite-timeout-seconds`, `max-size` |
| `modules/milestones.yml` | Soglie per internal name Mythic |

Testi, permessi, suoni, delay, coord, cap, nomi spawner: **solo YAML**. Se manca un testo, aggiungi la chiave, non un sistema nuovo.

## Package Java

```
com.valaryrpg.arena
  ValaryArenaPlugin          enable: config → storage → catalog → cleanup → comandi
  config/                    YamlFile, ConfigManager
  message/                   MessageService
  sound/                     SoundService
  storage/                   StorageManager, KillRepository  (query solo uuid+mob_id)
  util/                      Tasks (async/sync), ConsoleCommands ([console])
  command/                   ArenaCommand, PartyCommand
  party/                     Party, PartyService
  arena/                     Catalog, Service, Queue, Instance, WorldCopier, MythicSpawners, Listener
  milestone/                 MilestoneCatalog, KillTracker
```

Riaggancia `Tasks`, `messages()`, `sounds()`, `console()`, `storage`. Non duplicare helper.

## Flussi v1 (non uscire da qui)

### Start — `/arena start <boss>` (leader)

1. Si può entrare da soli senza creare un party. Se il player ha un party, può avviare solo il leader e parte tutto il party.
2. Slot libero 1..`max-instances-per-boss` (default 4) e cap globale se > 0. `COPYING` non è libero.
3. Pieno → coda RAM per quel boss (`ArenaQueue.enqueue`). Niente poll.
4. Template **unloaded**, copy **async** (`WorldCopier`, esclude `uid.dat` / `session.lock`).
5. Sync: `createWorld` `arena_{boss}_{slot}` → spawners Mythic (stesso xyz, nome da `spawner-name-format`) → TP party → `ACTIVE`.
6. Timer durata solo se `duration-seconds` > 0 e solo su `ACTIVE`.
7. Dopo un reload MythicMobs, gli spawner di tutte le istanze `ACTIVE` vengono ricreati per usare le configurazioni Mythic aggiornate.

### Leave / vuoto

Alla morte il membro è eliminato e respawna fuori dall’arena. Quando non restano membri vivi, l’arena chiude dopo che gli eliminati hanno respawnato.
Quit, `/arena leave`, world-change e party leave/disband rimuovono il membro.
0 player → TP leave (`leave.command` default `spawn`, else `leave.location`) → despawn + deregistra spawner → unload → delete cartella **async** → dequeue.

Niente `PlayerMoveEvent`.

### Enable

`ArenaService.cleanup()`: mondi/cartelle con `world-prefix` (`arena_`), spawners Mythic con quel prefisso, player residui fuori, coda vuota.

### Party — `/party`

create / invite / accept / leave / kick / disband.
Un player = un party. Leader leave → passa leader; ultimo → disband. Party in coda che si scioglie → fuori coda (`partyGone`).

### Kill / milestone

`MythicMobDeathEvent` solo se il killer è in un’istanza.
`KillTracker.record` → SQL **async** (`valary_arena_kills` + `valary_arena_milestones`) + cache online.
Soglia da `milestones.yml` (già in RAM). Reward: messaggio + suono + `[console] kit …` / `eco …`.
Niente `SELECT` sul main. Niente API Java Core.

## Come cambiare le cose

- **Nuovo boss**: blocco sotto `modules/arenas.yml` → `bosses.<id>`. Cartella template nella world container (stesso livello di `world/`), **non caricata**.
- **Testo/permesso/suono/delay**: YAML relativo. Java legge `configs().command` / `permission`.
- **Milestone**: `modules/milestones.yml` → `mobs.<MythicInternalName>.<soglia>`.
- **Reload**: `/arena reload` ricarica YAML + cataloghi. Non tocca istanze vive né il DB.

## Fuori scope

Editor in-game, shop, wave AMA, spectator, party chat, friendly fire, PAPI expansion, dipendenza Java Core.

## Core / Multiverse (non questo JAR)

Sul Core: `worlds.ignore-prefixes: ["arena_"]`.
Multiverse: non auto-importare `arena_*`. Questo plugin crea mondi con Bukkit, non importa in MV.

## Convenzioni

1. Classi autoesplicative, javadoc di una riga.
2. Copy/delete mondo async. Load/unload, TP, Mythic **sync**.
3. YAML boss/spawner/milestone in memoria al load/reload. Zero `getConfigurationSection` in timer/kill.
4. Dopo una feature: commit + push su `Nyxon-com/ValaryRPGarena-instances`. Mai AI nel messaggio.
5. Non compilare se non chiesto. Build: `gradlew.bat shadowJar` → `build/libs/ValaryRPG-Arena-1.0.0.jar`.
6. Tocca solo ciò che è stato chiesto. Niente README extra, test, refactor “mentre ci sei”.
