package com.afjan.juicer.client;

import com.afjan.juicer.Juicer;
import com.afjan.juicer.menu.MixerMenu;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;

public class MixerScreen extends AbstractContainerScreen<MixerMenu> {
    private static final Identifier TEXTURE = Juicer.id("textures/gui/container/mixer.png");
    private static final int TANK_X = 108;
    private static final int TANK_Y = 17;

    public MixerScreen(MixerMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }

    @Override
    protected void init() {
        super.init();
        this.addRenderableWidget(Button.builder(Component.literal("x"), button -> this.emptyTank())
                .bounds(this.leftPos + TANK_X + TankRenderer.WIDTH + 4, this.topPos + TANK_Y + TankRenderer.HEIGHT - 12, 12, 12)
                .tooltip(Tooltip.create(Component.translatable("gui.juicer.empty_tank")))
                .build());
    }

    private void emptyTank() {
        if (this.minecraft != null && this.minecraft.gameMode != null) {
            this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId, MixerMenu.BUTTON_EMPTY_TANK);
        }
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(graphics, mouseX, mouseY, partialTick);
        graphics.blit(RenderPipelines.GUI_TEXTURED, TEXTURE, this.leftPos, this.topPos, 0.0F, 0.0F, this.imageWidth, this.imageHeight, 256, 256);
        int progress = this.menu.getProgress();
        int max = this.menu.getMaxProgress();
        if (progress > 0 && max > 0) {
            int width = Math.min(22, progress * 22 / max + 1);
            graphics.blit(RenderPipelines.GUI_TEXTURED, TEXTURE, this.leftPos + 72, this.topPos + 35, 176.0F, 0.0F, width, 15, 256, 256);
        }
        TankRenderer.draw(graphics, TEXTURE, this.leftPos + TANK_X, this.topPos + TANK_Y,
                this.menu.getContents(), this.menu.getAmount(), this.menu.getCapacity());
    }

    @Override
    protected void extractTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        super.extractTooltip(graphics, mouseX, mouseY);
        if (this.isHovering(TANK_X, TANK_Y, TankRenderer.WIDTH, TankRenderer.HEIGHT, mouseX, mouseY)) {
            graphics.setComponentTooltipForNextFrame(this.font,
                    TankRenderer.tooltip(this.menu.getContents(), this.menu.getAmount(), this.menu.getCapacity()), mouseX, mouseY);
        }
    }
}
