# ValaryArena — wiki Cursor

Plugin Paper **separato** da ValaryRPG-Core. Server **ValaryRPG**, viola `#5F22C3`→`#40099B` + nero.
Non è AdvancedMobArena: template mondo → istanze `arena_<boss>_<slot>` o `dungeon_<id>_<slot>` → party → coda → kill/milestone SQL. Dungeon: stanze sequentiali, fill cubo, title, boss finale.

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
| `modules/dungeons.yml` | Slot, coda, leave, dungeon, stanze, spawners, wall, intro, hologram |

Testi, permessi, suoni, delay, coord, cap, nomi spawner: **solo YAML**. Se manca un testo, aggiungi la chiave, non un sistema nuovo.

### `modules/arenas.yml` — chiavi vive

- `settings.instances-directory` — copie in `plugins/ValaryRPG-Arena/instances/`
- `settings.max-instances-per-boss` (default 4), `max-instances-global` (0 = no cap; in produzione metti un numero)
- `settings.max-bosses` — cap kill boss per istanza (0 = illimitato). Override: `bosses.<id>.max-bosses`
- `settings.max-bosses` — cap kill boss per istanza (0 = illimitato). Override: `bosses.<id>.max-bosses`
- `settings.boss-cap-leave-ticks` — attesa poi TP fuori (min 1)
- `settings.entry-cooldown-seconds` — rientro **stessa** arena dopo uscita (0 = off)
- `settings.between-cooldown-seconds` — ingresso in un’**altra** arena (0 = off)
- `settings.hologram` — `enabled`, `update-ticks`, `offset-y`; per boss: `bosses.<id>.hologram` con xyz opzionali
- `bosses.<id>.spawners.*.source-spawner` — clona lo spawner Mythic
- `bosses.<id>.spawners.*.mob` — internal name Mythic per kill/milestone anche con `source-spawner`

### `modules/dungeons.yml` — chiavi vive

- `settings.world-prefix` — `dungeon_` (Core/Multiverse devono ignorarlo come `arena_`)
- `settings.max-instances-per-dungeon` (default 4), `max-instances-global`, `complete-leave-ticks`
- `dungeons.<id>.rooms` — ordine YAML. Stanza 1: `spawn` TP. `objective: clear-mobs | boss`
- `rooms.*.spawners` — come arene (`source-spawner` + `mob` + xyz + `max-mobs`)
- `rooms.*.wall.from/to` + `set-block` (default air) — fill sync al clear, no restore
- `rooms.*.intro` — title/subtitle/actionbar MiniMessage + fade ticks
- Ultima stanza `objective: boss` + `commands: ["[console] …"]`

### Comandi `/arena`

`start`, `list`, `leave`, `stats [player] [boss]`, `milestones [player]`, `reload`.
Vedere altri player: permesso `valary.arena.stats.other`.
Bypass cooldown: `valary.arena.cooldown.bypass`.

### Comandi `/dungeon`

`start <id>`, `list`, `leave`. Solo o con party (leader). Un player non sta in arena e dungeon insieme.

### Suoni

`party-invite`, `arena-start`, `arena-leave`, `arena-queue`, `milestone`, `command-denied`, `arena-cooldown`, `arena-boss-cap`, `dungeon-start`, `dungeon-leave`, `dungeon-queue`, `dungeon-room`, `dungeon-complete`.

## Package Java

```
com.valaryrpg.arena
  ValaryArenaPlugin          enable: config → storage → catalog → cleanup → comandi
  config/                    YamlFile, ConfigManager
  message/                   MessageService
  sound/                     SoundService
  storage/                   StorageManager, KillRepository  (query uuid, o lista kill per uuid)
  util/                      Tasks (async/sync/later/repeat), ConsoleCommands ([console])
  command/                   ArenaCommand, DungeonCommand, PartyCommand, CommandCooldowns
  party/                     Party, PartyService
  arena/                     Catalog, Service, Queue, Instance, WorldCopier,
                             MythicSpawners, Listener, Holograms, Cooldowns
  dungeon/                   Catalog, Service, Rooms, Instance, Command-side Listener, Holograms
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

### Start — `/dungeon start <id>` (leader)

1. Stesso flusso slot/coda/copy async delle arene. Mondi `dungeon_<id>_<slot>` in `plugins/ValaryRPG-Arena/instances/`.
2. Occupato in arena o dungeon → rifiuto. Party condiviso.
3. TP spawn stanza 1 → `ACTIVE` → title intro → **solo** spawners di quella stanza.
4. `MythicMobDeathEvent` nel mondo dungeon: se mob della stanza, ricalcola alive. `clear-mobs` e remaining 0 → halt spawner → fill wall → stanza successiva.
5. Ultima stanza `objective: boss`: alla kill tracciata, `[console]` YAML, messaggio, dopo `complete-leave-ticks` TP e unload.
6. Morte = eliminato come in arena. 0 vivi → chiude. Niente `PlayerMoveEvent`.

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

Enable: `cleanup()` unloada mondi `arena_*` e `dungeon_*` / cartelle instance del relativo prefisso, spawners Mythic prefisso, coda vuota. Finché il cleanup disco non finisce, start rifiutati. Un player non può essere in arena e dungeon insieme.
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
Dungeon: `hologram.dungeon-lines` (`{dungeon}` `{room}` `{remaining}`).

## Scala e prestazioni

Il plugin è fatto per **molte istanze insieme**, non per un player in creative. Il collo di bottiglia vero non è il Java del JAR: è **Paper + Mythic + disco** (un mondo Bukkit per slot).

### Cosa già non fa (e non deve fare)

- Niente `PlayerMoveEvent`.
- Niente poll sulla coda: dequeue sullo unload.
- YAML boss/stanza/spawner in RAM. Zero `getConfigurationSection` in timer/kill.
- Copy/delete cartella **async**. Load/unload, TP, API Mythic **sync**.
- SQL kill/milestone **async**. Niente `SELECT` sul main nel death event.
- Timer hologram / durata **solo** su slot `ACTIVE`, cancel allo unload.
- Slot `COPYING` non è libero. Cap per-tipo **4**. Tetto totale arene+dungeon: `config.yml` → `instances.max-loaded` (default **16**, pensato per 8 GB).
- Copy mondo **una alla volta** (`instances.copy-max-concurrent: 1`). Mondo istanza: spawn chunks off, view 4 / sim 2, random tick 0, niente mob vanilla.
- Unload: despawn Mythic nel **mondo** → `unloadWorld` → delete cartella solo se nessun World usa quella path.

### Costo per N istanze ACTIVE

| Pezzo | Costo | Note |
|-------|--------|------|
| Mondo Paper | alto | RAM chunk + tick world. Questo limita N, non il plugin. |
| Copy template | disco async, max 1 insieme | Evita picco RAM su 8 GB. |
| Hologram `repeat` | 1 task / istanza | Default 40 tick. Skip se il testo non è cambiato. |
| Danno (`EntityDamageByEntityEvent`) | 1 lookup | Mondo → istanza O(1); Mythic per UUID, niente scan globale. |
| Death Mythic | basso | Solo se il mondo è un’istanza. |
| Fill muro dungeon | sync, cubo YAML | Cubo piccolo. Un cubo enorme lagga un tick. |
| SQLite | 1 connessione | Va bene in test. Produzione con tante kill: MySQL/MariaDB. |

### Quante istanze regge

- **Default YAML:** tetto totale `instances.max-loaded: 16` (8 GB). 4 per boss/dungeon, poi il globale. I start extra vanno in coda.
- Paper: ogni mondo ha overhead (chunk, entities, random tick). 20+ mondi caricati insieme chiedono RAM e TPS, anche se il JAR è leggero.
- Template **unloaded**. Mondi `arena_*` / `dungeon_*` ignorati da Core e Multiverse.

Crash già coperti: cartella cancellata con World ancora loaded (fix: unload prima, delete async, disable senza delete). Disable: niente task delete post-disable.

### 8 GB RAM, ~20 player

Heap `-Xmx5G`. `instances.max-loaded: 16` (alza a 18 solo con template minuscoli). I 20esimi start vanno in **coda**, non aprono il 17° mondo. Template piccoli, pochi `max-mobs`.

### Come alzare il carico senza rompere

1. `config.yml` → `instances.max-loaded` (tetto **totale** arene+dungeon) e `copy-max-concurrent`.
2. `settings.max-instances-per-boss` / `max-instances-per-dungeon` e `max-instances-global` per-tipo.
3. `storage.yml` → `type: mysql` (o mariadb) se le kill sono tante. SQLite serializza gli INSERT.
4. Hologram: `update-ticks: 40` se non serve 1s. `enabled: false` se non lo vuoi.
5. Template mondo **piccolo** (poco terreno, pochi entity). Copy e RAM scalano con la cartella.
6. Muri dungeon: cubo stretto (porta), non un chunk intero.
7. Spawner: `max-mobs` basso. 10 mob/stanza × N istanze = N×10 living + Mythic AI.

### Hotspot da non reintrodurre

- Scan `getActiveMobs()` globale in damage/holo/death.
- Loop su tutte le istanze per ogni hit (lookup mondo deve restare mappa).
- `PlayerMoveEvent` per “sei nella stanza”.
- SQL o `getConfigurationSection` sul main in timer/kill.

## Come cambiare le cose

- **Nuovo boss**: `modules/arenas.yml` → `bosses.<id>`. Template nella world container, **non caricato**. `source-spawner` + `mob:` = internal name Mythic.
- **Milestone**: `milestones.yml` → `mobs.<id>.<soglia>`. L’id deve matchare boss id o Mythic name (`mob:`).
- **Holo / cap / cooldown**: YAML arenas + testi `hologram.*` / `errors.entry-cooldown` / `errors.between-cooldown` / `arena.boss-cap`.
- **Nuovo dungeon**: `modules/dungeons.yml` → `dungeons.<id>` + `rooms`. Template nella world container, **non caricato**.
- **Reload**: `/arena reload` ricarica YAML + cataloghi (anche dungeon). Non tocca istanze vive né il DB.

## Fuori scope

Editor in-game, shop, wave AMA, spectator, party chat, friendly fire, PAPI expansion, dipendenza Java Core, plugin hologram terzi, editor MythicDungeons.

## Core / Multiverse (non questo JAR)

Sul Core: `worlds.ignore-prefixes: ["arena_", "dungeon_"]`.
Multiverse: non auto-importare `arena_*` / `dungeon_*`. Mondi creati con Bukkit.

## Convenzioni

1. Classi autoesplicative, javadoc di una riga.
2. Copy/delete mondo async. Load/unload, TP, Mythic **sync**.
3. YAML boss/spawner/milestone/holo in RAM al load/reload. Zero `getConfigurationSection` in timer/kill.
4. Dopo una feature: commit + push su `Nyxon-com/ValaryRPGarena-instances`. Mai AI nel messaggio.
5. Non compilare se non chiesto. Build: `gradlew.bat shadowJar` → `build/libs/ValaryRPG-Arena-1.0.0.jar`.
6. Tocca solo ciò che è stato chiesto. Niente README extra, test, refactor “mentre ci sei”.
