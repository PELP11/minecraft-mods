package com.afjan.juicer.registry;

import com.afjan.juicer.Juicer;
import com.afjan.juicer.menu.InfuserMenu;
import com.afjan.juicer.menu.MixerMenu;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModMenus {
    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, Juicer.MODID);

    public static final DeferredHolder<MenuType<?>, MenuType<MixerMenu>> MIXER = MENUS.register("mixer",
            () -> new MenuType<>(MixerMenu::new, FeatureFlags.VANILLA_SET));

    public static final DeferredHolder<MenuType<?>, MenuType<InfuserMenu>> INFUSER = MENUS.register("infuser",
            () -> new MenuType<>(InfuserMenu::new, FeatureFlags.VANILLA_SET));

    private ModMenus() {}
}
