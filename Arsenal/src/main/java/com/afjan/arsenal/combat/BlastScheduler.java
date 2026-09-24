package com.afjan.arsenal.combat;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import com.afjan.arsenal.registry.ModSounds;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Carves very large craters without freezing the server. A 200 block blast is roughly five million blocks, so the
 * work is spread over many ticks: the crater is eaten one ring at a time, outwards from ground zero, with a fixed
 * block budget per tick. The visible result is a shockwave that races out over half a minute.
 *
 * <p>Also holds a small delayed-task queue, which is how the nuke's countdown and the ion cannon's walking beam are
 * timed.
 */
public final class BlastScheduler {
    /** Blocks removed per tick across all running blasts. Lower this if a nuke ever makes the server stutter. */
    public static final int BLOCKS_PER_TICK = 24000;
    /** ...and at most this long per tick, which leaves half of the 50 ms tick for everything else. */
    private static final long TIME_PER_TICK_NANOS = 25_000_000L;
    private static final int UPDATE_FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE | Block.UPDATE_SUPPRESS_DROPS;

    private static final List<Crater> CRATERS = new ArrayList<>();
    private static final List<Delayed> DELAYED = new ArrayList<>();

    private BlastScheduler() {}

    /**
     * @param radius horizontal reach in blocks
     * @param depth  how far below ground zero the crater digs at its deepest point
     * @param dome   how far above ground zero everything is swept away
     */
    public static void crater(ServerLevel level, BlockPos center, int radius, int depth, int dome, float fireChance) {
        CRATERS.add(new Crater(level, center, radius, depth, dome, fireChance));
    }

    public static void after(ServerLevel level, int ticks, Runnable action) {
        DELAYED.add(new Delayed(level, ticks, action));
    }

    public static boolean busy() {
        return !CRATERS.isEmpty();
    }

    /** Test hook: drop everything still queued. */
    public static void clear() {
        CRATERS.clear();
        DELAYED.clear();
    }

    /** Runs the whole crater immediately instead of over time; only used by the game tests. */
    public static void finishNow() {
        while (!CRATERS.isEmpty()) {
            tickCraters(Integer.MAX_VALUE, Long.MAX_VALUE);
        }
    }

    public static void tick() {
        if (!DELAYED.isEmpty()) {
            // Take the due tasks out first and run them afterwards: tasks schedule their own next step (the nuke's
            // countdown, the singularity's pull, lingering clouds), and adding to the list while walking it throws.
            List<Delayed> due = new ArrayList<>();
            for (Iterator<Delayed> it = DELAYED.iterator(); it.hasNext();) {
                Delayed delayed = it.next();
                if (--delayed.ticks <= 0) {
                    it.remove();
                    due.add(delayed);
                }
            }
            for (Delayed delayed : due) {
                delayed.action.run();
            }
        }
        if (!CRATERS.isEmpty()) {
            tickCraters(BLOCKS_PER_TICK, System.nanoTime() + TIME_PER_TICK_NANOS);
        }
    }

    /** Stops at whichever comes first: the block budget or the time budget (slower machines stay responsive). */
    private static void tickCraters(int budget, long deadline) {
        int share = Math.max(1, budget / CRATERS.size());
        // a copy, for the same reason: anything a crater triggers may start another one
        for (Crater crater : List.copyOf(CRATERS)) {
            if (crater.advance(share, deadline)) {
                CRATERS.remove(crater);
            }
        }
    }

    private static final class Delayed {
        private final ServerLevel level;
        private int ticks;
        private final Runnable action;

        private Delayed(ServerLevel level, int ticks, Runnable action) {
            this.level = level;
            this.ticks = ticks;
            this.action = action;
        }
    }

    private static final class Crater {
        private final ServerLevel level;
        private final int cx;
        private final int cy;
        private final int cz;
        private final int radius;
        private final int depth;
        private final int dome;
        private final float fireChance;
        private final BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

        private int ring;
        private int[] offsets;
        private int index;

        private Crater(ServerLevel level, BlockPos center, int radius, int depth, int dome, float fireChance) {
            this.level = level;
            this.cx = center.getX();
            this.cy = center.getY();
            this.cz = center.getZ();
            this.radius = radius;
            this.depth = depth;
            this.dome = dome;
            this.fireChance = fireChance;
            this.offsets = ringOffsets(0);
        }

        /** @return true when this crater is finished. */
        private boolean advance(int budget, long deadline) {
            RandomSource random = this.level.getRandom();
            while (budget > 0 && System.nanoTime() < deadline) {
                if (this.index >= this.offsets.length) {
                    if (++this.ring > this.radius) {
                        return true;
                    }
                    this.offsets = ringOffsets(this.ring);
                    this.index = 0;
                    this.announce(random);
                    continue;
                }
                int packed = this.offsets[this.index++];
                int dx = (short) (packed >> 16);
                int dz = (short) (packed & 0xFFFF);
                double distance = Math.sqrt((double) dx * dx + (double) dz * dz);
                if (distance > this.radius) {
                    continue;
                }
                budget -= this.clearColumn(this.cx + dx, this.cz + dz, distance, random);
            }
            return false;
        }

        private int clearColumn(int x, int z, double distance, RandomSource random) {
            if (!this.level.hasChunkAt(this.cursor.set(x, this.cy, z))) {
                return 1;
            }
            double falloff = distance / this.radius;
            int bottom = Math.max(this.level.getMinY() + 1,
                    this.cy - (int) Math.round(this.depth * Math.sqrt(Math.max(0.0, 1.0 - falloff * falloff))));
            int ceiling = this.cy + (int) Math.round(this.dome * (1.0 - 0.6 * falloff));
            int surface = this.level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
            int top = Math.min(Math.min(ceiling, surface), this.level.getMaxY());

            int touched = 1;
            int lastSolid = Integer.MIN_VALUE;
            for (int y = bottom; y <= top; y++) {
                this.cursor.set(x, y, z);
                BlockState state = this.level.getBlockState(this.cursor);
                if (state.isAir()) {
                    continue;
                }
                touched++;
                if (state.is(BlockTags.WITHER_IMMUNE)) {
                    continue;
                }
                this.level.setBlock(this.cursor, Blocks.AIR.defaultBlockState(), UPDATE_FLAGS);
                lastSolid = y;
            }
            if (this.fireChance > 0.0F && lastSolid > Integer.MIN_VALUE && random.nextFloat() < this.fireChance) {
                this.cursor.set(x, bottom, z);
                if (this.level.getBlockState(this.cursor.below()).isSolidRender()) {
                    this.level.setBlock(this.cursor, Blocks.FIRE.defaultBlockState(), UPDATE_FLAGS);
                }
            }
            return touched;
        }

        /** A rolling boom and a wall of fireballs travelling out with the shock front. */
        private void announce(RandomSource random) {
            if (this.ring % 6 != 0) {
                return;
            }
            for (int i = 0; i < 4; i++) {
                double angle = random.nextDouble() * Math.PI * 2.0;
                double px = this.cx + Math.cos(angle) * this.ring;
                double pz = this.cz + Math.sin(angle) * this.ring;
                double py = this.level.getHeight(Heightmap.Types.MOTION_BLOCKING, (int) px, (int) pz);
                this.level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, px, py + 2.0, pz, 1, 0.0, 0.0, 0.0, 0.0);
            }
            if (this.ring % 24 == 0) {
                // the shockwave rumbling outwards, heard from the edge of the crater it is carving
                double angle = random.nextDouble() * Math.PI * 2.0;
                ModSounds.broadcast(this.level, null, this.cx + Math.cos(angle) * this.ring, this.cy,
                        this.cz + Math.sin(angle) * this.ring, ModSounds.EXPLOSION_DISTANT, null, SoundSource.BLOCKS,
                        0.85F + random.nextFloat() * 0.2F);
            }
        }

        /** Every (dx, dz) at Chebyshev distance {@code r}, packed two shorts to an int. */
        private static int[] ringOffsets(int r) {
            if (r == 0) {
                return new int[] { 0 };
            }
            int[] offsets = new int[8 * r];
            int at = 0;
            for (int i = -r; i <= r; i++) {
                offsets[at++] = pack(i, -r);
                offsets[at++] = pack(i, r);
            }
            for (int j = -r + 1; j <= r - 1; j++) {
                offsets[at++] = pack(-r, j);
                offsets[at++] = pack(r, j);
            }
            return offsets;
        }

        private static int pack(int x, int z) {
            return (x << 16) | (z & 0xFFFF);
        }
    }
}
