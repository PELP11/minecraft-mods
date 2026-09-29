package com.afjan.tempered.mixin;

import com.afjan.tempered.event.BrokenTools;
import com.afjan.tempered.mastery.MasteryAttributes;
import net.minecraft.advancements.triggers.CriteriaTriggers;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import org.apache.commons.lang3.function.TriConsumer;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.BiConsumer;
import java.util.function.Consumer;

@Mixin(ItemStack.class)
public abstract class ItemStackMixin {

    /** A tool at 0 durability stays in the inventory as Broken instead of being destroyed. */
    @Inject(method = "applyDamage", at = @At("HEAD"), cancellable = true)
    private void tempered$keepBrokenTools(int newDamage, @Nullable ServerPlayer player, Consumer<ItemStack> onBreak, CallbackInfo ci) {
        ItemStack self = (ItemStack) (Object) this;
        if (newDamage < self.getMaxDamage() || !BrokenTools.keepsWhenBroken(self)) return;
        boolean wasBroken = self.isBroken();
        if (player != null) CriteriaTriggers.ITEM_DURABILITY_CHANGED.trigger(player, self, self.getMaxDamage());
        self.setDamageValue(self.getMaxDamage());
        if (!wasBroken) BrokenTools.onBroke(self, player);
        ci.cancel();
    }

    /** Reinforced: wear is rolled away before Unbreaking and the damage are applied. */
    @ModifyVariable(method = "hurtAndBreak(ILnet/minecraft/server/level/ServerLevel;Lnet/minecraft/server/level/ServerPlayer;Ljava/util/function/Consumer;)V",
            at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private int tempered$reinforced(int amount) {
        return BrokenTools.reinforce((ItemStack) (Object) this, amount);
    }

    /** Mastery attack-speed bonus, applied while held like any attribute modifier. */
    @Inject(method = "forEachModifier(Lnet/minecraft/world/entity/EquipmentSlot;Ljava/util/function/BiConsumer;)V", at = @At("TAIL"))
    private void tempered$slotModifiers(EquipmentSlot slot, BiConsumer<Holder<Attribute>, AttributeModifier> consumer, CallbackInfo ci) {
        if (slot == EquipmentSlot.MAINHAND) MasteryAttributes.forEach((ItemStack) (Object) this, consumer);
    }

    /** ... and shown in the tooltip's "When in Main Hand" section. */
    @Inject(method = "forEachModifier(Lnet/minecraft/world/entity/EquipmentSlotGroup;Lorg/apache/commons/lang3/function/TriConsumer;)V", at = @At("TAIL"))
    private void tempered$groupModifiers(EquipmentSlotGroup group,
                                         TriConsumer<Holder<Attribute>, AttributeModifier, ItemAttributeModifiers.Display> consumer, CallbackInfo ci) {
        if (group == EquipmentSlotGroup.MAINHAND) {
            MasteryAttributes.forEach((ItemStack) (Object) this, (attribute, modifier) ->
                    consumer.accept(attribute, modifier, ItemAttributeModifiers.Display.attributeModifiers()));
        }
    }
}
