package com.afjan.arsenal.client;

import java.util.ArrayList;
import java.util.List;

import com.afjan.arsenal.craft.Blueprints;
import com.afjan.arsenal.craft.Blueprints.Blueprint;
import com.afjan.arsenal.craft.Blueprints.Need;
import com.afjan.arsenal.gun.Attachment;
import com.afjan.arsenal.menu.WeaponWorkbenchMenu;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * The Weapon Workbench window: a browsable catalogue of everything the mod can build on the left, the selected
 * blueprint and its bill of materials on the right, and the attachment clamp along the bottom.
 *
 * <p>Drawn entirely from coloured rectangles rather than a GUI sheet, so there is no texture to keep in step with
 * the layout.
 */
public class WeaponWorkbenchScreen extends AbstractContainerScreen<WeaponWorkbenchMenu> {
    private static final int GRID_X = 8;
    private static final int GRID_Y = 40;
    private static final int GRID_COLS = 6;
    private static final int GRID_ROWS = 4;
    private static final int CELL = 18;
    private static final int SCROLL_X = GRID_X + GRID_COLS * CELL + 2;
    private static final int PANEL_X = 134;
    private static final int NEED_X = PANEL_X + 2;
    private static final int NEED_Y = 72;
    private static final int NEED_COLS = 4;

    private static final int COLOR_PANEL = 0xFF2B2F27;
    private static final int COLOR_PANEL_LIGHT = 0xFF3A4034;
    private static final int COLOR_SLOT = 0xFF14160F;
    private static final int COLOR_EDGE = 0xFF6E7A5E;
    private static final int COLOR_SELECTED = 0xFF8FA84B;

    private Blueprints.Category category = Blueprints.Category.WEAPONS;
    private final List<Blueprint> visible = new ArrayList<>();
    private int selected;
    private int scroll;
    private Button buildButton;
    private Button bulkButton;

    public WeaponWorkbenchScreen(WeaponWorkbenchMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, 256, 250);
    }

    @Override
    protected void init() {
        super.init();
        this.titleLabelX = 8;
        this.titleLabelY = 6;
        this.inventoryLabelX = 47;
        this.inventoryLabelY = 158;

        Blueprints.Category[] categories = Blueprints.Category.values();
        int tabWidth = (GRID_COLS * CELL + 12) / categories.length; // the tabs share the width over the grid
        for (int i = 0; i < categories.length; i++) {
            Blueprints.Category tab = categories[i];
            this.addRenderableWidget(Button.builder(Component.translatable(tab.translationKey() + ".short"),
                            button -> this.select(tab))
                    .bounds(this.leftPos + 8 + i * tabWidth, this.topPos + 18, tabWidth, 18)
                    .tooltip(net.minecraft.client.gui.components.Tooltip.create(Component.translatable(tab.translationKey())))
                    .build());
        }
        this.buildButton = this.addRenderableWidget(Button.builder(Component.translatable("gui.arsenal.build"),
                        button -> this.build(1))
                .bounds(this.leftPos + PANEL_X, this.topPos + 112, 78, 18)
                .build());
        this.bulkButton = this.addRenderableWidget(Button.builder(
                        Component.literal("x" + WeaponWorkbenchMenu.BULK_COUNT), button -> this.build(WeaponWorkbenchMenu.BULK_COUNT))
                .bounds(this.leftPos + PANEL_X + 80, this.topPos + 112, 34, 18)
                .tooltip(net.minecraft.client.gui.components.Tooltip.create(Component.translatable("gui.arsenal.build.hint")))
                .build());
        this.select(this.category);
    }

    private void select(Blueprints.Category category) {
        this.category = category;
        this.visible.clear();
        for (Blueprint blueprint : Blueprints.ALL) {
            if (blueprint.category() == category) {
                this.visible.add(blueprint);
            }
        }
        this.selected = 0;
        this.scroll = 0;
    }

    private Blueprint current() {
        return this.selected >= 0 && this.selected < this.visible.size() ? this.visible.get(this.selected) : null;
    }

    private void build(int times) {
        Blueprint blueprint = this.current();
        if (blueprint == null || this.minecraft == null || this.minecraft.gameMode == null) {
            return;
        }
        int id = Blueprints.indexOf(blueprint);
        this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId,
                times > 1 ? id + WeaponWorkbenchMenu.BULK : id);
        if (this.minecraft.player != null) {
            this.minecraft.player.playSound(SoundEvents.ANVIL_USE, 0.5F, 1.4F);
        }
    }

    private int maxScroll() {
        return Math.max(0, (this.visible.size() + GRID_COLS - 1) / GRID_COLS - GRID_ROWS);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        int index = this.cellAt(event.x(), event.y());
        if (index >= 0) {
            this.selected = index;
            if (this.minecraft != null && this.minecraft.player != null) {
                this.minecraft.player.playSound(SoundEvents.UI_BUTTON_CLICK.value(), 0.3F, 1.6F);
            }
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (this.inGrid(mouseX, mouseY) && this.maxScroll() > 0) {
            this.scroll = Math.max(0, Math.min(this.maxScroll(), this.scroll - (int) Math.signum(scrollY)));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    private boolean inGrid(double mouseX, double mouseY) {
        double x = mouseX - this.leftPos;
        double y = mouseY - this.topPos;
        return x >= GRID_X && x < GRID_X + GRID_COLS * CELL && y >= GRID_Y && y < GRID_Y + GRID_ROWS * CELL;
    }

    /** @return the blueprint index under the cursor, or -1. */
    private int cellAt(double mouseX, double mouseY) {
        if (!this.inGrid(mouseX, mouseY)) {
            return -1;
        }
        int col = (int) ((mouseX - this.leftPos - GRID_X) / CELL);
        int row = (int) ((mouseY - this.topPos - GRID_Y) / CELL);
        int index = (this.scroll + row) * GRID_COLS + col;
        return index < this.visible.size() ? index : -1;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(graphics, mouseX, mouseY, partialTick);
        int x = this.leftPos;
        int y = this.topPos;

        graphics.fill(x, y, x + this.imageWidth, y + this.imageHeight, COLOR_PANEL);
        graphics.fill(x + 1, y + 1, x + this.imageWidth - 1, y + 16, COLOR_PANEL_LIGHT);
        graphics.outline(x, y, this.imageWidth, this.imageHeight, COLOR_EDGE);

        // catalogue
        graphics.fill(x + GRID_X - 1, y + GRID_Y - 1, x + GRID_X + GRID_COLS * CELL + 1, y + GRID_Y + GRID_ROWS * CELL + 1, COLOR_SLOT);
        for (int row = 0; row < GRID_ROWS; row++) {
            for (int col = 0; col < GRID_COLS; col++) {
                int index = (this.scroll + row) * GRID_COLS + col;
                int cx = x + GRID_X + col * CELL;
                int cy = y + GRID_Y + row * CELL;
                if (index == this.selected) {
                    graphics.fill(cx, cy, cx + CELL - 1, cy + CELL - 1, COLOR_SELECTED);
                }
                if (index < this.visible.size()) {
                    graphics.item(this.visible.get(index).resultStack(), cx + 1, cy + 1);
                }
            }
        }
        this.scrollbar(graphics, x, y);

        // selected blueprint
        Blueprint blueprint = this.current();
        if (blueprint != null) {
            ItemStack result = blueprint.resultStack();
            graphics.fill(x + PANEL_X, y + GRID_Y - 1, x + PANEL_X + 114, y + GRID_Y + 19, COLOR_SLOT);
            graphics.item(result, x + PANEL_X + 2, y + GRID_Y + 1);
            graphics.itemDecorations(this.font, result, x + PANEL_X + 2, y + GRID_Y + 1);
            graphics.text(this.font, this.font.plainSubstrByWidth(result.getHoverName().getString(), 90),
                    x + PANEL_X + 22, y + GRID_Y + 6, 0xFFE8E8D8, false);
            graphics.text(this.font, Component.translatable("gui.arsenal.needs"), x + NEED_X, y + 62, 0xFF9AA888, false);
            this.needs(graphics, x, y, blueprint);

            boolean affordable = this.minecraft != null && this.minecraft.player != null
                    && Blueprints.affordable(this.minecraft.player, blueprint, 1) > 0;
            this.buildButton.active = affordable;
            this.bulkButton.active = affordable;
        } else {
            this.buildButton.active = false;
            this.bulkButton.active = false;
        }

        // attachment clamp
        graphics.text(this.font, Component.translatable("gui.arsenal.modify"), x + 8, y + 129, 0xFF9AA888, false);
        for (Slot slot : this.menu.slots) {
            graphics.fill(x + slot.x - 1, y + slot.y - 1, x + slot.x + 17, y + slot.y + 17, COLOR_SLOT);
        }
        Attachment.Slot[] kinds = Attachment.Slot.values();
        for (int i = 0; i < kinds.length; i++) {
            int cx = x + WeaponWorkbenchMenu.ATTACH_X + i * 22;
            graphics.outline(cx - 1, y + WeaponWorkbenchMenu.ATTACH_Y - 1, 18, 18, 0xFF4C5440);
        }
    }

    private void scrollbar(GuiGraphicsExtractor graphics, int x, int y) {
        int height = GRID_ROWS * CELL;
        graphics.fill(x + SCROLL_X, y + GRID_Y - 1, x + SCROLL_X + 8, y + GRID_Y + height + 1, COLOR_SLOT);
        int max = this.maxScroll();
        int handle = max == 0 ? height : Math.max(12, height / (max + GRID_ROWS) * GRID_ROWS);
        int offset = max == 0 ? 0 : (height - handle) * this.scroll / max;
        graphics.fill(x + SCROLL_X + 1, y + GRID_Y + offset, x + SCROLL_X + 7, y + GRID_Y + offset + handle, COLOR_EDGE);
    }

    private void needs(GuiGraphicsExtractor graphics, int x, int y, Blueprint blueprint) {
        List<Need> needs = blueprint.needs();
        for (int i = 0; i < needs.size(); i++) {
            Need need = needs.get(i);
            int cx = x + NEED_X + (i % NEED_COLS) * CELL;
            int cy = y + NEED_Y + (i / NEED_COLS) * CELL;
            graphics.fill(cx, cy, cx + CELL - 1, cy + CELL - 1, COLOR_SLOT);
            graphics.item(need.display(), cx + 1, cy + 1);
            int have = this.minecraft == null || this.minecraft.player == null ? 0
                    : Blueprints.count(this.minecraft.player.getInventory(), need);
            String text = String.valueOf(need.count());
            graphics.text(this.font, text, cx + CELL - 2 - this.font.width(text), cy + 9,
                    have >= need.count() ? 0xFF8FE07A : 0xFFE06A6A, true);
        }
    }

    @Override
    protected void extractTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        super.extractTooltip(graphics, mouseX, mouseY);
        if (this.minecraft == null || this.minecraft.player == null) {
            return;
        }
        int index = this.cellAt(mouseX, mouseY);
        if (index >= 0) {
            graphics.setTooltipForNextFrame(this.font, this.visible.get(index).resultStack(), mouseX, mouseY);
            return;
        }
        Blueprint blueprint = this.current();
        if (blueprint == null) {
            return;
        }
        List<Need> needs = blueprint.needs();
        for (int i = 0; i < needs.size(); i++) {
            int cx = this.leftPos + NEED_X + (i % NEED_COLS) * CELL;
            int cy = this.topPos + NEED_Y + (i / NEED_COLS) * CELL;
            if (mouseX < cx || mouseX >= cx + CELL || mouseY < cy || mouseY >= cy + CELL) {
                continue;
            }
            Need need = needs.get(i);
            int have = Blueprints.count(this.minecraft.player.getInventory(), need);
            graphics.setComponentTooltipForNextFrame(this.font, List.of(
                            need.display().getHoverName(),
                            Component.translatable("gui.arsenal.have", have, need.count())
                                    .withStyle(have >= need.count() ? ChatFormatting.GREEN : ChatFormatting.RED)),
                    mouseX, mouseY);
            return;
        }
    }
}
