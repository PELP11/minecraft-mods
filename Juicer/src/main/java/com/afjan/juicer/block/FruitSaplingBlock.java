package com.afjan.juicer.block;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.SaplingBlock;
import net.minecraft.world.level.block.grower.TreeGrower;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;

/** A regular sapling; the dragonfruit sapling may additionally be planted on sand. */
public class FruitSaplingBlock extends SaplingBlock {
    private final boolean growsOnSand;

    public FruitSaplingBlock(TreeGrower treeGrower, boolean growsOnSand, BlockBehaviour.Properties properties) {
        super(treeGrower, properties);
        this.growsOnSand = growsOnSand;
    }

    @Override
    protected boolean mayPlaceOn(BlockState state, BlockGetter level, BlockPos pos) {
        return super.mayPlaceOn(state, level, pos) || (this.growsOnSand && state.is(BlockTags.SAND));
    }
}
