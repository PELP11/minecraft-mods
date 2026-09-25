package com.afjan.stonesift.block;

import java.util.ArrayList;
import java.util.List;

import org.jetbrains.annotations.Nullable;

import com.afjan.stonesift.item.RockItem;
import com.afjan.stonesift.registry.ModBlockEntities;
import com.afjan.stonesift.rock.Rock;
import com.afjan.stonesift.rock.Yields;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * A sluice segment. The segment with a water source at its upper end is the controller: it scans the straight run
 * downstream (1-8 segments), takes rock flour from a hopper, sends each portion down the sluice (visible), rolls ore
 * fragments once per segment and hands fragments + 1 fine slurry to whatever sits past the last segment.
 */
public class SluiceBlockEntity extends BlockEntity implements WorldlyContainer {
    public static final int MAX_LENGTH = 8;
    public static final int TICKS_PER_SEGMENT = 10;
    public static final int FEED_INTERVAL = 10;
    public static final int MAX_PORTIONS = 16;
    private static final int[] SLOTS = {0};
    private static final int[] NONE = {};

    /** A portion of flour travelling down; age in ticks. */
    public record Portion(int rock, boolean rich, int age, List<ItemStack> found) {}

    private NonNullList<ItemStack> items = NonNullList.withSize(1, ItemStack.EMPTY);
    private final List<Portion> portions = new ArrayList<>();
    private int length;
    private boolean head;
    private int feedCooldown;

    public SluiceBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.SLUICE.get(), pos, state);
    }

    public Direction flow() {
        return this.getBlockState().getValue(SluiceBlock.FACING);
    }

    public boolean isHead() {
        return this.head;
    }

    public int length() {
        return this.length;
    }

    public List<Portion> portions() {
        return this.portions;
    }

    /** Head = a water source right above-stream. */
    public static boolean waterAt(Level level, BlockPos pos) {
        FluidState state = level.getFluidState(pos);
        return state.is(FluidTags.WATER) && state.isSource();
    }

    /** Re-checks whether this is the head and how long the run is. */
    public void scan(Level level) {
        Direction flow = this.flow();
        boolean wasHead = this.head;
        int oldLength = this.length;
        this.head = waterAt(level, this.worldPosition.relative(flow.getOpposite()));
        int n = 0;
        if (this.head) {
            BlockPos p = this.worldPosition;
            while (n < MAX_LENGTH) {
                BlockState state = level.getBlockState(p);
                if (!(state.getBlock() instanceof SluiceBlock) || state.getValue(SluiceBlock.FACING) != flow) {
                    break;
                }
                n++;
                p = p.relative(flow);
            }
        }
        this.length = n;
        if (wasHead != this.head || oldLength != this.length) {
            this.sync();
        }
    }

    public void serverTick(ServerLevel level) {
        if (level.getGameTime() % 20 == 0) {
            this.scan(level);
        }
        if (!this.head || this.length == 0) {
            return;
        }
        boolean changed = false;
        if (--this.feedCooldown <= 0 && this.portions.size() < MAX_PORTIONS) {
            ItemStack flour = this.items.get(0);
            RockItem rock = RockItem.of(flour, RockItem.Stage.FLOUR);
            if (rock != null) {
                this.portions.add(new Portion(rock.rock().ordinal(), RockItem.isRich(flour), 0, new ArrayList<>()));
                flour.shrink(1);
                this.feedCooldown = FEED_INTERVAL;
                changed = true;
            }
        }
        List<Portion> next = new ArrayList<>();
        for (Portion p : this.portions) {
            int age = p.age() + 1;
            if (age % TICKS_PER_SEGMENT == 0) {
                Rock rock = Rock.byOrdinal(p.rock());
                p.found().addAll(Yields.sluiceSegment(rock, p.rich(), level.getRandom()));
                if (age / TICKS_PER_SEGMENT >= this.length) {
                    List<ItemStack> out = new ArrayList<>(p.found());
                    out.add(RockItem.stack(RockItem.Stage.SLURRY, rock, p.rich(), 1));
                    this.deliver(level, out);
                    changed = true;
                    continue;
                }
            }
            next.add(new Portion(p.rock(), p.rich(), age, p.found()));
        }
        this.portions.clear();
        this.portions.addAll(next);
        if (changed) {
            this.setChanged();
            this.sync();
        }
    }

    /** Client: portions keep sliding between server updates. */
    public void clientTick() {
        List<Portion> next = new ArrayList<>();
        for (Portion p : this.portions) {
            if (p.age() + 1 < this.length * TICKS_PER_SEGMENT) {
                next.add(new Portion(p.rock(), p.rich(), p.age() + 1, p.found()));
            }
        }
        this.portions.clear();
        this.portions.addAll(next);
    }

    private void deliver(ServerLevel level, List<ItemStack> stacks) {
        Direction flow = this.flow();
        BlockPos end = this.worldPosition.relative(flow, this.length);
        Container target = level.getBlockEntity(end) instanceof Container c ? c : null;
        for (ItemStack stack : stacks) {
            ItemStack rest = target != null ? HopperBlockEntity.addItem(null, target, stack, flow.getOpposite()) : stack;
            if (!rest.isEmpty()) {
                ItemEntity item = new ItemEntity(level, end.getX() + 0.5, end.getY() + 0.3, end.getZ() + 0.5, rest);
                item.setDeltaMovement(flow.getStepX() * 0.1, 0.05, flow.getStepZ() * 0.1);
                level.addFreshEntity(item);
            }
        }
    }

    private void sync() {
        if (this.level != null && !this.level.isClientSide()) {
            BlockState state = this.getBlockState();
            this.level.sendBlockUpdated(this.worldPosition, state, state, Block.UPDATE_CLIENTS);
        }
    }

    // --- container: only the head takes rock flour ------------------------------------------------------------------

    @Override
    public int getContainerSize() {
        return 1;
    }

    @Override
    public boolean isEmpty() {
        return this.items.get(0).isEmpty();
    }

    @Override
    public ItemStack getItem(int slot) {
        return this.items.get(slot);
    }

    @Override
    public ItemStack removeItem(int slot, int amount) {
        return ContainerHelper.removeItem(this.items, slot, amount);
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        return ContainerHelper.takeItem(this.items, slot);
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        this.items.set(slot, stack);
        this.setChanged();
    }

    @Override
    public boolean stillValid(Player player) {
        return false;
    }

    @Override
    public void clearContent() {
        this.items.clear();
    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        return this.head && RockItem.of(stack, RockItem.Stage.FLOUR) != null;
    }

    @Override
    public int[] getSlotsForFace(Direction direction) {
        return this.head ? SLOTS : NONE;
    }

    @Override
    public boolean canPlaceItemThroughFace(int slot, ItemStack stack, @Nullable Direction direction) {
        return this.canPlaceItem(slot, stack);
    }

    @Override
    public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction direction) {
        return false;
    }

    public List<ItemStack> contents() {
        return this.items;
    }

    // --- saving / sync --------------------------------------------------------------------------------------------------

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        ContainerHelper.saveAllItems(output, this.items);
        output.putBoolean("head", this.head);
        output.putInt("length", this.length);
        int[] packed = new int[this.portions.size()];
        for (int i = 0; i < packed.length; i++) {
            Portion p = this.portions.get(i);
            packed[i] = p.age() << 8 | p.rock() << 1 | (p.rich() ? 1 : 0);
        }
        output.putIntArray("portions", packed);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        this.items = NonNullList.withSize(1, ItemStack.EMPTY);
        ContainerHelper.loadAllItems(input, this.items);
        this.head = input.getBooleanOr("head", false);
        this.length = input.getIntOr("length", 0);
        this.portions.clear();
        for (int v : input.getIntArray("portions").orElse(new int[0])) {
            this.portions.add(new Portion(v >> 1 & 0x7F, (v & 1) != 0, v >> 8, new ArrayList<>()));
        }
    }

    @Override
    public @Nullable Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return this.saveCustomOnly(registries);
    }

    public Component describe() {
        return this.head ? Component.translatable("message.stonesift.sluice_head", this.length)
                : Component.translatable("message.stonesift.sluice_segment");
    }
}
