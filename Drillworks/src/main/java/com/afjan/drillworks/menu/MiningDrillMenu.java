package com.afjan.drillworks.menu;

import org.jetbrains.annotations.Nullable;

import com.afjan.drillworks.drill.DrillModules;
import com.afjan.drillworks.entity.MiningDrillEntity;
import com.afjan.drillworks.item.DrillHeadItem;
import com.afjan.drillworks.item.ModuleItem;
import com.afjan.drillworks.registry.ModComponents;
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

/**
 * The drill's hold: the head slot with its module sockets (they mirror the head's modules component, so modules
 * travel with the head), the fuel slot, 36 storage slots and the player's inventory.
 */
public class MiningDrillMenu extends AbstractContainerMenu {
    public static final int HEAD_X = 8;
    public static final int HEAD_Y = 20;
    public static final int MODULE_X = 36;
    public static final int FUEL_X = 152;
    public static final int STORAGE_Y = 46;
    public static final int INVENTORY_Y = 130;
    public static final int HEIGHT = 212;

    private static final int MODULE_START = MiningDrillEntity.SIZE;
    private static final int INV_START = MODULE_START + DrillModules.MAX_SOCKETS;
    private static final int HOTBAR_START = INV_START + 27;
    private static final int INV_END = HOTBAR_START + 9;

    private final Container container;
    private final SimpleContainer modules = new SimpleContainer(DrillModules.MAX_SOCKETS);
    private final ContainerData data;
    private final Player player;
    @Nullable
    private final MiningDrillEntity drill;
    private ItemStack lastHead = ItemStack.EMPTY;
    private boolean syncing;

    public MiningDrillMenu(int containerId, Inventory inventory) {
        this(containerId, inventory, new SimpleContainer(MiningDrillEntity.SIZE), new SimpleContainerData(1), null);
    }

    public MiningDrillMenu(int containerId, Inventory inventory, MiningDrillEntity drill) {
        this(containerId, inventory, drill.inventory, new ContainerData() {
            @Override
            public int get(int index) {
                return drill.fuel();
            }

            @Override
            public void set(int index, int value) {}

            @Override
            public int getCount() {
                return 1;
            }
        }, drill);
    }

    private MiningDrillMenu(int containerId, Inventory inventory, Container container, ContainerData data,
            @Nullable MiningDrillEntity drill) {
        super(ModMenus.MINING_DRILL.get(), containerId);
        checkContainerSize(container, MiningDrillEntity.SIZE);
        this.container = container;
        this.data = data;
        this.player = inventory.player;
        this.drill = drill;

        this.addSlot(new Slot(container, MiningDrillEntity.SLOT_HEAD, HEAD_X, HEAD_Y) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return stack.getItem() instanceof DrillHeadItem;
            }

            @Override
            public int getMaxStackSize() {
                return 1;
            }
        });
        this.addSlot(new Slot(container, MiningDrillEntity.SLOT_FUEL, FUEL_X, HEAD_Y) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return stack.is(ModItems.GASOLINE_CANISTER.get()) || stack.is(ModItems.EMPTY_CANISTER.get());
            }
        });
        for (int row = 0; row < 4; row++) {
            for (int col = 0; col < 9; col++) {
                this.addSlot(new Slot(container, MiningDrillEntity.STORAGE_START + row * 9 + col, 8 + col * 18, STORAGE_Y + row * 18));
            }
        }
        for (int i = 0; i < DrillModules.MAX_SOCKETS; i++) {
            final int socket = i;
            this.addSlot(new Slot(this.modules, i, MODULE_X + i * 18, HEAD_Y) {
                @Override
                public boolean mayPlace(ItemStack stack) {
                    return stack.getItem() instanceof ModuleItem && socket < MiningDrillMenu.this.sockets();
                }

                @Override
                public int getMaxStackSize() {
                    return 1;
                }

                @Override
                public boolean isActive() {
                    return socket < MiningDrillMenu.this.sockets();
                }
            });
        }
        this.addDataSlots(data);
        this.addStandardInventorySlots(inventory, 8, INVENTORY_Y);
        this.refreshFromHead();
    }

    /** Sockets of the head in the head slot (0 without a head). */
    public int sockets() {
        return this.container.getItem(MiningDrillEntity.SLOT_HEAD).getItem() instanceof DrillHeadItem head
                ? head.material().sockets() : 0;
    }

    public int fuel() {
        return this.data.get(0);
    }

    @Override
    public boolean stillValid(Player player) {
        return this.drill == null || this.drill.isAlive() && player.distanceToSqr(this.drill) < 100.0;
    }

    /** The head slot belongs to an entity container, so the sockets are reconciled here every tick (server). */
    @Override
    public void broadcastChanges() {
        this.sync();
        super.broadcastChanges();
    }

    private void sync() {
        if (this.syncing || this.player.level().isClientSide()) {
            return;
        }
        this.syncing = true;
        ItemStack head = this.container.getItem(MiningDrillEntity.SLOT_HEAD);
        if (!(head.getItem() instanceof DrillHeadItem)) {
            this.lastHead = ItemStack.EMPTY;
            for (int i = 0; i < DrillModules.MAX_SOCKETS; i++) {
                this.modules.setItem(i, ItemStack.EMPTY);
            }
        } else if (head != this.lastHead) {
            this.refreshFromHead();
        } else {
            DrillModules current = DrillModules.EMPTY;
            for (int i = 0; i < DrillModules.MAX_SOCKETS; i++) {
                if (this.modules.getItem(i).getItem() instanceof ModuleItem module) {
                    current = current.with(i, module.module());
                }
            }
            if (!current.equals(DrillHeadItem.modules(head))) {
                if (current.isEmpty()) {
                    head.remove(ModComponents.MODULES.get());
                } else {
                    head.set(ModComponents.MODULES.get(), current);
                }
                this.container.setChanged();
            }
        }
        this.syncing = false;
    }

    private void refreshFromHead() {
        ItemStack head = this.container.getItem(MiningDrillEntity.SLOT_HEAD);
        if (!(head.getItem() instanceof DrillHeadItem)) {
            return;
        }
        this.lastHead = head;
        DrillModules fitted = DrillHeadItem.modules(head);
        for (int i = 0; i < DrillModules.MAX_SOCKETS; i++) {
            var module = fitted.get(i);
            this.modules.setItem(i, module == null ? ItemStack.EMPTY : new ItemStack(ModItems.MODULES.get(module).get()));
        }
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = this.slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        if (index < INV_START) {
            if (!this.moveItemStackTo(stack, INV_START, INV_END, true)) {
                return ItemStack.EMPTY;
            }
        } else {
            boolean moved = false;
            if (stack.getItem() instanceof DrillHeadItem) {
                moved = this.moveItemStackTo(stack, 0, 1, false);
            } else if (stack.getItem() instanceof ModuleItem) {
                moved = this.moveItemStackTo(stack, MODULE_START, MODULE_START + Math.min(this.sockets(), DrillModules.MAX_SOCKETS), false);
            } else if (stack.is(ModItems.GASOLINE_CANISTER.get())) {
                moved = this.moveItemStackTo(stack, 1, 2, false);
            }
            if (!moved && !this.moveItemStackTo(stack, 2, MiningDrillEntity.SIZE, false)) {
                return ItemStack.EMPTY;
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
