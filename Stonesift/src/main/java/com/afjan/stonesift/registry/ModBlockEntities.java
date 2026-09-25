package com.afjan.stonesift.registry;

import java.util.EnumMap;
import java.util.Map;

import com.afjan.stonesift.Stonesift;
import com.afjan.stonesift.block.HandSieveBlockEntity;
import com.afjan.stonesift.block.SluiceBlockEntity;
import com.afjan.stonesift.machine.CoalGeneratorBlockEntity;
import com.afjan.stonesift.machine.DeepDrillBlockEntity;
import com.afjan.stonesift.machine.FlotationCellBlockEntity;
import com.afjan.stonesift.machine.GrinderBlockEntity;
import com.afjan.stonesift.machine.MachineBlockEntity;
import com.afjan.stonesift.machine.MachineType;
import com.afjan.stonesift.machine.RockFormerBlockEntity;
import com.afjan.stonesift.machine.ShakerSieveBlockEntity;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, Stonesift.MODID);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<HandSieveBlockEntity>> HAND_SIEVE =
            BLOCK_ENTITIES.register("hand_sieve", () -> new BlockEntityType<>(HandSieveBlockEntity::new, ModBlocks.HAND_SIEVE.get()));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<SluiceBlockEntity>> SLUICE =
            BLOCK_ENTITIES.register("sluice", () -> new BlockEntityType<>(SluiceBlockEntity::new, ModBlocks.SLUICE.get()));

    public static final Map<MachineType, DeferredHolder<BlockEntityType<?>, BlockEntityType<? extends MachineBlockEntity>>> MACHINES =
            new EnumMap<>(MachineType.class);

    static {
        MACHINES.put(MachineType.GRINDER, BLOCK_ENTITIES.register("grinder",
                () -> new BlockEntityType<>(GrinderBlockEntity::new, ModBlocks.GRINDER.get())));
        MACHINES.put(MachineType.SHAKER_SIEVE, BLOCK_ENTITIES.register("shaker_sieve",
                () -> new BlockEntityType<>(ShakerSieveBlockEntity::new, ModBlocks.SHAKER_SIEVE.get())));
        MACHINES.put(MachineType.ROCK_FORMER, BLOCK_ENTITIES.register("rock_former",
                () -> new BlockEntityType<>(RockFormerBlockEntity::new, ModBlocks.ROCK_FORMER.get())));
        MACHINES.put(MachineType.FLOTATION_CELL, BLOCK_ENTITIES.register("flotation_cell",
                () -> new BlockEntityType<>(FlotationCellBlockEntity::new, ModBlocks.FLOTATION_CELL.get())));
        MACHINES.put(MachineType.COAL_GENERATOR, BLOCK_ENTITIES.register("coal_generator",
                () -> new BlockEntityType<>(CoalGeneratorBlockEntity::new, ModBlocks.COAL_GENERATOR.get())));
        MACHINES.put(MachineType.DEEP_DRILL, BLOCK_ENTITIES.register("deep_drill",
                () -> new BlockEntityType<>(DeepDrillBlockEntity::new, ModBlocks.DEEP_DRILL.get())));
    }

    private ModBlockEntities() {}
}
