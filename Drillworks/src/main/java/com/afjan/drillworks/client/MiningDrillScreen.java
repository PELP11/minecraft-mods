package com.afjan.drillworks.client;

import java.util.List;

import com.afjan.drillworks.drill.DrillModules;
import com.afjan.drillworks.entity.MiningDrillEntity;
import com.afjan.drillworks.menu.MiningDrillMenu;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/** The drill's menu, drawn from rectangles (no GUI texture): head + sockets, fuel gauge and slot, the hold. */
public class MiningDrillScreen extends AbstractContainerScreen<MiningDrillMenu> {
    static final int PANEL = 0xFF3A3322;
    static final int PANEL_LIGHT = 0xFF4E452D;
    static final int EDGE = 0xFFD9A916;
    static final int SLOT = 0xFF16140E;
    static final int TEXT = 0xFFF2E6C0;
    private static final int GAUGE_X = 112;
    private static final int GAUGE_W = 34;

    public MiningDrillScreen(MiningDrillMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, 176, MiningDrillMenu.HEIGHT);
    }

    @Override
    protected void init() {
        super.init();
        this.titleLabelY = 6;
        this.inventoryLabelY = MiningDrillMenu.INVENTORY_Y - 11;
    }

    static void slot(GuiGraphicsExtractor g, int x, int y) {
        g.fill(x - 1, y - 1, x + 17, y + 17, SLOT);
        g.outline(x - 1, y - 1, 18, 18, 0xFF5A5040);
    }

    static void panel(GuiGraphicsExtractor g, int x, int y, int w, int h) {
        g.fill(x, y, x + w, y + h, PANEL);
        g.fill(x + 1, y + 1, x + w - 1, y + 15, PANEL_LIGHT);
        g.outline(x, y, w, h, EDGE);
        for (int i = 0; i < 6; i++) {   // hazard stripes along the bottom edge
            int sx = x + w - 6 - i * 10;
            g.fill(sx, y + h - 4, sx + 5, y + h - 1, 0xFF1E1A10);
        }
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(g, mouseX, mouseY, partialTick);
        int x = this.leftPos;
        int y = this.topPos;
        panel(g, x, y, this.imageWidth, this.imageHeight);

        slot(g, x + MiningDrillMenu.HEAD_X, y + MiningDrillMenu.HEAD_Y);
        int sockets = this.menu.sockets();
        for (int i = 0; i < DrillModules.MAX_SOCKETS; i++) {
            int sx = x + MiningDrillMenu.MODULE_X + i * 18;
            int sy = y + MiningDrillMenu.HEAD_Y;
            if (i < sockets) {
                slot(g, sx, sy);
                g.outline(sx - 1, sy - 1, 18, 18, 0xFF3FA0B0);
            } else {
                g.fill(sx - 1, sy - 1, sx + 17, sy + 17, 0xFF2A2518);
                g.fill(sx + 3, sy + 7, sx + 13, sy + 9, 0xFF3E3726);
            }
        }
        slot(g, x + MiningDrillMenu.FUEL_X, y + MiningDrillMenu.HEAD_Y);

        // fuel gauge: amber bar that fills to the right
        int gx = x + GAUGE_X;
        int gy = y + MiningDrillMenu.HEAD_Y + 2;
        g.fill(gx - 1, gy - 1, gx + GAUGE_W + 1, gy + 13, 0xFF000000);
        int filled = Math.round(GAUGE_W * this.menu.fuel() / (float) MiningDrillEntity.TANK);
        if (filled > 0) {
            g.fillGradient(gx, gy, gx + filled, gy + 12, 0xFFFFC040, 0xFFB85A0C);
        }
        for (int i = 1; i < 4; i++) {
            g.fill(gx + i * GAUGE_W / 4, gy, gx + i * GAUGE_W / 4 + 1, gy + 3, 0x80FFFFFF);
        }

        for (int row = 0; row < 4; row++) {
            for (int col = 0; col < 9; col++) {
                slot(g, x + 8 + col * 18, y + MiningDrillMenu.STORAGE_Y + row * 18);
            }
        }
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                slot(g, x + 8 + col * 18, y + MiningDrillMenu.INVENTORY_Y + row * 18);
            }
        }
        for (int col = 0; col < 9; col++) {
            slot(g, x + 8 + col * 18, y + MiningDrillMenu.INVENTORY_Y + 58);
        }
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        g.text(this.font, this.title, this.titleLabelX, this.titleLabelY, TEXT, false);
        g.text(this.font, this.playerInventoryTitle, this.inventoryLabelX, this.inventoryLabelY, TEXT, false);
        g.text(this.font, Component.translatable("gui.drillworks.hold"), 8, MiningDrillMenu.STORAGE_Y - 10, 0xFFB8AC88, false);
    }

    @Override
    protected void extractTooltip(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        super.extractTooltip(g, mouseX, mouseY);
        if (this.isHovering(GAUGE_X, MiningDrillMenu.HEAD_Y + 2, GAUGE_W, 12, mouseX, mouseY)) {
            g.setComponentTooltipForNextFrame(this.font, List.of(
                    Component.translatable("gui.drillworks.fuel", this.menu.fuel(), MiningDrillEntity.TANK),
                    Component.translatable("gui.drillworks.fuel_hint").withStyle(ChatFormatting.GRAY)), mouseX, mouseY);
        } else if (this.menu.sockets() == 0 && this.isHovering(MiningDrillMenu.MODULE_X, MiningDrillMenu.HEAD_Y,
                DrillModules.MAX_SOCKETS * 18, 16, mouseX, mouseY)) {
            g.setComponentTooltipForNextFrame(this.font, List.of(
                    Component.translatable("gui.drillworks.sockets_hint").withStyle(ChatFormatting.GRAY)), mouseX, mouseY);
        }
    }
}
