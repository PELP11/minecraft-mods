package com.afjan.tempered;

import com.afjan.tempered.ability.Abilities;
import com.afjan.tempered.ability.Replanter;
import com.afjan.tempered.event.BrokenTools;
import com.afjan.tempered.event.PerkEvents;
import com.afjan.tempered.event.ProgressEvents;
import com.afjan.tempered.registry.ModComponents;
import com.afjan.tempered.registry.ModItems;
import com.afjan.tempered.registry.ModLoot;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;

/**
 * Tempered: every tool and weapon earns mastery milestones through use and grows beyond vanilla;
 * at 0 durability tools become Broken instead of vanishing.
 */
@Mod(Tempered.MODID)
public final class Tempered {
    public static final String MODID = "tempered";

    public Tempered(IEventBus modBus, Dist dist) {
        ModComponents.COMPONENTS.register(modBus);
        ModItems.ITEMS.register(modBus);
        modBus.addListener(ModItems::onBuildTabs);
        ModLoot.MODIFIERS.register(modBus);

        ProgressEvents.register();
        PerkEvents.register();
        BrokenTools.register();
        Abilities.register();
        Replanter.register();
        if (dist == Dist.CLIENT) {
            com.afjan.tempered.client.TemperedClient.init(modBus);
        }
    }
}
