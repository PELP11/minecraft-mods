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
 * The whole challenge catalogue, in code: 7 materials x 6 tool types plus 6 special tools, five milestones
 * each. Amounts scale with the material ({@link Tier#scale}); the later milestones ask for work somewhere
 * specific (deep caves, the Nether, the End, rare blocks) so a tool is mastered by using it everywhere.
 */
public final class Tracks {
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
        List<List<Req>> reqs = requirements(kind, tier);
        List<Map<Perk, Double>> steps = rewards(kind, tier);
        List<Milestone> milestones = new ArrayList<>();
        EnumMap<Perk, Double> totals = new EnumMap<>(Perk.class);
        for (int i = 0; i < Track.MAX_LEVEL; i++) {
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

    /** Round a scaled amount to a number that reads well (37 -> 35, 1437 -> 1450). */
    static int nice(double v) {
        if (v < 20) return (int) Math.max(1, Math.round(v));
        if (v < 100) return (int) (Math.round(v / 5) * 5);
        if (v < 1000) return (int) (Math.round(v / 10) * 10);
        return (int) (Math.round(v / 50) * 50);
    }

    /** reqs(STAT, amount, STAT, amount, ...) */
    private static List<Req> reqs(Object... pairs) {
        List<Req> list = new ArrayList<>();
        for (int i = 0; i < pairs.length; i += 2) {
            list.add(new Req((Stat) pairs[i], nice(((Number) pairs[i + 1]).doubleValue())));
        }
        return list;
    }

    private static List<Req> plus(List<Req> base, Object... extra) {
        List<Req> list = new ArrayList<>(base);
        list.addAll(reqs(extra));
        return list;
    }

    private static List<List<Req>> requirements(Kind kind, Tier t) {
        double s = t.scale;
        return switch (kind) {
            case PICKAXE -> List.of(
                    reqs(MINED, 15 * s),
                    reqs(MINED, 45 * s, ORES, 2 * s),
                    plus(reqs(MINED, 100 * s), special(kind, t, 3)),
                    plus(reqs(MINED, 180 * s), special(kind, t, 4)),
                    plus(reqs(MINED, 300 * s), special(kind, t, 5)));
            case AXE -> List.of(
                    reqs(LOGS, 10 * s),
                    reqs(LOGS, 30 * s),
                    plus(reqs(LOGS, 70 * s), special(kind, t, 3)),
                    plus(reqs(LOGS, 130 * s), special(kind, t, 4)),
                    plus(reqs(LOGS, 220 * s), special(kind, t, 5)));
            case SHOVEL -> List.of(
                    reqs(MINED, 20 * s),
                    reqs(MINED, 60 * s),
                    plus(reqs(MINED, 130 * s), special(kind, t, 3)),
                    plus(reqs(MINED, 240 * s), special(kind, t, 4)),
                    plus(reqs(MINED, 400 * s), special(kind, t, 5)));
            case HOE -> List.of(
                    reqs(TILLED, 12 * s),
                    reqs(HARVESTED, 30 * s),
                    plus(reqs(HARVESTED, 80 * s), special(kind, t, 3)),
                    plus(reqs(HARVESTED, 160 * s), special(kind, t, 4)),
                    plus(reqs(HARVESTED, 280 * s), special(kind, t, 5)));
            case SWORD, SPEAR -> List.of(
                    reqs(KILLS, 5 * s),
                    reqs(KILLS, 15 * s),
                    plus(reqs(HOSTILE, 25 * s), special(kind, t, 3)),
                    plus(reqs(HOSTILE, 50 * s), special(kind, t, 4)),
                    plus(reqs(HOSTILE, 90 * s), special(kind, t, 5)));
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
        };
    }

    /** The material-specific part of milestones III, IV and V. */
    private static Object[] special(Kind kind, Tier t, int level) {
        Object[][] byLevel = switch (kind) {
            case PICKAXE -> switch (t) {
                case WOOD -> of3(new Object[]{ORES, 5}, new Object[]{ORES, 10}, new Object[]{ORES, 20});
                case STONE -> of3(new Object[]{ORES, 12}, new Object[]{DEEP, 60}, new Object[]{ORES, 40});
                case COPPER -> of3(new Object[]{ORES, 20}, new Object[]{DEEP, 120}, new Object[]{ORES, 60});
                case IRON -> of3(new Object[]{ORES, 40}, new Object[]{DEEP, 250}, new Object[]{GEMS, 8});
                case GOLD -> of3(new Object[]{NETHER_MINED, 40}, new Object[]{NETHER_MINED, 120}, new Object[]{NETHER_MINED, 250});
                case DIAMOND -> of3(new Object[]{NETHER_MINED, 300}, new Object[]{END_MINED, 200, OBSIDIAN, 16}, new Object[]{DEBRIS, 8});
                case NETHERITE -> of3(new Object[]{NETHER_MINED, 600, DEBRIS, 4}, new Object[]{END_MINED, 500, OBSIDIAN, 48},
                        new Object[]{DEBRIS, 24, GEMS, 24});
                default -> none();
            };
            case AXE -> switch (t) {
                case WOOD -> of3(new Object[]{KILLS, 3}, new Object[]{KILLS, 8}, new Object[]{KILLS, 15});
                case STONE -> of3(new Object[]{KILLS, 5}, new Object[]{KILLS, 12}, new Object[]{KILLS, 25});
                case COPPER -> of3(new Object[]{KILLS, 8}, new Object[]{KILLS, 20}, new Object[]{KILLS, 40});
                case IRON -> of3(new Object[]{KILLS, 10}, new Object[]{NETHER_MINED, 64}, new Object[]{KILLS, 60});
                case GOLD -> of3(new Object[]{NETHER_MINED, 24}, new Object[]{NETHER_MINED, 64}, new Object[]{NETHER_MINED, 128});
                case DIAMOND -> of3(new Object[]{NETHER_MINED, 160}, new Object[]{END_MINED, 64}, new Object[]{KILLS, 100, ELITE, 1});
                case NETHERITE -> of3(new Object[]{NETHER_MINED, 320}, new Object[]{END_MINED, 160}, new Object[]{KILLS, 200, ELITE, 3});
                default -> none();
            };
            case SHOVEL -> switch (t) {
                case WOOD -> of3(new Object[]{GRAVEL, 10}, new Object[]{SAND, 32}, new Object[]{CLAY, 12});
                case STONE -> of3(new Object[]{GRAVEL, 20}, new Object[]{SAND, 64}, new Object[]{CLAY, 24});
                case COPPER -> of3(new Object[]{SAND, 100}, new Object[]{CLAY, 32}, new Object[]{SNOW, 64});
                case IRON -> of3(new Object[]{CLAY, 48}, new Object[]{SNOW, 128}, new Object[]{SOUL, 128});
                case GOLD -> of3(new Object[]{SOUL, 32}, new Object[]{SOUL, 96}, new Object[]{NETHER_MINED, 200});
                case DIAMOND -> of3(new Object[]{SOUL, 256}, new Object[]{SNOW, 256, CLAY, 96}, new Object[]{NETHER_MINED, 800});
                case NETHERITE -> of3(new Object[]{SOUL, 400}, new Object[]{SNOW, 400, CLAY, 160}, new Object[]{NETHER_MINED, 1500});
                default -> none();
            };
            case HOE -> switch (t) {
                case WOOD -> of3(new Object[]{TILLED, 40}, new Object[]{MINED, 24}, new Object[]{TILLED, 80});
                case STONE -> of3(new Object[]{TILLED, 60}, new Object[]{MINED, 48}, new Object[]{MINED, 96});
                case COPPER -> of3(new Object[]{TILLED, 90}, new Object[]{MINED, 96}, new Object[]{WARTS, 32});
                case IRON -> of3(new Object[]{WARTS, 48}, new Object[]{MINED, 160}, new Object[]{SCULK, 48});
                case GOLD -> of3(new Object[]{WARTS, 24}, new Object[]{WARTS, 64}, new Object[]{NETHER_MINED, 64});
                case DIAMOND -> of3(new Object[]{WARTS, 128}, new Object[]{SCULK, 128}, new Object[]{NETHER_MINED, 256});
                case NETHERITE -> of3(new Object[]{WARTS, 256}, new Object[]{SCULK, 256}, new Object[]{NETHER_MINED, 512});
                default -> none();
            };
            case SWORD -> switch (t) {
                case IRON -> of3(new Object[]{}, new Object[]{NETHER_KILLS, 20}, new Object[]{ELITE, 2});
                case GOLD -> of3(new Object[]{NETHER_KILLS, 10}, new Object[]{NETHER_KILLS, 30}, new Object[]{NETHER_KILLS, 60});
                case DIAMOND -> of3(new Object[]{NETHER_KILLS, 50}, new Object[]{END_KILLS, 40}, new Object[]{ELITE, 5});
                case NETHERITE -> of3(new Object[]{NETHER_KILLS, 100}, new Object[]{END_KILLS, 100}, new Object[]{ELITE, 10});
                default -> none();
            };
            case SPEAR -> switch (t) {
                case WOOD -> of3(new Object[]{}, new Object[]{MOUNTED, 3}, new Object[]{});
                case STONE -> of3(new Object[]{}, new Object[]{MOUNTED, 5}, new Object[]{});
                case COPPER -> of3(new Object[]{}, new Object[]{MOUNTED, 8}, new Object[]{});
                case IRON -> of3(new Object[]{}, new Object[]{MOUNTED, 12}, new Object[]{NETHER_KILLS, 25});
                case GOLD -> of3(new Object[]{NETHER_KILLS, 10}, new Object[]{MOUNTED, 8}, new Object[]{NETHER_KILLS, 40});
                case DIAMOND -> of3(new Object[]{MOUNTED, 25}, new Object[]{NETHER_KILLS, 50}, new Object[]{ELITE, 5});
                case NETHERITE -> of3(new Object[]{MOUNTED, 40}, new Object[]{NETHER_KILLS, 100}, new Object[]{ELITE, 10});
                default -> none();
            };
            default -> none();
        };
        return byLevel[level - 3];
    }

    private static Object[][] of3(Object[] l3, Object[] l4, Object[] l5) {
        return new Object[][]{l3, l4, l5};
    }

    private static Object[][] none() {
        return new Object[][]{{}, {}, {}};
    }

    // ------------------------------------------------------------------------------------------------
    // Rewards: each step sets the perk's total from that level on.

    private static List<Map<Perk, Double>> rewards(Kind kind, Tier t) {
        List<Map<Perk, Double>> steps = new ArrayList<>();
        for (int i = 0; i < Track.MAX_LEVEL; i++) steps.add(new EnumMap<>(Perk.class));
        switch (kind) {
            case PICKAXE -> {
                set(steps, t, 1, SPEED, 10);
                set(steps, t, 2, REINFORCED, 15);
                set(steps, t, 3, SPEED, 25, YIELD, 10);
                set(steps, t, 4, SPEED, 40, REINFORCED, 35, YIELD, 20);
                set(steps, t, 5, SPEED, 60, YIELD, 30, VEIN, t.veinSize);
                if (t == Tier.DIAMOND || t == Tier.NETHERITE) set(steps, t, 5, EXCAVATE, 1);
            }
            case AXE -> {
                set(steps, t, 1, SPEED, 10);
                set(steps, t, 2, REINFORCED, 15, DAMAGE, 5);
                set(steps, t, 3, SPEED, 25, YIELD, 10);
                set(steps, t, 4, SPEED, 40, REINFORCED, 35, DAMAGE, 10, YIELD, 20);
                set(steps, t, 5, SPEED, 60, DAMAGE, 15, YIELD, 30, TIMBER, t.timberSize);
            }
            case SHOVEL -> {
                set(steps, t, 1, SPEED, 10);
                set(steps, t, 2, REINFORCED, 15);
                set(steps, t, 3, SPEED, 25, TREASURE_HUNTER, 2);
                set(steps, t, 4, SPEED, 40, REINFORCED, 35, TREASURE_HUNTER, 4);
                set(steps, t, 5, SPEED, 60, TREASURE_HUNTER, 5, EXCAVATE, t == Tier.NETHERITE ? 2 : 1);
            }
            case HOE -> {
                set(steps, t, 1, YIELD, 10);
                set(steps, t, 2, REINFORCED, 15, REPLANT, 1);
                set(steps, t, 3, YIELD, 25, SPEED, 25);
                set(steps, t, 4, YIELD, 35, REINFORCED, 35, SPEED, 40, REAPER, 1);
                set(steps, t, 5, YIELD, 50, SPEED, 60, REAPER, t.reaperRadius);
            }
            case SWORD -> {
                set(steps, t, 1, DAMAGE, 5);
                set(steps, t, 2, ATTACK_SPEED, 8, REINFORCED, 15);
                set(steps, t, 3, DAMAGE, 12, LOOTING, 1);
                set(steps, t, 4, ATTACK_SPEED, 15, REINFORCED, 35, LIFESTEAL, 5);
                set(steps, t, 5, DAMAGE, 20, LOOTING, 2, LIFESTEAL, 8, EXECUTIONER, 1);
            }
            case SPEAR -> {
                set(steps, t, 1, DAMAGE, 5);
                set(steps, t, 2, ATTACK_SPEED, 8, REINFORCED, 15);
                set(steps, t, 3, DAMAGE, 12, LOOTING, 1);
                set(steps, t, 4, DAMAGE, 16, ATTACK_SPEED, 15, REINFORCED, 35);
                set(steps, t, 5, DAMAGE, 22, LOOTING, 2, CAVALRY, 1);
            }
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
        }
        if (t == Tier.GOLD) {
            // Gilded: golden tools are the experience tools.
            set(steps, t, 3, XP, 50);
            set(steps, t, 5, XP, 100);
        }
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
}
