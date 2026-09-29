package com.afjan.tempered.event;

import com.afjan.tempered.event.Events;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import com.afjan.tempered.mastery.Kind;
import com.afjan.tempered.mastery.Mastery;
import com.afjan.tempered.mastery.OreWeights;
import com.afjan.tempered.mastery.Progress;
import com.afjan.tempered.mastery.Stat;
import com.afjan.tempered.mastery.Track;
import com.afjan.tempered.mastery.Tracks;
import com.afjan.tempered.registry.ModComponents;
import com.afjan.tempered.registry.ModTags;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Shearable;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.MaceItem;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CocoaBlock;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.NetherWartBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.event.TagsUpdatedEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.ItemFishedEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

import java.util.ArrayList;
import java.util.List;

import static com.afjan.tempered.mastery.Stat.*;

/** Counts what tools do: blocks mined, crops harvested, mobs slain, fish caught, sheep sheared. */
public final class ProgressEvents {
    private ProgressEvents() {
    }

    /**
     * Counting listeners run last (LOWEST) and never cancel: a cancelled event stops before them.
     * Not MONITOR: EventBus 7.0.6 gives an event whose only listeners are monitors a no-op invoker.
     */
    public static void register() {
        Events.listen(EventPriority.LOWEST, BlockEvent.BreakEvent.class, e -> {
            onBreak(e);
            return false;
        });
        Events.listen(EventPriority.LOWEST, BlockEvent.EntityPlaceEvent.class, e -> {
            onPlace(e);
            return false;
        });
        Events.listen(EventPriority.LOWEST, LivingDeathEvent.class, e -> {
            onDeath(e);
            return false;
        });
        Events.listen(EventPriority.LOWEST, ItemFishedEvent.class, e -> {
            onFished(e);
            return false;
        });
        Events.listen(EventPriority.LOWEST, PlayerInteractEvent.EntityInteractSpecific.class, e -> {
            onInteractEntity(e);
            return false;
        });
        Events.listen(PlayerEvent.ItemCraftedEvent.class, ProgressEvents::onCrafted);
        Events.listen(TagsUpdatedEvent.class, e -> Tracks.clearCache());
        Events.listen(ServerStoppedEvent.class, e -> PlacedBlocks.clear());
        Events.listen(ServerTickEvent.Post.class, e -> PlacedBlocks.endTick());
    }

    // ------------------------------------------------------------------------------------------------ blocks

    private static void onBreak(BlockEvent.BreakEvent event) {
        if (!(event.getPlayer() instanceof ServerPlayer player) || !(event.getLevel() instanceof Level level)) return;
        boolean placed = PlacedBlocks.consume(level, event.getPos());
        if (player.isCreative() || player.isSpectator()) return;
        ItemStack tool = player.getMainHandItem();
        Track track = Tracks.get(tool);
        if (track == null || tool.isBroken()) return;
        Progress.record(player, tool, blockStats(track.kind, tool, level, event.getPos(), event.getState(), placed));
    }

    private static void onPlace(BlockEvent.EntityPlaceEvent event) {
        if (event.getEntity() instanceof Player && event.getLevel() instanceof Level level && !level.isClientSide()) {
            PlacedBlocks.placed(level, event.getPos(), event.getPlacedBlock());
        }
    }

    /**
     * What breaking this block with this tool counts as (nothing if the tool is not meant for it). A stat listed n times
     * counts n: a pickaxe's ores add {@link OreWeights} to "Mine X blocks".
     */
    public static List<Stat> blockStats(Kind kind, ItemStack tool, Level level, BlockPos pos, BlockState state, boolean placed) {
        List<Stat> stats = new ArrayList<>(4);
        boolean effective = isEffective(tool, state) && !placed;
        boolean nether = level.dimension() == Level.NETHER;
        boolean end = level.dimension() == Level.END;
        switch (kind) {
            case PICKAXE -> {
                if (!effective) break;
                for (int i = OreWeights.of(state); i > 0; i--) stats.add(MINED);
                if (state.is(ModTags.ORES)) stats.add(ORES);
                if (state.is(ModTags.GEM_ORES)) stats.add(GEMS);
                if (state.is(ModTags.OBSIDIAN)) stats.add(OBSIDIAN);
                if (state.is(ModTags.DEBRIS)) stats.add(DEBRIS);
                if (pos.getY() < 0 && level.dimension() == Level.OVERWORLD) stats.add(DEEP);
                if (nether) stats.add(NETHER_MINED);
                if (end) stats.add(END_MINED);
            }
            case AXE -> {
                if (!effective) break;
                stats.add(MINED);
                if (state.is(BlockTags.LOGS)) stats.add(LOGS);
                if (nether) stats.add(NETHER_MINED);
                if (end) stats.add(END_MINED);
            }
            case SHOVEL -> {
                if (!effective) break;
                stats.add(MINED);
                if (state.is(BlockTags.SAND)) stats.add(SAND);
                if (state.is(ModTags.GRAVEL)) stats.add(GRAVEL);
                if (state.is(ModTags.CLAY)) stats.add(CLAY);
                if (state.is(ModTags.SNOW)) stats.add(SNOW);
                if (state.is(ModTags.SOUL)) stats.add(SOUL);
                if (nether) stats.add(NETHER_MINED);
            }
            case HOE -> {
                if (isMatureCrop(state)) {
                    stats.add(HARVESTED);
                    if (state.getBlock() instanceof NetherWartBlock) stats.add(WARTS);
                    if (nether) stats.add(NETHER_MINED);
                } else if (effective) {
                    stats.add(MINED);
                    if (state.is(ModTags.SCULK)) stats.add(SCULK);
                    if (nether) stats.add(NETHER_MINED);
                }
            }
            case SHEARS -> {
                if (effective) stats.add(MINED);
            }
            default -> {
            }
        }
        return stats;
    }

    /** The tool speeds up breaking this block and gets its drops. */
    public static boolean isEffective(ItemStack tool, BlockState state) {
        return tool.getDestroySpeed(state) > 1.0F && (!state.requiresCorrectToolForDrops() || tool.isCorrectToolForDrops(state));
    }

    public static boolean isMatureCrop(BlockState state) {
        Block block = state.getBlock();
        if (block instanceof CropBlock crop) return crop.isMaxAge(state);
        if (block instanceof NetherWartBlock) return state.getValue(NetherWartBlock.AGE) >= NetherWartBlock.MAX_AGE;
        if (block instanceof CocoaBlock) return state.getValue(CocoaBlock.AGE) >= CocoaBlock.MAX_AGE;
        return false;
    }

    /** Called by the block-transformer mixin after a hoe tilled (or an axe stripped ...) a block. */
    public static void onTransformed(UseOnContext context) {
        if (!(context.getPlayer() instanceof ServerPlayer player) || player.isCreative()) return;
        ItemStack stack = context.getItemInHand();
        Track track = Tracks.get(stack);
        if (track == null || track.kind != Kind.HOE) return;
        if (PlacedBlocks.contains(context.getLevel(), context.getClickedPos())) return;
        Progress.record(player, stack, TILLED);
    }

    // ------------------------------------------------------------------------------------------------ kills

    private static void onDeath(LivingDeathEvent event) {
        LivingEntity victim = event.getEntity();
        if (victim.level().isClientSide() || victim instanceof ArmorStand) return;
        if (!(event.getSource().getEntity() instanceof ServerPlayer player) || player == victim || player.isCreative()) return;
        Weapons.Hit hit = Weapons.find(player, event.getSource());
        if (hit == null || hit.stack() == null) return;
        Track track = Tracks.get(hit.stack());
        if (track == null) return;

        List<Stat> stats = new ArrayList<>(6);
        stats.add(KILLS);
        if (victim instanceof Enemy) stats.add(HOSTILE);
        Level level = victim.level();
        if (level.dimension() == Level.NETHER) stats.add(NETHER_KILLS);
        if (level.dimension() == Level.END) stats.add(END_KILLS);
        if (victim.getType().is(ModTags.ELITE)) stats.add(ELITE);
        if (victim.getType().is(ModTags.AQUATIC)) stats.add(AQUATIC);
        if (victim.getType().is(EntityTypeTags.RAIDERS)) stats.add(RAIDERS);
        if (player.getVehicle() != null) stats.add(MOUNTED);
        if (hit.how() == Weapons.How.ARROW && player.distanceTo(victim) >= 30.0F) stats.add(LONG_SHOTS);
        if (hit.how() == Weapons.How.THROWN) stats.add(THROWN);
        if (hit.how() == Weapons.How.MELEE && track.kind == Kind.MACE && MaceItem.canSmashAttack(player)) stats.add(SMASH);
        Progress.record(player, hit.stack(), stats);
    }

    // ------------------------------------------------------------------------------------------------ fishing, shearing

    private static void onFished(ItemFishedEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        ItemStack rod = rodOf(player);
        if (rod == null) return;
        List<Stat> stats = new ArrayList<>(2);
        stats.add(CATCHES);
        if (event.getDrops().stream().anyMatch(s -> s.is(ModTags.FISHING_TREASURE))) stats.add(TREASURE);
        Progress.record(player, rod, stats);
    }

    public static ItemStack rodOf(Player player) {
        for (ItemStack stack : new ItemStack[]{player.getMainHandItem(), player.getOffhandItem()}) {
            Track track = Tracks.get(stack);
            if (track != null && track.kind == Kind.FISHING_ROD) return stack;
        }
        return null;
    }

    private static void onInteractEntity(PlayerInteractEvent.EntityInteractSpecific event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.isCreative()) return;
        ItemStack stack = player.getItemInHand(event.getHand());
        Track track = Tracks.get(stack);
        if (track == null || track.kind != Kind.SHEARS || stack.isBroken()) return;
        Entity target = event.getTarget();
        if (target instanceof Shearable shearable && shearable.readyForShearing()) {
            Progress.record(player, stack, SHEARED);
        }
    }

    // ------------------------------------------------------------------------------------------------ crafting

    /** Combining two worn tools in a crafting grid makes a fresh item: carry the better counters over. */
    private static void onCrafted(PlayerEvent.ItemCraftedEvent event) {
        ItemStack result = event.getCrafting();
        if (Tracks.get(result) == null || Mastery.of(result) != null) return;
        Mastery best = null;
        var grid = event.getInventory();
        for (int i = 0; i < grid.getContainerSize(); i++) {
            ItemStack input = grid.getItem(i);
            if (!input.is(result.getItem())) continue;
            Mastery mastery = Mastery.of(input);
            if (mastery != null) best = best == null ? mastery : best.max(mastery);
        }
        if (best != null) result.set(ModComponents.MASTERY.get(), best);
    }
}
