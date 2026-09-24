package com.afjan.drillworks.item;

import java.util.function.Consumer;

import com.afjan.drillworks.drill.DrillModules;
import com.afjan.drillworks.drill.HeadMaterial;
import com.afjan.drillworks.drill.Module;
import com.afjan.drillworks.registry.ModComponents;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

/** A drill head for the Mining Drill. Modules are socketed in the drill's menu and stay on the head. */
public class DrillHeadItem extends Item {
    private final HeadMaterial material;

    public DrillHeadItem(HeadMaterial material, Item.Properties properties) {
        super(properties);
        this.material = material;
    }

    public HeadMaterial material() {
        return this.material;
    }

    public static DrillModules modules(ItemStack stack) {
        return stack.getOrDefault(ModComponents.MODULES.get(), DrillModules.EMPTY);
    }

    /** Worn out: one point of durability left. The head stops drilling instead of breaking and losing its modules. */
    public static boolean isWorn(ItemStack stack) {
        return stack.isDamageableItem() && stack.getDamageValue() >= stack.getMaxDamage() - 1;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
            Consumer<Component> builder, TooltipFlag flag) {
        super.appendHoverText(stack, context, display, builder, flag);
        builder.accept(Component.translatable("tooltip.drillworks.head_stats",
                String.format(java.util.Locale.ROOT, "%.1f", this.material.speed()), this.material.sockets())
                .withStyle(ChatFormatting.GRAY));
        if (this.material.trait() != HeadMaterial.Trait.NONE) {
            builder.accept(Component.translatable("tooltip.drillworks.trait." + this.material.trait().key())
                    .withStyle(ChatFormatting.GOLD));
        }
        DrillModules modules = modules(stack);
        for (int i = 0; i < this.material.sockets(); i++) {
            Module module = modules.get(i);
            builder.accept(module == null
                    ? Component.translatable("tooltip.drillworks.empty_socket").withStyle(ChatFormatting.DARK_GRAY)
                    : Component.literal(" + ").append(Component.translatable("item.drillworks." + module.itemName()))
                            .withStyle(ChatFormatting.AQUA));
        }
        if (isWorn(stack)) {
            builder.accept(Component.translatable("tooltip.drillworks.worn").withStyle(ChatFormatting.RED));
        }
    }
}
