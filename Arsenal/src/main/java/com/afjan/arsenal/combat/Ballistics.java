package com.afjan.arsenal.combat;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import com.afjan.arsenal.entity.OrdnanceEntity;
import com.afjan.arsenal.gun.Caliber;
import com.afjan.arsenal.gun.GunData;
import com.afjan.arsenal.gun.GunType;
import com.afjan.arsenal.item.Guns;
import com.afjan.arsenal.network.ArsenalNetwork.ShotFx;
import com.afjan.arsenal.registry.ModDamageTypes;
import com.afjan.arsenal.registry.ModItems;
import com.afjan.arsenal.registry.ModSounds;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Pulls the trigger. Bullets are instant traces rather than entities: a full-auto AA-12 would otherwise spawn 60
 * projectiles a second per player. Launchers are the exception and spawn a real {@link OrdnanceEntity}.
 */
public final class Ballistics {
    /** How far behind the first target a piercing round keeps going. */
    private static final int MAX_PIERCE = 6;
    private static final double HEAD_FRACTION = 0.72;

    private Ballistics() {}

    /**
     * @return true when a round actually left the barrel. A charged weapon fires with the charge the server timed
     *         since the trigger went down ({@link RailCharge}).
     */
    public static boolean fire(ServerPlayer player, InteractionHand hand) {
        GunType type = Guns.typeOf(player.getItemInHand(hand));
        float charge = type != null && type.charges() ? RailCharge.release(player) : 1.0F;
        return fire(player, hand, charge);
    }

    /** The damage multiplier of a charged shot: a quarter at a tap, one and a half at a full charge. */
    public static float chargePower(float charge) {
        return 0.25F + 1.25F * (float) Math.pow(Mth.clamp(charge, 0.0F, 1.0F), 1.5);
    }

    /** @param charge how full a charged weapon's capacitors were (0..1); ignored by every other gun */
    public static boolean fire(ServerPlayer player, InteractionHand hand, float charge) {
        ItemStack stack = player.getItemInHand(hand);
        GunType type = Guns.typeOf(stack);
        if (type == null) {
            return false;
        }
        ServerLevel level = player.level();
        GunData data = Guns.dataOf(stack);

        if (Reloading.isReloading(player) || player.getCooldowns().isOnCooldown(stack)) {
            return false;
        }
        if (data.ammo() <= 0) {
            player.getCooldowns().addCooldown(stack, 10);
            level.playSound(null, player.getX(), player.getY(), player.getZ(), ModSounds.DRY_FIRE.value(),
                    SoundSource.PLAYERS, 1.0F, 0.95F + level.getRandom().nextFloat() * 0.1F);
            Reloading.start(player, hand);
            return false;
        }

        player.getCooldowns().addCooldown(stack, type.effectiveFireDelay(data));
        // Creative players still empty the magazine, but reloading costs them nothing.
        Guns.set(stack, data.withAmmo(data.ammo() - 1));
        player.awardStat(net.minecraft.stats.Stats.ITEM_USED.get(stack.getItem()));

        float power = type.charges() ? chargePower(charge) : 1.0F;
        shootSound(level, player, type, data, charge);
        if (type.firesProjectile()) {
            launch(level, player, type, data);
            PacketDistributor.sendToPlayersTrackingEntityAndSelf(player,
                    new ShotFx(player.getId(), ShotFx.STYLE_BULLET, !type.suppressed(data), 1.0F, List.of()));
        } else {
            trace(level, player, type, data, charge, power);
        }
        return true;
    }

    // --- hitscan ------------------------------------------------------------------------------------------------

    private static void trace(ServerLevel level, ServerPlayer player, GunType type, GunData data, float charge,
            float power) {
        RandomSource random = level.getRandom();
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getLookAngle();
        boolean crouched = player.isShiftKeyDown();
        int pellets = type.effectivePellets(data);
        float spread = type.effectiveSpread(data, crouched) * (player.isSprinting() ? 1.8F : 1.0F);
        double range = type.effectiveRange(data);
        float damage = type.effectiveDamage(data) * power;
        // a charged slug tears through more bodies the fuller the capacitors were
        int pierce = type.charges() ? 1 + Math.round(7.0F * charge) : MAX_PIERCE;
        Vec3 firstWall = null;
        // where every round stopped, for the tracers the clients draw (no smoke: the muzzle flash is client-side)
        List<Vec3> ends = new ArrayList<>();

        for (int pellet = 0; pellet < pellets; pellet++) {
            Vec3 direction = scatter(look, spread, random);
            Vec3 start = eye;
            Vec3 end = eye.add(direction.scale(range));

            Vec3 stop = end;
            BlockHitResult blockHit = level.clip(new ClipContext(start, end, ClipContext.Block.COLLIDER,
                    ClipContext.Fluid.NONE, player));
            if (blockHit.getType() != HitResult.Type.MISS && !type.piercesBlocks()) {
                stop = blockHit.getLocation();
            }

            List<EntityHit> hits = entitiesAlong(level, player, start, stop);
            int allowed = type.piercesEntities() ? pierce : 1;
            float remaining = damage;
            for (int i = 0; i < hits.size() && i < allowed; i++) {
                EntityHit hit = hits.get(i);
                boolean head = hit.location.y >= hit.entity.getY() + hit.entity.getBbHeight() * HEAD_FRACTION
                        && hit.entity.getBbHeight() > 0.8;
                float dealt = remaining * (head ? type.headshotMultiplier() : 1.0F);
                hurt(level, player, hit.entity, dealt, damageTypeFor(type, data, head));
                level.sendParticles(ParticleTypes.DAMAGE_INDICATOR, hit.location.x, hit.location.y, hit.location.z,
                        head ? 4 : 2, 0.1, 0.1, 0.1, 0.0);
                if (head) {
                    // the ping of a headshot: at the victim for bystanders, in the shooter's ears as confirmation
                    ModSounds.broadcast(level, player, hit.entity.getX(), hit.entity.getEyeY(), hit.entity.getZ(),
                            ModSounds.HEADSHOT, null, SoundSource.PLAYERS, 1.0F);
                    ModSounds.toPlayer(player, ModSounds.HEADSHOT, SoundSource.PLAYERS, 0.7F, 1.0F);
                }
                remaining *= 0.75F;
            }

            if (blockHit.getType() != HitResult.Type.MISS) {
                impact(level, blockHit, type);
                if (firstWall == null) {
                    firstWall = blockHit.getLocation();
                }
            }
            if (ends.size() < ShotFx.MAX_ENDS) {
                ends.add(!hits.isEmpty() && !type.piercesEntities() ? hits.getFirst().location
                        : type.piercesBlocks() ? end : stop);
            }
        }
        // a heavily charged slug hits the first wall with a shockwave (no crater: it throws things about)
        if (type.charges() && charge >= 0.75F && firstWall != null) {
            Detonations.shockwave(level, player, firstWall, 1.5F + 12.0F * (charge - 0.75F));
        }
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(player,
                new ShotFx(player.getId(), tracerStyle(type), !type.suppressed(data), power, ends));
    }

    private static byte tracerStyle(GunType type) {
        return switch (type.family()) {
            case RAILGUN -> ShotFx.STYLE_RAIL;
            case SNIPER -> ShotFx.STYLE_HEAVY;
            case SHOTGUN -> ShotFx.STYLE_PELLET;
            default -> ShotFx.STYLE_BULLET;
        };
    }

    private record EntityHit(Entity entity, Vec3 location, double distance) {}

    private static List<EntityHit> entitiesAlong(ServerLevel level, ServerPlayer shooter, Vec3 start, Vec3 end) {
        AABB search = new AABB(start, end).inflate(1.0);
        List<EntityHit> hits = new ArrayList<>();
        for (Entity entity : level.getEntities(shooter, search,
                candidate -> candidate.isAlive() && candidate.isPickable() && candidate != shooter)) {
            AABB box = entity.getBoundingBox().inflate(0.12);
            box.clip(start, end).ifPresent(point -> hits.add(new EntityHit(entity, point, start.distanceToSqr(point))));
        }
        hits.sort(Comparator.comparingDouble(EntityHit::distance));
        return hits;
    }

    private static void hurt(ServerLevel level, ServerPlayer shooter, Entity target, float damage,
            ResourceKey<DamageType> damageType) {
        target.hurtServer(level, ModDamageTypes.source(level, damageType, shooter), damage);
        if (target instanceof LivingEntity living) {
            living.setLastHurtByMob(shooter);
        }
    }

    private static ResourceKey<DamageType> damageTypeFor(GunType type, GunData data, boolean head) {
        if (type.family() == GunType.Family.RAILGUN) {
            return ModDamageTypes.RAILGUN;
        }
        if (head) {
            return ModDamageTypes.HEADSHOT;
        }
        return data.caliber() == Caliber.BUCKSHOT ? ModDamageTypes.BUCKSHOT : ModDamageTypes.BULLET;
    }

    /** Random cone scatter around the aim vector. */
    private static Vec3 scatter(Vec3 look, float spreadDegrees, RandomSource random) {
        if (spreadDegrees <= 0.0F) {
            return look;
        }
        Vec3 side = look.cross(new Vec3(0.0, 1.0, 0.0));
        if (side.lengthSqr() < 1.0E-4) {
            side = look.cross(new Vec3(1.0, 0.0, 0.0));
        }
        side = side.normalize();
        Vec3 up = side.cross(look).normalize();
        double scale = Math.tan(Math.toRadians(spreadDegrees));
        double x = random.nextGaussian() * 0.5 * scale;
        double y = random.nextGaussian() * 0.5 * scale;
        return look.add(side.scale(x)).add(up.scale(y)).normalize();
    }

    private static void impact(ServerLevel level, BlockHitResult hit, GunType type) {
        BlockPos pos = hit.getBlockPos();
        BlockState state = level.getBlockState(pos);
        Vec3 point = hit.getLocation();
        if (!state.isAir()) {
            level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, state),
                    point.x, point.y, point.z, 6, 0.1, 0.1, 0.1, 0.05);
            level.playSound(null, pos, state.getSoundType().getBreakSound(), SoundSource.BLOCKS, 0.4F, 1.4F);
        }
        if (type.family() == GunType.Family.RAILGUN) {
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK, point.x, point.y, point.z, 20, 0.3, 0.3, 0.3, 0.3);
        }
    }

    // --- launchers ----------------------------------------------------------------------------------------------

    /** Which projectile a launcher round becomes. */
    public static Ordnance ordnanceFor(GunType type, Caliber caliber) {
        return switch (caliber) {
            case ROCKET -> Ordnance.ROCKET;
            case ROCKET_TBG -> Ordnance.ROCKET_THERMOBARIC;
            case GRENADE40 -> Ordnance.GRENADE_40MM;
            default -> type == GunType.RPG7 ? Ordnance.ROCKET : Ordnance.GRENADE_40MM;
        };
    }

    private static void launch(ServerLevel level, ServerPlayer player, GunType type, GunData data) {
        Ordnance kind = ordnanceFor(type, data.caliber());
        ItemStack round = new ItemStack(ModItems.AMMO.get(data.caliber()).get());
        OrdnanceEntity projectile = new OrdnanceEntity(level, player, round, kind);
        projectile.shootFromRotation(player, player.getXRot(), player.getYRot(), 0.0F,
                kind.velocity(), type.effectiveSpread(data, player.isShiftKeyDown()));
        if (kind.isRocket()) {
            // a rocket is a metre long: start it just ahead of the face so it never fills the screen on launch
            Vec3 look = player.getLookAngle();
            projectile.setPos(player.getX() + look.x * 1.1, player.getEyeY() - 0.2 + look.y * 1.1,
                    player.getZ() + look.z * 1.1);
            backblast(level, player, look);
        }
        level.addFreshEntity(projectile);
    }

    /** The RPG's backblast: a cone of smoke and flame thrown out behind the shooter. */
    private static void backblast(ServerLevel level, ServerPlayer player, Vec3 look) {
        Vec3 behind = player.getEyePosition().subtract(look.scale(1.4)).subtract(0.0, 0.2, 0.0);
        level.sendParticles(ParticleTypes.CLOUD, behind.x, behind.y, behind.z, 14, 0.35, 0.25, 0.35, 0.06);
        level.sendParticles(ParticleTypes.LARGE_SMOKE, behind.x, behind.y, behind.z, 10, 0.4, 0.3, 0.4, 0.03);
        level.sendParticles(ParticleTypes.FLAME, behind.x, behind.y, behind.z, 8, 0.2, 0.2, 0.2, 0.08);
    }

    // --- audio --------------------------------------------------------------------------------------------------

    /** Pitch of a shot: a charged railgun drops from a snap to a deep boom as the charge grows. */
    public static float shotPitch(GunType type, float charge) {
        return type.charges() ? 1.25F - 0.3F * Mth.clamp(charge, 0.0F, 1.0F) : 1.0F;
    }

    /** At or above this charge the railgun shot adds its overcharge boom. */
    public static final float OVERCHARGE = 0.85F;

    /**
     * Everyone else hears the shot: up close the gun's own sound, further out the distant version (suppressed shots
     * carry no further than their near range). The shooter's client already played it when the trigger went.
     */
    private static void shootSound(ServerLevel level, ServerPlayer player, GunType type, GunData data, float charge) {
        boolean suppressed = type.suppressed(data);
        float pitch = shotPitch(type, charge) * (0.97F + level.getRandom().nextFloat() * 0.06F);
        ModSounds.broadcast(level, player, player.getX(), player.getEyeY(), player.getZ(),
                ModSounds.fire(type, suppressed), suppressed ? null : ModSounds.distant(type), SoundSource.PLAYERS,
                pitch);
        if (type.charges() && charge >= OVERCHARGE) {
            ModSounds.broadcast(level, player, player.getX(), player.getEyeY(), player.getZ(),
                    ModSounds.RAILGUN_OVERCHARGE, null, SoundSource.PLAYERS, 1.0F);
        }

        // An unsuppressed shot is loud enough to pull every hostile within earshot towards the shooter.
        if (!suppressed) {
            double radius = Math.min(32.0, 10.0 + type.damage());
            for (net.minecraft.world.entity.Mob mob : level.getEntitiesOfClass(net.minecraft.world.entity.Mob.class,
                    player.getBoundingBox().inflate(radius))) {
                if (mob.getTarget() == null && mob.distanceToSqr(player) > 16.0) {
                    mob.setTarget(player);
                }
            }
        }
    }

    /** Used by the HUD and the game tests: the spread a shot would actually have right now. */
    public static float currentSpread(ServerPlayer player, GunType type, GunData data) {
        return Mth.clamp(type.effectiveSpread(data, player.isShiftKeyDown())
                * (player.isSprinting() ? 1.8F : 1.0F), 0.0F, 45.0F);
    }
}
