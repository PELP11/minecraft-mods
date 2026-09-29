package com.afjan.tempered.client;

import com.afjan.tempered.event.BrokenTools;
import com.afjan.tempered.mastery.Mastery;
import com.afjan.tempered.mastery.Track;
import com.afjan.tempered.mastery.Tracks;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.client.IItemDecorator;

/**
 * Drawn on item icons in every inventory: one pip per mastery level along the top edge (pink when mastered)
 * and cracks over broken tools.
 */
final class MasteryDecorator implements IItemDecorator {
    static final MasteryDecorator INSTANCE = new MasteryDecorator();

    private static final int[][] CRACK = {{12, 1}, {11, 2}, {11, 3}, {10, 4}, {9, 5}, {9, 6}, {8, 7}, {7, 8}, {7, 9}, {6, 10},
            {5, 11}, {5, 12}, {4, 13}, {3, 14}, {10, 6}, {11, 7}, {12, 7}, {6, 9}, {5, 8}, {4, 8}};

    @Override
    public boolean render(GuiGraphicsExtractor graphics, Font font, ItemStack stack, int x, int y) {
        if (BrokenTools.isBrokenTool(stack)) {
            graphics.fill(x, y, x + 16, y + 16, 0x40FF2020);
            for (int[] p : CRACK) graphics.fill(x + p[0], y + p[1], x + p[0] + 1, y + p[1] + 1, 0xE0400000);
        }
        Track track = Tracks.get(stack);
        if (track == null) return false;
        Mastery mastery = Mastery.of(stack);
        int level = track.level(mastery);
        if (level <= 0) return false;
        int color = level >= Track.MAX_LEVEL ? MasteryText.MASTERED : MasteryText.GOLD;
        for (int i = 0; i < level; i++) {
            int px = x + 1 + i * 3;
            graphics.fill(px, y + 1, px + 2, y + 3, color);
            graphics.fill(px, y + 3, px + 2, y + 4, 0xA0000000);
        }
        return false;
    }
}
