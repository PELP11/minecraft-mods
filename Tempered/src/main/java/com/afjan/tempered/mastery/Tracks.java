package com.afjan.tempered.mastery;

import com.afjan.tempered.mastery.Milestone.Req;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import static com.afjan.tempered.mastery.Perk.*;
import static com.afjan.tempered.mastery.Stat.*;

/**
 * The whole challenge catalogue, in code.
 * <ul>
 * <li>Main tools (7 materials x pickaxe, axe, shovel, hoe, sword, spear): 20 levels. The main stat follows
 * {@link #CURVE} (level 20 needs 300x the work of level 1), and "rungs" add work somewhere specific (deep caves, the
 * Nether, the End, obsidian, Ancient Debris, elite foes ...). A rung a material cannot do (a stone pickaxe and
 * Ancient Debris) falls back to the next option. Every level raises a stat; abilities unlock on the way.</li>
 * <li>Bow, crossbow, trident, mace, shears, fishing rod: 5 levels each.</li>
 * </ul>
 * Amounts scale with the material ({@link Tier#scale}); speed and damage percentages with {@link Tier#power}.
 */
public final class Tracks {
    public static final int MAIN_LEVELS = 20;
    public static final int SPECIAL_LEVELS = 5;

    /** Main-stat multiplier per level (level 1 = x1). */
    static final double[] CURVE = {1, 2.5, 4.5, 7, 10, 14, 19, 25, 32, 40, 50, 62, 76, 92, 110, 135, 165, 200, 240, 300};

    private static final Kind[] TIERED = {Kind.PICKAXE, Kind.AXE, Kind.SHOVEL, Kind.HOE, Kind.SWORD, Kind.SPEAR};
    private static final Kind[] SPECIALS = {Kind.BOW, Kind.CROSSBOW, Kind.TRIDENT, Kind.MACE, Kind.SHEARS, Kind.FISHING_ROD};

    private static volatile Map<Item, Track> vanilla;
    private static final Map<Item, Optional<Track>> CACHE = new ConcurrentHashMap<>();

    private Tracks() {
    }

    /** Every vanilla track, grouped tool type by tool type (the overview's order). */
    public static List<Track> all() {
        return List.copyOf(vanilla().values());
    }

    public static Track of(Kind kind, Tier tier) {
        for (Track track : vanilla().values()) {
            if (track.kind == kind && track.tier == tier) return track;
        }
        return null;
    }

    public static Track get(ItemStack stack) {
        return stack.isEmpty() ? null : get(stack.getItem());
    }

    public static Track get(Item item) {
        Track track = vanilla().get(item);
        if (track != null) return track;
        return CACHE.computeIfAbsent(item, Tracks::modded).orElse(null);
    }

    /** Tags changed (datapack reload / joining a server): modded tools are re-evaluated. */
    public static void clearCache() {
        CACHE.clear();
    }

    private static Map<Item, Track> vanilla() {
        Map<Item, Track> map = vanilla;
        if (map == null) {
            synchronized (Tracks.class) {
                map = vanilla;
                if (map == null) {
                    map = new LinkedHashMap<>();
                    for (Kind kind : TIERED) {
                        for (Tier tier : Tier.MATERIALS) {
                            Item item = BuiltInRegistries.ITEM.getValue(Identifier.withDefaultNamespace(tier.prefix + "_" + kind.id));
                            if (item != Items.AIR) map.put(item, build(kind, tier, item));
                        }
                    }
                    for (Kind kind : SPECIALS) {
                        Item item = BuiltInRegistries.ITEM.getValue(Identifier.withDefaultNamespace(kind.id));
                        if (item != Items.AIR) map.put(item, build(kind, Tier.SPECIAL, item));
                    }
                    vanilla = map = Collections.unmodifiableMap(map);
                }
            }
        }
        return map;
    }

    /** Modded pickaxes, swords ... borrow the vanilla track of the closest material. */
    private static Optional<Track> modded(Item item) {
        ItemStack stack = new ItemStack(item);
        Kind kind = null;
        if (stack.is(ItemTags.PICKAXES)) kind = Kind.PICKAXE;
        else if (stack.is(ItemTags.AXES)) kind = Kind.AXE;
        else if (stack.is(ItemTags.SHOVELS)) kind = Kind.SHOVEL;
        else if (stack.is(ItemTags.HOES)) kind = Kind.HOE;
        else if (stack.is(ItemTags.SWORDS)) kind = Kind.SWORD;
        else if (stack.is(ItemTags.SPEARS)) kind = Kind.SPEAR;
        if (kind == null || !stack.has(DataComponents.MAX_DAMAGE)) return Optional.empty();
        Track template = of(kind, Tier.byDurability(stack.getMaxDamage()));
        return template == null ? Optional.empty() : Optional.of(template.withItem(item));
    }

    // ------------------------------------------------------------------------------------------------
    // Building

    private static Track build(Kind kind, Tier tier, Item item) {
        int levels = kind.tiered ? MAIN_LEVELS : SPECIAL_LEVELS;
        List<List<Req>> reqs = kind.tiered ? mainRequirements(kind, tier) : specialRequirements(kind);
        List<Map<Perk, Double>> steps = kind.tiered ? mainRewards(kind, tier) : specialRewards(kind, tier);
        List<Milestone> milestones = new ArrayList<>();
        EnumMap<Perk, Double> totals = new EnumMap<>(Perk.class);
        for (int i = 0; i < levels; i++) {
            List<Perk> gained = new ArrayList<>();
            for (Map.Entry<Perk, Double> step : steps.get(i).entrySet()) {
                Double before = totals.get(step.getKey());
                if (before == null || step.getValue() > before) gained.add(step.getKey());
                totals.put(step.getKey(), step.getValue());
            }
            milestones.add(new Milestone(i + 1, List.copyOf(reqs.get(i)),
                    Collections.unmodifiableMap(new EnumMap<>(totals)), List.copyOf(gained)));
        }
        return new Track(kind, tier, item, List.copyOf(milestones));
    }

    /** Round a scaled amount to a number that reads well (37 -> 35, 1437 -> 1450, 13200 -> 13000). */
    static int nice(double v) {
        if (v < 20) return (int) Math.max(1, Math.round(v));
        if (v < 100) return (int) (Math.round(v / 5) * 5);
        if (v < 1000) return (int) (Math.round(v / 10) * 10);
        if (v < 10000) return (int) (Math.round(v / 50) * 50);
        return (int) (Math.round(v / 500) * 500);
    }

    // ------------------------------------------------------------------------------------------------
    // Main tools: 20 levels

    /** How a rung's amount grows with the material: x scale, x sqrt(scale) (elite foes, riding) or fixed. */
    private enum Grow { S, R, F }

    /** One way to meet a rung; usable once the material's mining level is at least {@code minMining}. */
    private record Option(Stat stat, double base, Grow grow, int minMining) {
    }

    private record Rung(int level, List<Option> options) {
        Req resolve(Tier t) {
            for (Option o : options) {
                if (t.miningLevel >= o.minMining) {
                    double factor = switch (o.grow) {
                        case S -> t.scale;
                        case R -> Math.sqrt(t.scale);
                        case F -> 1.0;
                    };
                    return new Req(o.stat, nice(o.base * factor));
                }
            }
            throw new IllegalStateException("no option for " + t + " at level " + level);
        }
    }

    /** rung(level, STAT, base, Grow, minMining, [fallback STAT, base, Grow, minMining ...]) */
    private static Rung rung(int level, Object... spec) {
        List<Option> options = new ArrayList<>();
        for (int i = 0; i < spec.length; i += 4) {
            options.add(new Option((Stat) spec[i], ((Number) spec[i + 1]).doubleValue(), (Grow) spec[i + 2], (Integer) spec[i + 3]));
        }
        return new Rung(level, options);
    }

    private static final Grow S = Grow.S, R = Grow.R, F = Grow.F;

    private static List<Rung> rungs(Kind kind) {
        return switch (kind) {
            case PICKAXE -> List.of(
                    rung(2, ORES, 2, S, 0),
                    rung(4, ORES, 5, S, 0),
                    rung(6, DEEP, 30, S, 0),
                    rung(8, ORES, 15, S, 0),
                    rung(9, NETHER_MINED, 25, S, 0),
                    rung(11, DEEP, 150, S, 0),
                    rung(12, GEMS, 2, S, 2, ORES, 30, S, 0),
                    rung(13, NETHER_MINED, 150, S, 0),
                    rung(14, END_MINED, 50, S, 0),
                    rung(15, OBSIDIAN, 4, S, 3, ORES, 60, S, 0),
                    rung(16, DEBRIS, 0.5, S, 3, NETHER_MINED, 400, S, 0),
                    rung(17, END_MINED, 300, S, 0),
                    rung(18, GEMS, 6, S, 2, ORES, 100, S, 0),
                    rung(19, OBSIDIAN, 16, S, 3, DEEP, 600, S, 0),
                    rung(20, DEBRIS, 4, S, 3, GEMS, 12, S, 2, ORES, 150, S, 0));
            case AXE -> List.of(
                    rung(3, KILLS, 1, S, 0),
                    rung(6, KILLS, 3, S, 0),
                    rung(9, NETHER_MINED, 10, S, 0),
                    rung(12, KILLS, 10, S, 0),
                    rung(13, NETHER_MINED, 60, S, 0),
                    rung(15, END_MINED, 20, S, 0),
                    rung(17, KILLS, 30, S, 0),
                    rung(18, NETHER_MINED, 200, S, 0),
                    rung(19, END_MINED, 100, S, 0),
                    rung(20, ELITE, 1, R, 0));
            case SHOVEL -> List.of(
                    rung(2, GRAVEL, 3, S, 0),
                    rung(4, SAND, 10, S, 0),
                    rung(6, CLAY, 3, S, 0),
                    rung(8, GRAVEL, 15, S, 0),
                    rung(9, SNOW, 10, S, 0),
                    rung(11, SAND, 60, S, 0),
                    rung(12, SOUL, 10, S, 0),
                    rung(13, CLAY, 20, S, 0),
                    rung(14, NETHER_MINED, 50, S, 0),
                    rung(16, SNOW, 80, S, 0),
                    rung(17, SOUL, 80, S, 0),
                    rung(18, CLAY, 60, S, 0),
                    rung(19, NETHER_MINED, 300, S, 0),
                    rung(20, SAND, 400, S, 0));
            case HOE -> List.of(
                    rung(3, TILLED, 25, S, 0),
                    rung(5, MINED, 10, S, 0),
                    rung(7, TILLED, 60, S, 0),
                    rung(9, WARTS, 10, S, 0),
                    rung(11, MINED, 50, S, 0),
                    rung(12, WARTS, 40, S, 0),
                    rung(13, SCULK, 10, S, 0),
                    rung(15, NETHER_MINED, 40, S, 0),
                    rung(16, WARTS, 120, S, 0),
                    rung(17, SCULK, 50, S, 0),
                    rung(18, MINED, 250, S, 0),
                    rung(19, NETHER_MINED, 200, S, 0),
                    rung(20, SCULK, 150, S, 0));
            case SWORD -> List.of(
                    rung(6, NETHER_KILLS, 3, S, 0),
                    rung(9, NETHER_KILLS, 10, S, 0),
                    rung(11, ELITE, 1, F, 0),
                    rung(12, END_KILLS, 5, S, 0),
                    rung(14, NETHER_KILLS, 40, S, 0),
                    rung(15, ELITE, 2, R, 0),
                    rung(16, END_KILLS, 30, S, 0),
                    rung(17, NETHER_KILLS, 120, S, 0),
                    rung(18, ELITE, 4, R, 0),
                    rung(19, END_KILLS, 100, S, 0),
                    rung(20, ELITE, 8, R, 0));
            case SPEAR -> List.of(
                    rung(5, MOUNTED, 1, R, 0),
                    rung(8, MOUNTED, 3, R, 0),
                    rung(10, NETHER_KILLS, 5, S, 0),
                    rung(12, MOUNTED, 8, R, 0),
                    rung(14, NETHER_KILLS, 30, S, 0),
                    rung(15, ELITE, 1, R, 0),
                    rung(16, MOUNTED, 25, R, 0),
                    rung(17, END_KILLS, 30, S, 0),
                    rung(18, ELITE, 3, R, 0),
                    rung(19, MOUNTED, 60, R, 0),
                    rung(20, ELITE, 6, R, 0));
            default -> List.of();
        };
    }

    /** The tool's own work, level by level: blocks, logs, crops or kills along {@link #CURVE}. */
    private static Req mainRequirement(Kind kind, Tier t, int level) {
        double c = CURVE[level - 1] * t.scale;
        return switch (kind) {
            case PICKAXE -> new Req(MINED, nice(15 * c));
            case AXE -> new Req(LOGS, nice(10 * c));
            case SHOVEL -> new Req(MINED, nice(20 * c));
            case HOE -> level == 1 ? new Req(TILLED, nice(12 * t.scale)) : new Req(HARVESTED, nice(12 * c));
            case SWORD, SPEAR -> new Req(level <= 3 ? KILLS : HOSTILE, nice(4 * c));
            default -> throw new IllegalArgumentException(kind.id);
        };
    }

    private static List<List<Req>> mainRequirements(Kind kind, Tier t) {
        List<Rung> rungs = rungs(kind);
        List<List<Req>> levels = new ArrayList<>();
        for (int level = 1; level <= MAIN_LEVELS; level++) {
            List<Req> reqs = new ArrayList<>();
            reqs.add(mainRequirement(kind, t, level));
            for (Rung rung : rungs) {
                if (rung.level == level) reqs.add(rung.resolve(t));
            }
            levels.add(reqs);
        }
        return levels;
    }

    private static List<Map<Perk, Double>> mainRewards(Kind kind, Tier t) {
        List<Map<Perk, Double>> steps = emptySteps(MAIN_LEVELS);
        switch (kind) {
            case PICKAXE -> {
                for (int l = 1; l <= 20; l++) set(steps, t, l, SPEED, 5 * l);
                ladder(steps, t, REINFORCED, 3, 10, 7, 20, 11, 30, 15, 40, 19, 50);
                ladder(steps, t, YIELD, 6, 10, 12, 20, 17, 30, 20, 40);
                set(steps, t, 10, VEIN, t.veinSize);
                set(steps, t, 16, VEIN, 2 * t.veinSize);
                if (t == Tier.DIAMOND || t == Tier.NETHERITE) set(steps, t, 15, EXCAVATE, 1);
                if (t == Tier.IRON) set(steps, t, 20, EXCAVATE, 1);
                if (t == Tier.NETHERITE) set(steps, t, 20, EXCAVATE, 2);
                set(steps, t, 20, MAGNET, 1);
            }
            case AXE -> {
                for (int l = 1; l <= 20; l++) set(steps, t, l, SPEED, 5 * l);
                ladder(steps, t, DAMAGE, 2, 3, 6, 6, 10, 9, 14, 12, 18, 15);
                ladder(steps, t, REINFORCED, 3, 10, 7, 20, 11, 30, 15, 40, 19, 50);
                ladder(steps, t, YIELD, 5, 10, 9, 20, 13, 30, 17, 40);
                set(steps, t, 10, TIMBER, t.timberSize);
                set(steps, t, 16, TIMBER, 2 * t.timberSize);
                set(steps, t, 20, MAGNET, 1);
            }
            case SHOVEL -> {
                for (int l = 1; l <= 20; l++) set(steps, t, l, SPEED, 5 * l);
                ladder(steps, t, REINFORCED, 3, 10, 7, 20, 11, 30, 15, 40, 19, 50);
                ladder(steps, t, TREASURE_HUNTER, 6, 1, 10, 2, 14, 3, 18, 4, 20, 5);
                set(steps, t, 10, EXCAVATE, 1);
                set(steps, t, 20, EXCAVATE, 2);
            }
            case HOE -> {
                for (int l = 1; l <= 20; l++) set(steps, t, l, YIELD, 5 * l);
                for (int l = 2; l <= 20; l += 2) set(steps, t, l, SPEED, 5 * l / 2);
                set(steps, t, 3, REPLANT, 1);
                ladder(steps, t, REINFORCED, 5, 10, 9, 20, 13, 30, 17, 40, 20, 50);
                ladder(steps, t, REAPER, 8, 1, 14, 2, 20, Math.max(2, t.reaperRadius));
                set(steps, t, 20, MAGNET, 1);
            }
            case SWORD, SPEAR -> {
                for (int l = 1; l <= 20; l++) set(steps, t, l, DAMAGE, 2.5 * l);
                ladder(steps, t, ATTACK_SPEED, 3, 5, 7, 10, 11, 15, 15, 20, 19, 25);
                ladder(steps, t, REINFORCED, 4, 10, 8, 20, 12, 30, 16, 40, 20, 50);
                ladder(steps, t, LOOTING, 6, 1, 12, 2, 18, 3);
                if (kind == Kind.SWORD) {
                    ladder(steps, t, LIFESTEAL, 9, 3, 13, 6, 17, 9, 20, 12);
                    set(steps, t, 15, EXECUTIONER, 1);
                    set(steps, t, 20, SOUL_HARVEST, 1);
                } else {
                    set(steps, t, 10, CAVALRY, 1);
                    set(steps, t, 20, WARHORSE, 1);
                }
            }
            default -> throw new IllegalArgumentException(kind.id);
        }
        if (t == Tier.GOLD) {
            // Gilded: golden tools are the experience tools.
            ladder(steps, t, XP, 5, 25, 10, 50, 15, 75, 20, 100);
        }
        return steps;
    }

    // ------------------------------------------------------------------------------------------------
    // Bow, crossbow, trident, mace, shears, fishing rod: 5 levels

    /** reqs(STAT, amount, STAT, amount, ...) */
    private static List<Req> reqs(Object... pairs) {
        List<Req> list = new ArrayList<>();
        for (int i = 0; i < pairs.length; i += 2) {
            list.add(new Req((Stat) pairs[i], nice(((Number) pairs[i + 1]).doubleValue())));
        }
        return list;
    }

    private static List<List<Req>> specialRequirements(Kind kind) {
        return switch (kind) {
            case BOW -> List.of(
                    reqs(KILLS, 10),
                    reqs(KILLS, 30),
                    reqs(HOSTILE, 60, LONG_SHOTS, 5),
                    reqs(HOSTILE, 120, NETHER_KILLS, 20),
                    reqs(HOSTILE, 200, LONG_SHOTS, 25, ELITE, 2));
            case CROSSBOW -> List.of(
                    reqs(KILLS, 10),
                    reqs(KILLS, 30),
                    reqs(HOSTILE, 60, RAIDERS, 10),
                    reqs(HOSTILE, 120, LONG_SHOTS, 10),
                    reqs(HOSTILE, 200, RAIDERS, 40, ELITE, 2));
            case TRIDENT -> List.of(
                    reqs(KILLS, 8),
                    reqs(KILLS, 25),
                    reqs(THROWN, 15, AQUATIC, 10),
                    reqs(THROWN, 40, AQUATIC, 30),
                    reqs(KILLS, 150, THROWN, 80, ELITE, 1));
            case MACE -> List.of(
                    reqs(KILLS, 8),
                    reqs(SMASH, 5),
                    reqs(SMASH, 20, HOSTILE, 40),
                    reqs(SMASH, 50, NETHER_KILLS, 20),
                    reqs(SMASH, 100, ELITE, 3));
            case SHEARS -> List.of(
                    reqs(SHEARED, 5),
                    reqs(SHEARED, 12, MINED, 32),
                    reqs(SHEARED, 25),
                    reqs(SHEARED, 50, MINED, 128),
                    reqs(SHEARED, 100, MINED, 256));
            case FISHING_ROD -> List.of(
                    reqs(CATCHES, 5),
                    reqs(CATCHES, 15),
                    reqs(CATCHES, 40, TREASURE, 2),
                    reqs(CATCHES, 80, TREASURE, 6),
                    reqs(CATCHES, 150, TREASURE, 12));
            default -> throw new IllegalArgumentException(kind.id);
        };
    }

    private static List<Map<Perk, Double>> specialRewards(Kind kind, Tier t) {
        List<Map<Perk, Double>> steps = emptySteps(SPECIAL_LEVELS);
        switch (kind) {
            case BOW -> {
                set(steps, t, 1, DRAW, 15);
                set(steps, t, 2, DAMAGE, 10, REINFORCED, 15);
                set(steps, t, 3, ARROW_SAVER, 25, LOOTING, 1);
                set(steps, t, 4, DRAW, 35, DAMAGE, 20, REINFORCED, 35);
                set(steps, t, 5, DAMAGE, 25, ARROW_SAVER, 50, LOOTING, 2, VOLLEY, 1);
            }
            case CROSSBOW -> {
                set(steps, t, 1, DRAW, 20);
                set(steps, t, 2, DAMAGE, 10, REINFORCED, 15);
                set(steps, t, 3, ARROW_SAVER, 25, LOOTING, 1);
                set(steps, t, 4, DRAW, 45, DAMAGE, 20, REINFORCED, 35);
                set(steps, t, 5, DAMAGE, 25, ARROW_SAVER, 50, LOOTING, 2, AUTOLOAD, 1);
            }
            case TRIDENT -> {
                set(steps, t, 1, DAMAGE, 8);
                set(steps, t, 2, REINFORCED, 15, DRAW, 25);
                set(steps, t, 3, DAMAGE, 16, LOOTING, 1);
                set(steps, t, 4, REINFORCED, 35, DRAW, 50);
                set(steps, t, 5, DAMAGE, 25, LOOTING, 2, STORMCALLER, 1);
            }
            case MACE -> {
                set(steps, t, 1, DAMAGE, 8);
                set(steps, t, 2, REINFORCED, 15);
                set(steps, t, 3, DAMAGE, 16, LOOTING, 1);
                set(steps, t, 4, REINFORCED, 35, ATTACK_SPEED, 10);
                set(steps, t, 5, DAMAGE, 25, LOOTING, 2, SHOCKWAVE, 1);
            }
            case SHEARS -> {
                set(steps, t, 1, SPEED, 15);
                set(steps, t, 2, REINFORCED, 20);
                set(steps, t, 3, YIELD, 25);
                set(steps, t, 4, SPEED, 40, REINFORCED, 40);
                set(steps, t, 5, YIELD, 50, SHEAR_SWEEP, 1);
            }
            case FISHING_ROD -> {
                set(steps, t, 1, LURE, 1);
                set(steps, t, 2, REINFORCED, 20);
                set(steps, t, 3, YIELD, 20);
                set(steps, t, 4, LURE, 2, REINFORCED, 40, LUCK, 1);
                set(steps, t, 5, LURE, 3, YIELD, 40, LUCK, 2);
            }
            default -> throw new IllegalArgumentException(kind.id);
        }
        return steps;
    }

    // ------------------------------------------------------------------------------------------------
    // Reward helpers: a step sets a perk's total from that level on.

    private static List<Map<Perk, Double>> emptySteps(int levels) {
        List<Map<Perk, Double>> steps = new ArrayList<>();
        for (int i = 0; i < levels; i++) steps.add(new EnumMap<>(Perk.class));
        return steps;
    }

    /** set(steps, tier, level, PERK, value, PERK, value, ...); speed/damage percentages scale with the material. */
    private static void set(List<Map<Perk, Double>> steps, Tier t, int level, Object... pairs) {
        for (int i = 0; i < pairs.length; i += 2) {
            Perk perk = (Perk) pairs[i];
            double value = ((Number) pairs[i + 1]).doubleValue();
            if (perk == SPEED || perk == DAMAGE || perk == ATTACK_SPEED) value = Math.round(value * t.power);
            steps.get(level - 1).put(perk, value);
        }
    }

    /** ladder(steps, tier, PERK, level, value, level, value, ...): one perk growing over several levels. */
    private static void ladder(List<Map<Perk, Double>> steps, Tier t, Perk perk, int... levelValue) {
        for (int i = 0; i < levelValue.length; i += 2) set(steps, t, levelValue[i], perk, levelValue[i + 1]);
    }
}
