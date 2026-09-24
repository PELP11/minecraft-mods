package com.afjan.juicer.block.entity;

import org.jspecify.annotations.Nullable;

import com.afjan.juicer.block.Contents;
import com.afjan.juicer.fruit.Fruit;

import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/** A single-fluid tank holding one fruit's concentrate, measured in millibuckets (mB). */
public class ConcentrateTank {
    private final int capacity;
    private @Nullable Fruit fruit;
    private int amount;

    public ConcentrateTank(int capacity) {
        this.capacity = capacity;
    }

    public @Nullable Fruit fruit() {
        return this.amount > 0 ? this.fruit : null;
    }

    public int amount() {
        return this.amount;
    }

    public int capacity() {
        return this.capacity;
    }

    public int space() {
        return this.capacity - this.amount;
    }

    public boolean isEmpty() {
        return this.amount <= 0;
    }

    public boolean canAccept(Fruit fruit) {
        return this.amount <= 0 || this.fruit == fruit;
    }

    /** @return how much was (or would be, when simulating) accepted. */
    public int fill(Fruit fruit, int maxAmount, boolean simulate) {
        if (maxAmount <= 0 || !this.canAccept(fruit)) {
            return 0;
        }
        int accepted = Math.min(maxAmount, this.space());
        if (!simulate && accepted > 0) {
            this.fruit = fruit;
            this.amount += accepted;
        }
        return accepted;
    }

    /** @return how much was removed. */
    public int drain(int maxAmount) {
        int drained = Math.min(maxAmount, this.amount);
        this.amount -= drained;
        if (this.amount <= 0) {
            this.amount = 0;
            this.fruit = null;
        }
        return drained;
    }

    public void clear() {
        this.amount = 0;
        this.fruit = null;
    }

    public Contents contents() {
        return Contents.of(this.fruit());
    }

    /** Fill level from 0 (empty) to {@code steps} (full); any concentrate at all shows at least level 1. */
    public int level(int steps) {
        if (this.amount <= 0) {
            return 0;
        }
        int level = (int) Math.ceil(this.amount * (double) steps / this.capacity);
        return Math.max(1, Math.min(steps, level));
    }

    public void save(ValueOutput output, String prefix) {
        Fruit current = this.fruit();
        output.putString(prefix + "_fruit", current == null ? "" : current.id());
        output.putInt(prefix + "_amount", current == null ? 0 : this.amount);
    }

    public void load(ValueInput input, String prefix) {
        this.fruit = Fruit.byId(input.getStringOr(prefix + "_fruit", ""));
        this.amount = this.fruit == null ? 0 : Math.max(0, Math.min(this.capacity, input.getIntOr(prefix + "_amount", 0)));
        if (this.amount == 0) {
            this.fruit = null;
        }
    }
}
