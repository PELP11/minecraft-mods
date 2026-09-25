package com.afjan.stonesift.registry;

import java.util.ArrayList;
import java.util.List;

import com.afjan.stonesift.Stonesift;
import com.afjan.stonesift.block.HandSieveBlock;
import com.afjan.stonesift.block.ShakerSieveBlock;
import com.afjan.stonesift.block.SluiceBlock;
import com.afjan.stonesift.machine.CoalGeneratorBlockEntity;
import com.afjan.stonesift.machine.DeepDrillBlockEntity;
import com.afjan.stonesift.machine.FlotationCellBlockEntity;
import com.afjan.stonesift.machine.GrinderBlockEntity;
import com.afjan.stonesift.machine.MachineBlock;
import com.afjan.stonesift.machine.MachineType;
import com.afjan.stonesift.machine.RockFormerBlockEntity;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModBlocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(Stonesift.MODID);
    public static final List<DeferredBlock<?>> ALL = new ArrayList<>();

    public static final DeferredBlock<HandSieveBlock> HAND_SIEVE = add(BLOCKS.registerBlock("hand_sieve", HandSieveBlock::new,
            p -> p.mapColor(MapColor.WOOD).strength(1.5F).sound(SoundType.WOOD).noOcclusion()));
    public static final DeferredBlock<MachineBlock> GRINDER = add(BLOCKS.registerBlock("grinder",
            p -> new MachineBlock(MachineType.GRINDER, GrinderBlockEntity::new, Block.box(0, 0, 0, 16, 16, 16), p), ModBlocks::metal));
    public static final DeferredBlock<ShakerSieveBlock> SHAKER_SIEVE = add(BLOCKS.registerBlock("shaker_sieve", ShakerSieveBlock::new,
            p -> p.mapColor(MapColor.WOOD).strength(2.5F).sound(SoundType.WOOD).noOcclusion()));
    public static final DeferredBlock<MachineBlock> ROCK_FORMER = add(BLOCKS.registerBlock("rock_former",
            p -> new MachineBlock(MachineType.ROCK_FORMER, RockFormerBlockEntity::new, Block.box(0, 0, 0, 16, 14, 16), p), ModBlocks::metal));
    public static final DeferredBlock<SluiceBlock> SLUICE = add(BLOCKS.registerBlock("sluice", SluiceBlock::new,
            p -> p.mapColor(MapColor.WOOD).strength(1.5F).sound(SoundType.WOOD).noOcclusion()));
    public static final DeferredBlock<MachineBlock> FLOTATION_CELL = add(BLOCKS.registerBlock("flotation_cell",
            p -> new MachineBlock(MachineType.FLOTATION_CELL, FlotationCellBlockEntity::new, Block.box(1, 0, 1, 15, 16, 15), p), ModBlocks::metal));
    public static final DeferredBlock<MachineBlock> COAL_GENERATOR = add(BLOCKS.registerBlock("coal_generator",
            p -> new MachineBlock(MachineType.COAL_GENERATOR, CoalGeneratorBlockEntity::new, Block.box(0, 0, 0, 16, 16, 16), p),
            p -> metal(p).lightLevel(s -> s.getValue(MachineBlock.LIT) ? 12 : 0)));
    public static final DeferredBlock<Block> DEEP_DRILL_FRAME = add(BLOCKS.registerBlock("deep_drill_frame", Block::new,
            p -> metal(p).noOcclusion()));
    public static final DeferredBlock<MachineBlock> DEEP_DRILL = add(BLOCKS.registerBlock("deep_drill",
            p -> new MachineBlock(MachineType.DEEP_DRILL, DeepDrillBlockEntity::new, Block.box(0, 0, 0, 16, 16, 16), p),
            p -> metal(p).strength(5.0F, 12.0F)));

    private static net.minecraft.world.level.block.state.BlockBehaviour.Properties metal(
            net.minecraft.world.level.block.state.BlockBehaviour.Properties p) {
        return p.mapColor(MapColor.METAL).strength(3.5F, 6.0F).requiresCorrectToolForDrops().sound(SoundType.METAL).noOcclusion();
    }

    private static <T extends DeferredBlock<?>> T add(T block) {
        ALL.add(block);
        return block;
    }

    private ModBlocks() {}
}
