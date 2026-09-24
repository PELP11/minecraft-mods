package com.afjan.drillworks.drill;

import com.afjan.drillworks.Drillworks;

import net.minecraft.core.registries.Registries;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;

/**
 * The seven drill heads. Durability counts blocks drilled, speed multiplies the bore rate, sockets is how many
 * modules the head takes, and each material but iron and diamond has a trait of its own.
 */
public enum HeadMaterial {
    STONE("stone", 1500, 1.0F, 1, BlockTags.INCORRECT_FOR_STONE_TOOL, Trait.NONE),
    COPPER("copper", 2500, 1.3F, 1, BlockTags.INCORRECT_FOR_STONE_TOOL, Trait.CONDUCTIVE),
    IRON("iron", 5000, 1.7F, 2, BlockTags.INCORRECT_FOR_IRON_TOOL, Trait.NONE),
    GOLDEN("golden", 1800, 3.2F, 3, BlockTags.INCORRECT_FOR_IRON_TOOL, Trait.LUCKY),
    AMETHYST("amethyst", 4000, 2.0F, 2, BlockTags.INCORRECT_FOR_IRON_TOOL, Trait.RESONANT),
    DIAMOND("diamond", 12000, 2.6F, 3, BlockTags.INCORRECT_FOR_DIAMOND_TOOL, Trait.NONE),
    NETHERITE("netherite", 25000, 3.4F, 4, BlockTags.INCORRECT_FOR_NETHERITE_TOOL, Trait.MOLTEN);

    public enum Trait {
        NONE, CONDUCTIVE, LUCKY, RESONANT, MOLTEN;

        public String key() {
            return this.name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    private final String path;
    private final int durability;
    private final float speed;
    private final int sockets;
    private final TagKey<Block> incorrect;
    private final Trait trait;
    private final TagKey<Item> repair;

    HeadMaterial(String path, int durability, float speed, int sockets, TagKey<Block> incorrect, Trait trait) {
        this.path = path;
        this.durability = durability;
        this.speed = speed;
        this.sockets = sockets;
        this.incorrect = incorrect;
        this.trait = trait;
        this.repair = TagKey.create(Registries.ITEM, Drillworks.id("repairs_" + path + "_drill_head"));
    }

    public String itemName() {
        return this.path + "_drill_head";
    }

    public int durability() {
        return this.durability;
    }

    public float speed() {
        return this.speed;
    }

    public int sockets() {
        return this.sockets;
    }

    public TagKey<Block> incorrectBlocks() {
        return this.incorrect;
    }

    public Trait trait() {
        return this.trait;
    }

    public TagKey<Item> repairTag() {
        return this.repair;
    }
}
