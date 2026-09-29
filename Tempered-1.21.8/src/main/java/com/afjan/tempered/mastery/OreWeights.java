package com.afjan.tempered.mastery;

import com.afjan.tempered.registry.ModTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * How many blocks one mined block counts as for "Mine X blocks": 1, but ores count by rarity
 * (coal 2 ... diamond 10, Ancient Debris 16). Only that counter is weighted; "Mine X ores" still counts ores.
 */
public final class OreWeights {
    private OreWeights() {
    }

    public static int of(BlockState state) {
        if (!state.is(ModTags.ORES)) return 1;
        if (state.is(Blocks.ANCIENT_DEBRIS)) return 16;
        if (state.is(net.minecraft.tags.BlockTags.EMERALD_ORES)) return 12;
        if (state.is(net.minecraft.tags.BlockTags.DIAMOND_ORES)) return 10;
        if (state.is(Blocks.NETHER_GOLD_ORE)) return 3;
        if (state.is(net.minecraft.tags.BlockTags.GOLD_ORES) || state.is(net.minecraft.tags.BlockTags.LAPIS_ORES)) return 6;
        if (state.is(net.minecraft.tags.BlockTags.IRON_ORES) || state.is(net.minecraft.tags.BlockTags.REDSTONE_ORES)) return 4;
        if (state.is(net.minecraft.tags.BlockTags.COAL_ORES) || state.is(net.minecraft.tags.BlockTags.COPPER_ORES)
                || state.is(Blocks.NETHER_QUARTZ_ORE)) return 2;
        return 3; // other (modded) ores
    }
}
