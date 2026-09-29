package com.afjan.tempered.mastery;

import com.afjan.tempered.Tempered;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;

import java.util.function.BiConsumer;

/** Mastery perks that are plain attribute modifiers of the held item. */
public final class MasteryAttributes {
    public static final Identifier ATTACK_SPEED_ID = Identifier.fromNamespaceAndPath(Tempered.MODID, "mastery_attack_speed");

    private MasteryAttributes() {
    }

    public static void forEach(ItemStack stack, BiConsumer<Holder<Attribute>, AttributeModifier> consumer) {
        if (stack.isEmpty()) return;
        double attackSpeed = Mastery.perk(stack, Perk.ATTACK_SPEED);
        if (attackSpeed > 0) {
            consumer.accept(Attributes.ATTACK_SPEED,
                    new AttributeModifier(ATTACK_SPEED_ID, attackSpeed / 100.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        }
    }
}
