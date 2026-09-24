package com.afjan.arsenal.combat;

/**
 * Everything that flies through the air and then goes off: the four hand grenades, the four weapons of mass
 * destruction, the 40mm round from the M32 and the RPG's rocket. One entity class ({@code OrdnanceEntity}) covers all
 * of them; this enum is the difference between them.
 */
public enum Ordnance {
    // path                 fuse  gravity velocity bounces  thrown
    FRAG("frag_grenade", 60, true, 0.85F, true, true),
    INCENDIARY("incendiary_grenade", 50, true, 0.85F, true, true),
    FLASHBANG("flashbang", 30, true, 0.95F, true, true),
    SMOKE("smoke_grenade", 40, true, 0.85F, true, true),

    SINGULARITY("singularity_charge", 50, true, 0.70F, true, true),
    THERMOBARIC("thermobaric_bomb", 70, true, 0.60F, true, true),
    ION_BEACON("ion_cannon_beacon", 120, true, 0.70F, true, true),
    CHEMICAL("chemical_warhead", 60, true, 0.60F, true, true),

    /** Lobbed by the M32: arcs like a grenade but goes off the moment it touches something. */
    GRENADE_40MM("grenade_40mm", 0, true, 1.6F, false, false),
    /**
     * Fired by the RPG-7: leaves the tube slowly on its booster, then the sustainer lights and it speeds up to
     * {@link #maxSpeed()}; flies flat and detonates on contact.
     */
    ROCKET("rocket_round", 0, false, 1.3F, false, false),
    /** The RPG-7's thermobaric round: heavier, a little slower, and a far bigger blast. */
    ROCKET_THERMOBARIC("rocket_thermobaric", 0, false, 1.1F, false, false),

    // --- the F-14's stores (velocity is added to the jet's own) ---------------------------------------------------
    /** Heat-seeking missile: homes on its target, proximity fuse. */
    AIM9("aim9_sidewinder", 0, false, 1.2F, false, false),
    /** Long-range radar missile: drops off the pylon, then its motor lights; bigger warhead, wider turns. */
    AIM54("aim54_phoenix", 0, false, 0.4F, false, false),
    /** 5-inch unguided rocket from the LAU-10 pods. */
    ZUNI("zuni_rocket", 0, false, 1.8F, false, false),
    /** 500 lb free-fall bomb: keeps the jet's speed and falls on its target. */
    MK82("mk82_bomb", 0, true, 0.0F, false, false),
    /** A decoy flare: burns bright for a few seconds while it falls; heat seekers may take it for their target. */
    FLARE("flare_cartridges", 80, true, 0.3F, false, false);

    private final String path;
    private final int fuse;
    private final boolean gravity;
    private final float velocity;
    private final boolean bounces;
    private final boolean thrownByHand;

    Ordnance(String path, int fuse, boolean gravity, float velocity, boolean bounces, boolean thrownByHand) {
        this.path = path;
        this.fuse = fuse;
        this.gravity = gravity;
        this.velocity = velocity;
        this.bounces = bounces;
        this.thrownByHand = thrownByHand;
    }

    public String path() {
        return this.path;
    }

    /** Ticks until it goes off by itself; 0 means it detonates on impact instead. */
    public int fuse() {
        return this.fuse;
    }

    public boolean hasGravity() {
        return this.gravity;
    }

    public float velocity() {
        return this.velocity;
    }

    public boolean bounces() {
        return this.bounces;
    }

    /** True for the items you throw by hand, false for the two that are fired out of a launcher. */
    public boolean thrownByHand() {
        return this.thrownByHand;
    }

    public boolean isWeaponOfMassDestruction() {
        return this == SINGULARITY || this == THERMOBARIC || this == ION_BEACON || this == CHEMICAL;
    }

    /** RPG rounds: rocket motor, flame and smoke trail, no gravity. */
    public boolean isRocket() {
        return this == ROCKET || this == ROCKET_THERMOBARIC;
    }

    /** Guided air-to-air missiles. */
    public boolean isMissile() {
        return this == AIM9 || this == AIM54;
    }

    /** Anything driven by a rocket motor: flame, smoke trail, the roar that follows it, no gravity. */
    public boolean hasMotor() {
        return this.isRocket() || this.isMissile() || this == ZUNI;
    }

    /** Launched from the F-14 (these never leave anyone's hand). */
    public boolean fromJet() {
        return this == AIM9 || this == AIM54 || this == ZUNI || this == MK82 || this == FLARE;
    }

    /** Top speed once the rocket motor is burning (blocks per tick); 0 for anything without a motor. */
    public double maxSpeed() {
        return switch (this) {
            case ROCKET -> 2.3;
            case ROCKET_THERMOBARIC -> 1.9;
            case AIM9 -> 7.0;
            case AIM54 -> 7.5;
            case ZUNI -> 6.5;
            default -> 0.0;
        };
    }

    /** Ticks until a round that hit nothing gives up and self-destructs (motor out, fuel gone). */
    public int lifetime() {
        return switch (this) {
            case AIM9 -> 120;
            case AIM54 -> 200;
            case ZUNI -> 140;
            case ROCKET, ROCKET_THERMOBARIC -> 220;
            default -> 1200;
        };
    }

    /** How hard a guided missile can turn (radians per tick). */
    public double turnRate() {
        return switch (this) {
            case AIM9 -> 0.11;
            case AIM54 -> 0.055;
            default -> 0.0;
        };
    }

    /** Length of the round in blocks: the in-flight model is drawn at 1:1. */
    public float length() {
        return switch (this) {
            case AIM9 -> 2.87F;
            case AIM54 -> 3.96F;
            case ZUNI -> 2.79F;
            case MK82 -> 2.21F;
            default -> 0.0F;
        };
    }

    public String translationKey() {
        return "item.arsenal." + this.path;
    }

    public static Ordnance byIndex(int index) {
        Ordnance[] values = values();
        return index >= 0 && index < values.length ? values[index] : FRAG;
    }
}
