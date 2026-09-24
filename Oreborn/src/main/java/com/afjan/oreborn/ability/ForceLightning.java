package com.afjan.oreborn.ability;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.afjan.oreborn.registry.ModDamageTypes;
import com.afjan.oreborn.registry.ModTags;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.animal.fish.AbstractFish;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * The Lightning Staff's force lightning. While the use key is held, the lightning pours out along the aim: it strikes
 * everything inside a cone in front of the caster and arcs on to nearby monsters; with nothing to strike it follows
 * the aim, bounces off glass (up to {@link #MAX_BOUNCES} times, striking whatever the reflection points at), sets wood
 * and leaves on fire after {@link #IGNITE_TICKS} ticks and electrifies water it hits (fish are fried and come out cooked).
 * {@link #resolve} is shared by the server (effects) and the client (the bolts it draws), so both agree on the path.
 */
public final class ForceLightning {
    public static final double RANGE = 16.0;
    /** Half-angle of the cone the lightning can reach (around the aim, and a narrower one along reflections). */
    public static final double CONE_COS = Math.cos(Math.toRadians(22.0));
    public static final double REFLECTED_CONE_COS = Math.cos(Math.toRadians(15.0));
    public static final int MAX_TARGETS = 4;
    public static final int MAX_ARCS = 2;
    public static final double ARC_RANGE = 4.5;
    public static final int MAX_BOUNCES = 4;
    /** Damage is dealt every HIT_INTERVAL ticks (the damage type ignores the usual hurt cooldown). */
    public static final int HIT_INTERVAL = 4;
    public static final float PRIMARY_DAMAGE = 4.0F;   // 20 damage per second to the main target
    public static final float SECONDARY_DAMAGE = 2.5F;
    public static final float ARC_DAMAGE = 2.0F;
    /** Flammable blocks (wood, leaves, wool...) catch fire after being struck this long (1.5 s). */
    public static final int IGNITE_TICKS = 30;
    /** Electrified water: everything in up to this many connected water blocks within this radius. */
    public static final int WATER_RADIUS = 8;
    public static final int MAX_WATER = 400;
    public static final float WATER_DAMAGE = 3.0F;   // every half second, to anything but fish (fish die at once)
    /** Food points drained per second of channelling (1 food point = half a hunger shank). */
    public static final int FOOD_PER_SECOND = 1;
    public static final int DURABILITY_PER_SECOND = 1;

    /** Caster -> the flammable block being heated and for how long. Server thread only. */
    private static final Map<UUID, Heat> HEAT = new HashMap<>();

    private record Heat(BlockPos pos, int ticks) {}

    /**
     * Where the lightning goes this tick. {@code path} starts at the caster's eye and runs through every reflection; if
     * something is struck, the bolts to the {@code targets} start at the path's last point, otherwise the path ends on
     * {@code block}, in {@code water} or in the air.
     */
    public record Discharge(List<Vec3> path, int bounces, List<LivingEntity> targets, List<LivingEntity> arcs,
            @Nullable BlockHitResult block, @Nullable BlockPos water) {}

    private record Trace(List<Vec3> points, int bounces, @Nullable BlockHitResult block, @Nullable BlockPos water) {}

    private ForceLightning() {}

    // ---- where the lightning goes (both sides) -----------------------------------------------------------------------

    /** Things the lightning may hit: any living thing, except yourself, your pets, allies and armour stands. */
    public static boolean canStrike(LivingEntity user, Entity entity) {
        if (!(entity instanceof LivingEntity living) || !living.isAlive() || entity == user || entity.isSpectator()
                || entity instanceof ArmorStand || user.isAlliedTo(entity)) {
            return false;
        }
        if (entity instanceof TamableAnimal pet && pet.isOwnedBy(user)) {
            return false;
        }
        return !(entity instanceof Player target && user instanceof Player caster && !caster.canHarmPlayer(target));
    }

    public static Discharge resolve(LivingEntity user) {
        Vec3 eye = user.getEyePosition();
        Vec3 look = user.getViewVector(1.0F);
        Trace trace = trace(user, eye, look);
        List<Vec3> points = trace.points();
        // anything in the cone in front of the first surface the aim meets is struck directly
        double firstLength = points.get(1).distanceTo(eye);
        List<LivingEntity> direct = coneTargets(user, eye, look, Math.min(RANGE, firstLength + 0.5), CONE_COS);
        if (!direct.isEmpty()) {
            return new Discharge(List.of(eye), 0, direct, arcTargets(user, direct.getFirst(), direct), null, null);
        }
        // otherwise, whatever a reflection points at
        for (int k = 1; k <= trace.bounces(); k++) {
            Vec3 from = points.get(k);
            Vec3 dir = points.get(k + 1).subtract(from);
            double length = dir.length();
            if (length < 1.0E-3) {
                continue;
            }
            List<LivingEntity> reflected = coneTargets(user, from, dir.scale(1.0 / length), length + 0.5, REFLECTED_CONE_COS);
            if (!reflected.isEmpty()) {
                return new Discharge(new ArrayList<>(points.subList(0, k + 1)), k, reflected,
                        arcTargets(user, reflected.getFirst(), reflected), null, null);
            }
        }
        return new Discharge(points, trace.bounces(), List.of(), List.of(), trace.block(), trace.water());
    }

    /** Follows the aim until it meets water, a block, the end of the range; glass reflects it. */
    private static Trace trace(LivingEntity user, Vec3 origin, Vec3 direction) {
        Level level = user.level();
        List<Vec3> points = new ArrayList<>();
        points.add(origin);
        Vec3 pos = origin;
        Vec3 dir = direction.normalize();
        double remaining = RANGE;
        int bounces = 0;
        while (true) {
            Vec3 end = pos.add(dir.scale(remaining));
            BlockHitResult solid = level.clip(new ClipContext(pos, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, user));
            BlockHitResult wet = level.clip(new ClipContext(pos, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.WATER, user));
            boolean waterFirst = wet.getType() == HitResult.Type.BLOCK && level.getFluidState(wet.getBlockPos()).is(FluidTags.WATER)
                    && (solid.getType() == HitResult.Type.MISS || wet.getLocation().distanceToSqr(pos) < solid.getLocation().distanceToSqr(pos) - 1.0E-4);
            if (waterFirst) {
                points.add(wet.getLocation());
                return new Trace(points, bounces, null, wet.getBlockPos());
            }
            if (solid.getType() == HitResult.Type.MISS) {
                points.add(end);
                return new Trace(points, bounces, null, null);
            }
            points.add(solid.getLocation());
            remaining -= pos.distanceTo(solid.getLocation());
            BlockState state = level.getBlockState(solid.getBlockPos());
            if (bounces >= MAX_BOUNCES || remaining < 0.5 || !state.is(ModTags.REFLECTS_LIGHTNING)) {
                return new Trace(points, bounces, solid, null);
            }
            Direction face = solid.getDirection();
            Vec3 normal = new Vec3(face.getStepX(), face.getStepY(), face.getStepZ());
            dir = dir.subtract(normal.scale(2.0 * dir.dot(normal)));   // angle of incidence = angle of reflection
            pos = solid.getLocation().add(normal.scale(0.02));
            bounces++;
        }
    }

    /** Everything inside a cone from {@code from} along {@code dir} with a clear view, best aimed first. */
    private static List<LivingEntity> coneTargets(LivingEntity user, Vec3 from, Vec3 dir, double range, double cos) {
        Level level = user.level();
        AABB box = new AABB(from, from.add(dir.scale(range))).inflate(range * 0.42 + 1.0);
        record Candidate(LivingEntity entity, double score) {}
        List<Candidate> candidates = new ArrayList<>();
        for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class, box, e -> canStrike(user, e))) {
            Vec3 center = Targets.center(entity);
            Vec3 to = center.subtract(from);
            double distance = to.length();
            if (distance < 0.01 || distance > range || to.dot(dir) / distance < cos) {
                continue;
            }
            if (level.clip(new ClipContext(from, center, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, user)).getType() != HitResult.Type.MISS) {
                continue;
            }
            candidates.add(new Candidate(entity, to.dot(dir) / distance - distance * 0.004));
        }
        return candidates.stream()
                .sorted(Comparator.comparingDouble(Candidate::score).reversed())
                .limit(MAX_TARGETS)
                .map(Candidate::entity)
                .toList();
    }

    /** Monsters the lightning jumps on to from the main target. */
    private static List<LivingEntity> arcTargets(LivingEntity user, LivingEntity primary, List<LivingEntity> exclude) {
        Vec3 from = Targets.center(primary);
        return Targets.enemiesNear(user, from, ARC_RANGE).stream()
                .filter(e -> e != primary && !exclude.contains(e) && canStrike(user, e))
                .sorted(Comparator.comparingDouble(e -> Targets.center(e).distanceToSqr(from)))
                .limit(MAX_ARCS)
                .toList();
    }

    /** The water the lightning runs through: connected water blocks around the hit, nearest first. */
    public static List<BlockPos> electrifiedWater(Level level, BlockPos start) {
        List<BlockPos> found = new ArrayList<>();
        if (!level.getFluidState(start).is(FluidTags.WATER)) {
            return found;
        }
        Set<BlockPos> seen = new HashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        seen.add(start);
        queue.add(start);
        while (!queue.isEmpty() && found.size() < MAX_WATER) {
            BlockPos pos = queue.poll();
            found.add(pos);
            for (Direction direction : Direction.values()) {
                BlockPos next = pos.relative(direction);
                if (next.distSqr(start) <= WATER_RADIUS * WATER_RADIUS && seen.add(next) && level.getFluidState(next).is(FluidTags.WATER)) {
                    queue.add(next);
                }
            }
        }
        return found;
    }

    public static boolean isSurface(Level level, BlockPos water) {
        return !level.getFluidState(water.above()).is(FluidTags.WATER);
    }

    // ---- what it does (server) -----------------------------------------------------------------------------------

    public static boolean hasEnergy(Player player) {
        return player.getAbilities().instabuild || player.getFoodData().getFoodLevel() > 0;
    }

    public static void onStart(ServerLevel level, LivingEntity user) {
        Fx.sound(level, user.getEyePosition(), SoundEvents.TRIDENT_THUNDER, 0.5F, 1.8F);
        Fx.sound(level, user.getEyePosition(), SoundEvents.BEACON_POWER_SELECT, 0.6F, 2.0F);
    }

    /** Every tick of channelling (server side). {@code usedTicks} counts from 0. */
    public static void tick(ServerLevel level, LivingEntity user, ItemStack staff, int usedTicks) {
        RandomSource random = user.getRandom();
        Discharge discharge = resolve(user);
        if (usedTicks % HIT_INTERVAL == 0) {
            strike(level, user, discharge);
        }
        if (discharge.water() != null) {
            electrifyWater(level, user, discharge.water(), usedTicks);
        }
        heat(level, user, discharge);
        if (discharge.bounces() > 0 && usedTicks % 6 == 0) {
            for (int i = 1; i <= discharge.bounces(); i++) {   // the glass rings as the lightning glances off it
                Vec3 at = discharge.path().get(i);
                level.sendParticles(ParticleTypes.ELECTRIC_SPARK, at.x, at.y, at.z, 5, 0.1, 0.1, 0.1, 0.15);
                Fx.sound(level, at, SoundEvents.AMETHYST_BLOCK_CHIME, 0.5F, 1.6F + random.nextFloat() * 0.4F);
            }
        }
        if (usedTicks % 3 == 0) { // crackling
            Fx.sound(level, user.getEyePosition(), SoundEvents.LIGHTNING_BOLT_IMPACT, 0.25F + random.nextFloat() * 0.15F, 1.5F + random.nextFloat() * 0.6F);
        }
        if (usedTicks % 20 == 10) { // an electric hum underneath
            Fx.sound(level, user.getEyePosition(), SoundEvents.BEACON_AMBIENT, 0.8F, 1.9F);
        }
        if (usedTicks > 0 && usedTicks % 20 == 0) {
            staff.hurtAndBreak(DURABILITY_PER_SECOND, user, user.getUsedItemHand());
            if (user instanceof Player player && !player.getAbilities().instabuild) {
                FoodData food = player.getFoodData();
                food.setFoodLevel(Math.max(0, food.getFoodLevel() - FOOD_PER_SECOND));
                food.setSaturation(Math.min(food.getSaturationLevel(), food.getFoodLevel()));
                if (food.getFoodLevel() <= 0) {
                    player.stopUsingItem();
                    player.sendOverlayMessage(Component.translatable("message.oreborn.staff_exhausted").withStyle(ChatFormatting.GRAY));
                }
            }
        }
    }

    /** One pulse of damage: the targets, then the arcs jumping off the main one. */
    public static void strike(ServerLevel level, LivingEntity user, Discharge discharge) {
        List<LivingEntity> targets = discharge.targets();
        for (int i = 0; i < targets.size(); i++) {
            zap(level, user, targets.get(i), i == 0 ? PRIMARY_DAMAGE : SECONDARY_DAMAGE);
        }
        for (LivingEntity arc : discharge.arcs()) {
            zap(level, user, arc, ARC_DAMAGE);
        }
    }

    private static void zap(ServerLevel level, LivingEntity user, LivingEntity target, float damage) {
        target.hurtServer(level, ModDamageTypes.source(level, ModDamageTypes.FORCE_LIGHTNING, user), damage);
        // held in place by the current, convulsing
        target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 10, 2, false, false, true));
        RandomSource random = level.getRandom();
        target.push((random.nextDouble() - 0.5) * 0.12, 0.03, (random.nextDouble() - 0.5) * 0.12);
        Vec3 center = Targets.center(target);
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, center.x, center.y, center.z, 6,
                target.getBbWidth() * 0.4, target.getBbHeight() * 0.35, target.getBbWidth() * 0.4, 0.08);
    }

    /** Wood, leaves and other flammable blocks smoke, then catch fire after IGNITE_TICKS of lightning. */
    private static void heat(ServerLevel level, LivingEntity user, Discharge discharge) {
        BlockHitResult hit = discharge.block();
        if (hit == null || !level.getBlockState(hit.getBlockPos()).isFlammable(level, hit.getBlockPos(), hit.getDirection())) {
            HEAT.remove(user.getUUID());
            return;
        }
        Heat previous = HEAT.get(user.getUUID());
        int ticks = previous != null && previous.pos().equals(hit.getBlockPos()) ? previous.ticks() + 1 : 1;
        Vec3 at = hit.getLocation();
        if (ticks >= IGNITE_TICKS) {
            BlockPos firePos = hit.getBlockPos().relative(hit.getDirection());
            if (BaseFireBlock.canBePlacedAt(level, firePos, user.getDirection()) && level.mayInteract(user, firePos)) {
                level.setBlock(firePos, BaseFireBlock.getState(level, firePos), Block.UPDATE_ALL_IMMEDIATE);
                Fx.sound(level, at, SoundEvents.FIRECHARGE_USE, 0.8F, 1.2F);
                level.sendParticles(ParticleTypes.FLAME, at.x, at.y, at.z, 10, 0.2, 0.2, 0.2, 0.02);
            }
            ticks = 0;
        } else if (ticks % 3 == 0) {
            level.sendParticles(ticks >= IGNITE_TICKS * 2 / 3 ? ParticleTypes.FLAME : ParticleTypes.SMOKE, at.x, at.y, at.z, 2, 0.1, 0.1, 0.1, 0.01);
        }
        HEAT.put(user.getUUID(), new Heat(hit.getBlockPos(), ticks));
    }

    /** The lightning runs through the water it hits: fish are fried at once, everything else in it gets shocked. */
    private static void electrifyWater(ServerLevel level, LivingEntity user, BlockPos start, int usedTicks) {
        List<BlockPos> water = electrifiedWater(level, start);
        if (water.isEmpty()) {
            return;
        }
        Set<BlockPos> wet = new HashSet<>(water);
        BlockPos min = start;
        BlockPos max = start;
        for (BlockPos pos : water) {
            min = new BlockPos(Math.min(min.getX(), pos.getX()), Math.min(min.getY(), pos.getY()), Math.min(min.getZ(), pos.getZ()));
            max = new BlockPos(Math.max(max.getX(), pos.getX()), Math.max(max.getY(), pos.getY()), Math.max(max.getZ(), pos.getZ()));
        }
        AABB box = AABB.encapsulatingFullBlocks(min, max).inflate(0.5);
        for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class, box, e -> canStrike(user, e) && touches(wet, e))) {
            if (entity instanceof AbstractFish) {
                entity.hurtServer(level, ModDamageTypes.source(level, ModDamageTypes.FORCE_LIGHTNING, user), 1000.0F); // fried, see cookFish
            } else if (usedTicks % 10 == 0) {
                zap(level, user, entity, WATER_DAMAGE);
            }
        }
        RandomSource random = level.getRandom();
        if (usedTicks % 2 == 0) { // sparks dance across the surface
            for (int i = 0; i < 12; i++) {
                BlockPos pos = water.get(random.nextInt(water.size()));
                if (isSurface(level, pos)) {
                    double y = pos.getY() + level.getFluidState(pos).getHeight(level, pos);
                    level.sendParticles(ParticleTypes.ELECTRIC_SPARK, pos.getX() + random.nextDouble(), y + 0.05, pos.getZ() + random.nextDouble(), 2, 0.2, 0.02, 0.2, 0.05);
                }
            }
        }
        if (usedTicks % 8 == 0) {
            Fx.sound(level, Vec3.atCenterOf(start), SoundEvents.LIGHTNING_BOLT_IMPACT, 0.35F, 0.7F + random.nextFloat() * 0.3F);
        }
    }

    private static boolean touches(Set<BlockPos> water, Entity entity) {
        return water.contains(entity.blockPosition())
                || water.contains(BlockPos.containing(entity.getX(), entity.getY() + entity.getBbHeight() * 0.5, entity.getZ()));
    }

    /** Fish killed by force lightning drop already cooked (with a sizzle). */
    public static void cookFish(ServerLevel level, LivingEntity fish, Collection<ItemEntity> drops) {
        for (ItemEntity drop : drops) {
            drop.setItem(Traits.smelted(level, drop.getItem()));
        }
        Vec3 at = Targets.center(fish);
        level.sendParticles(ParticleTypes.SMOKE, at.x, at.y, at.z, 6, 0.2, 0.2, 0.2, 0.02);
        Fx.sound(level, at, SoundEvents.FIRE_EXTINGUISH, 0.4F, 1.6F);
    }

    public static void forget(UUID user) {
        HEAT.remove(user);
    }

    public static void clear() {
        HEAT.clear();
    }
}
