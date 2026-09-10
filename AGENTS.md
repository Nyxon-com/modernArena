# ValaryArena — Agent brief

Plugin Paper **separato** da ValaryRPG-Core. Server **ValaryRPG**, palette **viola + nero**.
Non è AdvancedMobArena: arena istanziata + party + coda + kill/milestone.

## Stack
Paper 1.21.11, Java 21, Gradle, MiniMessage, HikariCP (SQLite / MySQL / MariaDB).
Hard-depend: **MythicMobs**. Soft-depend: ValaryRPG-Core (spawn / ignora mondi), Vault, PlaceholderAPI.
Package: `com.valaryrpg.arena`.

## Brand (YAML, mai hardcode)
- Nome: `ValaryRPG`
- Viola: `#5F22C3` → `#40099B` | Nero: `#000000`
- Prefix: `<gradient:#5F22C3:#40099B><bold>ValaryRPG</bold></gradient> <dark_gray>»</dark_gray> `
- Separatore (linea): `<bold><strikethrough><gradient:#5F22C3:#40099B>                                                          </gradient></strikethrough></bold>`
  (equivalente MiniMessage della barra `<b><gradient:#5F22C3:#40099B>&m                                                          </gradient></b>`)

## Cosa fa (v1, non uscire da qui)
1. Template mondo sul disco, **non loaded** in copy.
2. Fino a **4 istanze per boss**. Slot `FREE | COPYING | ACTIVE | UNLOADING`. Pieno → coda per quel boss.
3. `/arena start <boss>` (leader): copy async → load `arena_<boss>_<slot>` → ricrea spawner Mythic da YAML (stesse xyz, mondo nuovo, nome spawner unico) → TP party.
4. 0 player (quit/leave) → TP leave → deregistra spawner → unload → delete cartella → dequeue.
5. **Cleanup all'enable**: ogni mondo/cartella `arena_*`, spawners istanza, player residui. Coda RAM vuota.
6. Party custom nel plugin (`/party` create/invite/accept/leave/kick/disband). Un player = un party. Leader leave → passa leader; ultimo → disband. Party in coda che si scioglie → fuori coda.
7. Kill Mythic → SQL async → milestone YAML (messaggio + comandi `[console]`, es. kit/eco Core). Niente API Java sul Core.

## Cosa non fare
- Non mettere questo codice in ValaryRPG-Core.
- Non hard-depend Core. Reward = comandi console YAML.
- Non editor in-game, shop, wave AMA, spectator, party chat, FF.
- Non compilare in locale. Non nominare AI nei commit. Commit + push dopo ogni feature (`Nyxon-com`).
- Testi, permessi, suoni, delay, coord, cap, nomi spawner: **solo YAML**.

## File
- `config.yml` comandi/permessi
- `lang/messages.yml` testi MiniMessage
- `sounds/sounds.yml`
- `storage.yml` DB proprio (`valary_arena_*`)
- `modules/arenas.yml` boss, template, spawners, slot, coda, leave-location
- `modules/party.yml` invite timeout, size default
- `modules/milestones.yml` soglie per mob id

## Core
Soft: `/spawn` in uscita (o comando configurabile per tp alla fine). Una patch Core: `worlds.ignore-prefixes: ["arena_"]`.
Multiverse: non auto-importare `arena_*`.

## Crediti e perf
Segui sempre `.cursor/rules/credits.mdc` e `.cursor/rules/optimize.mdc`.