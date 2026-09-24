package com.afjan.oreborn.item;

import java.util.function.Consumer;

import com.afjan.oreborn.ability.Combat;
import com.afjan.oreborn.ability.ToolActions;
import com.afjan.oreborn.material.GearType;
import com.afjan.oreborn.material.OreMaterial;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

/**
 * Every Oreborn tool. The abilities live in {@link com.afjan.oreborn.ability}: combat in {@link Combat}, right-click
 * powers in {@link ToolActions}, block-breaking patterns and traits in the event handlers.
 */
public class OrebornToolItem extends Item {
    private final OreMaterial material;
    private final GearType type;

    public OrebornToolItem(OreMaterial material, GearType type, Properties properties) {
        super(properties);
        this.material = material;
        this.type = type;
    }

    public OreMaterial material() {
        return material;
    }

    public GearType type() {
        return type;
    }

    public boolean is(OreMaterial material, GearType type) {
        return this.material == material && this.type == type;
    }

    @Override
    public float getAttackDamageBonus(Entity victim, float damage, DamageSource damageSource) {
        return victim.level().isClientSide() ? 0.0F : Combat.attackBonus(this, victim, damage, damageSource);
    }

    @Override
    public void hurtEnemy(ItemStack itemStack, LivingEntity mob, LivingEntity attacker) {
        Combat.onHit(this, mob, attacker);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        InteractionResult result = ToolActions.use(this, level, player, hand);
        return result != null ? result : super.use(level, player, hand);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        InteractionResult result = ToolActions.useOn(this, context);
        return result != null ? result : super.useOn(context);
    }

    @Override
    public void appendHoverText(ItemStack itemStack, TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag tooltipFlag) {
        super.appendHoverText(itemStack, context, display, builder, tooltipFlag);
        Tooltips.ability(builder, "tooltip.oreborn." + material.id() + "_" + type.id(), material.color());
        if (is(OreMaterial.UMBRIUM, GearType.PICKAXE)) {
            builder.accept(Component.translatable("tooltip.oreborn.mode", MiningMode.of(itemStack).displayName().copy().withStyle(ChatFormatting.WHITE))
                    .withStyle(ChatFormatting.GRAY));
        }
        String trait = "tooltip.oreborn.trait." + material.id();
        if (net.minecraft.locale.Language.getInstance().has(trait + ".name")) {
            Tooltips.ability(builder, trait, Tooltips.dim(material.color()));
        }
    }
}
