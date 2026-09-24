package com.afjan.juicer.block;

import java.util.Locale;

import net.minecraft.util.StringRepresentable;

/** How a piece of tubing connects on one side. MACHINE arms reach a little further to meet the machine body. */
public enum TubeConnection implements StringRepresentable {
    NONE,
    TUBE,
    MACHINE;

    public boolean isConnected() {
        return this != NONE;
    }

    @Override
    public String getSerializedName() {
        return this.name().toLowerCase(Locale.ROOT);
    }
}
