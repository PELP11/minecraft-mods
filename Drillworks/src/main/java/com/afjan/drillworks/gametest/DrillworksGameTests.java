package com.afjan.drillworks.gametest;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import com.afjan.drillworks.Drillworks;
import com.afjan.drillworks.block.entity.RefineryBlockEntity;
import com.afjan.drillworks.drill.Boring;
import com.afjan.drillworks.drill.DrillModules;
import com.afjan.drillworks.drill.HeadMaterial;
import com.afjan.drillworks.drill.Module;
import com.afjan.drillworks.entity.MiningDrillEntity;
import com.afjan.drillworks.registry.ModBlocks;
import com.afjan.drillworks.registry.ModComponents;
import com.afjan.drillworks.registry.ModEntities;
import com.afjan.drillworks.registry.ModItems;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.FunctionGameTestInstance;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestData;
import net.minecraft.gametest.framework.TestEnvironmentDefinition;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * The quarry structure is 16 x 12 x 16 with four layers of stone (y 0-3); tests stand the drill on y = 4, facing
 * south (+z), and drive it by calling {@link MiningDrillEntity#drive} directly (fake players cannot ride).
 */
public final class DrillworksGameTests {
    private static final DeferredRegister<Consumer<GameTestHelper>> FUNCTIONS =
            DeferredRegister.create(Registries.TEST_FUNCTION, Drillworks.MODID);
    private static final List<Test> TESTS = new ArrayList<>();

    private record Test(DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> function, int maxTicks) {}

    static {
        test("drill_bores_a_tunnel", DrillworksGameTests::boresATunnel, 100);
        test("drill_needs_fuel", DrillworksGameTests::needsFuel, 100);
        test("stone_head_cannot_bore_obsidian", DrillworksGameTests::stoneHeadCannotBoreObsidian, 100);
        test("wide_bore_digs_5x5", DrillworksGameTests::wideBore, 100);
        test("drill_climbs_and_descends", DrillworksGameTests::climbsAndDescends, 100);
        test("modules_change_drops", DrillworksGameTests::modulesChangeDrops, 100);
        test("vein_seeker_follows_ores", DrillworksGameTests::veinSeeker, 100);
        test("canister_refuels_drill", DrillworksGameTests::canisterRefuels, 100);
        test("refinery_makes_gasoline", DrillworksGameTests::refineryMakesGasoline, 700);
        test("refinery_needs_columns", DrillworksGameTests::refineryNeedsColumns, 200);
    }

    private DrillworksGameTests() {}

    private static void test(String name, Consumer<GameTestHelper> body, int maxTicks) {
        TESTS.add(new Test(FUNCTIONS.register(name, () -> body), maxTicks));
    }

    public static void register(IEventBus modBus) {
        FUNCTIONS.register(modBus);
        modBus.addListener(DrillworksGameTests::registerTests);
    }

    private static void registerTests(RegisterGameTestsEvent event) {
        Holder<TestEnvironmentDefinition<?>> environment = event.registerEnvironment(Drillworks.id("default"));
        Identifier quarry = Drillworks.id("quarry");
        for (Test test : TESTS) {
            event.registerTest(test.function().getId(), new FunctionGameTestInstance(test.function().getKey(),
                    new TestData<>(environment, quarry, test.maxTicks(), 1, true)));
        }
    }

    // --- helpers ----------------------------------------------------------------------------------------------------

    private static MiningDrillEntity drill(GameTestHelper helper, int x, int z, ItemStack head, int fuel) {
        ServerLevel level = helper.getLevel();
        MiningDrillEntity drill = ModEntities.MINING_DRILL.get().create(level, EntitySpawnReason.TRIGGERED);
        Vec3 at = helper.absoluteVec(new Vec3(x + 0.5, 4.0, z + 0.5));
        drill.snapTo(at.x, at.y, at.z, 0.0F, 0.0F);
        drill.inventory.setItem(MiningDrillEntity.SLOT_HEAD, head);
        drill.setFuel(fuel);
        level.addFreshEntity(drill);
        return drill;
    }

    private static ItemStack head(HeadMaterial material, Module... modules) {
        ItemStack stack = new ItemStack(ModItems.HEADS.get(material).get());
        DrillModules fitted = DrillModules.EMPTY;
        for (int i = 0; i < modules.length; i++) {
            fitted = fitted.with(i, modules[i]);
        }
        if (modules.length > 0) {
            stack.set(ModComponents.MODULES.get(), fitted);
        }
        return stack;
    }

    private static void fill(GameTestHelper helper, int x0, int y0, int z0, int x1, int y1, int z1, Block block) {
        for (int x = x0; x <= x1; x++) {
            for (int y = y0; y <= y1; y++) {
                for (int z = z0; z <= z1; z++) {
                    helper.setBlock(new BlockPos(x, y, z), block);
                }
            }
        }
    }

    private static void driveForward(MiningDrillEntity drill, int ticks, int tilt) {
        for (int i = 0; i < ticks; i++) {
            drill.drive(true, false, false, false, 0.0F, tilt);
        }
    }

    private static boolean air(GameTestHelper helper, int x, int y, int z) {
        return helper.getBlockState(new BlockPos(x, y, z)).isAir();
    }

    // --- the drill ---------------------------------------------------------------------------------------------------

    private static void boresATunnel(GameTestHelper helper) {
        fill(helper, 3, 4, 6, 12, 8, 13, Blocks.STONE);
        MiningDrillEntity drill = drill(helper, 7, 3, head(HeadMaterial.IRON), 5000);
        double startZ = drill.getZ();
        driveForward(drill, 160, 0);

        for (int x = 6; x <= 8; x++) {
            for (int y = 4; y <= 6; y++) {
                helper.assertTrue(air(helper, x, y, 6) && air(helper, x, y, 8), "3x3 tunnel at x " + x + " y " + y);
            }
        }
        helper.assertFalse(air(helper, 5, 5, 7), "the tunnel is exactly 3 wide (x 5 stays)");
        helper.assertFalse(air(helper, 7, 7, 7), "the tunnel is exactly 3 high (y 7 stays)");
        helper.assertTrue(drill.getZ() - startZ > 3.0, "the drill drove into its tunnel (" + (drill.getZ() - startZ) + ")");
        helper.assertTrue(drill.count(Items.COBBLESTONE) >= 27, "cobblestone collected: " + drill.count(Items.COBBLESTONE));
        helper.assertTrue(drill.fuel() < 5000, "fuel was burnt");
        helper.assertTrue(drill.head().getDamageValue() > 0, "the head wore");
        drill.discard();
        helper.succeed();
    }

    private static void needsFuel(GameTestHelper helper) {
        fill(helper, 3, 4, 6, 12, 8, 8, Blocks.STONE);
        MiningDrillEntity drill = drill(helper, 7, 3, head(HeadMaterial.DIAMOND), 0);
        driveForward(drill, 80, 0);
        helper.assertFalse(air(helper, 7, 5, 6), "no fuel, no drilling");
        drill.discard();
        helper.succeed();
    }

    private static void stoneHeadCannotBoreObsidian(GameTestHelper helper) {
        fill(helper, 3, 4, 5, 12, 8, 8, Blocks.OBSIDIAN);
        MiningDrillEntity drill = drill(helper, 7, 3, head(HeadMaterial.STONE), 5000);
        helper.assertValueEqual(drill.drillStep(0), MiningDrillEntity.DrillStep.BLOCKED, "stone head vs obsidian");
        driveForward(drill, 40, 0);
        helper.assertFalse(air(helper, 7, 5, 5), "obsidian stays");
        drill.discard();
        helper.succeed();
    }

    private static void wideBore(GameTestHelper helper) {
        fill(helper, 1, 4, 6, 14, 10, 9, Blocks.STONE);
        MiningDrillEntity drill = drill(helper, 7, 3, head(HeadMaterial.NETHERITE, Module.WIDE_BORE, Module.EFFICIENCY), 5000);
        driveForward(drill, 60, 0);
        helper.assertTrue(air(helper, 5, 8, 6) && air(helper, 9, 4, 6), "5 wide and 5 high");
        helper.assertFalse(air(helper, 4, 5, 6) || air(helper, 7, 9, 6), "but not 6");
        drill.discard();
        helper.succeed();
    }

    private static void climbsAndDescends(GameTestHelper helper) {
        fill(helper, 0, 4, 5, 15, 11, 15, Blocks.STONE);
        MiningDrillEntity up = drill(helper, 4, 2, head(HeadMaterial.NETHERITE, Module.EFFICIENCY, Module.EFFICIENCY), 9000);
        MiningDrillEntity down = drill(helper, 11, 2, head(HeadMaterial.NETHERITE, Module.EFFICIENCY, Module.EFFICIENCY), 9000);
        double upStart = up.getY();
        double downStart = down.getY();
        driveForward(up, 300, 1);
        driveForward(down, 300, -1);
        helper.assertTrue(up.getY() >= upStart + 2.0, "climbed its own staircase (+" + (up.getY() - upStart) + ")");
        helper.assertTrue(down.getY() <= downStart - 2.0, "went down its own ramp (" + (down.getY() - downStart) + ")");
        up.discard();
        down.discard();
        helper.succeed();
    }

    private static void modulesChangeDrops(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(2, 4, 2));
        BlockState ore = Blocks.IRON_ORE.defaultBlockState();
        helper.setBlock(new BlockPos(2, 4, 2), ore);

        Boring.Setup plain = Boring.Setup.of(head(HeadMaterial.IRON));
        Boring.Setup smelt = Boring.Setup.of(head(HeadMaterial.IRON, Module.SMELTING));
        Boring.Setup silk = Boring.Setup.of(head(HeadMaterial.IRON, Module.SILK_TOUCH));
        Boring.Setup molten = Boring.Setup.of(head(HeadMaterial.NETHERITE));
        Boring.Setup voidFilter = Boring.Setup.of(head(HeadMaterial.IRON, Module.VOID_FILTER));
        Boring.Setup lucky = Boring.Setup.of(head(HeadMaterial.GOLDEN, Module.FORTUNE, Module.FORTUNE, Module.FORTUNE));

        helper.assertTrue(first(level, pos, ore, plain).is(Items.RAW_IRON), "plain: raw iron");
        helper.assertTrue(first(level, pos, ore, smelt).is(Items.IRON_INGOT), "smelting module: iron ingot");
        helper.assertTrue(first(level, pos, ore, molten).is(Items.IRON_INGOT), "netherite head smelts by itself");
        helper.assertTrue(first(level, pos, ore, silk).is(Items.IRON_ORE), "silk touch: the ore block");
        helper.assertValueEqual(lucky.fortune(), 4, "golden head (+1) with three Fortune modules");
        helper.assertTrue(Boring.tool(level, lucky).isEnchanted(), "the virtual pickaxe carries the fortune");

        BlockState stone = Blocks.STONE.defaultBlockState();
        helper.setBlock(new BlockPos(2, 4, 2), stone);
        helper.assertTrue(Boring.drops(level, pos, stone, voidFilter, Boring.tool(level, voidFilter), null).isEmpty(),
                "void filter destroys cobblestone");
        helper.assertTrue(first(level, pos, stone, smelt).is(Items.COBBLESTONE), "smelting leaves cobblestone alone");

        helper.assertTrue(Boring.Setup.of(head(HeadMaterial.STONE, Module.WIDE_BORE)).wideBore(),
                "the first socket counts on a one-socket head");
        helper.assertFalse(Boring.Setup.of(head(HeadMaterial.STONE, Module.EFFICIENCY, Module.WIDE_BORE)).wideBore(),
                "sockets past the head's count do nothing");
        helper.succeed();
    }

    private static ItemStack first(ServerLevel level, BlockPos pos, BlockState state, Boring.Setup setup) {
        List<ItemStack> drops = Boring.drops(level, pos, state, setup, Boring.tool(level, setup), null);
        return drops.isEmpty() ? ItemStack.EMPTY : drops.get(0);
    }

    private static void veinSeeker(GameTestHelper helper) {
        fill(helper, 3, 4, 6, 12, 8, 9, Blocks.STONE);
        // a coal vein running sideways out of the bore
        for (int x = 9; x <= 11; x++) {
            helper.setBlock(new BlockPos(x, 5, 6), Blocks.COAL_ORE);
        }
        MiningDrillEntity drill = drill(helper, 7, 3, head(HeadMaterial.IRON, Module.VEIN_SEEKER), 5000);
        driveForward(drill, 40, 0);
        helper.assertTrue(air(helper, 11, 5, 6), "the vein was followed to its end");
        helper.assertTrue(drill.count(Items.COAL) >= 3, "and its coal collected");
        drill.discard();
        helper.succeed();
    }

    private static void canisterRefuels(GameTestHelper helper) {
        MiningDrillEntity drill = drill(helper, 7, 3, ItemStack.EMPTY, 0);
        drill.inventory.setItem(MiningDrillEntity.SLOT_FUEL, new ItemStack(ModItems.GASOLINE_CANISTER.get(), 2));
        helper.runAfterDelay(5, () -> {
            helper.assertValueEqual(drill.fuel(), 2 * MiningDrillEntity.CANISTER, "fuel after two canisters");
            helper.assertTrue(drill.count(ModItems.EMPTY_CANISTER.get()) + drill.inventory.getItem(MiningDrillEntity.SLOT_FUEL)
                    .getCount() == 2 && drill.inventory.getItem(MiningDrillEntity.SLOT_FUEL).is(ModItems.EMPTY_CANISTER.get()),
                    "both canisters come back empty");
            drill.discard();
            helper.succeed();
        });
    }

    // --- the refinery -------------------------------------------------------------------------------------------------

    private static RefineryBlockEntity refinery(GameTestHelper helper, boolean columns) {
        BlockPos pos = new BlockPos(3, 4, 3);
        helper.setBlock(pos, ModBlocks.REFINERY.get());
        if (columns) {
            helper.setBlock(pos.above(), ModBlocks.DISTILLATION_COLUMN.get());
            helper.setBlock(pos.above(2), ModBlocks.DISTILLATION_COLUMN.get());
        }
        RefineryBlockEntity refinery = (RefineryBlockEntity) helper.getLevel().getBlockEntity(helper.absolutePos(pos));
        refinery.setItem(RefineryBlockEntity.SLOT_CRUDE, new ItemStack(ModItems.CRUDE_OIL.get(), 8));
        refinery.setItem(RefineryBlockEntity.SLOT_FUEL, new ItemStack(Items.COAL, 4));
        refinery.setItem(RefineryBlockEntity.SLOT_CAN_IN, new ItemStack(ModItems.EMPTY_CANISTER.get(), 2));
        return refinery;
    }

    private static void refineryMakesGasoline(GameTestHelper helper) {
        RefineryBlockEntity refinery = refinery(helper, true);
        helper.succeedWhen(() -> helper.assertTrue(
                refinery.getItem(RefineryBlockEntity.SLOT_CAN_OUT).is(ModItems.GASOLINE_CANISTER.get()),
                "a canister was filled with gasoline"));
    }

    private static void refineryNeedsColumns(GameTestHelper helper) {
        RefineryBlockEntity refinery = refinery(helper, false);
        helper.runAfterDelay(150, () -> {
            helper.assertValueEqual(refinery.gasoline(), 0, "gasoline without the columns");
            helper.assertValueEqual(refinery.getItem(RefineryBlockEntity.SLOT_CRUDE).getCount(), 8, "crude oil left");
            helper.succeed();
        });
    }
}
