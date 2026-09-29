# Hatchery (Minecraft 26.3, Forge 66.0.8)

Spawners can be taken home, and every mob can become one.

- **Mine a spawner** with any pickaxe: it drops a **Broken Spawner** (plus the usual experience).
  Trial spawners drop one too (a pickaxe is required there). Breaking by hand gives nothing.
- **Spawn eggs drop from mobs**: a mob killed by a player has a **0.5%** chance to drop its own spawn
  egg (Looting adds 0.1% per level: 0.8% with Looting III). The egg glows through walls, never
  despawns, chimes, and the killer sees "Rare drop: ... Spawn Egg". The Wither and the Ender Dragon
  never drop theirs.
- **Bring it back to life**: place the Broken Spawner and right-click it with a spawn egg. It turns into
  a working spawner of that mob (the egg is used up). Mining it again gives the Broken Spawner back,
  but the egg is lost.

## Spawner modules
Use a module on a spawner to upgrade it. An empty hand on a spawner shows its modules; mining it gives every
module back (with the Broken Spawner). Swarm, Haste and Frailty stack: **level N uses N modules at once**, so
maxing Swarm or Haste takes 1+2+3+4+5 = 15 modules.

| Module | Recipe (3x3: corners / edges / centre) | Per level | Max |
|---|---|---|---|
| Swarm | diamond / emerald / netherite scrap | +2 mobs per wave (4 -> 14), room for +5 more nearby | V |
| Haste | redstone block / gold ingot / netherite scrap | waves 30% sooner (10-40 s -> 1.7-6.7 s) | V |
| Frailty | amethyst shard / quartz / fermented spider eye | mobs spawn at 75, 50, 25% health, IV: 1 HP | IV |
| Daylight | lapis block / diamond / daylight detector | spawns in any light and on any ground | I |
| Redstone | redstone / iron ingot / comparator | a redstone signal pauses the spawner | I |

A fully upgraded spawner costs 30 netherite scrap (30 Ancient Debris), 64 diamonds, 60 emeralds, 60 redstone
blocks and more: a late-game mining project. The recipes unlock once you hold a Broken Spawner.

A revived spawner is a normal vanilla spawner: it runs while a player is within 16 blocks, and its
mobs still need their usual spawn conditions (monsters need darkness, animals need grass and light).
Vanilla also lets you use a spawn egg on a working spawner to change its mob.

This is a **Forge** mod, like Tempered: put it into the same Forge 66.0.8 profile.

## Install (CurseForge app)
1. Use the Forge profile you made for Tempered (*Create Custom Profile* -> Minecraft **26.3** ->
   Modloader **Forge** -> **66.0.8** if you don't have one).
2. `⋮` -> *Open Folder* -> put `hatchery-1.1.0.jar` into `mods/`.

## Changing the numbers (datapack)
- Drop chance: `data/hatchery/loot_modifiers/spawn_eggs.json` (`unenchanted_chance` 0.005 = 0.5%).
- Mobs that never drop eggs: entity tag `hatchery:no_spawn_egg`.
- What spawners drop: `data/minecraft/loot_table/blocks/spawner.json` and `trial_spawner.json`.

## For developers
- `./gradlew build` -> `build/libs/hatchery-1.1.0.jar`; `./gradlew runGameTestServer` runs the 18 GameTests
  (spawner drops, reviving, the 0.5% rate over 20,000 kills, Looting, bosses, glowing drops, 3,000 real kills).
- `python3 tools/gen_textures.py` draws the Broken Spawner texture and the logo from the vanilla spawner;
  `python3 tools/gen_tests.py` writes the GameTest instance files after adding a test.
