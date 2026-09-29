package com.afjan.tempered.client;

import com.afjan.tempered.mastery.LangKeys;
import com.afjan.tempered.mastery.Track;
import com.afjan.tempered.mastery.Tracks;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraftforge.client.event.RegisterItemDecorationsEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

/** Client wiring: the overview key, tooltips and the pips on item icons. */
public final class TemperedClient {
    public static final KeyMapping OVERVIEW = new KeyMapping(LangKeys.KEY_OVERVIEW, InputConstants.KEY_K, KeyMapping.Category.INVENTORY);

    private TemperedClient() {
    }

    public static void init(FMLJavaModLoadingContext context) {
        RegisterKeyMappingsEvent.BUS.addListener(event -> event.register(OVERVIEW));
        RegisterItemDecorationsEvent.BUS.addListener(TemperedClient::registerDecorations);
        ItemTooltipEvent.BUS.addListener(MasteryTooltip::onTooltip);
        TickEvent.ClientTickEvent.Post.BUS.addListener(event -> onClientTick());
    }

    /**
     * Every item gets the decorator (modded tools included); it draws only for broken tools and tools with a
     * track. Item components must not be read here: 26.x binds some of them only once a world loads.
     */
    private static void registerDecorations(RegisterItemDecorationsEvent event) {
        for (Item item : BuiltInRegistries.ITEM) event.register(item, MasteryDecorator.INSTANCE);
    }

    private static void onClientTick() {
        Minecraft minecraft = Minecraft.getInstance();
        while (OVERVIEW.consumeClick()) {
            if (minecraft.player != null && minecraft.gui.screen() == null) {
                Track held = Tracks.get(minecraft.player.getMainHandItem());
                if (held == null || Tracks.of(held.kind, held.tier) != held) held = Tracks.get(net.minecraft.world.item.Items.IRON_PICKAXE);
                minecraft.gui.setScreen(new MasteryScreen(held));
            }
        }
    }
}
