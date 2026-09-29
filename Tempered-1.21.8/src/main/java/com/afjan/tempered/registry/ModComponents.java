package com.afjan.tempered.registry;

import com.afjan.tempered.Tempered;
import com.afjan.tempered.mastery.Mastery;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModComponents {
    public static final DeferredRegister.DataComponents COMPONENTS =
            DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, Tempered.MODID);

    /** Counters change on every block mined; the tools' re-equip check ignores this component (see mixin). */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Mastery>> MASTERY =
            COMPONENTS.registerComponentType("mastery", b -> b.persistent(Mastery.CODEC).networkSynchronized(Mastery.STREAM_CODEC));

    private ModComponents() {
    }
}
