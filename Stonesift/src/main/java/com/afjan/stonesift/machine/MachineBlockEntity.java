package com.afjan.stonesift.machine;

import java.util.ArrayList;
import java.util.List;

import com.afjan.stonesift.registry.ModBlockEntities;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/** Container, hopper faces, synced data and menu for every machine; subclasses only implement {@link #serverTick}. */
public abstract class MachineBlockEntity extends BaseContainerBlockEntity implements WorldlyContainer {
    protected final MachineType type;
    protected NonNullList<ItemStack> items;
    protected final int[] data;
    private final ContainerData dataAccess = new ContainerData() {
        @Override
        public int get(int index) {
            return MachineBlockEntity.this.data[index];
        }

        @Override
        public void set(int index, int value) {
            MachineBlockEntity.this.data[index] = value;
        }

        @Override
        public int getCount() {
            return MachineBlockEntity.this.data.length;
        }
    };

    protected MachineBlockEntity(MachineType type, BlockPos pos, BlockState state) {
        super(ModBlockEntities.MACHINES.get(type).get(), pos, state);
        this.type = type;
        this.items = NonNullList.withSize(type.slots().size(), ItemStack.EMPTY);
        this.data = new int[type.dataCount()];
    }

    public abstract void serverTick(ServerLevel level);

    /** Energy values do not fit the 16-bit data slots: they are sent as two 15-bit halves. */
    protected void putEnergy(int index, int energy) {
        this.data[index] = energy & 0x7FFF;
        this.data[index + 1] = energy >> 15;
    }

    /** Puts all stacks into the output slots, or nothing when they do not all fit. */
    protected boolean output(List<ItemStack> stacks) {
        List<ItemStack> copy = new ArrayList<>();
        for (ItemStack s : this.items) {
            copy.add(s.copy());
        }
        for (ItemStack stack : stacks) {
            ItemStack rest = stack.copy();
            for (int i = 0; i < copy.size() && !rest.isEmpty(); i++) {
                if (!this.type.isOutput(i)) {
                    continue;
                }
                ItemStack slot = copy.get(i);
                if (slot.isEmpty()) {
                    copy.set(i, rest.copy());
                    rest.setCount(0);
                } else if (ItemStack.isSameItemSameComponents(slot, rest)) {
                    int move = Math.min(rest.getCount(), slot.getMaxStackSize() - slot.getCount());
                    slot.grow(move);
                    rest.shrink(move);
                }
            }
            if (!rest.isEmpty()) {
                return false;
            }
        }
        for (int i = 0; i < copy.size(); i++) {
            if (this.type.isOutput(i)) {   // inputs stay the same objects: callers still hold them
                this.items.set(i, copy.get(i));
            }
        }
        this.setChanged();
        return true;
    }

    public ItemStack getItem(int slot) {
        return this.items.get(slot);
    }

    @Override
    protected Component getDefaultName() {
        return Component.translatable("container.stonesift." + this.type.key());
    }

    @Override
    public int getContainerSize() {
        return this.items.size();
    }

    @Override
    protected NonNullList<ItemStack> getItems() {
        return this.items;
    }

    @Override
    protected void setItems(NonNullList<ItemStack> items) {
        this.items = items;
    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        return this.type.accepts(slot, stack);
    }

    @Override
    public int[] getSlotsForFace(Direction direction) {
        return this.type.faces(direction);
    }

    @Override
    public boolean canPlaceItemThroughFace(int slot, ItemStack stack, Direction direction) {
        return this.canPlaceItem(slot, stack);
    }

    @Override
    public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction direction) {
        return this.type.isOutput(slot);
    }

    @Override
    protected AbstractContainerMenu createMenu(int containerId, Inventory inventory) {
        return new MachineMenu(this.type, containerId, inventory, this, this.dataAccess);
    }

    protected void saveExtra(ValueOutput output) {}

    protected void loadExtra(ValueInput input) {}

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        this.items = NonNullList.withSize(this.type.slots().size(), ItemStack.EMPTY);
        ContainerHelper.loadAllItems(input, this.items);
        this.loadExtra(input);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        ContainerHelper.saveAllItems(output, this.items);
        this.saveExtra(output);
    }
}
