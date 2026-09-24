package com.afjan.juicer.block.entity;

import org.jspecify.annotations.Nullable;

import com.afjan.juicer.block.MixerBlock;
import com.afjan.juicer.fruit.Fruit;
import com.afjan.juicer.menu.MixerMenu;
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
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/** Blends fruit into concentrate and pushes it through connected Juice Tubing. */
public class MixerBlockEntity extends BaseContainerBlockEntity implements WorldlyContainer {
    public static final int CAPACITY = 4000;
    public static final int CONCENTRATE_PER_FRUIT = 250;
    public static final int BLEND_TIME = 60;
    /** Concentrate pushed into the tubing every {@link #PUSH_INTERVAL} ticks. */
    public static final int PUSH_AMOUNT = 250;
    public static final int PUSH_INTERVAL = 5;
    public static final int DATA_PROGRESS = 0;
    public static final int DATA_MAX_PROGRESS = 1;
    public static final int DATA_CONTENTS = 2;
    public static final int DATA_AMOUNT = 3;
    public static final int DATA_CAPACITY = 4;
    public static final int DATA_COUNT = 5;
    private static final int[] SLOTS = new int[] { 0 };
    private static final Component DEFAULT_NAME = Component.translatable("container.juicer.mixer");

    private NonNullList<ItemStack> items = NonNullList.withSize(1, ItemStack.EMPTY);
    private final ConcentrateTank tank = new ConcentrateTank(CAPACITY);
    private int progress;

    private final ContainerData dataAccess = new ContainerData() {
        @Override
        public int get(int index) {
            return switch (index) {
                case DATA_PROGRESS -> MixerBlockEntity.this.progress;
                case DATA_MAX_PROGRESS -> BLEND_TIME;
                case DATA_CONTENTS -> MixerBlockEntity.this.tank.contents().ordinal();
                case DATA_AMOUNT -> MixerBlockEntity.this.tank.amount();
                case DATA_CAPACITY -> CAPACITY;
                default -> 0;
            };
        }

        @Override
        public void set(int index, int value) {
            if (index == DATA_PROGRESS) {
                MixerBlockEntity.this.progress = value;
            }
        }

        @Override
        public int getCount() {
            return DATA_COUNT;
        }
    };

    public MixerBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.MIXER.get(), pos, state);
    }

    public static void serverTick(ServerLevel level, BlockPos pos, BlockState state, MixerBlockEntity mixer) {
        boolean changed = false;
        boolean active = false;

        ItemStack input = mixer.items.get(0);
        Fruit fruit = Fruit.fromStack(input);
        if (fruit != null && mixer.tank.canAccept(fruit) && mixer.tank.space() >= CONCENTRATE_PER_FRUIT) {
            active = true;
            mixer.progress++;
            if (mixer.progress % 20 == 1) {
                level.playSound(null, pos, SoundEvents.GRINDSTONE_USE, SoundSource.BLOCKS, 0.25F, 1.7F);
            }
            if (mixer.progress >= BLEND_TIME) {
                mixer.progress = 0;
                input.shrink(1);
                mixer.tank.fill(fruit, CONCENTRATE_PER_FRUIT, false);
                level.playSound(null, pos, SoundEvents.HONEY_BLOCK_SLIDE, SoundSource.BLOCKS, 0.7F, 1.3F);
                changed = true;
            }
        } else if (mixer.progress != 0) {
            mixer.progress = 0;
            changed = true;
        }

        if (!mixer.tank.isEmpty() && level.getGameTime() % PUSH_INTERVAL == 0) {
            if (TubeNetwork.pushFrom(level, pos, mixer.tank, PUSH_AMOUNT) > 0) {
                changed = true;
            }
        }

        BlockState newState = state
                .setValue(MixerBlock.ACTIVE, active)
                .setValue(MixerBlock.CONTENTS, mixer.tank.contents())
                .setValue(MixerBlock.LEVEL, mixer.tank.level(4));
        if (newState != state) {
            level.setBlock(pos, newState, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
        }
        if (changed) {
            setChanged(level, pos, newState);
        }
    }

    public ConcentrateTank getTank() {
        return this.tank;
    }

    /** Called from the GUI's "empty tank" button. */
    public void dumpTank() {
        this.tank.clear();
        this.setChanged();
    }

    /** Moves as much of {@code stack} as fits into the fruit slot and returns how many items were moved. */
    public int insertFruit(ItemStack stack) {
        if (Fruit.fromStack(stack) == null) {
            return 0;
        }
        ItemStack slot = this.items.get(0);
        int moved;
        if (slot.isEmpty()) {
            moved = Math.min(stack.getCount(), stack.getMaxStackSize());
            this.items.set(0, stack.copyWithCount(moved));
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
        return Fruit.fromStack(stack) != null;
    }

    @Override
    public int[] getSlotsForFace(Direction direction) {
        return SLOTS;
    }

    @Override
    public boolean canPlaceItemThroughFace(int slot, ItemStack stack, @Nullable Direction direction) {
        return this.canPlaceItem(slot, stack);
    }

    @Override
    public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction direction) {
        return false;
    }

    @Override
    protected AbstractContainerMenu createMenu(int containerId, Inventory inventory) {
        return new MixerMenu(containerId, inventory, this, this.dataAccess);
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
