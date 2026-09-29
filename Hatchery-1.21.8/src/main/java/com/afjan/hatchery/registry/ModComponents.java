package com.afjan.hatchery.registry;

import com.afjan.hatchery.Hatchery;
import com.afjan.hatchery.spawner.SpawnerModules;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredHolder;

public final class ModComponents {
    public static final DeferredRegister.DataComponents COMPONENTS =
            DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, Hatchery.MODID);

    /** On a spawner's block entity: its installed modules. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<SpawnerModules>> SPAWNER_MODULES =
            COMPONENTS.registerComponentType("spawner_modules", b -> b.persistent(SpawnerModules.CODEC));

    private ModComponents() {
    }
}
