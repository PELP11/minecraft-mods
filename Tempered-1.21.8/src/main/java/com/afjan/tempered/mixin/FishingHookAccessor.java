package com.afjan.tempered.mixin;

import net.minecraft.world.entity.projectile.FishingHook;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Lure and Luck of the Sea are final fields set from the rod when the hook is cast. */
@Mixin(FishingHook.class)
public interface FishingHookAccessor {
    @Accessor("luck")
    int tempered$getLuck();

    @Mutable
    @Accessor("luck")
    void tempered$setLuck(int luck);

    @Accessor("lureSpeed")
    int tempered$getLureSpeed();

    @Mutable
    @Accessor("lureSpeed")
    void tempered$setLureSpeed(int lureSpeed);
}
