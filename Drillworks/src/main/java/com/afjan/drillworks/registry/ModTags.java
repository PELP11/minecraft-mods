package com.afjan.drillworks.registry;

import com.afjan.drillworks.Drillworks;

import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;

public final class ModTags {
    /** Junk the Void Filter module destroys instead of storing (cobblestone, dirt, gravel, netherrack, ...). */
    public static final TagKey<Item> VOID_FILTER = TagKey.create(Registries.ITEM, Drillworks.id("void_filter"));
    /** Drops the Smelting module leaves alone (it cooks ores, not the stone around them). */
    public static final TagKey<Item> NEVER_SMELT = TagKey.create(Registries.ITEM, Drillworks.id("never_smelt"));

    private ModTags() {}
}
