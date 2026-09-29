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

    // ------------------------------------------------------------------------------------------------ modules

    private static SpawnerBlockEntity spawnerAt(GameTestHelper helper, BlockPos rel, EntityType<?> type) {
        helper.setBlock(rel, Blocks.SPAWNER);
        SpawnerBlockEntity spawner = helper.getBlockEntity(rel, SpawnerBlockEntity.class);
        spawner.setEntityId(type, helper.getLevel().getRandom());
        return spawner;
    }

    private static net.minecraft.nbt.CompoundTag spawnerTag(GameTestHelper helper, SpawnerBlockEntity spawner) {
        var out = net.minecraft.world.level.storage.TagValueOutput.createWithContext(net.minecraft.util.ProblemReporter.DISCARDING,
                helper.getLevel().registryAccess());
        spawner.getSpawner().save(out);
        return out.buildResult();
    }

    /** Runs one wave now (a player must be within 16 blocks). */
    private static void spawnWave(GameTestHelper helper, SpawnerBlockEntity spawner, BlockPos rel) {
        ServerLevel level = helper.getLevel();
        var tag = spawnerTag(helper, spawner);
        tag.putShort("Delay", (short) 0);
        spawner.getSpawner().load(level, helper.absolutePos(rel), net.minecraft.world.level.storage.TagValueInput.create(
                net.minecraft.util.ProblemReporter.DISCARDING, level.registryAccess(), tag));
        spawner.getSpawner().serverTick(level, helper.absolutePos(rel));
    }

    private static List<? extends Entity> cowsAround(GameTestHelper helper, BlockPos rel) {
        return helper.getLevel().getEntities(EntityTypes.COW, new AABB(helper.absolutePos(rel)).inflate(6), e -> true);
    }

    private static ItemStack modules(com.afjan.hatchery.spawner.Module module, int count) {
        return new ItemStack(ModBlocks.MODULES.get(module).get(), count);
    }

    @GameTest
    public static void modulesStackOnASpawner(GameTestHelper helper) {
        BlockPos rel = new BlockPos(2, 1, 2);
        SpawnerBlockEntity spawner = spawnerAt(helper, rel, EntityTypes.ZOMBIE);
        ServerPlayer player = TestPlayers.survival(helper, new BlockPos(0, 1, 0), modules(com.afjan.hatchery.spawner.Module.SWARM, 4));
        useOn(player, helper, rel);
        useOn(player, helper, rel);
        var installed = com.afjan.hatchery.spawner.SpawnerModules.of(spawner);
        helper.assertValueEqual(installed.swarm(), 2, "swarm level");
        helper.assertValueEqual(player.getMainHandItem().getCount(), 1, "modules left (1 + 2 used)");
        useOn(player, helper, rel);
        helper.assertValueEqual(com.afjan.hatchery.spawner.SpawnerModules.of(spawner).swarm(), 2, "level III with only 1 module");
        helper.assertValueEqual(player.getMainHandItem().getCount(), 1, "modules left after the refusal");
        var tag = spawnerTag(helper, spawner);
        helper.assertValueEqual(tag.getShortOr("SpawnCount", (short) 0), (short) 8, "spawn count");
        helper.assertValueEqual(tag.getShortOr("MaxNearbyEntities", (short) 0), (short) 16, "max nearby");

        player.setItemInHand(InteractionHand.MAIN_HAND, modules(com.afjan.hatchery.spawner.Module.HASTE, 1));
        useOn(player, helper, rel);
        tag = spawnerTag(helper, spawner);
        helper.assertValueEqual(tag.getShortOr("MinSpawnDelay", (short) 0), (short) 140, "min delay");
        helper.assertValueEqual(tag.getShortOr("MaxSpawnDelay", (short) 0), (short) 560, "max delay");
        helper.assertValueEqual(tag.getShortOr("SpawnCount", (short) 0), (short) 8, "swarm kept");
        helper.assertValueEqual(spawnerMob(helper, rel), EntityTypes.ZOMBIE, "mob kept");

        player.setItemInHand(InteractionHand.MAIN_HAND, modules(com.afjan.hatchery.spawner.Module.REDSTONE, 2));
        useOn(player, helper, rel);
        useOn(player, helper, rel);
        helper.assertTrue(com.afjan.hatchery.spawner.SpawnerModules.of(spawner).redstone(), "redstone not installed");
        helper.assertValueEqual(player.getMainHandItem().getCount(), 1, "redstone modules left (one-off)");
        helper.succeed();
    }

    /** A real wave from a cow spawner with Daylight and Frailty II: cows in daylight, at half health. */
    @GameTest(structure = "forge:empty7x5x7", skyAccess = true)
    public static void daylightAndFrailtyShapeTheWave(GameTestHelper helper) {
        BlockPos rel = new BlockPos(3, 2, 3);
        SpawnerBlockEntity spawner = spawnerAt(helper, rel, EntityTypes.COW);
        new com.afjan.hatchery.spawner.SpawnerModules(0, 0, 2, true, false).install(helper.getLevel(), helper.absolutePos(rel), spawner);
        helper.assertTrue(spawnerTag(helper, spawner).getCompoundOrEmpty("SpawnData").contains("custom_spawn_rules"), "no light rules");
        TestPlayers.survival(helper, new BlockPos(0, 1, 0), ItemStack.EMPTY);
        spawnWave(helper, spawner, rel);
        var cows = cowsAround(helper, rel);
        helper.assertFalse(cows.isEmpty(), "no cows in daylight");
        for (Entity cow : cows) {
            LivingEntity living = (LivingEntity) cow;
            helper.assertTrue(Math.abs(living.getHealth() - living.getMaxHealth() * 0.5F) < 0.01F, "cow health " + living.getHealth());
        }
        cows.forEach(Entity::discard);
        helper.succeed();
    }

    @GameTest(structure = "forge:empty7x5x7", skyAccess = true)
    public static void redstonePausesTheSpawner(GameTestHelper helper) {
        BlockPos rel = new BlockPos(3, 2, 3);
        SpawnerBlockEntity spawner = spawnerAt(helper, rel, EntityTypes.COW);
        new com.afjan.hatchery.spawner.SpawnerModules(0, 0, 0, true, true).install(helper.getLevel(), helper.absolutePos(rel), spawner);
        TestPlayers.survival(helper, new BlockPos(0, 1, 0), ItemStack.EMPTY);
        helper.setBlock(rel.east(), Blocks.REDSTONE_BLOCK);
        spawnWave(helper, spawner, rel);
        helper.assertTrue(cowsAround(helper, rel).isEmpty(), "a powered spawner spawned cows");
        helper.setBlock(rel.east(), Blocks.AIR);
        spawnWave(helper, spawner, rel);
        var cows = cowsAround(helper, rel);
        helper.assertFalse(cows.isEmpty(), "the spawner stayed off without power");
        cows.forEach(Entity::discard);
        helper.succeed();
    }

    @GameTest
    public static void minedSpawnerGivesModulesBack(GameTestHelper helper) {
        BlockPos rel = new BlockPos(2, 1, 2);
        SpawnerBlockEntity spawner = spawnerAt(helper, rel, EntityTypes.ZOMBIE);
        new com.afjan.hatchery.spawner.SpawnerModules(3, 0, 0, false, true).install(helper.getLevel(), helper.absolutePos(rel), spawner);
        ServerPlayer player = TestPlayers.survival(helper, new BlockPos(0, 1, 0), new ItemStack(Items.IRON_PICKAXE));
        player.gameMode.destroyBlock(helper.absolutePos(rel));
        helper.assertValueEqual(dropped(helper, ModBlocks.MODULES.get(com.afjan.hatchery.spawner.Module.SWARM).get()), 6, "swarm modules (1+2+3)");
        helper.assertValueEqual(dropped(helper, ModBlocks.MODULES.get(com.afjan.hatchery.spawner.Module.REDSTONE).get()), 1, "redstone modules");
        helper.assertValueEqual(dropped(helper, ModBlocks.BROKEN_SPAWNER_ITEM.get()), 1, "broken spawner");
        helper.succeed();
    }

    @GameTest
    public static void moduleRecipes(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Object[][] grids = {
                {com.afjan.hatchery.spawner.Module.SWARM, Items.DIAMOND, Items.EMERALD, Items.NETHERITE_SCRAP},
                {com.afjan.hatchery.spawner.Module.HASTE, Items.REDSTONE_BLOCK, Items.GOLD_INGOT, Items.NETHERITE_SCRAP},
                {com.afjan.hatchery.spawner.Module.FRAILTY, Items.AMETHYST_SHARD, Items.QUARTZ, Items.FERMENTED_SPIDER_EYE},
                {com.afjan.hatchery.spawner.Module.DAYLIGHT, Items.LAPIS_BLOCK, Items.DIAMOND, Items.DAYLIGHT_DETECTOR},
                {com.afjan.hatchery.spawner.Module.REDSTONE, Items.REDSTONE, Items.IRON_INGOT, Items.COMPARATOR}};
        for (Object[] g : grids) {
            Item corner = (Item) g[1], edge = (Item) g[2], centre = (Item) g[3];
            List<ItemStack> items = new ArrayList<>();
            for (int i = 0; i < 9; i++) items.add(new ItemStack(i == 4 ? centre : i % 2 == 0 ? corner : edge));
            var input = net.minecraft.world.item.crafting.CraftingInput.of(3, 3, items);
            var recipe = level.recipeAccess().getRecipeFor(net.minecraft.world.item.crafting.RecipeType.CRAFTING, input, level);
            helper.assertTrue(recipe.isPresent() && recipe.get().value().assemble(input).is(ModBlocks.MODULES.get(g[0]).get()), "recipe of " + g[0]);
        }
        helper.succeed();
    }

    @GameTest
    public static void everyTextHasATranslation(GameTestHelper helper) {
        com.google.gson.JsonObject lang;
        try (var in = Hatchery.class.getResourceAsStream("/assets/hatchery/lang/en_us.json")) {
            lang = com.google.gson.JsonParser.parseReader(new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        }
        List<String> keys = new ArrayList<>(com.afjan.hatchery.spawner.Messages.ALL);
        keys.add(ModBlocks.BROKEN_SPAWNER_ITEM.get().getDescriptionId());
        keys.add(com.afjan.hatchery.block.BrokenSpawnerItem.TIP_1);
        keys.add(com.afjan.hatchery.block.BrokenSpawnerItem.TIP_2);
        keys.add(com.afjan.hatchery.event.EggDrops.MESSAGE);
        for (var module : com.afjan.hatchery.spawner.Module.values()) {
            keys.add(ModBlocks.MODULES.get(module).get().getDescriptionId());
            keys.add(module.translationKey());
            keys.add("tooltip.hatchery." + module.id);
        }
        for (String key : keys) helper.assertTrue(lang.has(key), "missing translation: " + key);
        helper.succeed();
    }
}
