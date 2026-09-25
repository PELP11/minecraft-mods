package com.afjan.stonesift.rock;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

/** The rock vein under each chunk, computed from Hash(seed, chunkX, chunkZ): nothing is stored. */
public final class Veins {
    private static final Rock[] OVERWORLD = {Rock.COBBLESTONE, Rock.COBBLESTONE, Rock.COBBLESTONE, Rock.ANDESITE,
            Rock.ANDESITE, Rock.GRANITE, Rock.GRANITE, Rock.DIORITE, Rock.DIORITE, Rock.TUFF, Rock.DEEPSLATE};

    private Veins() {}

    public static Rock vein(long seed, int chunkX, int chunkZ, boolean nether) {
        if (nether) {
            return Rock.BLACKSTONE;
        }
        long h = seed ^ (chunkX * 341873128712L) ^ (chunkZ * 132897987541L);
        h ^= h >>> 33;
        h *= 0xff51afd7ed558ccdL;
        h ^= h >>> 33;
        h *= 0xc4ceb9fe1a85ec53L;
        h ^= h >>> 33;
        return OVERWORLD[(int) Math.floorMod(h, (long) OVERWORLD.length)];
    }

    public static Rock vein(ServerLevel level, int chunkX, int chunkZ) {
        return vein(level.getSeed(), chunkX, chunkZ, level.dimension() == Level.NETHER);
    }
}
