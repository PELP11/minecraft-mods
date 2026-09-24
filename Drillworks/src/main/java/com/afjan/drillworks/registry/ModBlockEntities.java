package com.afjan.drillworks.registry;

import com.afjan.drillworks.Drillworks;
import com.afjan.drillworks.block.entity.RefineryBlockEntity;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, Drillworks.MODID);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<RefineryBlockEntity>> REFINERY =
            BLOCK_ENTITIES.register("refinery", () -> new BlockEntityType<>(RefineryBlockEntity::new, ModBlocks.REFINERY.get()));

    private ModBlockEntities() {}
}
