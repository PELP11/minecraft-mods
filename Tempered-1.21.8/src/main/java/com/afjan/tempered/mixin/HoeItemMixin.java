package com.afjan.tempered.mixin;

import com.afjan.tempered.event.ProgressEvents;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.HoeItem;
import net.minecraft.world.item.context.UseOnContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Counts tilling: the hoe's use returns a consuming result only when it turned the block into farmland/dirt. */
@Mixin(HoeItem.class)
public abstract class HoeItemMixin {
    @Inject(method = "useOn", at = @At("RETURN"))
    private void tempered$countTilling(UseOnContext context, CallbackInfoReturnable<InteractionResult> cir) {
        if (cir.getReturnValue().consumesAction() && !context.getLevel().isClientSide()) ProgressEvents.onTransformed(context);
    }
}
