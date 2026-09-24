# Oreborn

A NeoForge mod for **Minecraft 26.3** (NeoForge 26.3.0.7-beta or newer): four new rare ores, and for each of
them a full set of tools and armour in which **every piece has its own ability**.

## The ores (as rare as diamonds)

| Ore | Where | Drops | Processing |
|---|---|---|---|
| **Cryolite** (ice crystal) | Overworld, y −64 … 32 (most around y −16) | 2–3 Cryolite Shards | 4 shards → 1 **Cryolite Crystal** |
| **Fulgurite** (crystallised lightning) | Overworld, y 0 … 160 (most around y 80: hills and mountains) | 2–3 Fulgurite Shards | 4 shards → 1 **Fulgurite Crystal** |
| **Emberite** (fire metal) | Nether, y 8 … 120 | 1 Raw Emberite | smelt → Emberite Scrap; 4 scrap + 4 gold ingots → **Emberite Ingot** |
| **Umbrium** (void metal) | Overworld, right above the bedrock (y −64 … −44) | 1 Raw Umbrium | smelt → Umbrium Scrap; 4 scrap + 4 ender pearls → **Umbrium Ingot** |

Fortune works on all of them, Silk Touch gives the ore block (which also smelts into scrap). Cryolite and Fulgurite
need an iron pickaxe, Emberite and Umbrium a diamond pickaxe. Each material also has a storage block (9 → 1).

**Gear:** Cryolite and Fulgurite gear is crafted like diamond gear (crystals + sticks). Emberite and Umbrium gear is
made like netherite: put **diamond gear + an ingot into a Smithing Table** (no upgrade template needed); enchantments are kept.

## The abilities

Right-click abilities have a cooldown (in brackets) and cost a little durability.

| | Sword | Pickaxe | Axe | Shovel | Hoe |
|---|---|---|---|---|---|
| **Cryolite** | *Frostbite*: hits freeze; hitting a frozen enemy shatters it (+6 dmg, freezes everything near) | *Cryo Seal*: lava next to mined blocks turns to obsidian/cobblestone, water to ice | *Frost Nova* (RC, 12 s): freezes all monsters within 6 blocks | *Frost Heave*: digs 3×3 (sneak = 1 block) | *Glacial Irrigation*: tills 3×3; sneak + RC carves a hydrated 9×9 farm plot around a water source |
| **Fulgurite** | *Chain Lightning*: charged hits arc to 3 more enemies | *Ore Radar* (RC, 10 s): every ore within 12 blocks glows through walls for 10 s | *Stormcaller* (RC, 8 s): lightning where you look (32 blocks), charges creepers | *Landslide*: the whole sand/gravel column comes down | *Charged Soil* (RC, 5 s): crops within 4 blocks grow |
| **Emberite** | *Combustion*: sets enemies ablaze; burning enemies explode when they die (chain reactions!) | *Molten Core*: mines whole ore veins (up to 48, sneak = 1) | *Meteor Slam* (RC, 5 s): leap/plunge, landing = fiery shockwave | *Kiln Touch* (RC): smelts blocks in place 3×3 (sand → glass, cobble → stone …) | *Ember Scythe*: a real weapon, charged swings reap and ignite everything around |
| **Umbrium** | *Void Strike* (RC, 5 s): teleport behind the enemy you look at, next hit ×2; no enemy = blink 8 blocks | *Void Patterns* (sneak + RC to switch): **X Cross**, **Strip-Mine Tunnel** (1×2, 8 deep), **3×3** | *Worldfeller*: fells the whole tree (sneak = 1 log) | *Rift Burrow* (RC, 3 s): phase through up to 10 blocks of wall/floor/ceiling | *Void Harvest* (RC on a ripe crop): harvests and replants 9×9 |

**Tool traits:** Fulgurite *Momentum* (mining fast stacks Haste up to III) · Emberite *Molten* (ores, raw metal, sand
and crops drop already smelted, with the XP) · Umbrium *Void Pocket* (everything you break goes straight into your inventory).

| | Helmet | Chestplate | Leggings | Boots | Full set |
|---|---|---|---|---|---|
| **Cryolite** | breathe under water | melee attackers freeze | sneaking: no knockback, −25% damage | Frost Walker, walk on powder snow | *Permafrost*: slows monsters around you; below 30% health *Ice Block* (Resistance IV + Regeneration II, freezes attackers, 90 s) |
| **Fulgurite** | monsters within 24 blocks glow | *Storm Shield*: 2 absorption hearts that recharge 5 s after a hit | +20% speed | **double jump** | *Static Charge*: running charges you up; your next hit calls down a thunderbolt (+8) |
| **Emberite** | *Searing Gaze*: monsters you look at catch fire | immune to fire and lava, attackers burn | explosions deal half damage | *Lava Walker*: lava crusts over under your feet | *Phoenix Rebirth*: once per 10 minutes you survive a fatal blow and explode in flames |
| **Umbrium** | night vision, immune to Darkness/Blindness | 20% chance to phase through attacks | step up full blocks | no fall damage | *Umbral Wings*: **creative flight** |

Hover over any item in game to see its abilities.

## Lightning Staff

**Hold right-click** to pour force lightning out of the staff, like the Emperor in Star Wars: crackling, forking bolts
stream from the staff tip into up to 4 targets in front of you (16 blocks) and jump on to monsters next to them. About
20 damage per second to the main target (hits land 5 times a second and hold it in place), 12.5 to the others. With
nothing to strike, the lightning follows your aim:
- it **bounces off glass and glass panes** (up to 4 times) and fries whatever the reflection points at;
- held on **wood, leaves or anything else flammable** for 1.5 seconds, it sets it on **fire**;
- into **water** it electrifies everything connected within 8 blocks (a web of lightning races across the surface):
  **fish die instantly and drop cooked**, other creatures in the water get shocked.

**Electrocution:** every creature hit by electricity (the staff, electrified water, Fulgurite weapons and armour, even
real lightning bolts) convulses for a moment: it shudders and twitches, lit up by the lightning crawling over its body.

It costs **half a hunger shank per second** (it stops when your hunger bar is empty; free in
creative) and 1 durability per second (900; Unbreaking and Mending work, repair with Fulgurite Crystals). You move
slowly while channelling, like when drawing a bow.

Recipe: a **Block of Fulgurite** on top of two **Lightning Rods** placed diagonally (like a stick in a tool recipe).

## Installing with the CurseForge app

1. CurseForge → Minecraft → **Create Custom Profile** → Minecraft **26.3** → modloader **NeoForge** (26.3.0.7-beta or newer).
   (Oreborn also runs on 26.3.0.7-beta, so it can go into the existing Juicer profile too.)
2. On the profile: **⋮ → Open Folder**, open the `mods` folder and copy `oreborn-1.0.0.jar` into it.
3. Play. (Without CurseForge: install NeoForge from https://projects.neoforged.net/neoforged/neoforge with the official
   installer and put the jar into `%appdata%\.minecraft\mods`.)

The mod must be installed on the server too for multiplayer.

## Development

- `py tools/gen_data.py` regenerates all JSON (models, loot, recipes, tags, worldgen, lang); `py tools/gen_textures.py`
  regenerates all textures (needs one Gradle build first; writes `build/texture_preview.png`).
- `gradlew --no-configuration-cache runGameTestServer`: 26 automated in-game tests.
- `gradlew --no-configuration-cache runShowcase`: builds a demo world and saves screenshots to `run/screenshots/`.
- `gradlew --no-configuration-cache build` → `build/libs/oreborn-1.0.0.jar`.
