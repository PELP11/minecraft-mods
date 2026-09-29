package com.afjan.tempered.ability;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;

import java.util.ArrayList;
import java.util.List;

/** Replant: harvested crops are set back to their first growth stage at the end of the tick. */
public final class Replanter {
    private record Job(ServerLevel level, BlockPos pos, BlockState state) {
    }

    private static final List<Job> JOBS = new ArrayList<>();

    private Replanter() {
    }

    public static void register() {
        TickEvent.ServerTickEvent.Post.BUS.addListener(e -> run());
        ServerStoppedEvent.BUS.addListener(e -> {
            synchronized (JOBS) {
                JOBS.clear();
            }
        });
    }

    /** The crop's state with its age reset (facing etc. are kept, e.g. cocoa on a jungle log). */
    public static BlockState seedling(BlockState grown) {
        for (Property<?> property : grown.getProperties()) {
            if (property instanceof IntegerProperty age && property.getName().equals("age")) {
                return grown.setValue(age, age.getPossibleValues().stream().min(Integer::compare).orElse(0));
            }
        }
        return grown.getBlock().defaultBlockState();
    }

    public static void queue(ServerLevel level, BlockPos pos, BlockState grown) {
        synchronized (JOBS) {
            JOBS.add(new Job(level, pos.immutable(), seedling(grown)));
        }
    }

    public static void run() {
        List<Job> due;
        synchronized (JOBS) {
            if (JOBS.isEmpty()) return;
            due = new ArrayList<>(JOBS);
            JOBS.clear();
        }
        for (Job job : due) {
            if (job.level.getBlockState(job.pos).isAir() && job.state.canSurvive(job.level, job.pos)) {
                job.level.setBlock(job.pos, job.state, Block.UPDATE_ALL);
            }
        }
    }
}
