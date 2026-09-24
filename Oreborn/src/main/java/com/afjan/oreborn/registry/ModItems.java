package com.afjan.oreborn.registry;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import com.afjan.oreborn.Oreborn;
import com.afjan.oreborn.item.LightningStaffItem;
import com.afjan.oreborn.item.OrebornArmorItem;
import com.afjan.oreborn.item.OrebornToolItem;
import com.afjan.oreborn.material.GearType;
import com.afjan.oreborn.material.OreMaterial;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.ToolMaterial;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.equipment.ArmorType;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Oreborn.MODID);

    public static final Map<OreMaterial, DeferredItem<BlockItem>> ORES = new EnumMap<>(OreMaterial.class);
    public static final Map<OreMaterial, DeferredItem<BlockItem>> DEEPSLATE_ORES = new EnumMap<>(OreMaterial.class);
    public static final Map<OreMaterial, DeferredItem<BlockItem>> STORAGE = new EnumMap<>(OreMaterial.class);
    /** Shard (crystals) or raw chunk (ingot materials): what the ore drops. */
    public static final Map<OreMaterial, DeferredItem<Item>> DROPS = new EnumMap<>(OreMaterial.class);
    /** Scrap, only for the ingot materials. */
    public static final Map<OreMaterial, DeferredItem<Item>> SCRAP = new EnumMap<>(OreMaterial.class);
    /** Crystal or ingot: repairs and makes the gear. */
    public static final Map<OreMaterial, DeferredItem<Item>> MAIN = new EnumMap<>(OreMaterial.class);
    public static final Map<OreMaterial, Map<GearType, DeferredItem<Item>>> GEAR = new EnumMap<>(OreMaterial.class);

    private static final Style LORE_STYLE = Style.EMPTY.withItalic(false).withColor(ChatFormatting.GRAY);

    static {
        for (OreMaterial m : OreMaterial.values()) {
            boolean fireproof = m == OreMaterial.EMBERITE;
            ORES.put(m, blockItem(ModBlocks.ORES.get(m), fireproof));
            if (ModBlocks.DEEPSLATE_ORES.containsKey(m)) {
                DEEPSLATE_ORES.put(m, blockItem(ModBlocks.DEEPSLATE_ORES.get(m), fireproof));
            }
            STORAGE.put(m, blockItem(ModBlocks.STORAGE.get(m), fireproof));
            DROPS.put(m, ITEMS.registerSimpleItem(m.dropItemId(), p -> withLore(fireproof ? p.fireResistant() : p, m.dropItemId())));
            if (!m.isCrystal()) {
                SCRAP.put(m, ITEMS.registerSimpleItem(m.id() + "_scrap", p -> withLore(fireproof ? p.fireResistant() : p, m.id() + "_scrap")));
            }
            MAIN.put(m, ITEMS.registerSimpleItem(m.mainItemId(), p -> withLore(fireproof ? p.fireResistant() : p, m.mainItemId()).rarity(Rarity.UNCOMMON)));

            Map<GearType, DeferredItem<Item>> gear = new EnumMap<>(GearType.class);
            for (GearType type : GearType.values()) {
                String name = m.id() + "_" + type.id();
                Rarity rarity = m.isCrystal() ? Rarity.UNCOMMON : Rarity.RARE;
                if (type.isArmor()) {
                    gear.put(type, ITEMS.registerItem(name, p -> new OrebornArmorItem(m, type, p),
                            p -> armorProperties(m, type, fireproof ? p.fireResistant() : p).rarity(rarity)));
                } else {
                    gear.put(type, ITEMS.registerItem(name, p -> new OrebornToolItem(m, type, p),
                            p -> toolProperties(m, type, fireproof ? p.fireResistant() : p).rarity(rarity)));
                }
            }
            GEAR.put(m, gear);
        }
    }

    /** Channels force lightning; repaired with Fulgurite Crystals. */
    public static final DeferredItem<LightningStaffItem> LIGHTNING_STAFF = ITEMS.registerItem("lightning_staff", LightningStaffItem::new,
            p -> p.durability(900).repairable(OreMaterial.FULGURITE.repairItems()).enchantable(15).rarity(Rarity.EPIC));

    private ModItems() {}

    public static Item gear(OreMaterial material, GearType type) {
        return GEAR.get(material).get(type).get();
    }

    /** A grey line telling what the material is for (crafting hints). */
    private static Item.Properties withLore(Item.Properties properties, String itemId) {
        Component line = Component.translatable("lore." + Oreborn.MODID + "." + itemId).withStyle(LORE_STYLE);
        return properties.component(DataComponents.LORE, new ItemLore(List.of(line)));
    }

    private static DeferredItem<BlockItem> blockItem(DeferredBlock<?> block, boolean fireproof) {
        return ITEMS.registerSimpleBlockItem(block.getId().getPath(), block, p -> fireproof ? p.fireResistant() : p);
    }

    /** Vanilla diamond/netherite stats per tool, except the Emberite hoe which is a real weapon (the Ember Scythe). */
    private static Item.Properties toolProperties(OreMaterial m, GearType type, Item.Properties p) {
        ToolMaterial tier = m.toolMaterial();
        return switch (type) {
            case SWORD -> p.sword(tier, 3.0F, -2.4F);
            case PICKAXE -> p.pickaxe(tier, 1.0F, -2.8F);
            case AXE -> p.axe(tier, 5.0F, -3.0F);
            case SHOVEL -> p.shovel(tier, 1.5F, -3.0F);
            case HOE -> m == OreMaterial.EMBERITE ? p.hoe(tier, 2.0F, -2.9F) : p.hoe(tier, -tier.attackDamageBonus(), 0.0F);
            default -> throw new IllegalArgumentException(type + " is not a tool");
        };
    }

    /** Armour stats plus the attribute part of some pieces' passives (shown in the tooltip). */
    private static Item.Properties armorProperties(OreMaterial m, GearType type, Item.Properties p) {
        ArmorType armorType = type.armorType();
        ItemAttributeModifiers attributes = m.armorMaterial().createAttributes(armorType);
        EquipmentSlotGroup slot = EquipmentSlotGroup.bySlot(armorType.getSlot());
        var id = Oreborn.id("armor." + m.id() + "_" + type.id());
        if (m == OreMaterial.FULGURITE && type == GearType.CHESTPLATE) {
            // Storm Shield: the recharging absorption hearts need room
            attributes = attributes.withModifierAdded(Attributes.MAX_ABSORPTION, new AttributeModifier(id, 4.0, AttributeModifier.Operation.ADD_VALUE), slot);
        } else if (m == OreMaterial.FULGURITE && type == GearType.LEGGINGS) {
            attributes = attributes.withModifierAdded(Attributes.MOVEMENT_SPEED, new AttributeModifier(id, 0.2, AttributeModifier.Operation.ADD_MULTIPLIED_BASE), slot);
        } else if (m == OreMaterial.UMBRIUM && type == GearType.LEGGINGS) {
            attributes = attributes.withModifierAdded(Attributes.STEP_HEIGHT, new AttributeModifier(id, 0.5, AttributeModifier.Operation.ADD_VALUE), slot);
        }
        return p.humanoidArmor(m.armorMaterial(), armorType).attributes(attributes);
    }
}
