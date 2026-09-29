package com.afjan.hatchery.gametest;

import com.afjan.hatchery.Hatchery;
import com.afjan.hatchery.block.BrokenSpawnerBlock;
import com.afjan.hatchery.loot.SpawnEggModifier;
import com.afjan.hatchery.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.SpawnerBlockEntity;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.gametest.GameTest;
import net.minecraftforge.gametest.GameTestNamespace;
import net.minecraftforge.gametest.GameTestPrefix;

import java.util.ArrayList;
import java.util.List;

/**
 * Headless checks of the whole mod. Each method is one test; {@code tools/gen_tests.py} writes the matching
 * test_instance files (structure = an empty box that Forge generates, so no .nbt files are needed).
 */
@GameTestNamespace(Hatchery.MODID)
@GameTestPrefix("hatchery")
public final class HatcheryGameTests {
    private HatcheryGameTests() {
    }

    // ------------------------------------------------------------------------------------------------ helpers

    /** Item entities of {@code item} lying around the structure. */
    private static int dropped(GameTestHelper helper, Item item) {
        AABB box = new AABB(helper.absolutePos(BlockPos.ZERO)).inflate(10);
        int count = 0;
        for (ItemEntity drop : helper.getLevel().getEntitiesOfClass(ItemEntity.class, box, e -> e.getItem().is(item))) {
            count += drop.getItem().getCount();
        }
        return count;
    }

    private static void clearDrops(GameTestHelper helper) {
        AABB box = new AABB(helper.absolutePos(BlockPos.ZERO)).inflate(10);
        helper.getLevel().getEntitiesOfClass(ItemEntity.class, box).forEach(Entity::discard);
    }

    private static EntityType<?> spawnerMob(GameTestHelper helper, BlockPos rel) {
        if (!(helper.getBlockEntity(rel, SpawnerBlockEntity.class) instanceof SpawnerBlockEntity spawner)) return null;
        Entity shown = spawner.getSpawner().getOrCreateDisplayEntity(helper.getLevel(), helper.absolutePos(rel));
        return shown == null ? null : shown.getType();
    }

    private static void useOn(ServerPlayer player, GameTestHelper helper, BlockPos rel) {
        BlockPos pos = helper.absolutePos(rel);
        player.gameMode.useItemOn(player, helper.getLevel(), player.getMainHandItem(), InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false));
    }

    /** The loot of one death of {@code victim}, killed by {@code player} (or by nobody). */
    private static List<ItemStack> deathLoot(ServerLevel level, LivingEntity victim, ServerPlayer player, boolean playerKill) {
        LootTable table = level.getServer().reloadableRegistries().getLootTable(victim.getLootTable().orElseThrow());
        LootParams.Builder params = new LootParams.Builder(level)
                .withParameter(LootContextParams.THIS_ENTITY, victim)
                .withParameter(LootContextParams.ORIGIN, victim.position())
                .withParameter(LootContextParams.DAMAGE_SOURCE, playerKill ? player.damageSources().playerAttack(player) : level.damageSources().generic())
                .withOptionalParameter(LootContextParams.ATTACKING_ENTITY, playerKill ? player : null);
        if (playerKill) params.withParameter(LootContextParams.LAST_DAMAGE_PLAYER, player);
        return table.getRandomItems(params.create(LootContextParamSets.ENTITY));
    }

    private static int eggsIn(int rolls, ServerLevel level, LivingEntity victim, ServerPlayer player, boolean playerKill) {
        Item egg = SpawnEggModifier.eggOf(victim.getType()).orElseThrow().value();
        int eggs = 0;
        for (int i = 0; i < rolls; i++) {
            for (ItemStack stack : deathLoot(level, victim, player, playerKill)) if (stack.is(egg)) eggs += stack.getCount();
        }
        return eggs;
    }

    private static <T extends LivingEntity> T detached(GameTestHelper helper, EntityType<T> type) {
        T entity = type.create(helper.getLevel(), EntitySpawnReason.TRIGGERED);
        Vec3 at = Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(2, 1, 2)));
        entity.snapTo(at.x, at.y, at.z, 0.0F, 0.0F);
        return entity;
    }

    // ------------------------------------------------------------------------------------------------ spawners

    @GameTest
    public static void spawnerDropsBrokenSpawner(GameTestHelper helper) {
        ServerPlayer player = TestPlayers.survival(helper, new BlockPos(0, 1, 0), new ItemStack(Items.WOODEN_PICKAXE));
        BlockPos rel = new BlockPos(2, 1, 2);
        helper.setBlock(rel, Blocks.SPAWNER);
        helper.getBlockEntity(rel, SpawnerBlockEntity.class).setEntityId(EntityTypes.ZOMBIE, helper.getLevel().getRandom());
        player.gameMode.destroyBlock(helper.absolutePos(rel));
        helper.assertBlockNotPresent(Blocks.SPAWNER, rel);
        helper.assertValueEqual(dropped(helper, ModBlocks.BROKEN_SPAWNER_ITEM.get()), 1, "broken spawners dropped");
        helper.succeed();
    }

    @GameTest
    public static void spawnerByHandDropsNothing(GameTestHelper helper) {
        ServerPlayer player = TestPlayers.survival(helper, new BlockPos(0, 1, 0), ItemStack.EMPTY);
        BlockPos rel = new BlockPos(2, 1, 2);
        helper.setBlock(rel, Blocks.SPAWNER);
        player.gameMode.destroyBlock(helper.absolutePos(rel));
        helper.assertBlockNotPresent(Blocks.SPAWNER, rel);
        helper.assertValueEqual(dropped(helper, ModBlocks.BROKEN_SPAWNER_ITEM.get()), 0, "broken spawners dropped by hand");
        helper.succeed();
    }

    @GameTest
    public static void trialSpawnerDropsBrokenSpawner(GameTestHelper helper) {
        ServerPlayer player = TestPlayers.survival(helper, new BlockPos(0, 1, 0), ItemStack.EMPTY);
        helper.setBlock(new BlockPos(1, 1, 3), Blocks.TRIAL_SPAWNER);
        player.gameMode.destroyBlock(helper.absolutePos(new BlockPos(1, 1, 3)));
        helper.assertValueEqual(dropped(helper, ModBlocks.BROKEN_SPAWNER_ITEM.get()), 0, "trial spawner by hand");
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_PICKAXE));
        helper.setBlock(new BlockPos(3, 1, 3), Blocks.TRIAL_SPAWNER);
        player.gameMode.destroyBlock(helper.absolutePos(new BlockPos(3, 1, 3)));
        helper.assertValueEqual(dropped(helper, ModBlocks.BROKEN_SPAWNER_ITEM.get()), 1, "trial spawner with a pickaxe");
        helper.succeed();
    }

    @GameTest
    public static void brokenSpawnerDropsItself(GameTestHelper helper) {
        ServerPlayer player = TestPlayers.survival(helper, new BlockPos(0, 1, 0), ItemStack.EMPTY);
        helper.setBlock(new BlockPos(1, 1, 3), ModBlocks.BROKEN_SPAWNER.get());
        player.gameMode.destroyBlock(helper.absolutePos(new BlockPos(1, 1, 3)));
        helper.assertValueEqual(dropped(helper, ModBlocks.BROKEN_SPAWNER_ITEM.get()), 0, "broken spawner by hand");
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.STONE_PICKAXE));
        helper.setBlock(new BlockPos(3, 1, 3), ModBlocks.BROKEN_SPAWNER.get());
        player.gameMode.destroyBlock(helper.absolutePos(new BlockPos(3, 1, 3)));
        helper.assertValueEqual(dropped(helper, ModBlocks.BROKEN_SPAWNER_ITEM.get()), 1, "broken spawner with a pickaxe");
        helper.succeed();
    }

    @GameTest
    public static void eggRevivesBrokenSpawner(GameTestHelper helper) {
        ServerPlayer player = TestPlayers.survival(helper, new BlockPos(0, 1, 0), new ItemStack(Items.STICK));
        BlockPos rel = new BlockPos(2, 1, 2);
        helper.setBlock(rel, ModBlocks.BROKEN_SPAWNER.get());
        useOn(player, helper, rel);
        helper.assertBlockPresent(ModBlocks.BROKEN_SPAWNER.get(), rel);
        helper.assertValueEqual(player.getMainHandItem().getCount(), 1, "sticks after use");

        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.ZOMBIE_SPAWN_EGG, 2));
        useOn(player, helper, rel);
        helper.assertBlockPresent(Blocks.SPAWNER, rel);
        helper.assertValueEqual(spawnerMob(helper, rel), EntityTypes.ZOMBIE, "spawner mob");
        helper.assertValueEqual(player.getMainHandItem().getCount(), 1, "eggs left");
        helper.succeed();
    }

    @GameTest
    public static void revivedSpawnerCanBeMinedAgain(GameTestHelper helper) {
        ServerPlayer player = TestPlayers.survival(helper, new BlockPos(0, 1, 0), new ItemStack(Items.DIAMOND_PICKAXE));
        BlockPos rel = new BlockPos(2, 1, 2);
        helper.setBlock(rel, ModBlocks.BROKEN_SPAWNER.get());
        BrokenSpawnerBlock.revive(helper.getLevel(), helper.absolutePos(rel), EntityTypes.SKELETON, player);
        helper.assertValueEqual(spawnerMob(helper, rel), EntityTypes.SKELETON, "revived spawner mob");
        player.gameMode.destroyBlock(helper.absolutePos(rel));
        helper.assertBlockNotPresent(Blocks.SPAWNER, rel);
        helper.assertValueEqual(dropped(helper, ModBlocks.BROKEN_SPAWNER_ITEM.get()), 1, "broken spawner from a revived spawner");
        helper.succeed();
    }

    // ------------------------------------------------------------------------------------------------ spawn eggs

    @GameTest
    public static void everyMobFindsItsEgg(GameTestHelper helper) {
        helper.assertValueEqual(SpawnEggModifier.eggOf(EntityTypes.ZOMBIE).orElseThrow().value(), Items.ZOMBIE_SPAWN_EGG, "zombie egg");
        helper.assertValueEqual(SpawnEggModifier.eggOf(EntityTypes.COW).orElseThrow().value(), Items.COW_SPAWN_EGG, "cow egg");
        helper.assertValueEqual(SpawnEggModifier.eggOf(EntityTypes.BLAZE).orElseThrow().value(), Items.BLAZE_SPAWN_EGG, "blaze egg");
        helper.assertTrue(SpawnEggModifier.eggOf(EntityTypes.PLAYER).isEmpty(), "players have no spawn egg");
        helper.succeed();
    }

    /** 20,000 zombie deaths: 0.5% means about 100 eggs (4 standard deviations: 60..140). */
    @GameTest(maxTicks = 400)
    public static void eggsDropAtHalfAPercent(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = TestPlayers.survival(helper, new BlockPos(0, 1, 0), ItemStack.EMPTY);
        Zombie zombie = detached(helper, EntityTypes.ZOMBIE);
        int eggs = eggsIn(20000, level, zombie, player, true);
        helper.assertTrue(eggs >= 60 && eggs <= 140, "zombie eggs in 20000 player kills: " + eggs);
        helper.assertValueEqual(eggsIn(4000, level, zombie, player, false), 0, "eggs without a player kill");
        helper.succeed();
    }

    /** Looting III: 0.8% instead of 0.5% (30,000 kills: ~240 instead of ~150). */
    @GameTest(maxTicks = 400)
    public static void lootingRaisesTheChance(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ItemStack sword = new ItemStack(Items.DIAMOND_SWORD);
        sword.enchant(level.holderLookup(Registries.ENCHANTMENT).getOrThrow(Enchantments.LOOTING), 3);
        ServerPlayer player = TestPlayers.survival(helper, new BlockPos(0, 1, 0), sword);
        int eggs = eggsIn(30000, level, detached(helper, EntityTypes.ZOMBIE), player, true);
        helper.assertTrue(eggs >= 185 && eggs <= 300, "zombie eggs in 30000 Looting III kills: " + eggs);
        helper.succeed();
    }

    @GameTest(maxTicks = 400)
    public static void bossesNeverDropEggs(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = TestPlayers.survival(helper, new BlockPos(0, 1, 0), ItemStack.EMPTY);
        helper.assertValueEqual(eggsIn(6000, level, detached(helper, EntityTypes.WITHER), player, true), 0, "wither eggs");
        helper.assertValueEqual(eggsIn(6000, level, detached(helper, EntityTypes.ENDER_DRAGON), player, true), 0, "ender dragon eggs");
        helper.succeed();
    }

    @GameTest
    public static void droppedEggGlowsAndStays(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = TestPlayers.survival(helper, new BlockPos(0, 1, 0), ItemStack.EMPTY);
        Zombie zombie = helper.spawn(EntityTypes.ZOMBIE, new BlockPos(2, 1, 2));
        ItemEntity egg = new ItemEntity(level, zombie.getX(), zombie.getY(), zombie.getZ(), new ItemStack(Items.ZOMBIE_SPAWN_EGG));
        ItemEntity flesh = new ItemEntity(level, zombie.getX(), zombie.getY(), zombie.getZ(), new ItemStack(Items.ROTTEN_FLESH));
        List<ItemEntity> drops = new ArrayList<>(List.of(egg, flesh));
        LivingDropsEvent.BUS.post(new LivingDropsEvent(zombie, player.damageSources().playerAttack(player), drops, true));
        helper.assertTrue(egg.hasGlowingTag(), "the egg does not glow");
        helper.assertFalse(flesh.hasGlowingTag(), "rotten flesh glows");
        helper.succeed();
    }

    /** The whole chain in the world: 3,000 real player kills should give ~15 eggs, every one glowing. */
    @GameTest(maxTicks = 600)
    public static void playerKillsDropGlowingEggs(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = TestPlayers.survival(helper, new BlockPos(0, 1, 0), ItemStack.EMPTY);
        AABB box = new AABB(helper.absolutePos(BlockPos.ZERO)).inflate(10);
        int eggs = 0;
        for (int i = 0; i < 3000; i++) {
            Zombie zombie = helper.spawn(EntityTypes.ZOMBIE, new BlockPos(2, 1, 2));
            zombie.hurtServer(level, player.damageSources().playerAttack(player), 1000.0F);
            for (ItemEntity drop : level.getEntitiesOfClass(ItemEntity.class, box, e -> e.getItem().is(Items.ZOMBIE_SPAWN_EGG))) {
                helper.assertTrue(drop.hasGlowingTag(), "a dropped egg does not glow");
                eggs++;
            }
            clearDrops(helper);
            zombie.discard();
        }
        helper.assertTrue(eggs >= 1 && eggs <= 45, "eggs from 3000 zombie kills: " + eggs);
        helper.succeed();
    }
}
