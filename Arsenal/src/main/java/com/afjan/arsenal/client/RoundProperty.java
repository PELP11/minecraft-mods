package com.afjan.arsenal.client;

import org.jspecify.annotations.Nullable;

import com.afjan.arsenal.gun.GunType;
import com.afjan.arsenal.item.Guns;
import com.mojang.serialization.MapCodec;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.item.properties.numeric.RangeSelectItemModelProperty;
import net.minecraft.world.entity.ItemOwner;
import net.minecraft.world.item.ItemStack;

/**
 * The {@code arsenal:round} item model property: which of the gun's cartridges is loaded, counting from 1 (0 for
 * anything that is not a gun). The RPG shows the warhead of the rocket in its tube with it.
 */
public record RoundProperty() implements RangeSelectItemModelProperty {
    public static final MapCodec<RoundProperty> MAP_CODEC = MapCodec.unit(new RoundProperty());

    @Override
    public float get(ItemStack stack, @Nullable ClientLevel level, @Nullable ItemOwner owner, int seed) {
        GunType type = Guns.typeOf(stack);
        return type == null ? 0.0F : type.calibers().indexOf(Guns.dataOf(stack).caliber()) + 1.0F;
    }

    @Override
    public MapCodec<RoundProperty> type() {
        return MAP_CODEC;
    }
}
