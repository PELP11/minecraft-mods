# Tempered (Minecraft 26.3, Forge 66.0.8)

Tools and weapons get better the more you use them. Every tool of every material has five
**mastery milestones** (I-V). Each milestone is a challenge (mine 360 blocks, chop 70 logs in the
Nether, defeat 5 elite foes ...) and pays out real upgrades: faster mining, more damage, more drops,
and at Mastery V a signature ability like Vein Miner, Timber or Volley.
Tools also **never disappear** any more: at 0 durability they become *Broken* and can be repaired.

This is a **Forge** mod (not NeoForge like Juicer, Oreborn and Arsenal): it needs its own
CurseForge profile with Forge 66.0.8 for Minecraft 26.3.

## Install (CurseForge app)
1. *Create Custom Profile* -> Minecraft **26.3** -> Modloader **Forge** -> **66.0.8**.
2. On the profile: `⋮` -> *Open Folder* -> put `tempered-1.0.0.jar` into `mods/`.
3. Start the profile. Press **K** in game for the Tool Mastery overview (rebindable under
   Controls -> Inventory).

## What gets tracked
| Tools (7 materials each: wooden, stone, copper, iron, golden, diamond, netherite) | Counted when you ... |
|---|---|
| Pickaxe | mine blocks it is meant for (ores, deepslate, Nether, End, obsidian, Ancient Debris count separately) |
| Axe | chop wooden blocks and logs; kills with it count too |
| Shovel | dig dirt, sand, gravel, clay, snow, soul sand ... |
| Hoe | till soil, harvest ripe crops, Nether Wart, break leaves/moss/sculk |
| Sword, Spear | defeat mobs (monsters, in the Nether/End, while riding, elite foes) |

| Other tools | Counted when you ... |
|---|---|
| Bow, Crossbow | defeat mobs with its arrows (long shots, raiders, elite foes) |
| Trident | defeat mobs, especially with throws, and sea creatures |
| Mace | defeat mobs, especially with smash attacks |
| Shears | shear animals and blocks |
| Fishing Rod | catch fish and treasure |

Counters are lifetime totals stored on the item, so work done early also counts for later
milestones. Levels still unlock in order. Blocks you placed yourself do not count (no place-and-mine
loops). Upgrading a diamond tool to netherite keeps its counters; it is then measured against the
harder netherite milestones.

Early milestones come quickly (a wooden pickaxe reaches Mastery I after 15 blocks); diamond and
netherite ask for work in the Nether and the End (netherrack, End Stone, obsidian, Ancient Debris,
Nether Wart, sculk, elite foes ...). The full list is in [CHALLENGES.md](CHALLENGES.md) and in game.

## Rewards
- **Stat perks:** mining speed (+60% at Mastery V on diamond, +90% on netherite), damage, attack speed,
  faster bow draw / crossbow reload / trident wind-up, extra Looting (up to +2 on top of Looting III),
  lifesteal, Lure and Luck of the Sea beyond vanilla.
- **Reinforced:** a chance for each point of wear to be ignored (stacks with Unbreaking).
- **Yield:** chance for double ore drops, logs, crops, wool or fish. **Treasure hunter** (shovels):
  digging sometimes turns up flint, nuggets, emeralds, even diamonds.
- **Gilded:** golden tools give +50% / +100% experience.
- **Mastery V abilities** (sneak to switch the area ones off):
  - Pickaxe: *Vein Miner* (whole ore veins; 4 ores on wood up to 48 on netherite), diamond and
    netherite also *Excavate* 3x3.
  - Axe: *Timber* (fells whole trees, never leafless log buildings).
  - Shovel: *Excavate* 3x3 (5x5 on netherite).
  - Hoe: *Replant* (from Mastery II) and *Reaper* (harvests a 3x3 up to 9x9 field at once).
  - Sword: *Executioner* (+50% damage to foes below 35% health).
  - Spear: *Cavalry* (+40% damage while riding, faster mount).
  - Bow: *Volley* (full-power shots fire 3 arrows). Crossbow: *Self-loading*.
  - Trident: *Stormcaller* (thrown hits call lightning in any weather).
  - Mace: *Shockwave* (smash attacks also hit every foe nearby).
  - Shears: *Shear Sweep* (shears every animal around you).

## Broken tools
At 0 durability a tool, weapon, bow, shield, fishing rod ... stays in your inventory as **Broken**
(red cracks on the icon, "BROKEN" in the tooltip). It works like a bare hand until repaired and
keeps its enchantments and mastery.
Repair it at an **anvil** with its material: each material restores a quarter, costs **1 level**, and
never becomes "Too Expensive" (the prior-work penalty is ignored). Mending works as usual.
Armour still breaks like in vanilla; add items to the tag `tempered:keep_when_broken` with a
datapack to change that.

## Seeing progress
- **K** opens the overview: all 48 tools, the selected tool's five milestones, your progress on the
  best copy you carry, and every reward.
- Tooltips show the current mastery, the next milestone with progress (hold **Shift** for the perks).
- Pips on the item icon show the mastery level (pink when mastered).

## For developers
- `./gradlew build` -> `build/libs/tempered-1.0.0.jar`; `./gradlew runGameTestServer` runs the GameTests.
- The challenge catalogue is code: `src/main/java/com/afjan/tempered/mastery/Tracks.java`.
- `python3 tools/gen_tests.py` writes the GameTest instance files after adding a test.
