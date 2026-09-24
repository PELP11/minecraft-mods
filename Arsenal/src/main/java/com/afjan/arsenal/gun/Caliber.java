package com.afjan.arsenal.gun;

/** Every cartridge in the mod. The item id is {@code arsenal:<path>}; {@link com.afjan.arsenal.registry.ModItems#AMMO} holds the items. */
public enum Caliber {
    MM9("ammo_9mm", 1.0F, 1.0F),
    ACP45("ammo_45acp", 1.0F, 1.0F),
    AE50("ammo_50ae", 1.0F, 1.0F),
    MM556("ammo_556", 1.0F, 1.0F),
    MM762("ammo_762", 1.0F, 1.0F),
    LAPUA338("ammo_338", 1.0F, 1.0F),
    BMG50("ammo_50bmg", 1.0F, 1.0F),
    /** Shotgun default: the full pellet spread of whatever gun fires it. */
    BUCKSHOT("shell_buckshot", 1.0F, 1.0F),
    /** One solid projectile instead of a pellet cloud: far more damage per hit, far less forgiving. */
    SLUG("shell_slug", 4.6F, 0.45F),
    GRENADE40("grenade_40mm", 1.0F, 1.0F),
    ROCKET("rocket_round", 1.0F, 1.0F),
    RAILSLUG("rail_slug", 1.0F, 1.0F),
    /** The RPG's heavy round: a fuel-air warhead with over three times the blast of the standard rocket. */
    ROCKET_TBG("rocket_thermobaric", 1.0F, 1.0F);

    private final String path;
    private final float damageMul;
    private final float spreadMul;

    Caliber(String path, float damageMul, float spreadMul) {
        this.path = path;
        this.damageMul = damageMul;
        this.spreadMul = spreadMul;
    }

    public String path() {
        return this.path;
    }

    public float damageMul() {
        return this.damageMul;
    }

    public float spreadMul() {
        return this.spreadMul;
    }

    /** A slug turns a shotgun's pellet cloud into a single heavy projectile. */
    public boolean isSingleProjectile() {
        return this == SLUG;
    }

    public String translationKey() {
        return "item.arsenal." + this.path;
    }

    public static Caliber byIndex(int index) {
        Caliber[] values = values();
        return index >= 0 && index < values.length ? values[index] : MM9;
    }
}
