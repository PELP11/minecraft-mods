package com.afjan.arsenal.menu;

import com.afjan.arsenal.craft.Blueprints;
import com.afjan.arsenal.gun.Attachment;
import com.afjan.arsenal.gun.GunData;
import com.afjan.arsenal.gun.GunType;
import com.afjan.arsenal.item.AttachmentItem;
import com.afjan.arsenal.item.Guns;
import com.afjan.arsenal.registry.ModItems;
import com.afjan.arsenal.registry.ModMenus;

import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * The Weapon Workbench. Two jobs in one window: a browser that builds anything in {@link Blueprints} straight out of
 * the player's inventory, and a clamp with four attachment slots for fitting and stripping parts.
 *
 * <p>Attachments are the gun's own component data, so the four slots only ever mirror what the weapon in the clamp
 * is carrying: pull the gun out and the slots empty, because the parts left with it.
 */
public class WeaponWorkbenchMenu extends AbstractContainerMenu {
    /** Button ids below this build one item; add {@link #BULK} for a batch of eight. */
    public static final int BULK = 4096;
    public static final int BULK_COUNT = 8;

    public static final int GUN_SLOT = 0;
    public static final int FIRST_ATTACHMENT_SLOT = 1;
    public static final int ATTACHMENT_SLOTS = 4;
    private static final int MENU_SLOTS = 1 + ATTACHMENT_SLOTS;
    private static final int INV_START = MENU_SLOTS;
    private static final int HOTBAR_START = INV_START + 27;
    private static final int INV_END = HOTBAR_START + 9;

    public static final int GUN_X = 16;
    public static final int GUN_Y = 140;
    public static final int ATTACH_X = 52;
    public static final int ATTACH_Y = 140;

    private final Container clamp;
    private final Container parts = new SimpleContainer(ATTACHMENT_SLOTS);
    private final Player player;
    private boolean syncing;
    private ItemStack lastGun = ItemStack.EMPTY;

    public WeaponWorkbenchMenu(int containerId, Inventory inventory) {
        this(containerId, inventory, new SimpleContainer(WeaponWorkbenchBlockEntitySize.SIZE));
    }

    public WeaponWorkbenchMenu(int containerId, Inventory inventory, Container clamp) {
        super(ModMenus.WEAPON_WORKBENCH.get(), containerId);
        checkContainerSize(clamp, WeaponWorkbenchBlockEntitySize.SIZE);
        this.clamp = clamp;
        this.player = inventory.player;

        this.addSlot(new Slot(clamp, 0, GUN_X, GUN_Y) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return Guns.isGun(stack);
            }
        });
        Attachment.Slot[] kinds = Attachment.Slot.values();
        for (int i = 0; i < ATTACHMENT_SLOTS; i++) {
            Attachment.Slot kind = kinds[i];
            this.addSlot(new Slot(this.parts, i, ATTACH_X + i * 22, ATTACH_Y) {
                @Override
                public boolean mayPlace(ItemStack stack) {
                    return Guns.isGun(clamp.getItem(0))
                            && stack.getItem() instanceof AttachmentItem part && part.attachment().slot() == kind;
                }
            });
        }
        this.addStandardInventorySlots(inventory, 47, 168);
        this.refreshFromGun();
    }

    @Override
    public boolean stillValid(Player player) {
        return this.clamp.stillValid(player);
    }

    @Override
    public void slotsChanged(Container container) {
        super.slotsChanged(container);
        this.sync();
    }

    /**
     * A block entity container does not notify its menu the way a crafting grid does, so the clamp is also
     * reconciled once per tick from here, which is where the server pushes slot changes out anyway.
     */
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
        ItemStack gun = this.clamp.getItem(0);
        if (!Guns.isGun(gun)) {
            this.lastGun = ItemStack.EMPTY;
            for (int i = 0; i < ATTACHMENT_SLOTS; i++) {
                this.parts.setItem(i, ItemStack.EMPTY);
            }
        } else if (gun != this.lastGun) {
            this.lastGun = gun;
            this.refreshFromGun();
        } else {
            this.writeToGun(gun);
        }
        this.syncing = false;
    }

    /** Lays the weapon's fitted parts out in the four slots. */
    private void refreshFromGun() {
        ItemStack gun = this.clamp.getItem(0);
        if (!Guns.isGun(gun)) {
            return;
        }
        this.lastGun = gun;
        GunData data = Guns.dataOf(gun);
        Attachment.Slot[] kinds = Attachment.Slot.values();
        for (int i = 0; i < ATTACHMENT_SLOTS; i++) {
            Attachment fitted = data.inSlot(kinds[i]);
            this.parts.setItem(i, fitted == null ? ItemStack.EMPTY
                    : new ItemStack(ModItems.ATTACHMENTS.get(fitted).get()));
        }
    }

    /** Writes whatever is in the four slots onto the weapon, trimming the magazine if it just got smaller. */
    private void writeToGun(ItemStack gun) {
        GunData data = Guns.dataOf(gun);
        int mask = 0;
        for (int i = 0; i < ATTACHMENT_SLOTS; i++) {
            if (this.parts.getItem(i).getItem() instanceof AttachmentItem part) {
                mask |= part.attachment().bit();
            }
        }
        GunData updated = new GunData(data.ammo(), data.caliberId(), mask);
        GunType type = Guns.typeOf(gun);
        if (type != null) {
            updated = updated.withAmmo(Math.min(updated.ammo(), type.effectiveMagazine(updated)));
        }
        Guns.set(gun, updated);
    }

    @Override
    public boolean clickMenuButton(Player player, int buttonId) {
        boolean bulk = buttonId >= BULK;
        Blueprints.Blueprint blueprint = Blueprints.byIndex(bulk ? buttonId - BULK : buttonId);
        if (blueprint == null) {
            return false;
        }
        return Blueprints.build(player, blueprint, bulk ? BULK_COUNT : 1) > 0;
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        // The parts live on the weapon; the mirror slots are a view and must not drop a second copy.
        for (int i = 0; i < ATTACHMENT_SLOTS; i++) {
            this.parts.setItem(i, ItemStack.EMPTY);
        }
    }

    @Override
    public ItemStack quickMoveStack(Player player, int slotIndex) {
        Slot slot = this.slots.get(slotIndex);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        if (slotIndex < MENU_SLOTS) {
            if (!this.moveItemStackTo(stack, INV_START, INV_END, true)) {
                return ItemStack.EMPTY;
            }
        } else if (Guns.isGun(stack)) {
            if (!this.moveItemStackTo(stack, GUN_SLOT, GUN_SLOT + 1, false)) {
                return ItemStack.EMPTY;
            }
        } else if (stack.getItem() instanceof AttachmentItem) {
            if (!this.moveItemStackTo(stack, FIRST_ATTACHMENT_SLOT, FIRST_ATTACHMENT_SLOT + ATTACHMENT_SLOTS, false)) {
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

    public Player player() {
        return this.player;
    }

    /** Keeps the client constructor from depending on the block entity class. */
    private static final class WeaponWorkbenchBlockEntitySize {
        private static final int SIZE = 1;

        private WeaponWorkbenchBlockEntitySize() {}
    }
}
