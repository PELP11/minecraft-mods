package com.afjan.stonesift.machine;

import java.util.List;

import com.afjan.stonesift.item.RockItem;
import com.afjan.stonesift.rock.Rock;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/** Grinder: rock in from the top, coal from the side, gravel out below. 1 rock = 2 gravel (10% a third); 1 coal = 16 runs. */
public class GrinderBlockEntity extends MachineBlockEntity {
    public static final int TIME = 40;
    private int progress;
    private int fuelOps;
    private int fuelMax = 16;

    public GrinderBlockEntity(BlockPos pos, BlockState state) {
        super(MachineType.GRINDER, pos, state);
    }

    @Override
    public void serverTick(ServerLevel level) {
        ItemStack input = this.items.get(0);
        Rock rock = Rock.of(input);
        boolean working = false;
        if (rock != null) {
            if (this.fuelOps <= 0) {
                ItemStack fuel = this.items.get(1);
                int ops = MachineType.grinderFuelOps(fuel);
                if (ops > 0) {
                    fuel.shrink(1);
                    this.fuelOps = this.fuelMax = ops;
                    this.setChanged();
                }
            }
            if (this.fuelOps > 0) {
                working = true;
                if (++this.progress >= TIME) {
                    int count = 2 + (level.getRandom().nextFloat() < 0.1F ? 1 : 0);
                    if (this.output(List.of(RockItem.stack(RockItem.Stage.GRAVEL, rock, false, count)))) {
                        input.shrink(1);
                        this.fuelOps--;
                        level.playSound(null, this.worldPosition, SoundEvents.GRINDSTONE_USE, SoundSource.BLOCKS, 0.5F, 0.7F);
                    }
                    this.progress = 0;
                }
            }
        } else {
            this.progress = 0;
        }
        this.data[0] = this.progress;
        this.data[1] = TIME;
        this.data[2] = this.fuelOps;
        this.data[3] = this.fuelMax;
        MachineBlock.setLit(level, this.worldPosition, this.getBlockState(), working);
    }

    @Override
    protected void saveExtra(ValueOutput output) {
        output.putInt("progress", this.progress);
        output.putInt("fuel_ops", this.fuelOps);
        output.putInt("fuel_max", this.fuelMax);
    }

    @Override
    protected void loadExtra(ValueInput input) {
        this.progress = input.getIntOr("progress", 0);
        this.fuelOps = input.getIntOr("fuel_ops", 0);
        this.fuelMax = input.getIntOr("fuel_max", 16);
    }
}
