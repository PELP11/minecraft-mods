package com.afjan.stonesift.block;

import java.util.List;

import com.afjan.stonesift.item.SimpleItems;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Hand sieve: right-click with gravel puts a portion on it, holding right-click sifts, sneak + right-click with a
 * mesh swaps the mesh (with an empty hand it takes the mesh out). Results pop out on top.
 */
public class HandSieveBlock extends BaseEntityBlock {
    public static final IntegerProperty MESH = IntegerProperty.create("mesh", 0, 3);
    private static final VoxelShape SHAPE = Block.box(0.5, 0.0, 0.5, 15.5, 13.0, 15.5);

    public HandSieveBlock(BlockBehaviour.Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(MESH, 0));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(MESH);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new HandSieveBlockEntity(pos, state);
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
            InteractionHand hand, BlockHitResult hitResult) {
        if (!(level.getBlockEntity(pos) instanceof HandSieveBlockEntity sieve)) {
            return InteractionResult.PASS;
        }
        if (stack.getItem() instanceof SimpleItems.Mesh && player.isSecondaryUseActive()) {
            if (!level.isClientSide()) {
                ItemStack old = sieve.swapMesh(stack.copyWithCount(1));
                stack.consume(1, player);
                if (!old.isEmpty() && !player.getInventory().add(old)) {
                    player.drop(old, false, net.minecraft.util.Prediction.SERVER_ONLY);
                }
            }
            return InteractionResult.SUCCESS;
        }
        if (!level.isClientSide() && sieve.addGravel(stack)) {
            stack.consume(1, player);
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.TRY_WITH_EMPTY_HAND;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (!(level.getBlockEntity(pos) instanceof HandSieveBlockEntity sieve)) {
            return InteractionResult.PASS;
        }
        if (level instanceof ServerLevel server) {
            if (player.isSecondaryUseActive()) {
                if (sieve.count() == 0 && !sieve.mesh().isEmpty()) {
                    ItemStack old = sieve.swapMesh(ItemStack.EMPTY);
                    if (!player.getInventory().add(old)) {
                        player.drop(old, false, net.minecraft.util.Prediction.SERVER_ONLY);
                    }
                }
                return InteractionResult.SUCCESS;
            }
            List<ItemStack> out = sieve.work(server);
            if (out != null) {
                for (ItemStack stack : out) {
                    ItemEntity item = new ItemEntity(server, pos.getX() + 0.5, pos.getY() + 1.05, pos.getZ() + 0.5, stack);
                    item.setDeltaMovement(0.0, 0.15, 0.0);
                    server.addFreshEntity(item);
                }
            }
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston) {
        super.affectNeighborsAfterRemoval(state, level, pos, movedByPiston);
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof HandSieveBlockEntity sieve) {
            Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), sieve.mesh());
            if (sieve.rock() != null) {
                Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(),
                        com.afjan.stonesift.item.RockItem.stack(com.afjan.stonesift.item.RockItem.Stage.GRAVEL, sieve.rock(), sieve.rich(), sieve.count()));
            }
        }
        return super.playerWillDestroy(level, pos, state, player);
    }
}
