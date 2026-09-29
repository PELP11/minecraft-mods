package com.afjan.tempered.registry;

import com.afjan.tempered.Tempered;
import com.afjan.tempered.mastery.Mastery;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

public final class ModComponents {
    public static final DeferredRegister<DataComponentType<?>> COMPONENTS =
            DeferredRegister.create(Registries.DATA_COMPONENT_TYPE, Tempered.MODID);

    /** Counters change on every block mined: ignoreSwapAnimation keeps the held tool from dipping each time. */
    public static final RegistryObject<DataComponentType<Mastery>> MASTERY = COMPONENTS.register("mastery",
            () -> DataComponentType.<Mastery>builder()
                    .persistent(Mastery.CODEC)
                    .networkSynchronized(Mastery.STREAM_CODEC)
                    .ignoreSwapAnimation()
                    .build());

    private ModComponents() {
    }
}
