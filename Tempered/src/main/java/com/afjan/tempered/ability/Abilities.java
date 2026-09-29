package com.afjan.tempered.ability;

import com.afjan.tempered.event.PlacedBlocks;
import com.afjan.tempered.event.ProgressEvents;
import com.afjan.tempered.mastery.Kind;
import com.afjan.tempered.mastery.Mastery;
import com.afjan.tempered.mastery.Perk;
import com.afjan.tempered.mastery.Progress;
import com.afjan.tempered.mastery.Stat;
import com.afjan.tempered.mastery.Track;
import com.afjan.tempered.mastery.Tracks;
import com.afjan.tempered.mixin.CrossbowItemInvoker;
import com.afjan.tempered.registry.ModTags;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockItemTags;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.Shearable;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.entity.projectile.arrow.ThrownTrident;
import net.minecraft.world.item.ArrowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.ProjectileImpactEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.listener.Priority;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The capstone abilities. Area abilities (Vein Miner, Excavate, Timber, Reaper) break the extra blocks through
 * {@code ServerPlayerGameMode#destroyBlock}, so drops, enchantments, durability and counters all apply.
 * Sneaking turns them off.
 */
public final class Abilities {
    private static final List<TagKey<Block>> ORE_FAMILIES = List.of(
            BlockItemTags.COAL_ORES.block(), BlockItemTags.IRON_ORES.block(), BlockItemTags.COPPER_ORES.block(),
            BlockItemTags.GOLD_ORES.block(), BlockItemTags.REDSTONE_ORES.block(), BlockItemTags.LAPIS_ORES.block(),
            BlockItemTags.DIAMOND_ORES.block(), BlockItemTags.EMERALD_ORES.block());

    private static boolean areaActive;
    private static boolean shockwaveActive;
    private static boolean spawningVolley;

    private Abilities() {
    }

    /** Runs after the counting listeners (same LOWEST priority, registered later) and only if not cancelled. */
    public static void register() {
        BlockEvent.BreakEvent.BUS.addListener(Priority.LOWEST, e -> {
            onBreak(e);
            return false;
        });
        PlayerInteractEvent.EntityInteractSpecific.BUS.addListener(Priority.LOWEST, e -> {
            onInteractEntity(e);
            return false;
        });
        ProjectileImpactEvent.BUS.addListener(Abilities::onImpact);
    }

    public static boolean isShockwaveActive() {
        return shockwaveActive;
    }

    public static boolean isSpawningVolley() {
        return spawningVolley;
    }

    // ------------------------------------------------------------------------------------------------ area mining

    private static void onBreak(BlockEvent.BreakEvent event) {
        if (areaActive || !(event.getPlayer() instanceof ServerPlayer player) || !(event.getLevel() instanceof ServerLevel level)) return;
        if (player.isShiftKeyDown() || player.isCreative() || player.isSpectator()) return;
        ItemStack tool = player.getMainHandItem();
        Track track = Tracks.get(tool);
        if (track == null || tool.isBroken()) return;
        List<BlockPos> targets = targets(player, level, event.getPos(), event.getState(), tool, track.kind);
        if (!targets.isEmpty()) breakAll(player, tool, targets);
    }

    /** The extra blocks one break with this tool takes along (empty if no ability applies). */
    public static List<BlockPos> targets(ServerPlayer player, Level level, BlockPos origin, BlockState state, ItemStack tool, Kind kind) {
        return switch (kind) {
            case PICKAXE -> {
                int vein = (int) Mastery.perk(tool, Perk.VEIN);
                if (vein > 0 && state.is(ModTags.ORES)) yield vein(level, origin, state, vein);
                int excavate = (int) Mastery.perk(tool, Perk.EXCAVATE);
                yield excavate > 0 ? plane(player, level, origin, state, tool, excavate) : List.of();
            }
            case SHOVEL -> {
                int excavate = (int) Mastery.perk(tool, Perk.EXCAVATE);
                yield excavate > 0 ? plane(player, level, origin, state, tool, excavate) : List.of();
            }
            case AXE -> {
                int timber = (int) Mastery.perk(tool, Perk.TIMBER);
                yield timber > 0 && state.is(BlockTags.LOGS) ? tree(level, origin, timber) : List.of();
            }
            case HOE -> {
                int reaper = (int) Mastery.perk(tool, Perk.REAPER);
                yield reaper > 0 && ProgressEvents.isMatureCrop(state) ? crops(level, origin, reaper) : List.of();
            }
            default -> List.of();
        };
    }

    public static void breakAll(ServerPlayer player, ItemStack tool, List<BlockPos> targets) {
        areaActive = true;
        try {
            for (BlockPos pos : targets) {
                if (tool.isBroken() || player.getMainHandItem() != tool) break;
                player.gameMode.destroyBlock(pos);
            }
        } finally {
            areaActive = false;
        }
    }

    private static List<BlockPos> vein(Level level, BlockPos origin, BlockState state, int max) {
        List<BlockPos> found = new ArrayList<>();
        Set<BlockPos> seen = new HashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        queue.add(origin);
        seen.add(origin);
        while (!queue.isEmpty() && found.size() < max) {
            BlockPos pos = queue.poll();
            for (BlockPos next : BlockPos.betweenClosed(pos.offset(-1, -1, -1), pos.offset(1, 1, 1))) {
                if (found.size() >= max) break;
                BlockPos immutable = next.immutable();
                if (!seen.add(immutable)) continue;
                BlockState other = level.getBlockState(immutable);
                if (sameOre(state, other) && !PlacedBlocks.contains(level, immutable)) {
                    found.add(immutable);
                    queue.add(immutable);
                }
            }
        }
        return found;
    }

    private static boolean sameOre(BlockState a, BlockState b) {
        if (a.getBlock() == b.getBlock()) return true;
        for (TagKey<Block> family : ORE_FAMILIES) {
            if (a.is(family) && b.is(family)) return true;
        }
        return false;
    }

    /** The (2r+1)^2 face around the block, perpendicular to the side the player is mining. */
    private static List<BlockPos> plane(ServerPlayer player, Level level, BlockPos origin, BlockState state, ItemStack tool, int radius) {
        Direction face = minedFace(player, origin);
        float originHardness = state.getDestroySpeed(level, origin);
        if (originHardness < 0) return List.of();
        List<BlockPos> list = new ArrayList<>();
        for (int a = -radius; a <= radius; a++) {
            for (int b = -radius; b <= radius; b++) {
                if (a == 0 && b == 0) continue;
                BlockPos pos = switch (face.getAxis()) {
                    case X -> origin.offset(0, a, b);
                    case Y -> origin.offset(a, 0, b);
                    case Z -> origin.offset(a, b, 0);
                };
                BlockState other = level.getBlockState(pos);
                if (other.isAir() || level.getBlockEntity(pos) != null || PlacedBlocks.contains(level, pos)) continue;
                float hardness = other.getDestroySpeed(level, pos);
                if (hardness < 0 || hardness > originHardness + 3.5F) continue;
                if (ProgressEvents.isEffective(tool, other)) list.add(pos);
            }
        }
        return list;
    }

    private static Direction minedFace(ServerPlayer player, BlockPos origin) {
        HitResult hit = player.pick(player.blockInteractionRange() + 1.0, 1.0F, false);
        if (hit instanceof BlockHitResult blockHit && blockHit.getType() == HitResult.Type.BLOCK && blockHit.getBlockPos().equals(origin)) {
            return blockHit.getDirection();
        }
        return Direction.getApproximateNearest(player.getLookAngle()).getOpposite();
    }

    /** Connected logs above the cut, if they carry natural leaves (so houses are safe). */
    private static List<BlockPos> tree(Level level, BlockPos origin, int max) {
        List<BlockPos> logs = new ArrayList<>();
        Set<BlockPos> seen = new HashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        queue.add(origin);
        seen.add(origin);
        boolean crowned = false;
        while (!queue.isEmpty() && logs.size() < max) {
            BlockPos pos = queue.poll();
            for (BlockPos next : BlockPos.betweenClosed(pos.offset(-1, 0, -1), pos.offset(1, 1, 1))) {
                BlockPos immutable = next.immutable();
                if (!seen.add(immutable)) continue;
                BlockState other = level.getBlockState(immutable);
                if (other.is(BlockTags.LOGS)) {
                    if (PlacedBlocks.contains(level, immutable) || logs.size() >= max) continue;
                    logs.add(immutable);
                    queue.add(immutable);
                } else if (!crowned && isNaturalCrown(other)) {
                    crowned = true;
                }
            }
        }
        return crowned ? logs : List.of();
    }

    private static boolean isNaturalCrown(BlockState state) {
        if (!state.is(ModTags.TREE_CROWN)) return false;
        return !state.hasProperty(LeavesBlock.PERSISTENT) || !state.getValue(LeavesBlock.PERSISTENT);
    }

    private static List<BlockPos> crops(Level level, BlockPos origin, int radius) {
        List<BlockPos> list = new ArrayList<>();
        for (BlockPos pos : BlockPos.betweenClosed(origin.offset(-radius, -1, -radius), origin.offset(radius, 1, radius))) {
            if (pos.equals(origin)) continue;
            if (ProgressEvents.isMatureCrop(level.getBlockState(pos))) list.add(pos.immutable());
        }
        return list;
    }

    // ------------------------------------------------------------------------------------------------ mace, trident

    /** Smash hits also strike every other hostile within 4 blocks of the target. */
    public static void shockwave(ServerPlayer player, LivingEntity target, float damage) {
        ServerLevel level = player.level();
        shockwaveActive = true;
        try {
            for (LivingEntity other : level.getEntitiesOfClass(LivingEntity.class, target.getBoundingBox().inflate(4.0),
                    e -> e != player && e != target && e.isAlive() && !(e instanceof ArmorStand) && !(e instanceof Player)
                            && !(e instanceof OwnableEntity own && own.getOwner() == player) && e.distanceToSqr(target) <= 16.0)) {
                other.hurtServer(level, player.damageSources().playerAttack(player), damage);
            }
        } finally {
            shockwaveActive = false;
        }
        level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, target.getX(), target.getY(), target.getZ(), 1, 0, 0, 0, 0);
        level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.MACE_SMASH_GROUND_HEAVY, SoundSource.PLAYERS, 1.0F, 0.8F);
    }

    /** Stormcaller: a thrown trident calls lightning on what it hits, in any weather. */
    private static void onImpact(ProjectileImpactEvent event) {
        if (!(event.getProjectile() instanceof ThrownTrident trident) || !(trident.level() instanceof ServerLevel level)) return;
        if (!(event.getRayTraceResult() instanceof EntityHitResult entityHit) || !(trident.getOwner() instanceof ServerPlayer player)) return;
        ItemStack stack = trident.getWeaponItem();
        if (stack == null || Mastery.perk(stack, Perk.STORMCALLER) <= 0) return;
        Entity target = entityHit.getEntity();
        LightningBolt bolt = EntityTypes.LIGHTNING_BOLT.create(level, EntitySpawnReason.TRIGGERED);
        if (bolt == null) return;
        bolt.snapTo(target.getX(), target.getY(), target.getZ());
        bolt.setCause(player);
        level.addFreshEntity(bolt);
    }

    // ------------------------------------------------------------------------------------------------ bow, crossbow, spear

    /** Volley: a full-power bow shot is joined by two more arrows (which cannot be picked up). */
    public static void volley(ServerPlayer player, AbstractArrow arrow, ItemStack weapon) {
        ServerLevel level = player.level();
        ItemStack ammo = arrow.getPickupItemStackOrigin().copyWithCount(1);
        ArrowItem arrowItem = ammo.getItem() instanceof ArrowItem item ? item : (ArrowItem) Items.ARROW;
        spawningVolley = true;
        try {
            for (int side = -1; side <= 1; side += 2) {
                AbstractArrow extra = arrowItem.createArrow(level, ammo, player, weapon);
                Vec3 velocity = arrow.getDeltaMovement().yRot(side * 0.14F);
                extra.setPos(arrow.getX(), arrow.getY(), arrow.getZ());
                extra.setDeltaMovement(velocity);
                extra.setYRot(arrow.getYRot());
                extra.setXRot(arrow.getXRot());
                extra.setCritArrow(true);
                extra.pickup = AbstractArrow.Pickup.CREATIVE_ONLY;
                level.addFreshEntity(extra);
            }
        } finally {
            spawningVolley = false;
        }
    }

    /** Self-loading crossbows reload themselves after firing when there is ammo. */
    public static void tickAutoload(ServerPlayer player) {
        for (InteractionHand hand : InteractionHand.values()) {
            ItemStack stack = player.getItemInHand(hand);
            if (!(stack.getItem() instanceof CrossbowItem) || CrossbowItem.isCharged(stack) || Mastery.perk(stack, Perk.AUTOLOAD) <= 0) continue;
            if (player.isUsingItem() && player.getUsedItemHand() == hand) continue;
            if (player.getProjectile(stack).isEmpty()) continue;
            if (CrossbowItemInvoker.tempered$tryLoadProjectiles(player, stack)) {
                player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.CROSSBOW_LOADING_END.value(),
                        SoundSource.PLAYERS, 0.8F, 1.1F);
            }
        }
    }

    /** Cavalry: a spear in hand spurs the mount on. */
    public static void tickCavalry(ServerPlayer player) {
        if (player.getVehicle() instanceof LivingEntity mount && Mastery.perk(player.getMainHandItem(), Perk.CAVALRY) > 0) {
            mount.addEffect(new MobEffectInstance(MobEffects.SPEED, 30, 1, true, false, false));
        }
    }

    // ------------------------------------------------------------------------------------------------ shears

    /** Shear Sweep: shearing one animal shears every ready one within 5 blocks. */
    private static void onInteractEntity(PlayerInteractEvent.EntityInteractSpecific event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !(event.getTarget() instanceof Shearable first)) return;
        ItemStack stack = player.getItemInHand(event.getHand());
        if (!first.readyForShearing() || Mastery.perk(stack, Perk.SHEAR_SWEEP) <= 0) return;
        ServerLevel level = player.level();
        Entity origin = event.getTarget();
        for (Entity other : level.getEntities(origin, origin.getBoundingBox().inflate(5.0), e -> e instanceof Shearable s && s.readyForShearing())) {
            if (stack.isBroken()) break;
            ((Shearable) other).shear(level, SoundSource.PLAYERS, stack);
            stack.hurtAndBreak(1, player, event.getHand());
            Progress.record(player, stack, Stat.SHEARED);
        }
    }
}
