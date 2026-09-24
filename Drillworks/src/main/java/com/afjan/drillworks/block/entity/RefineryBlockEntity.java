package com.afjan.drillworks.block.entity;

import com.afjan.drillworks.block.RefineryBlock;
import com.afjan.drillworks.menu.RefineryMenu;
import com.afjan.drillworks.registry.ModBlockEntities;
import com.afjan.drillworks.registry.ModBlocks;
import com.afjan.drillworks.registry.ModItems;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
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
import net.minecraft.world.item.component.CookingFuel;
import net.minecraft.world.level.storage.loot.providers.number.ints.ResolvableInt;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * Crude oil + furnace fuel -> gasoline (250 mB per crude oil, 5 s each) into an 8000 mB tank, then 1000 mB into each
 * empty canister. Only runs with two Distillation Columns stacked on top. Hoppers: crude oil and fuel from the top
 * and sides, canisters in from the sides, full canisters out from below.
 */
public class RefineryBlockEntity extends BaseContainerBlockEntity implements WorldlyContainer {
    public static final int SLOT_CRUDE = 0;
    public static final int SLOT_FUEL = 1;
    public static final int SLOT_CAN_IN = 2;
    public static final int SLOT_CAN_OUT = 3;
    public static final int CAPACITY = 8000;
    public static final int PER_CRUDE = 250;
    public static final int REFINE_TIME = 100;
    public static final int FILL_TIME = 20;
    public static final int CANISTER = 1000;

    public static final int DATA_BURN = 0;
    public static final int DATA_BURN_MAX = 1;
    public static final int DATA_PROGRESS = 2;
    public static final int DATA_GASOLINE = 3;
    public static final int DATA_FORMED = 4;
    public static final int DATA_FILL = 5;
    public static final int DATA_COUNT = 6;

    private static final int[] TOP_SLOTS = {SLOT_CRUDE, SLOT_FUEL};
    private static final int[] SIDE_SLOTS = {SLOT_CRUDE, SLOT_FUEL, SLOT_CAN_IN};
    private static final int[] BOTTOM_SLOTS = {SLOT_CAN_OUT};
    private static final Component DEFAULT_NAME = Component.translatable("container.drillworks.refinery");

    private NonNullList<ItemStack> items = NonNullList.withSize(4, ItemStack.EMPTY);
    private int burn;
    private int burnMax;
    private int progress;
    private int gasoline;
    private int fill;
    private boolean formed;

    private final ContainerData dataAccess = new ContainerData() {
        @Override
        public int get(int index) {
            RefineryBlockEntity r = RefineryBlockEntity.this;
            return switch (index) {
                case DATA_BURN -> r.burn;
                case DATA_BURN_MAX -> r.burnMax;
                case DATA_PROGRESS -> r.progress;
                case DATA_GASOLINE -> r.gasoline;
                case DATA_FORMED -> r.formed ? 1 : 0;
                case DATA_FILL -> r.fill;
                default -> 0;
            };
        }

        @Override
        public void set(int index, int value) {}

        @Override
        public int getCount() {
            return DATA_COUNT;
        }
    };

    public RefineryBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.REFINERY.get(), pos, state);
    }

    /** Two Distillation Columns stacked on the still. */
    public static boolean isFormed(Level level, BlockPos pos) {
        return level.getBlockState(pos.above()).is(ModBlocks.DISTILLATION_COLUMN.get())
                && level.getBlockState(pos.above(2)).is(ModBlocks.DISTILLATION_COLUMN.get());
    }

    public static void serverTick(ServerLevel level, BlockPos pos, BlockState state, RefineryBlockEntity r) {
        boolean changed = false;
        if (level.getGameTime() % 10 == 0 || r.formed != isFormed(level, pos) && level.getGameTime() % 2 == 0) {
            r.formed = isFormed(level, pos);
        }
        ItemStack crude = r.items.get(SLOT_CRUDE);
        boolean canRefine = r.formed && crude.is(ModItems.CRUDE_OIL.get()) && r.gasoline + PER_CRUDE <= CAPACITY;
        if (r.burn <= 0 && canRefine) {
            ItemStack fuel = r.items.get(SLOT_FUEL);
            int duration = fuel.isEmpty() ? 0 : ResolvableInt.getFromItem(fuel, DataComponents.COOKING_FUEL, CookingFuel::burnTime,
                    r.getLootContext(level, fuel), 0);
            if (duration > 0) {
                r.burn = r.burnMax = duration;
                if (fuel.is(Items.LAVA_BUCKET)) {
                    r.items.set(SLOT_FUEL, new ItemStack(Items.BUCKET));
                } else {
                    fuel.shrink(1);
                }
                changed = true;
            }
        }
        boolean lit = r.burn > 0;
        if (lit) {
            r.burn--;
            if (canRefine) {
                r.progress++;
                if (r.progress >= REFINE_TIME) {
                    r.progress = 0;
                    crude.shrink(1);
                    r.gasoline += PER_CRUDE;
                    level.playSound(null, pos, SoundEvents.BREWING_STAND_BREW, SoundSource.BLOCKS, 0.6F, 0.7F);
                }
                changed = true;
            }
        }
        if (!canRefine && r.progress > 0) {
            r.progress = Math.max(0, r.progress - 2);
        }
        // fill canisters
        ItemStack in = r.items.get(SLOT_CAN_IN);
        ItemStack out = r.items.get(SLOT_CAN_OUT);
        boolean canFill = r.gasoline >= CANISTER && in.is(ModItems.EMPTY_CANISTER.get())
                && (out.isEmpty() || out.is(ModItems.GASOLINE_CANISTER.get()) && out.getCount() < out.getMaxStackSize());
        if (canFill) {
            r.fill++;
            if (r.fill >= FILL_TIME) {
                r.fill = 0;
                in.shrink(1);
                r.gasoline -= CANISTER;
                if (out.isEmpty()) {
                    r.items.set(SLOT_CAN_OUT, new ItemStack(ModItems.GASOLINE_CANISTER.get()));
                } else {
                    out.grow(1);
                }
                level.playSound(null, pos, SoundEvents.BUCKET_FILL, SoundSource.BLOCKS, 0.6F, 0.9F);
                changed = true;
            }
        } else {
            r.fill = 0;
        }
        if (state.getValue(RefineryBlock.LIT) != r.burn > 0) {
            state = state.setValue(RefineryBlock.LIT, r.burn > 0);
            level.setBlock(pos, state, Block.UPDATE_ALL);
        }
        if (changed) {
            setChanged(level, pos, state);
        }
    }

    public int gasoline() {
        return this.gasoline;
    }

    public boolean formed() {
        return this.formed;
    }

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
            case SLOT_CRUDE -> stack.is(ModItems.CRUDE_OIL.get());
            case SLOT_FUEL -> stack.has(DataComponents.COOKING_FUEL) && !stack.is(ModItems.CRUDE_OIL.get());
            case SLOT_CAN_IN -> stack.is(ModItems.EMPTY_CANISTER.get());
            default -> false;
        };
    }

    @Override
    public int[] getSlotsForFace(Direction direction) {
        return direction == Direction.DOWN ? BOTTOM_SLOTS : direction == Direction.UP ? TOP_SLOTS : SIDE_SLOTS;
    }

    @Override
    public boolean canPlaceItemThroughFace(int slot, ItemStack stack, Direction direction) {
        return this.canPlaceItem(slot, stack);
    }

    @Override
    public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction direction) {
        return slot == SLOT_CAN_OUT;
    }

    @Override
    protected AbstractContainerMenu createMenu(int containerId, Inventory inventory) {
        return new RefineryMenu(containerId, inventory, this, this.dataAccess);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        this.items = NonNullList.withSize(this.getContainerSize(), ItemStack.EMPTY);
        ContainerHelper.loadAllItems(input, this.items);
        this.burn = input.getIntOr("burn", 0);
        this.burnMax = input.getIntOr("burn_max", 0);
        this.progress = input.getIntOr("progress", 0);
        this.gasoline = input.getIntOr("gasoline", 0);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        ContainerHelper.saveAllItems(output, this.items);
        output.putInt("burn", this.burn);
        output.putInt("burn_max", this.burnMax);
        output.putInt("progress", this.progress);
        output.putInt("gasoline", this.gasoline);
    }
}
