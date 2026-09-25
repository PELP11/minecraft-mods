package com.afjan.stonesift.client;

import java.util.List;

import com.afjan.stonesift.machine.MachineMenu;
import com.afjan.stonesift.machine.MachineType;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/** Every machine screen, drawn from rectangles (no GUI textures): slots, gauges and a status line from MachineType. */
public class MachineScreen extends AbstractContainerScreen<MachineMenu> {
    private static final int PANEL = 0xFF2F3336;
    private static final int PANEL_LIGHT = 0xFF3F454A;
    private static final int EDGE = 0xFF8C9AA3;
    private static final int SLOT = 0xFF14171A;
    private static final int TEXT = 0xFFE6ECEF;

    public MachineScreen(MachineMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, 176, menu.type().height());
    }

    private static void slot(GuiGraphicsExtractor g, int x, int y, boolean output) {
        g.fill(x - 1, y - 1, x + 17, y + 17, SLOT);
        g.outline(x - 1, y - 1, 18, 18, output ? 0xFF6A8A5A : 0xFF5A646C);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(g, mouseX, mouseY, partialTick);
        int x = this.leftPos;
        int y = this.topPos;
        MachineType type = this.menu.type();
        g.fill(x, y, x + this.imageWidth, y + this.imageHeight, PANEL);
        g.fill(x + 1, y + 1, x + this.imageWidth - 1, y + 15, PANEL_LIGHT);
        g.outline(x, y, this.imageWidth, this.imageHeight, EDGE);
        for (int i = 0; i < type.slots().size(); i++) {
            MachineType.SlotDef def = type.slots().get(i);
            slot(g, x + def.x(), y + def.y(), def.filter() == null);
        }
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                slot(g, x + 8 + col * 18, y + 84 + row * 18, false);
            }
        }
        for (int col = 0; col < 9; col++) {
            slot(g, x + 8 + col * 18, y + 142, false);
        }
        for (MachineType.Bar bar : type.bars()) {
            int value = bar.energy() ? this.menu.energy(bar.value()) : this.menu.get(bar.value());
            int max = bar.max() < 0 ? -bar.max() : this.menu.get(bar.max());
            float f = max <= 0 ? 0.0F : Math.min(1.0F, value / (float) max);
            int bx = x + bar.x();
            int by = y + bar.y();
            g.fill(bx - 1, by - 1, bx + bar.w() + 1, by + bar.h() + 1, 0xFF000000);
            if (bar.vertical()) {
                int h = Math.round(bar.h() * f);
                g.fillGradient(bx, by + bar.h() - h, bx + bar.w(), by + bar.h(), bar.color() | 0xFF000000, darker(bar.color()));
            } else {
                g.fill(bx, by, bx + Math.round(bar.w() * f), by + bar.h(), bar.color());
            }
        }
    }

    private static int darker(int argb) {
        int r = (argb >> 16 & 0xFF) * 2 / 3;
        int gr = (argb >> 8 & 0xFF) * 2 / 3;
        int b = (argb & 0xFF) * 2 / 3;
        return 0xFF000000 | r << 16 | gr << 8 | b;
    }

    /** One status line per machine, from its synced data. */
    private Component status() {
        MachineType type = this.menu.type();
        return switch (type) {
            case ROCK_FORMER -> Component.translatable("gui.stonesift.former." + this.menu.get(2), this.menu.get(3));
            case DEEP_DRILL -> Component.translatable("gui.stonesift.drill." + this.menu.get(0), this.menu.get(1), this.menu.get(2));
            case FLOTATION_CELL -> this.menu.get(4) == 0 ? Component.translatable("gui.stonesift.no_water")
                    : Component.translatable("gui.stonesift.reagent", this.menu.get(5));
            case SHAKER_SIEVE -> Component.translatable("gui.stonesift.shaker_hint");
            case GRINDER -> Component.translatable("gui.stonesift.grinder_hint", this.menu.get(2));
            case COAL_GENERATOR -> Component.translatable("gui.stonesift.fe", this.menu.energy(2), com.afjan.stonesift.machine.CoalGeneratorBlockEntity.CAPACITY);
        };
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        g.text(this.font, this.title, this.titleLabelX, this.titleLabelY, TEXT, false);
        g.text(this.font, this.playerInventoryTitle, this.inventoryLabelX, this.inventoryLabelY, TEXT, false);
        Component status = this.status();
        g.text(this.font, this.font.plainSubstrByWidth(status.getString(), 150), 22, 72, 0xFFB8C4CA, false);
    }

    @Override
    protected void extractTooltip(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        super.extractTooltip(g, mouseX, mouseY);
        for (MachineType.Bar bar : this.menu.type().bars()) {
            if (bar.energy() && this.isHovering(bar.x(), bar.y(), bar.w(), bar.h(), mouseX, mouseY)) {
                g.setComponentTooltipForNextFrame(this.font, List.of(Component.translatable("gui.stonesift.fe",
                        this.menu.energy(bar.value()), -bar.max())), mouseX, mouseY);
            }
        }
    }
}
