package com.afjan.oreborn.ability;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;

import com.afjan.oreborn.item.MiningMode;
import com.afjan.oreborn.item.OrebornToolItem;
import com.afjan.oreborn.material.GearType;
import com.afjan.oreborn.registry.ModTags;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Everything that happens when an Oreborn tool breaks a block: the mining patterns, vein and tree felling, landslides,
 * Cryo Seal and Momentum. Extra blocks are broken through the player's own game mode, so drops, the Emberite and
 * Umbrium traits, durability, statistics and protection mods all apply per block.
 */
public final class AreaMining {
    public static final int VEIN_LIMIT = 48;
    public static final int TREE_LIMIT = 256;
    public static final int TUNNEL_LENGTH = 8;
    /** Pattern mining never chews through obsidian, ancient debris, reinforced deepslate and the like. */
    private static final float MAX_HARDNESS = 25.0F;

    /** Set while extra blocks are being broken, so they don't trigger patterns themselves. Server thread only. */
    private static boolean breakingExtra;
    private static final Map<UUID, Momentum> MOMENTUM = new HashMap<>();

    private static final class Momentum {
        int count;
        long lastBreak;
    }

    private AreaMining() {}

    public static boolean isBreakingExtra() {
        return breakingExtra;
    }

    /** Entry point from the block break event (before the block is removed). */
    public static void onBreak(ServerPlayer player, BlockPos pos, BlockState state) {
        if (breakingExtra) {
            return;
        }
        ItemStack tool = player.getMainHandItem();
        if (!(tool.getItem() instanceof OrebornToolItem item)) {
            return;
        }
        ServerLevel level = (ServerLevel) player.level();
        boolean sneaking = player.isShiftKeyDown();
        GearType type = item.type();
        switch (item.material()) {
            case CRYOLITE -> {
                if (type == GearType.PICKAXE) {
                    cryoSeal(level, pos);
                } else if (type == GearType.SHOVEL && !sneaking && state.is(BlockTags.MINEABLE_WITH_SHOVEL)) {
                    breakExtra(player, square(pos, hitFace(player, pos)), s -> s.is(BlockTags.MINEABLE_WITH_SHOVEL) && tool.isCorrectToolForDrops(s));
                }
            }
            case FULGURITE -> {
                momentum(player);
                if (type == GearType.SHOVEL && state.getBlock() instanceof FallingBlock) {
                    breakExtra(player, column(level, pos), s -> s.getBlock() instanceof FallingBlock);
                }
            }
            case EMBERITE -> {
                if (type == GearType.PICKAXE && !sneaking && state.is(ModTags.ORES)) {
                    List<BlockPos> vein = vein(level, pos, state);
                    if (!vein.isEmpty()) {
                        breakExtra(player, vein, s -> s.is(state.getBlock()));
                        Fx.burst(level, ParticleTypes.LAVA, Vec3.atCenterOf(pos), 6, 0.3, 0.0);
                    }
                }
            }
            case UMBRIUM -> {
                if (type == GearType.PICKAXE && !sneaking) {
                    List<BlockPos> pattern = pattern(MiningMode.of(tool), player, pos);
                    if (!pattern.isEmpty()) {
                        breakExtra(player, pattern, s -> s.is(BlockTags.MINEABLE_WITH_PICKAXE) && tool.isCorrectToolForDrops(s));
                    }
                } else if (type == GearType.AXE && !sneaking && state.is(BlockTags.LOGS)) {
                    List<BlockPos> tree = tree(level, pos);
                    if (!tree.isEmpty()) {
                        breakExtra(player, tree, s -> s.is(BlockTags.LOGS));
                        Fx.sound(level, Vec3.atCenterOf(pos), SoundEvents.ENDERMAN_TELEPORT, 0.4F, 0.6F);
                    }
                }
            }
        }
    }

    /** Breaks the given blocks as the player; stops when the tool breaks or is switched. */
    public static int breakExtra(ServerPlayer player, List<BlockPos> positions, Predicate<BlockState> filter) {
        if (breakingExtra) {
            return 0;
        }
        breakingExtra = true;
        int broken = 0;
        try {
            ItemStack tool = player.getMainHandItem();
            Level level = player.level();
            for (BlockPos pos : positions) {
                if (tool.isEmpty() || player.getMainHandItem() != tool) {
                    break;
                }
                BlockState state = level.getBlockState(pos);
                if (state.isAir() || !filter.test(state) || level.getBlockEntity(pos) != null) {
                    continue;
                }
                float hardness = state.getDestroySpeed(level, pos);
                if (hardness < 0.0F || hardness > MAX_HARDNESS || !level.mayInteract(player, pos)) {
                    continue;
                }
                if (player.gameMode.destroyBlock(pos)) {
                    broken++;
                }
            }
        } finally {
            breakingExtra = false;
        }
        return broken;
    }

    // ---- patterns ------------------------------------------------------------------------------------------------

    /** The face of the block the player is mining (falls back to the look direction). */
    public static Direction hitFace(ServerPlayer player, BlockPos pos) {
        HitResult hit = player.pick(player.blockInteractionRange() + 1.0, 1.0F, false);
        if (hit instanceof BlockHitResult blockHit && blockHit.getType() == HitResult.Type.BLOCK && blockHit.getBlockPos().equals(pos)) {
            return blockHit.getDirection();
        }
        return Direction.getApproximateNearest(player.getLookAngle()).getOpposite();
    }

    private static BlockPos inPlane(BlockPos center, Direction.Axis axis, int a, int b) {
        return switch (axis) {
            case X -> center.offset(0, a, b);
            case Y -> center.offset(a, 0, b);
            case Z -> center.offset(a, b, 0);
        };
    }

    /** The 8 blocks around {@code center} in the plane of the mined face. */
    public static List<BlockPos> square(BlockPos center, Direction face) {
        List<BlockPos> list = new ArrayList<>();
        for (int a = -1; a <= 1; a++) {
            for (int b = -1; b <= 1; b++) {
                if (a != 0 || b != 0) {
                    list.add(inPlane(center, face.getAxis(), a, b));
                }
            }
        }
        return list;
    }

    /** An X of radius 2 in the plane of the mined face (8 extra blocks). */
    public static List<BlockPos> cross(BlockPos center, Direction face) {
        List<BlockPos> list = new ArrayList<>();
        for (int d = 1; d <= 2; d++) {
            list.add(inPlane(center, face.getAxis(), d, d));
            list.add(inPlane(center, face.getAxis(), -d, d));
            list.add(inPlane(center, face.getAxis(), d, -d));
            list.add(inPlane(center, face.getAxis(), -d, -d));
        }
        return list;
    }

    /**
     * A 1 wide, 2 high strip-mining tunnel, {@link #TUNNEL_LENGTH} blocks deep, dug into a wall at the player's height.
     * Mining a floor or ceiling doesn't dig a tunnel (no accidental shafts into lava).
     */
    public static List<BlockPos> tunnel(ServerPlayer player, BlockPos pos, Direction face) {
        List<BlockPos> list = new ArrayList<>();
        if (face.getAxis() == Direction.Axis.Y) {
            return list;
        }
        Direction into = face.getOpposite();
        int feet = Mth.floor(player.getY() + 0.01);
        int low;
        if (pos.getY() == feet || pos.getY() == feet + 1) {
            low = feet;
        } else {
            low = pos.getY() > feet ? pos.getY() - 1 : pos.getY();
        }
        for (int i = 0; i < TUNNEL_LENGTH; i++) {
            for (int y = low; y <= low + 1; y++) {
                BlockPos p = new BlockPos(pos.getX() + into.getStepX() * i, y, pos.getZ() + into.getStepZ() * i);
                if (!p.equals(pos)) {
                    list.add(p);
                }
            }
        }
        return list;
    }

    public static List<BlockPos> pattern(MiningMode mode, ServerPlayer player, BlockPos pos) {
        return switch (mode) {
            case SINGLE -> List.of();
            case CROSS -> cross(pos, hitFace(player, pos));
            case TUNNEL -> tunnel(player, pos, hitFace(player, pos));
            case EXCAVATE -> square(pos, hitFace(player, pos));
        };
    }

    /** All blocks of the same ore connected to {@code origin} (diagonals count), nearest first. */
    public static List<BlockPos> vein(Level level, BlockPos origin, BlockState state) {
        return flood(level, origin, s -> s.is(state.getBlock()), VEIN_LIMIT, Integer.MAX_VALUE);
    }

    /**
     * The logs of a natural tree growing from {@code origin}: connected logs within 12 blocks sideways that touch
     * natural (non-persistent) leaves. Log cabins and other builds are left alone.
     */
    public static List<BlockPos> tree(Level level, BlockPos origin) {
        List<BlockPos> logs = flood(level, origin, s -> s.is(BlockTags.LOGS), TREE_LIMIT, 12);
        int leaves = 0;
        Set<BlockPos> counted = new HashSet<>();
        List<BlockPos> all = new ArrayList<>(logs);
        all.add(origin);
        for (BlockPos log : all) {
            for (Direction dir : Direction.values()) {
                BlockPos n = log.relative(dir);
                BlockState s = level.getBlockState(n);
                if (s.getBlock() instanceof LeavesBlock && !s.getValue(LeavesBlock.PERSISTENT) && counted.add(n)) {
                    leaves++;
                }
            }
        }
        return leaves >= 3 ? logs : List.of();
    }

    /** The falling blocks (sand, gravel, concrete powder...) stacked on top of {@code origin}. */
    public static List<BlockPos> column(Level level, BlockPos origin) {
        List<BlockPos> list = new ArrayList<>();
        BlockPos p = origin.above();
        while (list.size() < 64 && level.getBlockState(p).getBlock() instanceof FallingBlock) {
            list.add(p);
            p = p.above();
        }
        return list;
    }

    private static List<BlockPos> flood(Level level, BlockPos origin, Predicate<BlockState> matches, int limit, int maxHorizontal) {
        List<BlockPos> found = new ArrayList<>();
        Set<BlockPos> seen = new HashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        seen.add(origin);
        queue.add(origin);
        while (!queue.isEmpty() && found.size() < limit) {
            BlockPos current = queue.poll();
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        BlockPos n = current.offset(dx, dy, dz);
                        if (Math.abs(n.getX() - origin.getX()) > maxHorizontal || Math.abs(n.getZ() - origin.getZ()) > maxHorizontal) {
                            continue;
                        }
                        if (seen.add(n) && matches.test(level.getBlockState(n))) {
                            found.add(n);
                            queue.add(n);
                            if (found.size() >= limit) {
                                return found;
                            }
                        }
                    }
                }
            }
        }
        return found;
    }

    // ---- Cryolite pickaxe: Cryo Seal ------------------------------------------------------------------------------

    /** Freezes the liquids around a mined block: lava turns to obsidian (sources) or cobblestone, still water to ice. */
    public static int cryoSeal(ServerLevel level, BlockPos pos) {
        int frozen = 0;
        boolean lava = false;
        for (BlockPos p : BlockPos.betweenClosed(pos.offset(-2, -2, -2), pos.offset(2, 2, 2))) {
            BlockState state = level.getBlockState(p);
            FluidState fluid = state.getFluidState();
            if (state.is(Blocks.LAVA)) {
                level.setBlockAndUpdate(p, fluid.isSource() ? Blocks.OBSIDIAN.defaultBlockState() : Blocks.COBBLESTONE.defaultBlockState());
                frozen++;
                lava = true;
            } else if (state.is(Blocks.WATER) && fluid.isSource()) {
                level.setBlockAndUpdate(p, Blocks.ICE.defaultBlockState());
                frozen++;
            } else {
                continue;
            }
            level.sendParticles(ParticleTypes.SNOWFLAKE, p.getX() + 0.5, p.getY() + 0.8, p.getZ() + 0.5, 3, 0.3, 0.2, 0.3, 0.0);
        }
        if (frozen > 0) {
            Vec3 at = Vec3.atCenterOf(pos);
            Fx.sound(level, at, lava ? SoundEvents.FIRE_EXTINGUISH : SoundEvents.GLASS_BREAK, 0.7F, 1.5F);
        }
        return frozen;
    }

    // ---- Fulgurite trait: Momentum ---------------------------------------------------------------------------------

    /** Every 4 blocks mined in quick succession raise Haste by a level, up to Haste III. */
    private static void momentum(ServerPlayer player) {
        long now = player.level().getGameTime();
        Momentum state = MOMENTUM.computeIfAbsent(player.getUUID(), id -> new Momentum());
        state.count = now - state.lastBreak <= 40 ? state.count + 1 : 1;
        state.lastBreak = now;
        int amplifier = Math.min(2, (state.count - 1) / 4);
        player.addEffect(new MobEffectInstance(MobEffects.HASTE, 50, amplifier, true, false, true));
    }

    public static void clear() {
        MOMENTUM.clear();
    }

    public static void forget(UUID player) {
        MOMENTUM.remove(player);
    }
}
