package com.afjan.arsenal.gun;

import java.util.List;

/**
 * Every firearm in the mod and its raw statistics. Attachments are applied on top of these numbers by the
 * {@code effective*} helpers, so nothing else in the mod ever has to know which parts are bolted on.
 *
 * <p>Damage is in half-hearts, delays and reload times in ticks, spread in degrees of cone half-angle and range in
 * blocks.
 */
public enum GunType {
    // path            class    action   calibers                              mag  dmg  delay reload spread range pellets recoil
    AK47("ak47", Family.RIFLE, Action.AUTO, List.of(Caliber.MM762), 30, 9.0F, 4, 55, 2.6F, 60, 1, 1.5F),
    M4A1("m4a1", Family.RIFLE, Action.AUTO, List.of(Caliber.MM556), 30, 7.5F, 3, 48, 1.7F, 65, 1, 1.0F),
    SCAR_H("scar_h", Family.RIFLE, Action.AUTO, List.of(Caliber.MM762), 20, 11.5F, 5, 52, 2.2F, 70, 1, 1.7F),
    AUG("aug", Family.RIFLE, Action.AUTO, List.of(Caliber.MM556), 42, 7.0F, 3, 60, 1.5F, 65, 1, 0.9F),

    GLOCK17("glock17", Family.PISTOL, Action.SEMI, List.of(Caliber.MM9), 17, 5.0F, 3, 32, 2.4F, 35, 1, 0.7F),
    M1911("m1911", Family.PISTOL, Action.SEMI, List.of(Caliber.ACP45), 7, 7.5F, 4, 30, 2.2F, 35, 1, 1.0F),
    M9("m9", Family.PISTOL, Action.SEMI, List.of(Caliber.MM9), 15, 5.5F, 3, 32, 2.2F, 35, 1, 0.8F),
    DEAGLE("deagle", Family.PISTOL, Action.SEMI, List.of(Caliber.AE50), 7, 13.0F, 8, 38, 3.0F, 45, 1, 2.6F),

    BARRETT("barrett_m82", Family.SNIPER, Action.SEMI, List.of(Caliber.BMG50), 10, 30.0F, 24, 80, 0.6F, 180, 1, 4.5F),
    SVD("svd_dragunov", Family.SNIPER, Action.SEMI, List.of(Caliber.MM762), 10, 18.0F, 12, 60, 0.8F, 140, 1, 2.6F),
    AWP("awp", Family.SNIPER, Action.BOLT, List.of(Caliber.LAPUA338), 5, 45.0F, 30, 72, 0.35F, 220, 1, 5.0F),

    REMINGTON870("remington_870", Family.SHOTGUN, Action.PUMP, List.of(Caliber.BUCKSHOT, Caliber.SLUG), 6, 3.6F, 14, 65, 7.0F, 25, 8, 3.0F),
    SPAS12("spas12", Family.SHOTGUN, Action.SEMI, List.of(Caliber.BUCKSHOT, Caliber.SLUG), 8, 3.2F, 8, 55, 6.5F, 26, 9, 2.6F),
    AA12("aa12", Family.SHOTGUN, Action.AUTO, List.of(Caliber.BUCKSHOT, Caliber.SLUG), 20, 2.7F, 4, 75, 8.0F, 22, 7, 2.0F),
    SAWED_OFF("sawed_off", Family.SHOTGUN, Action.SEMI, List.of(Caliber.BUCKSHOT, Caliber.SLUG), 2, 4.2F, 2, 40, 10.0F, 18, 12, 4.0F),

    RPG7("rpg7", Family.LAUNCHER, Action.SEMI, List.of(Caliber.ROCKET, Caliber.ROCKET_TBG), 1, 0.0F, 40, 70, 1.0F, 0, 1, 14.0F),
    M32("m32_launcher", Family.LAUNCHER, Action.SEMI, List.of(Caliber.GRENADE40), 6, 0.0F, 10, 70, 1.5F, 0, 1, 2.5F),
    /** Charged: hold the trigger to fill the capacitors, release to fire (see {@link #charges()}). */
    RAILGUN("railgun", Family.RAILGUN, Action.BOLT, List.of(Caliber.RAILSLUG), 3, 60.0F, 30, 90, 0.10F, 160, 1, 6.0F);

    /** Drives the item model, the creative tab order and which blueprint category the gun lands in. */
    public enum Family {
        RIFLE, PISTOL, SNIPER, SHOTGUN, LAUNCHER, RAILGUN
    }

    /** How the trigger behaves. {@code AUTO} keeps firing while the button is held. */
    public enum Action {
        SEMI, AUTO, PUMP, BOLT
    }

    private final String path;
    private final Family family;
    private final Action action;
    private final List<Caliber> calibers;
    private final int magazine;
    private final float damage;
    private final int fireDelay;
    private final int reloadTicks;
    private final float spread;
    private final int range;
    private final int pellets;
    private final float recoil;

    GunType(String path, Family family, Action action, List<Caliber> calibers, int magazine, float damage,
            int fireDelay, int reloadTicks, float spread, int range, int pellets, float recoil) {
        this.path = path;
        this.family = family;
        this.action = action;
        this.calibers = calibers;
        this.magazine = magazine;
        this.damage = damage;
        this.fireDelay = fireDelay;
        this.reloadTicks = reloadTicks;
        this.spread = spread;
        this.range = range;
        this.pellets = pellets;
        this.recoil = recoil;
    }

    public String path() {
        return this.path;
    }

    public Family family() {
        return this.family;
    }

    public Action action() {
        return this.action;
    }

    public List<Caliber> calibers() {
        return this.calibers;
    }

    public boolean accepts(Caliber caliber) {
        return this.calibers.contains(caliber);
    }

    public int magazine() {
        return this.magazine;
    }

    public float damage() {
        return this.damage;
    }

    public int fireDelay() {
        return this.fireDelay;
    }

    public int reloadTicks() {
        return this.reloadTicks;
    }

    public float spread() {
        return this.spread;
    }

    public int range() {
        return this.range;
    }

    public int pellets() {
        return this.pellets;
    }

    public float recoil() {
        return this.recoil;
    }

    public boolean isAutomatic() {
        return this.action == Action.AUTO;
    }

    /** Rockets and 40mm grenades are real entities; everything else is an instant trace. */
    public boolean firesProjectile() {
        return this.family == Family.LAUNCHER;
    }

    /** The railgun's slug goes through walls and every target behind them. */
    public boolean piercesBlocks() {
        return this.family == Family.RAILGUN;
    }

    /**
     * Charged weapons fire on release: holding the trigger fills the capacitors over
     * {@link com.afjan.arsenal.combat.RailCharge#FULL_TICKS}, and the shot's power follows the charge.
     */
    public boolean charges() {
        return this.family == Family.RAILGUN;
    }

    /** The .50 BMG and the railgun punch straight through a target into whatever is standing behind it. */
    public boolean piercesEntities() {
        return this == BARRETT || this.family == Family.RAILGUN;
    }

    public float headshotMultiplier() {
        return switch (this.family) {
            case SNIPER -> 2.5F;
            case SHOTGUN -> 1.35F;
            default -> 1.8F;
        };
    }

    // --- attachment-adjusted statistics ------------------------------------------------------------------------

    public int effectiveMagazine(GunData data) {
        float mag = this.magazine;
        for (Attachment a : data.attachments()) {
            mag *= a.magMul();
        }
        return Math.max(1, Math.round(mag));
    }

    public float effectiveDamage(GunData data) {
        float damage = this.damage * data.caliber().damageMul();
        for (Attachment a : data.attachments()) {
            damage *= a.damageMul();
        }
        return damage;
    }

    /** {@code deployed} is true when the shooter is crouching, which is when a bipod does its work. */
    public float effectiveSpread(GunData data, boolean deployed) {
        float spread = this.spread * data.caliber().spreadMul();
        for (Attachment a : data.attachments()) {
            spread *= a == Attachment.BIPOD && deployed ? 0.25F : a.spreadMul();
        }
        return Math.max(0.0F, spread);
    }

    public double effectiveRange(GunData data) {
        double range = this.range;
        for (Attachment a : data.attachments()) {
            range *= a.rangeMul();
        }
        return range;
    }

    public int effectiveFireDelay(GunData data) {
        int delay = this.fireDelay;
        for (Attachment a : data.attachments()) {
            delay += a.delayAdd();
        }
        return Math.max(1, delay);
    }

    public int effectiveReload(GunData data) {
        float ticks = this.reloadTicks;
        for (Attachment a : data.attachments()) {
            ticks *= a.reloadMul();
        }
        return Math.max(5, Math.round(ticks));
    }

    public float effectiveRecoil(GunData data) {
        float recoil = this.recoil;
        for (Attachment a : data.attachments()) {
            recoil *= a.recoilMul();
        }
        return recoil;
    }

    public int effectivePellets(GunData data) {
        return data.caliber().isSingleProjectile() ? 1 : this.pellets;
    }

    /** 0 when there is no optic fitted, otherwise the FOV multiplier to apply while crouch-aiming. */
    public float zoom(GunData data) {
        for (Attachment a : data.attachments()) {
            if (a.zoom() > 0.0F) {
                return a.zoom();
            }
        }
        return this.family == Family.SNIPER ? 0.45F : 0.0F;
    }

    public boolean suppressed(GunData data) {
        return data.has(Attachment.SUPPRESSOR);
    }

    public String translationKey() {
        return "item.arsenal." + this.path;
    }
}
