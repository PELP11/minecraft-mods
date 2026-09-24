package com.afjan.arsenal.registry;

import com.afjan.arsenal.Arsenal;
import com.afjan.arsenal.combat.Ordnance;
import com.afjan.arsenal.gun.Attachment;
import com.afjan.arsenal.gun.Caliber;
import com.afjan.arsenal.gun.GunType;
import com.afjan.arsenal.vehicle.Store;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModTabs {
    public static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, Arsenal.MODID);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> ARSENAL = TABS.register("arsenal",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.arsenal"))
                    .withTabsBefore(CreativeModeTabs.SPAWN_EGGS)
                    .icon(() -> ModItems.GUNS.get(GunType.AK47).get().getDefaultInstance())
                    .displayItems((parameters, output) -> {
                        output.accept(ModItems.WEAPON_WORKBENCH.get());
                        for (GunType type : GunType.values()) {
                            output.accept(ModItems.GUNS.get(type).get());
                        }
                        for (Caliber caliber : Caliber.values()) {
                            output.accept(ModItems.AMMO.get(caliber).get());
                        }
                        for (Attachment attachment : Attachment.values()) {
                            output.accept(ModItems.ATTACHMENTS.get(attachment).get());
                        }
                        for (Ordnance kind : Ordnance.values()) {
                            if (kind.thrownByHand()) {
                                output.accept(ModItems.ORDNANCE_ITEMS.get(kind).get());
                            }
                        }
                        output.accept(ModItems.TACTICAL_NUKE.get());
                        output.accept(ModItems.F14_TOMCAT.get());
                        for (Store store : Store.values()) {
                            output.accept(ModItems.JET_STORES.get(store).get());
                        }
                        output.accept(ModItems.CANNON_SHELLS.get());
                        output.accept(ModItems.FLARE_CARTRIDGES.get());
                        output.accept(ModItems.STEEL_BLEND.get());
                        output.accept(ModItems.STEEL_INGOT.get());
                        output.accept(ModItems.STEEL_BLOCK.get());
                        output.accept(ModItems.BRASS_CASING.get());
                        output.accept(ModItems.BULLET_TIP.get());
                        output.accept(ModItems.PROPELLANT.get());
                        output.accept(ModItems.GUN_BARREL.get());
                        output.accept(ModItems.WEAPON_RECEIVER.get());
                        output.accept(ModItems.TRIGGER_ASSEMBLY.get());
                        output.accept(ModItems.WEAPON_STOCK.get());
                        output.accept(ModItems.PRECISION_PARTS.get());
                        output.accept(ModItems.OPTICAL_LENS.get());
                        output.accept(ModItems.LASER_MODULE.get());
                        output.accept(ModItems.CIRCUIT_BOARD.get());
                        output.accept(ModItems.EXPLOSIVE_COMPOUND.get());
                        output.accept(ModItems.ROCKET_MOTOR.get());
                        output.accept(ModItems.WARHEAD_CASING.get());
                        output.accept(ModItems.URANIUM_ORE.get());
                        output.accept(ModItems.DEEPSLATE_URANIUM_ORE.get());
                        output.accept(ModItems.RAW_URANIUM.get());
                        output.accept(ModItems.RAW_URANIUM_BLOCK.get());
                        output.accept(ModItems.URANIUM_INGOT.get());
                        output.accept(ModItems.URANIUM_BLOCK.get());
                        output.accept(ModItems.ENRICHED_URANIUM.get());
                        output.accept(ModItems.PLUTONIUM_CORE.get());
                    })
                    .build());

    private ModTabs() {}
}
