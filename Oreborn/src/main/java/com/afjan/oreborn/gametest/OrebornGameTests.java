package com.afjan.oreborn.gametest;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

import com.afjan.oreborn.Oreborn;
import com.afjan.oreborn.ability.ArmorSets;
import com.afjan.oreborn.ability.Combat;
import com.afjan.oreborn.ability.ForceLightning;
import com.afjan.oreborn.ability.OreRadar;
import com.afjan.oreborn.ability.Shock;
import com.afjan.oreborn.ability.ToolActions;
import com.afjan.oreborn.item.MiningMode;
import com.afjan.oreborn.item.OrebornToolItem;
import com.afjan.oreborn.material.GearType;
import com.afjan.oreborn.material.OreMaterial;
import com.afjan.oreborn.registry.ModBlocks;
import com.afjan.oreborn.registry.ModDamageTypes;
import com.afjan.oreborn.registry.ModEffects;
import com.afjan.oreborn.registry.ModItems;
import com.mojang.authlib.GameProfile;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.FunctionGameTestInstance;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestData;
import net.minecraft.gametest.framework.TestEnvironmentDefinition;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.item.crafting.SmithingRecipeInput;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.FarmlandBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForgeMod;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * In-game tests run by {@code gradlew runGameTestServer} (development only, never registered in a normal game).
 * Players are NeoForge fake players: real server players (inventory, game mode, block breaking) that don't tick
 * and ignore teleports, so teleport abilities are checked through their destination finders.
 */
public final class OrebornGameTests {
    private static final DeferredRegister<Consumer<GameTestHelper>> FUNCTIONS = DeferredRegister.create(Registries.TEST_FUNCTION, Oreborn.MODID);
    private static final List<Test> TESTS = new ArrayList<>();

    private record Test(DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> function, int maxTicks) {}

    static {
        test("ore_drops", OrebornGameTests::oreDrops);
        test("ores_generate", OrebornGameTests::oresGenerate);
        test("recipes", OrebornGameTests::recipes);
        test("x_cross_pattern", OrebornGameTests::xCrossPattern);
        test("strip_mine_tunnel", OrebornGameTests::stripMineTunnel);
        test("mining_mode_switch", OrebornGameTests::miningModeSwitch);
        test("frost_heave_3x3", OrebornGameTests::frostHeave);
        test("molten_core_vein", OrebornGameTests::moltenCoreVein);
        test("worldfeller", OrebornGameTests::worldfeller);
        test("landslide_and_momentum", OrebornGameTests::landslide);
        test("cryo_seal", OrebornGameTests::cryoSeal);
        test("kiln_touch", OrebornGameTests::kilnTouch);
        test("glacial_irrigation", OrebornGameTests::glacialIrrigation);
        test("void_harvest", OrebornGameTests::voidHarvest);
        test("charged_soil", OrebornGameTests::chargedSoil);
        test("frostbite_shatter", OrebornGameTests::frostbiteShatter);
        test("chain_lightning", OrebornGameTests::chainLightning);
        test("combustion_chain", OrebornGameTests::combustionChain);
        test("stormcaller", OrebornGameTests::stormcaller);
        test("frost_nova", OrebornGameTests::frostNova);
        test("void_strike", OrebornGameTests::voidStrike);
        test("rift_burrow", OrebornGameTests::riftBurrow);
        test("meteor_slam", OrebornGameTests::meteorSlam);
        test("ore_radar", OrebornGameTests::oreRadar);
        test("armor_set_bonuses", OrebornGameTests::armorSetBonuses);
        test("frost_and_lava_walkers", OrebornGameTests::walkers, 400);
        test("lightning_staff_strikes", OrebornGameTests::lightningStaffStrikes);
        test("lightning_staff_needs_food", OrebornGameTests::lightningStaffNeedsFood);
        test("lightning_reflects_off_glass", OrebornGameTests::lightningReflectsOffGlass);
        test("lightning_ignites_wood", OrebornGameTests::lightningIgnitesWood);
        test("lightning_electrifies_water", OrebornGameTests::lightningElectrifiesWater);
        test("electrocution_shock", OrebornGameTests::electrocutionShock);
    }

    private OrebornGameTests() {}

    private static void test(String name, Consumer<GameTestHelper> body) {
        test(name, body, 60);
    }

    private static void test(String name, Consumer<GameTestHelper> body, int maxTicks) {
        TESTS.add(new Test(FUNCTIONS.register(name, () -> body), maxTicks));
    }

    public static void register(IEventBus modBus) {
        FUNCTIONS.register(modBus);
        modBus.addListener(OrebornGameTests::registerTests);
    }

    private static void registerTests(RegisterGameTestsEvent event) {
        Holder<TestEnvironmentDefinition<?>> environment = event.registerEnvironment(Oreborn.id("default"));
        Identifier arena = Oreborn.id("test_arena");
        for (Test test : TESTS) {
            event.registerTest(test.function().getId(), new FunctionGameTestInstance(test.function().getKey(),
                    new TestData<>(environment, arena, test.maxTicks(), 1, true)));
        }
    }

    // ---- helpers -------------------------------------------------------------------------------------------------

    /** A survival fake player standing at the relative position, looking by yaw/pitch (yaw 0 = south, 180 = north). */
    private static FakePlayer player(GameTestHelper helper, double x, double y, double z, float yaw, float pitch) {
        FakePlayer player = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), "oreborn_test"));
        Vec3 pos = helper.absoluteVec(new Vec3(x, y, z));
        player.snapTo(pos.x, pos.y, pos.z, yaw, pitch);
        player.setYHeadRot(yaw);
        player.getInventory().clearContent();
        return player;
    }

    private static ItemStack gear(OreMaterial material, GearType type) {
        return new ItemStack(ModItems.gear(material, type));
    }

    private static ItemStack hold(Player player, ItemStack stack) {
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        return stack;
    }

    private static void wearSet(Player player, OreMaterial material) {
        for (GearType piece : GearType.ARMOR) {
            player.setItemSlot(piece.armorType().getSlot(), gear(material, piece));
        }
    }

    private static int count(Player player, Item item) {
        int total = 0;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack.is(item)) {
                total += stack.getCount();
            }
        }
        return total;
    }

    private static int droppedCount(GameTestHelper helper, Item item) {
        return helper.getEntities(EntityTypes.ITEM).stream().map(ItemEntity::getItem).filter(s -> s.is(item)).mapToInt(ItemStack::getCount).sum();
    }

    private static void fill(GameTestHelper helper, BlockPos from, BlockPos to, Block block) {
        for (BlockPos pos : BlockPos.betweenClosed(from, to)) {
            helper.setBlock(pos.immutable(), block);
        }
    }

    private static void mine(GameTestHelper helper, FakePlayer player, BlockPos relative) {
        player.gameMode.destroyBlock(helper.absolutePos(relative));
    }

    private static UseOnContext clickOn(GameTestHelper helper, Player player, BlockPos relative, Direction face) {
        BlockPos pos = helper.absolutePos(relative);
        return new UseOnContext(player, InteractionHand.MAIN_HAND, new BlockHitResult(Vec3.atCenterOf(pos), face, pos, false));
    }

    private static OrebornToolItem tool(ItemStack stack) {
        return (OrebornToolItem) stack.getItem();
    }

    private static Zombie zombie(GameTestHelper helper, int x, int z) {
        return helper.spawnWithNoFreeWill(EntityTypes.ZOMBIE, new BlockPos(x, 1, z));
    }

    // ---- ores, recipes, worldgen ---------------------------------------------------------------------------------

    /** Crystal ores drop 2-3 shards, ingot ores one raw chunk; mining levels are like diamond ore / ancient debris. */
    private static void oreDrops(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        FakePlayer player = player(helper, 2.5, 1, 4.5, 180, 0);
        BlockPos pos = helper.absolutePos(new BlockPos(2, 1, 2));
        ItemStack diamondPickaxe = new ItemStack(Items.DIAMOND_PICKAXE);
        ItemStack ironPickaxe = new ItemStack(Items.IRON_PICKAXE);
        for (OreMaterial m : OreMaterial.values()) {
            List<Block> ores = new ArrayList<>();
            ores.add(ModBlocks.ORES.get(m).get());
            if (ModBlocks.DEEPSLATE_ORES.containsKey(m)) {
                ores.add(ModBlocks.DEEPSLATE_ORES.get(m).get());
            }
            for (Block ore : ores) {
                BlockState state = ore.defaultBlockState();
                for (int i = 0; i < 10; i++) {
                    int drops = Block.getDrops(state, level, pos, null, player, diamondPickaxe).stream()
                            .filter(s -> s.is(ModItems.DROPS.get(m).get())).mapToInt(ItemStack::getCount).sum();
                    if (m.isCrystal()) {
                        helper.assertTrue(drops >= 2 && drops <= 3, ore + " should drop 2-3 shards, dropped " + drops);
                    } else {
                        helper.assertValueEqual(drops, 1, ore + " raw drops");
                    }
                }
                helper.assertTrue(diamondPickaxe.isCorrectToolForDrops(state), "a diamond pickaxe mines " + ore);
                helper.assertValueEqual(ironPickaxe.isCorrectToolForDrops(state), m.isCrystal(), "an iron pickaxe mines " + ore);
            }
        }
        helper.succeed();
    }

    /** Every ore is added to its dimension's biomes. */
    private static void oresGenerate(GameTestHelper helper) {
        var biomes = helper.getLevel().registryAccess().lookupOrThrow(Registries.BIOME);
        for (OreMaterial m : OreMaterial.values()) {
            ResourceKey<Biome> biome = m == OreMaterial.EMBERITE ? Biomes.NETHER_WASTES : Biomes.PLAINS;
            for (String suffix : new String[] { "", "_medium", "_buried" }) {
                ResourceKey<PlacedFeature> feature = ResourceKey.create(Registries.PLACED_FEATURE, Oreborn.id("ore_" + m.id() + suffix));
                boolean present = biomes.getOrThrow(biome).value().getGenerationSettings().features().stream()
                        .flatMap(HolderSet::stream).anyMatch(f -> f.is(feature));
                helper.assertTrue(present, feature.identifier() + " should generate in " + biome.identifier());
            }
        }
        helper.succeed();
    }

    private static void recipes(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        var recipes = level.recipeAccess();
        // 4 shards -> crystal
        ItemStack shard = new ItemStack(ModItems.DROPS.get(OreMaterial.CRYOLITE).get());
        CraftingInput shards = CraftingInput.of(2, 2, List.of(shard, shard.copy(), shard.copy(), shard.copy()));
        ItemStack crystal = recipes.getRecipeFor(RecipeType.CRAFTING, shards, level).orElseThrow().value().assemble(shards);
        helper.assertTrue(crystal.is(ModItems.MAIN.get(OreMaterial.CRYOLITE).get()), "4 shards should craft a crystal, got " + crystal);
        // crystal pickaxe
        ItemStack c = crystal.copy();
        ItemStack stick = new ItemStack(Items.STICK);
        CraftingInput pickaxeGrid = CraftingInput.of(3, 3, List.of(c, c.copy(), c.copy(), ItemStack.EMPTY, stick, ItemStack.EMPTY, ItemStack.EMPTY, stick.copy(), ItemStack.EMPTY));
        ItemStack pickaxe = recipes.getRecipeFor(RecipeType.CRAFTING, pickaxeGrid, level).orElseThrow().value().assemble(pickaxeGrid);
        helper.assertTrue(pickaxe.is(ModItems.gear(OreMaterial.CRYOLITE, GearType.PICKAXE)), "crystal pickaxe recipe, got " + pickaxe);
        // raw -> scrap
        SingleRecipeInput raw = new SingleRecipeInput(new ItemStack(ModItems.DROPS.get(OreMaterial.EMBERITE).get()));
        ItemStack scrap = recipes.getRecipeFor(RecipeType.SMELTING, raw, level).orElseThrow().value().assemble(raw);
        helper.assertTrue(scrap.is(ModItems.SCRAP.get(OreMaterial.EMBERITE).get()), "raw emberite should smelt into scrap, got " + scrap);
        // 4 scrap + 4 ender pearls -> umbrium ingot
        ItemStack s = new ItemStack(ModItems.SCRAP.get(OreMaterial.UMBRIUM).get());
        ItemStack pearl = new ItemStack(Items.ENDER_PEARL);
        CraftingInput alloy = CraftingInput.of(3, 3, List.of(s, s.copy(), s.copy(), s.copy(), pearl, pearl.copy(), pearl.copy(), pearl.copy(), ItemStack.EMPTY));
        ItemStack ingot = recipes.getRecipeFor(RecipeType.CRAFTING, alloy, level).orElseThrow().value().assemble(alloy);
        helper.assertTrue(ingot.is(ModItems.MAIN.get(OreMaterial.UMBRIUM).get()), "scrap + pearls should make an ingot, got " + ingot);
        // diamond sword + emberite ingot -> emberite sword, no template needed
        SmithingRecipeInput upgrade = new SmithingRecipeInput(ItemStack.EMPTY, new ItemStack(Items.DIAMOND_SWORD), new ItemStack(ModItems.MAIN.get(OreMaterial.EMBERITE).get()));
        ItemStack sword = recipes.getRecipeFor(RecipeType.SMITHING, upgrade, level).orElseThrow().value().assemble(upgrade);
        helper.assertTrue(sword.is(ModItems.gear(OreMaterial.EMBERITE, GearType.SWORD)), "smithing upgrade, got " + sword);
        // Lightning Staff: a Block of Fulgurite on two lightning rods
        ItemStack block = new ItemStack(ModItems.STORAGE.get(OreMaterial.FULGURITE).get());
        ItemStack rod = new ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM.getValue(Identifier.withDefaultNamespace("lightning_rod")));
        CraftingInput staffGrid = CraftingInput.of(3, 3, List.of(ItemStack.EMPTY, ItemStack.EMPTY, block, ItemStack.EMPTY, rod, ItemStack.EMPTY, rod.copy(), ItemStack.EMPTY, ItemStack.EMPTY));
        ItemStack staff = recipes.getRecipeFor(RecipeType.CRAFTING, staffGrid, level).orElseThrow().value().assemble(staffGrid);
        helper.assertTrue(staff.is(ModItems.LIGHTNING_STAFF.get()), "lightning staff recipe, got " + staff);
        helper.succeed();
    }

    // ---- mining patterns -----------------------------------------------------------------------------------------

    /** Umbrium pickaxe in X mode: the mined block and both diagonals (radius 2) go, drops land in the inventory. */
    private static void xCrossPattern(GameTestHelper helper) {
        fill(helper, new BlockPos(0, 1, 4), new BlockPos(10, 7, 4), Blocks.STONE);
        FakePlayer player = player(helper, 5.5, 3.0, 6.5, 180, 0); // eyes at y 4.62, looking at (5, 4, 4)
        ItemStack pickaxe = hold(player, gear(OreMaterial.UMBRIUM, GearType.PICKAXE));
        MiningMode.CROSS.applyTo(pickaxe);
        mine(helper, player, new BlockPos(5, 4, 4));
        int[][] cross = { { 0, 0 }, { 1, 1 }, { -1, 1 }, { 1, -1 }, { -1, -1 }, { 2, 2 }, { -2, 2 }, { 2, -2 }, { -2, -2 } };
        for (int[] d : cross) {
            helper.assertBlockPresent(Blocks.AIR, new BlockPos(5 + d[0], 4 + d[1], 4));
        }
        helper.assertBlockPresent(Blocks.STONE, new BlockPos(6, 4, 4));
        helper.assertBlockPresent(Blocks.STONE, new BlockPos(5, 5, 4));
        helper.assertValueEqual(count(player, Items.COBBLESTONE), 9, "cobblestone pocketed by Void Pocket");
        helper.assertValueEqual(droppedCount(helper, Items.COBBLESTONE), 0, "cobblestone left on the ground");
        helper.assertValueEqual(pickaxe.getDamageValue(), 9, "durability used");
        helper.succeed();
    }

    /** Tunnel mode digs a 1x2 tunnel 8 blocks deep at the player's height. */
    private static void stripMineTunnel(GameTestHelper helper) {
        fill(helper, new BlockPos(2, 1, 2), new BlockPos(8, 5, 12), Blocks.STONE);
        FakePlayer player = player(helper, 5.5, 1.0, 13.5, 180, 0); // looking at (5, 2, 12)
        ItemStack pickaxe = hold(player, gear(OreMaterial.UMBRIUM, GearType.PICKAXE));
        MiningMode.TUNNEL.applyTo(pickaxe);
        mine(helper, player, new BlockPos(5, 2, 12));
        for (int z = 5; z <= 12; z++) {
            helper.assertBlockPresent(Blocks.AIR, new BlockPos(5, 1, z));
            helper.assertBlockPresent(Blocks.AIR, new BlockPos(5, 2, z));
        }
        helper.assertBlockPresent(Blocks.STONE, new BlockPos(5, 1, 4));
        helper.assertBlockPresent(Blocks.STONE, new BlockPos(5, 3, 12));
        helper.assertBlockPresent(Blocks.STONE, new BlockPos(4, 1, 12));
        helper.assertValueEqual(count(player, Items.COBBLESTONE), 16, "cobblestone from the tunnel");
        helper.succeed();
    }

    private static void miningModeSwitch(GameTestHelper helper) {
        FakePlayer player = player(helper, 5.5, 1, 5.5, 0, 0);
        ItemStack pickaxe = hold(player, gear(OreMaterial.UMBRIUM, GearType.PICKAXE));
        player.setShiftKeyDown(true);
        pickaxe.getItem().use(helper.getLevel(), player, InteractionHand.MAIN_HAND);
        helper.assertValueEqual(MiningMode.of(pickaxe), MiningMode.CROSS, "mode after one switch");
        pickaxe.getItem().use(helper.getLevel(), player, InteractionHand.MAIN_HAND);
        helper.assertValueEqual(MiningMode.of(pickaxe), MiningMode.TUNNEL, "mode after two switches");
        helper.succeed();
    }

    private static void frostHeave(GameTestHelper helper) {
        fill(helper, new BlockPos(3, 1, 4), new BlockPos(7, 5, 4), Blocks.DIRT);
        FakePlayer player = player(helper, 5.5, 1.0, 6.5, 180, 0); // looking at (5, 2, 4)
        hold(player, gear(OreMaterial.CRYOLITE, GearType.SHOVEL));
        mine(helper, player, new BlockPos(5, 2, 4));
        for (BlockPos p : BlockPos.betweenClosed(new BlockPos(4, 1, 4), new BlockPos(6, 3, 4))) {
            helper.assertBlockPresent(Blocks.AIR, p.immutable());
        }
        helper.assertBlockPresent(Blocks.DIRT, new BlockPos(5, 4, 4));
        helper.succeed();
    }

    /** Emberite pickaxe: the whole connected vein goes at once, and the raw iron comes out as ingots. */
    private static void moltenCoreVein(GameTestHelper helper) {
        BlockPos[] vein = { new BlockPos(4, 2, 4), new BlockPos(5, 2, 4), new BlockPos(5, 3, 4), new BlockPos(6, 4, 5), new BlockPos(6, 4, 6) };
        for (BlockPos p : vein) {
            helper.setBlock(p, Blocks.IRON_ORE);
        }
        helper.setBlock(new BlockPos(11, 2, 11), Blocks.IRON_ORE);
        FakePlayer player = player(helper, 4.5, 1.0, 6.5, 180, 0);
        hold(player, gear(OreMaterial.EMBERITE, GearType.PICKAXE));
        mine(helper, player, vein[0]);
        helper.runAfterDelay(1, () -> helper.succeedIf(() -> {
            for (BlockPos p : vein) {
                helper.assertBlockPresent(Blocks.AIR, p);
            }
            helper.assertBlockPresent(Blocks.IRON_ORE, new BlockPos(11, 2, 11));
            helper.assertValueEqual(droppedCount(helper, Items.IRON_INGOT), 5, "iron ingots from the vein");
            helper.assertValueEqual(droppedCount(helper, Items.RAW_IRON), 0, "unsmelted raw iron");
        }));
    }

    /** Umbrium axe fells a natural tree in one chop (logs into the inventory) but leaves builds alone. */
    private static void worldfeller(GameTestHelper helper) {
        for (int y = 1; y <= 6; y++) {
            helper.setBlock(new BlockPos(8, y, 8), Blocks.OAK_LOG);
        }
        BlockState leaves = Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, false);
        for (BlockPos p : BlockPos.betweenClosed(new BlockPos(7, 5, 7), new BlockPos(9, 7, 9))) {
            if (helper.getBlockState(p).isAir()) {
                helper.setBlock(p.immutable(), leaves);
            }
        }
        for (int y = 1; y <= 3; y++) {
            helper.setBlock(new BlockPos(2, y, 2), Blocks.OAK_LOG); // a "wall", no leaves
        }
        FakePlayer player = player(helper, 8.5, 1.0, 10.5, 180, 0);
        hold(player, gear(OreMaterial.UMBRIUM, GearType.AXE));
        mine(helper, player, new BlockPos(8, 1, 8));
        for (int y = 1; y <= 6; y++) {
            helper.assertBlockPresent(Blocks.AIR, new BlockPos(8, y, 8));
        }
        helper.assertValueEqual(count(player, Items.OAK_LOG), 6, "logs pocketed");
        mine(helper, player, new BlockPos(2, 1, 2));
        helper.assertBlockPresent(Blocks.OAK_LOG, new BlockPos(2, 2, 2));
        helper.succeed();
    }

    /** Fulgurite shovel: the sand column above comes down with the dug block; quick digging gives Haste. */
    private static void landslide(GameTestHelper helper) {
        fill(helper, new BlockPos(3, 1, 3), new BlockPos(3, 5, 3), Blocks.SAND);
        FakePlayer player = player(helper, 3.5, 1.0, 5.5, 180, 0);
        hold(player, gear(OreMaterial.FULGURITE, GearType.SHOVEL));
        mine(helper, player, new BlockPos(3, 1, 3));
        for (int y = 1; y <= 5; y++) {
            helper.assertBlockPresent(Blocks.AIR, new BlockPos(3, y, 3));
        }
        helper.assertTrue(player.hasEffect(MobEffects.HASTE), "Momentum gives Haste");
        helper.runAfterDelay(1, () -> helper.succeedIf(() -> helper.assertValueEqual(droppedCount(helper, Items.SAND), 5, "sand dropped")));
    }

    /** Cryolite pickaxe: lava next to the mined block becomes obsidian, still water becomes ice. */
    private static void cryoSeal(GameTestHelper helper) {
        helper.setBlock(new BlockPos(4, 2, 4), Blocks.STONE);
        helper.setBlock(new BlockPos(5, 2, 4), Blocks.LAVA);
        helper.setBlock(new BlockPos(3, 2, 4), Blocks.WATER);
        FakePlayer player = player(helper, 4.5, 1.0, 6.5, 180, 0);
        hold(player, gear(OreMaterial.CRYOLITE, GearType.PICKAXE));
        mine(helper, player, new BlockPos(4, 2, 4));
        helper.assertBlockPresent(Blocks.OBSIDIAN, new BlockPos(5, 2, 4));
        helper.assertBlockPresent(Blocks.ICE, new BlockPos(3, 2, 4));
        helper.succeed();
    }

    /** Emberite shovel: right-click smelts a 3x3 of sand into glass in place; dug sand drops as glass. */
    private static void kilnTouch(GameTestHelper helper) {
        fill(helper, new BlockPos(4, 2, 4), new BlockPos(6, 4, 4), Blocks.SAND);
        FakePlayer player = player(helper, 5.5, 2.0, 6.5, 180, 0);
        ItemStack shovel = hold(player, gear(OreMaterial.EMBERITE, GearType.SHOVEL));
        shovel.getItem().useOn(clickOn(helper, player, new BlockPos(5, 3, 4), Direction.SOUTH));
        for (BlockPos p : BlockPos.betweenClosed(new BlockPos(4, 2, 4), new BlockPos(6, 4, 4))) {
            helper.assertBlockPresent(Blocks.GLASS, p.immutable());
        }
        helper.setBlock(new BlockPos(9, 1, 9), Blocks.SAND);
        mine(helper, player, new BlockPos(9, 1, 9));
        helper.runAfterDelay(1, () -> helper.succeedIf(() -> helper.assertValueEqual(droppedCount(helper, Items.GLASS), 1, "Molten: sand drops glass")));
    }

    /** Cryolite hoe, sneaking: a hydrated 9x9 farm plot around a water source. */
    private static void glacialIrrigation(GameTestHelper helper) {
        fill(helper, new BlockPos(2, 1, 2), new BlockPos(10, 1, 10), Blocks.DIRT);
        FakePlayer player = player(helper, 6.5, 2.0, 12.5, 180, 30);
        ItemStack hoe = hold(player, gear(OreMaterial.CRYOLITE, GearType.HOE));
        player.setShiftKeyDown(true);
        hoe.getItem().useOn(clickOn(helper, player, new BlockPos(6, 1, 6), Direction.UP));
        helper.assertBlockPresent(Blocks.WATER, new BlockPos(6, 1, 6));
        int farmland = 0;
        for (BlockPos p : BlockPos.betweenClosed(new BlockPos(2, 1, 2), new BlockPos(10, 1, 10))) {
            BlockState state = helper.getBlockState(p.immutable());
            if (state.is(Blocks.FARMLAND)) {
                helper.assertValueEqual(state.getValue(FarmlandBlock.MOISTURE), 7, "moisture");
                farmland++;
            }
        }
        helper.assertValueEqual(farmland, 80, "farmland in the plot");
        helper.succeed();
    }

    /** Umbrium hoe: every ripe crop around is harvested into the inventory and replanted. */
    private static void voidHarvest(GameTestHelper helper) {
        CropBlock wheat = (CropBlock) Blocks.WHEAT;
        for (int x = 3; x <= 5; x++) {
            helper.setBlock(new BlockPos(x, 1, 3), Blocks.FARMLAND.defaultBlockState().setValue(FarmlandBlock.MOISTURE, 7));
            helper.setBlock(new BlockPos(x, 2, 3), wheat.getStateForAge(wheat.getMaxAge()));
        }
        FakePlayer player = player(helper, 4.5, 1.0, 5.5, 180, 30);
        ItemStack hoe = hold(player, gear(OreMaterial.UMBRIUM, GearType.HOE));
        hoe.getItem().useOn(clickOn(helper, player, new BlockPos(4, 2, 3), Direction.UP));
        for (int x = 3; x <= 5; x++) {
            helper.assertBlockProperty(new BlockPos(x, 2, 3), CropBlock.AGE, 0);
        }
        helper.assertTrue(count(player, Items.WHEAT) >= 3, "wheat in the inventory: " + count(player, Items.WHEAT));
        helper.succeed();
    }

    /** Fulgurite hoe: lightning random-ticks the crops around the player. */
    private static void chargedSoil(GameTestHelper helper) {
        for (BlockPos p : BlockPos.betweenClosed(new BlockPos(4, 1, 4), new BlockPos(6, 1, 6))) {
            helper.setBlock(p.immutable(), Blocks.FARMLAND.defaultBlockState().setValue(FarmlandBlock.MOISTURE, 7));
            helper.setBlock(p.above(), Blocks.WHEAT);
        }
        FakePlayer player = player(helper, 5.5, 2.0, 8.5, 180, 0);
        ItemStack hoe = hold(player, gear(OreMaterial.FULGURITE, GearType.HOE));
        hoe.getItem().use(helper.getLevel(), player, InteractionHand.MAIN_HAND);
        int ages = 0;
        for (BlockPos p : BlockPos.betweenClosed(new BlockPos(4, 2, 4), new BlockPos(6, 2, 6))) {
            ages += helper.getBlockState(p.immutable()).getValue(CropBlock.AGE);
        }
        helper.assertTrue(ages > 0, "crops should have grown");
        helper.assertTrue(player.getCooldowns().isOnCooldown(hoe), "cooldown started");
        helper.succeed();
    }

    // ---- combat --------------------------------------------------------------------------------------------------

    private static void frostbiteShatter(GameTestHelper helper) {
        Zombie first = zombie(helper, 4, 4);
        Zombie second = zombie(helper, 5, 6);
        FakePlayer player = player(helper, 4.5, 1, 2.5, 0, 0);
        ItemStack sword = hold(player, gear(OreMaterial.CRYOLITE, GearType.SWORD));
        OrebornToolItem item = tool(sword);
        item.hurtEnemy(sword, first, player);
        helper.assertTrue(first.hasEffect(ModEffects.FROZEN), "first hit freezes");
        helper.assertValueEqual(item.getAttackDamageBonus(first, 7.0F, player.damageSources().playerAttack(player)), Combat.SHATTER_BONUS, "shatter bonus");
        item.hurtEnemy(sword, first, player);
        helper.assertFalse(first.hasEffect(ModEffects.FROZEN), "the ice shattered");
        helper.assertTrue(second.hasEffect(ModEffects.FROZEN), "the shatter froze the neighbour");
        helper.succeed();
    }

    private static void chainLightning(GameTestHelper helper) {
        Zombie[] zombies = { zombie(helper, 3, 3), zombie(helper, 6, 3), zombie(helper, 9, 3), zombie(helper, 12, 3) };
        FakePlayer player = player(helper, 3.5, 1, 1.5, 0, 0);
        ItemStack sword = hold(player, gear(OreMaterial.FULGURITE, GearType.SWORD));
        tool(sword).hurtEnemy(sword, zombies[0], player);
        for (int i = 1; i < zombies.length; i++) {
            helper.assertTrue(zombies[i].getHealth() < zombies[i].getMaxHealth(), "zombie " + i + " should be struck by the chain");
        }
        helper.succeed();
    }

    /** Emberite sword: a burning, marked enemy explodes when it dies and sets its neighbours alight. */
    private static void combustionChain(GameTestHelper helper) {
        Zombie victim = zombie(helper, 5, 5);
        Zombie near = zombie(helper, 7, 5);
        Zombie other = zombie(helper, 5, 7);
        FakePlayer player = player(helper, 5.5, 1, 2.5, 0, 0);
        ItemStack sword = hold(player, gear(OreMaterial.EMBERITE, GearType.SWORD));
        tool(sword).hurtEnemy(sword, victim, player);
        helper.assertTrue(victim.isOnFire(), "the hit sets the victim on fire");
        victim.hurtServer(helper.getLevel(), player.damageSources().playerAttack(player), 1000.0F);
        helper.assertTrue(near.getHealth() < near.getMaxHealth() && other.getHealth() < other.getMaxHealth(), "the combustion hurts both neighbours");
        helper.assertTrue(near.isOnFire() && Combat.isMarkedForCombustion(near), "neighbours burn and can explode in turn");
        helper.succeed();
    }

    private static void stormcaller(GameTestHelper helper) {
        Zombie target = zombie(helper, 5, 10);
        FakePlayer player = player(helper, 5.5, 1, 3.5, 0, 0); // looking south at the zombie
        ItemStack axe = hold(player, gear(OreMaterial.FULGURITE, GearType.AXE));
        axe.getItem().use(helper.getLevel(), player, InteractionHand.MAIN_HAND);
        helper.assertTrue(target.getHealth() < target.getMaxHealth(), "the lightning hits the zombie");
        helper.assertFalse(helper.getEntities(EntityTypes.LIGHTNING_BOLT).isEmpty(), "a lightning bolt appears");
        helper.assertTrue(player.getCooldowns().isOnCooldown(axe), "cooldown started");
        helper.succeed();
    }

    private static void frostNova(GameTestHelper helper) {
        Zombie a = zombie(helper, 3, 5);
        Zombie b = zombie(helper, 8, 5);
        FakePlayer player = player(helper, 5.5, 1, 5.5, 0, 0);
        ItemStack axe = hold(player, gear(OreMaterial.CRYOLITE, GearType.AXE));
        axe.getItem().use(helper.getLevel(), player, InteractionHand.MAIN_HAND);
        helper.assertTrue(a.hasEffect(ModEffects.FROZEN) && b.hasEffect(ModEffects.FROZEN), "both zombies frozen");
        helper.succeed();
    }

    /** Umbrium sword: lands right behind the zombie looked at, and the next hit deals double damage. */
    private static void voidStrike(GameTestHelper helper) {
        Zombie target = zombie(helper, 5, 11);
        FakePlayer player = player(helper, 5.5, 1, 3.5, 0, 0);
        ItemStack sword = hold(player, gear(OreMaterial.UMBRIUM, GearType.SWORD));
        ToolActions.Blink blink = ToolActions.voidStrikeDestination(helper.getLevel(), player);
        helper.assertTrue(blink != null && blink.target() == target, "Void Strike should target the zombie");
        helper.assertTrue(blink.spot().distanceTo(target.position()) < 2.0, "lands next to the zombie, at " + blink.spot());
        sword.getItem().use(helper.getLevel(), player, InteractionHand.MAIN_HAND);
        helper.assertValueEqual(tool(sword).getAttackDamageBonus(target, 8.0F, player.damageSources().playerAttack(player)), 8.0F, "double damage bonus");
        helper.succeed();
    }

    /** Umbrium shovel: through a 3 block thick wall to the open space behind it; nothing to burrow = no teleport. */
    private static void riftBurrow(GameTestHelper helper) {
        fill(helper, new BlockPos(3, 1, 5), new BlockPos(7, 3, 7), Blocks.STONE);
        FakePlayer player = player(helper, 5.5, 1, 4.5, 0, 0);
        hold(player, gear(OreMaterial.UMBRIUM, GearType.SHOVEL));
        Vec3 spot = ToolActions.riftDestination(helper.getLevel(), player);
        helper.assertTrue(spot != null && spot.distanceTo(helper.absoluteVec(new Vec3(5.5, 1, 8.5))) < 0.01, "should come out behind the wall, got " + spot);
        FakePlayer open = player(helper, 12.5, 1, 4.5, 0, 0);
        helper.assertTrue(ToolActions.riftDestination(helper.getLevel(), open) == null, "no wall in front: no rift");
        helper.succeed();
    }

    /** Emberite axe: the plunge's landing damages and ignites the monsters around. */
    private static void meteorSlam(GameTestHelper helper) {
        Zombie a = zombie(helper, 3, 5);
        Zombie b = zombie(helper, 7, 6);
        FakePlayer player = player(helper, 5.5, 1, 5.5, 0, 0);
        ItemStack axe = hold(player, gear(OreMaterial.EMBERITE, GearType.AXE));
        axe.getItem().use(helper.getLevel(), player, InteractionHand.MAIN_HAND);
        helper.assertTrue(ToolActions.onLanding(player, 8.0), "the landing triggers the slam");
        helper.assertTrue(a.getHealth() < a.getMaxHealth() && b.getHealth() < b.getMaxHealth(), "both zombies hit");
        helper.assertTrue(a.isOnFire(), "and set on fire");
        helper.assertFalse(ToolActions.onLanding(player, 8.0), "one slam per activation");
        helper.succeed();
    }

    // ---- ore radar and armour ------------------------------------------------------------------------------------

    private static void oreRadar(GameTestHelper helper) {
        helper.setBlock(new BlockPos(3, 2, 3), Blocks.DIAMOND_ORE);
        helper.setBlock(new BlockPos(9, 3, 9), ModBlocks.ORES.get(OreMaterial.CRYOLITE).get());
        FakePlayer player = player(helper, 6.5, 1, 6.5, 0, 0);
        hold(player, gear(OreMaterial.FULGURITE, GearType.PICKAXE));
        OreRadar.ping(helper.getLevel(), player);
        helper.assertTrue(helper.getEntities(EntityTypes.BLOCK_DISPLAY).size() >= 2, "both ores get a glowing outline");
        helper.assertValueEqual(OreRadar.colorFor(ModBlocks.ORES.get(OreMaterial.CRYOLITE).get().defaultBlockState()), OreMaterial.CRYOLITE.color(), "cryolite glow colour");
        helper.succeed();
    }

    private static void armorSetBonuses(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        // Umbrium: flight with the full set, taken away (with a safe landing) without it
        FakePlayer flyer = player(helper, 2.5, 1, 2.5, 0, 0);
        wearSet(flyer, OreMaterial.UMBRIUM);
        ArmorSets.umbralFlight(flyer, level.getGameTime());
        helper.assertTrue(flyer.getAttribute(NeoForgeMod.CREATIVE_FLIGHT).getValue() > 0.0, "full Umbrium set grants flight");
        flyer.setItemSlot(GearType.BOOTS.armorType().getSlot(), ItemStack.EMPTY);
        ArmorSets.umbralFlight(flyer, level.getGameTime());
        helper.assertTrue(flyer.getAttribute(NeoForgeMod.CREATIVE_FLIGHT).getValue() == 0.0, "flight ends with the set");
        helper.assertTrue(ArmorSets.hasFlightGrace(flyer), "safe landing after losing flight");

        // Emberite: Phoenix Rebirth once, then on cooldown
        FakePlayer phoenix = player(helper, 5.5, 1, 2.5, 0, 0);
        wearSet(phoenix, OreMaterial.EMBERITE);
        phoenix.setHealth(1.0F);
        helper.assertTrue(ArmorSets.tryPhoenix(phoenix), "Phoenix Rebirth triggers");
        helper.assertValueEqual(phoenix.getHealth(), phoenix.getMaxHealth() * 0.5F, "revived with half health");
        helper.assertFalse(ArmorSets.tryPhoenix(phoenix), "Phoenix Rebirth is on cooldown");

        // Cryolite: Ice Block below 30% health
        FakePlayer icy = player(helper, 8.5, 1, 2.5, 0, 0);
        wearSet(icy, OreMaterial.CRYOLITE);
        icy.setHealth(4.0F);
        ArmorSets.afterDamage(icy, icy.damageSources().generic());
        helper.assertTrue(icy.hasEffect(MobEffects.RESISTANCE) && icy.hasEffect(MobEffects.REGENERATION), "Ice Block protects");

        // Umbrium helmet: night vision while worn (shown as infinite), gone when taken off; potions are left alone
        FakePlayer seer = player(helper, 11.5, 1, 2.5, 0, 0);
        seer.setItemSlot(GearType.HELMET.armorType().getSlot(), gear(OreMaterial.UMBRIUM, GearType.HELMET));
        ArmorSets.tick(seer);
        helper.assertTrue(seer.hasEffect(MobEffects.NIGHT_VISION) && seer.getEffect(MobEffects.NIGHT_VISION).isInfiniteDuration(), "helmet grants night vision");
        seer.setItemSlot(GearType.HELMET.armorType().getSlot(), ItemStack.EMPTY);
        ArmorSets.tick(seer);
        helper.assertFalse(seer.hasEffect(MobEffects.NIGHT_VISION), "night vision ends with the helmet");
        seer.addEffect(new net.minecraft.world.effect.MobEffectInstance(MobEffects.NIGHT_VISION, 3600));
        ArmorSets.tick(seer);
        helper.assertTrue(seer.hasEffect(MobEffects.NIGHT_VISION), "a night vision potion is not removed");

        // attribute passives are on the items
        helper.assertTrue(hasAttribute(gear(OreMaterial.UMBRIUM, GearType.LEGGINGS), Attributes.STEP_HEIGHT), "Umbrium leggings: step height");
        helper.assertTrue(hasAttribute(gear(OreMaterial.FULGURITE, GearType.LEGGINGS), Attributes.MOVEMENT_SPEED), "Fulgurite leggings: speed");
        helper.assertTrue(hasAttribute(gear(OreMaterial.FULGURITE, GearType.CHESTPLATE), Attributes.MAX_ABSORPTION), "Fulgurite chestplate: shield room");
        helper.succeed();
    }

    private static boolean hasAttribute(ItemStack stack, Holder<net.minecraft.world.entity.ai.attributes.Attribute> attribute) {
        ItemAttributeModifiers modifiers = stack.get(DataComponents.ATTRIBUTE_MODIFIERS);
        return modifiers != null && modifiers.modifiers().stream().anyMatch(entry -> entry.attribute().is(attribute));
    }

    /**
     * Force lightning hits what is in front (not what is behind), arcs on to a monster beside the target, and every second
     * of channelling costs one food point (half a shank) and one durability.
     */
    private static void lightningStaffStrikes(GameTestHelper helper) {
        Zombie front = zombie(helper, 5, 10);
        Zombie beside = zombie(helper, 8, 10);   // ~30 degrees off the aim: outside the cone, but within arc range
        Zombie behind = zombie(helper, 5, 0);
        FakePlayer player = player(helper, 5.5, 1, 3.5, 0, 0); // looking south at the front zombie
        ItemStack staff = hold(player, new ItemStack(ModItems.LIGHTNING_STAFF.get()));
        List<net.minecraft.world.entity.LivingEntity> cone = ForceLightning.resolve(player).targets();
        helper.assertTrue(cone.contains(front), "the zombie in front is in the lightning cone");
        helper.assertFalse(cone.contains(behind) || cone.contains(beside), "the others are not in the cone");
        player.getFoodData().setFoodLevel(20);
        ForceLightning.tick(helper.getLevel(), player, staff, 0);
        helper.assertTrue(front.getHealth() <= front.getMaxHealth() - ForceLightning.PRIMARY_DAMAGE + 1.0F, "the target is fried");
        helper.assertTrue(beside.getHealth() < beside.getMaxHealth(), "the lightning arcs on to the zombie beside it");
        helper.assertValueEqual(behind.getHealth(), behind.getMaxHealth(), "nothing behind the caster is hit");
        float afterFirst = front.getHealth();
        ForceLightning.tick(helper.getLevel(), player, staff, ForceLightning.HIT_INTERVAL);
        helper.assertTrue(front.getHealth() < afterFirst, "hits land every few ticks (no hurt cooldown)");
        ForceLightning.tick(helper.getLevel(), player, staff, 20);
        helper.assertValueEqual(player.getFoodData().getFoodLevel(), 20 - ForceLightning.FOOD_PER_SECOND, "food after one second");
        helper.assertValueEqual(staff.getDamageValue(), ForceLightning.DURABILITY_PER_SECOND, "durability after one second");
        helper.succeed();
    }

    /** Aimed straight at a glass wall, the lightning bounces back and fries the zombie behind the caster. */
    private static void lightningReflectsOffGlass(GameTestHelper helper) {
        fill(helper, new BlockPos(1, 1, 9), new BlockPos(10, 5, 9), Blocks.GLASS);
        Zombie behind = zombie(helper, 5, 1);
        FakePlayer player = player(helper, 5.5, 1, 4.5, 0, 0); // looking south at the glass
        ItemStack staff = hold(player, new ItemStack(ModItems.LIGHTNING_STAFF.get()));
        ForceLightning.Discharge discharge = ForceLightning.resolve(player);
        helper.assertValueEqual(discharge.bounces(), 1, "bounces");
        helper.assertTrue(discharge.targets().contains(behind), "the reflection points at the zombie behind the caster");
        ForceLightning.tick(helper.getLevel(), player, staff, 0);
        helper.assertTrue(behind.getHealth() < behind.getMaxHealth(), "the reflected lightning hits it");
        helper.succeed();
    }

    /** Held on a log for 1.5 seconds, the lightning sets it on fire. */
    private static void lightningIgnitesWood(GameTestHelper helper) {
        helper.setBlock(new BlockPos(5, 2, 9), Blocks.OAK_LOG);
        FakePlayer player = player(helper, 5.5, 1, 4.5, 0, 0); // eyes at y 2.62, looking at the log
        ItemStack staff = hold(player, new ItemStack(ModItems.LIGHTNING_STAFF.get()));
        player.getFoodData().setFoodLevel(20);
        for (int tick = 0; tick < ForceLightning.IGNITE_TICKS - 1; tick++) {
            ForceLightning.tick(helper.getLevel(), player, staff, tick);
        }
        helper.assertBlockPresent(Blocks.AIR, new BlockPos(5, 2, 8));
        ForceLightning.tick(helper.getLevel(), player, staff, ForceLightning.IGNITE_TICKS - 1);
        helper.assertBlockPresent(Blocks.FIRE, new BlockPos(5, 2, 8));
        helper.succeed();
    }

    /** Lightning into a pond: the fish in it die and drop cooked; a zombie on the shore is left alone. */
    private static void lightningElectrifiesWater(GameTestHelper helper) {
        fill(helper, new BlockPos(2, 1, 2), new BlockPos(9, 1, 9), Blocks.WATER);
        helper.spawnWithNoFreeWill(EntityTypes.COD, new BlockPos(5, 1, 6));
        helper.spawnWithNoFreeWill(EntityTypes.SALMON, new BlockPos(7, 1, 7));
        Zombie shore = zombie(helper, 12, 12);
        FakePlayer player = player(helper, 5.5, 1, 0.5, 0, 45); // looking down at the pond
        ItemStack staff = hold(player, new ItemStack(ModItems.LIGHTNING_STAFF.get()));
        ForceLightning.Discharge discharge = ForceLightning.resolve(player);
        helper.assertTrue(discharge.water() != null && discharge.targets().isEmpty(), "the lightning goes into the water");
        helper.assertTrue(ForceLightning.electrifiedWater(helper.getLevel(), discharge.water()).size() >= 40, "a big area of water is electrified");
        ForceLightning.tick(helper.getLevel(), player, staff, 0);
        helper.runAfterDelay(1, () -> helper.succeedIf(() -> {
            helper.assertTrue(helper.getEntities(EntityTypes.COD).isEmpty() && helper.getEntities(EntityTypes.SALMON).isEmpty(), "the fish are dead");
            helper.assertValueEqual(droppedCount(helper, Items.COOKED_COD), 1, "cooked cod");
            helper.assertValueEqual(droppedCount(helper, Items.COOKED_SALMON), 1, "cooked salmon");
            helper.assertValueEqual(droppedCount(helper, Items.COD) + droppedCount(helper, Items.SALMON), 0, "raw fish");
            helper.assertValueEqual(shore.getHealth(), shore.getMaxHealth(), "the zombie on dry land");
        }));
    }

    /** Electric hits (force lightning, chain lightning, real lightning bolts) make creatures convulse for a moment. */
    private static void electrocutionShock(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Zombie zapped = zombie(helper, 3, 3);
        Zombie chained = zombie(helper, 6, 3);
        Zombie struck = zombie(helper, 9, 3);
        Zombie punched = zombie(helper, 12, 3);
        zapped.hurtServer(level, ModDamageTypes.source(level, ModDamageTypes.FORCE_LIGHTNING, null), 1.0F);
        chained.hurtServer(level, ModDamageTypes.source(level, ModDamageTypes.ELECTROCUTION, null), 1.0F);
        struck.hurtServer(level, level.damageSources().lightningBolt(), 1.0F);
        punched.hurtServer(level, level.damageSources().generic(), 1.0F);
        helper.assertTrue(Shock.isShocked(zapped) && Shock.isShocked(chained) && Shock.isShocked(struck), "electric hits shock");
        helper.assertFalse(Shock.isShocked(punched), "other damage doesn't");
        helper.runAfterDelay(Shock.TICKS + 1, () -> helper.succeedIf(() -> helper.assertFalse(Shock.isShocked(zapped), "the shock wears off")));
    }

    /** No food, no lightning (creative players channel for free). */
    private static void lightningStaffNeedsFood(GameTestHelper helper) {
        FakePlayer player = player(helper, 5.5, 1, 3.5, 0, 0);
        ItemStack staff = hold(player, new ItemStack(ModItems.LIGHTNING_STAFF.get()));
        player.getFoodData().setFoodLevel(0);
        helper.assertTrue(staff.getItem().use(helper.getLevel(), player, InteractionHand.MAIN_HAND) == net.minecraft.world.InteractionResult.FAIL,
                "a starving player can't channel");
        player.getAbilities().instabuild = true;
        helper.assertTrue(staff.getItem().use(helper.getLevel(), player, InteractionHand.MAIN_HAND) == net.minecraft.world.InteractionResult.CONSUME,
                "creative players channel anyway");
        ForceLightning.tick(helper.getLevel(), player, staff, 20);
        helper.assertValueEqual(player.getFoodData().getFoodLevel(), 0, "creative channelling costs no food");
        helper.succeed();
    }

    /** Cryolite boots freeze water, Emberite boots crust lava over; the crust melts back once they're gone. */
    private static void walkers(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        fill(helper, new BlockPos(1, 1, 1), new BlockPos(6, 1, 6), Blocks.WATER);
        FakePlayer frost = player(helper, 3.5, 2, 3.5, 0, 0);
        frost.setItemSlot(GearType.BOOTS.armorType().getSlot(), gear(OreMaterial.CRYOLITE, GearType.BOOTS));
        frost.setOnGround(true);
        ArmorSets.frostWalk(level, frost);
        helper.assertBlockPresent(Blocks.FROSTED_ICE, new BlockPos(3, 1, 3));
        helper.assertBlockPresent(Blocks.FROSTED_ICE, new BlockPos(5, 1, 3));

        fill(helper, new BlockPos(9, 1, 9), new BlockPos(14, 1, 14), Blocks.LAVA);
        FakePlayer ember = player(helper, 11.5, 2, 11.5, 0, 0);
        ember.setItemSlot(GearType.BOOTS.armorType().getSlot(), gear(OreMaterial.EMBERITE, GearType.BOOTS));
        ember.setOnGround(true);
        ArmorSets.lavaWalk(level, ember);
        helper.assertBlockPresent(ModBlocks.CRUSTED_LAVA.get(), new BlockPos(11, 1, 11));
        helper.assertBlockPresent(ModBlocks.CRUSTED_LAVA.get(), new BlockPos(12, 1, 11));
        // the fake player isn't really in the world, so nobody keeps the crust solid: it melts back into lava
        helper.succeedWhen(() -> helper.assertBlockPresent(Blocks.LAVA, new BlockPos(11, 1, 11)));
    }
}
