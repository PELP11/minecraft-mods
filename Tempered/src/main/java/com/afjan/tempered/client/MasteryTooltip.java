package com.afjan.tempered.client;

import com.afjan.tempered.event.BrokenTools;
import com.afjan.tempered.mastery.LangKeys;
import com.afjan.tempered.mastery.Mastery;
import com.afjan.tempered.mastery.Milestone;
import com.afjan.tempered.mastery.Perk;
import com.afjan.tempered.mastery.Track;
import com.afjan.tempered.mastery.Tracks;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;

import java.util.List;
import java.util.Map;

/** Mastery level, the next milestone with progress, and (with Shift) the tool's perks. */
final class MasteryTooltip {
    private MasteryTooltip() {
    }

    static void onTooltip(ItemTooltipEvent event) {
        ItemStack stack = event.getItemStack();
        List<Component> lines = event.getToolTip();
        if (BrokenTools.isBrokenTool(stack)) {
            lines.add(Math.min(1, lines.size()), Component.translatable(LangKeys.TIP_BROKEN).withStyle(ChatFormatting.RED, ChatFormatting.BOLD));
        }
        Track track = Tracks.get(stack);
        if (track == null) return;
        Mastery mastery = Mastery.of(stack);
        int level = track.level(mastery);

        lines.add(Component.empty());
        if (level == 0 && mastery == null) {
            lines.add(Component.translatable(LangKeys.TIP_UNTOUCHED).withStyle(ChatFormatting.GRAY));
        } else {
            lines.add(Component.translatable(level >= Track.MAX_LEVEL ? LangKeys.TIP_MASTERED : LangKeys.TIP_MASTERY, MasteryText.roman(level))
                    .append(" " + MasteryText.stars(level))
                    .withStyle(level >= Track.MAX_LEVEL ? ChatFormatting.LIGHT_PURPLE : ChatFormatting.GOLD));
        }
        if (level < Track.MAX_LEVEL) {
            Milestone next = track.milestones.get(level);
            lines.add(Component.translatable(LangKeys.TIP_NEXT, MasteryText.roman(level + 1)).withStyle(ChatFormatting.GRAY));
            for (Milestone.Req req : next.reqs()) {
                boolean done = MasteryText.isDone(req, mastery);
                lines.add(Component.literal("  ").append(MasteryText.requirementWithProgress(track.kind, req, mastery == null ? Mastery.EMPTY : mastery))
                        .withStyle(done ? ChatFormatting.GREEN : ChatFormatting.GRAY));
            }
        }
        Map<Perk, Double> perks = track.perks(level);
        if (!perks.isEmpty()) {
            if (Minecraft.getInstance().hasShiftDown()) {
                lines.add(Component.translatable(LangKeys.TIP_PERKS).withStyle(ChatFormatting.GRAY));
                perks.forEach((perk, value) -> lines.add(Component.literal("  ").append(MasteryText.perk(track.kind, perk, value))
                        .withStyle(MasteryText.perkFormatting(perk))));
            } else {
                lines.add(Component.translatable(LangKeys.TIP_HOLD_SHIFT).withStyle(ChatFormatting.DARK_GRAY));
            }
            if (perks.containsKey(Perk.VEIN) || perks.containsKey(Perk.EXCAVATE) || perks.containsKey(Perk.TIMBER) || perks.containsKey(Perk.REAPER)) {
                lines.add(Component.translatable(LangKeys.TIP_SNEAK).withStyle(ChatFormatting.DARK_GRAY));
            }
        }
        lines.add(Component.translatable(LangKeys.TIP_OVERVIEW, TemperedClient.OVERVIEW.getTranslatedKeyMessage()).withStyle(ChatFormatting.DARK_GRAY));
    }
}
