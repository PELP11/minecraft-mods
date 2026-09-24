package com.afjan.oreborn.material;

import org.jspecify.annotations.Nullable;

import net.minecraft.world.item.equipment.ArmorType;

/** The nine pieces of gear every Oreborn material comes in. */
public enum GearType {
    SWORD("sword", null),
    PICKAXE("pickaxe", null),
    AXE("axe", null),
    SHOVEL("shovel", null),
    HOE("hoe", null),
    HELMET("helmet", ArmorType.HELMET),
    CHESTPLATE("chestplate", ArmorType.CHESTPLATE),
    LEGGINGS("leggings", ArmorType.LEGGINGS),
    BOOTS("boots", ArmorType.BOOTS);

    public static final GearType[] TOOLS = { SWORD, PICKAXE, AXE, SHOVEL, HOE };
    public static final GearType[] ARMOR = { HELMET, CHESTPLATE, LEGGINGS, BOOTS };

    private final String id;
    private final @Nullable ArmorType armorType;

    GearType(String id, @Nullable ArmorType armorType) {
        this.id = id;
        this.armorType = armorType;
    }

    public String id() {
        return id;
    }

    public boolean isArmor() {
        return armorType != null;
    }

    public ArmorType armorType() {
        if (armorType == null) {
            throw new IllegalStateException(this + " is not armour");
        }
        return armorType;
    }
}
