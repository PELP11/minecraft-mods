package com.afjan.arsenal.client;

import org.jspecify.annotations.Nullable;

import com.afjan.arsenal.item.Guns;
import com.mojang.serialization.MapCodec;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.item.properties.conditional.ConditionalItemModelProperty;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/**
 * The {@code arsenal:loaded} item model property: true while the gun has a round in it. The RPG loses its warhead and
 * the railgun's coils go dark once they are empty. Inventory icons always show the loaded look, so the item stays
 * recognisable in a slot.
 */
public record LoadedProperty() implements ConditionalItemModelProperty {
    public static final MapCodec<LoadedProperty> MAP_CODEC = MapCodec.unit(new LoadedProperty());

    @Override
    public boolean get(ItemStack stack, @Nullable ClientLevel level, @Nullable LivingEntity owner, int seed,
            ItemDisplayContext displayContext) {
        if (displayContext == ItemDisplayContext.GUI || Guns.typeOf(stack) == null) {
            return true;
        }
        return Guns.dataOf(stack).ammo() > 0;
    }

    @Override
    public MapCodec<LoadedProperty> type() {
        return MAP_CODEC;
    }
}
