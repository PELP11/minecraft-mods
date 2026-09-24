package com.afjan.arsenal.vehicle;

import com.afjan.arsenal.combat.Ordnance;

import net.minecraft.world.phys.Vec3;

/**
 * What hangs under the F-14 and comes off it with the fire button (right click). {@code X} cycles through them. Each
 * store has fixed stations on the jet (the renderer shows exactly the rounds still loaded) and a count packed into the
 * jet's loadout int.
 */
public enum Store {
    /** Heat-seeking, short range: locks onto whatever is hot in a narrow cone ahead. Glove shoulder rails. */
    AIM9("aim9_sidewinder", Ordnance.AIM9, 2, 0, 2, 20, "AIM-9"),
    /** Radar-guided, long range, a much bigger warhead; slower to lock. Under the glove pylons. */
    AIM54("aim54_phoenix", Ordnance.AIM54, 2, 2, 2, 30, "AIM-54"),
    /** Unguided 5-inch rockets, four to a pod; hold the button to ripple them. */
    ZUNI("zuni_rocket", Ordnance.ZUNI, 8, 4, 4, 4, "ZUNI"),
    /** 500 lb free-fall bombs from the tunnel between the engines. */
    MK82("mk82_bomb", Ordnance.MK82, 2, 8, 2, 10, "MK-82");

    public static final int FLARE_SHIFT = 10;
    public static final int FLARE_BITS = 6;
    public static final int MAX_FLARES = 36;

    private final String itemPath;
    private final Ordnance ordnance;
    private final int capacity;
    private final int shift;
    private final int bits;
    private final int cooldown;
    private final String label;

    Store(String itemPath, Ordnance ordnance, int capacity, int shift, int bits, int cooldown, String label) {
        this.itemPath = itemPath;
        this.ordnance = ordnance;
        this.capacity = capacity;
        this.shift = shift;
        this.bits = bits;
        this.cooldown = cooldown;
        this.label = label;
    }

    public String itemPath() {
        return this.itemPath;
    }

    public Ordnance ordnance() {
        return this.ordnance;
    }

    public int capacity() {
        return this.capacity;
    }

    /** Ticks between two rounds of this store. */
    public int cooldown() {
        return this.cooldown;
    }

    /** How the HUD names it. */
    public String label() {
        return this.label;
    }

    public boolean guided() {
        return this == AIM9 || this == AIM54;
    }

    public int count(int loadout) {
        return (loadout >>> this.shift) & ((1 << this.bits) - 1);
    }

    public int withCount(int loadout, int count) {
        int mask = ((1 << this.bits) - 1) << this.shift;
        return (loadout & ~mask) | ((Math.max(0, Math.min(this.capacity, count)) << this.shift) & mask);
    }

    public static int flares(int loadout) {
        return (loadout >>> FLARE_SHIFT) & ((1 << FLARE_BITS) - 1);
    }

    public static int withFlares(int loadout, int flares) {
        int mask = ((1 << FLARE_BITS) - 1) << FLARE_SHIFT;
        return (loadout & ~mask) | ((Math.max(0, Math.min(MAX_FLARES, flares)) << FLARE_SHIFT) & mask);
    }

    /** A jet straight off the workbench: every station full. */
    public static int full() {
        int loadout = 0;
        for (Store store : values()) {
            loadout = store.withCount(loadout, store.capacity);
        }
        return withFlares(loadout, MAX_FLARES);
    }

    /**
     * Where the round that leaves next hangs on the jet (model space, metres, centre of gravity at the origin). They
     * come off in order: right, left, right, ... (rockets: right pod's tubes and left pod's tubes in turn).
     */
    public Vec3 station(int remaining) {
        int fired = this.capacity - remaining;
        boolean right = fired % 2 == 0;
        double side = right ? 1.0 : -1.0;
        return switch (this) {
            case AIM9 -> new Vec3(2.5 * side, -0.12, -0.55);
            case AIM54 -> new Vec3(2.15 * side, -0.66, -0.4);
            case MK82 -> new Vec3(0.42 * side, -0.6, -0.6);
            case ZUNI -> {
                int tube = (fired / 2) % 4;
                double dx = (tube % 2 == 0 ? 0.085 : -0.085);
                double dy = (tube < 2 ? 0.085 : -0.085);
                yield new Vec3(0.42 * side + dx, -0.62 + dy, 1.5);
            }
        };
    }

    /**
     * Which of the renderer's parts show at this count (the rest have been fired). Part names come from
     * tools/vehicle_models.py.
     */
    public static boolean partLoaded(String part, int loadout) {
        if (part.startsWith("zuni_")) {
            boolean right = part.charAt(5) == 'r';
            int tube = part.charAt(6) - '0';
            int order = tube * 2 + (right ? 0 : 1);
            return order >= ZUNI.capacity - ZUNI.count(loadout);
        }
        Store store = part.startsWith("aim9_") ? AIM9 : part.startsWith("aim54_") ? AIM54 : part.startsWith("mk82_") ? MK82 : null;
        if (store == null) {
            return true;
        }
        boolean right = part.endsWith("_r");
        int count = store.count(loadout);
        return right ? count >= 2 : count >= 1;
    }
}
