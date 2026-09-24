package com.afjan.drillworks.registry;

import com.afjan.drillworks.Drillworks;
import com.afjan.drillworks.block.DistillationColumnBlock;
import com.afjan.drillworks.block.RefineryBlock;

import net.minecraft.util.valueproviders.UniformInt;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DropExperienceBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModBlocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(Drillworks.MODID);

    public static final DeferredBlock<Block> CRUDE_OIL_ORE = BLOCKS.registerBlock("crude_oil_ore",
            p -> new DropExperienceBlock(UniformInt.of(0, 2), p),
            p -> p.mapColor(MapColor.STONE).strength(3.0F, 3.0F).requiresCorrectToolForDrops());

    public static final DeferredBlock<Block> DEEPSLATE_CRUDE_OIL_ORE = BLOCKS.registerBlock("deepslate_crude_oil_ore",
            p -> new DropExperienceBlock(UniformInt.of(0, 2), p),
            p -> p.mapColor(MapColor.DEEPSLATE).strength(4.5F, 3.0F).requiresCorrectToolForDrops()
                    .sound(SoundType.DEEPSLATE));

    public static final DeferredBlock<Block> REFINERY = BLOCKS.registerBlock("refinery", RefineryBlock::new,
            p -> p.mapColor(MapColor.COLOR_RED).strength(3.5F, 6.0F).requiresCorrectToolForDrops()
                    .sound(SoundType.METAL).lightLevel(state -> state.getValue(RefineryBlock.LIT) ? 13 : 0)
                    .noOcclusion());

    public static final DeferredBlock<Block> DISTILLATION_COLUMN = BLOCKS.registerBlock("distillation_column",
            DistillationColumnBlock::new,
            p -> p.mapColor(MapColor.METAL).strength(3.5F, 6.0F).requiresCorrectToolForDrops()
                    .sound(SoundType.METAL).noOcclusion().lightLevel(state -> state.getValue(DistillationColumnBlock.TOP) ? 7 : 0));

    private ModBlocks() {}
}
