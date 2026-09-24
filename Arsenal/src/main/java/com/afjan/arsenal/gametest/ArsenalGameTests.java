package com.afjan.arsenal.gametest;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import com.afjan.arsenal.Arsenal;
import com.afjan.arsenal.block.NukeBlock;
import com.afjan.arsenal.combat.Ballistics;
import com.afjan.arsenal.combat.BlastScheduler;
import com.afjan.arsenal.combat.Detonations;
import com.afjan.arsenal.combat.Ordnance;
import com.afjan.arsenal.combat.RailCharge;
import com.afjan.arsenal.combat.Reloading;
import com.afjan.arsenal.craft.Blueprints;
import com.afjan.arsenal.entity.OrdnanceEntity;
import com.afjan.arsenal.gun.Attachment;
import com.afjan.arsenal.gun.Caliber;
import com.afjan.arsenal.gun.GunData;
import com.afjan.arsenal.gun.GunType;
import com.afjan.arsenal.item.Guns;
import com.afjan.arsenal.registry.ModBlocks;
import com.afjan.arsenal.registry.ModItems;
import com.afjan.arsenal.vehicle.Store;
import com.mojang.authlib.GameProfile;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.FunctionGameTestInstance;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestData;
import net.minecraft.gametest.framework.TestEnvironmentDefinition;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * In-game tests run by {@code gradlew runGameTestServer} (development only). Players are NeoForge fake players:
 * real server players that do not tick, so anything time-based is driven by calling the tick methods directly.
 */
public final class ArsenalGameTests {
    private static final DeferredRegister<Consumer<GameTestHelper>> FUNCTIONS =
            DeferredRegister.create(Registries.TEST_FUNCTION, Arsenal.MODID);
    private static final List<Test> TESTS = new ArrayList<>();

    private record Test(DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> function, int maxTicks,
            String structure) {}

    static {
        test("gun_fires", ArsenalGameTests::gunFires);
        test("empty_gun_does_nothing", ArsenalGameTests::emptyGunDoesNothing);
        test("headshots_hurt_more", ArsenalGameTests::headshotsHurtMore);
        test("railgun_pierces", ArsenalGameTests::railgunPierces);
        test("railgun_charge_scales_damage", ArsenalGameTests::railgunChargeScalesDamage);
        test("rpg_fires_loaded_rocket", ArsenalGameTests::rpgFiresLoadedRocket);
        test("thermobaric_rocket_hits_harder", ArsenalGameTests::thermobaricRocketHitsHarder);
        test("reload_takes_ammo", ArsenalGameTests::reloadTakesAmmo);
        test("reload_switches_caliber", ArsenalGameTests::reloadSwitchesCaliber);
        test("attachments_change_stats", ArsenalGameTests::attachmentsChangeStats);
        test("attachments_are_one_per_slot", ArsenalGameTests::attachmentsAreOnePerSlot);
        test("workbench_builds", ArsenalGameTests::workbenchBuilds);
        test("workbench_refuses_without_materials", ArsenalGameTests::workbenchRefuses);
        test("every_item_has_a_blueprint", ArsenalGameTests::everyItemHasABlueprint);
        test("frag_grenade_hurts", ArsenalGameTests::fragGrenadeHurts);
        test("flashbang_blinds", ArsenalGameTests::flashbangBlinds);
        test("crater_removes_blocks", ArsenalGameTests::craterRemovesBlocks);
        test("scheduled_tasks_can_reschedule", ArsenalGameTests::scheduledTasksCanReschedule);
        test("nuke_countdown_runs", ArsenalGameTests::nukeCountdownRuns);
        test("stuck_nuke_can_be_rearmed", ArsenalGameTests::stuckNukeCanBeRearmed);
        test("smoke_cloud_lingers", ArsenalGameTests::smokeCloudLingers);

        // the F-14 (JetGameTests), in an arena big enough to park it
        test("jet_takes_off", JetGameTests::takesOff);
        test("jet_turns_level", JetGameTests::turnsLevel);
        test("jet_never_stalls_itself", JetGameTests::neverStallsItself);
        jetTest("jet_rolls_out_of_its_item", JetGameTests::rollsOutOfItsItem);
        jetTest("jet_rearms_from_items", JetGameTests::rearmsFromItems);
        jetTest("jet_packs_into_its_item", JetGameTests::packsIntoItsItem);
        jetTest("jet_cannon_hits", JetGameTests::cannonHits);
        jetTest("jet_sidewinder_runs_down_its_target", JetGameTests::sidewinderRunsDownItsTarget);
        jetTest("jet_hitboxes_pass_damage_on", JetGameTests::hitboxesPassDamageOn);
    }

    private ArsenalGameTests() {}

    private static void test(String name, Consumer<GameTestHelper> body) {
        test(name, body, 100);
    }

    private static void test(String name, Consumer<GameTestHelper> body, int maxTicks) {
        TESTS.add(new Test(FUNCTIONS.register(name, () -> body), maxTicks, "test_arena"));
    }

    private static void jetTest(String name, Consumer<GameTestHelper> body) {
        TESTS.add(new Test(FUNCTIONS.register(name, () -> body), 100, "jet_arena"));
    }

    public static void register(IEventBus modBus) {
        FUNCTIONS.register(modBus);
        modBus.addListener(ArsenalGameTests::registerTests);
    }

    private static void registerTests(RegisterGameTestsEvent event) {
        Holder<TestEnvironmentDefinition<?>> environment = event.registerEnvironment(Arsenal.id("default"));
        for (Test test : TESTS) {
            Identifier arena = Arsenal.id(test.structure());
            event.registerTest(test.function().getId(), new FunctionGameTestInstance(test.function().getKey(),
                    new TestData<>(environment, arena, test.maxTicks(), 1, true)));
        }
    }

    // --- shooting -----------------------------------------------------------------------------------------------

    private static void gunFires(GameTestHelper helper) {
        FakePlayer shooter = player(helper, 2, 1, 2, 0.0F, 0.0F);
        ItemStack gun = loaded(shooter, GunType.AK47, 30);
        // four blocks: the AK's 2.6 degree hip-fire spread missed a zombie at eight blocks about one shot in ten
        Zombie target = helper.spawn(EntityTypes.ZOMBIE, new BlockPos(2, 1, 6));
        aimAt(shooter, target);
        float before = target.getHealth();

        helper.assertTrue(Ballistics.fire(shooter, InteractionHand.MAIN_HAND), "the AK-47 should have fired");
        helper.assertTrue(target.getHealth() < before, "the zombie should have been hit");
        helper.assertValueEqual(Guns.dataOf(gun).ammo(), 29, "rounds left after one shot");
        helper.succeed();
    }

    private static void emptyGunDoesNothing(GameTestHelper helper) {
        FakePlayer shooter = player(helper, 2, 1, 2, 0.0F, 0.0F);
        loaded(shooter, GunType.M4A1, 0);
        Zombie target = helper.spawn(EntityTypes.ZOMBIE, new BlockPos(2, 1, 10));
        aimAt(shooter, target);
        float before = target.getHealth();

        helper.assertFalse(Ballistics.fire(shooter, InteractionHand.MAIN_HAND), "an empty gun must not fire");
        helper.assertValueEqual(target.getHealth(), before, "the zombie's health");
        helper.succeed();
    }

    private static void headshotsHurtMore(GameTestHelper helper) {
        FakePlayer shooter = player(helper, 2, 1, 2, 0.0F, 0.0F);
        Zombie body = helper.spawn(EntityTypes.ZOMBIE, new BlockPos(2, 1, 8));
        Zombie head = helper.spawn(EntityTypes.ZOMBIE, new BlockPos(6, 1, 8));
        loaded(shooter, GunType.M1911, 7);
        shooter.getCooldowns().removeCooldown(shooter.getCooldowns().getCooldownGroup(shooter.getMainHandItem()));
        lookAt(shooter, body.position().add(0.0, body.getBbHeight() * 0.3, 0.0));
        Ballistics.fire(shooter, InteractionHand.MAIN_HAND);
        float bodyDamage = body.getMaxHealth() - body.getHealth();

        loaded(shooter, GunType.M1911, 7);
        shooter.getCooldowns().removeCooldown(shooter.getCooldowns().getCooldownGroup(shooter.getMainHandItem()));
        lookAt(shooter, head.position().add(0.0, head.getBbHeight() * 0.9, 0.0));
        Ballistics.fire(shooter, InteractionHand.MAIN_HAND);
        float headDamage = head.getMaxHealth() - head.getHealth();

        helper.assertTrue(bodyDamage > 0.0F, "the body shot should have connected");
        helper.assertTrue(headDamage > bodyDamage, "a headshot should hurt more than a body shot");
        helper.succeed();
    }

    private static void railgunPierces(GameTestHelper helper) {
        FakePlayer shooter = player(helper, 2, 1, 1, 0.0F, 0.0F);
        loaded(shooter, GunType.RAILGUN, 3);
        Zombie near = helper.spawn(EntityTypes.ZOMBIE, new BlockPos(2, 1, 6));
        Zombie far = helper.spawn(EntityTypes.ZOMBIE, new BlockPos(2, 1, 10));
        near.setHealth(200.0F);
        far.setHealth(200.0F);
        lookAt(shooter, far.position().add(0.0, far.getBbHeight() * 0.5, 0.0));

        // 70 %: pierces several bodies, but stays under the charge that adds a shockwave where the slug hits a wall
        // (the tests run side by side, and a blast out there could reach another one)
        Ballistics.fire(shooter, InteractionHand.MAIN_HAND, 0.7F);
        helper.assertTrue(near.getHealth() < 200.0F || near.isDeadOrDying(), "the first zombie should be hit");
        helper.assertTrue(far.getHealth() < 200.0F || far.isDeadOrDying(), "the slug should pierce through to the second");
        helper.succeed();
    }

    /** A tap of the trigger fires a weak slug; holding it until the capacitors fill hits several times harder. */
    private static void railgunChargeScalesDamage(GameTestHelper helper) {
        float tap = railgunDamage(helper, 2, 0.0F);
        float charged = railgunDamage(helper, 12, 0.7F);
        helper.assertTrue(tap > 0.0F, "even an uncharged slug should hit");
        helper.assertTrue(charged > tap * 3.0F, "a charged shot should hit far harder than a tap (" + tap + " vs "
                + charged + ")");
        helper.assertTrue(RailCharge.release(player(helper, 2, 1, 2, 0.0F, 0.0F)) == 0.0F,
                "letting go without having charged is no charge at all");
        helper.succeed();
    }

    private static float railgunDamage(GameTestHelper helper, int x, float charge) {
        FakePlayer shooter = player(helper, x, 1, 1, 0.0F, 0.0F);
        loaded(shooter, GunType.RAILGUN, 3);
        Zombie target = helper.spawnWithNoFreeWill(EntityTypes.ZOMBIE, new BlockPos(x, 1, 4));
        target.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000.0);
        target.getAttribute(Attributes.ARMOR).setBaseValue(0.0);
        target.setHealth(1000.0F);
        // low and close: the slug goes on into the ground right behind the target, not off into the other tests
        lookAt(shooter, target.position().add(0.0, target.getBbHeight() * 0.3, 0.0));
        helper.assertTrue(Ballistics.fire(shooter, InteractionHand.MAIN_HAND, charge), "the railgun should fire");
        return 1000.0F - target.getHealth();
    }

    /** Sneak + R loads the thermobaric rocket: the RPG must launch that one, not the HEAT round. */
    private static void rpgFiresLoadedRocket(GameTestHelper helper) {
        FakePlayer shooter = player(helper, 8, 1, 2, 0.0F, 0.0F);
        ItemStack rpg = new ItemStack(ModItems.GUNS.get(GunType.RPG7).get());
        Guns.set(rpg, new GunData(1, Caliber.ROCKET_TBG.ordinal(), 0));
        shooter.setItemInHand(InteractionHand.MAIN_HAND, rpg);

        helper.assertTrue(Ballistics.fire(shooter, InteractionHand.MAIN_HAND), "the RPG should fire");
        List<OrdnanceEntity> rockets = helper.getLevel().getEntitiesOfClass(OrdnanceEntity.class,
                shooter.getBoundingBox().inflate(6.0));
        // gone before it ever ticks, so no rocket flies off into the other tests
        rockets.forEach(rocket -> rocket.discard());
        helper.assertValueEqual(rockets.size(), 1, "rockets launched");
        helper.assertTrue(rockets.getFirst().kind() == Ordnance.ROCKET_THERMOBARIC,
                "the RPG should launch the thermobaric rocket it was loaded with");
        helper.assertTrue(rockets.getFirst().kind().isRocket(), "a TBG-7V flies like a rocket");
        helper.succeed();
    }

    /** The thermobaric rocket was asked for as at least three times the power of the standard one. */
    private static void thermobaricRocketHitsHarder(GameTestHelper helper) {
        helper.assertTrue(Detonations.THERMOBARIC_ROCKET_POWER >= 3.0F * Detonations.ROCKET_POWER,
                "blast power at least three times the PG-7V's");
        helper.assertTrue(Detonations.THERMOBARIC_ROCKET_DAMAGE >= 3.0F * Detonations.ROCKET_DAMAGE,
                "area damage at least three times the PG-7V's");
        helper.assertTrue(Detonations.THERMOBARIC_ROCKET_RADIUS > Detonations.ROCKET_RADIUS, "a wider blast");
        // (not detonated here: a blast this size would wreck the tests running next to this one)
        helper.succeed();
    }

    // --- reloading ----------------------------------------------------------------------------------------------

    private static void reloadTakesAmmo(GameTestHelper helper) {
        FakePlayer shooter = player(helper, 2, 1, 2, 0.0F, 0.0F);
        ItemStack gun = loaded(shooter, GunType.AK47, 0);
        shooter.getInventory().add(new ItemStack(ModItems.AMMO.get(Caliber.MM762).get(), 40));

        Reloading.start(shooter, InteractionHand.MAIN_HAND);
        helper.assertTrue(Reloading.isReloading(shooter), "the reload should have started");
        shooter.tickCount += GunType.AK47.reloadTicks() + 1;
        Reloading.tick(shooter);

        helper.assertValueEqual(Guns.dataOf(gun).ammo(), 30, "a full magazine");
        helper.assertValueEqual(count(shooter, ModItems.AMMO.get(Caliber.MM762).get()), 10, "rounds left in the pack");
        helper.succeed();
    }

    private static void reloadSwitchesCaliber(GameTestHelper helper) {
        FakePlayer shooter = player(helper, 2, 1, 2, 0.0F, 0.0F);
        ItemStack gun = loaded(shooter, GunType.REMINGTON870, 0);
        shooter.getInventory().add(new ItemStack(ModItems.AMMO.get(Caliber.SLUG).get(), 12));

        Reloading.start(shooter, InteractionHand.MAIN_HAND);
        shooter.tickCount += GunType.REMINGTON870.reloadTicks() + 1;
        Reloading.tick(shooter);

        GunData data = Guns.dataOf(gun);
        helper.assertTrue(data.caliber() == Caliber.SLUG, "the shotgun should have loaded slugs");
        helper.assertValueEqual(GunType.REMINGTON870.effectivePellets(data), 1, "pellets fired by a slug");
        helper.assertTrue(GunType.REMINGTON870.effectiveDamage(data) > GunType.REMINGTON870.damage(),
                "a slug should hit harder than a single pellet of buckshot");
        helper.succeed();
    }

    // --- attachments --------------------------------------------------------------------------------------------

    private static void attachmentsChangeStats(GameTestHelper helper) {
        GunData plain = new GunData(0, Caliber.MM762.ordinal(), 0);
        GunData extended = plain.with(Attachment.EXTENDED_MAG);
        GunData suppressed = plain.with(Attachment.SUPPRESSOR);
        GunData scoped = plain.with(Attachment.ACOG_SCOPE);

        helper.assertTrue(GunType.AK47.effectiveMagazine(extended) > GunType.AK47.effectiveMagazine(plain),
                "an extended magazine should hold more");
        helper.assertTrue(GunType.AK47.effectiveDamage(suppressed) < GunType.AK47.effectiveDamage(plain),
                "a suppressor should cost a little damage");
        helper.assertTrue(GunType.AK47.suppressed(suppressed), "the gun should read as suppressed");
        helper.assertTrue(GunType.AK47.effectiveSpread(scoped, false) < GunType.AK47.effectiveSpread(plain, false),
                "an optic should tighten the group");
        helper.assertTrue(GunType.AK47.zoom(scoped) > 0.0F, "an optic should give a zoom");
        helper.succeed();
    }

    private static void attachmentsAreOnePerSlot(GameTestHelper helper) {
        GunData data = new GunData(0, 0, 0)
                .with(Attachment.SUPPRESSOR)
                .with(Attachment.MUZZLE_BRAKE)
                .with(Attachment.RED_DOT);

        helper.assertFalse(data.has(Attachment.SUPPRESSOR), "fitting a second muzzle part should replace the first");
        helper.assertTrue(data.has(Attachment.MUZZLE_BRAKE), "the newer muzzle part should be fitted");
        helper.assertTrue(data.has(Attachment.RED_DOT), "a part in another slot should stay");
        helper.assertValueEqual(data.attachments().size(), 2, "fitted parts");
        helper.assertValueEqual(data.without(Attachment.RED_DOT).attachments().size(), 1, "parts after stripping one");
        helper.succeed();
    }

    // --- the workbench ------------------------------------------------------------------------------------------

    private static void workbenchBuilds(GameTestHelper helper) {
        FakePlayer smith = player(helper, 2, 1, 2, 0.0F, 0.0F);
        Blueprints.Blueprint barrel = find(helper, ModItems.GUN_BARREL.get());
        smith.getInventory().add(new ItemStack(ModItems.STEEL_INGOT.get(), 64));

        helper.assertValueEqual(Blueprints.build(smith, barrel, 1), 1, "blueprints built");
        helper.assertValueEqual(count(smith, ModItems.GUN_BARREL.get()), 1, "barrels made");
        helper.assertValueEqual(count(smith, ModItems.STEEL_INGOT.get()), 61, "steel left");
        helper.succeed();
    }

    private static void workbenchRefuses(GameTestHelper helper) {
        FakePlayer smith = player(helper, 2, 1, 2, 0.0F, 0.0F);
        Blueprints.Blueprint nuke = find(helper, ModItems.TACTICAL_NUKE.get());

        helper.assertValueEqual(Blueprints.affordable(smith, nuke, 1), 0, "nukes an empty inventory can afford");
        helper.assertValueEqual(Blueprints.build(smith, nuke, 1), 0, "nukes built from nothing");
        helper.assertValueEqual(count(smith, ModItems.TACTICAL_NUKE.get()), 0, "nukes in the inventory");
        helper.succeed();
    }

    /** Every weapon, round, part and bomb must be reachable at the bench, or it would be uncraftable. */
    private static void everyItemHasABlueprint(GameTestHelper helper) {
        Set<Item> results = new HashSet<>();
        for (Blueprints.Blueprint blueprint : Blueprints.ALL) {
            results.add(blueprint.result().get());
        }
        for (GunType type : GunType.values()) {
            helper.assertTrue(results.contains(ModItems.GUNS.get(type).get()), "no blueprint for " + type.path());
        }
        for (Caliber caliber : Caliber.values()) {
            helper.assertTrue(results.contains(ModItems.AMMO.get(caliber).get()), "no blueprint for " + caliber.path());
        }
        for (Attachment attachment : Attachment.values()) {
            helper.assertTrue(results.contains(ModItems.ATTACHMENTS.get(attachment).get()),
                    "no blueprint for " + attachment.path());
        }
        for (Ordnance kind : Ordnance.values()) {
            if (kind.thrownByHand()) {
                helper.assertTrue(results.contains(ModItems.ORDNANCE_ITEMS.get(kind).get()),
                        "no blueprint for " + kind.path());
            }
        }
        helper.assertTrue(results.contains(ModItems.TACTICAL_NUKE.get()), "no blueprint for the nuke");
        helper.assertTrue(results.contains(ModItems.F14_TOMCAT.get()), "no blueprint for the F-14");
        for (Store store : Store.values()) {
            helper.assertTrue(results.contains(ModItems.JET_STORES.get(store).get()), "no blueprint for " + store.itemPath());
        }
        helper.assertTrue(results.contains(ModItems.CANNON_SHELLS.get()), "no blueprint for the cannon shells");
        helper.assertTrue(results.contains(ModItems.FLARE_CARTRIDGES.get()), "no blueprint for the flares");
        helper.succeed();
    }

    // --- ordnance -----------------------------------------------------------------------------------------------

    private static void fragGrenadeHurts(GameTestHelper helper) {
        Zombie target = helper.spawn(EntityTypes.ZOMBIE, new BlockPos(8, 2, 8));
        target.setHealth(200.0F);
        Vec3 at = helper.absoluteVec(new Vec3(8.0, 2.5, 8.0));

        Detonations.detonate(helper.getLevel(), Ordnance.FRAG, at, null);
        helper.assertTrue(target.getHealth() < 200.0F || target.isDeadOrDying(),
                "a frag grenade at its feet should hurt the zombie");
        helper.succeed();
    }

    private static void flashbangBlinds(GameTestHelper helper) {
        Zombie target = helper.spawn(EntityTypes.ZOMBIE, new BlockPos(8, 2, 8));
        Vec3 at = helper.absoluteVec(new Vec3(8.0, 2.5, 8.0));

        Detonations.detonate(helper.getLevel(), Ordnance.FLASHBANG, at, null);
        helper.assertTrue(target.hasEffect(MobEffects.BLINDNESS), "a flashbang should blind");
        helper.assertTrue(target.hasEffect(MobEffects.NAUSEA), "a flashbang should disorient");
        helper.assertTrue(target.getHealth() >= target.getMaxHealth() - 0.01F, "a flashbang should not wound");
        helper.succeed();
    }

    private static void craterRemovesBlocks(GameTestHelper helper) {
        BlockPos centre = new BlockPos(8, 3, 8);
        for (int y = 1; y <= 5; y++) {
            helper.setBlock(new BlockPos(8, y, 8), Blocks.STONE);
        }
        helper.assertBlockPresent(Blocks.STONE, new BlockPos(8, 3, 8));

        // no clear() first: tests run side by side, and it would wipe the other tests' scheduled tasks
        BlastScheduler.crater(helper.getLevel(), helper.absolutePos(centre), 3, 3, 3, 0.0F);
        BlastScheduler.finishNow();

        helper.assertBlockPresent(Blocks.AIR, new BlockPos(8, 3, 8));
        helper.assertBlockPresent(Blocks.AIR, new BlockPos(8, 2, 8));
        helper.succeed();
    }

    // --- the delayed-task queue: tasks that re-schedule themselves used to crash the server ----------------------

    /** A chain of tasks that each schedule the next from inside the queue, like the nuke's countdown. */
    private static void scheduledTasksCanReschedule(GameTestHelper helper) {
        AtomicInteger steps = new AtomicInteger();
        countdown(helper.getLevel(), steps, 3);
        helper.succeedWhen(() -> helper.assertValueEqual(steps.get(), 3, "countdown steps run"));
    }

    private static void countdown(ServerLevel level, AtomicInteger steps, int left) {
        if (left > 0) {
            BlastScheduler.after(level, 2, () -> {
                steps.incrementAndGet();
                countdown(level, steps, left - 1);
            });
        }
    }

    /** Arms a nuke with redstone, lets the countdown run past its first re-scheduled step, then defuses it. */
    private static void nukeCountdownRuns(GameTestHelper helper) {
        BlockPos pos = new BlockPos(8, 1, 8);
        helper.setBlock(pos.east(), Blocks.REDSTONE_BLOCK);
        helper.setBlock(pos, ModBlocks.TACTICAL_NUKE.get());
        helper.runAfterDelay(30, () -> {
            boolean armed = helper.getBlockState(pos).getOptionalValue(NukeBlock.ARMED).orElse(false);
            // defuse before asserting, so a failure can never leave a live warhead in the test world
            helper.setBlock(pos, Blocks.AIR);
            helper.assertTrue(armed, "the nuke should still be counting down");
            helper.succeed();
        });
    }

    /** A warhead saved as armed without its countdown (world closed mid-countdown) arms again when powered. */
    private static void stuckNukeCanBeRearmed(GameTestHelper helper) {
        BlockPos pos = new BlockPos(8, 1, 8);
        helper.setBlock(pos, ModBlocks.TACTICAL_NUKE.get().defaultBlockState().setValue(NukeBlock.ARMED, true));
        BlockPos absolute = helper.absolutePos(pos);
        helper.assertFalse(NukeBlock.isCounting(helper.getLevel(), absolute), "a reloaded warhead has no countdown");
        helper.setBlock(pos.east(), Blocks.REDSTONE_BLOCK);
        boolean counting = NukeBlock.isCounting(helper.getLevel(), absolute);
        helper.setBlock(pos, Blocks.AIR);
        helper.assertTrue(counting, "powering it should start the countdown again");
        helper.succeed();
    }

    /** The smoke cloud re-arms itself every three seconds: its second pulse must blind again. */
    private static void smokeCloudLingers(GameTestHelper helper) {
        Zombie inside = helper.spawnWithNoFreeWill(EntityTypes.ZOMBIE, new BlockPos(8, 1, 10));
        Detonations.detonate(helper.getLevel(), Ordnance.SMOKE, helper.absoluteVec(new Vec3(8.5, 1.0, 8.5)), null);
        helper.runAfterDelay(75, () -> {
            helper.assertTrue(inside.hasEffect(MobEffects.BLINDNESS), "the cloud's second pulse should blind again");
            helper.succeed();
        });
    }

    // --- helpers ------------------------------------------------------------------------------------------------

    private static FakePlayer player(GameTestHelper helper, double x, double y, double z, float yaw, float pitch) {
        FakePlayer player = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), "arsenal_test"));
        Vec3 pos = helper.absoluteVec(new Vec3(x, y, z));
        player.snapTo(pos.x, pos.y, pos.z, yaw, pitch);
        player.setYHeadRot(yaw);
        player.getInventory().clearContent();
        Reloading.cancel(player);
        return player;
    }

    private static ItemStack loaded(FakePlayer player, GunType type, int rounds) {
        ItemStack gun = new ItemStack(ModItems.GUNS.get(type).get());
        Guns.set(gun, new GunData(rounds, type.calibers().getFirst().ordinal(), 0));
        player.setItemInHand(InteractionHand.MAIN_HAND, gun);
        return gun;
    }

    private static void aimAt(FakePlayer shooter, net.minecraft.world.entity.Entity target) {
        lookAt(shooter, target.position().add(0.0, target.getBbHeight() * 0.5, 0.0));
    }

    private static void lookAt(FakePlayer shooter, Vec3 point) {
        Vec3 delta = point.subtract(shooter.getEyePosition());
        double flat = Math.sqrt(delta.x * delta.x + delta.z * delta.z);
        float yaw = (float) (Math.toDegrees(Math.atan2(delta.z, delta.x)) - 90.0);
        float pitch = (float) -Math.toDegrees(Math.atan2(delta.y, flat));
        shooter.snapTo(shooter.getX(), shooter.getY(), shooter.getZ(), yaw, pitch);
        shooter.setYHeadRot(yaw);
    }

    private static int count(FakePlayer player, Item item) {
        int total = 0;
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (stack.is(item)) {
                total += stack.getCount();
            }
        }
        return total;
    }

    private static Blueprints.Blueprint find(GameTestHelper helper, Item result) {
        for (Blueprints.Blueprint blueprint : Blueprints.ALL) {
            if (blueprint.result().get() == result) {
                return blueprint;
            }
        }
        helper.fail("no blueprint produces " + result);
        return null;
    }
}
