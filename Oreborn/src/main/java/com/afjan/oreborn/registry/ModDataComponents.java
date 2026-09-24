package com.afjan.oreborn.registry;

import com.afjan.oreborn.Oreborn;
import com.mojang.serialization.Codec;

import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModDataComponents {
    public static final DeferredRegister.DataComponents COMPONENTS = DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, Oreborn.MODID);

    /** Umbrium pickaxe: index into {@link com.afjan.oreborn.item.MiningMode}. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> MINING_MODE = COMPONENTS.registerComponentType("mining_mode",
            builder -> builder.persistent(Codec.INT).networkSynchronized(ByteBufCodecs.VAR_INT));

    private ModDataComponents() {}
}
