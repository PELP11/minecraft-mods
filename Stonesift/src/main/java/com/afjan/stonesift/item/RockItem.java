package com.afjan.stonesift.item;

import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

import org.jetbrains.annotations.Nullable;

import com.afjan.stonesift.registry.ModComponents;
import com.afjan.stonesift.registry.ModItems;
import com.afjan.stonesift.rock.Resource;
import com.afjan.stonesift.rock.Rock;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

/** Gravel, rock flour or fine slurry of one rock type. "Rich" material from the Deep Drill yields three times as much. */
public class RockItem extends Item {
    public enum Stage {
        GRAVEL, FLOUR, SLURRY;

        public String suffix() {
            return switch (this) {
                case GRAVEL -> "_gravel";
                case FLOUR -> "_rock_flour";
                case SLURRY -> "_fine_slurry";
            };
        }
    }

    private final Rock rock;
    private final Stage stage;

    public RockItem(Rock rock, Stage stage, Item.Properties properties) {
        super(properties);
        this.rock = rock;
        this.stage = stage;
    }

    public Rock rock() {
        return this.rock;
    }

    public Stage stage() {
        return this.stage;
    }

    public static boolean isRich(ItemStack stack) {
        return stack.getOrDefault(ModComponents.RICH.get(), false);
    }

    public static @Nullable RockItem of(ItemStack stack, Stage stage) {
        return stack.getItem() instanceof RockItem item && item.stage == stage ? item : null;
    }

    public static ItemStack stack(Stage stage, Rock rock, boolean rich, int count) {
        ItemStack stack = new ItemStack(ModItems.ROCK_ITEMS.get(stage).get(rock).get(), count);
        if (rich) {
            stack.set(ModComponents.RICH.get(), true);
            stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
        }
        return stack;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
            Consumer<Component> builder, TooltipFlag flag) {
        super.appendHoverText(stack, context, display, builder, flag);
        if (isRich(stack)) {
            builder.accept(Component.translatable("tooltip.stonesift.rich").withStyle(ChatFormatting.GOLD));
        }
        StringBuilder main = new StringBuilder();
        for (Map.Entry<Resource, Double> e : this.rock.profile().entrySet()) {
            builder.accept(Component.literal(" ").append(Component.translatable("resource.stonesift." + e.getKey().key()))
                    .append(String.format(Locale.ROOT, "  %.0f%%", e.getValue() * 100))
                    .withStyle(e.getKey() == this.rock.main() ? ChatFormatting.GRAY : ChatFormatting.DARK_GRAY));
        }
    }
}
