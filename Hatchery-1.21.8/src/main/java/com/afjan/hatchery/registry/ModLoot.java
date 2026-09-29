package com.afjan.hatchery.registry;

import com.afjan.hatchery.Hatchery;
import com.afjan.hatchery.loot.ModuleRefundModifier;
import com.afjan.hatchery.loot.SpawnEggModifier;
import com.mojang.serialization.MapCodec;
import net.neoforged.neoforge.common.loot.IGlobalLootModifier;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import net.neoforged.neoforge.registries.DeferredHolder;

public final class ModLoot {
    public static final DeferredRegister<MapCodec<? extends IGlobalLootModifier>> MODIFIERS =
            DeferredRegister.create(NeoForgeRegistries.Keys.GLOBAL_LOOT_MODIFIER_SERIALIZERS, Hatchery.MODID);

    public static final DeferredHolder<MapCodec<? extends IGlobalLootModifier>, MapCodec<SpawnEggModifier>> SPAWN_EGG = MODIFIERS.register("spawn_egg", () -> SpawnEggModifier.CODEC);
    public static final DeferredHolder<MapCodec<? extends IGlobalLootModifier>, MapCodec<ModuleRefundModifier>> MODULE_REFUND = MODIFIERS.register("module_refund", () -> ModuleRefundModifier.CODEC);

    private ModLoot() {
    }
}
