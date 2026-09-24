package com.afjan.drillworks.registry;

import com.afjan.drillworks.Drillworks;
import com.afjan.drillworks.menu.MiningDrillMenu;
import com.afjan.drillworks.menu.RefineryMenu;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModMenus {
    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, Drillworks.MODID);

    public static final DeferredHolder<MenuType<?>, MenuType<MiningDrillMenu>> MINING_DRILL =
            MENUS.register("mining_drill", () -> new MenuType<>(MiningDrillMenu::new, FeatureFlags.VANILLA_SET));
    public static final DeferredHolder<MenuType<?>, MenuType<RefineryMenu>> REFINERY =
            MENUS.register("refinery", () -> new MenuType<>(RefineryMenu::new, FeatureFlags.VANILLA_SET));

    private ModMenus() {}
}
