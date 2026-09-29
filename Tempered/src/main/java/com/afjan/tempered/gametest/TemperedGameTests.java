package com.afjan.tempered.gametest;

import com.afjan.tempered.Tempered;
import com.afjan.tempered.event.BrokenTools;
import com.afjan.tempered.event.PlacedBlocks;
import com.afjan.tempered.mastery.Kind;
import com.afjan.tempered.mastery.Mastery;
import com.afjan.tempered.mastery.Milestone;
import com.afjan.tempered.mastery.Perk;
import com.afjan.tempered.mastery.Progress;
import com.afjan.tempered.mastery.Stat;
import com.afjan.tempered.mastery.Tier;
import com.afjan.tempered.mastery.Track;
import com.afjan.tempered.mastery.Tracks;
import com.afjan.tempered.registry.ModComponents;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.entity.projectile.arrow.ThrownTrident;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.ForgeHooks;
import net.minecraftforge.event.AnvilUpdateEvent;
import net.minecraftforge.gametest.GameTest;
import net.minecraftforge.gametest.GameTestNamespace;
import net.minecraftforge.gametest.GameTestPrefix;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Headless checks of the whole mod. Each method is one test; {@code tools/gen_tests.py} writes the matching
 * test_instance files (structure = an empty box that Forge generates, so no .nbt files are needed).
 */
@GameTestNamespace(Tempered.MODID)
@GameTestPrefix("mastery")
public final class TemperedGameTests {
    private TemperedGameTests() {
    }

    // ------------------------------------------------------------------------------------------------ helpers

    /** A stack whose counters meet milestones 1..level of its track. */
    static ItemStack atLevel(Item item, int level) {
        ItemStack stack = new ItemStack(item);
        Track track = Tracks.get(item);
        Map<String, Integer> stats = new HashMap<>();
        for (int i = 0; i < level; i++) {
            for (Milestone.Req req : track.milestones.get(i).reqs()) stats.merge(req.stat().id, req.amount(), Math::max);
        }
        stack.set(ModComponents.MASTERY.get(), new Mastery(4242L, stats));
        return stack;
    }

    private static void fill(GameTestHelper helper, int x0, int y0, int z0, int x1, int y1, int z1, Block block) {
        for (BlockPos pos : BlockPos.betweenClosed(x0, y0, z0, x1, y1, z1)) helper.setBlock(pos, block);
    }

    private static int count(ItemStack stack, Stat stat) {
        Mastery mastery = Mastery.of(stack);
        return mastery == null ? 0 : mastery.get(stat);
    }

    // ------------------------------------------------------------------------------------------------ catalogue

    @GameTest
    public static void catalogueIsComplete(GameTestHelper helper) {
        List<Track> all = Tracks.all();
        helper.assertValueEqual(all.size(), 48, "number of tracks");
        for (Kind kind : Kind.values()) {
            long n = all.stream().filter(t -> t.kind == kind).count();
            helper.assertValueEqual(n, kind.tiered ? 7L : 1L, "tracks of " + kind.id);
        }
        for (Track track : all) {
            String name = track.tier.prefix + " " + track.kind.id;
            helper.assertValueEqual(track.milestones.size(), Track.MAX_LEVEL, name + " milestones");
            Map<Stat, Integer> seen = new HashMap<>();
            Map<Perk, Double> perks = new HashMap<>();
            for (Milestone milestone : track.milestones) {
                helper.assertFalse(milestone.reqs().isEmpty(), name + " " + milestone.level() + " has no requirement");
                helper.assertFalse(milestone.gained().isEmpty(), name + " " + milestone.level() + " gives nothing");
                for (Milestone.Req req : milestone.reqs()) {
                    helper.assertTrue(req.amount() >= seen.getOrDefault(req.stat(), 0), name + " asks for fewer " + req.stat().id);
                    seen.put(req.stat(), req.amount());
                }
                for (var e : milestone.totals().entrySet()) {
                    helper.assertTrue(e.getValue() >= perks.getOrDefault(e.getKey(), 0.0), name + " loses " + e.getKey().id);
                    perks.put(e.getKey(), e.getValue());
                }
            }
        }
        // Levels are consecutive: meeting III but not II is still level I.
        Track track = Tracks.of(Kind.PICKAXE, Tier.DIAMOND);
        Map<String, Integer> stats = new HashMap<>();
        for (Milestone.Req req : track.milestones.get(0).reqs()) stats.put(req.stat().id, req.amount());
        for (Milestone.Req req : track.milestones.get(2).reqs()) stats.merge(req.stat().id, req.amount(), Math::max);
        stats.put(Stat.ORES.id, 0);
        helper.assertValueEqual(track.level(new Mastery(1L, stats)), 1, "diamond pickaxe level without ores");
        helper.succeed();
    }

    private static JsonObject lang() {
        try (InputStream in = Tempered.class.getResourceAsStream("/assets/tempered/lang/en_us.json")) {
            if (in == null) throw new IllegalStateException("en_us.json missing");
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String tr(JsonObject lang, String key, Object... args) {
        return lang.has(key) ? String.format(lang.get(key).getAsString(), args) : key;
    }

    /** Also writes CHALLENGES.md (next to build.gradle) from the catalogue, so the list never goes stale. */
    @GameTest
    public static void writeChallengeSheet(GameTestHelper helper) {
        JsonObject lang = lang();
        StringBuilder md = new StringBuilder();
        md.append("# Tempered: every challenge\n\n")
                .append("Generated by the `write_challenge_sheet` GameTest from `Tracks.java`. Counters are lifetime totals;\n")
                .append("the rewards column lists what is new or raised at that level (totals, not additions).\n")
                .append("Elite foes: Wither, Ender Dragon, Warden, Elder Guardian, Evoker, Ravager, Piglin Brute.\n");
        for (Kind kind : Kind.values()) {
            md.append("\n## ").append(tr(lang, kind.translationKey())).append("\n");
            for (Track track : Tracks.all()) {
                if (track.kind != kind) continue;
                String name = kind.tiered ? tr(lang, track.tier.translationKey()) + " " + tr(lang, kind.translationKey()) : tr(lang, kind.translationKey());
                md.append("\n### ").append(name).append("\n\n| Mastery | Challenge | Rewards |\n|---|---|---|\n");
                for (Milestone milestone : track.milestones) {
                    List<String> reqs = new ArrayList<>();
                    for (Milestone.Req req : milestone.reqs()) reqs.add(tr(lang, req.stat().requirementKey(kind), req.amount()));
                    List<String> perks = new ArrayList<>();
                    for (Perk perk : milestone.gained()) {
                        int v = (int) Math.round(milestone.totals().get(perk));
                        perks.add(switch (perk.format) {
                            case FLAG -> tr(lang, perk.key(kind));
                            case AREA -> tr(lang, perk.key(kind), 2 * v + 1, 2 * v + 1);
                            default -> tr(lang, perk.key(kind), v);
                        });
                    }
                    md.append("| ").append(tr(lang, "mastery.tempered.level." + milestone.level())).append(" | ")
                            .append(String.join("<br>", reqs)).append(" | ").append(String.join("<br>", perks)).append(" |\n");
                }
            }
        }
        try {
            java.nio.file.Files.writeString(java.nio.file.Path.of("..", "CHALLENGES.md"), md.toString(), StandardCharsets.UTF_8);
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        }
        helper.succeed();
    }

    @GameTest
    public static void everyTextHasATranslation(GameTestHelper helper) {
        JsonObject lang = lang();
        List<String> keys = new ArrayList<>();
        for (Kind kind : Kind.values()) {
            keys.add(kind.translationKey());
            for (Stat stat : Stat.values()) keys.add(stat.requirementKey(kind));
        }
        for (Tier tier : Tier.values()) keys.add(tier.translationKey());
        for (int level = 0; level <= Track.MAX_LEVEL; level++) keys.add("mastery.tempered.level." + level);
        for (Track track : Tracks.all()) {
            for (Milestone milestone : track.milestones) {
                for (Perk perk : milestone.totals().keySet()) keys.add(perk.key(track.kind));
            }
        }
        keys.addAll(com.afjan.tempered.mastery.LangKeys.STATIC);
        for (String key : keys) helper.assertTrue(lang.has(key), "missing translation: " + key);
        helper.succeed();
    }

    // ------------------------------------------------------------------------------------------------ broken tools

    @GameTest
    public static void brokenToolsAreKept(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ItemStack pickaxe = new ItemStack(Items.DIAMOND_PICKAXE);
        pickaxe.setDamageValue(pickaxe.getMaxDamage() - 1);
        pickaxe.hurtAndBreak(10, level, null, item -> {
        });
        helper.assertFalse(pickaxe.isEmpty(), "the pickaxe vanished");
        helper.assertTrue(pickaxe.isBroken(), "the pickaxe is not broken");
        pickaxe.hurtAndBreak(10, level, null, item -> {
        });
        helper.assertValueEqual(pickaxe.getCount(), 1, "broken pickaxe count");

        ItemStack helmet = new ItemStack(Items.LEATHER_HELMET);
        helmet.setDamageValue(helmet.getMaxDamage() - 1);
        helmet.hurtAndBreak(10, level, null, item -> {
        });
        helper.assertTrue(helmet.isEmpty(), "armour should still break normally");
        helper.succeed();
    }

    @GameTest
    public static void brokenToolsWorkLikeAFist(GameTestHelper helper) {
        ItemStack pickaxe = new ItemStack(Items.DIAMOND_PICKAXE);
        ServerPlayer player = TestPlayers.survival(helper, new BlockPos(1, 1, 1), pickaxe);
        BlockState stone = Blocks.STONE.defaultBlockState();
        float working = player.getDestroySpeed(stone, null);
        pickaxe.setDamageValue(pickaxe.getMaxDamage());
        float broken = player.getDestroySpeed(stone, null);
        // Airborne test players mine 5x slower either way: compare the ratio (diamond pickaxe = 8x a fist).
        helper.assertTrue(Math.abs(working / broken - 8.0F) < 0.01F, "speed working " + working + ", broken " + broken);
        helper.assertFalse(ForgeHooks.isCorrectToolForDrops(stone, player), "a broken pickaxe still harvests stone");

        ItemStack sword = new ItemStack(Items.NETHERITE_SWORD);
        sword.setDamageValue(sword.getMaxDamage());
        player.setItemInHand(InteractionHand.MAIN_HAND, sword);
        Zombie zombie = helper.spawn(EntityTypes.ZOMBIE, new BlockPos(3, 1, 3));
        zombie.hurtServer(helper.getLevel(), player.damageSources().playerAttack(player), 12.0F);
        helper.assertTrue(zombie.getHealth() >= 18.5F, "broken sword dealt " + (20.0F - zombie.getHealth()));
        helper.succeed();
    }

    @GameTest
    public static void anvilRepairIsCheap(GameTestHelper helper) {
        ServerPlayer player = TestPlayers.survival(helper, new BlockPos(1, 1, 1), ItemStack.EMPTY);
        ItemStack pickaxe = atLevel(Items.IRON_PICKAXE, 3);
        pickaxe.setDamageValue(pickaxe.getMaxDamage());
        pickaxe.set(net.minecraft.core.component.DataComponents.REPAIR_COST, 63);
        AnvilUpdateEvent event = new AnvilUpdateEvent(pickaxe, new ItemStack(Items.IRON_INGOT, 3), "", 63, player);
        AnvilUpdateEvent.BUS.post(event);
        ItemStack out = event.getOutput();
        int quarter = pickaxe.getMaxDamage() / 4;
        helper.assertFalse(out.isEmpty(), "no repair offered");
        helper.assertValueEqual(out.getDamageValue(), pickaxe.getMaxDamage() - 3 * quarter, "damage after repair");
        helper.assertValueEqual(event.getCost(), 3L, "level cost");
        helper.assertValueEqual(event.getMaterialCost(), 3, "materials used");
        helper.assertValueEqual(Mastery.level(out), 3, "mastery kept");
        helper.succeed();
    }

    @GameTest
    public static void reinforcedIgnoresWear(GameTestHelper helper) {
        ItemStack maxed = atLevel(Items.IRON_PICKAXE, 5);
        double chance = Mastery.perk(maxed, Perk.REINFORCED);
        int kept = BrokenTools.reinforce(maxed, 4000);
        double expected = 4000 * (1 - chance / 100.0);
        helper.assertTrue(Math.abs(kept - expected) < 200, "kept " + kept + " of 4000 wear at " + chance + "%");
        helper.assertValueEqual(BrokenTools.reinforce(new ItemStack(Items.IRON_PICKAXE), 4000), 4000, "unmastered wear");
        helper.succeed();
    }

    // ------------------------------------------------------------------------------------------------ progress

    @GameTest(structure = "forge:empty7x5x7")
    public static void miningCountsAndLevelsUp(GameTestHelper helper) {
        ItemStack pickaxe = new ItemStack(Items.WOODEN_PICKAXE);
        ServerPlayer player = TestPlayers.survival(helper, new BlockPos(0, 1, 0), pickaxe);
        fill(helper, 1, 1, 1, 4, 4, 4, Blocks.STONE);
        int mined = 0;
        for (BlockPos rel : BlockPos.betweenClosed(1, 1, 1, 4, 4, 4)) {
            if (mined == 16) break;
            player.gameMode.destroyBlock(helper.absolutePos(rel));
            mined++;
        }
        helper.assertValueEqual(count(pickaxe, Stat.MINED), 16, "blocks counted");
        helper.assertValueEqual(Mastery.level(pickaxe), 1, "wooden pickaxe level");
        helper.assertTrue(Mastery.perk(pickaxe, Perk.SPEED) > 0, "level I gives speed");
        helper.assertFalse(pickaxe.isEmpty(), "pickaxe gone");
        helper.succeed();
    }

    @GameTest
    public static void placedBlocksDoNotCount(GameTestHelper helper) {
        ItemStack pickaxe = new ItemStack(Items.STONE_PICKAXE);
        ServerPlayer player = TestPlayers.survival(helper, new BlockPos(0, 1, 0), pickaxe);
        BlockPos rel = new BlockPos(1, 1, 1);
        // Place stone the way a player does (fires Forge's EntityPlaceEvent) on top of a floor block.
        helper.setBlock(rel.below(), Blocks.DIRT);
        player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.STONE));
        BlockPos floor = helper.absolutePos(rel.below());
        new ItemStack(Items.STONE).useOn(new net.minecraft.world.item.context.UseOnContext(helper.getLevel(), player, InteractionHand.OFF_HAND,
                player.getOffhandItem(), new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(floor).add(0, 0.5, 0),
                net.minecraft.core.Direction.UP, floor, false)));
        helper.assertTrue(helper.getBlockState(rel).is(Blocks.STONE), "stone was not placed");
        helper.assertTrue(PlacedBlocks.contains(helper.getLevel(), helper.absolutePos(rel)), "placement not remembered");
        player.gameMode.destroyBlock(helper.absolutePos(rel));
        helper.assertValueEqual(count(pickaxe, Stat.MINED), 0, "placed stone counted");
        helper.setBlock(rel, Blocks.STONE);
        player.gameMode.destroyBlock(helper.absolutePos(rel));
        helper.assertValueEqual(count(pickaxe, Stat.MINED), 1, "natural stone counted");
        helper.succeed();
    }

    @GameTest
    public static void killsCountForTheWeapon(GameTestHelper helper) {
        ItemStack sword = new ItemStack(Items.IRON_SWORD);
        ServerPlayer player = TestPlayers.survival(helper, new BlockPos(0, 1, 0), sword);
        Zombie zombie = helper.spawn(EntityTypes.ZOMBIE, new BlockPos(2, 1, 2));
        boolean hurt = zombie.hurtServer(helper.getLevel(), player.damageSources().playerAttack(player), 1000.0F);
        helper.assertTrue(zombie.isDeadOrDying(), "zombie survived (hurt=" + hurt + ", health " + zombie.getHealth() + ", mode "
                + player.gameMode.getGameModeForPlayer() + ", difficulty " + helper.getLevel().getDifficulty() + ")");
        helper.assertValueEqual(count(sword, Stat.KILLS), 1, "sword kills");
        helper.assertValueEqual(count(sword, Stat.HOSTILE), 1, "sword monster kills");
        helper.succeed();
    }

    @GameTest
    public static void arrowKillsCreditTheBow(GameTestHelper helper) {
        ItemStack bow = new ItemStack(Items.BOW);
        Mastery.getOrCreate(bow);
        ServerPlayer player = TestPlayers.survival(helper, new BlockPos(0, 1, 0), bow);
        Arrow arrow = new Arrow(helper.getLevel(), player, new ItemStack(Items.ARROW), bow.copy());
        Zombie zombie = helper.spawn(EntityTypes.ZOMBIE, new BlockPos(2, 1, 2));
        zombie.hurtServer(helper.getLevel(), player.damageSources().arrow(arrow, player), 1000.0F);
        helper.assertValueEqual(count(bow, Stat.KILLS), 1, "bow kills");

        ItemStack trident = new ItemStack(Items.TRIDENT);
        ThrownTrident thrown = new ThrownTrident(helper.getLevel(), player, trident);
        Zombie second = helper.spawn(EntityTypes.ZOMBIE, new BlockPos(2, 1, 2));
        second.hurtServer(helper.getLevel(), player.damageSources().trident(thrown, player), 1000.0F);
        helper.assertValueEqual(count(thrown.getWeaponItem(), Stat.THROWN), 1, "trident throw kills");
        helper.succeed();
    }

    @GameTest
    public static void netheriteKeepsDiamondCounters(GameTestHelper helper) {
        ItemStack diamond = atLevel(Items.DIAMOND_PICKAXE, 5);
        ItemStack netherite = diamond.transmuteCopy(Items.NETHERITE_PICKAXE);
        int level = Mastery.level(netherite);
        helper.assertTrue(level >= 1 && level < 5, "netherite level from diamond counters: " + level);
        helper.assertValueEqual(Mastery.of(netherite).uid(), 4242L, "uid kept");
        helper.succeed();
    }

    // ------------------------------------------------------------------------------------------------ perks

    @GameTest
    public static void speedAndLootingPerks(GameTestHelper helper) {
        ItemStack plain = new ItemStack(Items.DIAMOND_PICKAXE);
        ServerPlayer player = TestPlayers.survival(helper, new BlockPos(0, 1, 0), plain);
        BlockState stone = Blocks.STONE.defaultBlockState();
        float base = player.getDestroySpeed(stone, null);
        player.setItemInHand(InteractionHand.MAIN_HAND, atLevel(Items.DIAMOND_PICKAXE, 5));
        float mastered = player.getDestroySpeed(stone, null);
        double expected = 1 + Mastery.perk(player.getMainHandItem(), Perk.SPEED) / 100.0;
        helper.assertTrue(Math.abs(mastered / base - expected) < 0.01, "speed x" + mastered / base + ", expected x" + expected);

        player.setItemInHand(InteractionHand.MAIN_HAND, atLevel(Items.DIAMOND_SWORD, 5));
        Zombie zombie = helper.spawn(EntityTypes.ZOMBIE, new BlockPos(2, 1, 2));
        int looting = ForgeHooks.getLootingLevel(zombie, player, player.damageSources().playerAttack(player));
        helper.assertValueEqual(looting, 2, "looting from mastery");
        helper.succeed();
    }

    @GameTest
    public static void damagePerkAddsDamage(GameTestHelper helper) {
        ServerPlayer player = TestPlayers.survival(helper, new BlockPos(0, 1, 0), atLevel(Items.DIAMOND_SWORD, 3));
        var golem = helper.spawn(EntityTypes.IRON_GOLEM, new BlockPos(2, 1, 2));
        float before = golem.getHealth();
        golem.hurtServer(helper.getLevel(), player.damageSources().playerAttack(player), 10.0F);
        double bonus = Mastery.perk(player.getMainHandItem(), Perk.DAMAGE);
        float dealt = before - golem.getHealth();
        helper.assertTrue(Math.abs(dealt - 10.0F * (1 + bonus / 100.0)) < 0.05, "dealt " + dealt + " with +" + bonus + "%");
        helper.succeed();
    }

    @GameTest
    public static void attackSpeedIsAnAttribute(GameTestHelper helper) {
        ItemStack sword = atLevel(Items.IRON_SWORD, 4);
        List<Double> found = new ArrayList<>();
        sword.forEachModifier(net.minecraft.world.entity.EquipmentSlot.MAINHAND, (attribute, modifier) -> {
            if (modifier.id().getNamespace().equals(Tempered.MODID)) found.add(modifier.amount());
        });
        helper.assertValueEqual(found.size(), 1, "mastery attack speed modifiers");
        sword.setDamageValue(sword.getMaxDamage());
        found.clear();
        sword.forEachModifier(net.minecraft.world.entity.EquipmentSlot.MAINHAND, (attribute, modifier) -> {
            if (modifier.id().getNamespace().equals(Tempered.MODID)) found.add(modifier.amount());
        });
        helper.assertTrue(found.isEmpty(), "a broken sword keeps its attack speed bonus");
        helper.succeed();
    }

    // ------------------------------------------------------------------------------------------------ abilities

    @GameTest(structure = "forge:empty7x5x7")
    public static void veinMinerTakesTheVein(GameTestHelper helper) {
        ItemStack pickaxe = atLevel(Items.IRON_PICKAXE, 5);
        ServerPlayer player = TestPlayers.survival(helper, new BlockPos(0, 1, 0), pickaxe);
        fill(helper, 1, 1, 1, 3, 1, 2, Blocks.IRON_ORE);
        helper.setBlock(new BlockPos(4, 1, 1), Blocks.STONE);
        player.gameMode.destroyBlock(helper.absolutePos(new BlockPos(1, 1, 1)));
        for (BlockPos rel : BlockPos.betweenClosed(1, 1, 1, 3, 1, 2)) {
            helper.assertTrue(helper.getBlockState(rel).isAir(), "ore left at " + rel);
        }
        helper.assertTrue(helper.getBlockState(new BlockPos(4, 1, 1)).is(Blocks.STONE), "vein miner took stone");
        helper.assertValueEqual(count(pickaxe, Stat.ORES) - atLevelCount(Items.IRON_PICKAXE, Stat.ORES), 6, "ores counted");
        helper.succeed();
    }

    private static int atLevelCount(Item item, Stat stat) {
        return count(atLevel(item, 5), stat);
    }

    @GameTest(structure = "forge:empty7x6x7")
    public static void excavateMinesThreeByThree(GameTestHelper helper) {
        ItemStack pickaxe = atLevel(Items.DIAMOND_PICKAXE, 5);
        ServerPlayer player = TestPlayers.survival(helper, new BlockPos(2, 1, 0), pickaxe);
        fill(helper, 1, 1, 3, 3, 3, 3, Blocks.STONE);
        helper.setBlock(new BlockPos(2, 2, 4), Blocks.STONE);
        player.gameMode.destroyBlock(helper.absolutePos(new BlockPos(2, 2, 3)));
        for (BlockPos rel : BlockPos.betweenClosed(1, 1, 3, 3, 3, 3)) {
            helper.assertTrue(helper.getBlockState(rel).isAir(), "stone left at " + rel);
        }
        helper.assertTrue(helper.getBlockState(new BlockPos(2, 2, 4)).is(Blocks.STONE), "excavate went too deep");

        // Sneaking mines one block only.
        fill(helper, 1, 1, 3, 3, 3, 3, Blocks.STONE);
        player.setShiftKeyDown(true);
        player.gameMode.destroyBlock(helper.absolutePos(new BlockPos(2, 2, 3)));
        helper.assertTrue(helper.getBlockState(new BlockPos(1, 1, 3)).is(Blocks.STONE), "sneaking still excavated");
        helper.succeed();
    }

    @GameTest(structure = "forge:empty7x9x7")
    public static void timberFellsTreesNotHouses(GameTestHelper helper) {
        ItemStack axe = atLevel(Items.IRON_AXE, 5);
        ServerPlayer player = TestPlayers.survival(helper, new BlockPos(0, 1, 0), axe);
        fill(helper, 2, 1, 2, 2, 5, 2, Blocks.OAK_LOG);
        fill(helper, 1, 5, 1, 3, 6, 3, Blocks.OAK_LEAVES);
        helper.setBlock(new BlockPos(2, 5, 2), Blocks.OAK_LOG);
        player.gameMode.destroyBlock(helper.absolutePos(new BlockPos(2, 1, 2)));
        for (int y = 1; y <= 5; y++) helper.assertTrue(helper.getBlockState(new BlockPos(2, y, 2)).isAir(), "log left at y " + y);

        // A log pillar without leaves is a building: only the broken log goes.
        fill(helper, 5, 1, 5, 5, 4, 5, Blocks.OAK_LOG);
        player.gameMode.destroyBlock(helper.absolutePos(new BlockPos(5, 1, 5)));
        helper.assertTrue(helper.getBlockState(new BlockPos(5, 2, 5)).is(Blocks.OAK_LOG), "timber felled a leafless pillar");
        helper.succeed();
    }

    @GameTest(structure = "forge:empty7x4x7", skyAccess = true)
    public static void reaperHarvestsAndReplants(GameTestHelper helper) {
        ItemStack hoe = atLevel(Items.IRON_HOE, 5);
        ServerPlayer player = TestPlayers.survival(helper, new BlockPos(0, 1, 0), hoe);
        BlockState ripe = ((CropBlock) Blocks.WHEAT).getStateForAge(7);
        for (BlockPos rel : BlockPos.betweenClosed(1, 0, 1, 5, 0, 5)) {
            helper.setBlock(rel, Blocks.FARMLAND);
            helper.setBlock(rel.above(), ripe);
        }
        player.gameMode.destroyBlock(helper.absolutePos(new BlockPos(3, 1, 3)));
        helper.runAfterDelay(2, () -> {
            for (BlockPos rel : BlockPos.betweenClosed(1, 1, 1, 5, 1, 5)) {
                BlockState state = helper.getBlockState(rel);
                helper.assertTrue(state.is(Blocks.WHEAT) && ((CropBlock) Blocks.WHEAT).getAge(state) == 0, "not replanted at " + rel + ": " + state);
            }
            helper.succeed();
        });
    }

    @GameTest
    public static void hoeTillingCounts(GameTestHelper helper) {
        ItemStack hoe = new ItemStack(Items.STONE_HOE);
        ServerPlayer player = TestPlayers.survival(helper, new BlockPos(0, 1, 0), hoe);
        BlockPos rel = new BlockPos(2, 0, 2);
        helper.setBlock(rel, Blocks.DIRT);
        BlockPos dirt = helper.absolutePos(rel);
        hoe.useOn(new net.minecraft.world.item.context.UseOnContext(helper.getLevel(), player, InteractionHand.MAIN_HAND, hoe,
                new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(dirt).add(0, 0.5, 0),
                        net.minecraft.core.Direction.UP, dirt, false)));
        helper.assertTrue(helper.getBlockState(rel).is(Blocks.FARMLAND), "dirt was not tilled");
        helper.assertValueEqual(count(hoe, Stat.TILLED), 1, "tilled blocks");
        helper.succeed();
    }

    @GameTest
    public static void fishingRodLureAndLuck(GameTestHelper helper) {
        ItemStack rod = atLevel(Items.FISHING_ROD, 5);
        ServerPlayer player = TestPlayers.survival(helper, new BlockPos(0, 1, 0), rod);
        var hook = new net.minecraft.world.entity.projectile.FishingHook(player, helper.getLevel(), 0, 0);
        helper.getLevel().addFreshEntity(hook);
        var access = (com.afjan.tempered.mixin.FishingHookAccessor) hook;
        helper.assertValueEqual(access.tempered$getLureSpeed(), 100 * (int) Mastery.perk(rod, Perk.LURE), "lure speed");
        helper.assertValueEqual(access.tempered$getLuck(), (int) Mastery.perk(rod, Perk.LUCK), "luck");
        hook.discard();
        helper.succeed();
    }

    @GameTest
    public static void selfLoadingCrossbow(GameTestHelper helper) {
        ItemStack crossbow = atLevel(Items.CROSSBOW, 5);
        ServerPlayer player = TestPlayers.survival(helper, new BlockPos(0, 1, 0), crossbow);
        player.getInventory().add(new ItemStack(Items.ARROW, 8));
        com.afjan.tempered.ability.Abilities.tickAutoload(player);
        helper.assertTrue(net.minecraft.world.item.CrossbowItem.isCharged(crossbow), "crossbow did not load itself");
        helper.succeed();
    }

    @GameTest(structure = "forge:empty7x5x7")
    public static void bowVolleyFiresThreeArrows(GameTestHelper helper) {
        ItemStack bow = atLevel(Items.BOW, 5);
        ServerPlayer player = TestPlayers.survival(helper, new BlockPos(3, 1, 3), bow);
        Arrow arrow = new Arrow(helper.getLevel(), player, new ItemStack(Items.ARROW), bow.copy());
        arrow.shootFromRotation(player, 0.0F, 0.0F, 0.0F, 3.0F, 0.0F);
        arrow.setCritArrow(true);
        helper.getLevel().addFreshEntity(arrow);
        var box = new net.minecraft.world.phys.AABB(helper.absolutePos(BlockPos.ZERO)).inflate(12);
        int arrows = helper.getLevel().getEntitiesOfClass(net.minecraft.world.entity.projectile.arrow.AbstractArrow.class, box).size();
        helper.assertValueEqual(arrows, 3, "arrows in the air");
        helper.succeed();
    }

    @GameTest
    public static void tridentCallsLightning(GameTestHelper helper) {
        ItemStack trident = atLevel(Items.TRIDENT, 5);
        ServerPlayer player = TestPlayers.survival(helper, new BlockPos(0, 1, 0), ItemStack.EMPTY);
        ThrownTrident thrown = new ThrownTrident(helper.getLevel(), player, trident);
        Zombie zombie = helper.spawn(EntityTypes.ZOMBIE, new BlockPos(3, 1, 3));
        net.minecraftforge.event.entity.ProjectileImpactEvent.BUS.post(
                new net.minecraftforge.event.entity.ProjectileImpactEvent(thrown, new net.minecraft.world.phys.EntityHitResult(zombie)));
        var box = zombie.getBoundingBox().inflate(2);
        helper.assertFalse(helper.getLevel().getEntitiesOfClass(net.minecraft.world.entity.LightningBolt.class, box).isEmpty(), "no lightning");
        helper.succeed();
    }

    @GameTest(structure = "forge:empty7x5x7")
    public static void maceShockwaveHitsNearbyFoes(GameTestHelper helper) {
        ServerPlayer player = TestPlayers.survival(helper, new BlockPos(0, 1, 0), atLevel(Items.MACE, 5));
        Zombie target = helper.spawn(EntityTypes.ZOMBIE, new BlockPos(3, 1, 3));
        Zombie bystander = helper.spawn(EntityTypes.ZOMBIE, new BlockPos(5, 1, 3));
        player.fallDistance = 6.0;
        target.hurtServer(helper.getLevel(), player.damageSources().playerAttack(player), 6.0F);
        helper.assertTrue(bystander.getHealth() < bystander.getMaxHealth(), "the shockwave missed the bystander");
        helper.succeed();
    }

    @GameTest(structure = "forge:empty9x4x9")
    public static void shearSweepShearsTheFlock(GameTestHelper helper) {
        ItemStack shears = atLevel(Items.SHEARS, 5);
        ServerPlayer player = TestPlayers.survival(helper, new BlockPos(0, 1, 0), shears);
        List<net.minecraft.world.entity.animal.sheep.Sheep> flock = new ArrayList<>();
        for (int i = 0; i < 3; i++) flock.add(helper.spawn(EntityTypes.SHEEP, new BlockPos(3 + i, 1, 4)));
        player.interactOn(flock.getFirst(), InteractionHand.MAIN_HAND, net.minecraft.world.phys.Vec3.ZERO);
        for (var sheep : flock) helper.assertTrue(sheep.isSheared(), "a sheep kept its wool");
        helper.assertValueEqual(count(shears, Stat.SHEARED) - count(atLevel(Items.SHEARS, 5), Stat.SHEARED), 3, "sheared animals");
        helper.succeed();
    }

    @GameTest
    public static void lifestealHeals(GameTestHelper helper) {
        ServerPlayer player = TestPlayers.survival(helper, new BlockPos(0, 1, 0), atLevel(Items.DIAMOND_SWORD, 5));
        player.setHealth(10.0F);
        var golem = helper.spawn(EntityTypes.IRON_GOLEM, new BlockPos(2, 1, 2));
        golem.hurtServer(helper.getLevel(), player.damageSources().playerAttack(player), 10.0F);
        helper.assertTrue(player.getHealth() > 10.0F, "no lifesteal, health " + player.getHealth());
        helper.succeed();
    }

    @GameTest
    public static void levelUpAnnouncesWithoutErrors(GameTestHelper helper) {
        ItemStack sword = new ItemStack(Items.WOODEN_SWORD);
        ServerPlayer player = TestPlayers.survival(helper, new BlockPos(0, 1, 0), sword);
        for (int i = 0; i < 5; i++) Progress.record(player, sword, Stat.KILLS);
        helper.assertValueEqual(Mastery.level(sword), 1, "wooden sword level after 5 kills");
        helper.succeed();
    }
}
