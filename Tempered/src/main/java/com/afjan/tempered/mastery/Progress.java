package com.afjan.tempered.mastery;

import com.afjan.tempered.registry.ModComponents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

import java.util.Collection;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Adds to a tool's counters and celebrates new mastery levels. Server side only. */
public final class Progress {
    private static final Map<Kind, Set<Stat>> RELEVANT = new EnumMap<>(Kind.class);

    private Progress() {
    }

    /** Stats any milestone of this kind asks for; nothing else is stored on the item. */
    public static Set<Stat> relevant(Kind kind) {
        synchronized (RELEVANT) {
            return RELEVANT.computeIfAbsent(kind, k -> {
                Set<Stat> set = EnumSet.noneOf(Stat.class);
                for (Track track : Tracks.all()) {
                    if (track.kind != k) continue;
                    for (Milestone milestone : track.milestones) {
                        for (Milestone.Req req : milestone.reqs()) set.add(req.stat());
                    }
                }
                return set;
            });
        }
    }

    public static void record(@Nullable ServerPlayer player, ItemStack stack, Collection<Stat> stats) {
        Track track = Tracks.get(stack);
        if (track == null || stats.isEmpty() || stack.isBroken()) return;
        Set<Stat> relevant = relevant(track.kind);
        Mastery before = Mastery.getOrCreate(stack);
        Mastery after = before;
        for (Stat stat : stats) {
            if (relevant.contains(stat)) after = after.plus(stat, 1);
        }
        if (after == before) return;
        int oldLevel = track.level(before);
        stack.set(ModComponents.MASTERY.get(), after);
        int newLevel = track.level(after);
        if (player != null) {
            for (int level = oldLevel + 1; level <= newLevel; level++) announce(player, stack, track, level);
        }
    }

    public static void record(@Nullable ServerPlayer player, ItemStack stack, Stat stat) {
        record(player, stack, java.util.List.of(stat));
    }

    private static void announce(ServerPlayer player, ItemStack stack, Track track, int level) {
        ServerLevel world = player.level();
        boolean mastered = level == track.maxLevel();
        world.playSound(null, player.getX(), player.getY(), player.getZ(),
                mastered ? SoundEvents.UI_TOAST_CHALLENGE_COMPLETE : SoundEvents.PLAYER_LEVELUP,
                SoundSource.PLAYERS, mastered ? 1.0F : 0.6F, mastered ? 1.0F : 1.5F);
        world.sendParticles(mastered ? ParticleTypes.TOTEM_OF_UNDYING : ParticleTypes.HAPPY_VILLAGER,
                player.getX(), player.getY() + 1.0, player.getZ(), mastered ? 60 : 16, 0.5, 0.7, 0.5, mastered ? 0.35 : 0.05);

        Milestone milestone = track.milestones.get(level - 1);
        Component roman = Component.translatable("mastery.tempered.level." + level);
        player.sendSystemMessage((mastered
                ? Component.translatable(LangKeys.MASTERED, stack.getHoverName(), roman)
                : Component.translatable(LangKeys.LEVEL_UP, stack.getHoverName(), roman, level, track.maxLevel()))
                .withStyle(mastered ? ChatFormatting.LIGHT_PURPLE : ChatFormatting.GOLD));
        for (Perk perk : milestone.gained()) {
            player.sendSystemMessage(Component.literal("  + ").append(perk.describe(track.kind, milestone.totals().get(perk)))
                    .withStyle(perk.isAbility() ? ChatFormatting.AQUA : ChatFormatting.GREEN));
        }
        if (level < track.maxLevel()) {
            // The next challenge right away; the overview key is only explained on the first level-ups.
            MutableComponent next = Component.translatable(LangKeys.NEXT, Component.translatable("mastery.tempered.level." + (level + 1)));
            List<Milestone.Req> reqs = track.milestones.get(level).reqs();
            for (int i = 0; i < reqs.size(); i++) {
                next.append(i == 0 ? " " : ", ").append(Component.translatable(reqs.get(i).key(track.kind), reqs.get(i).amount()));
            }
            player.sendSystemMessage(next.withStyle(ChatFormatting.GRAY));
            if (level <= 2) player.sendSystemMessage(Component.translatable(LangKeys.OVERVIEW_HINT).withStyle(ChatFormatting.DARK_GRAY));
        }
    }
}
