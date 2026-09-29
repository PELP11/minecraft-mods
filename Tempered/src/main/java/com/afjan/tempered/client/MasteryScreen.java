package com.afjan.tempered.client;

import com.afjan.tempered.mastery.Kind;
import com.afjan.tempered.mastery.LangKeys;
import com.afjan.tempered.mastery.Mastery;
import com.afjan.tempered.mastery.Milestone;
import com.afjan.tempered.mastery.Perk;
import com.afjan.tempered.mastery.Stat;
import com.afjan.tempered.mastery.Tier;
import com.afjan.tempered.mastery.Track;
import com.afjan.tempered.mastery.Tracks;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The overview: every tool (7 materials x 6 types, plus bow, crossbow, trident, mace, shears and fishing rod) on
 * the left, the selected tool's milestones (20 for main tools, 5 for the others) with progress and rewards on the
 * right, scrolled to the next one. Drawn from plain rectangles and text, no GUI texture.
 */
public final class MasteryScreen extends Screen {
    private static final int CELL = 20;
    private static final Kind[] ROWS = {Kind.PICKAXE, Kind.AXE, Kind.SHOVEL, Kind.HOE, Kind.SWORD, Kind.SPEAR};
    private static final Kind[] SPECIALS = {Kind.BOW, Kind.CROSSBOW, Kind.TRIDENT, Kind.MACE, Kind.SHEARS, Kind.FISHING_ROD};

    private Track selected;
    private Map<Track, ItemStack> owned = Map.of();
    private int left, top, panelWidth, panelHeight;
    private int gridX, gridY, specialsY, detailX, detailY, detailWidth, detailHeight, listY, listHeight;
    private double scroll;
    private int contentHeight;
    /** Scroll the list to the milestone being worked on at the next frame (after opening or selecting). */
    private boolean jumpToCurrent = true;

    /** One row of the milestone list. */
    private record Line(@Nullable FormattedCharSequence text, int color, int indent, float progress, int height,
                        @Nullable Component tooltip, int level) {
    }

    public MasteryScreen(Track initial) {
        super(Component.translatable(LangKeys.GUI_TITLE));
        this.selected = initial;
    }

    @Override
    protected void init() {
        panelWidth = Math.min(width - 12, 480);
        panelHeight = Math.min(height - 12, 292);
        left = (width - panelWidth) / 2;
        top = (height - panelHeight) / 2;
        gridX = left + 10;
        gridY = top + 34;
        specialsY = gridY + ROWS.length * CELL + 14;
        detailX = gridX + Tier.MATERIALS.length * CELL + 12;
        detailY = top + 24;
        detailWidth = left + panelWidth - 10 - detailX;
        detailHeight = top + panelHeight - 8 - detailY;
        listY = detailY + 30;
        listHeight = detailY + detailHeight - listY;
        if (minecraft != null && minecraft.player != null) owned = OwnedTools.best(minecraft.player);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void tick() {
        if (minecraft != null && minecraft.player != null) owned = OwnedTools.best(minecraft.player);
    }

    // ------------------------------------------------------------------------------------------------ drawing

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partial) {
        g.fill(left, top, left + panelWidth, top + panelHeight, 0xF0101216);
        g.fillGradient(left, top, left + panelWidth, top + 20, 0xFF2A2418, 0xFF15161A);
        g.outline(left, top, panelWidth, panelHeight, 0xFF6E6250);
        g.text(font, title, left + 10, top + 6, MasteryText.GOLD);

        g.text(font, Component.translatable(LangKeys.GUI_TOOLS), gridX, gridY - 11, MasteryText.GRAY);
        for (int row = 0; row < ROWS.length; row++) {
            for (int col = 0; col < Tier.MATERIALS.length; col++) {
                Track track = Tracks.of(ROWS[row], Tier.MATERIALS[col]);
                if (track != null) cell(g, track, gridX + col * CELL, gridY + row * CELL, mouseX, mouseY);
            }
        }
        g.text(font, Component.translatable(LangKeys.GUI_SPECIAL), gridX, specialsY - 11, MasteryText.GRAY);
        for (int i = 0; i < SPECIALS.length; i++) {
            Track track = Tracks.of(SPECIALS[i], Tier.SPECIAL);
            if (track != null) cell(g, track, gridX + i * CELL, specialsY, mouseX, mouseY);
        }
        help(g);
        g.fill(detailX - 6, detailY, detailX - 5, detailY + detailHeight, 0xFF3A352C);
        details(g, mouseX, mouseY);
        super.extractRenderState(g, mouseX, mouseY, partial);
    }

    private void cell(GuiGraphicsExtractor g, Track track, int x, int y, int mouseX, int mouseY) {
        ItemStack stack = owned.get(track);
        int level = stack == null ? 0 : track.level(Mastery.of(stack));
        g.fill(x, y, x + CELL - 2, y + CELL - 2, stack == null ? 0xFF1A1B1F : 0xFF2B2720);
        int border = track == selected ? MasteryText.WHITE
                : level >= track.maxLevel() ? MasteryText.MASTERED : level > 0 ? 0xFFA07C2C : 0xFF34363C;
        g.outline(x, y, CELL - 2, CELL - 2, border);
        ItemStack shown = stack != null ? stack : track.item.getDefaultInstance();
        g.item(shown, x + 1, y + 1);
        g.itemDecorations(font, shown, x + 1, y + 1);
        if (stack == null) g.fill(x + 1, y + 1, x + CELL - 3, y + CELL - 3, 0x80101216);
        if (inside(mouseX, mouseY, x, y, CELL - 2, CELL - 2)) g.setTooltipForNextFrame(font, shown, mouseX, mouseY);
    }

    private void help(GuiGraphicsExtractor g) {
        int y = specialsY + CELL + 4;
        int width = Tier.MATERIALS.length * CELL - 2;
        int bottom = top + panelHeight - 6;
        for (String key : new String[]{LangKeys.GUI_HELP_1, LangKeys.GUI_HELP_2, LangKeys.GUI_HELP_3}) {
            for (FormattedCharSequence line : font.split(Component.translatable(key), width)) {
                if (y + 9 > bottom) return;
                g.text(font, line, gridX, y, MasteryText.DARK, false);
                y += 9;
            }
            y += 3;
        }
    }

    private void details(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        if (selected == null) return;
        ItemStack stack = owned.get(selected);
        Mastery mastery = stack == null ? null : Mastery.of(stack);
        if (stack != null && mastery == null) mastery = Mastery.EMPTY;
        int level = stack == null ? 0 : selected.level(mastery);

        ItemStack shown = stack != null ? stack : selected.item.getDefaultInstance();
        g.item(shown, detailX, detailY + 2);
        g.text(font, Component.translatable(LangKeys.GUI_MILESTONES, MasteryText.toolName(selected)), detailX + 20, detailY + 1, MasteryText.WHITE);
        Component sub = stack == null
                ? Component.translatable(LangKeys.GUI_NONE_OWNED)
                : Component.translatable(LangKeys.GUI_YOURS, MasteryText.roman(level), level, selected.maxLevel());
        List<FormattedCharSequence> subLines = font.split(sub, detailWidth - 20);
        if (!subLines.isEmpty()) g.text(font, subLines.getFirst(), detailX + 20, detailY + 12, stack == null ? MasteryText.GRAY : MasteryText.levelColor(selected, level), false);
        levelBar(g, level);

        List<Line> lines = buildLines(mastery, level);
        contentHeight = lines.stream().mapToInt(Line::height).sum();
        if (jumpToCurrent) {
            jumpToCurrent = false;
            int target = Math.min(level + 1, selected.maxLevel());
            int offset = 0;
            for (Line line : lines) {
                if (line.level() >= target) break;
                offset += line.height();
            }
            scroll = offset;
        }
        scroll = Mth.clamp(scroll, 0, Math.max(0, contentHeight - listHeight));

        g.enableScissor(detailX, listY, detailX + detailWidth, listY + listHeight);
        int y = listY - (int) scroll;
        Component hoverTip = null;
        for (Line line : lines) {
            if (y + line.height() >= listY && y <= listY + listHeight) {
                if (line.progress() >= 0) {
                    int barWidth = Math.min(120, detailWidth - line.indent() - 4);
                    int x0 = detailX + line.indent();
                    g.fill(x0, y + 1, x0 + barWidth, y + 4, 0xFF2A2A2A);
                    g.fill(x0, y + 1, x0 + Math.round(barWidth * line.progress()), y + 4, MasteryText.GOLD);
                } else if (line.text() != null) {
                    g.text(font, line.text(), detailX + line.indent(), y, line.color(), false);
                    if (line.tooltip() != null && inside(mouseX, mouseY, detailX, y, detailWidth, line.height())
                            && mouseY >= listY && mouseY < listY + listHeight) {
                        hoverTip = line.tooltip();
                    }
                }
            }
            y += line.height();
        }
        g.disableScissor();

        if (contentHeight > listHeight) {
            int trackX = detailX + detailWidth - 2;
            int thumb = Math.max(12, listHeight * listHeight / contentHeight);
            int thumbY = listY + (int) ((listHeight - thumb) * (scroll / (contentHeight - listHeight)));
            g.fill(trackX, listY, trackX + 2, listY + listHeight, 0xFF26262A);
            g.fill(trackX, thumbY, trackX + 2, thumbY + thumb, 0xFF8A7A5A);
        }
        if (hoverTip != null) {
            g.setComponentTooltipForNextFrame(font, List.of(hoverTip), mouseX, mouseY);
        }
    }

    /** One segment per level under the header: gold when reached, pink once mastered. */
    private void levelBar(GuiGraphicsExtractor g, int level) {
        int max = selected.maxLevel();
        int y = listY - 7;
        for (int i = 0; i < max; i++) {
            int x0 = detailX + i * detailWidth / max;
            int x1 = detailX + (i + 1) * detailWidth / max - 1;
            int color = i >= level ? 0xFF34363C : level >= max ? MasteryText.MASTERED : MasteryText.GOLD;
            g.fill(x0, y, x1, y + 3, color);
        }
    }

    private List<Line> buildLines(@Nullable Mastery mastery, int level) {
        List<Line> lines = new ArrayList<>();
        int textWidth = detailWidth - 6;
        for (Milestone milestone : selected.milestones) {
            int l = milestone.level();
            boolean done = l <= level;
            boolean current = l == level + 1;
            int headColor = done ? (l == selected.maxLevel() ? MasteryText.MASTERED : MasteryText.GREEN) : current ? MasteryText.GOLD : MasteryText.GRAY;
            Component head = Component.translatable(LangKeys.GUI_LEVEL, MasteryText.roman(l));
            if (done) head = head.copy().append("  ✔ ").append(Component.translatable(LangKeys.GUI_DONE));
            lines.add(new Line(head.getVisualOrderText(), headColor, 0, -1, 11, null, l));
            if (current && mastery != null) lines.add(new Line(null, 0, 8, milestone.progress(mastery), 6, null, l));
            for (Milestone.Req req : milestone.reqs()) {
                boolean reqDone = MasteryText.isDone(req, mastery);
                Component text = Component.literal("• ").append(MasteryText.requirementWithProgress(selected.kind, req, done ? null : mastery));
                int color = reqDone || done ? MasteryText.GREEN : current ? MasteryText.WHITE : MasteryText.GRAY;
                Component tip = req.stat() == Stat.ELITE ? Component.translatable(LangKeys.GUI_HELP_ELITE)
                        : req.stat() == Stat.RAIDERS ? Component.translatable(LangKeys.GUI_HELP_RAIDERS) : null;
                for (FormattedCharSequence part : font.split(text, textWidth - 8)) lines.add(new Line(part, color, 8, -1, 10, tip, l));
            }
            for (Perk perk : milestone.gained()) {
                Component text = Component.literal("+ ").append(MasteryText.perk(selected.kind, perk, milestone.totals().get(perk)));
                int color = done || current ? MasteryText.perkColor(perk) : darker(MasteryText.perkColor(perk));
                for (FormattedCharSequence part : font.split(text, textWidth - 8)) lines.add(new Line(part, color, 8, -1, 10, null, l));
            }
            lines.add(new Line(null, 0, 0, -1, 6, null, l));
        }
        return lines;
    }

    private static int darker(int argb) {
        int r = (argb >> 16 & 0xFF) * 3 / 5, gr = (argb >> 8 & 0xFF) * 3 / 5, b = (argb & 0xFF) * 3 / 5;
        return 0xFF000000 | r << 16 | gr << 8 | b;
    }

    private static boolean inside(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    // ------------------------------------------------------------------------------------------------ input

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double mx = event.x(), my = event.y();
        for (int row = 0; row < ROWS.length; row++) {
            for (int col = 0; col < Tier.MATERIALS.length; col++) {
                if (inside(mx, my, gridX + col * CELL, gridY + row * CELL, CELL - 2, CELL - 2)) {
                    return select(Tracks.of(ROWS[row], Tier.MATERIALS[col]));
                }
            }
        }
        for (int i = 0; i < SPECIALS.length; i++) {
            if (inside(mx, my, gridX + i * CELL, specialsY, CELL - 2, CELL - 2)) return select(Tracks.of(SPECIALS[i], Tier.SPECIAL));
        }
        return super.mouseClicked(event, doubleClick);
    }

    private boolean select(@Nullable Track track) {
        if (track == null) return false;
        selected = track;
        jumpToCurrent = true;
        if (minecraft != null) {
            minecraft.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                    net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1.0F));
        }
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (inside(mouseX, mouseY, detailX, listY, detailWidth, listHeight) && contentHeight > listHeight) {
            scroll = Mth.clamp(scroll - scrollY * 20, 0, contentHeight - listHeight);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (TemperedClient.OVERVIEW.matches(event)) {
            onClose();
            return true;
        }
        return super.keyPressed(event);
    }
}
