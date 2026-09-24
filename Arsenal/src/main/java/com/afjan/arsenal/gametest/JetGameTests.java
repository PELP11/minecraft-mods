package com.afjan.arsenal.gametest;

import java.util.List;
import java.util.UUID;

import com.afjan.arsenal.combat.Ordnance;
import com.afjan.arsenal.entity.OrdnanceEntity;
import com.afjan.arsenal.registry.ModEntities;
import com.afjan.arsenal.registry.ModItems;
import com.afjan.arsenal.vehicle.F14Entity;
import com.afjan.arsenal.vehicle.F14Part;
import com.afjan.arsenal.vehicle.FlightModel;
import com.afjan.arsenal.vehicle.JetWeapons;
import com.afjan.arsenal.vehicle.Store;
import com.mojang.authlib.GameProfile;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;

/**
 * The F-14. The flight model on its own (it takes off, turns level without losing its altitude and never flies itself
 * into a stall), then the jet in a 40 x 40 arena: rolled out of its item fully loaded, rearmed from items, packed back
 * up, its M61 hitting a zombie, a Sidewinder running one down, and damage through the hitboxes.
 */
final class JetGameTests {
    /** Where the jet parks in {@code jet_arena}: its nose points north with 19 blocks of room ahead. */
    private static final double JET_X = 20.5;
    private static final double JET_Z = 30.5;

    private JetGameTests() {}

    // --- the flight model ---------------------------------------------------------------------------------------

    static void takesOff(GameTestHelper helper) {
        FlightModel.State s = new FlightModel.State();
        s.onGround = true;
        s.flaps = true;
        s.throttle = 1.0;
        Vec3 pos = Vec3.ZERO;
        double liftOff = -1.0;
        for (int t = 0; t < 900; t++) {
            FlightModel.step(s, FlightModel.autopilot(s, direction(0.0, 10.0), 0.0));
            pos = pos.add(s.v);
            if (s.onGround && s.v.y > 0.01) {
                s.onGround = false; // what F14Entity.groundContact does when the wheels leave the runway
                liftOff = -pos.z;
            }
            if (!s.onGround && pos.y < -0.05) {
                helper.fail("the jet sank back onto the runway after lifting off");
                return;
            }
            s.flaps = s.flaps && s.v.length() < 2.8; // F14Entity.animate retracts them at speed
        }
        helper.assertTrue(liftOff > 60.0 && liftOff < 220.0, "lift-off at military power after " + (int) liftOff + " blocks");
        helper.assertTrue(pos.y > 30.0, "climbing away after take-off (height " + (int) pos.y + ")");
        helper.succeed();
    }

    static void turnsLevel(GameTestHelper helper) {
        FlightModel.State s = cruising(3.0);
        double y = 0.0;
        double lowest = 0.0;
        double highest = 0.0;
        Vec3 target = direction(90.0, 0.0);
        for (int t = 0; t < 300; t++) {
            FlightModel.step(s, FlightModel.autopilot(s, target, 0.0));
            y += s.v.y;
            lowest = Math.min(lowest, y);
            highest = Math.max(highest, y);
        }
        double error = Math.abs(((heading(s) - 90.0) % 360.0 + 540.0) % 360.0 - 180.0);
        helper.assertTrue(error < 5.0, "turned onto the new heading (" + (int) error + " degrees off)");
        helper.assertTrue(highest - lowest < 8.0, "held its altitude through the turn (" + (int) (highest - lowest)
                + " blocks up and down)");
        helper.succeed();
    }

    static void neverStallsItself(GameTestHelper helper) {
        // engines idle, nose held up: the autopilot trades speed for height, then keeps the wings flying
        FlightModel.State s = cruising(2.5);
        s.throttle = 0.0;
        double worst = 0.0;
        for (int t = 0; t < 400; t++) {
            FlightModel.step(s, FlightModel.autopilot(s, direction(0.0, 20.0), 0.0));
            worst = Math.max(worst, s.alpha);
        }
        helper.assertTrue(worst <= FlightModel.ALPHA_STALL, "the autopilot pulled the wings past the stall (alpha "
                + String.format("%.2f", worst) + ")");
        helper.succeed();
    }

    // --- the jet on the ground ----------------------------------------------------------------------------------

    static void rollsOutOfItsItem(GameTestHelper helper) {
        FakePlayer player = player(helper, JET_X, 1.0, JET_Z + 10.0, 180.0F);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.F14_TOMCAT.get()));
        BlockPos floor = helper.absolutePos(new BlockPos(20, 0, 30));
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(floor).add(0.0, 0.5, 0.0), Direction.UP, floor, false);
        ModItems.F14_TOMCAT.get().useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, hit));
        List<F14Entity> jets = jets(helper);
        helper.assertValueEqual(jets.size(), 1, "jets parked");
        F14Entity jet = jets.getFirst();
        helper.assertTrue(player.getMainHandItem().isEmpty(), "the item was used up");
        helper.assertTrue(jet.forward().z < -0.99, "the jet faces the way the player looked (north)");
        for (Store store : Store.values()) {
            helper.assertValueEqual(store.count(jet.loadout()), store.capacity(), store.label() + " rounds aboard");
        }
        helper.assertValueEqual(Store.flares(jet.loadout()), Store.MAX_FLARES, "flares aboard");
        helper.assertValueEqual(jet.gunAmmo(), F14Entity.GUN_ROUNDS, "20 mm rounds aboard");
        helper.succeed();
    }

    static void rearmsFromItems(GameTestHelper helper) {
        F14Entity jet = parked(helper, 0, 0);
        jet.damageDirect(60.0F);
        FakePlayer crew = player(helper, JET_X - 4.0, 1.0, JET_Z, 90.0F);
        rearm(jet, crew, new ItemStack(ModItems.JET_STORES.get(Store.AIM9).get(), 3));
        rearm(jet, crew, new ItemStack(ModItems.JET_STORES.get(Store.AIM9).get(), 3));
        rearm(jet, crew, new ItemStack(ModItems.JET_STORES.get(Store.AIM9).get(), 3));
        rearm(jet, crew, new ItemStack(ModItems.CANNON_SHELLS.get()));
        rearm(jet, crew, new ItemStack(ModItems.FLARE_CARTRIDGES.get()));
        rearm(jet, crew, new ItemStack(ModItems.STEEL_INGOT.get()));
        helper.assertValueEqual(Store.AIM9.count(jet.loadout()), Store.AIM9.capacity(), "Sidewinders loaded (no more than fit)");
        helper.assertValueEqual(jet.gunAmmo(), 225, "20 mm rounds loaded");
        helper.assertValueEqual(Store.flares(jet.loadout()), 12, "flares loaded");
        helper.assertValueEqual(jet.health(), F14Entity.MAX_HEALTH - 35.0F, "airframe patched with a steel ingot");
        helper.succeed();
    }

    static void packsIntoItsItem(GameTestHelper helper) {
        int loadout = Store.ZUNI.withCount(Store.AIM9.withCount(0, 1), 5);
        F14Entity jet = parked(helper, loadout, 300);
        FakePlayer crew = player(helper, JET_X - 4.0, 1.0, JET_Z, 90.0F);
        crew.setShiftKeyDown(true);
        jet.interact(crew, InteractionHand.MAIN_HAND, Vec3.ZERO);
        helper.assertTrue(jet.isRemoved(), "the jet was packed up");
        ItemStack item = ItemStack.EMPTY;
        for (int slot = 0; slot < crew.getInventory().getContainerSize(); slot++) {
            if (crew.getInventory().getItem(slot).is(ModItems.F14_TOMCAT.get())) {
                item = crew.getInventory().getItem(slot);
            }
        }
        helper.assertTrue(item.is(ModItems.F14_TOMCAT.get()), "the player got the jet back as an item");
        var tag = item.get(DataComponents.CUSTOM_DATA).copyTag();
        helper.assertValueEqual(tag.getIntOr("Loadout", -1), loadout, "the loadout travels with the item");
        helper.assertValueEqual(tag.getIntOr("GunAmmo", -1), 300, "the cannon rounds travel with the item");
        helper.succeed();
    }

    // --- weapons --------------------------------------------------------------------------------------------------

    static void cannonHits(GameTestHelper helper) {
        F14Entity jet = parked(helper, Store.full(), F14Entity.GUN_ROUNDS);
        Vec3 muzzle = jet.toWorld(F14Entity.GUN_MUZZLE);
        Vec3 ahead = helper.relativeVec(muzzle).add(0.0, 0.0, -12.0);
        Zombie zombie = helper.spawn(EntityTypes.ZOMBIE, new BlockPos((int) Math.floor(ahead.x), 1, (int) Math.floor(ahead.z)));
        zombie.snapTo(muzzle.x, helper.absoluteVec(new Vec3(0.0, 1.0, 0.0)).y, muzzle.z - 12.0, 0.0F, 0.0F);
        float before = zombie.getHealth();
        jet.setFlag(F14Entity.GUN, true);
        JetWeapons.tickGun(helper.getLevel(), jet);
        helper.assertTrue(zombie.getHealth() < before, "the 20 mm rounds hit the zombie");
        helper.assertValueEqual(jet.gunAmmo(), F14Entity.GUN_ROUNDS - JetWeapons.ROUNDS_PER_TICK, "rounds left");
        helper.succeed();
    }

    static void sidewinderRunsDownItsTarget(GameTestHelper helper) {
        F14Entity jet = parked(helper, Store.full(), F14Entity.GUN_ROUNDS);
        // (NeoForge's fake players cannot ride anything: the pilot stands by the jet, which is why a store remembers
        // the jet it came off rather than asking whether its owner still sits in it)
        FakePlayer pilot = player(helper, JET_X - 4.0, 1.0, JET_Z, 180.0F);
        Zombie zombie = helper.spawn(EntityTypes.ZOMBIE, new BlockPos(20, 1, 5));
        helper.assertTrue(JetWeapons.validTarget(jet, zombie, Store.AIM9, 1.0), "the zombie is in the seeker's cone");
        helper.assertTrue(JetWeapons.fireStore(helper.getLevel(), jet, pilot, Store.AIM9, zombie.getId()),
                "the Sidewinder left the rail");
        helper.assertValueEqual(Store.AIM9.count(jet.loadout()), Store.AIM9.capacity() - 1, "Sidewinders left");
        List<OrdnanceEntity> missiles = helper.getLevel().getEntitiesOfClass(OrdnanceEntity.class, helper.getBounds(),
                e -> e.kind() == Ordnance.AIM9);
        helper.assertValueEqual(missiles.size(), 1, "missiles in flight");
        helper.assertTrue(missiles.getFirst().target() == zombie, "the missile took the locked target");
        helper.succeedWhen(() -> helper.assertTrue(!zombie.isAlive(), "the missile killed the zombie"));
    }

    // --- damage ---------------------------------------------------------------------------------------------------

    static void hitboxesPassDamageOn(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        F14Entity jet = parked(helper, Store.full(), F14Entity.GUN_ROUNDS);
        F14Part wing = null;
        for (var part : jet.getParts()) {
            if (part instanceof F14Part p) {
                wing = p;
            }
        }
        helper.assertTrue(wing != null, "the jet has hitboxes");
        Zombie zombie = helper.spawn(EntityTypes.ZOMBIE, new BlockPos(2, 1, 2));
        wing.hurtServer(level, level.damageSources().mobAttack(zombie), 30.0F);
        helper.assertTrue(jet.health() < F14Entity.MAX_HEALTH, "a hit on a hitbox damages the jet");
        float damaged = jet.health();
        jet.repair(25.0F);
        helper.assertValueEqual(jet.health(), Math.min(F14Entity.MAX_HEALTH, damaged + 25.0F), "repaired");
        helper.succeed();
    }

    // --- helpers --------------------------------------------------------------------------------------------------

    /** A unit direction: heading clockwise from north (-z), pitch up from the horizon. */
    private static Vec3 direction(double headingDeg, double pitchDeg) {
        double h = Math.toRadians(headingDeg);
        double p = Math.toRadians(pitchDeg);
        return new Vec3(Math.sin(h) * Math.cos(p), Math.sin(p), -Math.cos(h) * Math.cos(p));
    }

    private static double heading(FlightModel.State s) {
        Vec3 f = FlightModel.forward(s.q);
        return Math.toDegrees(Math.atan2(f.x, -f.z));
    }

    /** Level flight to the north at {@code speed}, gear up, nose up by the angle of attack that carries 1 g. */
    private static FlightModel.State cruising(double speed) {
        FlightModel.State s = new FlightModel.State();
        s.gear = false;
        s.throttle = 1.0;
        s.v = new Vec3(0.0, 0.0, -speed);
        s.q.identity().rotateX(FlightModel.G / (FlightModel.K_LIFT * speed * speed));
        return s;
    }

    private static F14Entity parked(GameTestHelper helper, int loadout, int gunAmmo) {
        ServerLevel level = helper.getLevel();
        F14Entity jet = ModEntities.F14.get().create(level, EntitySpawnReason.TRIGGERED);
        Vec3 at = helper.absoluteVec(new Vec3(JET_X, 1.0 + F14Entity.GEAR_HEIGHT, JET_Z));
        jet.setPos(at.x, at.y, at.z);
        jet.setUpParked(180.0F, loadout, gunAmmo, F14Entity.MAX_HEALTH);
        level.addFreshEntity(jet);
        return jet;
    }

    private static List<F14Entity> jets(GameTestHelper helper) {
        return helper.getLevel().getEntitiesOfClass(F14Entity.class, helper.getBounds(), e -> !e.isRemoved());
    }

    private static void rearm(F14Entity jet, Player crew, ItemStack stack) {
        crew.setItemInHand(InteractionHand.MAIN_HAND, stack);
        jet.interact(crew, InteractionHand.MAIN_HAND, Vec3.ZERO);
    }

    private static FakePlayer player(GameTestHelper helper, double x, double y, double z, float yaw) {
        FakePlayer player = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), "arsenal_pilot"));
        Vec3 pos = helper.absoluteVec(new Vec3(x, y, z));
        player.snapTo(pos.x, pos.y, pos.z, yaw, 0.0F);
        player.setYHeadRot(yaw);
        player.getInventory().clearContent();
        player.setShiftKeyDown(false);
        if (player.isPassenger()) {
            player.stopRiding();
        }
        return player;
    }
}
