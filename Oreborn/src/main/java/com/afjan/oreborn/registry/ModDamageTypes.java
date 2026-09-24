package com.afjan.oreborn.registry;

import org.jspecify.annotations.Nullable;

import com.afjan.oreborn.Oreborn;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;

/** Damage types of the abilities (defined in data/oreborn/damage_type, with their own death messages). */
public final class ModDamageTypes {
    public static final ResourceKey<DamageType> FROSTBITE = key("frostbite");
    public static final ResourceKey<DamageType> ELECTROCUTION = key("electrocution");
    public static final ResourceKey<DamageType> COMBUSTION = key("combustion");
    /** Lightning Staff: ignores the hurt cooldown (hits 5 times a second) and doesn't knock back. */
    public static final ResourceKey<DamageType> FORCE_LIGHTNING = key("force_lightning");

    private ModDamageTypes() {}

    private static ResourceKey<DamageType> key(String name) {
        return ResourceKey.create(Registries.DAMAGE_TYPE, Oreborn.id(name));
    }

    public static DamageSource source(ServerLevel level, ResourceKey<DamageType> type, @Nullable Entity attacker) {
        return new DamageSource(level.registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE).getOrThrow(type), attacker);
    }
}
