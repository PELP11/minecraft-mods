package com.afjan.arsenal.gun;

/**
 * A bolt-on part. Every gun has one slot of each {@link Slot}, so a weapon carries at most four attachments; they are
 * stored as a bit mask in {@link GunData} and can be taken off again at the Weapon Workbench at any time.
 */
public enum Attachment {
    // --- muzzle -------------------------------------------------------------------------------------------------
    /** Quiet: nearby mobs are not alerted by the shot, at the cost of a little punch and reach. */
    SUPPRESSOR("suppressor", Slot.MUZZLE, 0.95F, 0.90F, 0.90F, 1.0F, 1.0F, 0, 1.0F, 0.0F),
    /** Vents the gases sideways: much tighter groups and far less kick. */
    MUZZLE_BRAKE("muzzle_brake", Slot.MUZZLE, 0.60F, 1.0F, 1.0F, 1.0F, 1.0F, 0, 0.65F, 0.0F),
    /** A longer, heavier barrel: hits harder and further, but slows the rate of fire. */
    HEAVY_BARREL("heavy_barrel", Slot.MUZZLE, 0.90F, 1.20F, 1.30F, 1.0F, 1.0F, 2, 1.1F, 0.0F),

    // --- optic --------------------------------------------------------------------------------------------------
    /** Fast target acquisition with a hint of zoom. */
    RED_DOT("red_dot_sight", Slot.OPTIC, 0.75F, 1.0F, 1.0F, 1.0F, 1.0F, 0, 1.0F, 0.85F),
    /** 4x glass: accurate at range, slow to swing around. */
    ACOG_SCOPE("acog_scope", Slot.OPTIC, 0.65F, 1.0F, 1.25F, 1.0F, 1.0F, 0, 1.0F, 0.40F),
    /** 8x thermal imaging: while you aim, every living thing in range lights up through walls. */
    THERMAL_SCOPE("thermal_scope", Slot.OPTIC, 0.70F, 1.0F, 1.15F, 1.0F, 1.0F, 0, 1.0F, 0.22F),

    // --- magazine -----------------------------------------------------------------------------------------------
    EXTENDED_MAG("extended_mag", Slot.MAGAZINE, 1.0F, 1.0F, 1.0F, 1.5F, 1.15F, 0, 1.0F, 0.0F),
    DRUM_MAG("drum_mag", Slot.MAGAZINE, 1.05F, 1.0F, 1.0F, 2.2F, 1.6F, 0, 1.1F, 0.0F),
    QUICKDRAW_MAG("quickdraw_mag", Slot.MAGAZINE, 1.0F, 1.0F, 1.0F, 1.0F, 0.55F, 0, 1.0F, 0.0F),

    // --- underbarrel --------------------------------------------------------------------------------------------
    LASER_SIGHT("laser_sight", Slot.UNDERBARREL, 0.70F, 1.0F, 1.0F, 1.0F, 1.0F, 0, 1.0F, 0.0F),
    FOREGRIP("foregrip", Slot.UNDERBARREL, 0.80F, 1.0F, 1.0F, 1.0F, 1.0F, 0, 0.70F, 0.0F),
    /** Deploys when you crouch: almost perfect accuracy while sneaking, slightly worse on the move. */
    BIPOD("bipod", Slot.UNDERBARREL, 1.05F, 1.0F, 1.0F, 1.0F, 1.0F, 0, 1.0F, 0.0F);

    public enum Slot {
        MUZZLE, OPTIC, MAGAZINE, UNDERBARREL;

        public String translationKey() {
            return "gui.arsenal.slot." + this.name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    private final String path;
    private final Slot slot;
    private final float spreadMul;
    private final float damageMul;
    private final float rangeMul;
    private final float magMul;
    private final float reloadMul;
    private final int delayAdd;
    private final float recoilMul;
    private final float zoom;

    Attachment(String path, Slot slot, float spreadMul, float damageMul, float rangeMul, float magMul,
            float reloadMul, int delayAdd, float recoilMul, float zoom) {
        this.path = path;
        this.slot = slot;
        this.spreadMul = spreadMul;
        this.damageMul = damageMul;
        this.rangeMul = rangeMul;
        this.magMul = magMul;
        this.reloadMul = reloadMul;
        this.delayAdd = delayAdd;
        this.recoilMul = recoilMul;
        this.zoom = zoom;
    }

    public String path() {
        return this.path;
    }

    public Slot slot() {
        return this.slot;
    }

    public float spreadMul() {
        return this.spreadMul;
    }

    public float damageMul() {
        return this.damageMul;
    }

    public float rangeMul() {
        return this.rangeMul;
    }

    public float magMul() {
        return this.magMul;
    }

    public float reloadMul() {
        return this.reloadMul;
    }

    public int delayAdd() {
        return this.delayAdd;
    }

    public float recoilMul() {
        return this.recoilMul;
    }

    /** FOV multiplier while aiming down the sights; 0 means this part is not an optic. */
    public float zoom() {
        return this.zoom;
    }

    public int bit() {
        return 1 << this.ordinal();
    }

    public String translationKey() {
        return "item.arsenal." + this.path;
    }
}
