package com.afjan.hatchery.spawner;

/**
 * Spawner upgrades. Swarm, Haste and Frailty stack: reaching level N uses N modules at once (maxing Swarm or Haste
 * takes 1+2+3+4+5 = 15 modules). Daylight and Redstone are one-offs.
 */
public enum Module {
    SWARM(5),
    HASTE(5),
    FRAILTY(4),
    DAYLIGHT(1),
    REDSTONE(1);

    public final String id;
    public final int maxLevel;

    Module(int maxLevel) {
        this.id = name().toLowerCase(java.util.Locale.ROOT);
        this.maxLevel = maxLevel;
    }

    public int level(SpawnerModules m) {
        return switch (this) {
            case SWARM -> m.swarm();
            case HASTE -> m.haste();
            case FRAILTY -> m.frailty();
            case DAYLIGHT -> m.daylight() ? 1 : 0;
            case REDSTONE -> m.redstone() ? 1 : 0;
        };
    }

    public SpawnerModules with(SpawnerModules m, int level) {
        return switch (this) {
            case SWARM -> new SpawnerModules(level, m.haste(), m.frailty(), m.daylight(), m.redstone());
            case HASTE -> new SpawnerModules(m.swarm(), level, m.frailty(), m.daylight(), m.redstone());
            case FRAILTY -> new SpawnerModules(m.swarm(), m.haste(), level, m.daylight(), m.redstone());
            case DAYLIGHT -> new SpawnerModules(m.swarm(), m.haste(), m.frailty(), level > 0, m.redstone());
            case REDSTONE -> new SpawnerModules(m.swarm(), m.haste(), m.frailty(), m.daylight(), level > 0);
        };
    }

    /** Modules used to go from level - 1 to {@code level}. */
    public int cost(int level) {
        return maxLevel > 1 ? level : 1;
    }

    /** Modules a spawner at {@code level} holds in total (given back when it is mined). */
    public int totalCost(int level) {
        int sum = 0;
        for (int l = 1; l <= level; l++) sum += cost(l);
        return sum;
    }

    public String itemName() {
        return id + "_module";
    }

    public String translationKey() {
        return "module.hatchery." + id;
    }
}
