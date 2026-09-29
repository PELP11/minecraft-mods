package com.afjan.hatchery.registry;

import com.afjan.hatchery.Hatchery;
import com.afjan.hatchery.spawner.SpawnerModules;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

public final class ModComponents {
    public static final DeferredRegister<DataComponentType<?>> COMPONENTS =
            DeferredRegister.create(Registries.DATA_COMPONENT_TYPE, Hatchery.MODID);

    /** On a spawner's block entity: its installed modules. */
    public static final RegistryObject<DataComponentType<SpawnerModules>> SPAWNER_MODULES = COMPONENTS.register("spawner_modules",
            () -> DataComponentType.<SpawnerModules>builder().persistent(SpawnerModules.CODEC).build());

    private ModComponents() {
    }
}
