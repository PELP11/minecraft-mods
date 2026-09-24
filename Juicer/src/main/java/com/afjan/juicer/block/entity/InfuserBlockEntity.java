package com.afjan.juicer.block.entity;

import org.jspecify.annotations.Nullable;

import com.afjan.juicer.block.InfuserBlock;
import com.afjan.juicer.fruit.Fruit;
import com.afjan.juicer.menu.InfuserMenu;
import com.afjan.juicer.registry.ModBlockEntities;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/** Mixes 1000 mB of concentrate with sugar and bottles it into a powerful juice. */
public class InfuserBlockEntity extends BaseContainerBlockEntity implements WorldlyContainer {
    public static final int SLOT_SUGAR = 0;
    public static final int SLOT_BOTTLE = 1;
    public static final int SLOT_OUTPUT = 2;
    public static final int CAPACITY = 8000;
    public static final int CONCENTRATE_PER_JUICE = 1000;
    public static final int INFUSE_TIME = 100;
    private static final int[] SLOTS_FOR_UP = new int[] { SLOT_SUGAR };
    private static final int[] SLOTS_FOR_SIDES = new int[] { SLOT_BOTTLE, SLOT_SUGAR };
    private static final int[] SLOTS_FOR_DOWN = new int[] { SLOT_OUTPUT };
    private static final Component DEFAULT_NAME = Component.translatable("container.juicer.infuser");

    private NonNullList<ItemStack> items = NonNullList.withSize(3, ItemStack.EMPTY);
    private final ConcentrateTank tank = new ConcentrateTank(CAPACITY);
    private int progress;

    private final ContainerData dataAccess = new ContainerData() {
        @Override
        public int get(int index) {
            return switch (index) {
                case MixerBlockEntity.DATA_PROGRESS -> InfuserBlockEntity.this.progress;
                case MixerBlockEntity.DATA_MAX_PROGRESS -> INFUSE_TIME;
                case MixerBlockEntity.DATA_CONTENTS -> InfuserBlockEntity.this.tank.contents().ordinal();
                case MixerBlockEntity.DATA_AMOUNT -> InfuserBlockEntity.this.tank.amount();
                case MixerBlockEntity.DATA_CAPACITY -> CAPACITY;
                default -> 0;
            };
        }

        @Override
        public void set(int index, int value) {
            if (index == MixerBlockEntity.DATA_PROGRESS) {
                InfuserBlockEntity.this.progress = value;
            }
        }

        @Override
        public int getCount() {
            return MixerBlockEntity.DATA_COUNT;
        }
    };

    public InfuserBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.INFUSER.get(), pos, state);
    }

    public static void serverTick(ServerLevel level, BlockPos pos, BlockState state, InfuserBlockEntity infuser) {
        boolean changed = false;
        boolean active = false;

        if (infuser.canInfuse()) {
            active = true;
            infuser.progress++;
            if (infuser.progress >= INFUSE_TIME) {
                infuser.progress = 0;
                infuser.infuse();
                level.playSound(null, pos, SoundEvents.BREWING_STAND_BREW, SoundSource.BLOCKS, 0.8F, 1.2F);
                changed = true;
            }
        } else if (infuser.progress != 0) {
            infuser.progress = 0;
            changed = true;
        }

        BlockState newState = state
                .setValue(InfuserBlock.ACTIVE, active)
                .setValue(InfuserBlock.CONTENTS, infuser.tank.contents())
                .setValue(InfuserBlock.LEVEL, infuser.tank.level(4));
        if (newState != state) {
            level.setBlock(pos, newState, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
        }
        if (changed) {
            setChanged(level, pos, newState);
        }
    }

    private boolean canInfuse() {
        Fruit fruit = this.tank.fruit();
        if (fruit == null || this.tank.amount() < CONCENTRATE_PER_JUICE) {
            return false;
        }
        if (!this.items.get(SLOT_SUGAR).is(Items.SUGAR) || !this.items.get(SLOT_BOTTLE).is(Items.GLASS_BOTTLE)) {
            return false;
        }
        ItemStack output = this.items.get(SLOT_OUTPUT);
        return output.isEmpty() || (output.is(fruit.juiceItem()) && output.getCount() < output.getMaxStackSize());
    }

    private void infuse() {
        Fruit fruit = this.tank.fruit();
        if (fruit == null) {
            return;
        }
        this.tank.drain(CONCENTRATE_PER_JUICE);
        this.items.get(SLOT_SUGAR).shrink(1);
        this.items.get(SLOT_BOTTLE).shrink(1);
        ItemStack output = this.items.get(SLOT_OUTPUT);
        if (output.isEmpty()) {
            this.items.set(SLOT_OUTPUT, new ItemStack(fruit.juiceItem()));
        } else {
            output.grow(1);
        }
    }

    /** Accepts concentrate from the tubing; a tank only ever holds one fruit at a time. */
    public int receiveConcentrate(Fruit fruit, int amount) {
        int accepted = this.tank.fill(fruit, amount, false);
        if (accepted > 0) {
            this.setChanged();
        }
        return accepted;
    }

    public ConcentrateTank getTank() {
        return this.tank;
    }

    public void dumpTank() {
        this.tank.clear();
        this.setChanged();
    }

    /** Puts sugar or glass bottles held by a player into the right slot; returns how many items were moved. */
    public int insertIngredient(ItemStack stack) {
        int slotIndex;
        if (stack.is(Items.SUGAR)) {
            slotIndex = SLOT_SUGAR;
        } else if (stack.is(Items.GLASS_BOTTLE)) {
            slotIndex = SLOT_BOTTLE;
        } else {
            return 0;
        }
        ItemStack slot = this.items.get(slotIndex);
        int moved;
        if (slot.isEmpty()) {
            moved = Math.min(stack.getCount(), stack.getMaxStackSize());
            this.items.set(slotIndex, stack.copyWithCount(moved));
        } else if (ItemStack.isSameItemSameComponents(slot, stack)) {
            moved = Math.min(stack.getCount(), slot.getMaxStackSize() - slot.getCount());
            slot.grow(moved);
        } else {
            return 0;
        }
        if (moved > 0) {
            this.setChanged();
        }
        return moved;
    }

    public int getComparatorSignal() {
        return this.tank.isEmpty() ? 0 : 1 + (int) (14.0 * this.tank.amount() / this.tank.capacity());
    }

    // --- container ---------------------------------------------------------------------------------------------

    @Override
    protected Component getDefaultName() {
        return DEFAULT_NAME;
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
        return switch (slot) {
            case SLOT_SUGAR -> stack.is(Items.SUGAR);
            case SLOT_BOTTLE -> stack.is(Items.GLASS_BOTTLE);
            default -> false;
        };
    }

    @Override
    public int[] getSlotsForFace(Direction direction) {
        return switch (direction) {
            case UP -> SLOTS_FOR_UP;
            case DOWN -> SLOTS_FOR_DOWN;
            default -> SLOTS_FOR_SIDES;
        };
    }

    @Override
    public boolean canPlaceItemThroughFace(int slot, ItemStack stack, @Nullable Direction direction) {
        return this.canPlaceItem(slot, stack);
    }

    @Override
    public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction direction) {
        return slot == SLOT_OUTPUT;
    }

    @Override
    protected AbstractContainerMenu createMenu(int containerId, Inventory inventory) {
        return new InfuserMenu(containerId, inventory, this, this.dataAccess);
    }

    // --- saving ------------------------------------------------------------------------------------------------

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        this.items = NonNullList.withSize(this.getContainerSize(), ItemStack.EMPTY);
        ContainerHelper.loadAllItems(input, this.items);
        this.progress = input.getIntOr("progress", 0);
        this.tank.load(input, "tank");
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        ContainerHelper.saveAllItems(output, this.items);
        output.putInt("progress", this.progress);
        this.tank.save(output, "tank");
    }
}
