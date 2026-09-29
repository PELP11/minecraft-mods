package com.afjan.tempered.client;

import com.afjan.tempered.mastery.Kind;
import com.afjan.tempered.mastery.LangKeys;
import com.afjan.tempered.mastery.Mastery;
import com.afjan.tempered.mastery.Milestone;
import com.afjan.tempered.mastery.Perk;
import com.afjan.tempered.mastery.Track;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import org.jspecify.annotations.Nullable;

/** Shared wording for tooltips and the overview screen. */
final class MasteryText {
    static final int GOLD = 0xFFFFC94A;
    static final int GREEN = 0xFF6BE36B;
    static final int AQUA = 0xFF63E3FF;
    static final int GRAY = 0xFF9A9A9A;
    static final int DARK = 0xFF5E5E5E;
    static final int WHITE = 0xFFFFFFFF;
    static final int RED = 0xFFFF5A5A;
    static final int MASTERED = 0xFFFF7BFF;

    private MasteryText() {
    }

    static MutableComponent roman(int level) {
        return Component.translatable("mastery.tempered.level." + Math.max(0, Math.min(Track.HIGHEST_LEVEL, level)));
    }

    /** "Mastery VII (7/20)", or "Mastery XX - mastered". */
    static MutableComponent masteryLine(Track track, int level) {
        return level >= track.maxLevel()
                ? Component.translatable(LangKeys.TIP_MASTERED, roman(level))
                : Component.translatable(LangKeys.TIP_MASTERY, roman(level), level, track.maxLevel());
    }

    static MutableComponent requirement(Kind kind, Milestone.Req req) {
        return Component.translatable(req.key(kind), req.amount());
    }

    /** "Mine 360 blocks (212/360)" when there is a tool to measure, else just the requirement. */
    static MutableComponent requirementWithProgress(Kind kind, Milestone.Req req, @Nullable Mastery mastery) {
        MutableComponent text = requirement(kind, req);
        if (mastery == null) return text;
        int have = Math.min(req.amount(), mastery.get(req.stat()));
        return text.append(Component.literal(" (" + have + "/" + req.amount() + ")"));
    }

    static boolean isDone(Milestone.Req req, @Nullable Mastery mastery) {
        return mastery != null && mastery.get(req.stat()) >= req.amount();
    }

    static MutableComponent perk(Kind kind, Perk perk, double value) {
        return perk.describe(kind, value);
    }

    static int perkColor(Perk perk) {
        return perk.isAbility() ? AQUA : GREEN;
    }

    static ChatFormatting perkFormatting(Perk perk) {
        return perk.isAbility() ? ChatFormatting.AQUA : ChatFormatting.GREEN;
    }

    static int levelColor(Track track, int level) {
        return level <= 0 ? DARK : level >= track.maxLevel() ? MASTERED : GOLD;
    }

    static MutableComponent toolName(Track track) {
        return Component.translatable(track.item.getDescriptionId());
    }
}
