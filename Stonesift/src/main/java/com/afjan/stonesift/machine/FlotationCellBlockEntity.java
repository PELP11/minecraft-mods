package com.afjan.stonesift.machine;

import java.util.List;

import com.afjan.stonesift.item.RockItem;
import com.afjan.stonesift.rock.Resource;
import com.afjan.stonesift.rock.Yields;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.transfer.energy.SimpleEnergyHandler;

/**
 * Flotation cell: FE + an adjacent water source + fine slurry + a reagent. The reagent picks the metal that floats:
 * bone meal copper, dried kelp iron, glowstone dust gold, amethyst shard diamond. One reagent treats 4 slurry.
 */
public class FlotationCellBlockEntity extends MachineBlockEntity {
    public static final int CAPACITY = 100_000;
    public static final int FE_PER_TICK = 20;
    public static final int TIME = 40;
    public static final int USES_PER_REAGENT = 4;
    public final SimpleEnergyHandler energy = Energy.storage(CAPACITY, 1000, 0, this::setChanged);
    private int progress;
    private int reagentUses;
    private Resource reagentMetal = Resource.IRON;

    public FlotationCellBlockEntity(BlockPos pos, BlockState state) {
        super(MachineType.FLOTATION_CELL, pos, state);
    }

    @Override
    public void serverTick(ServerLevel level) {
        ItemStack slurry = this.items.get(0);
        RockItem rock = RockItem.of(slurry, RockItem.Stage.SLURRY);
        boolean water = this.hasWater(level);
        boolean working = false;
        if (rock != null && water) {
            if (this.reagentUses <= 0) {
                Resource metal = Yields.reagent(this.items.get(1));
                if (metal != null) {
                    this.items.get(1).shrink(1);
                    this.reagentUses = USES_PER_REAGENT;
                    this.reagentMetal = metal;
                }
            }
            if (this.reagentUses > 0 && Energy.use(this.energy, FE_PER_TICK)) {
                working = true;
                if (++this.progress >= TIME) {
                    int n = Yields.flotation(rock.rock(), RockItem.isRich(slurry), this.reagentMetal, level.getRandom());
                    if (n == 0 || this.output(List.of(Yields.concentrate(this.reagentMetal, n)))) {
                        slurry.shrink(1);
                        this.reagentUses--;
                        this.setChanged();
                    }
                    this.progress = 0;
                }
                if (level.getGameTime() % 8 == 0) {
                    level.sendParticles(ParticleTypes.BUBBLE_POP, this.worldPosition.getX() + 0.5, this.worldPosition.getY() + 1.0,
                            this.worldPosition.getZ() + 0.5, 4, 0.25, 0.02, 0.25, 0.01);
                }
                if (level.getGameTime() % 40 == 0) {
                    level.playSound(null, this.worldPosition, SoundEvents.BUBBLE_COLUMN_UPWARDS_AMBIENT, SoundSource.BLOCKS, 0.4F, 1.2F);
                }
            }
        }
        this.data[0] = this.progress;
        this.data[1] = TIME;
        this.putEnergy(2, this.energy.getAmountAsInt());
        this.data[4] = water ? 1 : 0;
        this.data[5] = this.reagentUses;
        MachineBlock.setLit(level, this.worldPosition, this.getBlockState(), working);
    }

    private boolean hasWater(ServerLevel level) {
        for (Direction d : Direction.values()) {
            FluidState state = level.getFluidState(this.worldPosition.relative(d));
            if (state.is(FluidTags.WATER) && state.isSource()) {
                return true;
            }
        }
        return false;
    }

    @Override
    protected void saveExtra(ValueOutput output) {
        this.energy.serialize(output);
        output.putInt("progress", this.progress);
        output.putInt("reagent_uses", this.reagentUses);
        output.putInt("reagent_metal", this.reagentMetal.ordinal());
    }

    @Override
    protected void loadExtra(ValueInput input) {
        this.energy.deserialize(input);
        this.progress = input.getIntOr("progress", 0);
        this.reagentUses = input.getIntOr("reagent_uses", 0);
        this.reagentMetal = Resource.values()[Math.floorMod(input.getIntOr("reagent_metal", 1), Resource.values().length)];
    }
}
