package com.afjan.tempered.item;

import com.afjan.tempered.mastery.LangKeys;
import com.afjan.tempered.mastery.Tier;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickAction;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;

import java.util.function.Consumer;

/**
 * Repairs on the go: one kit gives back {@link #REPAIR} of an item's durability (broken tools included). Right-click
 * it onto the item in the inventory (like filling a bundle), or use it while the item is in the other hand. An anvil
 * stays better: a quarter per material.
 */
public final class RepairKitItem extends Item {
    /** Share of the max durability one kit restores (an anvil restores a quarter per material). */
    public static final float REPAIR = 0.15F;

    public final Tier tier;
    private final TagKey<Item> material;

    public RepairKitItem(Tier tier, Properties properties) {
        super(properties);
        this.tier = tier;
        this.material = materialTag(tier);
    }

    /** The vanilla tool material a kit is made from (and repairs with). */
    public static TagKey<Item> materialTag(Tier tier) {
        return switch (tier) {
            case WOOD -> ItemTags.WOODEN_TOOL_MATERIALS;
            case STONE -> ItemTags.STONE_TOOL_MATERIALS;
            case IRON -> ItemTags.IRON_TOOL_MATERIALS;
            case GOLD -> ItemTags.GOLD_TOOL_MATERIALS;
            case DIAMOND -> ItemTags.DIAMOND_TOOL_MATERIALS;
            case NETHERITE -> ItemTags.NETHERITE_TOOL_MATERIALS;
            case SPECIAL -> throw new IllegalArgumentException("no repair kit for special tools");
        };
    }

    /** Durability one kit gives back to this item. */
    public static int amount(ItemStack stack) {
        return Math.max(1, Math.round(stack.getMaxDamage() * REPAIR));
    }

    /** The item is damaged and an anvil would repair it with this kit's material. */
    public boolean fits(ItemStack stack) {
        if (stack.isEmpty() || !stack.isDamaged()) return false;
        for (Holder<Item> item : BuiltInRegistries.ITEM.getTagOrEmpty(material)) {
            if (stack.isValidRepairItem(new ItemStack(item))) return true;
        }
        return false;
    }

    /** Uses one kit from {@code kit} on {@code target}; false (and nothing used) if it does not fit. */
    public boolean repair(ItemStack kit, ItemStack target, Player player) {
        if (!fits(target)) return false;
        target.setDamageValue(Math.max(0, target.getDamageValue() - amount(target)));
        player.awardStat(Stats.ITEM_USED.get(this));
        kit.consume(1, player);
        return true;
    }

    /** Carried kit right-clicked onto an item in a slot. Runs on both sides, like a bundle. */
    @Override
    public boolean overrideStackedOnOther(ItemStack kit, Slot slot, ClickAction action, Player player) {
        if (action != ClickAction.SECONDARY || !slot.allowModification(player) || !repair(kit, slot.getItem(), player)) return false;
        slot.setChanged();
        player.playSound(SoundEvents.SMITHING_TABLE_USE, 0.7F, 1.2F);
        return true;
    }

    /** Carried item right-clicked onto a kit in a slot. */
    @Override
    public boolean overrideOtherStackedOnMe(ItemStack kit, ItemStack other, Slot slot, ClickAction action, Player player, SlotAccess carried) {
        if (action != ClickAction.SECONDARY || !slot.allowModification(player) || !repair(kit, other, player)) return false;
        slot.setChanged();
        player.playSound(SoundEvents.SMITHING_TABLE_USE, 0.7F, 1.2F);
        return true;
    }

    /** Used in one hand: repairs the item in the other hand. */
    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack target = player.getItemInHand(hand == InteractionHand.MAIN_HAND ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND);
        if (!fits(target)) return InteractionResult.PASS;
        if (!level.isClientSide()) {
            repair(player.getItemInHand(hand), target, player);
            level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.SMITHING_TABLE_USE, SoundSource.PLAYERS, 0.8F, 1.2F);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag flag) {
        builder.accept(Component.translatable(LangKeys.TIP_KIT_WHAT, Math.round(REPAIR * 100), Component.translatable(tier.translationKey()))
                .withStyle(ChatFormatting.GRAY));
        builder.accept(Component.translatable(LangKeys.TIP_KIT_HOW_1).withStyle(ChatFormatting.GRAY));
        builder.accept(Component.translatable(LangKeys.TIP_KIT_HOW_2).withStyle(ChatFormatting.GRAY));
        builder.accept(Component.translatable(LangKeys.TIP_KIT_ANVIL).withStyle(ChatFormatting.DARK_GRAY));
    }
}
