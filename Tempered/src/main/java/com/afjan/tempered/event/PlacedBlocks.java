package com.afjan.tempered.event;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.CocoaBlock;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.NetherWartBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Remembers blocks players placed so "place a block, mine it, repeat" earns nothing. Only in memory
 * (a restart forgets it), capped per dimension. Plants are not tracked: harvesting what you planted counts.
 */
public final class PlacedBlocks {
    private static final int CAP = 250_000;
    private static final Map<ResourceKey<Level>, LongOpenHashSet> PLACED = new HashMap<>();
    /** Placed blocks broken this tick: their drops (rolled a moment later) get no bonus. */
    private static final Set<String> BROKEN_THIS_TICK = new HashSet<>();

    private PlacedBlocks() {
    }

    public static synchronized void placed(Level level, BlockPos pos, BlockState state) {
        if (state.getDestroySpeed(level, pos) == 0.0F || state.is(BlockTags.SAPLINGS)) return;
        if (state.getBlock() instanceof CropBlock || state.getBlock() instanceof NetherWartBlock || state.getBlock() instanceof CocoaBlock) return;
        LongOpenHashSet set = PLACED.computeIfAbsent(level.dimension(), k -> new LongOpenHashSet());
        if (set.size() >= CAP) set.clear();
        set.add(pos.asLong());
    }

    /** True if a player placed the block here; forgets it (the block is being broken). */
    public static synchronized boolean consume(Level level, BlockPos pos) {
        LongOpenHashSet set = PLACED.get(level.dimension());
        boolean placed = set != null && set.remove(pos.asLong());
        if (placed) BROKEN_THIS_TICK.add(key(level, pos));
        return placed;
    }

    /** The block whose drops are being rolled was player-placed (checked by the loot modifier). */
    public static synchronized boolean wasJustBroken(Level level, BlockPos pos) {
        return BROKEN_THIS_TICK.contains(key(level, pos));
    }

    public static synchronized boolean contains(Level level, BlockPos pos) {
        LongOpenHashSet set = PLACED.get(level.dimension());
        return set != null && set.contains(pos.asLong());
    }

    public static synchronized void endTick() {
        BROKEN_THIS_TICK.clear();
    }

    public static synchronized void clear() {
        PLACED.clear();
        BROKEN_THIS_TICK.clear();
    }

    private static String key(Level level, BlockPos pos) {
        return level.dimension().identifier() + "@" + pos.asLong();
    }
}
