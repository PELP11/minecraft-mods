package com.afjan.arsenal.item;

import java.util.function.Consumer;

import com.afjan.arsenal.gun.Attachment;
import com.afjan.arsenal.gun.GunData;
import com.afjan.arsenal.gun.GunType;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;

/**
 * A firearm. Firing is not handled here: the client watches the use key and sends
 * {@link com.afjan.arsenal.network.FirePayload}, which keeps automatic weapons responsive and leaves the player's
 * movement speed alone (holding an item down the vanilla way slows you to a crawl).
 */
public class GunItem extends Item {
    private final GunType type;

    public GunItem(Properties properties, GunType type) {
        super(properties);
        this.type = type;
    }

    public GunType type() {
        return this.type;
    }

    public static GunData dataOf(ItemStack stack) {
        return Guns.dataOf(stack);
    }

    /**
     * Right-click is the trigger, but the shot itself comes from the network packet, so nothing happens here. PASS,
     * not CONSUME: vanilla repeats "use" every 4 ticks while right-click is held, and every successful use replays
     * the item's re-equip dip, which made a held gun bob up and down the whole time. (The off hand, which vanilla
     * tries next after a PASS, is kept out of it by ArsenalClient.)
     */
    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        return InteractionResult.PASS;
    }

    /**
     * Every shot rewrites the ammo count in the stack's component, which by default replays the "pull out" animation:
     * the gun would dip out of view on every round. Only a real swap (another slot, another item) plays it.
     */
    @Override
    public boolean shouldCauseReequipAnimation(ItemStack oldStack, ItemStack newStack, boolean slotChanged) {
        return slotChanged || !ItemStack.isSameItem(oldStack, newStack);
    }

    @Override
    public boolean isBarVisible(ItemStack stack) {
        return true;
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        GunData data = dataOf(stack);
        int capacity = this.type.effectiveMagazine(data);
        return Math.round(13.0F * Math.min(1.0F, (float) data.ammo() / capacity));
    }

    @Override
    public int getBarColor(ItemStack stack) {
        GunData data = dataOf(stack);
        float fraction = (float) data.ammo() / this.type.effectiveMagazine(data);
        return fraction > 0.5F ? 0x4CD964 : fraction > 0.2F ? 0xE8B33A : 0xD0342C;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
            Consumer<Component> builder, TooltipFlag flag) {
        super.appendHoverText(stack, context, display, builder, flag);
        GunData data = dataOf(stack);

        builder.accept(Component.translatable("tooltip.arsenal.ammo",
                        Component.literal(data.ammo() + " / " + this.type.effectiveMagazine(data)).withStyle(ChatFormatting.WHITE),
                        Component.translatable(data.caliber().translationKey()).withStyle(ChatFormatting.WHITE))
                .withStyle(ChatFormatting.GRAY));
        builder.accept(Component.translatable("tooltip.arsenal.stats",
                        Component.literal(String.format(java.util.Locale.ROOT, "%.1f", this.type.effectiveDamage(data))).withStyle(ChatFormatting.WHITE),
                        Component.literal(String.valueOf(Math.round(1200.0F / this.type.effectiveFireDelay(data)))).withStyle(ChatFormatting.WHITE),
                        Component.literal(String.valueOf(Math.round(this.type.effectiveRange(data)))).withStyle(ChatFormatting.WHITE))
                .withStyle(ChatFormatting.DARK_GRAY));
        builder.accept(Component.translatable("tooltip.arsenal.action",
                        Component.translatable("tooltip.arsenal.action." + this.type.action().name().toLowerCase(java.util.Locale.ROOT))
                                .withStyle(ChatFormatting.WHITE))
                .withStyle(ChatFormatting.DARK_GRAY));

        for (Attachment attachment : data.attachments()) {
            builder.accept(Component.literal(" + ")
                    .append(Component.translatable(attachment.translationKey()))
                    .withStyle(ChatFormatting.AQUA));
        }
        builder.accept(Component.translatable("tooltip.arsenal.reload_hint").withStyle(ChatFormatting.DARK_GRAY));
    }
}
