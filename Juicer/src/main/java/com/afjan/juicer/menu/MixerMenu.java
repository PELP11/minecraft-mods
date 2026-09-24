package com.afjan.juicer.menu;

import com.afjan.juicer.block.Contents;
import com.afjan.juicer.block.entity.MixerBlockEntity;
import com.afjan.juicer.fruit.Fruit;
import com.afjan.juicer.registry.ModMenus;

import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

public class MixerMenu extends AbstractContainerMenu {
    public static final int BUTTON_EMPTY_TANK = 0;
    private static final int CONTAINER_SLOTS = 1;
    private static final int INV_START = CONTAINER_SLOTS;
    private static final int HOTBAR_START = INV_START + 27;
    private static final int INV_END = HOTBAR_START + 9;

    private final Container container;
    private final ContainerData data;

    /** Client-side constructor; the real values are synced from the server. */
    public MixerMenu(int containerId, Inventory inventory) {
        this(containerId, inventory, new SimpleContainer(CONTAINER_SLOTS), new SimpleContainerData(MixerBlockEntity.DATA_COUNT));
    }

    public MixerMenu(int containerId, Inventory inventory, Container container, ContainerData data) {
        super(ModMenus.MIXER.get(), containerId);
        checkContainerSize(container, CONTAINER_SLOTS);
        checkContainerDataCount(data, MixerBlockEntity.DATA_COUNT);
        this.container = container;
        this.data = data;
        this.addSlot(new Slot(container, 0, 44, 35) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return Fruit.fromStack(stack) != null;
            }
        });
        this.addDataSlots(data);
        this.addStandardInventorySlots(inventory, 8, 84);
    }

    @Override
    public boolean stillValid(Player player) {
        return this.container.stillValid(player);
    }

    @Override
    public boolean clickMenuButton(Player player, int buttonId) {
        if (buttonId == BUTTON_EMPTY_TANK) {
            if (this.container instanceof MixerBlockEntity mixer) {
                mixer.dumpTank();
            }
            return true;
        }
        return false;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int slotIndex) {
        Slot slot = this.slots.get(slotIndex);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        if (slotIndex < CONTAINER_SLOTS) {
            if (!this.moveItemStackTo(stack, INV_START, INV_END, true)) {
                return ItemStack.EMPTY;
            }
        } else if (Fruit.fromStack(stack) != null) {
            if (!this.moveItemStackTo(stack, 0, CONTAINER_SLOTS, false)) {
                return ItemStack.EMPTY;
            }
        } else if (slotIndex < HOTBAR_START) {
            if (!this.moveItemStackTo(stack, HOTBAR_START, INV_END, false)) {
                return ItemStack.EMPTY;
            }
        } else if (!this.moveItemStackTo(stack, INV_START, HOTBAR_START, false)) {
            return ItemStack.EMPTY;
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

    public int getProgress() {
        return this.data.get(MixerBlockEntity.DATA_PROGRESS);
    }

    public int getMaxProgress() {
        return this.data.get(MixerBlockEntity.DATA_MAX_PROGRESS);
    }

    public Contents getContents() {
        return Contents.byOrdinal(this.data.get(MixerBlockEntity.DATA_CONTENTS));
    }

    public int getAmount() {
        return this.data.get(MixerBlockEntity.DATA_AMOUNT);
    }

    public int getCapacity() {
        return this.data.get(MixerBlockEntity.DATA_CAPACITY);
    }
}
