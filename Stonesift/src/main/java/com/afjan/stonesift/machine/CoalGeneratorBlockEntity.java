package com.afjan.stonesift.machine;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CookingFuel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.level.storage.loot.providers.number.ints.ResolvableInt;
import net.neoforged.neoforge.transfer.energy.SimpleEnergyHandler;

/** Coal generator: burns any furnace fuel for 40 FE/t and pushes the FE into every neighbour that takes energy. */
public class CoalGeneratorBlockEntity extends MachineBlockEntity {
    public static final int CAPACITY = 64_000;
    public static final int FE_PER_TICK = 40;
    public final SimpleEnergyHandler energy = Energy.storage(CAPACITY, 0, 1000, this::setChanged);
    private int burn;
    private int burnMax = 1;

    public CoalGeneratorBlockEntity(BlockPos pos, BlockState state) {
        super(MachineType.COAL_GENERATOR, pos, state);
    }

    @Override
    public void serverTick(ServerLevel level) {
        int stored = this.energy.getAmountAsInt();
        if (this.burn <= 0 && stored + FE_PER_TICK <= CAPACITY) {
            ItemStack fuel = this.items.get(0);
            int time = fuel.isEmpty() ? 0 : ResolvableInt.getFromItem(fuel, DataComponents.COOKING_FUEL, CookingFuel::burnTime,
                    this.getLootContext(level, fuel), 0);
            if (time > 0) {
                this.burn = this.burnMax = time;
                if (fuel.is(Items.LAVA_BUCKET)) {
                    this.items.set(0, new ItemStack(Items.BUCKET));
                } else {
                    fuel.shrink(1);
                }
                this.setChanged();
            }
        }
        if (this.burn > 0) {
            this.burn--;
            this.energy.set(Math.min(CAPACITY, this.energy.getAmountAsInt() + FE_PER_TICK));
        }
        Energy.pushToNeighbours(level, this.worldPosition, this.energy, 1000);
        this.data[0] = this.burn;
        this.data[1] = this.burnMax;
        this.putEnergy(2, this.energy.getAmountAsInt());
        MachineBlock.setLit(level, this.worldPosition, this.getBlockState(), this.burn > 0);
    }

    @Override
    protected void saveExtra(ValueOutput output) {
        this.energy.serialize(output);
        output.putInt("burn", this.burn);
        output.putInt("burn_max", this.burnMax);
    }

    @Override
    protected void loadExtra(ValueInput input) {
        this.energy.deserialize(input);
        this.burn = input.getIntOr("burn", 0);
        this.burnMax = Math.max(1, input.getIntOr("burn_max", 1));
    }
}
