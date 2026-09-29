package com.afjan.tempered.mastery;

import net.minecraft.world.item.Item;

import java.util.List;
import java.util.Map;

/** The milestones of one tool (e.g. the diamond pickaxe): 20 for main tools, 5 for the others. */
public final class Track {
    /** Highest level any track has (roman numerals and translations go this far). */
    public static final int HIGHEST_LEVEL = Tracks.MAIN_LEVELS;
    private static final Map<Perk, Double> NONE = Map.of();

    public final Kind kind;
    public final Tier tier;
    public final Item item;
    public final List<Milestone> milestones;

    Track(Kind kind, Tier tier, Item item, List<Milestone> milestones) {
        this.kind = kind;
        this.tier = tier;
        this.item = item;
        this.milestones = milestones;
    }

    public int maxLevel() {
        return milestones.size();
    }

    /** Levels are consecutive: milestone III only counts once I and II are met. */
    public int level(Mastery mastery) {
        if (mastery == null) return 0;
        int level = 0;
        for (Milestone milestone : milestones) {
            if (!milestone.isMet(mastery)) break;
            level++;
        }
        return level;
    }

    public Map<Perk, Double> perks(int level) {
        return level <= 0 ? NONE : milestones.get(Math.min(level, milestones.size()) - 1).totals();
    }

    public double perk(int level, Perk perk) {
        return perks(level).getOrDefault(perk, 0.0);
    }

    /** Same milestones bound to another item (modded tools borrow the nearest vanilla track). */
    Track withItem(Item other) {
        return new Track(kind, tier, other, milestones);
    }
}
