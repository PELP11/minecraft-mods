package com.afjan.tempered.event;

import com.afjan.tempered.mastery.Mastery;
import com.afjan.tempered.mastery.Perk;
import com.afjan.tempered.registry.ModTags;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.StringUtil;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.common.util.Result;
import net.minecraftforge.event.AnvilUpdateEvent;
import net.minecraftforge.event.entity.living.LivingEntityUseItemEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.listener.Priority;
import org.jspecify.annotations.Nullable;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Tools never disappear: at 0 durability they become Broken (vanilla's own "damage == max" state, which
 * already drops their attack bonuses) and stop working until repaired. Repairing with the tool's material
 * at an anvil costs 1 level per material and never becomes "Too Expensive".
 */
public final class BrokenTools {
    private BrokenTools() {
    }

    public static void register() {
        PlayerEvent.BreakSpeed.BUS.addListener(Priority.LOW, BrokenTools::onBreakSpeed);
        PlayerEvent.HarvestCheck.BUS.addListener(BrokenTools::onHarvestCheck);
        PlayerInteractEvent.RightClickItem.BUS.addListener(BrokenTools::onRightClickItem);
        PlayerInteractEvent.RightClickBlock.BUS.addListener(BrokenTools::onRightClickBlock);
        PlayerInteractEvent.EntityInteractSpecific.BUS.addListener(Priority.HIGH, BrokenTools::onInteractEntity);
        LivingEntityUseItemEvent.Start.BUS.addListener(Priority.HIGH, BrokenTools::onUseStart);
        AnvilUpdateEvent.BUS.addListener(BrokenTools::onAnvilUpdate);
    }

    /** Items that break "softly" (a tag, so packs can add armour or modded tools). */
    public static boolean keepsWhenBroken(ItemStack stack) {
        return stack.is(ModTags.KEEP_WHEN_BROKEN);
    }

    public static boolean isBrokenTool(ItemStack stack) {
        return !stack.isEmpty() && stack.isBroken() && keepsWhenBroken(stack);
    }

    // ------------------------------------------------------------------------------------------------ mixin hooks

    /** ItemStack#applyDamage would destroy the stack: keep it at 0 durability instead. */
    public static void onBroke(ItemStack stack, @Nullable ServerPlayer player) {
        if (player == null) return;
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ITEM_BREAK.value(),
                SoundSource.PLAYERS, 0.8F, 0.8F + player.getRandom().nextFloat() * 0.4F);
        player.sendOverlayMessage(Component.translatable("message.tempered.broke", stack.getHoverName()).withStyle(ChatFormatting.RED));
    }

    /** Reinforced: every point of wear has a chance to be ignored. */
    public static int reinforce(ItemStack stack, int amount) {
        if (amount <= 0) return amount;
        double chance = Mastery.perk(stack, Perk.REINFORCED) / 100.0;
        if (chance <= 0) return amount;
        int kept = 0;
        ThreadLocalRandom random = ThreadLocalRandom.current();
        for (int i = 0; i < amount; i++) {
            if (random.nextDouble() >= chance) kept++;
        }
        return kept;
    }

    // ------------------------------------------------------------------------------------------------ broken behaviour

    private static boolean onBreakSpeed(PlayerEvent.BreakSpeed event) {
        ItemStack tool = event.getEntity().getMainHandItem();
        if (isBrokenTool(tool)) {
            float toolSpeed = tool.getDestroySpeed(event.getState());
            if (toolSpeed > 1.0F) event.setNewSpeed(event.getNewSpeed() / toolSpeed);
        }
        return false;
    }

    private static void onHarvestCheck(PlayerEvent.HarvestCheck event) {
        if (event.getTargetBlock().requiresCorrectToolForDrops() && isBrokenTool(event.getEntity().getMainHandItem())) {
            event.setCanHarvest(false);
        }
    }

    private static boolean onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        return refuse(event.getEntity(), event.getEntity().getItemInHand(event.getHand()));
    }

    private static boolean onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        ItemStack stack = event.getEntity().getItemInHand(event.getHand());
        if (isBrokenTool(stack)) {
            // Let the block react (open doors, chests ...), but the broken tool does nothing.
            event.setUseItem(Result.DENY);
            boolean worksOnBlocks = stack.has(DataComponents.BLOCK_TRANSFORMER) || stack.is(Items.FLINT_AND_STEEL) || stack.is(Items.BRUSH);
            if (worksOnBlocks && !event.getEntity().level().isClientSide()) hint(event.getEntity(), stack);
        }
        return false;
    }

    private static boolean onInteractEntity(PlayerInteractEvent.EntityInteractSpecific event) {
        return refuse(event.getEntity(), event.getEntity().getItemInHand(event.getHand()));
    }

    private static boolean onUseStart(LivingEntityUseItemEvent.Start event) {
        return isBrokenTool(event.getItem());
    }

    private static boolean refuse(Player player, ItemStack stack) {
        if (!isBrokenTool(stack)) return false;
        if (!player.level().isClientSide()) hint(player, stack);
        return true;
    }

    private static void hint(Player player, ItemStack stack) {
        player.sendOverlayMessage(Component.translatable("message.tempered.is_broken", stack.getHoverName()).withStyle(ChatFormatting.RED));
    }

    // ------------------------------------------------------------------------------------------------ anvil

    /**
     * Material repairs of our tools: each material restores a quarter (like vanilla), costs 1 level, ignores
     * and does not raise the prior-work penalty. Renaming or books go through the vanilla anvil.
     */
    private static boolean onAnvilUpdate(AnvilUpdateEvent event) {
        ItemStack left = event.getLeft();
        ItemStack right = event.getRight();
        if (left.isEmpty() || right.isEmpty() || !keepsWhenBroken(left) || !left.isDamaged() || !left.isValidRepairItem(right)) return false;
        String name = event.getName();
        if (name != null && !StringUtil.isBlank(name) && !name.equals(left.getHoverName().getString())) return false;

        ItemStack output = left.copy();
        int perMaterial = Math.max(1, output.getMaxDamage() / 4);
        int used = 0;
        while (output.getDamageValue() > 0 && used < right.getCount()) {
            output.setDamageValue(Math.max(0, output.getDamageValue() - perMaterial));
            used++;
        }
        event.setOutput(output);
        event.setCost(used);
        event.setMaterialCost(used);
        return false;
    }
}
