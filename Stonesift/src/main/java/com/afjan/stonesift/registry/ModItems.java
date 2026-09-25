package com.afjan.stonesift.registry;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;

import com.afjan.stonesift.Stonesift;
import com.afjan.stonesift.item.RockItem;
import com.afjan.stonesift.item.SimpleItems;
import com.afjan.stonesift.rock.Rock;

import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.ToolMaterial;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Stonesift.MODID);

    /** Gravel, rock flour and fine slurry per rock type. */
    public static final Map<RockItem.Stage, Map<Rock, DeferredItem<RockItem>>> ROCK_ITEMS = new EnumMap<>(RockItem.Stage.class);

    static {
        for (RockItem.Stage stage : RockItem.Stage.values()) {
            Map<Rock, DeferredItem<RockItem>> map = new EnumMap<>(Rock.class);
            for (Rock rock : Rock.values()) {
                map.put(rock, ITEMS.registerItem(rock.key() + stage.suffix(), p -> new RockItem(rock, stage, p)));
            }
            ROCK_ITEMS.put(stage, map);
        }
    }

    public static final DeferredItem<Item> IRON_FRAGMENT = ITEMS.registerSimpleItem("iron_ore_fragment");
    public static final DeferredItem<Item> COPPER_FRAGMENT = ITEMS.registerSimpleItem("copper_ore_fragment");
    public static final DeferredItem<Item> GOLD_FRAGMENT = ITEMS.registerSimpleItem("gold_ore_fragment");
    public static final DeferredItem<Item> NETHERITE_FRAGMENT = ITEMS.registerSimpleItem("netherite_fragment",
            p -> p.fireResistant().rarity(Rarity.UNCOMMON));
    public static final DeferredItem<Item> DIAMOND_SHARD = ITEMS.registerSimpleItem("diamond_shard");
    public static final DeferredItem<Item> EMERALD_SHARD = ITEMS.registerSimpleItem("emerald_shard");
    public static final DeferredItem<Item> IRON_CONCENTRATE = ITEMS.registerSimpleItem("iron_concentrate");
    public static final DeferredItem<Item> COPPER_CONCENTRATE = ITEMS.registerSimpleItem("copper_concentrate");
    public static final DeferredItem<Item> GOLD_CONCENTRATE = ITEMS.registerSimpleItem("gold_concentrate");
    public static final DeferredItem<Item> DIAMOND_CONCENTRATE = ITEMS.registerSimpleItem("diamond_concentrate",
            p -> p.rarity(Rarity.UNCOMMON));

    public static final DeferredItem<SimpleItems.Mesh> STRING_MESH = ITEMS.registerItem("string_mesh",
            p -> new SimpleItems.Mesh(1, p), p -> p.durability(256));
    public static final DeferredItem<SimpleItems.Mesh> IRON_MESH = ITEMS.registerItem("iron_mesh",
            p -> new SimpleItems.Mesh(2, p), p -> p.durability(1024));
    public static final DeferredItem<SimpleItems.Mesh> DIAMOND_MESH = ITEMS.registerItem("diamond_mesh",
            p -> new SimpleItems.Mesh(3, p), p -> p.durability(4096).rarity(Rarity.UNCOMMON));

    /** Stone hammers, wood to diamond: a pickaxe of the same tier, 25% slower. */
    public static final Map<String, DeferredItem<SimpleItems.StoneHammer>> HAMMERS = new LinkedHashMap<>();

    static {
        hammer("wooden", ToolMaterial.WOOD);
        hammer("stone", ToolMaterial.STONE);
        hammer("iron", ToolMaterial.IRON);
        hammer("golden", ToolMaterial.GOLD);
        hammer("diamond", ToolMaterial.DIAMOND);
    }

    private static void hammer(String name, ToolMaterial m) {
        ToolMaterial slower = new ToolMaterial(m.incorrectBlocksForDrops(), m.durability(), m.speed() * 0.75F,
                m.attackDamageBonus(), m.enchantmentValue(), m.repairItems());
        HAMMERS.put(name, ITEMS.registerItem(name + "_stone_hammer", SimpleItems.StoneHammer::new,
                p -> p.pickaxe(slower, 2.0F, -3.0F)));
    }

    public static final DeferredItem<SimpleItems.GeologistHammer> GEOLOGIST_HAMMER = ITEMS.registerItem("geologist_hammer",
            SimpleItems.GeologistHammer::new, p -> p.durability(250));
    public static final DeferredItem<SimpleItems.Hinted> SPEED_UPGRADE = ITEMS.registerItem("speed_upgrade",
            p -> new SimpleItems.Hinted("speed_upgrade", p), p -> p.stacksTo(1));
    public static final DeferredItem<SimpleItems.Hinted> SPEED_UPGRADE_ADVANCED = ITEMS.registerItem("advanced_speed_upgrade",
            p -> new SimpleItems.Hinted("advanced_speed_upgrade", p), p -> p.stacksTo(1).rarity(Rarity.UNCOMMON));
    public static final DeferredItem<SimpleItems.Hinted> DIAMOND_DRILL_BIT = ITEMS.registerItem("diamond_drill_bit",
            p -> new SimpleItems.Hinted("drill_bit", p), p -> p.durability(600));
    public static final DeferredItem<SimpleItems.Hinted> NETHERITE_DRILL_BIT = ITEMS.registerItem("netherite_drill_bit",
            p -> new SimpleItems.Hinted("drill_bit", p), p -> p.durability(2400).fireResistant().rarity(Rarity.RARE));

    public static final Map<String, DeferredItem<BlockItem>> BLOCK_ITEMS = new LinkedHashMap<>();

    static {
        for (DeferredBlock<?> block : ModBlocks.ALL) {
            BLOCK_ITEMS.put(block.getId().getPath(), ITEMS.registerSimpleBlockItem(block));
        }
    }

    private ModItems() {}
}
