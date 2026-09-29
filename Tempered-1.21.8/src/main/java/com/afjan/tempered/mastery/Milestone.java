package com.afjan.tempered.mastery;

import java.util.List;
import java.util.Map;

/**
 * One mastery level. {@code reqs} must all be met (lifetime totals); {@code totals} are the perk values
 * the tool has from this level on and {@code gained} lists the perks that are new or bigger here.
 */
public record Milestone(int level, List<Req> reqs, Map<Perk, Double> totals, List<Perk> gained) {

    public record Req(Stat stat, int amount) {
        /** Translation key of the challenge text; an amount of 1 uses the singular ("Defeat an elite foe"). */
        public String key(Kind kind) {
            String key = stat.requirementKey(kind);
            return amount == 1 ? key + ".one" : key;
        }
    }

    public boolean isMet(Mastery mastery) {
        for (Req req : reqs) {
            if (mastery.get(req.stat()) < req.amount()) return false;
        }
        return true;
    }

    /** 0..1, averaged over the requirements (each one capped at done). */
    public float progress(Mastery mastery) {
        if (reqs.isEmpty()) return 1.0F;
        float sum = 0;
        for (Req req : reqs) {
            sum += Math.min(1.0F, mastery.get(req.stat()) / (float) req.amount());
        }
        return sum / reqs.size();
    }
}
