# Tempered (Minecraft 26.3, Forge 66.0.8)

Tools and weapons get better the more you use them. The main tools (pickaxe, axe, shovel, hoe,
sword and spear, in all 7 materials) have **20 mastery levels** (I-XX); bow, crossbow, trident, mace,
shears and fishing rod have 5. Each level is a challenge (mine 360 blocks, dig 200 blocks in the
Nether, defeat an elite foe ...) and pays out real upgrades: faster mining, more damage, more drops,
and on the way signature abilities like Vein Miner, Timber, Excavate, Magnet or Soul Harvest.
Tools also **never disappear** any more: at 0 durability they become *Broken* and can be repaired.

This is a **Forge** mod (not NeoForge like Juicer, Oreborn and Arsenal): it needs its own
CurseForge profile with Forge 66.0.8 for Minecraft 26.3.

## Install (CurseForge app)
1. *Create Custom Profile* -> Minecraft **26.3** -> Modloader **Forge** -> **66.0.8**.
2. On the profile: `⋮` -> *Open Folder* -> put `tempered-1.2.0.jar` into `mods/` (delete an older `tempered-*.jar` first).
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
milestones. Ores count extra toward a pickaxe's "Mine X blocks" by rarity: coal, copper and quartz 2,
nether gold 3, iron and redstone 4, gold and lapis 6, diamond 10, emerald 12, Ancient Debris 16
("Mine X ores" still counts each ore once). Levels still unlock in order. Blocks you placed yourself do not count (no place-and-mine
loops). Upgrading a diamond tool to netherite keeps its counters; it is then measured against the
harder netherite milestones.

Early levels come quickly (a wooden pickaxe reaches Mastery I after 15 blocks, a diamond one after
120). The late game is a real grind: the main work grows to **300x** level I by Mastery XX, and the
top levels add hard extra challenges in the Nether and the End (deep mining, obsidian, Ancient Debris,
diamond ores, Nether Wart, sculk, elite foes ...). A diamond pickaxe needs 36,000 blocks and 30 Ancient
Debris for Mastery XX, a diamond sword 9,600 monsters and 25 elite foes. The full list is in
[CHALLENGES.md](CHALLENGES.md) and in game.

## Rewards
- **Stat perks:** every main-tool level raises its core stat: mining speed (+125% at Mastery XX on
  diamond, +150% on netherite) or damage (+63% / +75%). Also attack speed, faster bow draw / crossbow
  reload / trident wind-up, extra Looting (up to +3 on top of Looting III), lifesteal (up to 12%),
  Lure and Luck of the Sea beyond vanilla.
- **Reinforced:** a chance for each point of wear to be ignored (stacks with Unbreaking).
- **Yield:** chance for double ore drops, logs, crops, wool or fish. **Treasure hunter** (shovels):
  digging sometimes turns up flint, nuggets, emeralds, even diamonds.
- **Gilded:** golden tools give +25% experience at Mastery V, up to +100% at Mastery XX.
- **Abilities** (sneak to switch the area ones off):
  - Pickaxe: *Vein Miner* at X (whole ore veins; 4 ores on wood up to 48 on netherite, doubled at
    XVI), *Excavate* 3x3 at XV on diamond and netherite (iron at XX, netherite 5x5 at XX), *Magnet* at XX.
  - Axe: *Timber* at X (fells whole trees, never leafless log buildings; doubled at XVI), *Magnet* at XX.
  - Shovel: *Excavate* 3x3 at X, 5x5 at XX.
  - Hoe: *Replant* at III, *Reaper* at VIII (3x3), XIV (5x5) and XX (7x7 diamond, 9x9 netherite),
    *Magnet* at XX; by then every crop drops double.
  - Sword: *Executioner* at XV (+50% damage to foes below 35% health), *Soul Harvest* at XX (every kill
    heals 2 hearts and gives Strength for 5 seconds).
  - Spear: *Cavalry* at X (+40% damage while riding, faster mount), *Warhorse* at XX (you and your
    mount take 40% less damage while riding).
  - *Magnet*: drops go straight into your inventory (whatever does not fit falls as usual).
  - Mastery V of the other tools: Bow: *Volley* (full-power shots fire 3 arrows). Crossbow: *Self-loading*.
  - Trident: *Stormcaller* (thrown hits call lightning in any weather).
  - Mace: *Shockwave* (smash attacks also hit every foe nearby).
  - Shears: *Shear Sweep* (shears every animal around you).

## Broken tools
At 0 durability a tool, weapon, bow, shield, fishing rod ... stays in your inventory as **Broken**
(red cracks on the icon, "BROKEN" in the tooltip). It works like a bare hand until repaired and
keeps its enchantments and mastery.
Repair it at an **anvil** with its material: each material restores a quarter, costs **1 level**, and
never becomes "Too Expensive" (the prior-work penalty is ignored). Mending works as usual.

### Repair kits (on the go)
Craft a kit from **1 iron ingot + the tool's material** (planks, cobblestone, copper, gold, diamond or
netherite ingot; any layout). The **Iron Repair Kit** is two iron ingots **on top of each other** (side by
side they make vanilla's heavy pressure plate, diagonal makes shears).
- Right-click the kit onto the damaged item in your inventory (like filling a bundle), or hold the item
  in your other hand and use the kit.
- One kit gives back **15%** of the durability (broken tools included) and costs no experience. The anvil
  stays better: **25% per material**.
- Kits fix anything the anvil would fix with that material: tools, weapons and armour (the Wooden kit
  also fixes shields).
Armour still breaks like in vanilla; add items to the tag `tempered:keep_when_broken` with a
datapack to change that.

## Seeing progress
- **K** opens the overview: all 48 tools, the selected tool's milestones (scrolled to the one you are
  working on), your progress on the best copy you carry, and every reward.
- Tooltips show the current mastery ("Mastery VII (7/20)") and the next milestone with progress (hold
  **Shift** for the perks). Every level-up message also names the next challenge.
- A thin bar along the top of the item icon shows the mastery level (pink when mastered).

## For developers
- `./gradlew build` -> `build/libs/tempered-1.2.0.jar`; `./gradlew runGameTestServer` runs the GameTests.
- The challenge catalogue is code: `src/main/java/com/afjan/tempered/mastery/Tracks.java`.
- `python3 tools/gen_tests.py` writes the GameTest instance files after adding a test.
- `python3 tools/gen_textures.py` draws the repair kit icons (preview: `build/kit_preview.png`).
