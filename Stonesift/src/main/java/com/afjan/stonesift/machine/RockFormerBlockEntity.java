package com.afjan.stonesift.machine;

import org.jetbrains.annotations.Nullable;

import com.afjan.stonesift.rock.Rock;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * Rock former: a water and a lava source next to it, a rock block in the pattern slot (kept), 1 block per second into
 * a 64 buffer (x2 / x4 with speed upgrades). Deepslate drinks one lava source per 64 blocks; blackstone needs the Nether.
 */
public class RockFormerBlockEntity extends MachineBlockEntity {
    public static final int TIME = 20;
    public static final int OK = 0;
    public static final int NO_WATER = 1;
    public static final int NO_LAVA = 2;
    public static final int NO_PATTERN = 3;
    public static final int NOT_NETHER = 4;
    public static final int FULL = 5;
    private int progress;
    private int sinceLava;

    public RockFormerBlockEntity(BlockPos pos, BlockState state) {
        super(MachineType.ROCK_FORMER, pos, state);
    }

    @Override
    public void serverTick(ServerLevel level) {
        int speed = Math.max(MachineType.speedOf(this.items.get(1)), MachineType.speedOf(this.items.get(2)));
        int time = TIME / speed;
        int status = this.status(level);
        if (status == OK) {
            if (++this.progress >= time) {
                this.progress = 0;
                this.produce(level);
            }
        } else {
            this.progress = 0;
        }
        this.data[0] = this.progress;
        this.data[1] = time;
        this.data[2] = status;
        this.data[3] = speed;
        MachineBlock.setLit(level, this.worldPosition, this.getBlockState(), status == OK);
    }

    public int status(ServerLevel level) {
        Rock rock = Rock.of(this.items.get(0));
        if (rock == null) {
            return NO_PATTERN;
        }
        if (this.source(level, FluidTags.WATER) == null) {
            return NO_WATER;
        }
        if (this.source(level, FluidTags.LAVA) == null) {
            return NO_LAVA;
        }
        if (rock == Rock.BLACKSTONE && level.dimension() != Level.NETHER) {
            return NOT_NETHER;
        }
        ItemStack out = this.items.get(3);
        if (!out.isEmpty() && (!ItemStack.isSameItem(out, this.items.get(0)) || out.getCount() >= 64)) {
            return FULL;
        }
        return OK;
    }

    private void produce(ServerLevel level) {
        ItemStack pattern = this.items.get(0);
        ItemStack out = this.items.get(3);
        if (out.isEmpty()) {
            this.items.set(3, pattern.copyWithCount(1));
        } else {
            out.grow(1);
        }
        if (Rock.of(pattern) == Rock.DEEPSLATE && ++this.sinceLava >= 64) {
            this.sinceLava = 0;
            BlockPos lava = this.source(level, FluidTags.LAVA);
            if (lava != null) {
                level.setBlockAndUpdate(lava, Blocks.AIR.defaultBlockState());
            }
        }
        if (level.getRandom().nextInt(4) == 0) {
            level.playSound(null, this.worldPosition, SoundEvents.LAVA_EXTINGUISH, SoundSource.BLOCKS, 0.2F, 1.6F);
        }
        this.setChanged();
    }

    private @Nullable BlockPos source(ServerLevel level, net.minecraft.tags.TagKey<net.minecraft.world.level.material.Fluid> fluid) {
        for (Direction d : Direction.values()) {
            BlockPos p = this.worldPosition.relative(d);
            FluidState state = level.getFluidState(p);
            if (state.is(fluid) && state.isSource()) {
                return p;
            }
        }
        return null;
    }

    @Override
    protected void saveExtra(ValueOutput output) {
        output.putInt("since_lava", this.sinceLava);
    }

    @Override
    protected void loadExtra(ValueInput input) {
        this.sinceLava = input.getIntOr("since_lava", 0);
    }
}
