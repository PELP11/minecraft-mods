package com.afjan.juicer.gametest;

import java.util.Map;
import java.util.function.Consumer;

import com.afjan.juicer.Juicer;
import com.afjan.juicer.block.Contents;
import com.afjan.juicer.block.FruitLeavesBlock;
import com.afjan.juicer.block.FruitSaplingBlock;
import com.afjan.juicer.block.InfuserBlock;
import com.afjan.juicer.block.MixerBlock;
import com.afjan.juicer.block.TubeConnection;
import com.afjan.juicer.block.TubingBlock;
import com.afjan.juicer.block.entity.InfuserBlockEntity;
import com.afjan.juicer.block.entity.MixerBlockEntity;
import com.afjan.juicer.fruit.Fruit;
import com.afjan.juicer.registry.ModBlocks;
import com.afjan.juicer.registry.ModEffects;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.FunctionGameTestInstance;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestData;
import net.minecraft.gametest.framework.TestEnvironmentDefinition;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.event.level.block.BreakBlockEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * In-game tests run by {@code gradlew runGameTestServer} (development only, never registered in a normal game).
 */
public final class JuicerGameTests {
    private static final DeferredRegister<Consumer<GameTestHelper>> FUNCTIONS = DeferredRegister.create(Registries.TEST_FUNCTION, Juicer.MODID);

    private static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> PIPELINE =
            FUNCTIONS.register("pipeline", () -> JuicerGameTests::pipeline);
    private static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> WRONG_FRUIT_REJECTED =
            FUNCTIONS.register("wrong_fruit_rejected", () -> JuicerGameTests::wrongFruitRejected);
    private static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> JUICE_EFFECTS =
            FUNCTIONS.register("juice_effects", () -> JuicerGameTests::juiceEffects);
    private static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> LOOTING_KILLS =
            FUNCTIONS.register("looting_kills", () -> JuicerGameTests::lootingKills);
    private static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> TREES_GROW =
            FUNCTIONS.register("trees_grow", () -> JuicerGameTests::treesGrow);
    private static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> HARVEST_FRUIT =
            FUNCTIONS.register("harvest_fruit", () -> JuicerGameTests::harvestFruit);
    private static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> FRUIT_GROWS =
            FUNCTIONS.register("fruit_grows", () -> JuicerGameTests::fruitGrows);
    private static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> TREES_IN_BIOMES =
            FUNCTIONS.register("trees_in_biomes", () -> JuicerGameTests::treesInBiomes);
    private static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> FORTUNE_MINING =
            FUNCTIONS.register("fortune_mining", () -> JuicerGameTests::fortuneMining);

    private JuicerGameTests() {}

    public static void register(IEventBus modBus) {
        FUNCTIONS.register(modBus);
        modBus.addListener(JuicerGameTests::registerTests);
    }

    private static void registerTests(RegisterGameTestsEvent event) {
        Holder<TestEnvironmentDefinition<?>> environment = event.registerEnvironment(Juicer.id("default"));
        Identifier platform = Juicer.id("test_platform");
        Identifier orchard = Juicer.id("test_orchard");
        add(event, PIPELINE, environment, platform, 1400);
        add(event, WRONG_FRUIT_REJECTED, environment, platform, 200);
        add(event, JUICE_EFFECTS, environment, platform, 40);
        add(event, LOOTING_KILLS, environment, platform, 60);
        add(event, TREES_GROW, environment, orchard, 60);
        add(event, HARVEST_FRUIT, environment, platform, 40);
        add(event, FRUIT_GROWS, environment, platform, 40);
        add(event, TREES_IN_BIOMES, environment, platform, 20);
        add(event, FORTUNE_MINING, environment, platform, 20);
    }

    private static void add(RegisterGameTestsEvent event, DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> function,
            Holder<TestEnvironmentDefinition<?>> environment, Identifier structure, int maxTicks) {
        event.registerTest(function.getId(), new FunctionGameTestInstance(function.getKey(),
                new TestData<>(environment, structure, maxTicks, 1, true)));
    }

    // ---------------------------------------------------------------------------------------------------------

    /** 8 oranges -> Fruit Mixer -> 3 tubes -> Juice Infuser (+2 sugar, +2 bottles) = 2 Orange Juices. */
    private static void pipeline(GameTestHelper helper) {
        BlockPos mixerPos = new BlockPos(1, 1, 2);
        BlockPos infuserPos = new BlockPos(5, 1, 2);
        helper.setBlock(mixerPos, ModBlocks.MIXER.get());
        for (int x = 2; x <= 4; x++) {
            helper.setBlock(new BlockPos(x, 1, 2), ModBlocks.TUBING.get());
        }
        helper.setBlock(infuserPos, ModBlocks.INFUSER.get());
        // setBlock skips placement logic, so apply the connections a player-placed tube would get
        for (int x = 2; x <= 4; x++) {
            BlockPos absolute = helper.absolutePos(new BlockPos(x, 1, 2));
            BlockState tube = helper.getLevel().getBlockState(absolute);
            helper.getLevel().setBlockAndUpdate(absolute, ModBlocks.TUBING.get().withConnections(tube, helper.getLevel(), absolute));
        }

        MixerBlockEntity mixer = helper.getBlockEntity(mixerPos, MixerBlockEntity.class);
        InfuserBlockEntity infuser = helper.getBlockEntity(infuserPos, InfuserBlockEntity.class);
        mixer.setItem(0, new ItemStack(Fruit.ORANGE.fruitItem(), 8));
        infuser.setItem(InfuserBlockEntity.SLOT_SUGAR, new ItemStack(Items.SUGAR, 2));
        infuser.setItem(InfuserBlockEntity.SLOT_BOTTLE, new ItemStack(Items.GLASS_BOTTLE, 2));

        helper.succeedWhen(() -> {
            ItemStack output = infuser.getItem(InfuserBlockEntity.SLOT_OUTPUT);
            helper.assertTrue(output.is(Fruit.ORANGE.juiceItem()) && output.getCount() == 2,
                    "Expected 2 Orange Juice in the infuser output, found " + output);
            helper.assertTrue(mixer.getItem(0).isEmpty(), "Mixer should have used all fruit");
            helper.assertTrue(mixer.getTank().isEmpty() && infuser.getTank().isEmpty(),
                    "All 2000 mB of concentrate should have become juice");
            helper.assertTrue(infuser.getItem(InfuserBlockEntity.SLOT_SUGAR).isEmpty()
                    && infuser.getItem(InfuserBlockEntity.SLOT_BOTTLE).isEmpty(), "Sugar and bottles should be used up");
            BlockState first = helper.getBlockState(new BlockPos(2, 1, 2));
            BlockState middle = helper.getBlockState(new BlockPos(3, 1, 2));
            BlockState last = helper.getBlockState(new BlockPos(4, 1, 2));
            helper.assertTrue(first.getValue(TubingBlock.WEST) == TubeConnection.MACHINE
                    && first.getValue(TubingBlock.EAST) == TubeConnection.TUBE, "First tube should connect mixer and tube: " + first);
            helper.assertTrue(middle.getValue(TubingBlock.EAST) == TubeConnection.TUBE
                    && middle.getValue(TubingBlock.WEST) == TubeConnection.TUBE, "Middle tube should connect both ways: " + middle);
            helper.assertTrue(last.getValue(TubingBlock.EAST) == TubeConnection.MACHINE
                    && last.getValue(TubingBlock.NORTH) == TubeConnection.NONE, "Last tube should connect to the infuser: " + last);
        });
    }

    /** An infuser holding starfruit concentrate must not accept lime concentrate. */
    private static void wrongFruitRejected(GameTestHelper helper) {
        BlockPos mixerPos = new BlockPos(1, 1, 2);
        BlockPos infuserPos = new BlockPos(2, 1, 2);
        helper.setBlock(mixerPos, ModBlocks.MIXER.get());
        helper.setBlock(infuserPos, ModBlocks.INFUSER.get());
        MixerBlockEntity mixer = helper.getBlockEntity(mixerPos, MixerBlockEntity.class);
        InfuserBlockEntity infuser = helper.getBlockEntity(infuserPos, InfuserBlockEntity.class);
        infuser.receiveConcentrate(Fruit.STARFRUIT, 500);
        mixer.getTank().fill(Fruit.LIME, 1000, false);

        helper.runAfterDelay(40, () -> helper.succeedIf(() -> {
            helper.assertValueEqual(infuser.getTank().fruit(), Fruit.STARFRUIT, "infuser fruit");
            helper.assertValueEqual(infuser.getTank().amount(), 500, "infuser amount");
            helper.assertValueEqual(mixer.getTank().amount(), 1000, "mixer amount");
            helper.assertValueEqual(helper.getBlockState(mixerPos).getValue(MixerBlock.CONTENTS), Contents.LIME, "mixer liquid");
            helper.assertValueEqual(helper.getBlockState(infuserPos).getValue(InfuserBlock.CONTENTS), Contents.STARFRUIT, "infuser liquid");
        }));
    }

    /** Drinking each juice applies its signature buff. */
    private static void juiceEffects(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        for (Fruit fruit : Fruit.values()) {
            ItemStack juice = new ItemStack(fruit.juiceItem());
            ItemStack left = juice.finishUsingItem(helper.getLevel(), player);
            helper.assertTrue(left.is(Items.GLASS_BOTTLE), fruit.id() + " juice should leave a glass bottle, got " + left);
        }
        helper.assertLivingEntityHasMobEffect(player, ModEffects.LOOTING, 9);
        helper.assertLivingEntityHasMobEffect(player, ModEffects.FORTUNE, 9);
        helper.assertFalse(player.hasEffect(MobEffects.LUCK), "Starfruit Juice no longer gives Luck");
        helper.assertLivingEntityHasMobEffect(player, ModEffects.FLIGHT, 0);
        helper.assertLivingEntityHasMobEffect(player, ModEffects.TITAN, 0);
        helper.assertLivingEntityHasMobEffect(player, ModEffects.VITALITY, 0);
        helper.assertLivingEntityHasMobEffect(player, ModEffects.ZEST, 0);
        helper.assertTrue(player.mayFly(), "Dragonfruit Juice should allow flying");
        helper.assertValueEqual(player.getMaxHealth(), 40.0F, "max health with Vitality");
        helper.assertTrue(player.getAttributeValue(Attributes.ATTACK_DAMAGE) >= 9.0, "Titan should add attack damage");
        helper.assertTrue(player.getAttributeValue(Attributes.STEP_HEIGHT) > 1.0, "Zest should allow stepping up full blocks");

        // Vitality blocks harmful effects
        player.addEffect(new net.minecraft.world.effect.MobEffectInstance(MobEffects.POISON, 200));
        helper.assertFalse(player.hasEffect(MobEffects.POISON), "Vitality should prevent poison");
        helper.succeed();
    }

    /** 20 zombies killed with Looting X must drop far more rotten flesh than without it (~6 vs ~1 per zombie). */
    private static void lootingKills(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.addEffect(new net.minecraft.world.effect.MobEffectInstance(ModEffects.LOOTING, 1200, 9));
        BlockPos pos = new BlockPos(4, 2, 2);
        for (int i = 0; i < 20; i++) {
            Zombie zombie = helper.spawnWithNoFreeWill(EntityTypes.ZOMBIE, pos);
            zombie.hurtServer(helper.getLevel(), player.damageSources().playerAttack(player), 1000.0F);
        }
        helper.runAfterDelay(2, () -> {
            int flesh = helper.getEntities(EntityTypes.ITEM).stream()
                    .map(ItemEntity::getItem)
                    .filter(stack -> stack.is(Items.ROTTEN_FLESH))
                    .mapToInt(ItemStack::getCount)
                    .sum();
            helper.assertTrue(flesh >= 60, "Looting X should give lots of rotten flesh, got only " + flesh);
            helper.succeed();
        });
    }

    /**
     * 20 coal ores mined with a plain diamond pickaxe: exactly 20 coal normally, but ~110 with the Fortune X effect
     * (goes through the real break-event + loot-table path, including the pickaxe copy the game uses).
     */
    private static void fortuneMining(GameTestHelper helper) {
        BlockState ore = Blocks.COAL_ORE.defaultBlockState();
        BlockPos luckyPos = new BlockPos(2, 1, 2);
        BlockPos normalPos = new BlockPos(6, 1, 2);
        helper.setBlock(luckyPos, ore);
        helper.setBlock(normalPos, ore);
        Player lucky = helper.makeMockPlayer(GameType.SURVIVAL);
        lucky.addEffect(new net.minecraft.world.effect.MobEffectInstance(ModEffects.FORTUNE, 1200, 9));
        Player normal = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack pickaxe = new ItemStack(Items.DIAMOND_PICKAXE);
        int withFortune = 0;
        int without = 0;
        for (int i = 0; i < 20; i++) {
            withFortune += mineCoal(helper, helper.absolutePos(luckyPos), ore, lucky, pickaxe);
            without += mineCoal(helper, helper.absolutePos(normalPos), ore, normal, pickaxe);
        }
        helper.assertValueEqual(without, 20, "coal from 20 ores without Fortune");
        helper.assertTrue(withFortune >= 60, "Fortune X should multiply ore drops, got only " + withFortune + " coal");
        helper.succeed();
    }

    private static int mineCoal(GameTestHelper helper, BlockPos pos, BlockState state, Player player, ItemStack tool) {
        NeoForge.EVENT_BUS.post(new BreakBlockEvent(helper.getLevel(), pos, state, player));
        return Block.getDrops(state, helper.getLevel(), pos, null, player, tool.copy()).stream()
                .filter(stack -> stack.is(Items.COAL))
                .mapToInt(ItemStack::getCount)
                .sum();
    }

    /** Every sapling grows into its tree (validates the tree feature JSON too). */
    private static void treesGrow(GameTestHelper helper) {
        Fruit[] fruits = Fruit.values();
        BlockPos[] spots = { new BlockPos(2, 1, 2), new BlockPos(12, 1, 2), new BlockPos(2, 1, 12), new BlockPos(12, 1, 12), new BlockPos(7, 1, 7) };
        for (int i = 0; i < fruits.length; i++) {
            FruitSaplingBlock sapling = ModBlocks.SAPLINGS.get(fruits[i]).get();
            helper.setBlock(spots[i], sapling);
            BlockPos absolute = helper.absolutePos(spots[i]);
            BlockState state = helper.getLevel().getBlockState(absolute);
            sapling.advanceTree(helper.getLevel(), absolute, state, helper.getLevel().getRandom());
            sapling.advanceTree(helper.getLevel(), absolute, helper.getLevel().getBlockState(absolute), helper.getLevel().getRandom());
        }
        helper.runAfterDelay(1, () -> helper.succeedIf(() -> {
            for (int i = 0; i < fruits.length; i++) {
                BlockState grown = helper.getBlockState(spots[i]);
                helper.assertFalse(grown.getBlock() instanceof FruitSaplingBlock, fruits[i].id() + " sapling did not grow");
                helper.assertTrue(grown.is(net.minecraft.tags.BlockTags.LOGS), fruits[i].id() + " tree should have a log trunk, found " + grown);
                boolean leavesFound = false;
                for (BlockPos p : BlockPos.betweenClosed(spots[i].offset(-5, 0, -5), spots[i].offset(5, 14, 5))) {
                    if (helper.getBlockState(p).getBlock() instanceof FruitLeavesBlock leaves && leaves.getFruit() == fruits[i]) {
                        leavesFound = true;
                        break;
                    }
                }
                helper.assertTrue(leavesFound, fruits[i].id() + " tree should have " + fruits[i].id() + " leaves");
            }
        }));
    }

    /** Right-clicking ripe leaves drops fruit and resets the leaves. */
    private static void harvestFruit(GameTestHelper helper) {
        BlockPos pos = new BlockPos(3, 2, 2);
        helper.setBlock(pos, ModBlocks.LEAVES.get(Fruit.POMEGRANATE).get().defaultBlockState()
                .setValue(FruitLeavesBlock.AGE, FruitLeavesBlock.MAX_AGE)
                .setValue(LeavesBlock.PERSISTENT, true));
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        helper.useBlock(pos, player);
        helper.runAfterDelay(1, () -> helper.succeedIf(() -> {
            helper.assertItemEntityPresent(Fruit.POMEGRANATE.fruitItem(), pos, 2.0);
            helper.assertBlockProperty(pos, FruitLeavesBlock.AGE, 0);
        }));
    }

    /** The biome modifiers add each fruit tree to (a sample of) its biomes, so they generate naturally. */
    private static void treesInBiomes(GameTestHelper helper) {
        var biomes = helper.getLevel().registryAccess().lookupOrThrow(Registries.BIOME);
        Map<Fruit, ResourceKey<Biome>> samples = Map.of(
                Fruit.ORANGE, Biomes.PLAINS,
                Fruit.LIME, Biomes.SWAMP,
                Fruit.STARFRUIT, Biomes.JUNGLE,
                Fruit.POMEGRANATE, Biomes.SAVANNA,
                Fruit.DRAGONFRUIT, Biomes.DESERT);
        for (Map.Entry<Fruit, ResourceKey<Biome>> sample : samples.entrySet()) {
            ResourceKey<PlacedFeature> tree = ResourceKey.create(Registries.PLACED_FEATURE, Juicer.id(sample.getKey().id() + "_tree"));
            boolean present = biomes.getOrThrow(sample.getValue()).value().getGenerationSettings().features().stream()
                    .flatMap(HolderSet::stream)
                    .anyMatch(feature -> feature.is(tree));
            helper.assertTrue(present, tree.identifier() + " should generate in " + sample.getValue().identifier());
        }
        helper.succeed();
    }

    /** Random ticks ripen fruit (and persistent leaves don't decay without a trunk). */
    private static void fruitGrows(GameTestHelper helper) {
        BlockPos pos = new BlockPos(3, 2, 2);
        helper.setBlock(pos, ModBlocks.LEAVES.get(Fruit.LIME).get().defaultBlockState().setValue(LeavesBlock.PERSISTENT, true));
        for (int i = 0; i < 120; i++) {
            helper.randomTick(pos);
        }
        helper.succeedIf(() -> {
            helper.assertBlockPresent(ModBlocks.LEAVES.get(Fruit.LIME).get(), pos);
            helper.assertBlockProperty(pos, FruitLeavesBlock.AGE, FruitLeavesBlock.MAX_AGE);
            helper.assertTrue(helper.getBlockState(pos).getValue(BlockStateProperties.PERSISTENT), "leaves stay persistent");
        });
    }
}
