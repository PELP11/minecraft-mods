package com.afjan.tempered.mastery;

import com.afjan.tempered.registry.ModComponents;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;

import java.util.HashMap;
import java.util.Map;

/**
 * The item component: lifetime counters of one tool plus a random id, so an arrow (which carries a copy of
 * its bow) can find the bow it came from. Levels are not stored; {@link Track#level} derives them, so a
 * diamond pickaxe upgraded to netherite keeps its counters and is simply measured against the new track.
 */
public record Mastery(long uid, Map<String, Integer> stats) {
    public static final Codec<Mastery> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.LONG.optionalFieldOf("uid", 0L).forGetter(Mastery::uid),
            Codec.unboundedMap(Codec.STRING, Codec.INT).optionalFieldOf("stats", Map.of()).forGetter(Mastery::stats)
    ).apply(i, Mastery::new));

    public static final StreamCodec<ByteBuf, Mastery> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_LONG, Mastery::uid,
            ByteBufCodecs.map(HashMap::new, ByteBufCodecs.STRING_UTF8, ByteBufCodecs.VAR_INT), Mastery::stats,
            Mastery::new);

    /** No progress yet (used to show "0/15" for tools that were never used). */
    public static final Mastery EMPTY = new Mastery(0L, Map.of());

    public Mastery {
        stats = Map.copyOf(stats);
    }

    public int get(Stat stat) {
        return stats.getOrDefault(stat.id, 0);
    }

    public Mastery plus(Stat stat, int amount) {
        Map<String, Integer> next = new HashMap<>(stats);
        next.merge(stat.id, amount, (a, b) -> (int) Math.min(Integer.MAX_VALUE, (long) a + b));
        return new Mastery(uid, next);
    }

    /** Per stat the higher of the two (used when two tools are combined in a crafting grid). */
    public Mastery max(Mastery other) {
        Map<String, Integer> next = new HashMap<>(stats);
        other.stats.forEach((k, v) -> next.merge(k, v, Math::max));
        return new Mastery(uid != 0 ? uid : other.uid, next);
    }

    public static Mastery of(ItemStack stack) {
        return stack.get(ModComponents.MASTERY.get());
    }

    /** The stack's mastery, created (with a fresh id) if it has none yet. */
    public static Mastery getOrCreate(ItemStack stack) {
        Mastery mastery = of(stack);
        if (mastery == null) {
            mastery = new Mastery(newUid(), Map.of());
            stack.set(ModComponents.MASTERY.get(), mastery);
        } else if (mastery.uid == 0) {
            mastery = new Mastery(newUid(), mastery.stats);
            stack.set(ModComponents.MASTERY.get(), mastery);
        }
        return mastery;
    }

    private static long newUid() {
        long id;
        do {
            id = java.util.concurrent.ThreadLocalRandom.current().nextLong();
        } while (id == 0);
        return id;
    }

    /** Level of a stack on its track (0 for untracked items or tools without progress). */
    public static int level(ItemStack stack) {
        Track track = Tracks.get(stack);
        return track == null ? 0 : track.level(of(stack));
    }

    /**
     * The perk value a stack has right now. Broken tools have none: until repaired they work like a fist.
     */
    public static double perk(ItemStack stack, Perk perk) {
        if (stack.isEmpty() || stack.isBroken()) return 0;
        Track track = Tracks.get(stack);
        if (track == null) return 0;
        Mastery mastery = of(stack);
        if (mastery == null) return 0;
        return track.perk(track.level(mastery), perk);
    }
}
