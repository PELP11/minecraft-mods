package com.afjan.arsenal.block;

import java.util.HashSet;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import com.afjan.arsenal.combat.BlastScheduler;
import com.afjan.arsenal.combat.Detonations;
import com.afjan.arsenal.registry.ModBlocks;
import com.afjan.arsenal.registry.ModSounds;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.redstone.Orientation;
import net.minecraft.world.phys.BlockHitResult;

/**
 * The Tactical Nuke. Arm it with flint and steel or a redstone signal, then run: ten seconds later everything within
 * two hundred blocks stops existing, all the way down past the deepslate. Breaking an armed warhead defuses it.
 */
public class NukeBlock extends Block {
    public static final BooleanProperty ARMED = BooleanProperty.create("armed");
    /** Seconds between arming and detonation. */
    public static final int COUNTDOWN = 10;
    public static final int RADIUS = 200;
    /** Warheads whose countdown is running right now. */
    private static final Set<GlobalPos> COUNTING = new HashSet<>();

    public NukeBlock(BlockBehaviour.Properties properties) {
        super(properties);
        this.registerDefaultState(this.defaultBlockState().setValue(ARMED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(ARMED);
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
            InteractionHand hand, BlockHitResult hitResult) {
        if (!stack.is(Items.FLINT_AND_STEEL) && !stack.is(Items.FIRE_CHARGE)) {
            return super.useItemOn(stack, state, level, pos, player, hand, hitResult);
        }
        if (arm(level, pos, state)) {
            if (stack.is(Items.FLINT_AND_STEEL)) {
                stack.hurtAndBreak(1, player, hand.asEquipmentSlot());
            } else {
                stack.consume(1, player);
            }
            player.sendOverlayMessage(Component.translatable("message.arsenal.nuke_armed", COUNTDOWN));
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        if (!oldState.is(state.getBlock()) && level.hasNeighborSignal(pos)) {
            arm(level, pos, state);
        }
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block,
            @Nullable Orientation orientation, boolean movedByPiston) {
        if (level.hasNeighborSignal(pos)) {
            arm(level, pos, state);
        }
    }

    /** A nuke caught in someone else's blast goes off too. */
    @Override
    public void wasExploded(ServerLevel level, BlockPos pos, Explosion explosion) {
        arm(level, pos, level.getBlockState(pos));
    }

    /**
     * Arms the warhead, unless its countdown is already running. A warhead saved as armed whose countdown was lost
     * (the world was closed mid-countdown) can be armed again instead of sitting there dead.
     */
    private static boolean arm(Level level, BlockPos pos, BlockState state) {
        if (!(level instanceof ServerLevel serverLevel) || !state.is(ModBlocks.TACTICAL_NUKE.get())) {
            return false;
        }
        GlobalPos key = GlobalPos.of(level.dimension(), pos.immutable());
        if (!COUNTING.add(key)) {
            return false;
        }
        if (!state.getValue(ARMED)) {
            serverLevel.setBlock(pos, state.setValue(ARMED, true), Block.UPDATE_ALL);
        }
        tickCountdown(serverLevel, pos, COUNTDOWN);
        return true;
    }

    /** Forgets every running countdown (the server is stopping; the queue driving them is cleared with it). */
    public static void forgetCountdowns() {
        COUNTING.clear();
    }

    public static boolean isCounting(Level level, BlockPos pos) {
        return COUNTING.contains(GlobalPos.of(level.dimension(), pos));
    }

    private static void tickCountdown(ServerLevel level, BlockPos pos, int secondsLeft) {
        BlockState state = level.getBlockState(pos);
        if (!state.is(ModBlocks.TACTICAL_NUKE.get()) || !state.getOptionalValue(ARMED).orElse(false)) {
            COUNTING.remove(GlobalPos.of(level.dimension(), pos));
            return; // defused: the warhead was broken or replaced while the clock ran
        }
        if (secondsLeft <= 0) {
            COUNTING.remove(GlobalPos.of(level.dimension(), pos));
            level.removeBlock(pos, false);
            Detonations.nuke(level, pos, null);
            return;
        }
        // an alarm that climbs as the clock runs down
        float pitch = 0.7F + (COUNTDOWN - secondsLeft) * 0.09F;
        level.playSound(null, pos, ModSounds.NUKE_BEEP.value(), SoundSource.BLOCKS, 1.0F, pitch);
        level.sendParticles(net.minecraft.core.particles.ParticleTypes.SMOKE,
                pos.getX() + 0.5, pos.getY() + 1.1, pos.getZ() + 0.5, 10, 0.2, 0.1, 0.2, 0.02);
        BlastScheduler.after(level, 20, () -> tickCountdown(level, pos, secondsLeft - 1));
    }
}
