package com.afjan.stonesift.item;

import java.util.function.Consumer;

import com.afjan.stonesift.rock.Resource;
import com.afjan.stonesift.rock.Rock;
import com.afjan.stonesift.rock.Veins;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.context.UseOnContext;

/** The small item classes: sieve meshes, stone hammers, the geologist's hammer and items with a hint line. */
public final class SimpleItems {
    private SimpleItems() {}

    /** A sieve mesh: 1 string, 2 iron, 3 diamond. Decides which resources can fall through at all. */
    public static class Mesh extends Item {
        private final int tier;

        public Mesh(int tier, Item.Properties properties) {
            super(properties);
            this.tier = tier;
        }

        public int tier() {
            return this.tier;
        }

        public static int tierOf(ItemStack stack) {
            return stack.getItem() instanceof Mesh mesh ? mesh.tier : 0;
        }

        @Override
        public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
                Consumer<Component> builder, TooltipFlag flag) {
            StringBuilder lets = new StringBuilder();
            for (Resource r : Resource.values()) {
                if (r.tier() <= this.tier) {
                    builder.accept(Component.literal(" ").append(Component.translatable("resource.stonesift." + r.key()))
                            .withStyle(r.tier() == this.tier ? ChatFormatting.GRAY : ChatFormatting.DARK_GRAY));
                }
            }
        }
    }

    /** Mines rock like a slightly slower pickaxe; the rock drops as 2 gravel of its type (see StonesiftEvents). */
    public static class StoneHammer extends Item {
        public StoneHammer(Item.Properties properties) {
            super(properties);
        }

        @Override
        public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
                Consumer<Component> builder, TooltipFlag flag) {
            builder.accept(Component.translatable("tooltip.stonesift.stone_hammer").withStyle(ChatFormatting.GRAY));
        }
    }

    /** Right-click the ground: tells the rock vein of this chunk, where a Deep Drill would pump from. */
    public static class GeologistHammer extends Item {
        public GeologistHammer(Item.Properties properties) {
            super(properties);
        }

        @Override
        public InteractionResult useOn(UseOnContext context) {
            Player player = context.getPlayer();
            if (context.getLevel() instanceof ServerLevel level && player != null) {
                BlockPos pos = context.getClickedPos();
                Rock rock = Veins.vein(level, pos.getX() >> 4, pos.getZ() >> 4);
                player.sendOverlayMessage(Component.translatable("message.stonesift.vein",
                        Component.translatable("rock.stonesift." + rock.key()),
                        Component.translatable("richness.stonesift." + rock.main().key())).withStyle(ChatFormatting.GOLD));
                level.playSound(null, pos, SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 0.25F, 1.8F);
                context.getItemInHand().hurtAndBreak(1, player, context.getHand());
            }
            return InteractionResult.SUCCESS;
        }
    }

    /** An item whose tooltip explains it: tooltip.stonesift.<name>. */
    public static class Hinted extends Item {
        private final String key;

        public Hinted(String key, Item.Properties properties) {
            super(properties);
            this.key = key;
        }

        @Override
        public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
                Consumer<Component> builder, TooltipFlag flag) {
            builder.accept(Component.translatable("tooltip.stonesift." + this.key).withStyle(ChatFormatting.GRAY));
        }
    }
}
