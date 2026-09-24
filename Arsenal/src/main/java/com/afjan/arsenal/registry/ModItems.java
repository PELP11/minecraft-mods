package com.afjan.arsenal.registry;

import java.util.EnumMap;
import java.util.Map;

import com.afjan.arsenal.Arsenal;
import com.afjan.arsenal.combat.Ordnance;
import com.afjan.arsenal.gun.Attachment;
import com.afjan.arsenal.gun.Caliber;
import com.afjan.arsenal.gun.GunType;
import com.afjan.arsenal.item.AttachmentItem;
import com.afjan.arsenal.item.GunItem;
import com.afjan.arsenal.item.OrdnanceItem;
import com.afjan.arsenal.vehicle.F14Item;
import com.afjan.arsenal.vehicle.Store;
import com.afjan.arsenal.vehicle.StoreItem;

import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Arsenal.MODID);

    // --- raw materials and components ---------------------------------------------------------------------------
    public static final DeferredItem<Item> STEEL_BLEND = ITEMS.registerSimpleItem("steel_blend");
    public static final DeferredItem<Item> STEEL_INGOT = ITEMS.registerSimpleItem("steel_ingot");
    public static final DeferredItem<Item> BRASS_CASING = ITEMS.registerSimpleItem("brass_casing");
    public static final DeferredItem<Item> BULLET_TIP = ITEMS.registerSimpleItem("bullet_tip");
    public static final DeferredItem<Item> PROPELLANT = ITEMS.registerSimpleItem("propellant");
    public static final DeferredItem<Item> GUN_BARREL = ITEMS.registerSimpleItem("gun_barrel");
    public static final DeferredItem<Item> WEAPON_RECEIVER = ITEMS.registerSimpleItem("weapon_receiver");
    public static final DeferredItem<Item> TRIGGER_ASSEMBLY = ITEMS.registerSimpleItem("trigger_assembly");
    public static final DeferredItem<Item> WEAPON_STOCK = ITEMS.registerSimpleItem("weapon_stock");
    public static final DeferredItem<Item> PRECISION_PARTS = ITEMS.registerSimpleItem("precision_parts",
            p -> p.rarity(Rarity.UNCOMMON));
    public static final DeferredItem<Item> OPTICAL_LENS = ITEMS.registerSimpleItem("optical_lens");
    public static final DeferredItem<Item> LASER_MODULE = ITEMS.registerSimpleItem("laser_module");
    public static final DeferredItem<Item> CIRCUIT_BOARD = ITEMS.registerSimpleItem("circuit_board");
    public static final DeferredItem<Item> EXPLOSIVE_COMPOUND = ITEMS.registerSimpleItem("explosive_compound");
    public static final DeferredItem<Item> ROCKET_MOTOR = ITEMS.registerSimpleItem("rocket_motor");
    public static final DeferredItem<Item> WARHEAD_CASING = ITEMS.registerSimpleItem("warhead_casing");
    public static final DeferredItem<Item> RAW_URANIUM = ITEMS.registerSimpleItem("raw_uranium");
    public static final DeferredItem<Item> URANIUM_INGOT = ITEMS.registerSimpleItem("uranium_ingot");
    public static final DeferredItem<Item> ENRICHED_URANIUM = ITEMS.registerSimpleItem("enriched_uranium",
            p -> p.rarity(Rarity.RARE));
    public static final DeferredItem<Item> PLUTONIUM_CORE = ITEMS.registerSimpleItem("plutonium_core",
            p -> p.rarity(Rarity.EPIC));

    // --- block items ---------------------------------------------------------------------------------------------
    public static final DeferredItem<BlockItem> WEAPON_WORKBENCH = ITEMS.registerSimpleBlockItem(ModBlocks.WEAPON_WORKBENCH);
    public static final DeferredItem<BlockItem> TACTICAL_NUKE = ITEMS.registerSimpleBlockItem(ModBlocks.TACTICAL_NUKE,
            p -> p.rarity(Rarity.EPIC));
    public static final DeferredItem<BlockItem> URANIUM_ORE = ITEMS.registerSimpleBlockItem(ModBlocks.URANIUM_ORE);
    public static final DeferredItem<BlockItem> DEEPSLATE_URANIUM_ORE = ITEMS.registerSimpleBlockItem(ModBlocks.DEEPSLATE_URANIUM_ORE);
    public static final DeferredItem<BlockItem> RAW_URANIUM_BLOCK = ITEMS.registerSimpleBlockItem(ModBlocks.RAW_URANIUM_BLOCK);
    public static final DeferredItem<BlockItem> URANIUM_BLOCK = ITEMS.registerSimpleBlockItem(ModBlocks.URANIUM_BLOCK);
    public static final DeferredItem<BlockItem> STEEL_BLOCK = ITEMS.registerSimpleBlockItem(ModBlocks.STEEL_BLOCK);

    // --- the arsenal itself --------------------------------------------------------------------------------------
    public static final Map<Caliber, DeferredItem<Item>> AMMO = new EnumMap<>(Caliber.class);
    public static final Map<GunType, DeferredItem<GunItem>> GUNS = new EnumMap<>(GunType.class);
    public static final Map<Attachment, DeferredItem<AttachmentItem>> ATTACHMENTS = new EnumMap<>(Attachment.class);
    public static final Map<Ordnance, DeferredItem<OrdnanceItem>> ORDNANCE_ITEMS = new EnumMap<>(Ordnance.class);

    static {
        for (Caliber caliber : Caliber.values()) {
            AMMO.put(caliber, ITEMS.registerSimpleItem(caliber.path(), p -> p.stacksTo(64)));
        }
        for (GunType type : GunType.values()) {
            Rarity rarity = switch (type.family()) {
                case PISTOL, SHOTGUN, RIFLE -> Rarity.UNCOMMON;
                case SNIPER, LAUNCHER -> Rarity.RARE;
                case RAILGUN -> Rarity.EPIC;
            };
            GUNS.put(type, ITEMS.registerItem(type.path(), p -> new GunItem(p, type), p -> p.stacksTo(1).rarity(rarity)));
        }
        for (Attachment attachment : Attachment.values()) {
            ATTACHMENTS.put(attachment, ITEMS.registerItem(attachment.path(),
                    p -> new AttachmentItem(p, attachment), p -> p.stacksTo(16).rarity(Rarity.UNCOMMON)));
        }
        for (Ordnance kind : Ordnance.values()) {
            if (!kind.thrownByHand()) {
                continue; // the 40mm round and the rocket are ammunition, registered above
            }
            Rarity rarity = kind.isWeaponOfMassDestruction() ? Rarity.EPIC : Rarity.UNCOMMON;
            int stack = kind.isWeaponOfMassDestruction() ? 1 : 16;
            ORDNANCE_ITEMS.put(kind, ITEMS.registerItem(kind.path(),
                    p -> new OrdnanceItem(p, kind), p -> p.stacksTo(stack).rarity(rarity)));
        }
    }

    /** Convenience for {@link com.afjan.arsenal.entity.OrdnanceEntity}'s default item. */
    public static final DeferredItem<OrdnanceItem> FRAG_GRENADE = ORDNANCE_ITEMS.get(Ordnance.FRAG);

    // --- aircraft ------------------------------------------------------------------------------------------------
    public static final DeferredItem<F14Item> F14_TOMCAT = ITEMS.registerItem("f14_tomcat", F14Item::new,
            p -> p.stacksTo(1).rarity(Rarity.EPIC));
    /** What loads onto the F-14's pylons (one item = one round; the rockets load into the pods one at a time). */
    public static final Map<Store, DeferredItem<StoreItem>> JET_STORES = new EnumMap<>(Store.class);

    static {
        for (Store store : Store.values()) {
            int stack = store == Store.ZUNI ? 16 : 4;
            JET_STORES.put(store, ITEMS.registerItem(store.itemPath(), p -> new StoreItem(p, store.itemPath()),
                    p -> p.stacksTo(stack).rarity(Rarity.RARE)));
        }
    }

    public static final DeferredItem<StoreItem> CANNON_SHELLS = ITEMS.registerItem("cannon_shells_20mm",
            p -> new StoreItem(p, "cannon_shells_20mm"), p -> p.stacksTo(16).rarity(Rarity.UNCOMMON));
    public static final DeferredItem<StoreItem> FLARE_CARTRIDGES = ITEMS.registerItem("flare_cartridges",
            p -> new StoreItem(p, "flare_cartridges"), p -> p.stacksTo(16).rarity(Rarity.UNCOMMON));

    private ModItems() {}
}
