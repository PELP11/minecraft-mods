package com.afjan.oreborn.registry;

import com.afjan.oreborn.Oreborn;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;

public final class ModTags {
    /** Drops that Emberite tools smelt on the spot (raw ores, ore blocks, sand, potatoes, kelp...). */
    public static final TagKey<Item> EMBERITE_SMELTABLE = TagKey.create(Registries.ITEM, Oreborn.id("emberite_smeltable"));

    /** Everything the Molten Core pickaxe vein-mines and the Ore Radar reveals. */
    public static final TagKey<Block> ORES = common("ores");

    /** Glass and glass panes: force lightning bounces off them. */
    public static final TagKey<Block> REFLECTS_LIGHTNING = TagKey.create(Registries.BLOCK, Oreborn.id("reflects_lightning"));

    private ModTags() {}

    public static TagKey<Block> common(String path) {
        return TagKey.create(Registries.BLOCK, Identifier.fromNamespaceAndPath("c", path));
    }
}
