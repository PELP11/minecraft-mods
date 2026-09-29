package com.afjan.tempered.mixin;

import com.afjan.tempered.event.ProgressEvents;
import net.minecraft.core.component.BlockTransformer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.context.UseOnContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Tilling (hoes), stripping and pathing are data-driven block transformers in 26.x: count them here. */
@Mixin(BlockTransformer.class)
public abstract class BlockTransformerMixin {
    @Inject(method = "transformBlock", at = @At("RETURN"))
    private void tempered$countTransform(UseOnContext context, CallbackInfoReturnable<InteractionResult> cir) {
        if (cir.getReturnValue().consumesAction() && !context.getLevel().isClientSide()) ProgressEvents.onTransformed(context);
    }
}
