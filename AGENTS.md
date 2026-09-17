# ValaryArena — wiki Cursor

Plugin Paper **separato** da ValaryRPG-Core. Server **ValaryRPG**, viola `#5F22C3` → `#40099B` + nero.

Non è AdvancedMobArena e non è un modulo Core. Template mondo **unloaded** → copie `arena_<boss>_<slot>` o `dungeon_<id>_<slot>` → party → coda RAM → kill/milestone SQL.

Repo: `Nyxon-com/ValaryRPGarena-instances` (privata). Artifact: `ValaryRPG-Arena`. Package: `com.valaryrpg.arena`.

## Per l’agente (prima di toccare codice)

1. **YAML decide, Java orchestra.** Testi, permessi, suoni, delay, coord, cap, nomi mob/spawner: solo YAML. Se manca un testo, aggiungi la chiave in `lang/messages.yml`, non un sistema nuovo.
2. **Riaggancia** `Tasks`, `messages()`, `sounds()`, `console()`, `storage`. Non duplicare helper.
3. **Niente** `PlayerMoveEvent`, `compileOnly` sul Core, scan `getActiveMobs()` globale, loop su tutte le istanze per un hit, SQL o `getConfigurationSection` sul main in timer/kill.
4. Tocco minimo: solo ciò che è stato chiesto. Niente README extra, test, refactor “mentre ci sei”.
5. **Non compilare** se non chiesto. Build: `gradlew.bat shadowJar` → `build/libs/ValaryRPG-Arena-1.0.0.jar`.
6. Feature finita → **commit + push**. Messaggio senza AI. Autore **solo** `Nyxon-com` (`325790384+Nyxon-com@users.noreply.github.com`). Non usare Haze / Cursor Agent. Non modificare `git config`: `git -c user.name="Nyxon-com" -c user.email="325790384+Nyxon-com@users.noreply.github.com"`.

## Stack

Paper 1.21.11, Java 21, Gradle Shadow, MiniMessage, HikariCP (SQLite / MySQL / MariaDB).
Il JAR imbusta i driver SQL (servono alle kill). Native SQLite inutili (Android/Mac/ARM) esclusi.
Hard-depend: **MythicMobs**. Soft: ValaryRPG-Core, Vault, PlaceholderAPI, Multiverse-Core, TAB (scoreboard).
Kit/eco Core = `[console] …` in YAML. Hologram = `TextDisplay` Paper.

## Brand (solo YAML)

`lang/messages.yml` → `brand.*`. Mai hardcodare in Java. `MessageService` sostituisce `{prefix}` `{separator}` `{brand}`.

- Nome: `ValaryRPG`
- Prefix: `<gradient:#5F22C3:#40099B><bold>ValaryRPG</bold></gradient> <dark_gray>»</dark_gray> `
- Separator: `<bold><strikethrough><gradient:#5F22C3:#40099B>                                                          </gradient></strikethrough></bold>`

## File YAML

| File | Ruolo |
|------|--------|
| `config.yml` | Nomi sottocomandi, permessi, tetto istanze (`instances.max-loaded`), `blocked-commands` |
| `lang/messages.yml` | Testi MiniMessage, linee hologram, `milestone.progress` |
| `sounds/sounds.yml` | Chiavi suono |
| `storage.yml` | DB, `table-prefix: valary_arena_` |
| `modules/arenas.yml` | Slot, coda, leave, boss, spawners Mythic, hologram, cap, cooldown |
| `modules/dungeons.yml` | Slot, coda, leave, dungeon, stanze, pack, wall, intro/outro, comandi, holo `follow-door` |
| `modules/party.yml` | `invite-timeout-seconds`, `max-size` |
| `modules/milestones.yml` | Soglie per Mythic internal name **o** id boss |

### Arena — chiavi vive (`modules/arenas.yml`)

- `settings.instances-directory` — copie in `plugins/ValaryRPG-Arena/instances/`
- `settings.max-instances-per-boss` (default 4), `max-instances-global` (0 = no cap)
- `settings.max-bosses` — cap kill boss per istanza (0 = illimitato). Override: `bosses.<id>.max-bosses`
- `settings.boss-cap-leave-ticks` — attesa poi TP fuori (min 1)
- `settings.entry-cooldown-seconds` — rientro **stessa** arena (0 = off)
- `settings.between-cooldown-seconds` — ingresso in un’**altra** arena (0 = off; se 0 usa entry)
- `settings.hologram` — `enabled`, `update-ticks`, `offset-y`; per boss xyz opzionali in `bosses.<id>.hologram`
- `bosses.<id>.spawners.*.source-spawner` — **clona** lo spawner Mythic
- `bosses.<id>.spawners.*.mob` — internal name Mythic per kill/milestone anche con `source-spawner`

### Dungeon — chiavi vive (`modules/dungeons.yml`)

- `settings.world-prefix` — `dungeon_` (Core/Multiverse lo ignorano come `arena_`)
- `settings.max-instances-per-dungeon` (default 4), `max-instances-global`, `complete-leave-ticks`
- `settings.hologram` — `enabled`, `update-ticks`, `offset-y`, `follow-door`, `fly-ticks`, `spawn-forward` (blocchi avanti dallo spawn stanza 1, yaw della stanza)
- `dungeons.<id>.hologram` — override. Ingresso: **sempre** spawn stanza 1 + `spawn-forward` + `offset-y` (niente xyz porta). Poi `follow-door` vola al wall di ogni stanza al clear
- `settings.spawn-safe-radius` — intorno allo spawn stanza 1 i pack non spawnano (scatter spinto fuori). Solo allo spawn, niente push dopo
- `settings.wave-delay-seconds` / `wave-require-clear` — default onde. Pack `trigger: wave` solo `clear-mobs`: `amount` = mob per onda, `times` = numero onde. Prossima onda se delay scaduto **e/o** onda precedente morta (`wave-require-clear`)
- `settings.proximity-check-ticks` — timer su slot `ACTIVE` per pack `radius` e leash (niente `PlayerMoveEvent`)
- `settings.leash.radius` — default leash pack (0 = off). `settings.leash.heal` — cura al ritip (default true)
- `dungeons.<id>.rooms` — ordine YAML. Stanza 1: `spawn` TP. `objective: clear-mobs | boss`
- `rooms.*.mobs.<id>` — **niente spawner Mythic**. Spawn diretto:
  - `mob` internal name Mythic
  - `trigger: immediate` | `radius` | `wave` (`wave` solo clear-mobs; prima onda subito)
  - `amount` mob per fire, `times` quante volte quel punto spara (default 1)
  - `scatter`, `level`, xyz
  - `leash` (numero **o** blocco `radius`/`heal`) — se il mob supera i blocchi dallo spawn, TP indietro. `0` = off. Default: `settings.leash.radius`
  - `heal-on-leash` / `leash.heal` — cura a pieno HP al TP. Default: `settings.leash.heal`
- `rooms.*.spawners` — alias di `mobs` (`max-mobs` → `amount`). Non clona spawner Mythic.
- `rooms.*.wall.from/to` + `set-block` (default air) — fill sync al clear, no restore. Centro cubo = target holo se `follow-door`
- `rooms.*.intro` — title/subtitle/actionbar + fade ticks **all’ingresso**
- `rooms.*.outro` — stesso blocco **a fine stanza** (clear-mobs o kill boss). Assente = niente title
- `rooms.*.commands` (anche sotto `outro`) — ogni stanza, per i vivi:
  - `[console] kit starter {player}` = console
  - senza prefisso = comando del player
  - `{player}` `{name}` `{uuid}` `{dungeon}` `{room}`

Clear stanza `clear-mobs`: pack tutti sparati **e** 0 living Mythic della stanza nel mondo istanza. **Non** usare `deaths >= spawned` (Mythic può non mandare la death). Recheck: death, +2 tick, timer proximity, timer holo se `{remaining}` è 0.

## Comandi

`/arena` — `start <boss>`, `list`, `leave`, `stats [player] [boss]`, `milestones [player]`, `reload`.
Altri player: `valary.arena.stats.other`. Bypass cooldown: `valary.arena.cooldown.bypass`.
`/arena reload` ricarica YAML + cataloghi (anche dungeon). Non tocca istanze vive né il DB.

`/dungeon` — `start <id>`, `list`, `leave`. Solo o con party (leader). Un player non sta in arena e dungeon insieme.

Comandi vietati in istanza: `config.yml` `blocked-commands` (primo token). `/arena` `/dungeon` `/party` sempre ok. Bypass: `valary.arena.command.bypass`.

PAPI (softdepend): `%valary_time%` `%valary_room%` `%valary_remaining%` `%valary_remaining_<roomid>%` `%valary_mobs%` `%valary_name%` `%valary_type%`. Fuori istanza: vuoto.

TAB (softdepend): una scoreboard (`lang/messages.yml` `scoreboard.*`) in arena e dungeon, tempo incluso. Leave → scoreboard TAB di default del server. Niente bossbar.

`/party` — create / invite / accept / leave / kick / disband. Un player = un party. Leader leave → passa leader; ultimo → disband. Party in coda che si scioglie → fuori coda.

## Suoni

`party-invite`, `arena-start`, `arena-leave`, `arena-queue`, `milestone`, `command-denied`, `arena-cooldown`, `arena-boss-cap`, `dungeon-start`, `dungeon-leave`, `dungeon-queue`, `dungeon-room`, `dungeon-complete`.

## Package

```
com.valaryrpg.arena
  ValaryArenaPlugin     enable: config → storage → catalog → cleanup → comandi
  config/               YamlFile, ConfigManager
  message/              MessageService
  sound/                SoundService
  storage/              StorageManager, KillRepository
  util/                 Tasks (async/sync/later/repeat), ConsoleCommands ([console])
  command/              ArenaCommand, DungeonCommand, PartyCommand, CommandCooldowns
  party/                Party, PartyService
  arena/                Catalog, Service, Queue, Instance, WorldCopier,
                        MythicSpawners, Listener, Holograms, Cooldowns
  dungeon/              Catalog, Service, Rooms, Packs, Instance, Listener, Holograms
  milestone/            MilestoneCatalog, KillTracker
```

## Flussi (non uscire da qui)

### `/arena start <boss>` (leader)

1. Solo o con party: se ha party, solo il leader avvia e parte tutto il party.
2. Cooldown: stessa arena = `entry-cooldown-seconds`; arena diversa = `between-cooldown-seconds`.
3. Slot libero 1..`max-instances-per-boss` e tetto globale `config.yml` `instances.max-loaded`. `COPYING` non è libero.
4. Pieno → coda RAM per quel boss. Niente poll. Cooldown blocca anche la coda.
5. Template unloaded, copy **async** (`WorldCopier`, esclude `uid.dat` / `session.lock`) in `plugins/ValaryRPG-Arena/instances/`. Copy max 1 insieme.
6. Sync: `createWorld` → clona spawner Mythic → TP party → `ACTIVE` → hologram se enabled.
7. Timer durata solo se `duration-seconds` > 0 e solo su `ACTIVE`.
8. Reload Mythic: spawner delle istanze `ACTIVE` ricreati (skip se `spawnersHalted`).

### `/dungeon start <id>` (leader)

1. Stesso slot/coda/copy async. Mondi `dungeon_<id>_<slot>`.
2. Occupato in arena o dungeon → rifiuto. Party condiviso.
3. TP spawn stanza 1 → `ACTIVE` → `intro` → pack `immediate` spawnano; pack `radius` aspettano il player. Leash: se un mob esce dal raggio YAML, TP allo spawn del pack e opzionale heal.
4. Kill Mythic nel mondo dungeon: pack esausti + 0 living della stanza → fill wall → `outro` + `commands` → se `follow-door` l’holo vola al centro wall → stanza successiva. Recheck 2 tick dopo la death e sul timer holo. Niente spawner Mythic.
5. Ultima stanza `objective: boss`: alla kill, `outro` + `commands`, messaggio complete, dopo `complete-leave-ticks` TP e unload.
6. Morte = eliminato. 0 vivi → chiude. Proximity e leash = timer su slot `ACTIVE`.

### Leave / morte / cap

Morte = eliminato, respawn fuori. 0 vivi → chiude dopo il respawn degli eliminati.
Quit, `/arena leave` o `/dungeon leave`, world-change, party leave/disband = fuori.
0 player → TP leave (`leave.command` default `spawn`) → hologram off → despawn + deregistra spawner → **unloadWorld prima di cancellare la cartella** → delete async (solo se nessun World usa quella path) → dequeue.

`max-bosses` raggiunto → ferma spawner → messaggio/suono → dopo `boss-cap-leave-ticks` TP tutti fuori e unload.

### Mondo (Windows)

Lookup World per **cartella** (`world.getWorldFolder()`), non solo per nome (`/` vs `\`).
Mai cancellare la directory se un World è ancora caricato.
Disable: unload sync, **niente delete**; cleanup cartelle al prossimo enable.

### Enable / disable

Enable: `cleanup()` unloada mondi `arena_*` e `dungeon_*`, cartelle orfane, spawners Mythic prefisso, code vuote. Finché il cleanup disco non finisce, start rifiutati.
Disable: `Tasks.shutdown()` poi unload mondi istanza sync, niente task delete post-disable. Svuota party, code, cooldown comando, mapping, lock SQL. Cooldown ingresso arena = RAM, si azzera.

### Kill / milestone (per player)

Morte Mythic **nel mondo istanza**. Mob tracciato (id boss o `spawners.*.mob` / pack dungeon, case-insensitive) → ogni membro **vivo** +1. Altrimenti solo il killer.
Chiave = match `milestones.yml`, senno id boss.
SQL async `(player_uuid, mob_id)`. Milestone se `kills >= soglia` e claim atomico.
Dopo l’incremento, chat `milestone.progress` (`{kills}` `{threshold}` `{mob}`).
`/arena stats` e `/arena milestones` leggono SQL async.

### Hologram (solo `ACTIVE`)

Arena: warmup spawner Mythic, boss uccisi in questa istanza, top danno del boss **precedente**. Danno da `EntityDamageByEntityEvent` (melee, projectile, tameable). Linee: `hologram.lines`.

Dungeon: `hologram.dungeon-lines`. Ingresso: spawn stanza 1 + `spawn-forward` + `offset-y`. `follow-door: true` → dopo ogni clear vola al centro wall di quella stanza.
Update-ticks default 40. Skip se il testo non è cambiato. Cancel allo unload.

## Scala (8 GB, ~20 player)

Il collo di bottiglia è **Paper + Mythic + disco** (un mondo Bukkit per slot), non il JAR.

| Limite | Default | Nota |
|--------|---------|------|
| Tetto totale mondi | `config.yml` `instances.max-loaded: 16` | Heap `-Xmx5G`. Alza a 18 solo con template minuscoli |
| Per boss / dungeon | 4 | Poi coda |
| Copy contemporanee | 1 | `instances.copy-max-concurrent` |
| Mondo istanza | view 4 / sim 2 / random tick 0 / spawn chunks off | Niente mob vanilla |

- Template **unloaded**. Core: `worlds.ignore-prefixes: ["arena_", "dungeon_"]`. Multiverse: non auto-importare.
- SQLite = test. Tante kill → `storage.yml` `type: mysql` (o mariadb).
- Muri dungeon = cubo stretto (porta). Cubo enorme = lag di un tick.
- Unload: despawn Mythic **di quel mondo** → `unloadWorld` → delete cartella solo se nessun World usa quella path.

**Non reintrodurre:** `getActiveMobs()` globale; loop su tutte le istanze per hit; `PlayerMoveEvent`; SQL/`getConfigurationSection` sul main in timer/kill; clear dungeon con `deaths >= spawned`.

## Come cambiare le cose

- **Nuovo boss**: `modules/arenas.yml` → `bosses.<id>`. Template nella world container, **non caricato**. `source-spawner` + `mob:`.
- **Milestone**: `milestones.yml` → `mobs.<id>.<soglia>`. L’id deve matchare boss id o Mythic name. Testo progresso: `lang/messages.yml` → `milestone.progress`.
- **Holo / cap / cooldown**: YAML arenas + testi `hologram.*` / `errors.entry-cooldown` / `errors.between-cooldown` / `arena.boss-cap`. Dungeon holo porta: `follow-door` + `fly-ticks`.
- **Nuovo dungeon**: `modules/dungeons.yml` → `dungeons.<id>` + `rooms`. Template unloaded. Ingresso: `intro`. Fine stanza: `outro` + `commands`. Porta: `wall` + holo `follow-door`.
- **Alzare il carico**: `instances.max-loaded`, `copy-max-concurrent`, max per-tipo, MySQL, hologram 40 tick o off, template piccoli, pochi mob.

## Fuori scope

Editor in-game, shop, wave AMA, spectator, party chat, friendly fire, dipendenza Java Core, plugin hologram terzi, editor MythicDungeons, bossbar.

## Convenzioni Java

1. Classi autoesplicative, javadoc di una riga.
2. Copy/delete mondo **async**. Load/unload, TP, Mythic **sync**.
3. YAML boss/stanza/spawner/milestone/holo in RAM al load/reload.
4. Classi PascalCase. Niente logica copiata tra arena e dungeon: condividi `WorldCopier`, `MythicSpawners`, party, coda, cooldown, kill tracker.
