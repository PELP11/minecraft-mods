package com.afjan.arsenal.craft;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import org.jspecify.annotations.Nullable;

import com.afjan.arsenal.combat.Ordnance;
import com.afjan.arsenal.gun.Attachment;
import com.afjan.arsenal.gun.Caliber;
import com.afjan.arsenal.gun.GunType;
import com.afjan.arsenal.registry.ModItems;
import com.afjan.arsenal.vehicle.Store;

import net.minecraft.tags.ItemTags;
import net.minecraft.util.Prediction;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Everything the Weapon Workbench can build. The catalogue lives in code rather than in recipe JSON because the
 * workbench shows it in its own browser: there is no hunting for recipes, and nothing here can be crafted on a
 * vanilla bench by accident.
 */
public final class Blueprints {
    public enum Category {
        MATERIALS, AMMO, ATTACHMENTS, WEAPONS, ORDNANCE, AIRCRAFT;

        public String translationKey() {
            return "gui.arsenal.category." + this.name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    /** One ingredient. {@code tag} is used when any item of a kind will do; {@code icon} is what the UI shows. */
    public record Need(Supplier<? extends Item> icon, @Nullable TagKey<Item> tag, int count) {
        public boolean matches(ItemStack stack) {
            return this.tag != null ? stack.is(this.tag) : stack.is(this.icon.get());
        }

        public ItemStack display() {
            return new ItemStack(this.icon.get(), this.count);
        }
    }

    public record Blueprint(Category category, Supplier<? extends Item> result, int count, List<Need> needs) {
        public ItemStack resultStack() {
            return new ItemStack(this.result.get(), this.count);
        }
    }

    public static final List<Blueprint> ALL = new ArrayList<>();

    private Blueprints() {}

    private static Need of(Supplier<? extends Item> item, int count) {
        return new Need(item, null, count);
    }

    private static Need of(Item item, int count) {
        return new Need(() -> item, null, count);
    }

    private static Need tag(TagKey<Item> tag, Item icon, int count) {
        return new Need(() -> icon, tag, count);
    }

    private static void add(Category category, Supplier<? extends Item> result, int count, Need... needs) {
        ALL.add(new Blueprint(category, result, count, List.of(needs)));
    }

    private static Supplier<? extends Item> gun(GunType type) {
        return ModItems.GUNS.get(type);
    }

    private static Supplier<? extends Item> ammo(Caliber caliber) {
        return ModItems.AMMO.get(caliber);
    }

    private static Supplier<? extends Item> part(Attachment attachment) {
        return ModItems.ATTACHMENTS.get(attachment);
    }

    private static Supplier<? extends Item> bomb(Ordnance kind) {
        return ModItems.ORDNANCE_ITEMS.get(kind);
    }

    static {
        // --- components ---------------------------------------------------------------------------------------
        add(Category.MATERIALS, ModItems.BRASS_CASING, 6,
                of(Items.COPPER_INGOT, 1), of(Items.GOLD_NUGGET, 1));
        add(Category.MATERIALS, ModItems.BULLET_TIP, 8,
                of(Items.IRON_INGOT, 1));
        add(Category.MATERIALS, ModItems.PROPELLANT, 4,
                of(Items.GUNPOWDER, 2), of(Items.BLAZE_POWDER, 1), of(Items.REDSTONE, 1));
        add(Category.MATERIALS, ModItems.GUN_BARREL, 1,
                of(ModItems.STEEL_INGOT, 3));
        add(Category.MATERIALS, ModItems.WEAPON_RECEIVER, 1,
                of(ModItems.STEEL_INGOT, 4), of(Items.REDSTONE, 1));
        add(Category.MATERIALS, ModItems.TRIGGER_ASSEMBLY, 1,
                of(ModItems.STEEL_INGOT, 2), of(Items.COPPER_INGOT, 1), of(Items.REDSTONE, 1));
        add(Category.MATERIALS, ModItems.WEAPON_STOCK, 1,
                tag(ItemTags.PLANKS, Items.OAK_PLANKS, 3), of(Items.LEATHER, 1), of(ModItems.STEEL_INGOT, 1));
        add(Category.MATERIALS, ModItems.PRECISION_PARTS, 1,
                of(ModItems.STEEL_INGOT, 2), of(Items.DIAMOND, 1), of(Items.REDSTONE, 1));
        add(Category.MATERIALS, ModItems.OPTICAL_LENS, 2,
                of(Items.GLASS, 3), of(Items.AMETHYST_SHARD, 1));
        add(Category.MATERIALS, ModItems.LASER_MODULE, 1,
                of(ModItems.OPTICAL_LENS, 1), of(Items.REDSTONE_BLOCK, 1), of(Items.GOLD_INGOT, 1));
        add(Category.MATERIALS, ModItems.CIRCUIT_BOARD, 1,
                of(Items.COPPER_INGOT, 2), of(Items.REDSTONE, 1), of(Items.QUARTZ, 1), of(Items.GOLD_NUGGET, 1));
        add(Category.MATERIALS, ModItems.EXPLOSIVE_COMPOUND, 2,
                of(Items.GUNPOWDER, 4), of(ModItems.PROPELLANT, 1), of(Items.CLAY_BALL, 1));
        add(Category.MATERIALS, ModItems.ROCKET_MOTOR, 1,
                of(ModItems.STEEL_INGOT, 2), of(ModItems.PROPELLANT, 2), of(Items.COPPER_INGOT, 1));
        add(Category.MATERIALS, ModItems.WARHEAD_CASING, 1,
                of(ModItems.STEEL_INGOT, 4), of(ModItems.EXPLOSIVE_COMPOUND, 1));
        add(Category.MATERIALS, ModItems.ENRICHED_URANIUM, 1,
                of(ModItems.URANIUM_INGOT, 4), of(Items.BLAZE_POWDER, 1), of(Items.REDSTONE_BLOCK, 1));
        add(Category.MATERIALS, ModItems.PLUTONIUM_CORE, 1,
                of(ModItems.ENRICHED_URANIUM, 6), of(Items.NETHERITE_INGOT, 1), of(ModItems.CIRCUIT_BOARD, 2));

        // --- ammunition ---------------------------------------------------------------------------------------
        add(Category.AMMO, ammo(Caliber.MM9), 16,
                of(ModItems.BRASS_CASING, 2), of(ModItems.PROPELLANT, 1), of(ModItems.BULLET_TIP, 2));
        add(Category.AMMO, ammo(Caliber.ACP45), 12,
                of(ModItems.BRASS_CASING, 2), of(ModItems.PROPELLANT, 1), of(ModItems.BULLET_TIP, 3));
        add(Category.AMMO, ammo(Caliber.MM556), 16,
                of(ModItems.BRASS_CASING, 3), of(ModItems.PROPELLANT, 1), of(ModItems.BULLET_TIP, 2));
        add(Category.AMMO, ammo(Caliber.MM762), 12,
                of(ModItems.BRASS_CASING, 3), of(ModItems.PROPELLANT, 2), of(ModItems.BULLET_TIP, 3));
        add(Category.AMMO, ammo(Caliber.AE50), 8,
                of(ModItems.BRASS_CASING, 4), of(ModItems.PROPELLANT, 2), of(ModItems.BULLET_TIP, 3));
        add(Category.AMMO, ammo(Caliber.LAPUA338), 6,
                of(ModItems.BRASS_CASING, 4), of(ModItems.PROPELLANT, 3), of(ModItems.BULLET_TIP, 4),
                of(ModItems.STEEL_INGOT, 1));
        add(Category.AMMO, ammo(Caliber.BMG50), 6,
                of(ModItems.BRASS_CASING, 5), of(ModItems.PROPELLANT, 3), of(ModItems.BULLET_TIP, 4),
                of(ModItems.STEEL_INGOT, 2));
        add(Category.AMMO, ammo(Caliber.BUCKSHOT), 12,
                of(ModItems.BRASS_CASING, 3), of(ModItems.PROPELLANT, 2), of(ModItems.BULLET_TIP, 4));
        add(Category.AMMO, ammo(Caliber.SLUG), 8,
                of(ModItems.BRASS_CASING, 3), of(ModItems.PROPELLANT, 2), of(ModItems.STEEL_INGOT, 2));
        add(Category.AMMO, ammo(Caliber.GRENADE40), 4,
                of(ModItems.WARHEAD_CASING, 2), of(ModItems.PROPELLANT, 2), of(ModItems.EXPLOSIVE_COMPOUND, 2));
        add(Category.AMMO, ammo(Caliber.ROCKET), 2,
                of(ModItems.ROCKET_MOTOR, 1), of(ModItems.WARHEAD_CASING, 1), of(ModItems.EXPLOSIVE_COMPOUND, 2));
        // the fuel-air round: one at a time, and it wants a real fuel charge
        add(Category.AMMO, ammo(Caliber.ROCKET_TBG), 1,
                of(ModItems.ROCKET_MOTOR, 1), of(ModItems.WARHEAD_CASING, 2), of(ModItems.EXPLOSIVE_COMPOUND, 4),
                of(Items.BLAZE_POWDER, 4), of(Items.FIRE_CHARGE, 2));
        add(Category.AMMO, ammo(Caliber.RAILSLUG), 4,
                of(ModItems.STEEL_INGOT, 4), of(Items.NETHERITE_SCRAP, 1), of(ModItems.PRECISION_PARTS, 1));

        // --- attachments --------------------------------------------------------------------------------------
        add(Category.ATTACHMENTS, part(Attachment.SUPPRESSOR), 1,
                of(ModItems.STEEL_INGOT, 4), of(ModItems.PRECISION_PARTS, 1));
        add(Category.ATTACHMENTS, part(Attachment.MUZZLE_BRAKE), 1,
                of(ModItems.STEEL_INGOT, 3), of(ModItems.PRECISION_PARTS, 1));
        add(Category.ATTACHMENTS, part(Attachment.HEAVY_BARREL), 1,
                of(ModItems.GUN_BARREL, 1), of(ModItems.STEEL_INGOT, 4), of(ModItems.PRECISION_PARTS, 1));
        add(Category.ATTACHMENTS, part(Attachment.RED_DOT), 1,
                of(ModItems.OPTICAL_LENS, 1), of(ModItems.LASER_MODULE, 1), of(ModItems.STEEL_INGOT, 2));
        add(Category.ATTACHMENTS, part(Attachment.ACOG_SCOPE), 1,
                of(ModItems.OPTICAL_LENS, 2), of(ModItems.STEEL_INGOT, 3), of(ModItems.PRECISION_PARTS, 1));
        add(Category.ATTACHMENTS, part(Attachment.THERMAL_SCOPE), 1,
                of(ModItems.OPTICAL_LENS, 2), of(ModItems.CIRCUIT_BOARD, 2), of(ModItems.LASER_MODULE, 1),
                of(ModItems.STEEL_INGOT, 4));
        add(Category.ATTACHMENTS, part(Attachment.EXTENDED_MAG), 1,
                of(ModItems.STEEL_INGOT, 3), of(ModItems.TRIGGER_ASSEMBLY, 1));
        add(Category.ATTACHMENTS, part(Attachment.DRUM_MAG), 1,
                of(ModItems.STEEL_INGOT, 6), of(ModItems.TRIGGER_ASSEMBLY, 1), of(ModItems.PRECISION_PARTS, 1));
        add(Category.ATTACHMENTS, part(Attachment.QUICKDRAW_MAG), 1,
                of(ModItems.STEEL_INGOT, 3), of(ModItems.PRECISION_PARTS, 1), of(Items.COPPER_INGOT, 1));
        add(Category.ATTACHMENTS, part(Attachment.LASER_SIGHT), 1,
                of(ModItems.LASER_MODULE, 1), of(ModItems.STEEL_INGOT, 2), of(Items.REDSTONE, 1));
        add(Category.ATTACHMENTS, part(Attachment.FOREGRIP), 1,
                of(ModItems.STEEL_INGOT, 2), tag(ItemTags.PLANKS, Items.OAK_PLANKS, 2));
        add(Category.ATTACHMENTS, part(Attachment.BIPOD), 1,
                of(ModItems.STEEL_INGOT, 4), of(ModItems.PRECISION_PARTS, 1));

        // --- weapons ------------------------------------------------------------------------------------------
        add(Category.WEAPONS, gun(GunType.GLOCK17), 1,
                of(ModItems.GUN_BARREL, 1), of(ModItems.WEAPON_RECEIVER, 1), of(ModItems.TRIGGER_ASSEMBLY, 1),
                of(ModItems.STEEL_INGOT, 2));
        add(Category.WEAPONS, gun(GunType.M9), 1,
                of(ModItems.GUN_BARREL, 1), of(ModItems.WEAPON_RECEIVER, 1), of(ModItems.TRIGGER_ASSEMBLY, 1),
                of(ModItems.STEEL_INGOT, 3));
        add(Category.WEAPONS, gun(GunType.M1911), 1,
                of(ModItems.GUN_BARREL, 1), of(ModItems.WEAPON_RECEIVER, 1), of(ModItems.TRIGGER_ASSEMBLY, 1),
                of(ModItems.STEEL_INGOT, 4), of(Items.GOLD_INGOT, 1));
        add(Category.WEAPONS, gun(GunType.DEAGLE), 1,
                of(ModItems.GUN_BARREL, 2), of(ModItems.WEAPON_RECEIVER, 1), of(ModItems.TRIGGER_ASSEMBLY, 1),
                of(ModItems.STEEL_INGOT, 6), of(Items.GOLD_INGOT, 2), of(ModItems.PRECISION_PARTS, 1));

        add(Category.WEAPONS, gun(GunType.AK47), 1,
                of(ModItems.GUN_BARREL, 2), of(ModItems.WEAPON_RECEIVER, 1), of(ModItems.TRIGGER_ASSEMBLY, 1),
                of(ModItems.WEAPON_STOCK, 1), of(ModItems.STEEL_INGOT, 8), of(ModItems.PRECISION_PARTS, 1));
        add(Category.WEAPONS, gun(GunType.M4A1), 1,
                of(ModItems.GUN_BARREL, 2), of(ModItems.WEAPON_RECEIVER, 2), of(ModItems.TRIGGER_ASSEMBLY, 1),
                of(ModItems.WEAPON_STOCK, 1), of(ModItems.STEEL_INGOT, 8), of(ModItems.PRECISION_PARTS, 2));
        add(Category.WEAPONS, gun(GunType.AUG), 1,
                of(ModItems.GUN_BARREL, 2), of(ModItems.WEAPON_RECEIVER, 2), of(ModItems.TRIGGER_ASSEMBLY, 1),
                of(ModItems.WEAPON_STOCK, 1), of(ModItems.STEEL_INGOT, 10), of(ModItems.PRECISION_PARTS, 2),
                of(ModItems.OPTICAL_LENS, 1));
        add(Category.WEAPONS, gun(GunType.SCAR_H), 1,
                of(ModItems.GUN_BARREL, 3), of(ModItems.WEAPON_RECEIVER, 2), of(ModItems.TRIGGER_ASSEMBLY, 1),
                of(ModItems.WEAPON_STOCK, 1), of(ModItems.STEEL_INGOT, 12), of(ModItems.PRECISION_PARTS, 3));

        add(Category.WEAPONS, gun(GunType.SAWED_OFF), 1,
                of(ModItems.GUN_BARREL, 2), of(ModItems.WEAPON_RECEIVER, 1), of(ModItems.TRIGGER_ASSEMBLY, 1),
                of(ModItems.STEEL_INGOT, 4));
        add(Category.WEAPONS, gun(GunType.REMINGTON870), 1,
                of(ModItems.GUN_BARREL, 2), of(ModItems.WEAPON_RECEIVER, 1), of(ModItems.TRIGGER_ASSEMBLY, 1),
                of(ModItems.WEAPON_STOCK, 1), of(ModItems.STEEL_INGOT, 6));
        add(Category.WEAPONS, gun(GunType.SPAS12), 1,
                of(ModItems.GUN_BARREL, 2), of(ModItems.WEAPON_RECEIVER, 2), of(ModItems.TRIGGER_ASSEMBLY, 1),
                of(ModItems.WEAPON_STOCK, 1), of(ModItems.STEEL_INGOT, 10), of(ModItems.PRECISION_PARTS, 1));
        add(Category.WEAPONS, gun(GunType.AA12), 1,
                of(ModItems.GUN_BARREL, 3), of(ModItems.WEAPON_RECEIVER, 2), of(ModItems.TRIGGER_ASSEMBLY, 2),
                of(ModItems.WEAPON_STOCK, 1), of(ModItems.STEEL_INGOT, 14), of(ModItems.PRECISION_PARTS, 3));

        add(Category.WEAPONS, gun(GunType.SVD), 1,
                of(ModItems.GUN_BARREL, 3), of(ModItems.WEAPON_RECEIVER, 2), of(ModItems.TRIGGER_ASSEMBLY, 1),
                of(ModItems.WEAPON_STOCK, 1), of(ModItems.STEEL_INGOT, 12), of(ModItems.PRECISION_PARTS, 2),
                of(ModItems.OPTICAL_LENS, 1));
        add(Category.WEAPONS, gun(GunType.AWP), 1,
                of(ModItems.GUN_BARREL, 4), of(ModItems.WEAPON_RECEIVER, 2), of(ModItems.TRIGGER_ASSEMBLY, 1),
                of(ModItems.WEAPON_STOCK, 1), of(ModItems.STEEL_INGOT, 18), of(ModItems.PRECISION_PARTS, 4),
                of(ModItems.OPTICAL_LENS, 2), of(Items.NETHERITE_INGOT, 1));
        add(Category.WEAPONS, gun(GunType.BARRETT), 1,
                of(ModItems.GUN_BARREL, 4), of(ModItems.WEAPON_RECEIVER, 3), of(ModItems.TRIGGER_ASSEMBLY, 1),
                of(ModItems.WEAPON_STOCK, 1), of(ModItems.STEEL_INGOT, 20), of(ModItems.PRECISION_PARTS, 4),
                of(ModItems.OPTICAL_LENS, 1), of(Items.NETHERITE_INGOT, 1));

        add(Category.WEAPONS, gun(GunType.RPG7), 1,
                of(ModItems.GUN_BARREL, 3), of(ModItems.WEAPON_RECEIVER, 2), of(ModItems.TRIGGER_ASSEMBLY, 1),
                of(ModItems.STEEL_INGOT, 16), of(ModItems.PRECISION_PARTS, 2), of(ModItems.ROCKET_MOTOR, 1));
        add(Category.WEAPONS, gun(GunType.M32), 1,
                of(ModItems.GUN_BARREL, 4), of(ModItems.WEAPON_RECEIVER, 2), of(ModItems.TRIGGER_ASSEMBLY, 2),
                of(ModItems.WEAPON_STOCK, 1), of(ModItems.STEEL_INGOT, 18), of(ModItems.PRECISION_PARTS, 3));
        add(Category.WEAPONS, gun(GunType.RAILGUN), 1,
                of(ModItems.GUN_BARREL, 2), of(ModItems.WEAPON_RECEIVER, 3), of(ModItems.TRIGGER_ASSEMBLY, 1),
                of(ModItems.CIRCUIT_BOARD, 6), of(ModItems.PRECISION_PARTS, 4), of(Items.NETHERITE_INGOT, 2),
                of(ModItems.LASER_MODULE, 1), of(ModItems.STEEL_INGOT, 16));

        // --- grenades and weapons of mass destruction ----------------------------------------------------------
        add(Category.ORDNANCE, bomb(Ordnance.FRAG), 2,
                of(ModItems.STEEL_INGOT, 3), of(ModItems.EXPLOSIVE_COMPOUND, 2), of(ModItems.PROPELLANT, 1));
        add(Category.ORDNANCE, bomb(Ordnance.INCENDIARY), 2,
                of(ModItems.STEEL_INGOT, 2), of(ModItems.EXPLOSIVE_COMPOUND, 1), of(Items.BLAZE_POWDER, 2),
                of(Items.MAGMA_CREAM, 1));
        add(Category.ORDNANCE, bomb(Ordnance.FLASHBANG), 2,
                of(ModItems.STEEL_INGOT, 2), of(ModItems.PROPELLANT, 1), of(Items.GLOWSTONE_DUST, 2),
                of(Items.GUNPOWDER, 1));
        add(Category.ORDNANCE, bomb(Ordnance.SMOKE), 2,
                of(ModItems.STEEL_INGOT, 2), of(ModItems.PROPELLANT, 1), of(Items.COAL, 2), of(Items.GUNPOWDER, 1));

        add(Category.ORDNANCE, bomb(Ordnance.SINGULARITY), 1,
                of(Items.NETHER_STAR, 1), of(Items.ENDER_PEARL, 8), of(ModItems.WARHEAD_CASING, 4),
                of(ModItems.EXPLOSIVE_COMPOUND, 6), of(ModItems.CIRCUIT_BOARD, 2), of(ModItems.ENRICHED_URANIUM, 4));
        add(Category.ORDNANCE, bomb(Ordnance.THERMOBARIC), 1,
                of(ModItems.WARHEAD_CASING, 6), of(ModItems.EXPLOSIVE_COMPOUND, 12), of(Items.BLAZE_POWDER, 8),
                of(ModItems.PROPELLANT, 4), of(ModItems.CIRCUIT_BOARD, 2), of(Items.NETHERITE_INGOT, 1));
        add(Category.ORDNANCE, bomb(Ordnance.ION_BEACON), 1,
                of(Items.BEACON, 1), of(ModItems.CIRCUIT_BOARD, 4), of(ModItems.LASER_MODULE, 2),
                of(ModItems.OPTICAL_LENS, 4), of(ModItems.STEEL_INGOT, 8), of(ModItems.PRECISION_PARTS, 2),
                of(Items.NETHER_STAR, 1));
        add(Category.ORDNANCE, bomb(Ordnance.CHEMICAL), 1,
                of(ModItems.WARHEAD_CASING, 4), of(ModItems.EXPLOSIVE_COMPOUND, 8),
                of(Items.FERMENTED_SPIDER_EYE, 6), of(ModItems.ENRICHED_URANIUM, 4),
                of(ModItems.CIRCUIT_BOARD, 2), of(Items.POISONOUS_POTATO, 8));

        add(Category.ORDNANCE, ModItems.TACTICAL_NUKE, 1,
                of(ModItems.PLUTONIUM_CORE, 6), of(ModItems.ENRICHED_URANIUM, 8), of(ModItems.STEEL_INGOT, 16),
                of(ModItems.CIRCUIT_BOARD, 4), of(Items.NETHER_STAR, 1), of(ModItems.EXPLOSIVE_COMPOUND, 8),
                of(ModItems.WARHEAD_CASING, 4), of(ModItems.PRECISION_PARTS, 4));

        // --- the F-14 and what it carries -------------------------------------------------------------------------
        // a whole fighter: the airframe, avionics, the M61's six barrels, turbine blades, the canopy and the wing
        add(Category.AIRCRAFT, ModItems.F14_TOMCAT, 1,
                of(ModItems.STEEL_INGOT, 48), of(ModItems.CIRCUIT_BOARD, 12), of(ModItems.PRECISION_PARTS, 8),
                of(ModItems.GUN_BARREL, 6), of(ModItems.ROCKET_MOTOR, 2), of(Items.NETHERITE_INGOT, 2),
                of(Items.GLASS, 6), of(Items.ELYTRA, 1));
        add(Category.AIRCRAFT, store(Store.AIM9), 1,
                of(ModItems.ROCKET_MOTOR, 1), of(ModItems.WARHEAD_CASING, 1), of(ModItems.EXPLOSIVE_COMPOUND, 2),
                of(ModItems.CIRCUIT_BOARD, 1), of(ModItems.OPTICAL_LENS, 1));
        add(Category.AIRCRAFT, store(Store.AIM54), 1,
                of(ModItems.ROCKET_MOTOR, 2), of(ModItems.WARHEAD_CASING, 2), of(ModItems.EXPLOSIVE_COMPOUND, 4),
                of(ModItems.CIRCUIT_BOARD, 3), of(ModItems.LASER_MODULE, 1), of(ModItems.STEEL_INGOT, 4));
        add(Category.AIRCRAFT, store(Store.ZUNI), 4,
                of(ModItems.ROCKET_MOTOR, 2), of(ModItems.WARHEAD_CASING, 1), of(ModItems.EXPLOSIVE_COMPOUND, 2),
                of(ModItems.STEEL_INGOT, 2));
        add(Category.AIRCRAFT, store(Store.MK82), 1,
                of(ModItems.WARHEAD_CASING, 3), of(ModItems.EXPLOSIVE_COMPOUND, 6), of(ModItems.STEEL_INGOT, 4));
        add(Category.AIRCRAFT, ModItems.CANNON_SHELLS, 1,
                of(ModItems.BRASS_CASING, 12), of(ModItems.PROPELLANT, 6), of(ModItems.BULLET_TIP, 12),
                of(ModItems.STEEL_INGOT, 2));
        add(Category.AIRCRAFT, ModItems.FLARE_CARTRIDGES, 2,
                of(ModItems.BRASS_CASING, 2), of(ModItems.PROPELLANT, 2), of(Items.GLOWSTONE_DUST, 2),
                of(Items.BLAZE_POWDER, 1));
    }

    private static Supplier<? extends Item> store(Store store) {
        return ModItems.JET_STORES.get(store);
    }

    public static @Nullable Blueprint byIndex(int index) {
        return index >= 0 && index < ALL.size() ? ALL.get(index) : null;
    }

    public static int indexOf(Blueprint blueprint) {
        return ALL.indexOf(blueprint);
    }

    /** How many of this blueprint the player could build right now, capped at {@code limit}. */
    public static int affordable(Player player, Blueprint blueprint, int limit) {
        if (player.getAbilities().instabuild) {
            return limit;
        }
        int possible = limit;
        for (Need need : blueprint.needs()) {
            possible = Math.min(possible, count(player.getInventory(), need) / need.count());
            if (possible <= 0) {
                return 0;
            }
        }
        return possible;
    }

    public static int count(Inventory inventory, Need need) {
        int total = 0;
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (need.matches(stack)) {
                total += stack.getCount();
            }
        }
        return total;
    }

    /**
     * Takes the materials and hands over the goods.
     *
     * @return the number of times the blueprint was actually built
     */
    public static int build(Player player, Blueprint blueprint, int times) {
        int runs = Math.min(times, affordable(player, blueprint, times));
        if (runs <= 0) {
            return 0;
        }
        if (!player.getAbilities().instabuild) {
            for (Need need : blueprint.needs()) {
                take(player.getInventory(), need, need.count() * runs);
            }
        }
        for (int i = 0; i < runs; i++) {
            ItemStack result = blueprint.resultStack();
            if (!player.getInventory().add(result) && !result.isEmpty()) {
                player.drop(result, false, Prediction.SERVER_ONLY);
            }
        }
        return runs;
    }

    private static void take(Inventory inventory, Need need, int wanted) {
        for (int slot = 0; slot < inventory.getContainerSize() && wanted > 0; slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (need.matches(stack)) {
                int move = Math.min(stack.getCount(), wanted);
                stack.shrink(move);
                wanted -= move;
            }
        }
    }
}
