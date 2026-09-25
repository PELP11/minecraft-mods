package com.afjan.stonesift.registry;

import com.afjan.stonesift.Stonesift;
import com.mojang.serialization.Codec;

import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModComponents {
    public static final DeferredRegister.DataComponents COMPONENTS =
            DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, Stonesift.MODID);

    /** Rich material from the Deep Drill: triple yield at every stage. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Boolean>> RICH =
            COMPONENTS.registerComponentType("rich", b -> b.persistent(Codec.BOOL).networkSynchronized(ByteBufCodecs.BOOL));

    private ModComponents() {}
}
