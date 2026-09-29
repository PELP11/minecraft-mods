package com.afjan.tempered.mastery;

import net.minecraft.world.item.Item;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** The five milestones of one tool (e.g. the diamond pickaxe). */
public final class Track {
    public static final int MAX_LEVEL = 5;
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

    static Map<Perk, Double> copy(Map<Perk, Double> perks) {
        return perks.isEmpty() ? new EnumMap<>(Perk.class) : new EnumMap<>(perks);
    }
}
