package com.afjan.arsenal.registry;

import org.jspecify.annotations.Nullable;

import com.afjan.arsenal.Arsenal;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;

/** Damage types with their own death messages (defined in {@code data/arsenal/damage_type}). */
public final class ModDamageTypes {
    public static final ResourceKey<DamageType> BULLET = key("bullet");
    public static final ResourceKey<DamageType> HEADSHOT = key("headshot");
    public static final ResourceKey<DamageType> BUCKSHOT = key("buckshot");
    public static final ResourceKey<DamageType> RAILGUN = key("railgun");
    public static final ResourceKey<DamageType> SHRAPNEL = key("shrapnel");
    public static final ResourceKey<DamageType> BLAST = key("blast");
    public static final ResourceKey<DamageType> RADIATION = key("radiation");
    public static final ResourceKey<DamageType> NERVE_AGENT = key("nerve_agent");
    public static final ResourceKey<DamageType> SINGULARITY = key("singularity");
    public static final ResourceKey<DamageType> ION_BEAM = key("ion_beam");
    public static final ResourceKey<DamageType> NUKE = key("nuke");
    /** The F-14's 20 mm cannon. */
    public static final ResourceKey<DamageType> CANNON = key("cannon");
    /** A missile's warhead going off next to you. */
    public static final ResourceKey<DamageType> MISSILE = key("missile");

    private ModDamageTypes() {}

    private static ResourceKey<DamageType> key(String name) {
        return ResourceKey.create(Registries.DAMAGE_TYPE, Arsenal.id(name));
    }

    public static DamageSource source(ServerLevel level, ResourceKey<DamageType> type, @Nullable Entity attacker) {
        return new DamageSource(level.registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE).getOrThrow(type), attacker);
    }
}
