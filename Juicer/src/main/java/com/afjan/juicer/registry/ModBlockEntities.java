package com.afjan.juicer.registry;

import com.afjan.juicer.Juicer;
import com.afjan.juicer.block.entity.InfuserBlockEntity;
import com.afjan.juicer.block.entity.MixerBlockEntity;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, Juicer.MODID);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<MixerBlockEntity>> MIXER = BLOCK_ENTITIES.register("mixer",
            () -> new BlockEntityType<>(MixerBlockEntity::new, ModBlocks.MIXER.get()));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<InfuserBlockEntity>> INFUSER = BLOCK_ENTITIES.register("infuser",
            () -> new BlockEntityType<>(InfuserBlockEntity::new, ModBlocks.INFUSER.get()));

    private ModBlockEntities() {}
}
