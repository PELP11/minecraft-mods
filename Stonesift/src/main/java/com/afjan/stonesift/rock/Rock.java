package com.afjan.stonesift.rock;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.jetbrains.annotations.Nullable;

import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The rock types and their mineral profiles. A weight is the expected number of units one gravel gives in the hand
 * sieve (64 cobblestone = 128 gravel -> 16 iron fragments = 4 raw iron). The first entry is the main yield.
 */
public enum Rock {
    COBBLESTONE(List.of(Blocks.COBBLESTONE, Blocks.STONE), p(Resource.IRON, 0.125, Resource.COAL, 0.08, Resource.COPPER, 0.03)),
    ANDESITE(List.of(Blocks.ANDESITE), p(Resource.IRON, 0.25, Resource.COAL, 0.03)),
    GRANITE(List.of(Blocks.GRANITE), p(Resource.COPPER, 0.25, Resource.GOLD, 0.03)),
    DIORITE(List.of(Blocks.DIORITE), p(Resource.QUARTZ, 0.12, Resource.LAPIS, 0.03)),
    TUFF(List.of(Blocks.TUFF), p(Resource.GOLD, 0.15, Resource.EMERALD, 0.03)),
    DEEPSLATE(List.of(Blocks.DEEPSLATE, Blocks.COBBLED_DEEPSLATE), p(Resource.REDSTONE, 0.15, Resource.LAPIS, 0.10, Resource.DIAMOND, 0.03)),
    BLACKSTONE(List.of(Blocks.BLACKSTONE), p(Resource.GOLD, 0.12, Resource.QUARTZ, 0.12, Resource.NETHERITE, 0.012));

    private final List<Block> blocks;
    private final Map<Resource, Double> profile;

    Rock(List<Block> blocks, Map<Resource, Double> profile) {
        this.blocks = blocks;
        this.profile = profile;
    }

    private static Map<Resource, Double> p(Object... pairs) {
        Map<Resource, Double> map = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            map.put((Resource) pairs[i], (Double) pairs[i + 1]);
        }
        return map;
    }

    public String key() {
        return this.name().toLowerCase(Locale.ROOT);
    }

    /** The block the rock former and the hand-placed pattern use as "this rock". */
    public Block block() {
        return this.blocks.get(0);
    }

    public Map<Resource, Double> profile() {
        return this.profile;
    }

    /** Main yield (first entry). */
    public Resource main() {
        return this.profile.keySet().iterator().next();
    }

    public double weight(Resource resource) {
        return this.profile.getOrDefault(resource, 0.0);
    }

    public static @Nullable Rock of(BlockState state) {
        return of(state.getBlock());
    }

    public static @Nullable Rock of(Block block) {
        for (Rock rock : values()) {
            if (rock.blocks.contains(block)) {
                return rock;
            }
        }
        return null;
    }

    public static @Nullable Rock of(ItemStack stack) {
        return stack.getItem() instanceof BlockItem item ? of(item.getBlock()) : null;
    }

    public static Rock byOrdinal(int i) {
        Rock[] v = values();
        return v[Math.floorMod(i, v.length)];
    }
}
