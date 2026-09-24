package com.afjan.drillworks.registry;

import com.afjan.drillworks.Drillworks;
import com.afjan.drillworks.drill.DrillModules;
import com.mojang.serialization.Codec;

import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModComponents {
    public static final DeferredRegister.DataComponents COMPONENTS =
            DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, Drillworks.MODID);

    /** The modules socketed into a drill head. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<DrillModules>> MODULES =
            COMPONENTS.registerComponentType("modules", builder -> builder
                    .persistent(DrillModules.CODEC)
                    .networkSynchronized(DrillModules.STREAM_CODEC));

    /** Gasoline left in a Mining Drill that was packed back into its item (mB). */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> FUEL =
            COMPONENTS.registerComponentType("fuel", builder -> builder
                    .persistent(Codec.INT)
                    .networkSynchronized(ByteBufCodecs.VAR_INT));

    private ModComponents() {}
}
