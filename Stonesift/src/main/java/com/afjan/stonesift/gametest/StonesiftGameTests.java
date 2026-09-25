package com.afjan.stonesift.gametest;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

import com.afjan.stonesift.Stonesift;
import com.afjan.stonesift.block.HandSieveBlockEntity;
import com.afjan.stonesift.block.SluiceBlock;
import com.afjan.stonesift.item.RockItem;
import com.afjan.stonesift.machine.CoalGeneratorBlockEntity;
import com.afjan.stonesift.machine.DeepDrillBlockEntity;
import com.afjan.stonesift.machine.FlotationCellBlockEntity;
import com.afjan.stonesift.machine.GrinderBlockEntity;
import com.afjan.stonesift.machine.MachineBlockEntity;
import com.afjan.stonesift.machine.RockFormerBlockEntity;
import com.afjan.stonesift.machine.ShakerSieveBlockEntity;
import com.afjan.stonesift.registry.ModBlocks;
import com.afjan.stonesift.registry.ModItems;
import com.afjan.stonesift.rock.Resource;
import com.afjan.stonesift.rock.Rock;
import com.afjan.stonesift.rock.Veins;
import com.afjan.stonesift.rock.Yields;
import com.mojang.authlib.GameProfile;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.FunctionGameTestInstance;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestData;
import net.minecraft.gametest.framework.TestEnvironmentDefinition;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** GameTests in a 12 x 8 x 12 yard with a stone floor at y 0. */
public final class StonesiftGameTests {
    private static final DeferredRegister<Consumer<GameTestHelper>> FUNCTIONS =
            DeferredRegister.create(Registries.TEST_FUNCTION, Stonesift.MODID);
    private static final List<Test> TESTS = new ArrayList<>();

    private record Test(DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> function, int maxTicks) {}

    static {
        test("sieve_yields_follow_profile_and_mesh", StonesiftGameTests::sieveYields, 20);
        test("balance_64_cobblestone", StonesiftGameTests::balance, 20);
        test("rich_rock_triples", StonesiftGameTests::richTriples, 20);
        test("veins_are_deterministic", StonesiftGameTests::veins, 20);
        test("stone_hammer_drops_gravel", StonesiftGameTests::stoneHammer, 40);
        test("hand_sieve_sifts", StonesiftGameTests::handSieve, 40);
        test("grinder_makes_gravel", StonesiftGameTests::grinder, 300);
        test("shaker_sieve_needs_rising_edges", StonesiftGameTests::shakerSieve, 60);
        test("rock_former_forms_rock", StonesiftGameTests::rockFormer, 120);
        test("rock_former_deepslate_drinks_lava", StonesiftGameTests::rockFormerLava, 500);
        test("sluice_washes_flour", StonesiftGameTests::sluice, 300);
        test("generator_powers_flotation", StonesiftGameTests::flotation, 400);
        test("deep_drill_bores_then_pumps", StonesiftGameTests::deepDrill, 900);
    }

    private StonesiftGameTests() {}

    private static void test(String name, Consumer<GameTestHelper> body, int maxTicks) {
        TESTS.add(new Test(FUNCTIONS.register(name, () -> body), maxTicks));
    }

    public static void register(IEventBus modBus) {
        FUNCTIONS.register(modBus);
        modBus.addListener(StonesiftGameTests::registerTests);
    }

    private static void registerTests(RegisterGameTestsEvent event) {
        Holder<TestEnvironmentDefinition<?>> environment = event.registerEnvironment(Stonesift.id("default"));
        for (Test test : TESTS) {
            event.registerTest(test.function().getId(), new FunctionGameTestInstance(test.function().getKey(),
                    new TestData<>(environment, Stonesift.id("yard"), test.maxTicks(), 1, true)));
        }
    }

    // --- helpers -------------------------------------------------------------------------------------------------------

    private static int count(List<ItemStack> stacks, Item item) {
        return stacks.stream().filter(s -> s.is(item)).mapToInt(ItemStack::getCount).sum();
    }

    private static int count(MachineBlockEntity be, Item item) {
        int n = 0;
        for (int i = 0; i < be.getContainerSize(); i++) {
            if (be.getItem(i).is(item)) {
                n += be.getItem(i).getCount();
            }
        }
        return n;
    }

    private static <T> T be(GameTestHelper helper, BlockPos pos, Class<T> type) {
        return type.cast(helper.getLevel().getBlockEntity(helper.absolutePos(pos)));
    }

    private static Item gravel(Rock rock) {
        return ModItems.ROCK_ITEMS.get(RockItem.Stage.GRAVEL).get(rock).get();
    }

    // --- numbers -------------------------------------------------------------------------------------------------------

    private static void sieveYields(GameTestHelper helper) {
        RandomSource random = RandomSource.create(42);
        List<ItemStack> all = new ArrayList<>();
        for (int i = 0; i < 4000; i++) {
            all.addAll(Yields.sieve(Rock.COBBLESTONE, false, 1, random));
        }
        int iron = count(all, ModItems.IRON_FRAGMENT.get());
        helper.assertTrue(iron > 420 && iron < 580, "iron fragments from 4000 cobblestone gravel ~500: " + iron);
        helper.assertTrue(count(all, Items.COAL) > 250, "coal from cobblestone");
        List<ItemStack> tuff = new ArrayList<>();
        for (int i = 0; i < 2000; i++) {
            tuff.addAll(Yields.sieve(Rock.TUFF, false, 1, random));
        }
        helper.assertTrue(tuff.isEmpty(), "a string mesh lets no gold or emerald through");
        List<ItemStack> deep = new ArrayList<>();
        for (int i = 0; i < 2000; i++) {
            deep.addAll(Yields.sieve(Rock.DEEPSLATE, false, 3, random));
        }
        helper.assertTrue(count(deep, ModItems.DIAMOND_SHARD.get()) > 30, "diamond mesh + deepslate: diamond shards");
        helper.assertTrue(count(deep, Items.REDSTONE) > 200, "deepslate: redstone");
        helper.succeed();
    }

    /** 64 cobblestone = 128 gravel: ~4 raw iron by hand, ~9 with the sluice, ~14 with flotation. */
    private static void balance(GameTestHelper helper) {
        RandomSource random = RandomSource.create(7);
        double sieve = 0;
        double sluice = 0;
        double flotation = 0;
        int runs = 50;
        for (int r = 0; r < runs; r++) {
            for (int g = 0; g < 128; g++) {
                sieve += count(Yields.sieve(Rock.COBBLESTONE, false, 1, random), ModItems.IRON_FRAGMENT.get()) / 4.0;
                for (int s = 0; s < 8; s++) {
                    sluice += count(Yields.sluiceSegment(Rock.COBBLESTONE, false, random), ModItems.IRON_FRAGMENT.get()) / 4.0;
                }
                flotation += Yields.flotation(Rock.COBBLESTONE, false, Resource.IRON, random);
            }
        }
        double hand = sieve / runs;
        double withSluice = hand + sluice / runs;
        double withFlotation = withSluice + flotation / runs;
        helper.assertTrue(Math.abs(hand - 4) < 0.6, "hand sieve ~4 raw iron: " + hand);
        helper.assertTrue(Math.abs(withSluice - 9) < 1.0, "+ sluice ~9: " + withSluice);
        helper.assertTrue(Math.abs(withFlotation - 14) < 1.2, "+ flotation ~14: " + withFlotation);
        helper.succeed();
    }

    private static void richTriples(GameTestHelper helper) {
        RandomSource random = RandomSource.create(3);
        int plain = 0;
        int rich = 0;
        for (int i = 0; i < 3000; i++) {
            plain += count(Yields.sieve(Rock.ANDESITE, false, 1, random), ModItems.IRON_FRAGMENT.get());
            rich += count(Yields.sieve(Rock.ANDESITE, true, 1, random), ModItems.IRON_FRAGMENT.get());
        }
        double ratio = rich / (double) plain;
        helper.assertTrue(ratio > 2.6 && ratio < 3.4, "rich rock yields three times: " + ratio);
        helper.succeed();
    }

    private static void veins(GameTestHelper helper) {
        helper.assertValueEqual(Veins.vein(1234L, 10, -7, false), Veins.vein(1234L, 10, -7, false), "same chunk, same vein");
        helper.assertValueEqual(Veins.vein(1234L, 10, -7, true), Rock.BLACKSTONE, "Nether vein");
        Set<Rock> seen = EnumSet.noneOf(Rock.class);
        for (int x = 0; x < 40; x++) {
            for (int z = 0; z < 40; z++) {
                seen.add(Veins.vein(99L, x, z, false));
            }
        }
        helper.assertTrue(seen.size() == 6, "all six overworld rocks occur: " + seen);
        helper.succeed();
    }

    // --- phase 1 -------------------------------------------------------------------------------------------------------

    private static void stoneHammer(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos rel = new BlockPos(3, 1, 3);
        helper.setBlock(rel, Blocks.GRANITE);
        FakePlayer player = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "hammer"));
        BlockPos pos = helper.absolutePos(rel);
        player.snapTo(pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 2.5, 180.0F, 30.0F);
        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new ItemStack(ModItems.HAMMERS.get("iron").get()));
        player.gameMode.destroyBlock(pos);
        List<ItemEntity> drops = level.getEntities(EntityTypes.ITEM, new AABB(pos).inflate(2), e -> true);
        int gravel = drops.stream().filter(e -> e.getItem().is(gravel(Rock.GRANITE))).mapToInt(e -> e.getItem().getCount()).sum();
        helper.assertValueEqual(gravel, 2, "granite gravel from a hammered granite block");
        helper.assertTrue(drops.stream().noneMatch(e -> e.getItem().is(Items.GRANITE)), "no granite block");
        drops.forEach(ItemEntity::discard);
        helper.succeed();
    }

    private static void handSieve(GameTestHelper helper) {
        BlockPos rel = new BlockPos(2, 1, 2);
        helper.setBlock(rel, ModBlocks.HAND_SIEVE.get());
        HandSieveBlockEntity sieve = be(helper, rel, HandSieveBlockEntity.class);
        ItemStack gravel = RockItem.stack(RockItem.Stage.GRAVEL, Rock.ANDESITE, false, 3);
        helper.assertTrue(sieve.addGravel(gravel), "gravel goes on the sieve");
        helper.assertTrue(sieve.work(helper.getLevel()) == null, "no mesh yet: nothing happens");
        sieve.swapMesh(new ItemStack(ModItems.STRING_MESH.get()));
        helper.assertFalse(sieve.addGravel(RockItem.stack(RockItem.Stage.GRAVEL, Rock.GRANITE, false, 1)), "one rock per pile");
        List<ItemStack> out = null;
        int clicks = 0;
        while (out == null && clicks < 20) {
            out = sieve.work(helper.getLevel());
            clicks++;
        }
        helper.assertValueEqual(clicks, HandSieveBlockEntity.WORK / HandSieveBlockEntity.PER_CLICK, "clicks (2 s held)");
        helper.assertTrue(out != null && count(out, ModItems.ROCK_ITEMS.get(RockItem.Stage.FLOUR).get(Rock.ANDESITE).get()) == 1,
                "one rock flour per gravel");
        helper.assertValueEqual(sieve.count(), 0, "pile used up");
        helper.succeed();
    }

    // --- phase 2 -------------------------------------------------------------------------------------------------------

    private static void grinder(GameTestHelper helper) {
        BlockPos rel = new BlockPos(2, 1, 2);
        helper.setBlock(rel, ModBlocks.GRINDER.get());
        GrinderBlockEntity grinder = be(helper, rel, GrinderBlockEntity.class);
        grinder.setItem(0, new ItemStack(Items.DIORITE, 4));
        grinder.setItem(1, new ItemStack(Items.COAL, 1));
        helper.succeedWhen(() -> {
            int n = count(grinder, gravel(Rock.DIORITE));
            helper.assertTrue(grinder.getItem(0).isEmpty() && n >= 8 && n <= 12, "4 diorite -> 8-12 diorite gravel: " + n);
            helper.assertTrue(grinder.getItem(1).isEmpty(), "one coal was burnt");
        });
    }

    private static void shakerSieve(GameTestHelper helper) {
        BlockPos rel = new BlockPos(2, 1, 2);
        helper.setBlock(rel, ModBlocks.SHAKER_SIEVE.get());
        ShakerSieveBlockEntity sieve = be(helper, rel, ShakerSieveBlockEntity.class);
        ServerLevel level = helper.getLevel();
        sieve.setItem(0, RockItem.stack(RockItem.Stage.GRAVEL, Rock.COBBLESTONE, false, 10));
        sieve.setItem(1, new ItemStack(ModItems.STRING_MESH.get()));
        sieve.onSignal(level, true);
        helper.assertValueEqual(sieve.getItem(0).getCount(), 9, "rising edge sifts once");
        sieve.onSignal(level, true);
        helper.assertValueEqual(sieve.getItem(0).getCount(), 9, "steady signal does nothing");
        sieve.onSignal(level, false);
        sieve.onSignal(level, true);
        helper.assertValueEqual(sieve.getItem(0).getCount(), 9, "5-tick cooldown");
        helper.assertValueEqual(sieve.getItem(1).getDamageValue(), 1, "the mesh wore");
        helper.runAfterDelay(6, () -> {
            sieve.onSignal(level, false);
            sieve.onSignal(level, true);
            helper.assertValueEqual(sieve.getItem(0).getCount(), 8, "after the cooldown it sifts again");
            helper.assertValueEqual(count(sieve, ModItems.ROCK_ITEMS.get(RockItem.Stage.FLOUR).get(Rock.COBBLESTONE).get()), 2,
                    "rock flour per pass");
            helper.succeed();
        });
    }

    private static void rockFormer(GameTestHelper helper) {
        // liquids sunk into the floor so the water cannot reach the lava
        helper.setBlock(new BlockPos(4, 0, 5), Blocks.WATER);
        helper.setBlock(new BlockPos(6, 0, 5), Blocks.LAVA);
        BlockPos rel = new BlockPos(5, 0, 5);
        helper.setBlock(rel, ModBlocks.ROCK_FORMER.get());
        RockFormerBlockEntity former = be(helper, rel, RockFormerBlockEntity.class);
        former.setItem(0, new ItemStack(Items.GRANITE));
        helper.runAfterDelay(65, () -> {
            helper.assertTrue(former.getItem(3).is(Items.GRANITE) && former.getItem(3).getCount() >= 2,
                    "about one granite per second: " + former.getItem(3));
            helper.assertValueEqual(former.getItem(0).getCount(), 1, "the pattern block stays");
            helper.setBlock(new BlockPos(6, 0, 5), Blocks.STONE);
            former.setItem(0, new ItemStack(Items.BLACKSTONE));
            helper.assertValueEqual(former.status(helper.getLevel()), RockFormerBlockEntity.NO_LAVA, "no lava, no rock");
            helper.succeed();
        });
    }

    private static void rockFormerLava(GameTestHelper helper) {
        // liquids sunk into the floor so the water cannot reach the lava
        helper.setBlock(new BlockPos(4, 0, 5), Blocks.WATER);
        helper.setBlock(new BlockPos(6, 0, 5), Blocks.LAVA);
        BlockPos rel = new BlockPos(5, 0, 5);
        helper.setBlock(rel, ModBlocks.ROCK_FORMER.get());
        RockFormerBlockEntity former = be(helper, rel, RockFormerBlockEntity.class);
        former.setItem(0, new ItemStack(Items.COBBLED_DEEPSLATE));
        former.setItem(1, new ItemStack(ModItems.SPEED_UPGRADE_ADVANCED.get()));
        helper.succeedWhen(() -> {
            helper.assertTrue(former.getItem(3).getCount() >= 64, "64 deepslate formed: " + former.getItem(3).getCount());
            helper.assertBlockPresent(Blocks.AIR, new BlockPos(6, 0, 5));
        });
    }

    // --- phase 3 -------------------------------------------------------------------------------------------------------

    private static void sluice(GameTestHelper helper) {
        helper.setBlock(new BlockPos(5, 1, 1), Blocks.WATER);
        for (int z = 2; z <= 5; z++) {
            helper.setBlock(new BlockPos(5, 1, z), ModBlocks.SLUICE.get().defaultBlockState().setValue(SluiceBlock.FACING, Direction.SOUTH));
        }
        helper.setBlock(new BlockPos(5, 1, 6), Blocks.CHEST);
        com.afjan.stonesift.block.SluiceBlockEntity head = be(helper, new BlockPos(5, 1, 2), com.afjan.stonesift.block.SluiceBlockEntity.class);
        head.scan(helper.getLevel());
        helper.assertTrue(head.isHead() && head.length() == 4, "controller with 4 segments: " + head.length());
        head.setItem(0, RockItem.stack(RockItem.Stage.FLOUR, Rock.ANDESITE, false, 6));
        ChestBlockEntity chest = be(helper, new BlockPos(5, 1, 6), ChestBlockEntity.class);
        Item slurry = ModItems.ROCK_ITEMS.get(RockItem.Stage.SLURRY).get(Rock.ANDESITE).get();
        helper.succeedWhen(() -> {
            int n = 0;
            for (int i = 0; i < chest.getContainerSize(); i++) {
                if (chest.getItem(i).is(slurry)) {
                    n += chest.getItem(i).getCount();
                }
            }
            helper.assertValueEqual(n, 6, "every flour portion comes out as fine slurry");
        });
    }

    private static void flotation(GameTestHelper helper) {
        BlockPos genRel = new BlockPos(2, 1, 5);
        BlockPos cellRel = new BlockPos(3, 1, 5);
        helper.setBlock(genRel, ModBlocks.COAL_GENERATOR.get());
        helper.setBlock(cellRel, ModBlocks.FLOTATION_CELL.get());
        helper.setBlock(new BlockPos(4, 1, 5), Blocks.WATER);
        CoalGeneratorBlockEntity gen = be(helper, genRel, CoalGeneratorBlockEntity.class);
        FlotationCellBlockEntity cell = be(helper, cellRel, FlotationCellBlockEntity.class);
        gen.setItem(0, new ItemStack(Items.COAL, 2));
        cell.setItem(0, RockItem.stack(RockItem.Stage.SLURRY, Rock.ANDESITE, true, 4));
        cell.setItem(1, new ItemStack(Items.DRIED_KELP, 1));
        helper.succeedWhen(() -> {
            helper.assertTrue(cell.getItem(0).isEmpty(), "all 4 slurry floated: " + cell.getItem(0).getCount());
            helper.assertTrue(cell.getItem(1).isEmpty(), "one reagent treats 4 slurry");
            helper.assertTrue(gen.getItem(0).getCount() < 2, "the generator burnt coal");
        });
    }

    // --- phase 4 -------------------------------------------------------------------------------------------------------

    private static void deepDrill(GameTestHelper helper) {
        BlockPos rel = new BlockPos(6, 2, 6);
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (dx != 0 || dz != 0) {
                    helper.setBlock(rel.offset(dx, 0, dz), ModBlocks.DEEP_DRILL_FRAME.get());
                }
            }
        }
        helper.setBlock(rel, ModBlocks.DEEP_DRILL.get());
        DeepDrillBlockEntity drill = be(helper, rel, DeepDrillBlockEntity.class);
        drill.timeScale = 1000;
        drill.energy.set(DeepDrillBlockEntity.CAPACITY);
        drill.setItem(0, new ItemStack(ModItems.DIAMOND_DRILL_BIT.get()));
        helper.assertTrue(drill.isFormed(helper.getLevel()), "3x3 frame ring recognised");
        BlockPos abs = helper.absolutePos(rel);
        Rock vein = Veins.vein(helper.getLevel(), abs.getX() >> 4, abs.getZ() >> 4);
        helper.succeedWhen(() -> {
            helper.assertTrue(drill.reachedBedrock(), "bored down to the bedrock");
            helper.assertBlockPresent(Blocks.AIR, rel.below(2));
            ItemStack first = drill.getItem(1);
            helper.assertTrue(first.is(gravel(vein)) && RockItem.isRich(first), "pumps rich " + vein.key() + " gravel: " + first);
            helper.assertTrue(drill.getItem(0).getDamageValue() > 0, "the bit wears");
        });
    }
}
