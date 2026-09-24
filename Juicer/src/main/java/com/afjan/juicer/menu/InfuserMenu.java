package com.afjan.juicer.menu;

import com.afjan.juicer.block.Contents;
import com.afjan.juicer.block.entity.InfuserBlockEntity;
import com.afjan.juicer.block.entity.MixerBlockEntity;
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
import net.minecraft.world.item.Items;

public class InfuserMenu extends AbstractContainerMenu {
    public static final int BUTTON_EMPTY_TANK = 0;
    private static final int CONTAINER_SLOTS = 3;
    private static final int INV_START = CONTAINER_SLOTS;
    private static final int HOTBAR_START = INV_START + 27;
    private static final int INV_END = HOTBAR_START + 9;

    private final Container container;
    private final ContainerData data;

    public InfuserMenu(int containerId, Inventory inventory) {
        this(containerId, inventory, new SimpleContainer(CONTAINER_SLOTS), new SimpleContainerData(MixerBlockEntity.DATA_COUNT));
    }

    public InfuserMenu(int containerId, Inventory inventory, Container container, ContainerData data) {
        super(ModMenus.INFUSER.get(), containerId);
        checkContainerSize(container, CONTAINER_SLOTS);
        checkContainerDataCount(data, MixerBlockEntity.DATA_COUNT);
        this.container = container;
        this.data = data;
        this.addSlot(new Slot(container, InfuserBlockEntity.SLOT_SUGAR, 62, 20) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return stack.is(Items.SUGAR);
            }
        });
        this.addSlot(new Slot(container, InfuserBlockEntity.SLOT_BOTTLE, 62, 50) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return stack.is(Items.GLASS_BOTTLE);
            }
        });
        this.addSlot(new Slot(container, InfuserBlockEntity.SLOT_OUTPUT, 124, 35) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return false;
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
            if (this.container instanceof InfuserBlockEntity infuser) {
                infuser.dumpTank();
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
            if (slotIndex == InfuserBlockEntity.SLOT_OUTPUT) {
                slot.onQuickCraft(stack, original);
            }
        } else if (stack.is(Items.SUGAR)) {
            if (!this.moveItemStackTo(stack, InfuserBlockEntity.SLOT_SUGAR, InfuserBlockEntity.SLOT_SUGAR + 1, false)) {
                return ItemStack.EMPTY;
            }
        } else if (stack.is(Items.GLASS_BOTTLE)) {
            if (!this.moveItemStackTo(stack, InfuserBlockEntity.SLOT_BOTTLE, InfuserBlockEntity.SLOT_BOTTLE + 1, false)) {
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
