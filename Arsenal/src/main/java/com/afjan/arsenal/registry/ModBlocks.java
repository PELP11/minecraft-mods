package com.afjan.arsenal.registry;

import com.afjan.arsenal.Arsenal;
import com.afjan.arsenal.block.NukeBlock;
import com.afjan.arsenal.block.WeaponWorkbenchBlock;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModBlocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(Arsenal.MODID);

    public static final DeferredBlock<Block> WEAPON_WORKBENCH = BLOCKS.registerBlock("weapon_workbench",
            WeaponWorkbenchBlock::new, p -> p.mapColor(MapColor.COLOR_GRAY).strength(4.0F, 8.0F)
                    .requiresCorrectToolForDrops().sound(SoundType.METAL));

    public static final DeferredBlock<Block> TACTICAL_NUKE = BLOCKS.registerBlock("tactical_nuke",
            NukeBlock::new, p -> p.mapColor(MapColor.TERRACOTTA_YELLOW).strength(6.0F, 1200.0F)
                    .sound(SoundType.METAL));

    public static final DeferredBlock<Block> URANIUM_ORE = BLOCKS.registerSimpleBlock("uranium_ore",
            p -> p.mapColor(MapColor.STONE).strength(4.5F, 3.0F).requiresCorrectToolForDrops());

    public static final DeferredBlock<Block> DEEPSLATE_URANIUM_ORE = BLOCKS.registerSimpleBlock("deepslate_uranium_ore",
            p -> p.mapColor(MapColor.DEEPSLATE).strength(6.0F, 3.0F).requiresCorrectToolForDrops()
                    .sound(SoundType.DEEPSLATE));

    public static final DeferredBlock<Block> RAW_URANIUM_BLOCK = BLOCKS.registerSimpleBlock("raw_uranium_block",
            p -> p.mapColor(MapColor.COLOR_LIGHT_GREEN).strength(5.0F, 6.0F).requiresCorrectToolForDrops());

    public static final DeferredBlock<Block> URANIUM_BLOCK = BLOCKS.registerSimpleBlock("uranium_block",
            p -> p.mapColor(MapColor.COLOR_LIGHT_GREEN).strength(5.5F, 6.0F).requiresCorrectToolForDrops()
                    .sound(SoundType.METAL));

    public static final DeferredBlock<Block> STEEL_BLOCK = BLOCKS.registerSimpleBlock("steel_block",
            p -> p.mapColor(MapColor.COLOR_GRAY).strength(5.5F, 7.0F).requiresCorrectToolForDrops()
                    .sound(SoundType.METAL));

    private ModBlocks() {}
}
