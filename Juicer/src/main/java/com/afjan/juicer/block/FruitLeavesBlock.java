package com.afjan.juicer.block;

import com.afjan.juicer.fruit.Fruit;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ColorParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.ParticleUtils;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BonemealSource;
import net.minecraft.world.level.block.BonemealableBlock;
import net.minecraft.world.level.block.FallingParticlesLeavesBlock;
import net.minecraft.world.level.block.sounds.AmbientLeavesBlockSoundPlayer;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Leaves that slowly grow fruit: 0 = leaves, 1 = blossoms, 2 = unripe fruit, 3 = ripe fruit.
 * Right-click ripe leaves to pick the fruit; bone meal speeds up growth.
 */
public class FruitLeavesBlock extends FallingParticlesLeavesBlock implements BonemealableBlock {
    public static final int MAX_AGE = 3;
    public static final IntegerProperty AGE = BlockStateProperties.AGE_3;
    /** One in this many random ticks advances the fruit by one stage (roughly 10 minutes from bare to ripe). */
    private static final int GROWTH_CHANCE = 3;

    private final Fruit fruit;

    public FruitLeavesBlock(Fruit fruit, BlockBehaviour.Properties properties) {
        super(0.01F, AmbientLeavesBlockSoundPlayer.noAmbientSound(), properties);
        this.fruit = fruit;
        this.registerDefaultState(this.defaultBlockState().setValue(AGE, 0));
    }

    public Fruit getFruit() {
        return this.fruit;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(AGE);
    }

    @Override
    protected boolean isRandomlyTicking(BlockState state) {
        return super.isRandomlyTicking(state) || state.getValue(AGE) < MAX_AGE;
    }

    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        super.randomTick(state, level, pos, random); // leaf decay
        if (!level.getBlockState(pos).is(this)) {
            return;
        }
        int age = state.getValue(AGE);
        if (age < MAX_AGE && random.nextInt(GROWTH_CHANCE) == 0) {
            level.setBlock(pos, state.setValue(AGE, age + 1), Block.UPDATE_CLIENTS);
        }
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (state.getValue(AGE) != MAX_AGE) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide()) {
            int count = 1 + level.getRandom().nextInt(2);
            popResource(level, pos, new ItemStack(this.fruit.fruitItem(), count));
            level.playSound(null, pos, SoundEvents.SWEET_BERRY_BUSH_PICK_BERRIES, SoundSource.BLOCKS,
                    1.0F, 0.8F + level.getRandom().nextFloat() * 0.4F);
            BlockState picked = state.setValue(AGE, 0);
            level.setBlock(pos, picked, Block.UPDATE_CLIENTS);
            level.gameEvent(GameEvent.BLOCK_CHANGE, pos, GameEvent.Context.of(player, picked));
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    protected void spawnFallingLeavesParticle(Level level, BlockPos pos, RandomSource random) {
        ParticleUtils.spawnParticleBelow(level, pos, random,
                ColorParticleOption.create(ParticleTypes.TINTED_LEAVES, 0xFF000000 | this.fruit.leafColor()));
    }

    // --- bone meal ---------------------------------------------------------------------------------------------

    @Override
    public boolean isValidBonemealTarget(LevelReader level, BlockPos pos, BlockState state, BonemealSource source) {
        return state.getValue(AGE) < MAX_AGE;
    }

    @Override
    public boolean isBonemealSuccess(Level level, RandomSource random, BlockPos pos, BlockState state, BonemealSource source) {
        return true;
    }

    @Override
    public void performBonemeal(ServerLevel level, RandomSource random, BlockPos pos, BlockState state, BonemealSource source) {
        level.setBlock(pos, state.setValue(AGE, Math.min(MAX_AGE, state.getValue(AGE) + 1)), Block.UPDATE_CLIENTS);
    }
}
