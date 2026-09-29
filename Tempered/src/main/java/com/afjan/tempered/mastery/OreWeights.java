package com.afjan.tempered.mastery;

import com.afjan.tempered.registry.ModTags;
import net.minecraft.tags.BlockItemTags;
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
        if (state.is(BlockItemTags.EMERALD_ORES.block())) return 12;
        if (state.is(BlockItemTags.DIAMOND_ORES.block())) return 10;
        if (state.is(Blocks.NETHER_GOLD_ORE)) return 3;
        if (state.is(BlockItemTags.GOLD_ORES.block()) || state.is(BlockItemTags.LAPIS_ORES.block())) return 6;
        if (state.is(BlockItemTags.IRON_ORES.block()) || state.is(BlockItemTags.REDSTONE_ORES.block())) return 4;
        if (state.is(BlockItemTags.COAL_ORES.block()) || state.is(BlockItemTags.COPPER_ORES.block())
                || state.is(Blocks.NETHER_QUARTZ_ORE)) return 2;
        return 3; // other (modded) ores
    }
}
