package com.afjan.drillworks.menu;

import com.afjan.drillworks.block.entity.RefineryBlockEntity;
import com.afjan.drillworks.registry.ModItems;
import com.afjan.drillworks.registry.ModMenus;

import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

public class RefineryMenu extends AbstractContainerMenu {
    public static final int CRUDE_X = 26;
    public static final int CRUDE_Y = 18;
    public static final int FUEL_Y = 54;
    public static final int CAN_X = 134;
    private static final int SLOTS = 4;
    private static final int INV_START = SLOTS;
    private static final int HOTBAR_START = INV_START + 27;
    private static final int INV_END = HOTBAR_START + 9;

    private final Container container;
    private final ContainerData data;
    private final Player player;

    public RefineryMenu(int containerId, Inventory inventory) {
        this(containerId, inventory, new SimpleContainer(SLOTS), new SimpleContainerData(RefineryBlockEntity.DATA_COUNT));
    }

    public RefineryMenu(int containerId, Inventory inventory, Container container, ContainerData data) {
        super(ModMenus.REFINERY.get(), containerId);
        checkContainerSize(container, SLOTS);
        checkContainerDataCount(data, RefineryBlockEntity.DATA_COUNT);
        this.container = container;
        this.data = data;
        this.player = inventory.player;
        this.addSlot(new Slot(container, RefineryBlockEntity.SLOT_CRUDE, CRUDE_X, CRUDE_Y) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return stack.is(ModItems.CRUDE_OIL.get());
            }
        });
        this.addSlot(new Slot(container, RefineryBlockEntity.SLOT_FUEL, CRUDE_X, FUEL_Y) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return RefineryMenu.this.isFuel(stack);
            }
        });
        this.addSlot(new Slot(container, RefineryBlockEntity.SLOT_CAN_IN, CAN_X, CRUDE_Y) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return stack.is(ModItems.EMPTY_CANISTER.get());
            }
        });
        this.addSlot(new Slot(container, RefineryBlockEntity.SLOT_CAN_OUT, CAN_X, FUEL_Y) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return false;
            }
        });
        this.addDataSlots(data);
        this.addStandardInventorySlots(inventory, 8, 84);
    }

    private boolean isFuel(ItemStack stack) {
        return stack.has(net.minecraft.core.component.DataComponents.COOKING_FUEL) && !stack.is(ModItems.CRUDE_OIL.get());
    }

    @Override
    public boolean stillValid(Player player) {
        return this.container.stillValid(player);
    }

    public int get(int index) {
        return this.data.get(index);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = this.slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        if (index < SLOTS) {
            if (!this.moveItemStackTo(stack, INV_START, INV_END, true)) {
                return ItemStack.EMPTY;
            }
        } else if (stack.is(ModItems.CRUDE_OIL.get())) {
            if (!this.moveItemStackTo(stack, 0, 1, false)) {
                return ItemStack.EMPTY;
            }
        } else if (stack.is(ModItems.EMPTY_CANISTER.get())) {
            if (!this.moveItemStackTo(stack, 2, 3, false)) {
                return ItemStack.EMPTY;
            }
        } else if (this.isFuel(stack)) {
            if (!this.moveItemStackTo(stack, 1, 2, false)) {
                return ItemStack.EMPTY;
            }
        } else if (index < HOTBAR_START) {
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
}
