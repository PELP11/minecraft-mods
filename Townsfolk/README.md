# Townsfolk (Minecraft 26.3, Forge 66.0.8)

Villagers made easy.

- **Pick up**: sneak + right-click a villager with an empty hand. It becomes an item that keeps
  everything (job, level, trades, experience). The tooltip lists its level and trades, enchanted books
  included ("Enchanted Book (Mending)"), so you can sort traders in a chest.
- **Set down**: use the item on a block.
- **Trade from your pocket**: use the item in the air. The villager steps out, the trading screen opens,
  and it goes back into your inventory when you close it.
- **Change job**: sneak + right-click a villager with a workstation (lectern, composter, blast furnace ...).
  It takes that job at once with fresh novice trades, even an experienced villager or a nitwit, and keeps
  the job without the block nearby. The workstation is not used up. Doing it twice rerolls the trades.
- **Remove job**: sneak + right-click with the villager's own workstation. It becomes unemployed and can
  take a new job from a nearby workstation as usual.
- **Restock**: opening a villager's trades restocks them if the last restock was 5 minutes ago or more,
  no workstation needed (pocket villagers too).

Forge mod: put `townsfolk-1.0.0.jar` into the same Forge 66.0.8 profile as Tempered and Hatchery.

## For developers
- `./gradlew runGameTestServer build` -> `build/libs/townsfolk-1.0.0.jar` (6 GameTests).
- `python3 tools/gen_textures.py` draws the villager icon and logo; `python3 tools/gen_tests.py` writes the
  GameTest instance files.
