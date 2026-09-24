package com.afjan.oreborn.item;

import com.afjan.oreborn.registry.ModDataComponents;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/** Umbrium pickaxe patterns, switched with Sneak + Right-click. */
public enum MiningMode {
    SINGLE("single"),
    CROSS("cross"),
    TUNNEL("tunnel"),
    EXCAVATE("excavate");

    private final String id;

    MiningMode(String id) {
        this.id = id;
    }

    public static MiningMode of(ItemStack stack) {
        int index = stack.getOrDefault(ModDataComponents.MINING_MODE.get(), 0);
        MiningMode[] modes = values();
        return index >= 0 && index < modes.length ? modes[index] : SINGLE;
    }

    public void applyTo(ItemStack stack) {
        stack.set(ModDataComponents.MINING_MODE.get(), ordinal());
    }

    public MiningMode next() {
        MiningMode[] modes = values();
        return modes[(ordinal() + 1) % modes.length];
    }

    public Component displayName() {
        return Component.translatable("mode.oreborn." + id);
    }
}
