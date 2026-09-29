package com.afjan.tempered.registry;

import com.afjan.tempered.Tempered;
import com.afjan.tempered.loot.MasteryLootModifier;
import com.mojang.serialization.MapCodec;
import net.neoforged.neoforge.common.loot.IGlobalLootModifier;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

public final class ModLoot {
    public static final DeferredRegister<MapCodec<? extends IGlobalLootModifier>> MODIFIERS =
            DeferredRegister.create(NeoForgeRegistries.Keys.GLOBAL_LOOT_MODIFIER_SERIALIZERS, Tempered.MODID);

    public static final DeferredHolder<MapCodec<? extends IGlobalLootModifier>, MapCodec<MasteryLootModifier>> MASTERY =
            MODIFIERS.register("mastery", () -> MasteryLootModifier.CODEC);

    private ModLoot() {
    }
}
