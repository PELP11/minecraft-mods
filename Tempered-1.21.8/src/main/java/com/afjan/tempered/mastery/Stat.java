package com.afjan.tempered.mastery;

import java.util.Set;

/**
 * A counter stored on a tool. Every milestone is a list of "reach N of this stat" requirements.
 * Counters are lifetime totals, so work done early also counts towards later milestones.
 */
public enum Stat {
    MINED("mined"),
    ORES("ores"),
    DEEP("deep"),
    NETHER_MINED("nether_mined"),
    END_MINED("end_mined"),
    OBSIDIAN("obsidian"),
    DEBRIS("debris"),
    GEMS("gems"),
    LOGS("logs"),
    SAND("sand"),
    GRAVEL("gravel"),
    CLAY("clay"),
    SNOW("snow"),
    SOUL("soul"),
    TILLED("tilled"),
    HARVESTED("harvested"),
    WARTS("warts"),
    SCULK("sculk"),
    KILLS("kills"),
    HOSTILE("hostile"),
    NETHER_KILLS("nether_kills"),
    END_KILLS("end_kills"),
    ELITE("elite"),
    MOUNTED("mounted"),
    LONG_SHOTS("long_shots"),
    RAIDERS("raiders"),
    THROWN("thrown"),
    AQUATIC("aquatic"),
    SMASH("smash"),
    SHEARED("sheared"),
    CATCHES("catches"),
    TREASURE("treasure");

    /** Stats whose wording depends on the tool ("Mine 50 blocks" vs "Dig 50 blocks"). */
    private static final Set<Stat> KIND_WORDED = Set.of(MINED, NETHER_MINED, END_MINED);

    public final String id;

    Stat(String id) {
        this.id = id;
    }

    /** Requirement wording with one %s for the amount, e.g. "Mine %s ores". */
    public String requirementKey(Kind kind) {
        return KIND_WORDED.contains(this)
                ? "stat.tempered." + kind.id + "." + id
                : "stat.tempered." + id;
    }

    public static Stat byId(String id) {
        for (Stat stat : values()) {
            if (stat.id.equals(id)) return stat;
        }
        return null;
    }
}
