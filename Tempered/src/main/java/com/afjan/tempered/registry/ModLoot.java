package com.afjan.tempered.registry;

import com.afjan.tempered.Tempered;
import com.afjan.tempered.loot.MasteryLootModifier;
import com.mojang.serialization.MapCodec;
import net.minecraftforge.common.loot.IGlobalLootModifier;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModLoot {
    public static final DeferredRegister<MapCodec<? extends IGlobalLootModifier>> MODIFIERS =
            DeferredRegister.create(ForgeRegistries.Keys.GLOBAL_LOOT_MODIFIER_SERIALIZERS, Tempered.MODID);

    public static final RegistryObject<MapCodec<MasteryLootModifier>> MASTERY = MODIFIERS.register("mastery", () -> MasteryLootModifier.CODEC);

    private ModLoot() {
    }
}
