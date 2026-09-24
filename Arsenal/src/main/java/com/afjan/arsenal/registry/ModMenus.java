package com.afjan.arsenal.registry;

import com.afjan.arsenal.Arsenal;
import com.afjan.arsenal.menu.WeaponWorkbenchMenu;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModMenus {
    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, Arsenal.MODID);

    public static final DeferredHolder<MenuType<?>, MenuType<WeaponWorkbenchMenu>> WEAPON_WORKBENCH =
            MENUS.register("weapon_workbench", () -> new MenuType<>(WeaponWorkbenchMenu::new, FeatureFlags.VANILLA_SET));

    private ModMenus() {}
}
