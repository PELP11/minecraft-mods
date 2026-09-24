package com.afjan.arsenal.vehicle;

import org.jspecify.annotations.Nullable;

import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.entity.PartEntity;

/**
 * One hitbox of the F-14 (nose, cockpit, wings, tail...). The jet is 19 blocks long and rolls in every direction, far
 * more than one axis-aligned box can cover, so like the ender dragon it is a multipart entity: these boxes follow the
 * airframe every tick, catch bullets and missiles and pass the damage (and a right click to climb in) to the jet.
 */
public class F14Part extends PartEntity<F14Entity> {
    public final String name;
    /** Where the box's centre sits on the airframe (model space, metres); wing boxes move with the sweep. */
    public final Vec3 local;
    private final EntityDimensions size;

    public F14Part(F14Entity jet, String name, Vec3 local, float width, float height) {
        super(jet);
        this.name = name;
        this.local = local;
        this.size = EntityDimensions.scalable(width, height);
        this.refreshDimensions();
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder entityData) {
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
    }

    @Override
    public boolean isPickable() {
        return true;
    }

    @Override
    public @Nullable ItemStack getPickResult() {
        return this.getParent().getPickResult();
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
        return !this.isInvulnerableToBase(source) && this.getParent().hurtFromPart(level, this, source, damage);
    }

    @Override
    public InteractionResult interact(Player player, InteractionHand hand, Vec3 location) {
        return this.getParent().interact(player, hand, location);
    }

    @Override
    public boolean is(Entity other) {
        return this == other || this.getParent() == other;
    }

    @Override
    public EntityDimensions getDimensions(Pose pose) {
        return this.size;
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }
}
