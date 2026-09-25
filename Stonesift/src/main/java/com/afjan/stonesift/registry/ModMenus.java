package com.afjan.stonesift.registry;

import java.util.EnumMap;
import java.util.Map;

import com.afjan.stonesift.Stonesift;
import com.afjan.stonesift.machine.MachineMenu;
import com.afjan.stonesift.machine.MachineType;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModMenus {
    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, Stonesift.MODID);
    public static final Map<MachineType, DeferredHolder<MenuType<?>, MenuType<MachineMenu>>> MACHINES = new EnumMap<>(MachineType.class);

    static {
        for (MachineType type : MachineType.values()) {
            MACHINES.put(type, MENUS.register(type.key(),
                    () -> new MenuType<>((id, inventory) -> new MachineMenu(type, id, inventory), FeatureFlags.VANILLA_SET)));
        }
    }

    private ModMenus() {}
}
