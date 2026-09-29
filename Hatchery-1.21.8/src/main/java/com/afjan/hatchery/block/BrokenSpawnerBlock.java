package com.afjan.hatchery.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.SpawnerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.BlockHitResult;
import javax.annotation.Nullable;

/**
 * What a mined spawner leaves behind: an empty, dead cage. Use a spawn egg on it and it becomes a working vanilla
 * spawner of that mob again (the egg is used up).
 */
public final class BrokenSpawnerBlock extends Block {
    public BrokenSpawnerBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                          InteractionHand hand, BlockHitResult hit) {
        EntityType<?> type = stack.getItem() instanceof SpawnEggItem egg ? egg.getType(level.registryAccess(), stack) : null;
        if (type == null) return InteractionResult.TRY_WITH_EMPTY_HAND;
        if (level instanceof ServerLevel server) {
            revive(server, pos, type, player);
            player.awardStat(Stats.ITEM_USED.get(stack.getItem()));
            stack.consume(1, player);
        }
        return InteractionResult.SUCCESS;
    }

    /** Turns the broken cage at {@code pos} into a vanilla spawner of {@code type}. */
    public static void revive(ServerLevel level, BlockPos pos, EntityType<?> type, @Nullable Player player) {
        BlockState spawner = Blocks.SPAWNER.defaultBlockState();
        level.setBlock(pos, spawner, Block.UPDATE_ALL);
        if (level.getBlockEntity(pos) instanceof SpawnerBlockEntity entity) {
            entity.setEntityId(type, level.getRandom());
            level.sendBlockUpdated(pos, spawner, spawner, Block.UPDATE_ALL);
        }
        level.gameEvent(player, GameEvent.BLOCK_CHANGE, pos);
        level.playSound(null, pos, SoundEvents.RESPAWN_ANCHOR_CHARGE, SoundSource.BLOCKS, 1.0F, 0.8F);
        level.playSound(null, pos, SoundEvents.TRIAL_SPAWNER_SPAWN_MOB, SoundSource.BLOCKS, 1.0F, 1.0F);
        double x = pos.getX() + 0.5, y = pos.getY() + 0.5, z = pos.getZ() + 0.5;
        level.sendParticles(ParticleTypes.FLAME, x, y, z, 30, 0.35, 0.35, 0.35, 0.02);
        level.sendParticles(ParticleTypes.SOUL, x, y, z, 12, 0.3, 0.3, 0.3, 0.03);
    }

    /** A dead cage still smoulders a little. */
    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (random.nextInt(4) == 0) {
            level.addParticle(ParticleTypes.SMOKE, pos.getX() + 0.3 + random.nextDouble() * 0.4, pos.getY() + 0.3 + random.nextDouble() * 0.4,
                    pos.getZ() + 0.3 + random.nextDouble() * 0.4, 0.0, 0.01, 0.0);
        }
    }
}
