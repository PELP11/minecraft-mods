package com.afjan.townsfolk.villager;

import com.afjan.townsfolk.registry.ModComponents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.Consumer;

/**
 * A picked-up villager. Use it on a block to set it down; use it in the air to trade with it: it steps out, the
 * trading screen opens, and it goes back into the inventory when the screen closes.
 */
public final class VillagerItem extends Item {
    public static final String OF = "item.townsfolk.villager.of";
    public static final String UNEMPLOYED = "item.townsfolk.villager.unemployed";
    public static final String TIP_LEVEL = "tooltip.townsfolk.level";
    public static final String TIP_TRADE = "tooltip.townsfolk.trade";
    public static final String TIP_TRADE_TWO = "tooltip.townsfolk.trade_two";
    public static final String TIP_MORE = "tooltip.townsfolk.more";
    public static final String TIP_USE = "tooltip.townsfolk.use";
    public static final String NOTHING = "message.townsfolk.nothing_to_trade";
    private static final int SHOWN_TRADES = 10;

    /** Villagers out of the pocket for a trade, by player; they go back when the trading screen closes. */
    private static final Map<Player, Villager> TRADING = new WeakHashMap<>();

    public VillagerItem(Properties properties) {
        super(properties);
    }

    public static ItemStack of(CapturedVillager villager, Item item) {
        ItemStack stack = new ItemStack(item);
        stack.set(ModComponents.VILLAGER.get(), villager);
        return stack;
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        CapturedVillager villager = context.getItemInHand().get(ModComponents.VILLAGER.get());
        if (villager == null) return InteractionResult.FAIL;
        if (!(context.getLevel() instanceof ServerLevel level)) return InteractionResult.SUCCESS;
        BlockPos pos = context.getClickedPos();
        if (!level.getBlockState(pos).canBeReplaced()) pos = pos.relative(context.getClickedFace());
        Player player = context.getPlayer();
        if (villager.spawn(level, CapturedVillager.standOn(pos), player == null ? 0.0F : player.getYRot() + 180.0F) == null) return InteractionResult.FAIL;
        level.playSound(null, pos, SoundEvents.VILLAGER_AMBIENT, net.minecraft.sounds.SoundSource.NEUTRAL, 1.0F, 1.0F);
        context.getItemInHand().consume(1, player);
        return InteractionResult.SUCCESS;
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        CapturedVillager captured = stack.get(ModComponents.VILLAGER.get());
        if (captured == null || player.isSecondaryUseActive()) return InteractionResult.PASS;
        if (captured.results().isEmpty()) {
            player.sendOverlayMessage(Component.translatable(NOTHING).withStyle(ChatFormatting.GRAY));
            return InteractionResult.FAIL;
        }
        if (!(level instanceof ServerLevel server) || !(player instanceof ServerPlayer serverPlayer)) return InteractionResult.SUCCESS;
        // Step out in front of the player (or on the spot when a wall is in the way).
        Vec3 ahead = player.position().add(Vec3.directionFromRotation(0.0F, player.getYRot()).scale(1.5));
        Vec3 at = server.noCollision(EntityTypes.VILLAGER.getSpawnAABB(ahead.x, ahead.y, ahead.z)) ? ahead : player.position();
        Villager villager = captured.spawn(server, at, player.getYRot() + 180.0F);
        if (villager == null) return InteractionResult.FAIL;
        player.setItemInHand(hand, ItemStack.EMPTY);
        villager.mobInteract(player, hand);
        if (villager.getTradingPlayer() == player) {
            TRADING.put(player, villager);
        } else {
            giveBack(serverPlayer, villager);  // a baby or a sleepy villager does not trade: straight back in
        }
        return InteractionResult.SUCCESS;
    }

    /** Trading screen closed: the pocket villager goes back into the inventory. */
    public static void onTradingClosed(ServerPlayer player) {
        Villager villager = TRADING.remove(player);
        if (villager != null && villager.isAlive()) giveBack(player, villager);
    }

    private static void giveBack(ServerPlayer player, Villager villager) {
        ItemStack stack = of(CapturedVillager.capture(player.level(), villager), ModComponents.VILLAGER_ITEM.get());
        if (player.getMainHandItem().isEmpty()) player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        else player.getInventory().placeItemBackInInventory(stack, net.minecraft.util.Prediction.SERVER_ONLY);
    }

    @Override
    public Component getName(ItemStack stack) {
        CapturedVillager villager = stack.get(ModComponents.VILLAGER.get());
        if (villager == null) return super.getName(stack);
        return Component.translatable(OF, professionName(villager));
    }

    private static Component professionName(CapturedVillager villager) {
        if (villager.profession().equals(VillagerProfession.NONE.identifier())) return Component.translatable(UNEMPLOYED);
        return BuiltInRegistries.VILLAGER_PROFESSION.getOptional(villager.profession()).map(VillagerProfession::name)
                .orElse(Component.literal(villager.profession().getPath()));
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag flag) {
        CapturedVillager villager = stack.get(ModComponents.VILLAGER.get());
        if (villager == null) return;
        if (!villager.profession().equals(VillagerProfession.NONE.identifier())) {
            builder.accept(Component.translatable(TIP_LEVEL, Component.translatable("merchant.level." + villager.level()), villager.level())
                    .withStyle(ChatFormatting.GOLD));
        }
        int trades = villager.results().size();
        for (int i = 0; i < Math.min(trades, SHOWN_TRADES); i++) {
            ItemStack b = villager.costB().get(i);
            builder.accept((b.isEmpty()
                    ? Component.translatable(TIP_TRADE, amount(villager.costA().get(i)), amount(villager.results().get(i)))
                    : Component.translatable(TIP_TRADE_TWO, amount(villager.costA().get(i)), amount(b), amount(villager.results().get(i))))
                    .withStyle(ChatFormatting.GRAY));
        }
        if (trades > SHOWN_TRADES) builder.accept(Component.translatable(TIP_MORE, trades - SHOWN_TRADES).withStyle(ChatFormatting.DARK_GRAY));
        builder.accept(Component.translatable(TIP_USE).withStyle(ChatFormatting.DARK_GRAY));
    }

    /** "3 Emerald", or "Enchanted Book (Mending)". */
    private static Component amount(ItemStack stack) {
        MutableComponent text = stack.getCount() > 1 ? Component.literal(stack.getCount() + " ").append(stack.getHoverName()) : stack.getHoverName().copy();
        var stored = stack.get(DataComponents.STORED_ENCHANTMENTS);
        if (stored != null && !stored.isEmpty()) {
            MutableComponent list = Component.empty();
            stored.entrySet().forEach(e -> list.append(list.getSiblings().isEmpty() ? Component.empty() : Component.literal(", "))
                    .append(Enchantment.getFullname(e.getKey(), e.getIntValue())));
            text.append(" (").append(list).append(")");
        }
        return text;
    }
}
