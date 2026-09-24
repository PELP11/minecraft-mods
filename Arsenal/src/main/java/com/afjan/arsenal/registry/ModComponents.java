package com.afjan.arsenal.registry;

import com.afjan.arsenal.Arsenal;
import com.afjan.arsenal.gun.GunData;

import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModComponents {
    public static final DeferredRegister.DataComponents COMPONENTS =
            DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, Arsenal.MODID);

    /** Rounds loaded, the cartridge in the chamber and the fitted attachments. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<GunData>> GUN =
            COMPONENTS.registerComponentType("gun", builder -> builder
                    .persistent(GunData.CODEC)
                    .networkSynchronized(GunData.STREAM_CODEC));

    private ModComponents() {}
}
