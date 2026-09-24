package com.afjan.arsenal.block.entity;

import com.afjan.arsenal.item.Guns;
import com.afjan.arsenal.menu.WeaponWorkbenchMenu;
import com.afjan.arsenal.registry.ModBlockEntities;

import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * Holds the single weapon currently clamped in the bench. The four attachment slots belong to the menu rather than
 * the block: a fitted part lives on the gun as a component, so mirroring it into a persistent container as well
 * would be a second copy of the same item.
 */
public class WeaponWorkbenchBlockEntity extends BaseContainerBlockEntity {
    public static final int SIZE = 1;
    private static final Component DEFAULT_NAME = Component.translatable("container.arsenal.weapon_workbench");

    private NonNullList<ItemStack> items = NonNullList.withSize(SIZE, ItemStack.EMPTY);

    public WeaponWorkbenchBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.WEAPON_WORKBENCH.get(), pos, state);
    }

    @Override
    protected Component getDefaultName() {
        return DEFAULT_NAME;
    }

    @Override
    public int getContainerSize() {
        return SIZE;
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
        return Guns.isGun(stack);
    }

    @Override
    protected AbstractContainerMenu createMenu(int containerId, Inventory inventory) {
        return new WeaponWorkbenchMenu(containerId, inventory, this);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        this.items = NonNullList.withSize(SIZE, ItemStack.EMPTY);
        ContainerHelper.loadAllItems(input, this.items);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        ContainerHelper.saveAllItems(output, this.items);
    }
}
