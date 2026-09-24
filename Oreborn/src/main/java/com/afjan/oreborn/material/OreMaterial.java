package com.afjan.oreborn.material;

import java.util.EnumMap;
import java.util.Map;

import com.afjan.oreborn.Oreborn;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ToolMaterial;
import net.minecraft.world.item.equipment.ArmorMaterial;
import net.minecraft.world.item.equipment.ArmorType;
import net.minecraft.world.item.equipment.EquipmentAsset;
import net.minecraft.world.item.equipment.EquipmentAssets;
import net.minecraft.world.level.block.Block;

/**
 * The four rare ores. Crystals (Cryolite, Fulgurite) are mined as shards and crafted into crystals, which craft
 * straight into gear (diamond tier+). Ingot materials (Emberite, Umbrium) are smelted into scrap and alloyed into
 * ingots like netherite, which upgrade diamond gear at a smithing table (netherite tier+).
 */
public enum OreMaterial {
    CRYOLITE("cryolite", true, 0x8FD8FF,
            BlockTags.INCORRECT_FOR_DIAMOND_TOOL, 1800, 8.5F, 3.0F, 16,
            35, 2.0F, 0.05F, 11, SoundEvents.ARMOR_EQUIP_DIAMOND),
    FULGURITE("fulgurite", true, 0xFFE14D,
            BlockTags.INCORRECT_FOR_DIAMOND_TOOL, 1650, 10.0F, 3.0F, 18,
            33, 2.0F, 0.0F, 11, SoundEvents.ARMOR_EQUIP_DIAMOND),
    EMBERITE("emberite", false, 0xFF7A2A,
            BlockTags.INCORRECT_FOR_NETHERITE_TOOL, 2300, 9.5F, 4.0F, 15,
            39, 3.0F, 0.1F, 19, SoundEvents.ARMOR_EQUIP_NETHERITE),
    UMBRIUM("umbrium", false, 0xB57CFF,
            BlockTags.INCORRECT_FOR_NETHERITE_TOOL, 2500, 9.5F, 4.5F, 17,
            40, 3.5F, 0.1F, 19, SoundEvents.ARMOR_EQUIP_NETHERITE);

    private final String id;
    private final boolean crystal;
    private final int color;
    private final TagKey<Item> repairItems;
    private final ToolMaterial toolMaterial;
    private final ArmorMaterial armorMaterial;

    OreMaterial(String id, boolean crystal, int color,
            TagKey<Block> incorrectBlocks, int toolDurability, float speed, float attackBonus, int enchantability,
            int armorDurability, float toughness, float knockbackResistance, int bodyDefense, Holder<SoundEvent> equipSound) {
        this.id = id;
        this.crystal = crystal;
        this.color = color;
        this.repairItems = TagKey.create(Registries.ITEM, Oreborn.id(id + "_repair_materials"));
        this.toolMaterial = new ToolMaterial(incorrectBlocks, toolDurability, speed, attackBonus, enchantability, repairItems);
        Map<ArmorType, Integer> defense = new EnumMap<>(ArmorType.class);
        defense.put(ArmorType.BOOTS, 3);
        defense.put(ArmorType.LEGGINGS, 6);
        defense.put(ArmorType.CHESTPLATE, 8);
        defense.put(ArmorType.HELMET, 3);
        defense.put(ArmorType.BODY, bodyDefense);
        ResourceKey<EquipmentAsset> asset = ResourceKey.create(EquipmentAssets.ROOT_ID, Oreborn.id(id));
        this.armorMaterial = new ArmorMaterial(armorDurability, defense, enchantability, equipSound, toughness, knockbackResistance,
                repairItems, asset);
    }

    public String id() {
        return id;
    }

    /** Crystal materials craft into gear; the others are netherite-style smithing upgrades of diamond gear. */
    public boolean isCrystal() {
        return crystal;
    }

    /** Signature colour (tooltips, ore radar glow). */
    public int color() {
        return color;
    }

    public TagKey<Item> repairItems() {
        return repairItems;
    }

    public ToolMaterial toolMaterial() {
        return toolMaterial;
    }

    public ArmorMaterial armorMaterial() {
        return armorMaterial;
    }

    /** Crystal or ingot: what the gear is made of. */
    public String mainItemId() {
        return crystal ? id + "_crystal" : id + "_ingot";
    }

    /** What the ore drops: shards for crystals, a raw chunk for ingot materials. */
    public String dropItemId() {
        return crystal ? id + "_shard" : "raw_" + id;
    }
}
