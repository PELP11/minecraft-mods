package com.afjan.arsenal.client;

import org.jspecify.annotations.Nullable;

import com.afjan.arsenal.combat.RailCharge;
import com.afjan.arsenal.gun.GunType;
import com.afjan.arsenal.item.Guns;
import com.mojang.serialization.MapCodec;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.item.properties.numeric.RangeSelectItemModelProperty;
import net.minecraft.world.entity.ItemOwner;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * The {@code arsenal:charge} item model property: how full the holder's railgun capacitors are (0..1). The railgun's
 * item definition steps through five models with it, lighting the capacitor meter on its sides segment by segment.
 * The local player's charge comes straight from the trigger; everyone else's from the synced charge start.
 */
public record ChargeProperty() implements RangeSelectItemModelProperty {
    public static final MapCodec<ChargeProperty> MAP_CODEC = MapCodec.unit(new ChargeProperty());

    @Override
    public float get(ItemStack stack, @Nullable ClientLevel level, @Nullable ItemOwner owner, int seed) {
        GunType type = Guns.typeOf(stack);
        if (type == null || !type.charges() || owner == null || !(owner.asLivingEntity() instanceof Player player)) {
            return 0.0F;
        }
        if (player == Minecraft.getInstance().player) {
            return ArsenalClient.localCharge();
        }
        return RailCharge.chargeOf(player, 0.0F);
    }

    @Override
    public MapCodec<ChargeProperty> type() {
        return MAP_CODEC;
    }
}
