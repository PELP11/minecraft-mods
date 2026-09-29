package com.afjan.townsfolk.villager;

import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerData;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;

import java.util.Optional;

/**
 * Jobs by workstation: sneak-use a workstation on a villager to give it that job (fresh novice trades, kept even
 * without the block nearby); use its own workstation to take the job away (it may then find a new one as usual).
 */
public final class Jobs {
    public static final String NEW_JOB = "message.townsfolk.new_job";
    public static final String LOST_JOB = "message.townsfolk.lost_job";
    public static final String TOO_YOUNG = "message.townsfolk.too_young";

    private Jobs() {
    }

    /** The profession whose workstation this item places (lectern -> librarian ...). */
    public static Optional<Holder.Reference<VillagerProfession>> forWorkstation(ItemStack stack) {
        if (!(stack.getItem() instanceof BlockItem item)) return Optional.empty();
        return PoiTypes.forState(item.getBlock().defaultBlockState()).flatMap(poi -> BuiltInRegistries.VILLAGER_PROFESSION.listElements()
                .filter(profession -> profession.value().heldJobSite().test(poi)).findFirst());
    }

    public static void apply(ServerLevel level, Villager villager, Holder<VillagerProfession> job, Player player) {
        if (villager.isBaby()) {
            player.sendOverlayMessage(Component.translatable(TOO_YOUNG).withStyle(ChatFormatting.RED));
            return;
        }
        villager.releasePoi(MemoryModuleType.JOB_SITE);
        villager.releasePoi(MemoryModuleType.POTENTIAL_JOB_SITE);
        VillagerData data = villager.getVillagerData();
        boolean quit = data.profession().equals(job);
        if (quit) {
            villager.setVillagerData(data.withProfession(level.registryAccess(), VillagerProfession.NONE).withLevel(1));
            villager.setVillagerXp(0);
        } else {
            // A changed profession drops the old trades; 1 experience keeps the job without the workstation nearby.
            villager.setVillagerData(data.withProfession(job).withLevel(1));
            villager.setVillagerXp(1);
            villager.getOffers();
        }
        villager.refreshBrain(level);
        villager.playSound(quit ? SoundEvents.VILLAGER_NO : SoundEvents.VILLAGER_CELEBRATE, 1.0F, 1.0F);
        level.sendParticles(quit ? ParticleTypes.SMOKE : ParticleTypes.HAPPY_VILLAGER, villager.getX(), villager.getY() + 1.8, villager.getZ(),
                10, 0.3, 0.3, 0.3, 0.02);
        player.sendOverlayMessage(quit ? Component.translatable(LOST_JOB, job.value().name())
                : Component.translatable(NEW_JOB, job.value().name()).withStyle(ChatFormatting.GREEN));
    }
}
