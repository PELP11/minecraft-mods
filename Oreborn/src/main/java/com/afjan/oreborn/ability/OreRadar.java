package com.afjan.oreborn.ability;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.joml.Vector3f;

import com.afjan.oreborn.material.OreMaterial;
import com.afjan.oreborn.registry.ModTags;
import com.mojang.math.Transformation;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Brightness;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Fulgurite pickaxe: an electromagnetic pulse that outlines every ore within {@link #RADIUS} blocks in its own colour,
 * visible through walls, for 10 seconds. The outlines are glowing block displays of the ore itself; they are
 * removed on time and never survive a restart.
 */
public final class OreRadar {
    public static final int RADIUS = 12;
    public static final int DURATION = 200;
    public static final String MARKER_TAG = "oreborn_radar";
    private static final int MAX_MARKERS = 160;

    private record Marker(ResourceKey<Level> dimension, UUID id, long expires) {}

    private record Glow(TagKey<Block> ores, int color) {}

    private static final List<Glow> COLORS = List.of(
            new Glow(ModTags.common("ores/cryolite"), OreMaterial.CRYOLITE.color()),
            new Glow(ModTags.common("ores/fulgurite"), OreMaterial.FULGURITE.color()),
            new Glow(ModTags.common("ores/emberite"), OreMaterial.EMBERITE.color()),
            new Glow(ModTags.common("ores/umbrium"), OreMaterial.UMBRIUM.color()),
            new Glow(ModTags.common("ores/diamond"), 0x4DF5FF),
            new Glow(ModTags.common("ores/emerald"), 0x2EE86B),
            new Glow(ModTags.common("ores/gold"), 0xFFD23D),
            new Glow(ModTags.common("ores/iron"), 0xE3A77F),
            new Glow(ModTags.common("ores/copper"), 0xFF8A3D),
            new Glow(ModTags.common("ores/redstone"), 0xFF2A2A),
            new Glow(ModTags.common("ores/lapis"), 0x3B63FF),
            new Glow(ModTags.common("ores/coal"), 0x8C8C8C),
            new Glow(ModTags.common("ores/quartz"), 0xF4F0E8),
            new Glow(ModTags.common("ores/netherite_scrap"), 0xB0603A));

    private static final List<Marker> MARKERS = new ArrayList<>();
    private static final Set<UUID> ACTIVE = new HashSet<>();

    private OreRadar() {}

    public static int colorFor(BlockState state) {
        for (Glow glow : COLORS) {
            if (state.is(glow.ores())) {
                return glow.color();
            }
        }
        return 0xFFFFFF;
    }

    /** Scans around the player and outlines the ores. Returns how many were found. */
    public static List<BlockPos> scan(ServerLevel level, BlockPos center) {
        List<BlockPos> found = new ArrayList<>();
        for (BlockPos p : BlockPos.betweenClosed(center.offset(-RADIUS, -RADIUS, -RADIUS), center.offset(RADIUS, RADIUS, RADIUS))) {
            if (level.getBlockState(p).is(ModTags.ORES)) {
                found.add(p.immutable());
            }
        }
        found.sort(Comparator.comparingDouble(p -> p.distSqr(center)));
        return found;
    }

    public static boolean ping(ServerLevel level, ServerPlayer player) {
        List<BlockPos> found = scan(level, player.blockPosition());
        int shown = Math.min(found.size(), MAX_MARKERS);
        for (int i = 0; i < shown; i++) {
            BlockPos pos = found.get(i);
            spawnMarker(level, pos, level.getBlockState(pos));
        }
        Vec3 at = player.position().add(0.0, 1.0, 0.0);
        for (double r = 2.0; r <= 8.0; r += 2.0) {
            Fx.ring(level, ParticleTypes.ELECTRIC_SPARK, at, r, (int) (r * 8));
        }
        Fx.sound(level, at, SoundEvents.BEACON_ACTIVATE, 0.6F, 2.0F);
        Fx.sound(level, at, SoundEvents.AMETHYST_BLOCK_CHIME, 1.0F, 1.5F);
        Component message = found.isEmpty()
                ? Component.translatable("message.oreborn.radar_none", RADIUS).withStyle(ChatFormatting.GRAY)
                : Component.translatable("message.oreborn.radar", found.size()).withStyle(style -> style.withColor(OreMaterial.FULGURITE.color()));
        player.sendOverlayMessage(message);
        return true;
    }

    public static void spawnMarker(ServerLevel level, BlockPos pos, BlockState state) {
        CompoundTag tag = new CompoundTag();
        tag.put("block_state", BlockState.CODEC.encodeStart(NbtOps.INSTANCE, state).getOrThrow());
        tag.putBoolean("Glowing", true);
        tag.putInt("glow_color_override", colorFor(state));
        tag.put("brightness", Brightness.CODEC.encodeStart(NbtOps.INSTANCE, Brightness.FULL_BRIGHT).getOrThrow());
        // a hair smaller than the ore, so the outline hugs the block
        tag.put("transformation", Transformation.EXTENDED_CODEC.encodeStart(NbtOps.INSTANCE,
                new Transformation(new Vector3f(0.03F), null, new Vector3f(0.94F), null)).getOrThrow());
        Entity marker = EntityType.loadEntityRecursive(EntityTypes.BLOCK_DISPLAY, tag, level, EntitySpawnReason.TRIGGERED, entity -> {
            entity.snapTo(pos.getX(), pos.getY(), pos.getZ());
            return entity;
        });
        if (marker == null) {
            return;
        }
        marker.addTag(MARKER_TAG);
        ACTIVE.add(marker.getUUID());
        MARKERS.add(new Marker(level.dimension(), marker.getUUID(), level.getGameTime() + DURATION));
        level.addFreshEntity(marker);
    }

    /** Removes expired outlines (called every server tick). */
    public static void tick(MinecraftServer server) {
        if (MARKERS.isEmpty()) {
            return;
        }
        long now = server.overworld().getGameTime();
        Iterator<Marker> it = MARKERS.iterator();
        while (it.hasNext()) {
            Marker marker = it.next();
            if (marker.expires() > now) {
                continue;
            }
            ServerLevel level = server.getLevel(marker.dimension());
            if (level != null && level.getEntity(marker.id()) instanceof Entity entity) {
                entity.discard();
            }
            ACTIVE.remove(marker.id());
            it.remove();
        }
    }

    /** An outline loaded back from disk (the game was closed while it glowed): it must not stay forever. */
    public static boolean isStale(Entity entity) {
        return entity.entityTags().contains(MARKER_TAG) && !ACTIVE.contains(entity.getUUID());
    }

    public static int activeCount() {
        return ACTIVE.size();
    }

    public static void clear() {
        MARKERS.clear();
        ACTIVE.clear();
    }
}
