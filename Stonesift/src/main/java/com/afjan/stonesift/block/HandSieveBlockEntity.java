package com.afjan.stonesift.block;

import java.util.ArrayList;
import java.util.List;

import org.jetbrains.annotations.Nullable;

import com.afjan.stonesift.item.RockItem;
import com.afjan.stonesift.item.SimpleItems;
import com.afjan.stonesift.registry.ModBlockEntities;
import com.afjan.stonesift.rock.Rock;
import com.afjan.stonesift.rock.Yields;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * The hand sieve's pile (up to 8 gravel of one rock) and its mesh. Holding right-click sifts one gravel every
 * ~2 s; not a container on purpose, so it cannot be automated.
 */
public class HandSieveBlockEntity extends BlockEntity {
    public static final int CAPACITY = 8;
    public static final int WORK = 40;
    /** Right-click repeats every 4 ticks while held. */
    public static final int PER_CLICK = 4;

    private ItemStack mesh = ItemStack.EMPTY;
    private int rock = -1;
    private boolean rich;
    private int count;
    private int progress;

    public HandSieveBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.HAND_SIEVE.get(), pos, state);
    }

    public ItemStack mesh() {
        return this.mesh;
    }

    public int count() {
        return this.count;
    }

    public @Nullable Rock rock() {
        return this.count > 0 && this.rock >= 0 ? Rock.byOrdinal(this.rock) : null;
    }

    public boolean rich() {
        return this.rich;
    }

    /** How full the pile looks, 0..1: shrinks while the top portion is being sifted. */
    public float fill() {
        return Math.max(0.0F, (this.count - this.progress / (float) WORK) / CAPACITY);
    }

    /** Adds one gravel if it matches the pile; returns whether it was taken. */
    public boolean addGravel(ItemStack stack) {
        RockItem gravel = RockItem.of(stack, RockItem.Stage.GRAVEL);
        if (gravel == null || this.count >= CAPACITY) {
            return false;
        }
        boolean rich = RockItem.isRich(stack);
        if (this.count > 0 && (this.rock != gravel.rock().ordinal() || this.rich != rich)) {
            return false;
        }
        this.rock = gravel.rock().ordinal();
        this.rich = rich;
        this.count++;
        this.changed();
        return true;
    }

    /** Swaps the mesh; returns the one taken out. */
    public ItemStack swapMesh(ItemStack newMesh) {
        ItemStack old = this.mesh;
        this.mesh = newMesh;
        this.changed();
        return old;
    }

    /** One held-right-click step; returns the sifted items when a gravel is done, else null. */
    public @Nullable List<ItemStack> work(ServerLevel level) {
        int tier = SimpleItems.Mesh.tierOf(this.mesh);
        Rock rock = this.rock();
        if (rock == null || tier == 0) {
            return null;
        }
        this.progress += PER_CLICK;
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.GRAVEL.defaultBlockState()),
                this.worldPosition.getX() + 0.5, this.worldPosition.getY() + 0.75, this.worldPosition.getZ() + 0.5, 3, 0.2, 0.02, 0.2, 0.02);
        level.playSound(null, this.worldPosition, SoundEvents.GRAVEL_HIT, SoundSource.BLOCKS, 0.5F, 0.9F + level.getRandom().nextFloat() * 0.3F);
        List<ItemStack> out = null;
        if (this.progress >= WORK) {
            this.progress = 0;
            this.count--;
            out = new ArrayList<>(Yields.sieve(rock, this.rich, tier, level.getRandom()));
            out.add(RockItem.stack(RockItem.Stage.FLOUR, rock, this.rich, 1));
            if (this.count == 0) {
                this.rich = false;
            }
        }
        this.changed();
        return out;
    }

    private void changed() {
        this.setChanged();
        if (this.level != null && !this.level.isClientSide()) {
            BlockState state = this.getBlockState();
            int meshState = SimpleItems.Mesh.tierOf(this.mesh);
            if (state.getValue(HandSieveBlock.MESH) != meshState) {
                this.level.setBlock(this.worldPosition, state.setValue(HandSieveBlock.MESH, meshState), Block.UPDATE_CLIENTS);
            }
            this.level.sendBlockUpdated(this.worldPosition, state, state, Block.UPDATE_CLIENTS);
        }
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        if (!this.mesh.isEmpty()) {
            output.store("mesh", ItemStack.CODEC, this.mesh);
        }
        output.putInt("rock", this.rock);
        output.putBoolean("rich", this.rich);
        output.putInt("count", this.count);
        output.putInt("progress", this.progress);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        this.mesh = input.read("mesh", ItemStack.CODEC).orElse(ItemStack.EMPTY);
        this.rock = input.getIntOr("rock", -1);
        this.rich = input.getBooleanOr("rich", false);
        this.count = input.getIntOr("count", 0);
        this.progress = input.getIntOr("progress", 0);
    }

    @Override
    public @Nullable Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return this.saveCustomOnly(registries);
    }
}
