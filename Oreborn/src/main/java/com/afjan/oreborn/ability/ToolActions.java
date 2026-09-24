package com.afjan.oreborn.ability;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiPredicate;

import org.jspecify.annotations.Nullable;

import com.afjan.oreborn.item.MiningMode;
import com.afjan.oreborn.item.OrebornToolItem;
import com.afjan.oreborn.material.GearType;
import com.afjan.oreborn.material.OreMaterial;
import com.afjan.oreborn.registry.ModDamageTypes;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.stats.Stats;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CocoaBlock;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.FarmlandBlock;
import net.minecraft.world.level.block.NetherWartBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** Right-click abilities. {@code null} means "not this tool's business", so the vanilla behaviour runs. */
public final class ToolActions {
    /** Emberite axe: player -> game time until which a landing triggers the Meteor Slam. */
    private static final Map<UUID, Long> METEOR = new HashMap<>();

    private ToolActions() {}

    public static @Nullable InteractionResult use(OrebornToolItem item, Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        GearType type = item.type();
        return switch (item.material()) {
            case CRYOLITE -> type == GearType.AXE ? activate(level, player, stack, hand, 240, 3, ToolActions::frostNova) : null;
            case FULGURITE -> switch (type) {
                case AXE -> activate(level, player, stack, hand, 160, 4, ToolActions::stormcaller);
                case PICKAXE -> activate(level, player, stack, hand, 200, 5, OreRadar::ping);
                case HOE -> activate(level, player, stack, hand, 100, 2, ToolActions::chargedSoil);
                default -> null;
            };
            case EMBERITE -> type == GearType.AXE ? activate(level, player, stack, hand, 100, 4, ToolActions::meteor) : null;
            case UMBRIUM -> switch (type) {
                case SWORD -> activate(level, player, stack, hand, 100, 2, ToolActions::voidStrike);
                case PICKAXE -> cycleMode(level, player, stack);
                case SHOVEL -> activate(level, player, stack, hand, 60, 3, ToolActions::riftBurrow);
                default -> null;
            };
        };
    }

    public static @Nullable InteractionResult useOn(OrebornToolItem item, UseOnContext context) {
        if (item.is(OreMaterial.CRYOLITE, GearType.HOE)) {
            return glacialIrrigation(context);
        }
        if (item.is(OreMaterial.EMBERITE, GearType.SHOVEL)) {
            return kilnTouch(context);
        }
        if (item.is(OreMaterial.UMBRIUM, GearType.HOE)) {
            return voidHarvest(context);
        }
        return null;
    }

    /** Cooldown + durability wrapper: the ability runs on the server and may decline (e.g. no target). */
    private static InteractionResult activate(Level level, Player player, ItemStack stack, InteractionHand hand, int cooldown, int durability,
            BiPredicate<ServerLevel, ServerPlayer> ability) {
        if (player.getCooldowns().isOnCooldown(stack)) {
            return InteractionResult.FAIL;
        }
        if (level instanceof ServerLevel serverLevel && player instanceof ServerPlayer serverPlayer) {
            if (!ability.test(serverLevel, serverPlayer)) {
                return InteractionResult.FAIL;
            }
            player.getCooldowns().addCooldown(stack, cooldown);
            stack.hurtAndBreak(durability, player, hand);
            player.awardStat(Stats.ITEM_USED.get(stack.getItem()));
        }
        return InteractionResult.SUCCESS;
    }

    // ---- Cryolite axe: Frost Nova ------------------------------------------------------------------------------------

    private static boolean frostNova(ServerLevel level, ServerPlayer player) {
        Vec3 at = player.position();
        for (LivingEntity enemy : Targets.enemiesNear(player, at, 6.0)) {
            Combat.freeze(enemy, 80);
            enemy.hurtServer(level, ModDamageTypes.source(level, ModDamageTypes.FROSTBITE, player), 4.0F);
        }
        for (double r = 1.5; r <= 6.0; r += 1.5) {
            Fx.ring(level, ParticleTypes.SNOWFLAKE, at.add(0.0, 0.3, 0.0), r, (int) (r * 10));
        }
        Fx.burst(level, ParticleTypes.ITEM_SNOWBALL, at.add(0.0, 1.0, 0.0), 30, 1.5, 0.1);
        Fx.sound(level, at, SoundEvents.GLASS_BREAK, 1.0F, 0.6F);
        Fx.sound(level, at, SoundEvents.POWDER_SNOW_BREAK, 1.0F, 0.8F);
        return true;
    }

    // ---- Fulgurite axe: Stormcaller ----------------------------------------------------------------------------------

    private static boolean stormcaller(ServerLevel level, ServerPlayer player) {
        HitResult blockHit = Targets.lookedAtBlock(player, 32.0);
        LivingEntity aimed = Targets.lookedAtEntity(player, 32.0);
        Vec3 strike;
        if (aimed != null) {
            strike = aimed.position();
        } else if (blockHit.getType() == HitResult.Type.BLOCK) {
            strike = blockHit.getLocation();
        } else {
            return false;
        }
        LightningBolt bolt = EntityTypes.LIGHTNING_BOLT.create(level, EntitySpawnReason.TRIGGERED);
        if (bolt != null) {
            bolt.snapTo(strike);
            bolt.setVisualOnly(true); // no fires, no mob conversions: the damage below is ours
            level.addFreshEntity(bolt);
        }
        List<LivingEntity> victims = new ArrayList<>(Targets.enemiesNear(player, strike, 3.0));
        if (aimed != null && !victims.contains(aimed)) {
            victims.add(aimed);
        }
        for (LivingEntity victim : victims) {
            victim.hurtServer(level, ModDamageTypes.source(level, ModDamageTypes.ELECTROCUTION, player), 8.0F);
            if (victim instanceof Creeper creeper && bolt != null) {
                creeper.thunderHit(level, bolt); // charged creeper
            }
        }
        Fx.burst(level, ParticleTypes.ELECTRIC_SPARK, strike.add(0.0, 0.5, 0.0), 40, 1.0, 0.3);
        return true;
    }

    // ---- Fulgurite hoe: Charged Soil ---------------------------------------------------------------------------------

    private static boolean isGrowable(BlockState state) {
        return state.is(BlockTags.CROPS) || state.is(BlockTags.SAPLINGS) || state.is(Blocks.SUGAR_CANE) || state.is(Blocks.CACTUS)
                || state.is(Blocks.NETHER_WART) || state.is(Blocks.COCOA) || state.is(Blocks.SWEET_BERRY_BUSH) || state.is(Blocks.BAMBOO)
                || state.is(Blocks.BAMBOO_SAPLING) || state.is(Blocks.MELON_STEM) || state.is(Blocks.PUMPKIN_STEM);
    }

    /** Five random ticks for every crop in a 9x9 area: lightning enriches the soil. */
    private static boolean chargedSoil(ServerLevel level, ServerPlayer player) {
        BlockPos center = player.blockPosition();
        int charged = 0;
        for (BlockPos p : BlockPos.betweenClosed(center.offset(-4, -2, -4), center.offset(4, 2, 4))) {
            if (!isGrowable(level.getBlockState(p))) {
                continue;
            }
            BlockPos pos = p.immutable();
            for (int i = 0; i < 5; i++) {
                BlockState state = level.getBlockState(pos);
                if (!isGrowable(state) || !state.isRandomlyTicking()) {
                    break;
                }
                state.randomTick(level, pos, level.getRandom());
            }
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 4, 0.3, 0.3, 0.3, 0.05);
            level.sendParticles(ParticleTypes.HAPPY_VILLAGER, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 2, 0.3, 0.3, 0.3, 0.0);
            charged++;
        }
        if (charged == 0) {
            player.sendOverlayMessage(Component.translatable("message.oreborn.no_crops").withStyle(ChatFormatting.GRAY));
            return false;
        }
        Fx.ring(level, ParticleTypes.ELECTRIC_SPARK, player.position().add(0.0, 0.2, 0.0), 4.0, 40);
        Fx.sound(level, player.position(), SoundEvents.LIGHTNING_BOLT_IMPACT, 0.4F, 2.0F);
        return true;
    }

    // ---- Emberite axe: Meteor Slam -----------------------------------------------------------------------------------

    /** On the ground: leap up. In the air: plunge. Either way the landing becomes a fiery shockwave. */
    private static boolean meteor(ServerLevel level, ServerPlayer player) {
        Vec3 look = player.getLookAngle();
        if (player.onGround()) {
            player.setDeltaMovement(look.x * 0.6, 1.15, look.z * 0.6);
        } else {
            Vec3 motion = player.getDeltaMovement();
            player.setDeltaMovement(motion.x * 0.3, -2.2, motion.z * 0.3);
        }
        player.syncVelocity = true;
        METEOR.put(player.getUUID(), level.getGameTime() + 100);
        Fx.burst(level, ParticleTypes.FLAME, player.position(), 20, 0.4, 0.05);
        Fx.sound(level, player.position(), SoundEvents.BLAZE_SHOOT, 0.8F, 0.8F);
        return true;
    }

    /** Called when a player lands; returns true when a Meteor Slam went off (fall damage is then cancelled). */
    public static boolean onLanding(ServerPlayer player, double fallDistance) {
        Long until = METEOR.remove(player.getUUID());
        ServerLevel level = (ServerLevel) player.level();
        if (until == null || level.getGameTime() > until) {
            return false;
        }
        slam(level, player, fallDistance);
        return true;
    }

    public static void slam(ServerLevel level, LivingEntity player, double fallDistance) {
        Vec3 at = player.position();
        float damage = 5.0F + (float) Math.min(20.0, fallDistance * 1.5);
        for (LivingEntity enemy : Targets.enemiesNear(player, at, 4.5)) {
            Combat.ignite(enemy, player, 4);
            enemy.hurtServer(level, ModDamageTypes.source(level, ModDamageTypes.COMBUSTION, player), damage);
            Vec3 away = enemy.position().subtract(at).multiply(1.0, 0.0, 1.0);
            away = away.lengthSqr() < 1.0E-4 ? Vec3.ZERO : away.normalize().scale(0.9);
            enemy.push(away.x, 0.5, away.z);
        }
        for (double r = 1.0; r <= 4.5; r += 1.0) {
            Fx.ring(level, ParticleTypes.FLAME, at.add(0.0, 0.1, 0.0), r, (int) (r * 12));
        }
        Fx.burst(level, ParticleTypes.LAVA, at, 12, 1.0, 0.0);
        Fx.burst(level, ParticleTypes.EXPLOSION, at.add(0.0, 0.5, 0.0), 3, 1.0, 0.0);
        Fx.sound(level, at, SoundEvents.GENERIC_EXPLODE, 1.0F, 0.8F);
        Fx.sound(level, at, SoundEvents.MACE_SMASH_GROUND_HEAVY, 1.0F, 0.9F);
    }

    // ---- Umbrium sword: Void Strike ----------------------------------------------------------------------------------

    /** Where Void Strike takes the player, and the enemy it strikes at (null: a plain blink). */
    public record Blink(Vec3 spot, @Nullable LivingEntity target) {}

    /** Teleport behind the enemy you look at (next hit within 3 s deals double damage), or blink 8 blocks ahead. */
    private static boolean voidStrike(ServerLevel level, ServerPlayer player) {
        Blink blink = voidStrikeDestination(level, player);
        if (blink == null) {
            return false;
        }
        Vec3 from = player.position();
        Vec3 spot = blink.spot();
        if (blink.target() != null) {
            teleportFacing(level, player, spot, blink.target().getEyePosition());
            Combat.openVoidStrike(player, 60);
        } else {
            player.teleportTo(spot.x, spot.y, spot.z);
            player.resetFallDistance();
        }
        voidFx(level, from, spot);
        return true;
    }

    public static @Nullable Blink voidStrikeDestination(ServerLevel level, ServerPlayer player) {
        Vec3 from = player.position();
        LivingEntity target = Targets.lookedAtEntity(player, 20.0);
        if (target != null) {
            Vec3 facing = target.getViewVector(1.0F).multiply(1.0, 0.0, 1.0);
            if (facing.lengthSqr() < 1.0E-4) {
                facing = target.position().subtract(from).multiply(1.0, 0.0, 1.0);
            }
            facing = facing.normalize();
            double distance = target.getBbWidth() * 0.5 + 0.9;
            Vec3 side = new Vec3(-facing.z, 0.0, facing.x);
            Vec3[] spots = {
                    target.position().subtract(facing.scale(distance)),
                    target.position().add(side.scale(distance)),
                    target.position().subtract(side.scale(distance)),
                    target.position().add(facing.scale(distance)) };
            for (Vec3 spot : spots) {
                if (canStandAt(level, player, spot)) {
                    return new Blink(spot, target);
                }
            }
            return null;
        }
        Vec3 look = player.getViewVector(1.0F);
        Vec3 end = Targets.lookedAtBlock(player, 8.0).getLocation().subtract(look.scale(0.7));
        Vec3 feet = end.subtract(0.0, player.getEyeHeight(), 0.0);
        for (double dy : new double[] { 0.0, 0.5, 1.0, -0.5, -1.0 }) {
            Vec3 spot = feet.add(0.0, dy, 0.0);
            if (spot.distanceToSqr(from) > 1.0 && canStandAt(level, player, spot)) {
                return new Blink(spot, null);
            }
        }
        return null;
    }

    private static void teleportFacing(ServerLevel level, ServerPlayer player, Vec3 spot, Vec3 lookAt) {
        double dx = lookAt.x - spot.x;
        double dz = lookAt.z - spot.z;
        double dy = lookAt.y - (spot.y + player.getEyeHeight());
        float yaw = (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90.0F;
        float pitch = (float) (-(Mth.atan2(dy, Math.sqrt(dx * dx + dz * dz)) * Mth.RAD_TO_DEG));
        player.teleportTo(level, spot.x, spot.y, spot.z, Set.of(), yaw, pitch, false);
        player.resetFallDistance();
    }

    private static void voidFx(ServerLevel level, Vec3 from, Vec3 to) {
        Fx.burst(level, ParticleTypes.REVERSE_PORTAL, from.add(0.0, 1.0, 0.0), 30, 0.4, 0.05);
        Fx.burst(level, ParticleTypes.PORTAL, to.add(0.0, 1.0, 0.0), 40, 0.4, 0.4);
        Fx.sound(level, from, SoundEvents.ENDERMAN_TELEPORT, 0.8F, 1.2F);
        Fx.sound(level, to, SoundEvents.ENDERMAN_TELEPORT, 0.8F, 1.4F);
    }

    /** The player's hitbox fits at {@code feet} without touching blocks or lava. */
    private static boolean canStandAt(ServerLevel level, ServerPlayer player, Vec3 feet) {
        AABB box = player.getDimensions(player.getPose()).makeBoundingBox(feet);
        return level.noCollision(player, box) && level.getBlockStates(box).noneMatch(s -> s.getFluidState().is(FluidTags.LAVA));
    }

    // ---- Umbrium pickaxe: pattern switch -----------------------------------------------------------------------------

    private static @Nullable InteractionResult cycleMode(Level level, Player player, ItemStack stack) {
        if (!player.isShiftKeyDown()) {
            return null;
        }
        if (!level.isClientSide()) {
            MiningMode next = MiningMode.of(stack).next();
            next.applyTo(stack);
            player.sendOverlayMessage(Component.translatable("message.oreborn.mode", next.displayName())
                    .withStyle(style -> style.withColor(OreMaterial.UMBRIUM.color())));
            level.playSound(null, player.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, player.getSoundSource(), 1.0F, 0.6F + next.ordinal() * 0.25F);
        }
        return InteractionResult.SUCCESS;
    }

    // ---- Umbrium shovel: Rift Burrow ---------------------------------------------------------------------------------

    /** Phase through up to ~10 blocks of solid ground (walls, ceilings, floors) to the open space behind. */
    private static boolean riftBurrow(ServerLevel level, ServerPlayer player) {
        Vec3 spot = riftDestination(level, player);
        if (spot == null) {
            player.sendOverlayMessage(Component.translatable("message.oreborn.no_rift").withStyle(ChatFormatting.GRAY));
            return false;
        }
        Vec3 from = player.position();
        player.teleportTo(spot.x, spot.y, spot.z);
        player.resetFallDistance();
        voidFx(level, from, spot);
        return true;
    }

    /** The open space behind the wall, floor or ceiling the player faces (null: nothing to burrow through). */
    public static @Nullable Vec3 riftDestination(ServerLevel level, ServerPlayer player) {
        Vec3 look = player.getLookAngle();
        Direction dir = look.y > 0.75 ? Direction.UP : look.y < -0.75 ? Direction.DOWN : player.getDirection();
        BlockPos feet = player.blockPosition();
        boolean passedSolid = false;
        for (int i = 1; i <= 12; i++) {
            BlockPos cell = feet.relative(dir, i);
            Vec3 spot = Vec3.atBottomCenterOf(cell);
            if (!canStandAt(level, player, spot)) {
                if (isUnbreakable(level, cell) || isUnbreakable(level, cell.above())) {
                    break;
                }
                passedSolid = true;
                continue;
            }
            if (!passedSolid) {
                break;
            }
            if (dir == Direction.DOWN && !level.getBlockState(cell.below()).isFaceSturdy(level, cell.below(), Direction.UP)) {
                continue; // don't drop into a cave, look for a floor further down
            }
            return spot;
        }
        return null;
    }

    private static boolean isUnbreakable(Level level, BlockPos pos) {
        return level.getBlockState(pos).getDestroySpeed(level, pos) < 0.0F;
    }

    // ---- Cryolite hoe: Glacial Irrigation ----------------------------------------------------------------------------

    private static boolean tillable(Level level, BlockPos pos) {
        return level.getBlockState(pos).is(BlockTags.TURNS_INTO_FARMLAND) && level.getBlockState(pos.above()).isAir();
    }

    /** Tills 3x3; sneaking carves a hydrated 9x9 farm plot around a water source instead. */
    private static @Nullable InteractionResult glacialIrrigation(UseOnContext context) {
        Level level = context.getLevel();
        BlockPos center = context.getClickedPos();
        Player player = context.getPlayer();
        if (context.getClickedFace() == Direction.DOWN || !tillable(level, center)) {
            return null;
        }
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        ItemStack stack = context.getItemInHand();
        boolean plot = player != null && player.isShiftKeyDown();
        if (plot && player.getCooldowns().isOnCooldown(stack)) {
            return InteractionResult.FAIL;
        }
        int radius = plot ? 4 : 1;
        int tilled = 0;
        for (BlockPos p : BlockPos.betweenClosed(center.offset(-radius, 0, -radius), center.offset(radius, 0, radius))) {
            BlockPos pos = p.immutable();
            if (!tillable(level, pos)) {
                continue;
            }
            if (plot && pos.equals(center)) {
                level.setBlockAndUpdate(pos, Blocks.WATER.defaultBlockState());
            } else {
                level.setBlockAndUpdate(pos, Blocks.FARMLAND.defaultBlockState().setValue(FarmlandBlock.MOISTURE, plot ? 7 : 0));
                tilled++;
            }
        }
        ServerLevel serverLevel = (ServerLevel) level;
        Vec3 at = Vec3.atCenterOf(center).add(0.0, 0.6, 0.0);
        Fx.sound(serverLevel, at, SoundEvents.HOE_TILL, 1.0F, 1.0F);
        if (plot) {
            Fx.sound(serverLevel, at, SoundEvents.BUCKET_EMPTY, 1.0F, 1.0F);
            Fx.ring(serverLevel, ParticleTypes.SNOWFLAKE, at, 2.5, 30);
            Fx.ring(serverLevel, ParticleTypes.SNOWFLAKE, at, 4.0, 45);
        }
        if (player != null) {
            stack.hurtAndBreak(plot ? Math.max(1, tilled / 4) : Math.max(1, tilled), player, context.getHand());
            if (plot) {
                player.getCooldowns().addCooldown(stack, 20);
            }
        }
        return InteractionResult.SUCCESS;
    }

    // ---- Emberite shovel: Kiln Touch ---------------------------------------------------------------------------------

    /** What a block turns into in a furnace, if that result is a block (sand -> glass, cobblestone -> stone...). */
    public static Optional<BlockState> smelted(ServerLevel level, BlockState state) {
        Item item = state.getBlock().asItem();
        if (item == Items.AIR) {
            return Optional.empty();
        }
        SingleRecipeInput input = new SingleRecipeInput(new ItemStack(item));
        return level.recipeAccess().getRecipeFor(RecipeType.SMELTING, input, level)
                .map(recipe -> recipe.value().assemble(input))
                .filter(result -> result.getItem() instanceof BlockItem blockItem && blockItem.getBlock() != state.getBlock())
                .map(result -> copyProperties(state, ((BlockItem) result.getItem()).getBlock().defaultBlockState()));
    }

    private static BlockState copyProperties(BlockState from, BlockState to) {
        for (Property<?> property : from.getProperties()) {
            if (to.hasProperty(property)) {
                to = copy(from, to, property);
            }
        }
        return to;
    }

    private static <T extends Comparable<T>> BlockState copy(BlockState from, BlockState to, Property<T> property) {
        return to.setValue(property, from.getValue(property));
    }

    /** Smelts the clicked block (and its 3x3, unless sneaking) in place; anything else makes a path as usual. */
    private static @Nullable InteractionResult kilnTouch(UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        if (level.isClientSide()) {
            return level.getBlockState(pos).isAir() ? null : InteractionResult.SUCCESS;
        }
        ServerLevel serverLevel = (ServerLevel) level;
        if (smelted(serverLevel, level.getBlockState(pos)).isEmpty()) {
            return null;
        }
        Player player = context.getPlayer();
        List<BlockPos> targets = new ArrayList<>();
        targets.add(pos);
        if (player == null || !player.isShiftKeyDown()) {
            targets.addAll(AreaMining.square(pos, context.getClickedFace()));
        }
        int smelted = 0;
        for (BlockPos target : targets) {
            if (level.getBlockEntity(target) != null || player != null && !level.mayInteract(player, target)) {
                continue;
            }
            Optional<BlockState> result = smelted(serverLevel, level.getBlockState(target));
            if (result.isEmpty()) {
                continue;
            }
            level.setBlockAndUpdate(target, result.get());
            Vec3 at = Vec3.atCenterOf(target);
            serverLevel.sendParticles(ParticleTypes.FLAME, at.x, at.y, at.z, 6, 0.35, 0.35, 0.35, 0.01);
            serverLevel.sendParticles(ParticleTypes.SMOKE, at.x, at.y + 0.5, at.z, 3, 0.3, 0.1, 0.3, 0.01);
            smelted++;
        }
        Vec3 at = Vec3.atCenterOf(pos);
        Fx.sound(serverLevel, at, SoundEvents.FIRECHARGE_USE, 0.6F, 1.2F);
        Fx.sound(serverLevel, at, SoundEvents.FURNACE_FIRE_CRACKLE, 1.0F, 1.0F);
        if (player != null && smelted > 0) {
            context.getItemInHand().hurtAndBreak(smelted, player, context.getHand());
        }
        return InteractionResult.SUCCESS;
    }

    // ---- Umbrium hoe: Void Harvest -----------------------------------------------------------------------------------

    public static boolean isMatureCrop(BlockState state) {
        Block block = state.getBlock();
        if (block instanceof CropBlock crop) {
            return crop.isMaxAge(state);
        }
        if (block instanceof NetherWartBlock) {
            return state.getValue(NetherWartBlock.AGE) >= NetherWartBlock.MAX_AGE;
        }
        if (block instanceof CocoaBlock) {
            return state.getValue(CocoaBlock.AGE) >= CocoaBlock.MAX_AGE;
        }
        return false;
    }

    private static BlockState replanted(BlockState state) {
        if (state.getBlock() instanceof CropBlock crop) {
            return crop.getStateForAge(0);
        }
        if (state.getBlock() instanceof NetherWartBlock) {
            return state.setValue(NetherWartBlock.AGE, 0);
        }
        return state.setValue(CocoaBlock.AGE, 0);
    }

    /** Harvests and replants every ripe crop in a 9x9 area; the harvest goes straight into the inventory. */
    private static @Nullable InteractionResult voidHarvest(UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        if (!isMatureCrop(level.getBlockState(pos))) {
            if (!isMatureCrop(level.getBlockState(pos.above()))) {
                return null;
            }
            pos = pos.above();
        }
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        ServerLevel serverLevel = (ServerLevel) level;
        Player player = context.getPlayer();
        ItemStack tool = context.getItemInHand();
        int harvested = 0;
        for (BlockPos p : BlockPos.betweenClosed(pos.offset(-4, -1, -4), pos.offset(4, 1, 4))) {
            BlockState crop = level.getBlockState(p);
            if (!isMatureCrop(crop)) {
                continue;
            }
            BlockPos at = p.immutable();
            List<ItemStack> drops = Block.getDrops(crop, serverLevel, at, null, player, tool);
            Item seed = crop.getBlock().asItem();
            for (ItemStack drop : drops) {
                if (drop.is(seed)) {
                    drop.shrink(1); // the replanted seed
                    break;
                }
            }
            level.setBlockAndUpdate(at, replanted(crop));
            for (ItemStack drop : drops) {
                giveOrDrop(player, drop, level, at);
            }
            serverLevel.sendParticles(ParticleTypes.REVERSE_PORTAL, at.getX() + 0.5, at.getY() + 0.5, at.getZ() + 0.5, 5, 0.25, 0.25, 0.25, 0.02);
            harvested++;
        }
        Vec3 at = Vec3.atCenterOf(pos);
        Fx.sound(serverLevel, at, SoundEvents.CROP_BREAK, 1.0F, 1.0F);
        Fx.sound(serverLevel, at, SoundEvents.ENDERMAN_TELEPORT, 0.3F, 1.6F);
        if (player != null) {
            tool.hurtAndBreak(Math.max(1, harvested / 4), player, context.getHand());
        }
        return InteractionResult.SUCCESS;
    }

    /** Void Pocket: into the inventory if it fits, the rest drops where it was. */
    public static void giveOrDrop(@Nullable Player player, ItemStack stack, Level level, BlockPos pos) {
        if (stack.isEmpty()) {
            return;
        }
        if (player != null) {
            player.getInventory().add(stack);
        }
        if (!stack.isEmpty()) {
            Block.popResource(level, pos, stack);
        }
    }

    public static void clear() {
        METEOR.clear();
    }

    public static void prune(long gameTime) {
        METEOR.values().removeIf(until -> until < gameTime);
    }
}
