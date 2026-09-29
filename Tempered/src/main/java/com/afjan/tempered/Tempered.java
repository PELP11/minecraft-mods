package com.afjan.tempered;

import com.afjan.tempered.ability.Abilities;
import com.afjan.tempered.ability.Replanter;
import com.afjan.tempered.event.BrokenTools;
import com.afjan.tempered.event.PerkEvents;
import com.afjan.tempered.event.ProgressEvents;
import com.afjan.tempered.gametest.TemperedGameTests;
import com.afjan.tempered.registry.ModComponents;
import com.afjan.tempered.registry.ModLoot;
import net.minecraft.core.registries.Registries;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.gametest.ForgeGameTestHooks;
import net.minecraftforge.registries.RegisterEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;

/**
 * Tempered: every tool and weapon earns mastery milestones through use and grows beyond vanilla;
 * at 0 durability tools become Broken instead of vanishing.
 */
@Mod(Tempered.MODID)
public final class Tempered {
    public static final String MODID = "tempered";

    public Tempered(FMLJavaModLoadingContext context) {
        var modBus = context.getModBusGroup();
        ModComponents.COMPONENTS.register(modBus);
        ModLoot.MODIFIERS.register(modBus);

        // GameTests exist only in dev runs (their test_instance files are left out of the jar).
        if (ForgeGameTestHooks.isGametestEnabled()) {
            var tests = ForgeGameTestHooks.gatherTests(TemperedGameTests.class, null);
            RegisterEvent.getBus(modBus).addListener(event -> {
                if (event.getRegistryKey().equals(Registries.TEST_FUNCTION)) {
                    tests.forEach((id, test) -> event.register(Registries.TEST_FUNCTION, id, test::consumer));
                }
            });
        }

        ProgressEvents.register();
        PerkEvents.register();
        BrokenTools.register();
        Abilities.register();
        Replanter.register();

        if (FMLEnvironment.dist == Dist.CLIENT) {
            com.afjan.tempered.client.TemperedClient.init(context);
        }
    }
}
