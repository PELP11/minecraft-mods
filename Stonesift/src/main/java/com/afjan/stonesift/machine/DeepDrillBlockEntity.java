package com.afjan.stonesift.machine;

import java.util.List;

import com.afjan.stonesift.item.RockItem;
import com.afjan.stonesift.registry.ModBlocks;
import com.afjan.stonesift.registry.ModItems;
import com.afjan.stonesift.rock.Rock;
import com.afjan.stonesift.rock.Veins;

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
import net.neoforged.neoforge.transfer.energy.SimpleEnergyHandler;

/**
 * Deep Drill: the drill head in the middle of a 3x3 ring of Deep Drill Frames. It first bores a shaft down to the
 * bedrock (about 10 minutes, the hole stays open), then pumps rich gravel of the chunk's rock vein forever: same
 * profiles, three times the yield. Costs FE and wears the drill bit (diamond, netherite = twice as fast).
 */
public class DeepDrillBlockEntity extends MachineBlockEntity {
    public static final int CAPACITY = 500_000;
    public static final int BORE_TIME = 12_000;
    public static final int BORE_FE_PER_TICK = 40;
    public static final int GRAVEL_FE = 2_000;
    public static final int UNFORMED = 0;
    public static final int NO_BIT = 1;
    public static final int NO_POWER = 2;
    public static final int BORING = 3;
    public static final int PUMPING = 4;
    public static final int FULL = 5;
    public final SimpleEnergyHandler energy = Energy.storage(CAPACITY, 10_000, 0, this::setChanged);
    private int shaftY = Integer.MIN_VALUE;
    private int bored;
    private int total;
    private int progress;
    private boolean reachedBedrock;
    /** GameTests shorten the timings. */
    public int timeScale = 1;

    public DeepDrillBlockEntity(BlockPos pos, BlockState state) {
        super(MachineType.DEEP_DRILL, pos, state);
    }

    public boolean isFormed(ServerLevel level) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if ((dx != 0 || dz != 0) && !level.getBlockState(this.worldPosition.offset(dx, 0, dz)).is(ModBlocks.DEEP_DRILL_FRAME.get())) {
                    return false;
                }
            }
        }
        return true;
    }

    public boolean reachedBedrock() {
        return this.reachedBedrock;
    }

    @Override
    public void serverTick(ServerLevel level) {
        int state = this.work(level);
        this.data[0] = state;
        this.data[1] = this.bored;
        this.data[2] = Math.max(1, this.total);
        this.putEnergy(3, this.energy.getAmountAsInt());
        MachineBlock.setLit(level, this.worldPosition, this.getBlockState(), state == BORING || state == PUMPING);
    }

    private int work(ServerLevel level) {
        if (!this.isFormed(level)) {
            return UNFORMED;
        }
        ItemStack bit = this.items.get(0);
        if (bit.isEmpty()) {
            return NO_BIT;
        }
        boolean netherite = bit.is(ModItems.NETHERITE_DRILL_BIT.get());
        if (!this.reachedBedrock) {
            if (this.shaftY == Integer.MIN_VALUE) {
                this.shaftY = this.worldPosition.getY() - 1;
                this.total = Math.max(1, this.shaftY - level.getMinY());
            }
            if (!Energy.use(this.energy, BORE_FE_PER_TICK)) {
                return NO_POWER;
            }
            int interval = Math.max(1, BORE_TIME / this.total / (netherite ? 2 : 1) / this.timeScale);
            if (++this.progress >= interval) {
                this.progress = 0;
                this.boreOne(level, bit);
            }
            return BORING;
        }
        int interval = Math.max(1, (netherite ? 20 : 40) / this.timeScale);
        if (this.energy.getAmountAsInt() < GRAVEL_FE) {
            return NO_POWER;
        }
        if (++this.progress < interval) {
            return PUMPING;
        }
        this.progress = 0;
        Rock rock = Veins.vein(level, this.worldPosition.getX() >> 4, this.worldPosition.getZ() >> 4);
        if (!this.output(List.of(RockItem.stack(RockItem.Stage.GRAVEL, rock, true, 1)))) {
            return FULL;
        }
        Energy.use(this.energy, GRAVEL_FE);
        this.wear(level, bit);
        if (level.getRandom().nextInt(3) == 0) {
            level.playSound(null, this.worldPosition, SoundEvents.STONE_BREAK, SoundSource.BLOCKS, 0.6F, 0.6F);
        }
        return PUMPING;
    }

    private void boreOne(ServerLevel level, ItemStack bit) {
        BlockPos at = new BlockPos(this.worldPosition.getX(), this.shaftY, this.worldPosition.getZ());
        BlockState state = level.getBlockState(at);
        if (this.shaftY <= level.getMinY() || state.is(Blocks.BEDROCK)) {
            this.reachedBedrock = true;
            this.bored = this.total;
            this.setChanged();
            return;
        }
        if (!state.isAir()) {
            level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, state), at.getX() + 0.5, at.getY() + 0.5, at.getZ() + 0.5,
                    10, 0.3, 0.3, 0.3, 0.1);
            level.setBlockAndUpdate(at, Blocks.AIR.defaultBlockState());
            level.playSound(null, this.worldPosition, SoundEvents.STONE_BREAK, SoundSource.BLOCKS, 0.8F, 0.5F);
            this.wear(level, bit);
        }
        this.shaftY--;
        this.bored++;
        this.setChanged();
    }

    private void wear(ServerLevel level, ItemStack bit) {
        bit.setDamageValue(bit.getDamageValue() + 1);
        if (bit.getDamageValue() >= bit.getMaxDamage()) {
            this.items.set(0, ItemStack.EMPTY);
            level.playSound(null, this.worldPosition, SoundEvents.ITEM_BREAK.value(), SoundSource.BLOCKS, 1.0F, 0.8F);
        }
    }

    @Override
    protected void saveExtra(ValueOutput output) {
        this.energy.serialize(output);
        output.putInt("shaft_y", this.shaftY);
        output.putInt("bored", this.bored);
        output.putInt("total", this.total);
        output.putBoolean("bedrock", this.reachedBedrock);
    }

    @Override
    protected void loadExtra(ValueInput input) {
        this.energy.deserialize(input);
        this.shaftY = input.getIntOr("shaft_y", Integer.MIN_VALUE);
        this.bored = input.getIntOr("bored", 0);
        this.total = input.getIntOr("total", 0);
        this.reachedBedrock = input.getBooleanOr("bedrock", false);
    }
}
