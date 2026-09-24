package com.afjan.drillworks.drill;

import java.util.ArrayList;
import java.util.List;

import com.mojang.serialization.Codec;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/** The modules socketed into a drill head, by socket (-1 = empty socket). */
public record DrillModules(List<Integer> slots) {
    public static final int MAX_SOCKETS = 4;
    public static final DrillModules EMPTY = new DrillModules(List.of(-1, -1, -1, -1));
    public static final Codec<DrillModules> CODEC = Codec.INT.listOf().xmap(DrillModules::new, DrillModules::slots);
    public static final StreamCodec<ByteBuf, DrillModules> STREAM_CODEC =
            ByteBufCodecs.INT.apply(ByteBufCodecs.list()).map(DrillModules::new, DrillModules::slots);

    public DrillModules {
        List<Integer> fixed = new ArrayList<>(slots);
        while (fixed.size() < MAX_SOCKETS) {
            fixed.add(-1);
        }
        slots = List.copyOf(fixed.subList(0, MAX_SOCKETS));
    }

    public Module get(int socket) {
        return Module.byOrdinal(this.slots.get(socket));
    }

    public DrillModules with(int socket, Module module) {
        List<Integer> copy = new ArrayList<>(this.slots);
        copy.set(socket, module == null ? -1 : module.ordinal());
        return new DrillModules(copy);
    }

    /** How many of the given module sit in the first {@code sockets} sockets. */
    public int count(Module module, int sockets) {
        int n = 0;
        for (int i = 0; i < Math.min(sockets, MAX_SOCKETS); i++) {
            if (this.get(i) == module) {
                n++;
            }
        }
        return Math.min(n, module.maxStack());
    }

    public boolean isEmpty() {
        return this.slots.stream().allMatch(i -> i < 0);
    }
}
