package com.afjan.drillworks.client;

import java.util.List;

import com.afjan.drillworks.block.entity.RefineryBlockEntity;
import com.afjan.drillworks.menu.RefineryMenu;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/** Crude oil and fuel on the left, the gasoline tank in the middle, canisters on the right. */
public class RefineryScreen extends AbstractContainerScreen<RefineryMenu> {
    private static final int TANK_X = 80;
    private static final int TANK_Y = 16;
    private static final int TANK_W = 16;
    private static final int TANK_H = 56;

    public RefineryScreen(RefineryMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, 176, 166);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(g, mouseX, mouseY, partialTick);
        int x = this.leftPos;
        int y = this.topPos;
        MiningDrillScreen.panel(g, x, y, this.imageWidth, this.imageHeight);
        MiningDrillScreen.slot(g, x + RefineryMenu.CRUDE_X, y + RefineryMenu.CRUDE_Y);
        MiningDrillScreen.slot(g, x + RefineryMenu.CRUDE_X, y + RefineryMenu.FUEL_Y);
        MiningDrillScreen.slot(g, x + RefineryMenu.CAN_X, y + RefineryMenu.CRUDE_Y);
        MiningDrillScreen.slot(g, x + RefineryMenu.CAN_X, y + RefineryMenu.FUEL_Y);

        // flame between crude oil and fuel
        int burn = this.menu.get(RefineryBlockEntity.DATA_BURN);
        int burnMax = Math.max(1, this.menu.get(RefineryBlockEntity.DATA_BURN_MAX));
        int fx = x + RefineryMenu.CRUDE_X + 2;
        int fy = y + 38;
        g.fill(fx, fy, fx + 12, fy + 12, 0xFF201A12);
        if (burn > 0) {
            int h = Math.max(1, 12 * burn / burnMax);
            g.fillGradient(fx + 2, fy + 12 - h, fx + 10, fy + 12, 0xFFFFE070, 0xFFE0500C);
        }

        // refining arrow crude -> tank
        int progress = this.menu.get(RefineryBlockEntity.DATA_PROGRESS);
        int ax = x + 48;
        int ay = y + 26;
        g.fill(ax, ay, ax + 26, ay + 4, 0xFF201A12);
        g.fill(ax, ay, ax + 26 * progress / RefineryBlockEntity.REFINE_TIME, ay + 4, 0xFFE8A020);

        // tank
        int tx = x + TANK_X;
        int ty = y + TANK_Y;
        g.fill(tx - 1, ty - 1, tx + TANK_W + 1, ty + TANK_H + 1, 0xFF000000);
        int gasoline = this.menu.get(RefineryBlockEntity.DATA_GASOLINE);
        int h = Math.round(TANK_H * gasoline / (float) RefineryBlockEntity.CAPACITY);
        if (h > 0) {
            g.fillGradient(tx, ty + TANK_H - h, tx + TANK_W, ty + TANK_H, 0xFFFFC040, 0xFFB85A0C);
        }
        for (int i = 1; i < 8; i++) {
            g.fill(tx, ty + i * TANK_H / 8, tx + (i % 2 == 0 ? 8 : 4), ty + i * TANK_H / 8 + 1, 0x80FFFFFF);
        }

        // filling arrow tank -> canisters
        int fill = this.menu.get(RefineryBlockEntity.DATA_FILL);
        int cx = x + 102;
        g.fill(cx, ay, cx + 26, ay + 4, 0xFF201A12);
        g.fill(cx, ay, cx + 26 * fill / RefineryBlockEntity.FILL_TIME, ay + 4, 0xFFE8A020);
        int dx = x + RefineryMenu.CAN_X + 6;
        g.fill(dx, y + 38, dx + 4, y + 50, 0xFF201A12);
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        g.text(this.font, this.title, this.titleLabelX, this.titleLabelY, MiningDrillScreen.TEXT, false);
        g.text(this.font, this.playerInventoryTitle, this.inventoryLabelX, this.inventoryLabelY, MiningDrillScreen.TEXT, false);
        if (this.menu.get(RefineryBlockEntity.DATA_FORMED) == 0) {
            Component warning = Component.translatable("gui.drillworks.unformed");
            g.centeredText(this.font, warning, this.imageWidth / 2, 62 + 12, 0xFFFF6040);
        }
    }

    @Override
    protected void extractTooltip(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        super.extractTooltip(g, mouseX, mouseY);
        if (this.isHovering(TANK_X, TANK_Y, TANK_W, TANK_H, mouseX, mouseY)) {
            g.setComponentTooltipForNextFrame(this.font, List.of(Component.translatable("gui.drillworks.gasoline",
                    this.menu.get(RefineryBlockEntity.DATA_GASOLINE), RefineryBlockEntity.CAPACITY)), mouseX, mouseY);
        }
    }
}
