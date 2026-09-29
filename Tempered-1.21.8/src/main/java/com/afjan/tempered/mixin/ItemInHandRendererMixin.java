package com.afjan.tempered.mixin;

import com.afjan.tempered.registry.ModComponents;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Mastery counters change on every block mined: a change to them alone never replays the held tool's re-equip dip. */
@Mixin(ItemInHandRenderer.class)
public abstract class ItemInHandRendererMixin {
    @Inject(method = "shouldInstantlyReplaceVisibleItem", at = @At("HEAD"), cancellable = true)
    private void tempered$ignoreMastery(ItemStack oldItem, ItemStack newItem, CallbackInfoReturnable<Boolean> cir) {
        if (oldItem.isEmpty() || newItem.isEmpty() || !oldItem.is(newItem.getItem())) return;
        if (!oldItem.has(ModComponents.MASTERY.get()) && !newItem.has(ModComponents.MASTERY.get())) return;
        ItemStack a = oldItem.copy();
        ItemStack b = newItem.copy();
        a.remove(ModComponents.MASTERY.get());
        b.remove(ModComponents.MASTERY.get());
        if (ItemStack.matches(a, b)) cir.setReturnValue(true);
    }
}
