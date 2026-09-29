package com.afjan.townsfolk.villager;

import net.minecraft.world.entity.npc.VillagerData;
import com.afjan.townsfolk.mixin.VillagerInvoker;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.phys.Vec3;
import javax.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * A villager in an item: its whole saved entity (trades, experience, gossip ...) plus a summary for the tooltip
 * (profession, level and every trade's costs and result).
 */
public record CapturedVillager(CompoundTag entity, ResourceLocation profession, int level,
                               List<ItemStack> costA, List<ItemStack> costB, List<ItemStack> results) {
    public static final Codec<CapturedVillager> CODEC = RecordCodecBuilder.create(i -> i.group(
            CompoundTag.CODEC.fieldOf("entity").forGetter(CapturedVillager::entity),
            ResourceLocation.CODEC.fieldOf("profession").forGetter(CapturedVillager::profession),
            Codec.INT.fieldOf("level").forGetter(CapturedVillager::level),
            ItemStack.OPTIONAL_CODEC.listOf().fieldOf("cost_a").forGetter(CapturedVillager::costA),
            ItemStack.OPTIONAL_CODEC.listOf().fieldOf("cost_b").forGetter(CapturedVillager::costB),
            ItemStack.OPTIONAL_CODEC.listOf().fieldOf("results").forGetter(CapturedVillager::results)
    ).apply(i, CapturedVillager::new));
    public static final StreamCodec<RegistryFriendlyByteBuf, CapturedVillager> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.COMPOUND_TAG, CapturedVillager::entity,
            ResourceLocation.STREAM_CODEC, CapturedVillager::profession,
            ByteBufCodecs.VAR_INT, CapturedVillager::level,
            ItemStack.OPTIONAL_LIST_STREAM_CODEC, CapturedVillager::costA,
            ItemStack.OPTIONAL_LIST_STREAM_CODEC, CapturedVillager::costB,
            ItemStack.OPTIONAL_LIST_STREAM_CODEC, CapturedVillager::results,
            CapturedVillager::new);

    private static final List<MemoryModuleType<GlobalPos>> PLACES = List.of(MemoryModuleType.HOME, MemoryModuleType.JOB_SITE,
            MemoryModuleType.POTENTIAL_JOB_SITE, MemoryModuleType.MEETING_POINT);

    /** Saves the villager (it lets go of its bed, workstation and bell first) and removes it from the world. */
    public static CapturedVillager capture(ServerLevel level, Villager villager) {
        villager.stopRiding();
        villager.ejectPassengers();
        if (villager.isLeashed()) villager.dropLeash();
        PLACES.forEach(villager::releasePoi);
        // 1.21.8 grants a level 2 seconds after the trading screen closes and does not save that pending level, so
        // pocket trading (or picking up right after a trade) would lose it: grant every level the XP has earned now.
        while (VillagerData.canLevelUp(villager.getVillagerData().level())
                && villager.getVillagerXp() >= VillagerData.getMaxXpPerLevel(villager.getVillagerData().level())) {
            ((VillagerInvoker) villager).townsfolk$increaseMerchantCareer();
        }
        List<ItemStack> a = new ArrayList<>(), b = new ArrayList<>(), r = new ArrayList<>();
        for (MerchantOffer offer : villager.getOffers()) {
            a.add(offer.getCostA().copy());
            b.add(offer.getCostB().copy());
            r.add(offer.getResult().copy());
        }
        TagValueOutput out = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, level.registryAccess());
        villager.save(out);
        CompoundTag tag = out.buildResult();
        tag.remove("UUID");
        var data = villager.getVillagerData();
        villager.discard();
        return new CapturedVillager(tag, data.profession().unwrapKey().orElseThrow().location(), data.level(), a, b, r);
    }

    /** Puts the villager back into the world, standing at {@code pos}. */
    public @Nullable Villager spawn(ServerLevel level, Vec3 pos, float yaw) {
        Entity entity = EntityType.loadEntityRecursive(TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), this.entity.copy()),
                level, EntitySpawnReason.SPAWN_ITEM_USE, e -> {
                    e.snapTo(pos.x, pos.y, pos.z, yaw, 0.0F);
                    return e;
                });
        return entity instanceof Villager villager && level.addFreshEntity(villager) ? villager : null;
    }

    public static Vec3 standOn(BlockPos pos) {
        return Vec3.atBottomCenterOf(pos);
    }
}
