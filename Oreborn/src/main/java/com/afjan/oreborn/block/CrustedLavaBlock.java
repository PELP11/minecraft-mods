package com.afjan.oreborn.block;

import org.jspecify.annotations.Nullable;

import com.afjan.oreborn.ability.ArmorSets;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.IntegerProperty;

/**
 * Lava crust formed under Emberite boots (the lava counterpart of frosted ice). It holds while someone wearing the
 * boots stands nearby, then cracks up over a few seconds and melts back into a lava source.
 */
public class CrustedLavaBlock extends Block {
    public static final IntegerProperty AGE = BlockStateProperties.AGE_3;
    public static final int MAX_AGE = 3;
    private static final int STEP_TICKS = 20;

    public CrustedLavaBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(AGE, 0));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(AGE);
    }

    /** Called when a lava walker forms or refreshes this crust. */
    public static void scheduleMelt(ServerLevel level, BlockPos pos, Block block, RandomSource random) {
        level.scheduleTick(pos, block, STEP_TICKS + random.nextInt(STEP_TICKS));
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (ArmorSets.isLavaWalkerNear(level, pos)) {
            if (state.getValue(AGE) != 0) {
                level.setBlock(pos, state.setValue(AGE, 0), Block.UPDATE_CLIENTS);
            }
            scheduleMelt(level, pos, this, random);
            return;
        }
        int age = state.getValue(AGE);
        if (age < MAX_AGE) {
            level.setBlock(pos, state.setValue(AGE, age + 1), Block.UPDATE_CLIENTS);
            scheduleMelt(level, pos, this, random);
        } else {
            level.setBlockAndUpdate(pos, Blocks.LAVA.defaultBlockState());
        }
    }

    /** Breaking the crust just exposes the lava again. */
    @Override
    public void playerDestroy(ServerLevel level, ServerPlayer player, BlockPos pos, BlockState state, @Nullable BlockEntity blockEntity, ItemStack destroyedWith) {
        super.playerDestroy(level, player, pos, state, blockEntity, destroyedWith);
        level.setBlockAndUpdate(pos, Blocks.LAVA.defaultBlockState());
    }
}
