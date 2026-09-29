package com.afjan.hatchery.registry;

import com.afjan.hatchery.Hatchery;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;

public final class ModTags {
    /** Mobs that never drop their spawn egg (the bosses by default). */
    public static final TagKey<EntityType<?>> NO_SPAWN_EGG =
            TagKey.create(Registries.ENTITY_TYPE, ResourceLocation.fromNamespaceAndPath(Hatchery.MODID, "no_spawn_egg"));

    private ModTags() {
    }
}
