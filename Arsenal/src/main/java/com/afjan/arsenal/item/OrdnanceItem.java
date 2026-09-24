package com.afjan.arsenal.item;

import java.util.function.Consumer;

import com.afjan.arsenal.combat.Ordnance;
import com.afjan.arsenal.entity.OrdnanceEntity;
import com.afjan.arsenal.registry.ModSounds;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;

/** A grenade or a weapon of mass destruction: right-click to throw it, then get clear. */
public class OrdnanceItem extends Item {
    private final Ordnance kind;

    public OrdnanceItem(Properties properties, Ordnance kind) {
        super(properties);
        this.kind = kind;
    }

    public Ordnance kind() {
        return this.kind;
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        // the pin, the spoon flying off, the throw
        level.playSound(null, player.getX(), player.getY(), player.getZ(), ModSounds.GRENADE_THROW.value(),
                SoundSource.PLAYERS, 1.0F, 0.95F + level.getRandom().nextFloat() * 0.1F);
        if (level instanceof ServerLevel serverLevel) {
            OrdnanceEntity thrown = new OrdnanceEntity(serverLevel, player, stack, this.kind);
            thrown.shootFromRotation(player, player.getXRot(), player.getYRot(), 0.0F, this.kind.velocity(), 1.0F);
            serverLevel.addFreshEntity(thrown);
        }
        if (!player.getAbilities().instabuild) {
            stack.shrink(1);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
            Consumer<Component> builder, TooltipFlag flag) {
        super.appendHoverText(stack, context, display, builder, flag);
        builder.accept(Component.translatable("tooltip.arsenal." + this.kind.path())
                .withStyle(this.kind.isWeaponOfMassDestruction() ? ChatFormatting.RED : ChatFormatting.GOLD));
        if (this.kind.fuse() > 0) {
            builder.accept(Component.translatable("tooltip.arsenal.fuse",
                            Component.literal(String.format(java.util.Locale.ROOT, "%.1fs", this.kind.fuse() / 20.0F))
                                    .withStyle(ChatFormatting.WHITE))
                    .withStyle(ChatFormatting.DARK_GRAY));
        }
    }
}
