package com.afjan.stonesift.machine;

import java.util.ArrayList;
import java.util.List;

import com.afjan.stonesift.item.RockItem;
import com.afjan.stonesift.item.SimpleItems;
import com.afjan.stonesift.rock.Yields;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/** Shaker sieve: every rising redstone edge sifts one gravel (5-tick cooldown = max 4 per second); the mesh wears. */
public class ShakerSieveBlockEntity extends MachineBlockEntity {
    public static final int COOLDOWN = 5;
    private boolean powered;
    private long readyAt;

    public ShakerSieveBlockEntity(BlockPos pos, BlockState state) {
        super(MachineType.SHAKER_SIEVE, pos, state);
    }

    @Override
    public void serverTick(ServerLevel level) {
        this.data[0] = (int) Math.max(0, this.readyAt - level.getGameTime());
    }

    /** Called from neighborChanged: acts on the rising edge only. */
    public void onSignal(ServerLevel level, boolean signal) {
        if (signal && !this.powered && level.getGameTime() >= this.readyAt) {
            this.readyAt = level.getGameTime() + COOLDOWN;
            this.sift(level);
        }
        this.powered = signal;
    }

    /** One sifting pass; returns whether anything was sifted. */
    public boolean sift(ServerLevel level) {
        ItemStack input = this.items.get(0);
        ItemStack mesh = this.items.get(1);
        RockItem gravel = RockItem.of(input, RockItem.Stage.GRAVEL);
        int tier = SimpleItems.Mesh.tierOf(mesh);
        if (gravel == null || tier == 0) {
            return false;
        }
        boolean rich = RockItem.isRich(input);
        List<ItemStack> out = new ArrayList<>(Yields.sieve(gravel.rock(), rich, tier, level.getRandom()));
        out.add(RockItem.stack(RockItem.Stage.FLOUR, gravel.rock(), rich, 1));
        if (!this.output(out)) {
            return false;
        }
        input.shrink(1);
        mesh.setDamageValue(mesh.getDamageValue() + 1);
        if (mesh.getDamageValue() >= mesh.getMaxDamage()) {
            this.items.set(1, ItemStack.EMPTY);
            level.playSound(null, this.worldPosition, SoundEvents.ITEM_BREAK.value(), SoundSource.BLOCKS, 0.8F, 1.0F);
        }
        level.playSound(null, this.worldPosition, SoundEvents.GRAVEL_HIT, SoundSource.BLOCKS, 0.7F, 1.2F);
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.GRAVEL.defaultBlockState()),
                this.worldPosition.getX() + 0.5, this.worldPosition.getY() + 1.0, this.worldPosition.getZ() + 0.5, 6, 0.3, 0.05, 0.3, 0.05);
        this.setChanged();
        return true;
    }

    @Override
    protected void saveExtra(ValueOutput output) {
        output.putBoolean("powered", this.powered);
    }

    @Override
    protected void loadExtra(ValueInput input) {
        this.powered = input.getBooleanOr("powered", false);
    }
}
