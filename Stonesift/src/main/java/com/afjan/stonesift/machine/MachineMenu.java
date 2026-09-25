package com.afjan.stonesift.machine;

import com.afjan.stonesift.registry.ModMenus;

import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

public class MachineMenu extends AbstractContainerMenu {
    private final MachineType type;
    private final Container container;
    private final ContainerData data;
    private final int size;

    public MachineMenu(MachineType type, int containerId, Inventory inventory) {
        this(type, containerId, inventory, new SimpleContainer(type.slots().size()), new SimpleContainerData(type.dataCount()));
    }

    public MachineMenu(MachineType type, int containerId, Inventory inventory, Container container, ContainerData data) {
        super(ModMenus.MACHINES.get(type).get(), containerId);
        this.type = type;
        this.container = container;
        this.data = data;
        this.size = type.slots().size();
        for (int i = 0; i < this.size; i++) {
            final int index = i;
            MachineType.SlotDef def = type.slots().get(i);
            this.addSlot(new Slot(container, i, def.x(), def.y()) {
                @Override
                public boolean mayPlace(ItemStack stack) {
                    return type.accepts(index, stack);
                }
            });
        }
        this.addDataSlots(data);
        this.addStandardInventorySlots(inventory, 8, 84);
    }

    public MachineType type() {
        return this.type;
    }

    public int get(int index) {
        return this.data.get(index);
    }

    public int energy(int loIndex) {
        return this.data.get(loIndex) | this.data.get(loIndex + 1) << 15;
    }

    @Override
    public boolean stillValid(Player player) {
        return this.container.stillValid(player);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = this.slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        int end = this.size + 36;
        if (index < this.size) {
            if (!this.moveItemStackTo(stack, this.size, end, true)) {
                return ItemStack.EMPTY;
            }
        } else {
            boolean moved = false;
            for (int i = 0; i < this.size && !moved; i++) {
                if (this.type.accepts(i, stack)) {
                    moved = this.moveItemStackTo(stack, i, i + 1, false);
                }
            }
            if (!moved) {
                boolean inHotbar = index >= this.size + 27;
                if (!this.moveItemStackTo(stack, inHotbar ? this.size : this.size + 27, inHotbar ? this.size + 27 : end, false)) {
                    return ItemStack.EMPTY;
                }
            }
        }
        if (stack.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        if (stack.getCount() == original.getCount()) {
            return ItemStack.EMPTY;
        }
        slot.onTake(player, stack);
        return original;
    }
}
