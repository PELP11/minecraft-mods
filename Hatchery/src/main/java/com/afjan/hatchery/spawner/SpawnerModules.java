package com.afjan.hatchery.spawner;

import com.afjan.hatchery.registry.ModComponents;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.InclusiveRange;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.level.BaseSpawner;
import net.minecraft.world.level.SpawnData;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.SpawnerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;

/**
 * The modules installed in a vanilla spawner, kept as a data component on its block entity (saved with it).
 * Swarm and Haste rewrite the spawner's own numbers, Daylight gives its spawn data light rules that allow any light;
 * Frailty and Redstone act when a mob spawns ({@code event/SpawnerEvents}).
 */
public record SpawnerModules(int swarm, int haste, int frailty, boolean daylight, boolean redstone) {
    public static final SpawnerModules NONE = new SpawnerModules(0, 0, 0, false, false);
    public static final Codec<SpawnerModules> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.optionalFieldOf("swarm", 0).forGetter(SpawnerModules::swarm),
            Codec.INT.optionalFieldOf("haste", 0).forGetter(SpawnerModules::haste),
            Codec.INT.optionalFieldOf("frailty", 0).forGetter(SpawnerModules::frailty),
            Codec.BOOL.optionalFieldOf("daylight", false).forGetter(SpawnerModules::daylight),
            Codec.BOOL.optionalFieldOf("redstone", false).forGetter(SpawnerModules::redstone)
    ).apply(i, SpawnerModules::new));

    public static SpawnerModules of(BlockEntity entity) {
        return entity.components().getOrDefault(ModComponents.SPAWNER_MODULES.get(), NONE);
    }

    /** Mobs per wave: 4, +2 per Swarm level (14 at V). */
    public int spawnCount() {
        return 4 + 2 * swarm;
    }

    /** Wave delay in ticks: vanilla 200-800, x0.7 per Haste level (34-134 at V). */
    public int minDelay() {
        return (int) Math.round(200 * Math.pow(0.7, haste));
    }

    public int maxDelay() {
        return (int) Math.round(800 * Math.pow(0.7, haste));
    }

    /** Share of max health a spawned mob keeps (Frailty IV: 1 HP, see {@link #frailHealth}). */
    public float healthShare() {
        return 1.0F - 0.25F * frailty;
    }

    public float frailHealth(float maxHealth) {
        return frailty >= 4 ? 1.0F : Math.max(1.0F, maxHealth * healthShare());
    }

    /** Stores the modules on the spawner and rewrites its numbers to match. */
    public void install(ServerLevel level, BlockPos pos, SpawnerBlockEntity entity) {
        BaseSpawner spawner = entity.getSpawner();
        TagValueOutput out = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, level.registryAccess());
        spawner.save(out);
        CompoundTag tag = out.buildResult();
        tag.putShort("SpawnCount", (short) spawnCount());
        tag.putShort("MaxNearbyEntities", (short) (6 + 5 * swarm));
        tag.putShort("MinSpawnDelay", (short) minDelay());
        tag.putShort("MaxSpawnDelay", (short) maxDelay());
        if (daylight) {
            // Custom light rules replace the mob's own spawn checks: any block and sky light.
            Tag rules = SpawnData.CustomSpawnRules.CODEC.encodeStart(NbtOps.INSTANCE,
                    new SpawnData.CustomSpawnRules(new InclusiveRange<>(0, 15), new InclusiveRange<>(0, 15))).getOrThrow();
            tag.getCompound("SpawnData").ifPresent(data -> data.put("custom_spawn_rules", rules.copy()));
            tag.getList("SpawnPotentials").ifPresent(list -> list.forEach(entry -> {
                if (entry instanceof CompoundTag weighted) weighted.getCompound("data").ifPresent(data -> data.put("custom_spawn_rules", rules.copy()));
            }));
        }
        spawner.load(level, pos, TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), tag));
        entity.setComponents(DataComponentMap.builder().addAll(entity.components()).set(ModComponents.SPAWNER_MODULES.get(), this).build());
        entity.setChanged();
        BlockState state = level.getBlockState(pos);
        level.sendBlockUpdated(pos, state, state, Block.UPDATE_ALL);
    }
}
