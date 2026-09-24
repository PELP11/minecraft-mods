package com.afjan.juicer.client;

import java.util.ArrayList;
import java.util.List;

import com.afjan.juicer.block.Contents;
import com.afjan.juicer.fruit.Fruit;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/** Draws the concentrate gauge shared by both machine screens (18x52 px, overlay sprite at u=176, v=20). */
final class TankRenderer {
    static final int WIDTH = 18;
    static final int HEIGHT = 52;

    private TankRenderer() {}

    static void draw(GuiGraphicsExtractor graphics, Identifier texture, int x, int y, Contents contents, int amount, int capacity) {
        Fruit fruit = contents.fruit();
        if (fruit != null && amount > 0 && capacity > 0) {
            int height = Math.max(1, Math.min(HEIGHT, amount * HEIGHT / capacity));
            int color = 0xFF000000 | fruit.color();
            graphics.fillGradient(x, y + HEIGHT - height, x + WIDTH, y + HEIGHT, lighter(color), darker(color));
            // bright surface line
            graphics.fill(x, y + HEIGHT - height, x + WIDTH, y + HEIGHT - height + 1, lighter(lighter(color)));
        }
        graphics.blit(RenderPipelines.GUI_TEXTURED, texture, x, y, 176.0F, 20.0F, WIDTH, HEIGHT, 256, 256);
    }

    static List<Component> tooltip(Contents contents, int amount, int capacity) {
        List<Component> lines = new ArrayList<>();
        Fruit fruit = contents.fruit();
        if (fruit == null || amount <= 0) {
            lines.add(Component.translatable("gui.juicer.tank.empty"));
        } else {
            lines.add(Component.translatable("concentrate.juicer." + fruit.id()).withStyle(style -> style.withColor(fruit.color())));
        }
        lines.add(Component.translatable("gui.juicer.tank.amount", amount, capacity).withStyle(ChatFormatting.GRAY));
        return lines;
    }

    private static int lighter(int argb) {
        return mix(argb, 0xFFFFFFFF, 0.25F);
    }

    private static int darker(int argb) {
        return mix(argb, 0xFF000000, 0.3F);
    }

    private static int mix(int a, int b, float t) {
        int r = (int) (((a >> 16) & 0xFF) * (1 - t) + ((b >> 16) & 0xFF) * t);
        int g = (int) (((a >> 8) & 0xFF) * (1 - t) + ((b >> 8) & 0xFF) * t);
        int bl = (int) ((a & 0xFF) * (1 - t) + (b & 0xFF) * t);
        return 0xFF000000 | (r << 16) | (g << 8) | bl;
    }
}
