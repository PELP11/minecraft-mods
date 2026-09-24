package com.afjan.juicer.registry;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.afjan.juicer.Juicer;
import com.afjan.juicer.fruit.Fruit;
import com.afjan.juicer.fruit.JuiceRecipes;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.storage.loot.providers.number.ints.ContextIntProviders;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Juicer.MODID);

    public static final Map<Fruit, DeferredItem<Item>> FRUITS = new EnumMap<>(Fruit.class);
    public static final Map<Fruit, DeferredItem<Item>> JUICES = new EnumMap<>(Fruit.class);
    public static final Map<Fruit, DeferredItem<BlockItem>> SAPLINGS = new EnumMap<>(Fruit.class);
    public static final Map<Fruit, DeferredItem<BlockItem>> LEAVES = new EnumMap<>(Fruit.class);

    private static final FoodProperties FRUIT_FOOD = new FoodProperties.Builder().nutrition(4).saturationModifier(0.3F).build();
    private static final Style LORE_STYLE = Style.EMPTY.withItalic(false).withColor(ChatFormatting.GRAY);

    static {
        for (Fruit fruit : Fruit.values()) {
            FRUITS.put(fruit, ITEMS.registerSimpleItem(fruit.id(),
                    p -> p.food(FRUIT_FOOD).compostable(ContextIntProviders.COMPOSTABLE_MEDIUM)));

            JUICES.put(fruit, ITEMS.registerSimpleItem(fruit.id() + "_juice", p -> p
                    .stacksTo(16)
                    .rarity(Rarity.EPIC)
                    .component(DataComponents.POTION_CONTENTS, new PotionContents(
                            Optional.empty(), Optional.of(0xFF000000 | fruit.color()), JuiceRecipes.effectsFor(fruit), Optional.empty()))
                    .component(DataComponents.CONSUMABLE, JuiceRecipes.consumableFor(fruit))
                    .component(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true)
                    .component(DataComponents.LORE, lore("item.juicer." + fruit.id() + "_juice.desc"))
                    .usingConvertsTo(Items.GLASS_BOTTLE)));

            SAPLINGS.put(fruit, ITEMS.registerSimpleBlockItem(fruit.id() + "_sapling", ModBlocks.SAPLINGS.get(fruit),
                    p -> p.compostable(ContextIntProviders.COMPOSTABLE_LOW)));
            LEAVES.put(fruit, ITEMS.registerSimpleBlockItem(fruit.id() + "_leaves", ModBlocks.LEAVES.get(fruit),
                    p -> p.compostable(ContextIntProviders.COMPOSTABLE_LOW)));
        }
    }

    public static final DeferredItem<BlockItem> MIXER = ITEMS.registerSimpleBlockItem("mixer", ModBlocks.MIXER,
            p -> p.component(DataComponents.LORE, lore("block.juicer.mixer.desc1", "block.juicer.mixer.desc2")));
    public static final DeferredItem<BlockItem> INFUSER = ITEMS.registerSimpleBlockItem("infuser", ModBlocks.INFUSER,
            p -> p.component(DataComponents.LORE, lore("block.juicer.infuser.desc1", "block.juicer.infuser.desc2")));
    public static final DeferredItem<BlockItem> TUBING = ITEMS.registerSimpleBlockItem("tubing", ModBlocks.TUBING,
            p -> p.component(DataComponents.LORE, lore("block.juicer.tubing.desc1")));

    private static ItemLore lore(String... keys) {
        return new ItemLore(java.util.Arrays.stream(keys)
                .map(key -> (Component) Component.translatable(key).withStyle(LORE_STYLE))
                .toList());
    }

    private ModItems() {}
}
