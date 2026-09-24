package com.afjan.oreborn.ability;

import java.util.Comparator;
import java.util.List;

import org.jspecify.annotations.Nullable;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Who area abilities may hit: monsters and anything currently hunting the user. Players, pets, villagers and farm
 * animals are never caught in a blast.
 */
public final class Targets {
    private Targets() {}

    public static boolean isEnemy(LivingEntity user, Entity entity) {
        if (!(entity instanceof LivingEntity living) || !living.isAlive() || entity == user || entity.isSpectator()) {
            return false;
        }
        if (entity instanceof Player || entity instanceof ArmorStand) {
            return false;
        }
        return entity instanceof Enemy || entity instanceof Mob mob && mob.getTarget() == user;
    }

    public static List<LivingEntity> enemiesNear(LivingEntity user, Vec3 center, double radius) {
        AABB box = new AABB(center, center).inflate(radius);
        return user.level().getEntitiesOfClass(LivingEntity.class, box,
                e -> isEnemy(user, e) && center(e).distanceToSqr(center) <= radius * radius);
    }

    public static @Nullable LivingEntity nearestEnemy(LivingEntity user, Vec3 center, double radius, List<LivingEntity> exclude) {
        return enemiesNear(user, center, radius).stream()
                .filter(e -> !exclude.contains(e))
                .min(Comparator.comparingDouble(e -> center(e).distanceToSqr(center)))
                .orElse(null);
    }

    public static Vec3 center(Entity entity) {
        return entity.position().add(0.0, entity.getBbHeight() * 0.5, 0.0);
    }

    /** The first block the user looks at within range (or the end of the ray). */
    public static HitResult lookedAtBlock(LivingEntity user, double range) {
        Vec3 eye = user.getEyePosition();
        Vec3 end = eye.add(user.getViewVector(1.0F).scale(range));
        return user.level().clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, user));
    }

    /** The first living entity (any, not only enemies) in the user's line of sight within range, not behind blocks. */
    public static @Nullable LivingEntity lookedAtEntity(LivingEntity user, double range) {
        Vec3 eye = user.getEyePosition();
        Vec3 look = user.getViewVector(1.0F);
        Vec3 end = lookedAtBlock(user, range).getLocation();
        AABB box = user.getBoundingBox().expandTowards(look.scale(range)).inflate(1.0);
        EntityHitResult hit = ProjectileUtil.getEntityHitResult(user, eye, end, box,
                e -> e instanceof LivingEntity living && living.isAlive() && !e.isSpectator() && e != user, range * range);
        return hit != null && hit.getEntity() instanceof LivingEntity living ? living : null;
    }
}
