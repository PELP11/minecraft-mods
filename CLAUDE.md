# Minecraft modding guide — ONLY for creating/editing Minecraft mods & modpacks

Lessons from building the "Juicer" and "Oreborn" mods (Sept 2026). Everything below cost real time or credits the first time.
Two identical copies exist; when you learn something new, update both:
`C:\Users\afjan\.claude\minecraft-modding.md` (user-level, read on demand) and
`C:\Users\afjan\OneDrive\Desktop\Mod Minecraft\CLAUDE.md` (auto-loaded for chats inside that folder).
Your training data predates Minecraft 26.x: verify 26.x APIs in the decompiled sources, never from memory.

## 0. Cloud sessions (claude.ai/code)
This folder is the user's private GitHub repo `minecraft-mods`; cloud sessions clone it onto a Linux VM (Ubuntu 24.04,
4 CPUs, 16 GB RAM). There, sections 1-2 (Windows paths, `py`, CurseForge folders) don't apply:
- JDK 25 isn't preinstalled (only 21): `apt-get install -y openjdk-25-jdk-headless` (prefix `sudo` if not root);
  Gradle's toolchain finds it in /usr/lib/jvm, no JAVA_HOME needed; don't rely on Gradle downloading a JDK.
  Python is `python3`; `apt-get install -y ffmpeg` for gen_sounds.py. Gradle: `(cd <Mod> && ./gradlew --no-configuration-cache ...)`.
- Builds need maven.neoforged.net and the Mojang servers. If those downloads are blocked, the user must set the
  environment's network access to **Full** (claude.ai/code, environment settings): tell them, don't work around it.
- No game window in the cloud: test with `runGameTestServer` only. A new mod = a new top-level folder (section 3).
- Hand-over: the user can't reach the VM. Commit the jar as `<Mod>/<modid>-<version>.jar`, push, and give them the
  GitHub link to download it; they put it into the CurseForge "Juicer" instance's `mods/` themselves.
  Exception: **Tempered is a MinecraftForge mod** (the user asked for "Forge 66.0.8") and needs its own Forge
  profile; NeoForge jars (Juicer, Oreborn, Arsenal) and Forge jars never share an instance (section 14).
  **Hatchery** is Forge too: asked for as "another mod" right after Tempered without a loader, so it was built for
  the same Forge profile (a NeoForge port was offered, not requested). **Townsfolk** (villagers) is Forge too.
- If a user names a loader version, check which loader it is: `66.0.8` only exists in the MinecraftForge maven
  (`26.3-66.0.8`), NeoForge 26.3 versions look like `26.3.0.x-beta`.
- The Bash safety check can fail transiently ("no verdict"); after 10 in a row the turn ends. Don't retry blindly:
  do Read/Write/Edit work in between (they don't need the check) and batch commands into fewer Bash calls.
- User preferences (from local memory): never launch the game, the user tests in game and reports back; show
  item/weapon state on the model, not HUD bars; held items never bob or tremble; 3D item models and synthesised
  sounds are the approved style; big, powerful gameplay buffs are welcome.
- In the cloud update only this file; a local session copies it to `~/.claude/minecraft-modding.md` after `git pull`.

## 1. Machine facts (do not rediscover)
- `java` on PATH is **Java 8** → Gradle 9 can't run with it. Put this in *every* Bash command that runs Gradle
  (shell state doesn't persist between calls):
  `export JAVA_HOME="/c/Users/afjan/.gradle/jdks/eclipse_adoptium-25-amd64-windows.2"` (JDK 25, provisioned by foojay).
  Fallback bootstrap JRE (Java 21, from the Minecraft launcher):
  `/c/Users/afjan/AppData/Local/Packages/Microsoft.4297127D64EC6_8wekyb3d8bbwe/LocalCache/Local/runtime/java-runtime-delta/windows-x64/java-runtime-delta`
- Python 3.9 is `py` (**not** `python`, which is the Microsoft Store stub). No Pillow/numpy → write PNGs with
  zlib+struct (reusable: `/c/Users/afjan/OneDrive/Desktop/Mod Minecraft/Juicer/tools/pixels.py`).
- Git and curl work. The Bash tool is Git Bash; PowerShell is also available. Some sessions start Git Bash with a
  Windows-style PATH (no `ls`/`sed`/`grep`): prefix Bash commands with
  `export PATH="/usr/bin:/mingw64/bin:/c/WINDOWS:/c/WINDOWS/system32:$PATH";`.
- Mod projects live in `C:\Users\afjan\OneDrive\Desktop\Mod Minecraft\` (put new ones there too so the project-level
  copy of this guide auto-loads). It's **inside OneDrive**: `build/` and `run/` add 100+ MB of sync traffic; safe to delete.
- The user plays via the **CurseForge app** → default to **NeoForge** and give CurseForge install steps
  (Create custom profile → version + NeoForge → ⋮ → Open Folder → `mods`). Whether CurseForge lists a NeoForge *beta*
  can't be checked from here; don't spend searches on it, give the fallback (official installer from
  projects.neoforged.net + vanilla launcher + `%appdata%\.minecraft\mods`).

## 2. Modpacks / existing installs
- CurseForge instances: `C:\Users\afjan\curseforge\minecraft\Instances\` (e.g. "All the Mods 9 - ATM9",
  "Project Architect 2", "The Chocolate Edition - [FORGE]"). CurseForge's own Java runtimes:
  `C:\Users\afjan\curseforge\minecraft\Install\runtime\`. Vanilla launcher: `C:\Users\afjan\AppData\Roaming\.minecraft`.
- Don't modify an instance's `mods/` or `config/` without asking, and copy them to a backup first.
- The user tests the mods in the CurseForge instance **"Juicer"** (all our jars in its `mods/`). When they report a
  crash, read `Instances\Juicer\crash-reports\` (newest first) and `logs\latest.log` before guessing: older reports
  there show whether the bug predates the latest change.

## 3. Starting a new mod fast
```bash
curl -s https://piston-meta.mojang.com/mc/game/version_manifest_v2.json | head -c 200          # latest release
curl -s https://maven.neoforged.net/releases/net/neoforged/neoforge/maven-metadata.xml | grep -o '<version>26\.[^<]*' | tail -5
curl -s "https://api.github.com/orgs/NeoForgeMDKs/repos?per_page=100&sort=updated" | grep -o '"full_name": "[^"]*"' | head
git clone --depth 1 https://github.com/NeoForgeMDKs/MDK-<mc-version>-ModDevGradle.git <scratchpad>/mdk
```
- Copy the MDK in, set `mod_id`/`mod_name`/`mod_group_id` in `gradle.properties`, `org.gradle.jvmargs=-Xmx3G`,
  add a stub `@Mod` class, then start `./gradlew --no-configuration-cache build` **in the background immediately**
  (first build ≈ 7 min: downloads + decompile + recompile). Write textures/design while it runs.
- The first `run*` task downloads ~5,100 asset files (≈212 MB, ≈2 min) once.
- Then extract sources once to the scratchpad and grep there (batch several greps per call):
```bash
unzip -q -o build/moddev/artifacts/minecraft-patched-*-sources.jar -d <scratchpad>/src     # MC + vanilla data/ and assets/
unzip -q -o ~/.gradle/caches/modules-2/files-2.1/net.neoforged/neoforge/<neo-ver>/*/neoforge-<neo-ver>-sources.jar -d <scratchpad>/neo
```
- Copy JSON formats from vanilla files in `<scratchpad>/src/data/minecraft/` and `src/assets/minecraft/` instead of guessing.
- Look for a NeoForge event (`neo/net/neoforged/neoforge/event/**`) before considering a mixin.
- Compile early and often (`./gradlew --no-configuration-cache compileJava -q`); checking sources first meant only 7 errors.
- Second mod onward: copy `gradlew*`, `gradle/`, `settings.gradle`, `build.gradle` and `tools/pixels.py` from an existing
  project instead of the MDK (same MDG). `gradlew -Pneo_version=<ver> runGameTestServer` = one-off check against another
  NeoForge build (e.g. the one the user's CurseForge profile has) before lowering the dependency range.

## 4. Minecraft 26.3 / NeoForge 26.3.0.7-beta cheat sheet (all verified)
MC 26.x ships **unobfuscated** (official names everywhere). Versions are year-based: 26.1, 26.2, 26.3…
- Names: `ResourceLocation` → `Identifier` (`Identifier.fromNamespaceAndPath`). Entity type constants live in
  `EntityTypes`, block entity types in `BlockEntityTypes`. `level.isClientSide()` is a method.
- Blocks: no `codec()` needed. `updateShape(state, LevelReader, ScheduledTickAccess, pos, dir, neighborPos, neighborState, RandomSource)`.
  `useItemOn(...)` default returns `InteractionResult.TRY_WITH_EMPTY_HAND`, then `useWithoutItem(...)` runs.
  `Blocks.ocelotOrParrot` is public; `Blocks.never/always` are private (use lambdas).
- Leaves: `LeavesBlock(AmbientLeavesBlockSoundPlayer, Properties)`; for falling-leaf particles extend
  `FallingParticlesLeavesBlock(chance, AmbientLeavesBlockSoundPlayer.noAmbientSound(), props)` and implement
  `spawnFallingLeavesParticle` (`ColorParticleOption.create(ParticleTypes.TINTED_LEAVES, argb)`). Vanilla leaves use `PushReaction.POPPED`.
- Bone meal: `BonemealableBlock` methods take `net.minecraft.world.level.block.BonemealSource`.
- Trees: configured features are now registry **`Registries.FEATURE`** (`ResourceKey<net.minecraft.world.level.levelgen.feature.Feature>`),
  data folder `data/<ns>/worldgen/feature/` (+ `worldgen/placed_feature/`). `new TreeGrower(String name,
  WeightedList.of(key), WeightedList.of(), WeightedList.of(), key)`. TreeFeature doesn't require dirt below.
- Block states in data JSON: `{"id": "ns:block", "properties": {...}}` or just `"ns:block"` (not Name/Properties).
  Weighted provider: `{"type":"minecraft:weighted","entries":[{"data":<state>,"weight":n}]}`.
- Loot tables: conditions use `"type"`; block-state check is `{"type":"minecraft:match_block","blocks":"ns:x","state":{"age":"3"}}`;
  functions go under `"modifier"` (object or list); predicates can be references like `"minecraft:tool/can_shear"`.
- Recipes: `"key": {"C": "minecraft:copper_ingot"}` (plain strings, `#tag` for tags), `"result": {"id": ..., "count": n}`.
- Items: `DeferredRegister.Items.registerSimpleItem(name, props -> ...)` / `registerSimpleBlockItem(name, block, props -> ...)`.
  Compostability is an item property: `.compostable(ContextIntProviders.COMPOSTABLE_LOW|MEDIUM)`.
  Client item definitions are required: `assets/<ns>/items/<name>.json` → `{"model":{"type":"minecraft:model","model":"ns:item/x"}}`;
  `minecraft:composite` combines models.
- Drinkable effect items without custom code: components `POTION_CONTENTS` = `new PotionContents(Optional.empty(),
  Optional.of(0xFF000000|rgb), List<MobEffectInstance>, Optional.empty())` + `CONSUMABLE` = `Consumables.DEFAULT_DRINK`
  + `.usingConvertsTo(Items.GLASS_BOTTLE)` → effects, tooltip and bottle return all work. Extra consume effects:
  `Consumables.defaultDrink().onConsume(new RemoveStatusEffectsConsumeEffect(HolderSet.direct(...))).build()`.
- Lang: vanilla only has `potion.potency.0`–`5`; add `potion.potency.6`–`9` (VII–X) for amplifiers above 5.
- Effects: `MobEffect` ctor is protected (subclass it). `addAttributeModifier(Holder<Attribute>, Identifier, amount, Operation)`
  (amount × (amplifier+1)). `applyEffectTick(ServerLevel, LivingEntity, int)` returns boolean.
  For long buffs use `new MobEffectInstance(effect, dur, amp, false, false, true)` (no particle swirl, icon kept).
- NeoForge hooks: flight = attribute `NeoForgeMod.CREATIVE_FLIGHT` (ADD_VALUE 1.0; flying stops automatically when removed);
  looting level = `EnchantedEntityLootEvent` (no mixin!); fortune level = `EnchantedBlockLootEvent` (it has no player →
  remember the player from `BreakBlockEvent` [`net.neoforged.neoforge.event.level.block`, was BlockEvent.BreakEvent]
  keyed by pos, clear on `ServerTickEvent.Post`); block an effect = `MobEffectEvent.Applicable` →
  `setResult(Result.DO_NOT_APPLY)`; `LivingFallEvent`; `MobEffectEvent.Expired/Remove`.
  `DeferredHolder` is equal to the registry holder (fine for `hasEffect`, map keys).
- Block entities: `loadAdditional(ValueInput)` / `saveAdditional(ValueOutput)` (`getIntOr`, `getStringOr`, `putInt`…),
  `ContainerHelper.loadAllItems(input, items)`. Extend `BaseContainerBlockEntity` (Container + MenuProvider);
  container contents drop automatically on break (`preRemoveSideEffects`). `new BlockEntityType<>(Factory::new, block)`.
  Ticker: `createTickerHelper(type, MY_TYPE.get(), (l, p, s, be) -> ...)`. ContainerData values sync as shorts (<32767).
- Menus/GUI: `new MenuType<>(Menu::new, FeatureFlags.VANILLA_SET)`, `addStandardInventorySlots(inv, 8, 84)`.
  GUI buttons without packets: `clickMenuButton(player, id)` + `minecraft.gameMode.handleInventoryButtonClick(containerId, id)`.
  Screen: `extractBackground(GuiGraphicsExtractor g, mx, my, partial)` (was renderBg), `extractTooltip(...)`,
  `g.blit(RenderPipelines.GUI_TEXTURED, tex, x, y, u, v, w, h, 256, 256)`, `g.fillGradient`, `g.setComponentTooltipForNextFrame`.
  `Button.builder(text, b -> ...).bounds(...).tooltip(Tooltip.create(...)).build()`. Register via `RegisterMenuScreensEvent`.
- Client: `mc.gui.screen()/setScreen()/overlay()`, `mc.gui.hud.toggle()/isHidden()`, `mc.gameRenderer.mainRenderTarget()`.
  `ServerLevel.setDayTime` is gone (world clocks) → run the command `time set noon`.
- Rendering: solid/cutout/translucent is picked **automatically per quad from texture alpha** (0 → cutout,
  partial → translucent). No `render_type` in model JSON. Translucent glass/liquid "just works".
- Worldgen: NeoForge biome modifier `data/<ns>/neoforge/biome_modifier/x.json` =
  `{"type":"neoforge:add_features","biomes":[...] or "#tag","features":"ns:placed","step":"vegetal_decoration"}`.
- `neoforge.mods.toml`: `logoFile` is deprecated → `bannerFile` (wide) + `iconFile` (square).
- Common tags that exist: `c:foods/fruit`, `c:drinks/juice`; `#minecraft:leaves` is already in `replaceable_by_trees`.
- Tools (26.3): `props.sword|pickaxe|axe|shovel|hoe(ToolMaterial, dmgBaseline, speedBaseline)` (diamond: 3/-2.4, 1/-2.8,
  5/-3, 1.5/-3, -3/0); `new ToolMaterial(incorrectBlocksTag, durability, speed, dmgBonus, enchantability, repairItemTag)`.
  Axes/shovels/hoes are plain `Item`s with a data-driven `BLOCK_TRANSFORMER` (`data/minecraft/block_transformer/*.json`):
  subclass `Item`, override `useOn`, fall back to `super.useOn`. Put gear in `#minecraft:swords|pickaxes|axes|shovels|hoes|
  head_armor|chest_armor|leg_armor|foot_armor` (enchanting uses these).
- Armour: `new ArmorMaterial(durability, Map<ArmorType,Integer>, enchant, equipSound, toughness, kbRes, repairTag,
  ResourceKey.create(EquipmentAssets.ROOT_ID, id))` + `props.humanoidArmor(material, ArmorType)`; worn look =
  `assets/<ns>/equipment/<name>.json` `{"layers":{"humanoid":[{"texture":"ns:name"}],"humanoid_leggings":[...]}}` ->
  `textures/entity/equipment/humanoid[_leggings]/<name>.png` (64x32). Extra stats: `material.createAttributes(type)
  .withModifierAdded(attr, modifier, EquipmentSlotGroup.bySlot(slot))`, then `.attributes(...)` after `humanoidArmor`.
- Melee hooks: `Item.getAttackDamageBonus(victim, dmg, source)` runs before the hit (read `getAttackStrengthScale(0.5F)`
  here; it also runs client-side -> return 0 there) -> `hurtEnemy(stack, target, attacker)` (void, server) -> `postHurtEnemy`.
- Renamed/moved: `Entity.hurtMarked` -> `syncVelocity` (send server-set velocity to the player); `PushReaction.BLOCK` ->
  `IMMOVEABLE`; action bar = `player.sendOverlayMessage(c)`; `MobEffects.HASTE/SLOWNESS/RESISTANCE`; `entity.entityTags()`;
  `Level.getEntity(UUID)`; `Direction.getApproximateNearest(Vec3)`; `ParticleTypes` is an interface of constants.
- Display entities have private setters -> build them from NBT: `EntityType.loadEntityRecursive(EntityTypes.BLOCK_DISPLAY,
  tag, level, EntitySpawnReason.TRIGGERED, e -> { e.snapTo(x, y, z); return e; })`, keys `block_state`
  (`BlockState.CODEC.encodeStart(NbtOps.INSTANCE, s).getOrThrow()`), `Glowing`, `glow_color_override`, `brightness`,
  `transformation` (`Transformation.EXTENDED_CODEC`). A glowing block display = X-ray outline through walls (Oreborn's Ore
  Radar). Tag them and cancel stale ones in `EntityJoinLevelEvent` so they never outlive a restart.
- Lightning without griefing: `EntityTypes.LIGHTNING_BOLT.create(level, reason)`, `snapTo`, `setVisualOnly(true)` (still
  thunders), apply your own damage; `creeper.thunderHit(level, bolt)` charges creepers.
- Custom damage type: `data/<ns>/damage_type/x.json` `{"message_id":"ns.x","exhaustion":0.1,"scaling":
  "when_caused_by_living_non_player","effects":"freezing|burning"}` + lang `death.attack.ns.x[.player|.item]`;
  `new DamageSource(level.registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE).getOrThrow(key), attacker)`.
- NeoForge events: `BlockDropsEvent` (`event.level`: breaker, tool, mutable `getDrops()`, `setDroppedExperience`) = auto-smelt
  / drops-to-inventory without loot modifiers; area mining = `serverPlayer.gameMode.destroyBlock(pos)` from `BreakBlockEvent`
  behind a static recursion guard (drops, events, durability all apply); `LivingIncomingDamageEvent` (setAmount/cancel),
  `LivingDamageEvent.Post.getInflictedDamage()`, cancelable `LivingDeathEvent` (setHealth first = totem), `LivingKnockBackEvent`,
  `LivingBreatheEvent.setCanBreathe`, `LivingFallEvent.setDamageMultiplier(0)`, `PlayerTickEvent.Post`.
- Networking: `RegisterPayloadHandlersEvent` (mod bus) -> `event.registrar("1").playToServer(TYPE, StreamCodec.unit(INSTANCE),
  (p, ctx) -> ...)` (main thread by default); payload = record implementing `CustomPacketPayload`; client sends with
  `ClientPacketDistributor.sendToServer(p)`. Client input: `ClientTickEvent.Post` + `mc.options.keyJump.isDown()`.
- Attachments: `DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, id)`, `AttachmentType.builder(() -> 0L)
  .serialize(Codec.LONG.fieldOf("x")).copyOnDeath().build()`, `player.getData/setData`. Data components:
  `DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, id).registerComponentType(n, b -> b.persistent(Codec.INT)
  .networkSynchronized(ByteBufCodecs.VAR_INT))`.
  Client-visible entity state without own packets: `.sync(ByteBufCodecs.VAR_LONG)` on an attachment (no serializer
  needed) sends it to every client tracking the holder on `setData`; for short effects store an expiry game time.
  Check `hasData` before `getData` (getData attaches the default).
- Recipes: `smithing_transform` `template` is optional (netherite-style upgrade without a template). The recipe book only
  shows recipes unlocked by an advancement: `data/<ns>/advancement/recipes/x.json`, parent `minecraft:recipes/root`,
  criterion `minecraft:inventory_changed` `{"items":[{"items":[ids]}]}`, `rewards.recipes`. Lookups:
  `level.recipeAccess().getRecipeFor(RecipeType.SMELTING, new SingleRecipeInput(s), level)` -> `.value().assemble(input)`,
  `.experience()`; `CraftingInput.of(w, h, list)`; `new SmithingRecipeInput(template, base, addition)`.
- Loot: pool-level `"condition": {"type": "minecraft:survives_explosion"}`; ore drops = `set_count` + `apply_bonus`
  (fortune, `ore_drops`) + `explosion_decay` under `"modifier"`. Ores: copy `worldgen/feature/ore_diamond_*.json` (targets
  use `height_specific_ore_replaceables` rules), placement `trapezoid`/`uniform` with `absolute`/`above_bottom`; the Nether is
  256 tall with its roof at y 127 -> absolute heights. Biome modifier `step: underground_ores`.
- Effects granted by armour: `new MobEffectInstance(e, MobEffectInstance.INFINITE_DURATION, 0, true, false, false)` (shown
  as infinite, no flicker); remove it when the piece comes off (`isInfiniteDuration() && isAmbient() && !isVisible()`).
- Optional tooltip lines: `Language.getInstance().has(key)`. Item lore: `DataComponents.LORE` + `new ItemLore(List.of(c))`.
- Glowing blocks without light: a model element with `"light_emission": 15` renders fullbright (vanilla `cross_emissive`
  / firefly bush do this). Oreborn ores = parent `oreborn:block/glowing_ore`: the normal cube + a coplanar overlay cube
  (like grass's side overlay) with `light_emission` 15, `"shade_direction_override": "up"` and NeoForge's
  `"neoforge_data": {"ambient_occlusion": false}`, textured with a transparent glow layer -> veins glow in dark caves.
- Animated textures: `<tex>.png` = vertical strip of 16x16 frames + `<tex>.png.mcmeta` `{"animation": {"frametime": n,
  "interpolate": true}}` or `"frames": [{"index": i, "time": t}, ...]` (irregular timing: flicker, glints). With
  `interpolate` keep the same pixels opaque in every frame and animate only colour (alpha must stay 0/255 for cutout).
- Custom world rendering (beams, bolts): build the geometry in `ExtractLevelRenderStateEvent` (game bus) and store it with
  `event.getRenderState().setRenderData(new ContextKey<>(id), data)`; draw it in `SubmitCustomGeometryEvent` via
  `event.getSubmitNodeCollector().submitCustomGeometry(event.getPoseStack(), RenderTypes.lightning(), (pose, buffer) ->
  buffer.addVertex(pose.pose(), x, y, z).setColor(r, g, b, a))`. The pose stack is identity: use camera-relative coordinates
  (`levelRenderState.cameraRenderState.pos`). `RenderTypes.lightning()` = POSITION_COLOR quads, additive (SRC_ALPHA, ONE),
  default culling -> emit both windings. Oreborn `client/ForceLightningRenderer` = working example (jagged forking bolts).
  Effects over entities: loop `ClientLevel.entitiesForRendering()` in the extract event (skip the camera entity in first person).
- Per-entity look tweaks without mixins: `RegisterRenderStateModifiersEvent.registerEntityModifier(LivingEntityRenderer.class`
  (cast to the generic class type)`, (entity, state) -> ...)` runs after `extractRenderState` for every subclass renderer,
  players included. `state.x/y/z` moves the model (shake), `bodyRot`, `yRot/xRot` (head), `walkAnimationPos/Speed`
  (limbs), `ageInTicks` (idle animations: fish tails, wings), `lightCoords = LightCoordsUtil.FULL_BRIGHT`. `isFullyFrozen`
  = vanilla freeze shake, but `SquidRenderer` (and fish) ignore it. Oreborn `OrebornClient.convulse` = electrocution spasm.
- Held-item tip in first person = fishing-rod maths: eye + `camera.getNearPlane(fov).getPointOnPlane(side * 0.525F, -0.1F)
  .scale(960 / fov)` with the item model parent `minecraft:item/handheld_rod`. Arm pose while using an item:
  `RegisterClientExtensionsEvent` -> `IClientItemExtensions.getArmPose` (`HumanoidModel.ArmPose.BOW_AND_ARROW` = both arms
  forward; custom poses need NeoForge enum extensions).
  That tip is only ~0.75 blocks from the eye: glows/flares/ribbons there fill the screen (user: "way too bright") ->
  tiny first-person flare (~0.035), thin ribbons near the camera (width x min(1, 0.3 + 0.7 * dist / 3)).
- Channelled items: `use` -> `player.startUsingItem(hand)` + `InteractionResult.CONSUME`, `getUseDuration` 72000,
  `onUseTick(level, entity, stack, remainingTicks)` runs every tick on both sides, `getUseAnimation` -> `ItemUseAnimation.NONE`
  (players move at 20% speed while using an item). Rapid hits: damage type in `#minecraft:bypasses_cooldown` (+ `no_knockback`).
- 26.x oddities: `Items.LIGHTNING_ROD` is a `WeatheringCopperCollection` (rods oxidise) -> recipes use `#minecraft:lightning_rods`,
  code looks the item up by id. Hunger: `player.getFoodData().setFoodLevel/setSaturation`.
- Beams that interact with the world: trace with `level.clip` twice (`ClipContext.Fluid.NONE` and `ClipContext.Fluid.WATER`,
  the nearer hit wins) to tell water from blocks; reflect with `d - 2(d.n)n` (n = hit face normal) off blocks in a tag
  (`#c:glass_blocks`, `#c:glass_panes`). Put the trace in one `resolve()` used by server and client so effects and visuals
  agree. Set fire like flint and steel: `BaseFireBlock.canBePlacedAt(level, pos, dir)` + `BaseFireBlock.getState(level, pos)`
  with `Block.UPDATE_ALL_IMMEDIATE`; flammability = NeoForge `state.isFlammable(level, pos, face)`. Change what a mob drops
  (cooked fish): `LivingDropsEvent` (`getSource().is(damageTypeKey)`, mutable `getDrops()`); fish = `animal.fish.AbstractFish`.

## 5. Testing that actually works
- **GameTests** (server, headless, no EULA): `./gradlew --no-configuration-cache runGameTestServer > <log> 2>&1; echo EXIT=$?`
  then `grep -v DEBUG <log> | grep -E "GAME TESTS|required tests|failed at"`. Exit value 2 = a test failed.
  Ticks run unthrottled (a 1400-tick test takes ~2 s); whole run ≈ 1 min when warm.
  - Register functions: `DeferredRegister.create(Registries.TEST_FUNCTION, MODID)`; tests in `RegisterGameTestsEvent`
    (fires only when gametests are enabled) as `new FunctionGameTestInstance(fn.getKey(), new TestData<>(env, structureId, maxTicks, 1, true))`,
    env = `event.registerEnvironment(id)`.
  - A structure `.nbt` is required (vanilla `minecraft:empty` is only 1×1×1). Gzipped NBT, palette entries use `"id"`,
    `DataVersion` 5023 for 26.3 (read `version.json` → `world_version` for other versions). Generator: `Juicer/tools/gen_data.py`.
  - Use `helper.makeMockPlayer(...)`, **not** `makeMockServerPlayer` (no connection → NPE when effects sync).
  - `helper.setBlock` skips `getStateForPlacement` → compute connection-type states yourself.
  - Test drop logic without a real player: post `new BreakBlockEvent(level, pos, state, player)` on `NeoForge.EVENT_BUS`,
    then sum `Block.getDrops(state, level, pos, null, player, tool)`.
- **Visual check** (client) - ONLY if the user explicitly asks; they prefer to test in game themselves and
  report fixes (don't pop a game window on their screen): a dev-only tour (`Juicer/src/main/java/com/afjan/juicer/client/DevShowcase.java`, task
  `runShowcase`) creates a world, builds a scene, saves screenshots to `run/screenshots/`, quits. ≈2.5 min, and it
  **opens a game window on the user's screen**, so tell them first. Get it right in one run:
  - pass `--width 1600 --height 900` (the default is 854×480); `WorldPresets::createTestWorldDimensions` = flat **sand** world;
  - `teleportTo` sets the **feet**: eye = y + 1.62; use spectator mode for close-ups; re-apply yaw/pitch every tick
    (user mouse moves the camera); wait ≥20 ticks between closing one menu and opening another (race);
  - the user may click in the window (split stacks etc.), so that's not a mod bug.
- MDG run dir is `run/` for every run config.
- Log noise to ignore: Perflib/oshi errors, `CrashReport.preload` stack traces (not a crash), command "Ambiguity"
  warnings, "Missing translation key for rename", "Ignoring chat session … Services public key".
- Need a real `ServerPlayer` in a GameTest (inventory, `gameMode.destroyBlock`, `BlockDropsEvent` breaker)? Use
  `FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "name"))` + `snapTo(abs x, y, z, yaw, pitch)`. It does
  not tick, its teleports are no-ops and `level.getEntity(uuid)` can't find it -> split "find destination" from "teleport",
  call per-tick logic directly, keep entity references instead of UUID lookups. `setShiftKeyDown(true)` = sneaking.
  Its attack strength is ~0 -> test weapon abilities by calling `item.hurtEnemy(stack, target, player)` directly.
- GameTests run side by side in one world: never detonate a big explosive in one (a power-14 blast reaches the
  neighbouring structures) and aim piercing hitscan shots down into the floor right behind the target.
  `LivingEntity.setHealth` clamps to max health (a zombie `setHealth(200)` is still 20): raise
  `getAttribute(Attributes.MAX_HEALTH).setBaseValue(...)` first when measuring damage.

## 6. Tool habits that save credits
- **Never `sleep`** (blocked). Background long jobs (`run_in_background`) and use **one** Monitor per job with an
  until-loop ending on a marker (`echo EXIT=$?`). Narrow filters only; multiple or broad monitors ("Exception|Crash|missing")
  flood the chat with duplicate events. Monitors expire after 5 min.
- Logs are 20k+ DEBUG lines: always `grep -v DEBUG` + specific patterns + `head`; never cat a log.
- Use the **Edit tool** on files you've already read. Editing them with `sed`/Python triggers "file changed on disk"
  notices that re-inject the whole file into context (expensive), and Write then refuses until you Read again.
- Don't `cd` into subfolders (the cwd persists); use absolute paths.
- Each screenshot you Read costs tokens: plan camera shots once, view only the ones you need.
- Draw textures with a script and review them as an upscaled **preview sheet** (1 image) before going in-game.
  Decide GUI widgets before drawing GUI textures (a vanilla `Button` needs no texture; I drew unused icons).
- Textures from vanilla: `tools/pixels.py` has `decode_png/load_png`; read vanilla PNGs straight from
  `build/moddev/artifacts/minecraft-patched-*-sources.jar` with `zipfile`. Recolouring vanilla silhouettes by lightness rank
  (hue splits wooden handles off) gives vanilla-quality gear icons and armour layers in minutes (Oreborn `gen_textures.py`).
  Python's `hash()` of strings changes every run -> seed with `zlib.crc32`.
- Never `grep -rn` the whole extracted source tree (times out after 2 min); grep the specific files.
- Multi-line Python/JSON-ish text in a Bash heredoc can break the shell parser: write the script with the Write tool, run it.
- The scratchpad gets cleaned between sessions (extracted sources vanish). For single lookups skip extraction:
  `unzip -p "<jar>" path/To/File.java | grep -n ...`; `unzip -l "<jar>" | grep` finds paths (packages move between versions,
  e.g. 26.3 block model elements are `net/minecraft/client/resources/model/cuboid/CuboidModelElement.java`).

## 7. Design lessons from Juicer
- Fruit must contrast with its leaves (limes on bright-green leaves were invisible → darker leaves, brighter fruit).
- Avoid black parts that dominate the item icon (the blender's black lid → orange lid).
- Long buffs: hide effect particles (they blob across the camera). Add `potion.potency.6-9` when using level X.
- Recoloured gear is easily mistaken for vanilla: yellow reads as gold and orange as copper -> off-palette outlines (slate),
  glowing veins, sparkles. Big surfaces (armour) need a monotonic palette; a bright mid-tone floods the whole piece.
- "Special" ores = art in the stone (frost / scorch / char / void halo around the veins, not just stamped gems) + a glow
  layer + a little animation (Oreborn: twinkling ice, flickering lightning scar, breathing embers, void rift with stars).
  Review them in a preview that shows base, daylight and several dark-cave frames side by side (`build/ore_preview.png`).

## 8. Juicer project map (`C:\Users\afjan\OneDrive\Desktop\Mod Minecraft\Juicer`)
- Mod id `juicer`, package `com.afjan.juicer`, MC `[26.3]`, NeoForge `[26.3.0.7-beta,)`, MDG 2.0.147, Gradle 9.2.1.
- `registry/` (ModBlocks, ModItems, ModEffects, ModBlockEntities, ModMenus, ModCreativeTabs) · `block/` (MixerBlock,
  InfuserBlock, TubingBlock, FruitLeavesBlock, FruitSaplingBlock, Contents, TubeConnection) · `block/entity/`
  (Mixer/InfuserBlockEntity, ConcentrateTank, TubeNetwork = BFS from mixer through tubes) · `menu/` · `client/`
  (screens, TankRenderer, DevShowcase) · `effect/` · `event/JuicerEvents` (Looting, Fortune, Vitality immunity, flight grace) ·
  `fruit/` (Fruit enum, JuiceRecipes = juice buffs) · `gametest/JuicerGameTests` (9 tests). Starfruit Juice = Looting X + Fortune X (Luck was replaced).
- **All textures and JSON are generated**: edit `tools/gen_textures.py` / `tools/gen_data.py`, then run them with `py`
  (gen_data deletes and rewrites its output folders; hand edits to generated JSON get lost).
- Balance: 1 fruit = 250 mB (60 ticks); juice = 1000 mB + sugar + bottle (100 ticks); mixer tank 4000, infuser 8000;
  tubes push 250 mB / 5 ticks, max 512 tubes per network.
- Release: `./gradlew --no-configuration-cache build` → `build/libs/juicer-1.0.0.jar` (a copy sits in the project root).
  Before shipping: `runGameTestServer` must print "All … required tests passed".

## 9. Oreborn project map (`C:\Users\afjan\OneDrive\Desktop\Mod Minecraft\Oreborn`)
- Mod id `oreborn`, package `com.afjan.oreborn`, MC `[26.3]`, built with NeoForge 26.3.0.8-beta. 4 ores (`material/OreMaterial`:
  Cryolite + Fulgurite crystals, Emberite + Umbrium ingots via scrap), 9 gear pieces each (`material/GearType`).
- `item/OrebornToolItem|OrebornArmorItem` delegate to `ability/`: `Combat` (weapon hits), `ToolActions` (right-click),
  `AreaMining` (patterns, vein, tree, landslide, Cryo Seal, Momentum), `Traits` (auto-smelt, void pocket), `OreRadar`,
  `ArmorSets` (passives + set bonuses); `event/OrebornEvents` wires the NeoForge events; `network/DoubleJumpPayload` +
  `client/OrebornClient` (double jump, staff arm pose, electrocution spasm); `client/DevShowcase` (`runShowcase`);
  `gametest/OrebornGameTests` (33 tests). Lightning Staff = `item/LightningStaffItem` + `ability/ForceLightning` (server)
  + `client/ForceLightningRenderer`. `ability/Shock` = electrocution state (synced attachment, set by any electric damage).
- Generated: `tools/gen_data.py` (all JSON + lang + test structure) and `tools/gen_textures.py` (needs build/ once).
  Ore art: `CRYOLITE_ART`, `FULGURITE_BOLT`, `EMBER_*`, `UMBRIUM_ART` in gen_textures.py -> base + `<mat>_ore_glow` strip.
- Release: `./gradlew --no-configuration-cache build` -> `build/libs/oreborn-1.0.0.jar` (a copy sits in the project root).

## 10. More 26.3 API facts (learned building Arsenal — read together with section 4)
- Damage: `Entity.hurt` is **gone**. Use `entity.hurtServer(ServerLevel, DamageSource, float)` (or `hurtOrSimulate`
  for either side). `Entity.setInvulnerable` is gone too. `Entity.syncVelocity` is a public **field** (`e.syncVelocity = true`).
- `Player.drop(stack, thrownFromHand, Prediction)` — the third arg is new (`Prediction.SERVER_ONLY`).
- Client renames: `mc.screen` → `mc.gui.screen()`; `mc.options.hideGui` → `mc.gui.hud.isHidden()`.
- **Keybinds: lwjgl/GLFW is NOT on the compile classpath.** 26.3 has its own key numbering in `InputConstants`
  (`InputConstants.KEY_R` == 21, not GLFW's 82) and `InputConstants.Type.KEYSYM` no longer exists. Use
  `new KeyMapping("key.x", InputConstants.KEY_R, KeyMapping.Category.GAMEPLAY)`; `KeyMapping.Category` is a record
  (MOVEMENT/MISC/GAMEPLAY/INVENTORY/…), custom ones via `RegisterKeyMappingsEvent#registerCategory`.
- Screens: `imageWidth`/`imageHeight` are **final** → `super(menu, inventory, title, 256, 250)`.
  `Screen.hasShiftDown()` is gone; `mouseClicked(MouseButtonEvent event, boolean doubleClick)` (`event.x()/y()/button()`,
  `event.hasShiftDown()`), while `mouseScrolled(double,double,double,double)` is unchanged.
- `GuiGraphicsExtractor` drawing: `item(stack,x,y)`, `itemDecorations(font,stack,x,y[,countText])`,
  `text(font, Component|String, x, y, argb[, shadow])`, `centeredText`, `fill(x0,y0,x1,y1,argb)`, `fillGradient`,
  `outline(x,y,w,h,argb)`, `setTooltipForNextFrame(font, stack, x, y)`. A GUI drawn from `fill`/`outline` rectangles
  needs **no GUI texture at all** — vanilla `Button`s already have their own.
- `ParticleTypes.FLASH` is a `ParticleType<ColorParticleOption>` → `ColorParticleOption.create(ParticleTypes.FLASH, argb)`.
- `BaseEntityBlock` has no `codec()` to override (matching "Blocks: no codec() needed" in section 4).
- Entities: `DeferredRegister.createEntities(MODID)` + `registerEntityType(name, Factory::new, MobCategory.MISC,
  b -> b.sized(.3F,.3F).clientTrackingRange(8).updateInterval(2))`. Throwables moved to
  `net.minecraft.world.entity.projectile.throwableitemprojectile.ThrowableItemProjectile`. Renderer:
  `EntityRenderersEvent.RegisterRenderers` → `registerEntityRenderer(TYPE.get(), c -> new ThrownItemRenderer<>(c, scale, fullBright))`.
  Bounce a grenade off a wall by flipping the velocity component of `hitResult.getDirection().getAxis()`.
- Hitscan weapons: `level.clip(new ClipContext(from, to, Block.COLLIDER, Fluid.NONE, entity))` for the wall, then
  `entity.getBoundingBox().inflate(0.12).clip(from, to)` over `level.getEntities(shooter, AABB.enclosing(from,to), pred)`
  sorted by distance (simpler than `ProjectileUtil.getEntityHitResult`, and gives piercing for free).
  Headshot = hit y >= `entity.getY() + bbHeight * 0.72`.
- Cooldowns: `player.getCooldowns().addCooldown(stack, ticks)` / `isOnCooldown(stack)` /
  `removeCooldown(getCooldownGroup(stack))` — it also draws the vanilla cooldown sweep on the icon. Works on FakePlayers.
- `ServerLevel.sendParticles(particle, x, y, z, count, dx, dy, dz, speed)`; one call per point, so don't emit a tracer
  per block of a 200-block shot (muzzle flash + impact only, tracers just for slow-firing weapons).
- **Recipes: do not write a custom recipe type in 26.3 unless you must.** `Recipe` now carries `CommonInfo`,
  `CraftingBookInfo`, `PlacementInfo` and `RecipeDisplay` — a big, fast-moving surface. An in-Java catalogue browsed
  in your own menu (see Arsenal `craft/Blueprints` + `WeaponWorkbenchScreen`) costs ~150 lines, is self-documenting
  in game (no JEI needed), and nothing leaks into the vanilla crafting table.
- **A menu over a BlockEntity container never gets `slotsChanged`** (only crafting-style containers call it).
  Reconcile in `broadcastChanges()` instead — it runs every tick server-side. Guard it with `level.isClientSide()`.
- Data components as records: a record component cannot have an accessor of a different type (`int caliber` +
  `Caliber caliber()` won't compile) — name the component `caliberId`. Keeping a component to plain ints makes both
  `RecordCodecBuilder` and `StreamCodec.composite` trivial and keeps old saves loading.
- Guns/channelled items: **drive firing from a client packet**, not `Item#use` — `startUsingItem` slows the player to
  20% speed, which ruins an automatic weapon. `ClientTickEvent.Post` + `mc.options.keyUse.isDown()` + a local
  next-shot tick, `Item#use` returns **`PASS`**, and the server re-checks its own cooldown so a spammed packet can't
  outrun the weapon. NOT `CONSUME`/`SUCCESS`: vanilla re-runs "use" every 4 ticks while right-click is held and every
  Success calls `player.itemUsed(hand)` = the re-equip dip, so the held gun bobbed nonstop (user: "wiggles like it
  loops the reload animation"). After a PASS vanilla tries the off hand, so cancel
  `InputEvent.InteractionKeyMappingTriggered` when `isUseItem()` and `getHand() == OFF_HAND` while a gun is in the
  main hand (`setSwingHand(false)` + `setCanceled(true)`); block/entity interactions with the main hand still work. Apply recoil client-side (`player.setXRot(...)`) so it feels immediate.
- Aiming: `ComputeFovModifierEvent` (`setNewFovModifier(current * zoom)`) on the game bus, client dist only.
- **Destroying huge volumes without freezing the server**: budget blocks per tick (~24k) and eat the crater in
  Chebyshev rings outward from ground zero, so it reads as a shockwave. Per column: skip if `!level.hasChunkAt(pos)`
  (keeps it from generating thousands of chunks), start at `level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z)`
  so you never walk through air, skip `BlockTags.WITHER_IMMUNE` (bedrock/barrier), and use flags
  `UPDATE_CLIENTS | UPDATE_KNOWN_SHAPE | UPDATE_SUPPRESS_DROPS` (vanilla batches >64 changes per section into a
  section resend, so this is fine over the network). A 200-block radius crater is ~5M blocks ≈ 25 s of game time.
  The same class is the natural home for a delayed-task queue (`after(ticks, Runnable)`) for countdowns and
  multi-stage weapons; a task that re-schedules itself gives lingering clouds and walking beams for free, BUT then
  `tick()` must pull the due tasks out into a list first and run them after the loop (running them inside the
  iterator loop = `ConcurrentModificationException` = server crash; it shipped that way and crashed the nuke,
  singularity and smoke clouds). Iterate craters over `List.copyOf`, add a time budget (~25 ms/tick) next to the
  block budget (a laptop i5 lagged 2.4 s), clear static queues on `ServerStoppedEvent`, and GameTest a
  self-rescheduling chain through the real server tick (these tests reproduce the crash on the old loop).
- Item icons that sit on the diagonal like a vanilla tool: draw the thing **horizontally** in a roomy buffer
  (58×24), `scaled(2)`, rotate ~33° into a 128×128 canvas, then downsample ×4 taking the average of the opaque
  samples and thresholding alpha at half. Clean edges, no mush. Budget the length so
  `L*cos(angle) + H*sin(angle) <= 31` or it clips.
- GameTests never load client assets, so a missing texture/model/lang key only shows up in game. A ~90-line
  `tools/check_assets.py` that greps the item ids straight out of the Java enums and asserts a texture, item model,
  client item definition and translation for each one catches that whole class of bug for free (Arsenal has one).

## 11. Arsenal project map (`C:\Users\afjan\OneDrive\Desktop\Mod Minecraft\Arsenal`)
- Mod id `arsenal`, package `com.afjan.arsenal`, MC `[26.3]`, NeoForge `[26.3.0.7-beta,)`, built with 26.3.0.8-beta.
- 18 firearms (`gun/GunType`: 4 rifles, 4 pistols, 3 snipers, 4 shotguns, RPG-7, M32, railgun), 13 cartridges
  (`gun/Caliber`, incl. the PG-7V and TBG-7V rockets: Sneak+R swaps), 12 attachments in 4 slots (`gun/Attachment`), 8 thrown items (`combat/Ordnance`: 4 grenades +
  4 weapons of mass destruction), a Tactical Nuke block, 20 components, uranium worldgen and a flyable F-14
  Tomcat (section 13) with 4 stores (`vehicle/Store`: AIM-9, AIM-54, Zuni, Mk 82), 20mm shells and flares.
- `gun/GunData` = the item component (ammo, caliber id, attachment bit mask — all ints).
- `combat/`: `Ballistics` (hitscan + launcher spawning), `Reloading` (server-side state map, ticked from
  `PlayerTickEvent.Post`), `RailCharge` (railgun hold-to-charge: server-timed start in a synced attachment,
  `registry/ModAttachments`), `Detonations` (what every bomb does), `BlastScheduler` (ringed crater engine +
  delayed-task queue, ticked from `ServerTickEvent.Post`), `Ordnance` (enum).
- `craft/Blueprints` = the entire crafting catalogue in Java (~66 entries); only the workbench, steel and uranium
  smelting and the storage blocks are real recipe JSON. `menu/WeaponWorkbenchMenu` + `client/WeaponWorkbenchScreen`
  (256×250, drawn from rectangles, no GUI texture). A gun slot plus 4 attachment slots mirror the gun's component mask.
- `client/ArsenalClient`: trigger (`keyUse`; railgun = hold to charge, release to fire, `Charge` packet), reload key
  R (sneak+R changes cartridge), recoil (+ a physical shove from recoil 8 up), ADS FOV, ammo HUD; cancels the
  off-hand use while a gun is held (see section 10, `Item#use` PASS).
- `client/GunFeel`: viewmodel kick, view punch, muzzle flash + tracers (from the `ShotFx` packet; no smoke), crossbow-
  hold arm pose, charge-scaled railgun beam. All 18 guns + 8 throwables + 3 launched rounds are 3D models from
  `tools/gun_models.py` (user approved the look); `client/GunViewmodels.java` is GENERATED by gen_data (muzzle
  points); model switches `LoadedProperty` (`arsenal:loaded`), `ChargeProperty` (`arsenal:charge`, railgun coil compartments),
  `RoundProperty` (`arsenal:round`, RPG warhead). `client/OrdnanceRenderer` draws rockets/40mm as their 3D model
  along the flight path + motor flame; `client/ClientSounds` = sounds that follow an entity (charge whine, rocket roar).
- Sounds: `tools/gen_sounds.py` synthesises all 84 events (incl. 17 for the jet) + sounds.json; `registry/ModSounds`
  (names/ranges must match, check_assets verifies) with `broadcast` (near sound + distant version per listener);
  `Reloading` plays timed foley cues per gun (mag out/in/charge, shells, pump, bolt).
- `entity/OrdnanceEntity` — one entity for every kind of ordnance (incl. the jet's missiles, rockets, bombs and
  flares). `gametest/ArsenalGameTests` (31 tests; `JetGameTests` = the 9 for the F-14, in a 40x40 `jet_arena`).
- F-14: `vehicle/` (`FlightModel`, `F14Entity`, `F14Part`, `Store`, `JetWeapons`, `JetControl` packet, `F14Item`)
  + `client/vehicle/` (`F14Renderer` + `VehicleMesh`, `JetClient` controls/seekers/camera, `JetHud` green HUD,
  `JetFx` tracers, `JetSounds` loops with Doppler). Mesh + atlas + icon from `tools/vehicle_models.py`
  (called by gen_textures). The workbench has an AIR tab. Controls: mouse steers (flight path), W/S throttle
  (hold W at 100% for afterburner), A/D roll, LMB M61, RMB store, V next store, B flares, G gear, Space
  brakes, K canopy, LAlt free look, hold Shift to eject. The jet draws a green real-jet HUD: a deliberate
  exception to the user's 'no HUD bars' preference, not yet confirmed by them.
- Generated: `tools/gen_data.py` (250 JSON + lang + test structures), `tools/gen_textures.py` (92 PNGs + the mod icon
  and banner; the `preview` argument writes `build/texture_preview.png` and `build/block_preview.png`),
  `tools/check_assets.py` (validates assets against the Java enums, and every translation key, key mapping,
  texture and mesh named in the Java code).
- Release: `./gradlew --no-configuration-cache runGameTestServer build` → `build/libs/arsenal-1.0.0.jar`
  (a copy sits in the project root).

## 12. 3D item models + gun feel (Arsenal, Sept 2026)
- 3D items: cuboid `elements` models work for items in 26.3; element rotation takes **any angle** (right-hand rule,
  `{"angle","axis","origin"}`, or Euler `x/y/z`). Display transform = `T + Rx·Ry·Rz · S · (p/16 - 0.5)` (translation
  in units/16); define only the right-hand entries, the game mirrors left. Base frames: first person = view space
  after the hand offset (0.56, -0.52, -0.72), -z forward; third person (arm hanging) = frame +y forward, +z up, so a
  model pointing -z needs `rotation [90,0,0]`; GUI looks at the +z face, item frames at the -z face.
  Arsenal `tools/gun_models.py`: boxes -> per-face painted atlas (2 texels/unit, shelf-packed, non-square is fine)
  -> model JSON, GUI/fixed transforms solved from a rotation matrix (`euler_xyz`, auto-fit to the slot), and a
  **software renderer** (`py tools/gun_models.py [names]` -> `build/models_gui|side|fp|tp.png`) to tune without
  launching the game. Add an item by listing it in `GUNS`/`THROWN`; gen_data/gen_textures pick it up.
  Round parts = 2-3 crossed boxes (rounded-square section) with the inner boxes' end caps inset 0.06 (no z-fight).
  First person: place the grip at a fixed view point per kind + scale by mm-per-unit; third person: build the
  player chain in Python (`BODY = Ry180 . S(-1,-1,1) . S(0.9375) . T(0,-1.501,0)`, arm pivot (-5,2,0)/16,
  `Rz.Ry.Rx` of the pose, then ItemInHandLayer's `Rx(-90) . Ry(180) . T(1,2,-10)/16`) and solve the display
  rotation as `C^T . Ry(180)` so the barrel points forward; arm pose via `IClientItemExtensions.getArmPose`
  (consulted for any held item, not only while using; CROSSBOW_HOLD = two-handed aim).
  Loaded/empty looks: `RegisterConditionalItemModelPropertyEvent.register(id, MapCodec.unit(...))` + item
  definition `{"type":"minecraft:condition","property":"ns:loaded","on_true":...,"on_false":...}`.
  Stepped looks (charge meter, which rocket is loaded): `RegisterRangeSelectItemModelPropertyEvent.register(id, codec)`,
  a record implementing `RangeSelectItemModelProperty.get(stack, ClientLevel, ItemOwner, seed)` (`owner.asLivingEntity()`)
  + `{"type":"minecraft:range_dispatch","property":"ns:x","entries":[{"threshold":0.2,"model":...}],"fallback":...}`
  (highest threshold <= value wins; optional `scale`). gun_models.py: a spec's `states` (suffix -> state dict) +
  `dispatch` (property, [(threshold, suffix)]); boxes take `when=lambda s: ...`. **Every variant must reuse the base
  model's display transforms** (fitted per variant, the empty RPG was 45% bigger in third person - its z extent shrank).
- Viewmodel animation: `IClientItemExtensions.applyForgeHandTransform` (register in `RegisterClientExtensionsEvent`),
  apply the vanilla hand offset yourself (+ `equipProcess * -0.6` on y) then your own kick; returning true skips the
  swing. Changing a held stack's components (ammo) replays the pull-out dip every shot -> override
  `Item.shouldCauseReequipAnimation(old, new, slotChanged)` -> `slotChanged || !ItemStack.isSameItem(old, new)`.
- View punch that recovers: `ViewportEvent.ComputeCameraAngles` `setPitch/setYaw`; decaying impulses evaluated as
  `v0 * exp(-dt/tau)` with `System.nanoTime()` (no per-tick state).
- `PoseStack.mulPose(Quaternionf)` is gone in 26.3: `poseStack.rotate(Axis.YP.rotation(r))` (Quaternionfc),
  `rotate(axis, rad)`, `rotateDegrees(axis, deg)`; `mulPose` only takes a Matrix4fc / Transformation.
- A projectile drawn as a 3D item model: `EntityRenderer<E, S extends EntityRenderState>` with `createRenderState`,
  `extractRenderState` (`context.getItemModelResolver().updateForNonLiving(state.item, stack, ItemDisplayContext.NONE,
  entity)`, an `ItemStackRenderState` field) and `submit(state, poseStack, collector, CameraRenderState camera)` ->
  `state.item.submit(poseStack, collector, state.lightCoords, OverlayTexture.NO_OVERLAY, state.outlineColor)`. Context
  NONE = no display transform: model point p lands at p/16 - 0.5 (centre the model on 8,8,8), so the renderer's own
  rotate/scale is all there is. Point it along the velocity: `rotate(Axis.YP.rotation(atan2(-dx,-dz)))` then
  `rotate(Axis.XP.rotation(asin(dy)))` for a nose at -z. Override `getBlockLightLevel` -> 15 for a burning motor.
  Skip drawing while `age < 2` within 3 blocks of the camera (vanilla ThrownItemRenderer does too), start smoke
  particles a few ticks late, and fade additive glows by camera distance - otherwise a launch flashes the screen.
- Hold-to-charge weapons: the user wants the charge shown ON the gun (the railgun's coil compartments light up one
  by one, white-hot when full), no HUD bar, and the held gun perfectly still (no tremble). The client keeps its own
  charge (instant model switch; first step at > 0 so the press answers at once), the server stores the
  start game time in a synced attachment on `Charge(start)` and computes the charge itself on release (no trusting
  the client), and everyone else derives it from that attachment (`(gameTime - start + partial) / FULL`). Cancel on
  slot change, reload or an opened screen; don't send packets when `mc.getConnection() == null`.
- Sounds that follow an entity: subclass `AbstractTickableSoundInstance` (set `looping`, `delay = 0`, copy x/y/z in
  `tick()`, `stop()` when the entity is removed or a condition ends), keep one per entity id, `getSoundManager().play`.
  Seamless loops for them: exactly 1.0 s, every partial a whole number of Hz, noise filtered as a loop (filter three
  copies, keep the middle), no end fade; check the seam jump |x[0]-x[-1]| against the mean sample step.
- First-person points on screen -> world: hands draw at a fixed 70 deg FOV; world point = camera pos + forward*(-vz)
  + up*vy*s + right*vx*s with `s = tan(camera.getFov()/2) / tan(35 deg)` (`Camera.getFov()`, forward/up/leftVector()).
- Server->client packets: common `registrar.playToClient(TYPE, CODEC)` without handler + client
  `RegisterClientPayloadHandlersEvent.register(TYPE, handler)`; send with
  `PacketDistributor.sendToPlayersTrackingEntityAndSelf`. `Vec3.STREAM_CODEC.apply(ByteBufCodecs.list(n))`.
- **Sound synthesis without libraries** (Arsenal `tools/gen_sounds.py`): pure-Python DSP (RBJ biquads, gliding
  one-pole LPs, recursive-sine metal modes, Friedlander blast pulse) -> 16-bit WAV (`wave` module) -> Ogg Vorbis
  with **ffmpeg + libvorbis**: none on PATH, but OBS inside Overwolf ships one
  (`%LOCALAPPDATA%\Overwolf\Extensions\*\*\obs\bin\64bit\ffmpeg.exe`); CapCut's ffmpeg only has the weak
  native `vorbis` encoder. `-q:a 5 -fflags +bitexact -map_metadata -1`. ProcessPoolExecutor: 149 files in ~35 s.
  Level control that works: soft-clip (tanh) ONLY the direct bang, then add tail/echoes/mechanics at fixed dB
  (clipping everything boosted the tail ~10 dB = washy). Targets (RMS re peak): rifle -17 dB @100 ms, -30 @200 ms;
  pistol -30 @100 ms; .50/AWP -8..-11 @100 ms with 2-3 s tails. Measure with a WAV level table, not by eye; give
  every long layer a fade (cut-off buffers click). No way to listen: plan by physics, check envelopes + band plots.
- Minecraft sound facts (26.3): positional sounds must be mono; loudness is linear to
  `max(volume,1) * attenuation_distance` (sounds.json, default 16) -> use `SoundEvent.createFixedRangeEvent(id,
  range)` with range == attenuation_distance and play at volume 1. Near/far per listener: send
  `new ClientboundSoundPacket(holder, source, x, y, z, vol, pitch, seed)` via `player.connection.send` yourself.
  Per-player sounds (ringing ears) the same way at the player's position. The shooter predicts his own shot with
  `level.playLocalSound(...)` and is excluded server-side. `Level#explode` makes the CLIENT play the explosion
  sound at pitch ~0.7: pass the full overload with `BuiltInRegistries.SOUND_EVENT.wrapAsHolder(SoundEvents.EMPTY)`
  (`intentionally_empty`, silent; an empty "sounds": [] list warns once) and play your own; the overload needs a
  `WeightedList<ExplosionParticleInfo>` (`net.minecraft.core.particles`) and `Explosion.getDefaultDamageSource`.

## 13. Vehicles: the F-14 (Arsenal, Sept 2026)
- **Who simulates**: fly it like a vanilla boat. `isLocalInstanceAuthoritative()` is true on the client of the
  controlling passenger (`getControllingPassenger()` = the first `Player` passenger) and on the server when nobody
  flies it; vanilla `LocalPlayer` already sends `ServerboundMoveVehiclePacket`. The server's `handleMoveVehicle`
  rejects "moved too quickly" when `movedDist - vel^2 > 100` (not in singleplayer) and re-moves the vehicle with its
  own AABB ("moved wrongly" > 0.25) -> keep the entity's own box small (override `makeBoundingBox(Vec3)`, e.g.
  centred) and do ground/terrain collision yourself; `isFlyingVehicle()` -> true stops the "floating too long" kick.
  Everything else (orientation, velocity, switches, fire/cycle/flare sequence counters, locked target, "I crashed")
  goes in one `playToServer` packet per tick; the server clamps it (speed cap, gear stays down on the ground) and
  runs weapons and damage. Other players see it smoothed: `createInterpolationHandler()` ->
  `LinearInterpolationHandler.create(this, 3)`; orientation = a joml `Quaterniond` in 4 synced floats, slerped.
- **Hitboxes bigger than an AABB**: NeoForge `PartEntity<T>` (like `EnderDragonPart`): `isMultipartEntity()` +
  `getParts()`, parts built in the constructor right after `super` (they take the next ids, the dragon relies on the
  same), client side `recreateFromPacket` -> `parts[i].setId(packet.getId() + i + 1)`; move them every tick; part
  `hurtServer`/`interact` forward to the parent. `Level.getEntities` includes parts (filter your own out).
- **Seats**: `canAddPassenger` (seat count), `positionRider(p, moveFunction)` (eye at the seat:
  `eye.y - p.getEyeHeight()`), `getDismountLocationForPassenger` (ladder on the ground, above the canopy when
  ejecting). Block getting out in the air with `EntityMountEvent` (`isDismounting()`, cancel server-side only). 26.3
  `Player.rideTick` calls `stopRiding()` every tick while sneak is held and, when that is cancelled, skips the
  passenger's own tick + positioning; `isShiftKeyDown()` stays true server-side while held -> hold-Shift-to-eject.
  **NeoForge `FakePlayer.startRiding` always returns false**: GameTests cannot seat a fake pilot, and logic must not
  depend on "the owner still sits in it" (projectiles remember the jet they came off: `setLauncher`).
- **Drawing a big mesh**: generate quads in Python (lofted sections, slabs, cylinders; Arsenal `tools/vehicle_models.py`),
  paint one atlas, write a small zlib'd binary (parts: name, parent, pivot, axis, flags; quads: xyz+uv x4, normal),
  load it with `Minecraft.getInstance().getResourceManager().getResource(id)` + `InflaterInputStream`, and in
  `submit` per part: push, apply the parent chain's transforms (rotation about pivot/axis), then
  `collector.submitCustomGeometry(poseStack, RenderTypes.entityCutout(tex)` (glass: `entityTranslucent`)`,
  (pose, buf) -> buf.addVertex(pose, x, y, z).setColor(-1).setUv(u, v).setOverlay(o).setLight(l).setNormal(pose,
  nx, ny, nz))`. Lamps/screens: light `LightCoordsUtil.FULL_BRIGHT` per quad; red hurt flash
  `OverlayTexture.pack(0.0F, true)`; override `getBoundingBoxForCulling` and `shouldRenderAtSqrDistance`. 4,300 quads
  and 58 animated parts render fine. A Python software renderer of the same mesh (`py tools/vehicle_models.py side
  gear ...`) catches most mistakes before the game does.
- **Cockpit**: `CalculateDetachedCameraDistanceEvent.setDistance` (third-person camera far enough back),
  `RenderHandEvent` cancel (no arm), `RenderGuiLayerEvent.Pre` + `VanillaGuiLayers.HOTBAR/CROSSHAIR/...` cancel, seated
  players hidden with `RenderLivingEvent.Pre` cancel (the jet draws helmeted crew). HUD symbols on world points:
  `camera.getViewRotationProjectionMatrix(new Matrix4f())` on the camera-relative point (`Vector4f`, w <= 0 = behind),
  then `(x/w + 1)/2 * guiWidth`, `(1 - y/w)/2 * guiHeight`. Only `ClientLevel` has `entitiesForRendering()`.
- **Flight model that plays well** (Arsenal `vehicle/FlightModel`, prototype in Python first): point mass + angle of
  attack, lift ~ alpha * v^2 (flat past the stall), rate-commanded nose (fly-by-wire), weathervane into the relative
  wind. Mouse aim = a g-command autopilot flying the *velocity* onto the look vector: roll the lift vector onto the
  aim point first, then pull; cap the pull at what the wings give at the alpha limit (so it cannot stall itself);
  push (don't roll inverted) for aim points slightly below; feed the path rate forward to kill steady-state alpha
  error; level-turn fallback for aim points behind (else it split-S's). GameTest the model with no world at all.
- **Take-off rotation**: compute the tail-strike angle from the mesh (min over vertices behind the main wheels of
  atan(height above the contact / distance behind it)); the F-14's ventral fins touch at 8 deg -> rotation limit
  7.5 deg plus a guard near the ground (pitch <= atan((0.76 + wheel height) / 5.6)) or landing flares scrape the tail.
- **Guided missiles**: sync the target id (`EntityDataAccessor<Integer>`), guide on both sides (aim at target +
  target velocity * time-to-go, turn the velocity towards it by at most `turnRate` a tick, slerp on the unit
  sphere), proximity fuse server-side; heat seekers may switch to a flare near the target; the target vehicle gets a
  synced warning flag for the cockpit tone. Launch from the jet's velocity (+ a kick), not from rest.

## 14. MinecraftForge 66 (26.3) — learned building Tempered (all verified)
Not NeoForge: different Gradle plugin, event bus and hooks. Most MC 26.3 facts in sections 4/10 still apply.
- Versions: `curl -s https://maven.minecraftforge.net/net/minecraftforge/forge/maven-metadata.xml | grep -o '<version>26\.3[^<]*'`;
  MDK = `.../forge/<mc>-<ver>/forge-<mc>-<ver>-mdk.zip` (ForgeGradle 7 `net.minecraftforge.gradle` `[7.0.17,8)`, Gradle
  9.7.1; `minecraft.dependency("net.minecraftforge:forge:26.3-66.0.8")`). First build ≈ 3 min. FG7 supports the
  configuration cache: plain `./gradlew build` / `runGameTestServer` work (no `--no-configuration-cache` needed).
- Sources: `~/.gradle/caches/minecraftforge/forgegradle/mavenizer/caches/forge/net/minecraftforge/forge/<ver>/injected-sources.jar`
  (patched MC + Forge; `...-sources.jar` there is Forge only). Vanilla assets/data: `.../mavenizer/caches/minecraft_tasks/26.3/client.jar`.
  EventBus sources: `https://maven.minecraftforge.net/net/minecraftforge/eventbus/7.0.6/eventbus-7.0.6-sources.jar`.
  Forge's own test mods (examples of everything): `.../net/minecraftforge/forge-tests/<ver>/forge-tests-<ver>-sources.jar`.
- Mod class: `@Mod(ID) public Mod(FMLJavaModLoadingContext ctx)`, `var bus = ctx.getModBusGroup()`,
  `DeferredRegister.create(Registries.X | ForgeRegistries.Keys.X, ID).register(bus)`, `RegistryObject<T>`.
  Client-only code behind `if (FMLEnvironment.dist == Dist.CLIENT)`.
- **EventBus 7**: every event has a static `BUS`; mod-bus events use `X.getBus(bus)`. Cancellable buses:
  `addListener(Predicate)` (return true = cancel; cancelling stops later listeners), `addListener(byte priority, Predicate)`,
  `addListener(ObjBooleanBiConsumer)` = monitor. **Bug in 7.0.6: an event whose only listeners are monitors gets a no-op
  invoker, the monitors never run** (LivingDeathEvent, ItemFishedEvent, EntityPlaceEvent, LivingDamageEvent ...). Use
  `addListener(Priority.LOWEST, e -> { ...; return false; })` instead: it runs last and only if nobody cancelled.
  Same-priority listeners keep registration order. Use method refs / block lambdas so Predicate vs Consumer is unambiguous.
- Events that exist: `BlockEvent.BreakEvent` (before removal, `setExpToDrop`), `BlockEvent.EntityPlaceEvent`,
  `PlayerEvent.BreakSpeed/HarvestCheck/ItemCraftedEvent`, `LivingHurtEvent` (before armour) / `LivingDamageEvent`
  (after), record `LivingDeathEvent`, `LootingLevelEvent` (feeds 26.x looting loot functions), `LivingExperienceDropEvent`,
  `AnvilUpdateEvent` (setOutput/setCost/setMaterialCost -> skips vanilla anvil logic), `ItemFishedEvent`,
  `ProjectileImpactEvent`, `EntityJoinLevelEvent`, `LivingEntityUseItemEvent.Start/Tick` (Tick: `setDuration` = remaining
  use ticks, lower it for faster bows/crossbows/tridents), `PlayerInteractEvent.RightClickItem/RightClickBlock`
  (`setUseItem(net.minecraftforge.common.util.Result.DENY)`) and `EntityInteractSpecific` (the only entity-interact
  event), `TickEvent.ServerTickEvent/PlayerTickEvent/ClientTickEvent.Post` (records: `event.player()`),
  `ItemTooltipEvent` (record: `getToolTip()` list), `RegisterKeyMappingsEvent`, `RegisterItemDecorationsEvent`
  (`IItemDecorator.render(g, font, stack, x, y)`: pips/overlays on item icons), `TagsUpdatedEvent`, `ServerStoppedEvent`.
  Missing vs NeoForge: ItemAttributeModifierEvent, BlockDropsEvent, BlockToolModificationEvent, FakePlayer, GetEnchantmentLevelEvent.
- Global loot modifiers: `DeferredRegister.create(ForgeRegistries.Keys.GLOBAL_LOOT_MODIFIER_SERIALIZERS, ID)` registering a
  `MapCodec` (`RecordCodecBuilder.mapCodec(i -> codecStart(i).apply(i, Ctor::new))`, class extends `LootModifier`) +
  `data/forge/loot_modifiers/global_loot_modifiers.json` `{"replace":false,"entries":["ns:x"]}` + `data/ns/loot_modifiers/x.json`
  `{"type":"ns:x","conditions":[]}`. `LootContextParams.TOOL` is an `ItemInstance` (instanceof ItemStack); block loot runs
  inside `playerDestroy` after the block is gone; `context.getQueriedLootTableId()` tells fishing (`gameplay/fishing`)
  and shearing (`shearing/...`) apart; only the top-level table is modified.
- **Mixin**: Forge 66 ships Mixin 0.8.7 + MixinExtras 0.5.4. Jar manifest `MixinConfigs: x.mixins.json` (build.gradle
  `jar { manifest { attributes['MixinConfigs'] = ... } }`) and for dev runs `args "--mixin.config=x.mixins.json"` in
  `minecraft.runs.configureEach`. 26.x is unobfuscated: no refmap, no annotation processor. `compatibilityLevel`
  JAVA_21 (the highest 0.8.7 knows) works with Java 25 class files. `@Mutable @Accessor` sets final fields
  (FishingHook luck/lureSpeed); static `@Invoker` in an interface calls private static methods.
- Durability hooks (ItemStack): `hurtAndBreak(int, ServerLevel, ServerPlayer, Consumer)` -> `processDurabilityChange`
  (Unbreaking) -> private `applyDamage(newDamage, player, onBreak)` which shrinks the stack when `isBroken()`
  (damage >= max). Tempered's mixin cancels `applyDamage` for tagged tools and parks them at damage == max: vanilla then
  skips their attribute modifiers (`LivingEntity.collectEquipmentChanges` checks `isBroken()`), the durability bar is empty.
  `forEachModifier(EquipmentSlot, BiConsumer)` and `(EquipmentSlotGroup, TriConsumer)` feed equipment attributes and
  the tooltip: inject at TAIL to add per-stack modifiers (there is no attribute-modifier event).
- Items: `DataComponentType.builder()...ignoreSwapAnimation()` = changing the component on a held item never replays
  the re-equip dip (counters on every block mined). `Item.components()` can throw before a world is loaded (26.x binds
  delayed components like BLOCK_TRANSFORMER late): never read item components during client init events.
  Hoe/axe/shovel right-clicks are data-driven `BlockTransformer.transformBlock(UseOnContext)` (mixin at RETURN to count
  tilling). Vanilla tags in 26.3: `minecraft:ores`, `iron_ores` ... (Java: `BlockItemTags.IRON_ORES.block()`), `spears`;
  entity tag test = `entity.is(TagKey<EntityType<?>>)`. Forge adds `c:ores`, `c:gravels`, `c:obsidians` ... tags.
- GameTests: `@GameTestNamespace(ID) @GameTestPrefix("x")` class with `@GameTest public static void name(GameTestHelper)`;
  register with `ForgeGameTestHooks.gatherTests(cls, null)` inside `if (ForgeGameTestHooks.isGametestEnabled())` +
  `RegisterEvent` for `Registries.TEST_FUNCTION`. Test instances are data: `data/<ns>/test_instance/x/<name>.json`
  `{"type":"minecraft:function","environment":"minecraft:default","function":"ns:x/<name>","max_ticks":100,
  "structure":"forge:empty7x5x7"}` (Forge builds `forge:emptyWxHxD` air boxes on the fly; optional `"sky_access": true`).
  Tempered's `tools/gen_tests.py` writes them from the annotations; exclude them from the jar. `helper.addCleanup(...)`.
  **No FakePlayer**: build a connected ServerPlayer (`CommonListenerCookie.createInitial`, `new Connection(SERVERBOUND)` +
  `new EmbeddedChannel(connection)`, `playerList.placeNewPlayer`, `setGameMode(SURVIVAL)`, `snapTo`; Tempered
  `gametest/TestPlayers`): `gameMode.destroyBlock`, chat and inventory syncing then work. Mock players float
  (not on ground) -> destroy speed is 5x lower: compare ratios. A GameTest can write docs (Tempered's CHALLENGES.md; the
  run dir is `run/`).
- Anvil: vanilla's prior-work penalty (REPAIR_COST) makes repairs "Too Expensive"; an AnvilUpdateEvent output with
  your own cost bypasses it (Tempered: 1 level per material).

## 15. Tempered project map (`Tempered/`, MinecraftForge 66.0.8)
- Mod id `tempered`, package `com.afjan.tempered`, MC `[26.3,26.4)`, Forge `[66.0.8,)`. Tool mastery: 42 tiered tools
  (wooden..netherite x pickaxe/axe/shovel/hoe/sword/spear) with 20 levels each + bow, crossbow, trident, mace, shears,
  fishing rod with 5; tools never vanish (Broken at 0 durability, cheap anvil repair). User feedback on v1 (5 levels
  everywhere): "20 levels for the main tools, late game harder" -> main work follows `Tracks.CURVE` (XX = 300x level I)
  plus "rungs" (extra challenges with fallbacks by `Tier.miningLevel`); capstones at XX: Magnet, Soul Harvest, Warhorse.
- `mastery/`: `Tracks` = the whole catalogue in code (requirements scale with `Tier.scale`, speed/damage perks with
  `Tier.power`; `Track.maxLevel()` differs per track, never hard-code a level count), `Mastery` = the item component (uid + lifetime counters; the level is derived, so a netherite upgrade
  re-measures the same counters), `Stat`/`Perk`/`Kind`/`Tier`/`Milestone`/`Track`, `Progress` (count + level-up chat,
  sound, particles), `MasteryAttributes` (attack speed via the forEachModifier mixin), `LangKeys`.
- `event/`: `ProgressEvents` (counting; placed blocks via `PlacedBlocks` never count), `PerkEvents` (speed, damage,
  lifesteal, looting, XP, draw speed, arrow saver, lure/luck), `BrokenTools` (broken behaviour, anvil), `Weapons`
  (which item made a hit: arrows carry a copy of the bow, found again by the component uid). `ability/Abilities` (Vein
  Miner, Excavate, Timber, Reaper through `gameMode.destroyBlock` + guard, Shockwave, Volley, self-loading crossbow,
  Cavalry, Stormcaller, Shear Sweep), `ability/Replanter`, `loot/MasteryLootModifier` (yield, treasure, replant),
  `mixin/` (ItemStack durability + modifiers, BlockTransformer, FishingHook accessor, CrossbowItem invoker),
  `client/` (K = `MasteryScreen` overview drawn from rectangles, auto-scrolls to the current milestone;
  `MasteryTooltip`; `MasteryDecorator` = mastery bar along the icon's top edge + cracks).
- 1.2.0 (user request): **repair kits** `item/RepairKitItem` x7 (`registry/ModItems`, `<prefix>_repair_kit`) = 1 iron
  ingot + the vanilla `#<tier>_tool_materials` tag; 15% durability per kit vs the anvil's 25% per material (the user
  wanted the anvil to stay better; `anvilBeatsRepairKits` checks every tool). Applied bundle-style
  (`Item.overrideStackedOnOther` / `overrideOtherStackedOnMe`, right-click = `ClickAction.SECONDARY`, runs on both
  sides; test through `player.inventoryMenu.setCarried(...)` + `clicked(slot, 1, ContainerInput.PICKUP, player)`) or
  from the other hand (`use`). **Recipe conflicts:** two iron ingots side by side = heavy pressure plate, diagonal =
  shears, so the iron kit is shaped (stacked); scan vanilla recipes from client.jar and GameTest every recipe
  through `level.recipeAccess().getRecipeFor(RecipeType.CRAFTING, CraftingInput.of(w, h, items), level)`.
  **Ore weights** (`mastery/OreWeights`: coal/copper/quartz 2, nether gold 3, iron/redstone 4, gold/lapis 6, diamond
  10, emerald 12, debris 16) only for the pickaxe's MINED counter: `blockStats` lists MINED n times.
- Challenge texts with amount 1 use a singular key (`Milestone.Req.key` -> `stat.tempered.elite.one`); a translatable
  with fewer args than `%s` renders the raw format string, so give every changed format its own key.
- `gametest/TemperedGameTests` (37 tests) + `tools/gen_tests.py`; `tools/gen_logo.py` (logo from the vanilla pickaxe), `tools/gen_textures.py` (repair kit icons + `build/kit_preview.png`).
  Connected test players are invulnerable (`ServerPlayer.isInvulnerableTo`: `!connection.hasClientLoaded()`): test
  damage changes to them by posting a `LivingHurtEvent` yourself. `startRiding(horse, true, false)` works for them.
- Release: `./gradlew runGameTestServer build` -> `build/libs/tempered-1.2.0.jar` (a copy sits in `Tempered/`);
  `CHALLENGES.md` is rewritten by the `write_challenge_sheet` test.

## 16. Hatchery project map (`Hatchery/`, MinecraftForge 66.0.8)
- Mod id `hatchery`, package `com.afjan.hatchery`, same Gradle setup as Tempered minus mixins. Mined spawners (and
  trial spawners, pickaxe only) drop a Broken Spawner; mobs killed by a player drop their spawn egg at 0.5% (+0.1% per
  Looting level); a spawn egg used on a Broken Spawner turns it into a vanilla spawner of that mob.
- `block/BrokenSpawnerBlock` (`useItemOn` + static `revive`), `block/BrokenSpawnerItem` (tooltip),
  `loot/SpawnEggModifier` (GLM), `event/EggDrops` (glow, no despawn, chime, action-bar notice), `registry/`
  (`ModBlocks` incl. creative tabs, `ModLoot`, `ModTags.NO_SPAWN_EGG`), `gametest/HatcheryGameTests` (18 tests).
  `tools/gen_textures.py` recolours the vanilla spawner into the broken cage + logo (`build/texture_preview.png`).
- Lessons (26.3 + Forge 66, verified):
  - Overriding a vanilla block's drops = ship `data/minecraft/loot_table/blocks/<block>.json` in the mod (vanilla's
    spawner/trial_spawner tables exist but are empty). Loot **pools** take one `"condition"` (combine with
    `minecraft:all_of` + `terms`); a GLM's JSON takes a `"conditions"` list. `match_tool` = `{"predicate":{"items":"#tag"}}`.
  - Forge routes `random_chance_with_enchanted_bonus` for Looting through `LootContext.getLootingModifier()` =
    `LootingLevelEvent`, so Tempered's mastery Looting raises data-driven chances too.
  - A GLM keyed on `THIS_ENTITY` must also check `context.getQueriedLootTableId()` == `entity.getLootTable()`:
    shearing, bartering, cat gifts ... also carry THIS_ENTITY. `killed_by_player` = LAST_DAMAGE_PLAYER is present.
  - Spawn eggs: `SpawnEggItem.getType(stack)`, `SpawnEggItem.byId(type)` -> `Optional<Holder<Item>>` (scans the items'
    `ENTITY_DATA` default component: cache it). Vanilla 26.3 lets survival players re-type spawners with an egg;
    `SpawnerBlockEntity.setEntityId(type, random)`, read back with `getSpawner().getOrCreateDisplayEntity(level, pos)`.
  - `Block.useItemOn` runs before the held item's `useOn`; return `TRY_WITH_EMPTY_HAND` to let the item act.
    `ItemStack.consume(1, player)` skips creative. Registration: `Properties.of().setId(BLOCKS.key(name))`,
    `new Item.Properties().setId(ITEMS.key(name)).useBlockDescriptionPrefix()`; creative tabs:
    `BuildCreativeModeTabContentsEvent.BUS` (global bus, record, `getTabKey()`, `accept(supplier)`).
  - `LivingDropsEvent` (record) carries the captured `ItemEntity`s before they enter the world:
    `setGlowingTag(true)` + `setUnlimitedLifetime()` mark a rare drop.
  - Testing drop rates: build `LootParams` like `LivingEntity.dropFromLootTable` and call `table.getRandomItems(params)`
    20,000x (GLMs apply); victims without AI from `type.create(level, EntitySpawnReason.TRIGGERED)` + `snapTo` (a
    Wither that never enters the world). 3,000 real spawn-and-kill cycles in one GameTest take ~1 s.
- 1.1.0: **spawner modules** (`spawner/Module`, `SpawnerModules`, `ModuleItem`): Swarm/Haste V, Frailty IV (level N uses
  N modules), Daylight, Redstone. Stored as a data component on the vanilla spawner's block entity
  (`be.setComponents(...)`, saved under "components" - Forge 66 has no BE persistent data). Swarm/Haste rewrite
  `SpawnCount`/`MaxNearbyEntities`/`Min|MaxSpawnDelay` by a save -> edit tag -> `BaseSpawner.load` round trip; Daylight
  adds `custom_spawn_rules` (light 0-15, also skips the mob's own placement rules) to `SpawnData` + `SpawnPotentials`.
  Forge's `PositionCheck` gets a **null** spawner, `FinalizeSpawn.getSpawner()` works (and `getSpawnerBlockEntity()`):
  mark the mob there, then cancel (redstone) or weaken (frailty) it in `EntityJoinLevelEvent` - after finalizeSpawn,
  which resets slime health; a cancelled join makes the spawner wait for its next wave. Cancelling FinalizeSpawn
  does NOT stop the spawn. GameTests run real waves with `BaseSpawner.serverTick` after loading `Delay: 0`.
  `ModuleRefundModifier` (GLM on `BLOCK_ENTITY`) gives all modules back when the spawner is mined.
- Release: `./gradlew runGameTestServer build` -> `build/libs/hatchery-1.1.0.jar` (a copy sits in `Hatchery/`).

## 17. Townsfolk project map (`Townsfolk/`, MinecraftForge 66.0.8)
- Mod id `townsfolk`, package `com.afjan.townsfolk`, Hatchery's Gradle setup. Villager QoL: sneak + empty hand picks a
  villager up (`villager/CapturedVillager` = saved entity NBT minus UUID + a tooltip summary of the trades, component
  `townsfolk:villager` on item `townsfolk:villager`); use on a block sets it down; use in the air = pocket trading
  (spawn in front, `villager.mobInteract(player, hand)` opens the trades, `PlayerContainerEvent.Close` puts it back);
  sneak + workstation item = that job (`villager/Jobs`: `PoiTypes.forState(block)` -> profession whose
  `heldJobSite` matches), own workstation = unemployed; trades restock on opening if 5 min old (`PlayerContainerEvent.Open`,
  the trading player is already set, offers are sent after the event; last restock in `getPersistentData()`).
- 26.3 villager facts: `mobInteract` ignores sneaking players (sneak gestures never open trades); `setVillagerData`
  with a new profession drops the offers (`getOffers()` regenerates them); `ResetProfession` fires a villager with
  0 XP, level <= 1 and no job site, so a job given without the block needs XP >= 1; `releaseAllPois` is private:
  call `releasePoi` for HOME/JOB_SITE/POTENTIAL_JOB_SITE/MEETING_POINT before removing a villager (else its bed and
  workstation stay claimed); `restock()`/`refreshBrain(level)` are public; `Inventory.placeItemBackInInventory(stack,
  Prediction.SERVER_ONLY)`. GameTests: count entities inside your own structure box only (neighbour tests' mobs).
- Release: `./gradlew runGameTestServer build` -> `build/libs/townsfolk-1.0.0.jar` (a copy sits in `Townsfolk/`).

## 18. 1.21.8 ports (NeoForge 21.8.54) — `Tempered-1.21.8/`, `Hatchery-1.21.8/`, `Townsfolk-1.21.8/`
- The user asked for "1.12.8, neoforge 21.8.54" = Minecraft **1.21.8**. Same content as the Forge 26.3 mods except
  what 1.21.8 lacks (Tempered: no copper tier/kit, no spears, no Cavalry/Warhorse). Jars `<modid>-1.21.8-<ver>.jar`
  in each folder; they go into a NeoForge 21.8.54 (MC 1.21.8) profile, never next to the 26.3 jars.
- Setup: NeoForgeMDKs `MDK-1.21.8-ModDevGradle` (MDG 2.0.147, Java 21 preinstalled, Parchment), then
  `porting/port_setup.py <Mod>` (skeleton + mods.toml template with `[[mixins]]`, drops GameTests), `port_rename.py`
  (mechanical renames), `port_events.py <java root> <package>` (EventBus 7 `X.BUS.addListener` -> an `Events.listen`
  helper; its Predicate overload is bound to `ICancellableEvent`, so returning true cancels and lambdas stay unambiguous).
  First `createMinecraftArtifacts` ≈ 4 min; sources = `build/moddev/artifacts/neoforge-21.8.54-sources.jar`, vanilla
  data/assets = `...-client-extra-aka-minecraft-resources.jar`.
- No GameTests in the ports: `runGameTestServer` boots, loads all data (logs broken tags/loot/advancements/mixins)
  and exits by itself -> grep the log for ERROR. It caught `#minecraft:ores` (26.x only) and advancement keys.
- 26.3 Forge -> 1.21.8 NeoForge: `Identifier`->`ResourceLocation`, `key.identifier()`->`location()`, `EntityTypes`->
  `EntityType`, `npc.villager.*`->`npc.*`, `sendOverlayMessage(c)`->`displayClientMessage(c, true)`, `BlockItemTags.X
  .block()`->`BlockTags.X`, `entity.is(tag)`->`entity.getType().is(tag)`, `SpawnEggItem.getType(stack)`-> instance
  `getType(registries, stack)`, `byId` returns the item; GUI `GuiGraphicsExtractor`->`GuiGraphics` (`text`->`drawString`,
  `outline`->`renderOutline`, `item`->`renderItem`, `extractRenderState`->`render`), `mouseClicked(double,double,int)`,
  `keyPressed(int,int,int)`, `Screen.hasShiftDown()`, `mc.screen`/`mc.setScreen`, KeyMapping category is a String.
- Events: LivingHurtEvent -> `LivingIncomingDamageEvent`; LivingDamageEvent -> `LivingDamageEvent.Post#getNewDamage`;
  LootingLevelEvent -> `GetEnchantmentLevelEvent` (`isTargetting(Enchantments.LOOTING)`, `getEnchantments().set`);
  BreakEvent XP -> `BlockDropsEvent#setDroppedExperience`; FinalizeSpawn -> `FinalizeSpawnEvent#getSpawner()` =
  `Either<BlockEntity, Entity>`; `AnvilUpdateEvent#setXpCost`; `setUseItem(net.minecraft.util.TriState.FALSE)`;
  `PlayerTickEvent#getEntity`; creative tabs, key mappings and item decorators on the **mod bus**.
- Data: loot conditions use `"condition": "minecraft:x"`, pools a `"conditions": [...]` list; GLM list in
  `data/neoforge/loot_modifiers/`, `doApply(ObjectArrayList, LootContext)`, `context.getOptionalParameter(...)`;
  `recipe_unlocked` takes `"recipe"`. Render types are NOT picked from texture alpha: add `"render_type":
  "minecraft:cutout"` to a see-through block model.
- Mixins: NeoForge routes damage through `ItemStack.hurtAndBreak/applyDamage(..., LivingEntity, Consumer<Item>)`;
  no `ignoreSwapAnimation` -> client mixin on `ItemInHandRenderer.shouldInstantlyReplaceVisibleItem` (ignore our
  component); no BlockTransformer -> inject `HoeItem.useOn` RETURN. `forEachModifier` injections also reach NeoForge's
  attribute tooltips (`AttributeUtil` calls the EquipmentSlotGroup overload).
