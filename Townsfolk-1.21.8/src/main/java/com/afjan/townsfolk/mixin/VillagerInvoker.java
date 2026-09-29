package com.afjan.townsfolk.mixin;

import net.minecraft.world.entity.npc.Villager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Grants a villager its next level exactly like vanilla does after a trade (new level + that level's trades). */
@Mixin(Villager.class)
public interface VillagerInvoker {
    @Invoker("increaseMerchantCareer")
    void townsfolk$increaseMerchantCareer();
}
