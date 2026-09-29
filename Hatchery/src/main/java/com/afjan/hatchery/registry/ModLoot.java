package com.afjan.hatchery.registry;

import com.afjan.hatchery.Hatchery;
import com.afjan.hatchery.loot.SpawnEggModifier;
import com.mojang.serialization.MapCodec;
import net.minecraftforge.common.loot.IGlobalLootModifier;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModLoot {
    public static final DeferredRegister<MapCodec<? extends IGlobalLootModifier>> MODIFIERS =
            DeferredRegister.create(ForgeRegistries.Keys.GLOBAL_LOOT_MODIFIER_SERIALIZERS, Hatchery.MODID);

    public static final RegistryObject<MapCodec<SpawnEggModifier>> SPAWN_EGG = MODIFIERS.register("spawn_egg", () -> SpawnEggModifier.CODEC);

    private ModLoot() {
    }
}
