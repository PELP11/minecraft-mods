package com.afjan.stonesift.machine;

import java.util.List;
import java.util.Locale;
import java.util.function.Predicate;

import org.jetbrains.annotations.Nullable;

import com.afjan.stonesift.item.RockItem;
import com.afjan.stonesift.item.SimpleItems;
import com.afjan.stonesift.registry.ModItems;
import com.afjan.stonesift.rock.Rock;
import com.afjan.stonesift.rock.Yields;

import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Slot layout, hopper faces, synced data and screen gauges of every machine with a menu. One generic block entity
 * base, menu and screen read this table.
 */
public enum MachineType {
    GRINDER(166, 4,
            List.of(in(56, 17, s -> Rock.of(s) != null), in(56, 53, MachineType::grinderFuel), out(116, 35), out(134, 35)),
            new int[]{0}, new int[]{1}, new int[]{2, 3},
            List.of(new Bar(80, 38, 24, 5, 0, 1, 0xFFE8A020, false), new Bar(38, 17, 6, 52, 2, 3, 0xFFE06020, true))),
    SHAKER_SIEVE(166, 1,
            List.of(in(26, 26, s -> RockItem.of(s, RockItem.Stage.GRAVEL) != null), in(26, 50, s -> s.getItem() instanceof SimpleItems.Mesh),
                    out(98, 17), out(116, 17), out(134, 17), out(98, 35), out(116, 35), out(134, 35), out(98, 53), out(116, 53), out(134, 53)),
            new int[]{0}, new int[]{0, 1}, new int[]{2, 3, 4, 5, 6, 7, 8, 9, 10},
            List.of()),
    ROCK_FORMER(166, 4,
            List.of(in(35, 35, s -> Rock.of(s) != null), in(62, 53, MachineType::upgrade), in(80, 53, MachineType::upgrade), out(125, 35)),
            new int[]{3}, new int[]{3}, new int[]{3},
            List.of(new Bar(62, 38, 40, 5, 0, 1, 0xFF60B0E0, false))),
    FLOTATION_CELL(166, 6,
            List.of(in(35, 20, s -> RockItem.of(s, RockItem.Stage.SLURRY) != null), in(35, 50, s -> Yields.reagent(s) != null),
                    out(116, 26), out(134, 26), out(116, 44), out(134, 44)),
            new int[]{0, 1}, new int[]{0, 1}, new int[]{2, 3, 4, 5},
            List.of(new Bar(60, 38, 40, 5, 0, 1, 0xFFE8A020, false), Bar.energy(8, 17, 2, FlotationCellBlockEntity.CAPACITY))),
    COAL_GENERATOR(166, 4,
            List.of(in(80, 45, s -> s.has(DataComponents.COOKING_FUEL))),
            new int[]{0}, new int[]{0}, new int[]{0},
            List.of(new Bar(82, 26, 12, 14, 0, 1, 0xFFE06020, true), Bar.energy(8, 17, 2, CoalGeneratorBlockEntity.CAPACITY))),
    DEEP_DRILL(166, 5,
            List.of(in(26, 35, s -> s.is(ModItems.DIAMOND_DRILL_BIT.get()) || s.is(ModItems.NETHERITE_DRILL_BIT.get())),
                    out(98, 17), out(116, 17), out(134, 17), out(98, 35), out(116, 35), out(134, 35), out(98, 53), out(116, 53), out(134, 53)),
            new int[]{0}, new int[]{1, 2, 3, 4, 5, 6, 7, 8, 9}, new int[]{1, 2, 3, 4, 5, 6, 7, 8, 9},
            List.of(new Bar(48, 62, 40, 5, 1, 2, 0xFF909098, false), Bar.energy(8, 17, 3, DeepDrillBlockEntity.CAPACITY)));

    /** A slot: position and what it accepts (null = output only). */
    public record SlotDef(int x, int y, @Nullable Predicate<ItemStack> filter) {}

    /** A gauge on the screen: value data[value] of data[max] (max < 0 = constant -max); energy = lo/hi split pair. */
    public record Bar(int x, int y, int w, int h, int value, int max, int color, boolean vertical, boolean energy) {
        Bar(int x, int y, int w, int h, int value, int max, int color, boolean vertical) {
            this(x, y, w, h, value, max, color, vertical, false);
        }

        static Bar energy(int x, int y, int loIndex, int capacity) {
            return new Bar(x, y, 8, 52, loIndex, -capacity, 0xFFD02020, true, true);
        }
    }

    private final int height;
    private final int dataCount;
    private final List<SlotDef> slots;
    private final int[] top;
    private final int[] side;
    private final int[] bottom;
    private final List<Bar> bars;

    MachineType(int height, int dataCount, List<SlotDef> slots, int[] top, int[] side, int[] bottom, List<Bar> bars) {
        this.height = height;
        this.dataCount = dataCount;
        this.slots = slots;
        this.top = top;
        this.side = side;
        this.bottom = bottom;
        this.bars = bars;
    }

    private static SlotDef in(int x, int y, Predicate<ItemStack> filter) {
        return new SlotDef(x, y, filter);
    }

    private static SlotDef out(int x, int y) {
        return new SlotDef(x, y, null);
    }

    public static int grinderFuelOps(ItemStack stack) {
        if (stack.is(Items.COAL) || stack.is(Items.CHARCOAL)) {
            return 16;
        }
        return stack.is(Items.COAL_BLOCK) ? 144 : 0;
    }

    private static boolean grinderFuel(ItemStack stack) {
        return grinderFuelOps(stack) > 0;
    }

    public static int speedOf(ItemStack stack) {
        return stack.is(ModItems.SPEED_UPGRADE_ADVANCED.get()) ? 4 : stack.is(ModItems.SPEED_UPGRADE.get()) ? 2 : 1;
    }

    private static boolean upgrade(ItemStack stack) {
        return speedOf(stack) > 1;
    }

    public String key() {
        return this.name().toLowerCase(Locale.ROOT);
    }

    public int height() {
        return this.height;
    }

    public int dataCount() {
        return this.dataCount;
    }

    public List<SlotDef> slots() {
        return this.slots;
    }

    public List<Bar> bars() {
        return this.bars;
    }

    public boolean accepts(int slot, ItemStack stack) {
        Predicate<ItemStack> f = this.slots.get(slot).filter();
        return f != null && f.test(stack);
    }

    public boolean isOutput(int slot) {
        return this.slots.get(slot).filter() == null;
    }

    public int[] faces(Direction direction) {
        return direction == Direction.UP ? this.top : direction == Direction.DOWN ? this.bottom : this.side;
    }
}
