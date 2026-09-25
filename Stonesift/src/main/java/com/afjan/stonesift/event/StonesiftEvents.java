package com.afjan.stonesift.event;

import com.afjan.stonesift.Stonesift;
import com.afjan.stonesift.item.RockItem;
import com.afjan.stonesift.item.SimpleItems;
import com.afjan.stonesift.machine.MachineType;
import com.afjan.stonesift.registry.ModBlockEntities;
import com.afjan.stonesift.rock.Rock;
import com.afjan.stonesift.machine.CoalGeneratorBlockEntity;
import com.afjan.stonesift.machine.DeepDrillBlockEntity;
import com.afjan.stonesift.machine.FlotationCellBlockEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.event.level.BlockDropsEvent;

@EventBusSubscriber(modid = Stonesift.MODID)
public final class StonesiftEvents {
    private StonesiftEvents() {}

    /** Stone hammer: rock drops as 2 gravel of its type instead of the block. */
    @SubscribeEvent
    static void onBlockDrops(BlockDropsEvent event) {
        if (!(event.getTool().getItem() instanceof SimpleItems.StoneHammer)) {
            return;
        }
        Rock rock = Rock.of(event.getState());
        if (rock == null) {
            return;
        }
        event.getDrops().clear();
        ServerLevel level = event.getLevel();
        BlockPos pos = event.getPos();
        event.getDrops().add(new ItemEntity(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                RockItem.stack(RockItem.Stage.GRAVEL, rock, false, 2)));
    }

    @SuppressWarnings("unchecked")
    public static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(Capabilities.Energy.BLOCK,
                (BlockEntityType<FlotationCellBlockEntity>) ModBlockEntities.MACHINES.get(MachineType.FLOTATION_CELL).get(), (be, side) -> be.energy);
        event.registerBlockEntity(Capabilities.Energy.BLOCK,
                (BlockEntityType<CoalGeneratorBlockEntity>) ModBlockEntities.MACHINES.get(MachineType.COAL_GENERATOR).get(), (be, side) -> be.energy);
        event.registerBlockEntity(Capabilities.Energy.BLOCK,
                (BlockEntityType<DeepDrillBlockEntity>) ModBlockEntities.MACHINES.get(MachineType.DEEP_DRILL).get(), (be, side) -> be.energy);
    }
}
