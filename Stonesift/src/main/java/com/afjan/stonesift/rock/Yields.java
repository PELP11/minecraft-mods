package com.afjan.stonesift.rock;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.jetbrains.annotations.Nullable;

import com.afjan.stonesift.registry.ModItems;

import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * The whole processing chain in numbers. Rich material (from the Deep Drill) rolls three times at every stage.
 * Starting balance for 64 cobblestone: hand sieve ~4 raw iron, + sluice ~9, + flotation ~14.
 */
public final class Yields {
    /** Sluice: expected units per segment, relative to the sieve weight (8 segments = 1.25x the sieve). */
    public static final double SLUICE_PER_SEGMENT = 0.156;
    /** Flotation: concentrates per slurry, relative to the sieve weight (5 ingots from 128 slurry of cobblestone). */
    public static final double FLOTATION = 0.3125;

    private Yields() {}

    /** One gravel through a sieve with the given mesh tier: resources the mesh lets through (no rock flour). */
    public static List<ItemStack> sieve(Rock rock, boolean rich, int meshTier, RandomSource random) {
        List<ItemStack> out = new ArrayList<>();
        for (int roll = 0; roll < (rich ? 3 : 1); roll++) {
            for (Map.Entry<Resource, Double> e : rock.profile().entrySet()) {
                if (e.getKey().tier() <= meshTier) {
                    add(out, e.getKey(), count(e.getValue(), random));
                }
            }
        }
        return out;
    }

    /** One sluice segment washing one portion of rock flour: everything but gems and netherite. */
    public static List<ItemStack> sluiceSegment(Rock rock, boolean rich, RandomSource random) {
        List<ItemStack> out = new ArrayList<>();
        for (int roll = 0; roll < (rich ? 3 : 1); roll++) {
            for (Map.Entry<Resource, Double> e : rock.profile().entrySet()) {
                if (e.getKey().tier() <= 2) {
                    add(out, e.getKey(), count(e.getValue() * SLUICE_PER_SEGMENT, random));
                }
            }
        }
        return out;
    }

    /** Flotation of one fine slurry with a reagent: concentrates of the reagent's metal only. */
    public static int flotation(Rock rock, boolean rich, Resource metal, RandomSource random) {
        int n = 0;
        for (int roll = 0; roll < (rich ? 3 : 1); roll++) {
            n += count(rock.weight(metal) * FLOTATION, random);
        }
        return n;
    }

    /** The metal a flotation reagent brings up, or null. */
    public static @Nullable Resource reagent(ItemStack stack) {
        if (stack.is(Items.BONE_MEAL)) {
            return Resource.COPPER;
        }
        if (stack.is(Items.DRIED_KELP)) {
            return Resource.IRON;
        }
        if (stack.is(Items.GLOWSTONE_DUST)) {
            return Resource.GOLD;
        }
        if (stack.is(Items.AMETHYST_SHARD)) {
            return Resource.DIAMOND;
        }
        return null;
    }

    public static ItemStack concentrate(Resource metal, int count) {
        return switch (metal) {
            case IRON -> new ItemStack(ModItems.IRON_CONCENTRATE.get(), count);
            case COPPER -> new ItemStack(ModItems.COPPER_CONCENTRATE.get(), count);
            case GOLD -> new ItemStack(ModItems.GOLD_CONCENTRATE.get(), count);
            case DIAMOND -> new ItemStack(ModItems.DIAMOND_CONCENTRATE.get(), count);
            default -> ItemStack.EMPTY;
        };
    }

    /** Expected value -> whole units: floor plus one more with the fractional chance. */
    public static int count(double expected, RandomSource random) {
        int whole = (int) expected;
        return whole + (random.nextDouble() < expected - whole ? 1 : 0);
    }

    private static void add(List<ItemStack> out, Resource resource, int count) {
        if (count <= 0) {
            return;
        }
        for (ItemStack s : out) {
            if (s.is(resource.item())) {
                s.grow(count);
                return;
            }
        }
        out.add(new ItemStack(resource.item(), count));
    }
}
