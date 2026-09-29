package com.afjan.tempered.mixin;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Self-loading crossbows load exactly like a finished manual reload. */
@Mixin(CrossbowItem.class)
public interface CrossbowItemInvoker {
    @Invoker("tryLoadProjectiles")
    static boolean tempered$tryLoadProjectiles(LivingEntity shooter, ItemStack heldItem) {
        throw new AssertionError();
    }
}
