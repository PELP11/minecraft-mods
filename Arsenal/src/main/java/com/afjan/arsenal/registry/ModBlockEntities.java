package com.afjan.arsenal.registry;

import com.afjan.arsenal.Arsenal;
import com.afjan.arsenal.block.entity.WeaponWorkbenchBlockEntity;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, Arsenal.MODID);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<WeaponWorkbenchBlockEntity>> WEAPON_WORKBENCH =
            BLOCK_ENTITIES.register("weapon_workbench",
                    () -> new BlockEntityType<>(WeaponWorkbenchBlockEntity::new, ModBlocks.WEAPON_WORKBENCH.get()));

    private ModBlockEntities() {}
}
