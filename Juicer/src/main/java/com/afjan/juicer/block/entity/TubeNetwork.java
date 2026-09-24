package com.afjan.juicer.block.entity;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.afjan.juicer.block.TubingBlock;
import com.afjan.juicer.fruit.Fruit;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;

/**
 * Juice Tubing has no logic of its own: a Fruit Mixer walks the connected tubes (breadth-first) and pushes its
 * concentrate into every Juice Infuser it can reach. Infusers directly next to a mixer are fed as well.
 */
public final class TubeNetwork {
    /** Upper bound on tubes visited per search, so huge or looping networks stay cheap. */
    public static final int MAX_TUBES = 512;

    private TubeNetwork() {}

    /** @return the amount of concentrate that was moved out of {@code source}. */
    public static int pushFrom(Level level, BlockPos sourcePos, ConcentrateTank source, int maxAmount) {
        Fruit fruit = source.fruit();
        if (fruit == null || maxAmount <= 0) {
            return 0;
        }
        int budget = Math.min(maxAmount, source.amount());
        int moved = 0;
        for (InfuserBlockEntity target : findInfusers(level, sourcePos)) {
            if (budget <= 0) {
                break;
            }
            int accepted = target.receiveConcentrate(fruit, budget);
            budget -= accepted;
            moved += accepted;
        }
        if (moved > 0) {
            source.drain(moved);
        }
        return moved;
    }

    public static List<InfuserBlockEntity> findInfusers(Level level, BlockPos start) {
        List<InfuserBlockEntity> found = new ArrayList<>();
        Set<BlockPos> visited = new HashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        visited.add(start);
        queue.add(start);
        int tubes = 0;
        while (!queue.isEmpty()) {
            BlockPos current = queue.poll();
            for (Direction direction : Direction.values()) {
                BlockPos next = current.relative(direction);
                if (!visited.add(next) || !level.isLoaded(next)) {
                    continue;
                }
                if (level.getBlockState(next).getBlock() instanceof TubingBlock) {
                    if (++tubes > MAX_TUBES) {
                        return found;
                    }
                    queue.add(next);
                } else if (level.getBlockEntity(next) instanceof InfuserBlockEntity infuser) {
                    found.add(infuser);
                }
            }
        }
        return found;
    }
}
