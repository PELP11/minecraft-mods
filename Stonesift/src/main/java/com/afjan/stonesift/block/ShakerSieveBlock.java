package com.afjan.stonesift.block;

import org.jetbrains.annotations.Nullable;

import com.afjan.stonesift.machine.MachineBlock;
import com.afjan.stonesift.machine.MachineType;
import com.afjan.stonesift.machine.ShakerSieveBlockEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.redstone.Orientation;

/** Shaker sieve: one sifting pass per rising redstone edge (clock or observer loop). */
public class ShakerSieveBlock extends MachineBlock {
    public ShakerSieveBlock(BlockBehaviour.Properties properties) {
        super(MachineType.SHAKER_SIEVE, ShakerSieveBlockEntity::new, Block.box(0, 0, 0, 16, 14, 16), properties);
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, @Nullable Orientation orientation,
            boolean movedByPiston) {
        super.neighborChanged(state, level, pos, block, orientation, movedByPiston);
        if (level instanceof ServerLevel server && level.getBlockEntity(pos) instanceof ShakerSieveBlockEntity sieve) {
            sieve.onSignal(server, level.hasNeighborSignal(pos));
        }
    }
}
