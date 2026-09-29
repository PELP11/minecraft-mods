package com.afjan.tempered.registry;

import com.afjan.tempered.Tempered;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;

public final class ModTags {
    /** Tools and weapons that become Broken at 0 durability instead of disappearing. */
    public static final TagKey<Item> KEEP_WHEN_BROKEN = item("keep_when_broken");
    /** Fishing catches that count as treasure. */
    public static final TagKey<Item> FISHING_TREASURE = item("fishing_treasure");

    public static final TagKey<Block> ORES = block("ores");
    public static final TagKey<Block> GEM_ORES = block("gem_ores");
    public static final TagKey<Block> OBSIDIAN = block("obsidian");
    public static final TagKey<Block> DEBRIS = block("debris");
    public static final TagKey<Block> GRAVEL = block("gravel");
    public static final TagKey<Block> CLAY = block("clay");
    public static final TagKey<Block> SNOW = block("snow");
    public static final TagKey<Block> SOUL = block("soul");
    public static final TagKey<Block> SCULK = block("sculk");
    /** Blocks around logs that prove they are a tree (Timber ignores log cabins). */
    public static final TagKey<Block> TREE_CROWN = block("tree_crown");

    public static final TagKey<EntityType<?>> ELITE = entity("elite");
    public static final TagKey<EntityType<?>> AQUATIC = entity("aquatic");

    private static TagKey<Item> item(String name) {
        return TagKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath(Tempered.MODID, name));
    }

    private static TagKey<Block> block(String name) {
        return TagKey.create(Registries.BLOCK, ResourceLocation.fromNamespaceAndPath(Tempered.MODID, name));
    }

    private static TagKey<EntityType<?>> entity(String name) {
        return TagKey.create(Registries.ENTITY_TYPE, ResourceLocation.fromNamespaceAndPath(Tempered.MODID, name));
    }

    private ModTags() {
    }
}
