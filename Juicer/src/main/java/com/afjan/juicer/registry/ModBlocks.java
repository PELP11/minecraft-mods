package com.afjan.juicer.registry;

import java.util.EnumMap;
import java.util.Map;

import com.afjan.juicer.Juicer;
import com.afjan.juicer.block.FruitLeavesBlock;
import com.afjan.juicer.block.FruitSaplingBlock;
import com.afjan.juicer.block.InfuserBlock;
import com.afjan.juicer.block.MixerBlock;
import com.afjan.juicer.block.TubingBlock;
import com.afjan.juicer.fruit.Fruit;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.random.WeightedList;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.grower.TreeGrower;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModBlocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(Juicer.MODID);

    public static final Map<Fruit, DeferredBlock<FruitLeavesBlock>> LEAVES = new EnumMap<>(Fruit.class);
    public static final Map<Fruit, DeferredBlock<FruitSaplingBlock>> SAPLINGS = new EnumMap<>(Fruit.class);
    public static final Map<Fruit, ResourceKey<Feature>> TREE_FEATURES = new EnumMap<>(Fruit.class);

    static {
        for (Fruit fruit : Fruit.values()) {
            // data/juicer/worldgen/feature/<fruit>_tree.json
            ResourceKey<Feature> treeKey = ResourceKey.create(Registries.FEATURE, Juicer.id(fruit.id() + "_tree"));
            TREE_FEATURES.put(fruit, treeKey);
            TreeGrower grower = new TreeGrower(Juicer.MODID + "_" + fruit.id(),
                    WeightedList.of(treeKey), WeightedList.of(), WeightedList.of(), treeKey);

            LEAVES.put(fruit, BLOCKS.registerBlock(fruit.id() + "_leaves",
                    p -> new FruitLeavesBlock(fruit, p), ModBlocks::leavesProperties));
            SAPLINGS.put(fruit, BLOCKS.registerBlock(fruit.id() + "_sapling",
                    p -> new FruitSaplingBlock(grower, fruit == Fruit.DRAGONFRUIT, p), ModBlocks::saplingProperties));
        }
    }

    public static final DeferredBlock<MixerBlock> MIXER = BLOCKS.registerBlock("mixer", MixerBlock::new,
            p -> p.mapColor(MapColor.METAL).strength(2.0F, 6.0F).requiresCorrectToolForDrops()
                    .sound(SoundType.METAL).noOcclusion());

    public static final DeferredBlock<InfuserBlock> INFUSER = BLOCKS.registerBlock("infuser", InfuserBlock::new,
            p -> p.mapColor(MapColor.COLOR_ORANGE).strength(2.0F, 6.0F).requiresCorrectToolForDrops()
                    .sound(SoundType.COPPER).noOcclusion());

    public static final DeferredBlock<TubingBlock> TUBING = BLOCKS.registerBlock("tubing", TubingBlock::new,
            p -> p.mapColor(MapColor.COLOR_ORANGE).strength(0.6F).sound(SoundType.COPPER).noOcclusion());

    private static BlockBehaviour.Properties leavesProperties(BlockBehaviour.Properties p) {
        return p.mapColor(MapColor.PLANT)
                .strength(0.2F)
                .randomTicks()
                .sound(SoundType.GRASS)
                .noOcclusion()
                .isValidSpawn(Blocks::ocelotOrParrot)
                .isSuffocating((state, level, pos) -> false)
                .ignitedByLava()
                .pushReaction(PushReaction.POPPED)
                .isRedstoneConductor((state, level, pos) -> false);
    }

    private static BlockBehaviour.Properties saplingProperties(BlockBehaviour.Properties p) {
        return p.mapColor(MapColor.PLANT)
                .noCollision()
                .randomTicks()
                .instabreak()
                .sound(SoundType.GRASS)
                .pushReaction(PushReaction.POPPED);
    }

    private ModBlocks() {}
}
