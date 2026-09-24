package com.afjan.juicer.event;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.afjan.juicer.Juicer;
import com.afjan.juicer.registry.ModEffects;

import net.minecraft.core.BlockPos;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.enchantment.Enchantments;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.enchanting.EnchantedBlockLootEvent;
import net.neoforged.neoforge.event.enchanting.EnchantedEntityLootEvent;
import net.neoforged.neoforge.event.entity.living.LivingFallEvent;
import net.neoforged.neoforge.event.entity.living.MobEffectEvent;
import net.neoforged.neoforge.event.level.block.BreakBlockEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

@EventBusSubscriber(modid = Juicer.MODID)
public final class JuicerEvents {
    /** After Flight runs out you get this long to land without fall damage. */
    private static final int FLIGHT_GRACE_TICKS = 20 * 20;
    /** Server-thread only: entity UUID -> game time until which a landing is safe. */
    private static final Map<UUID, Long> FLIGHT_GRACE = new HashMap<>();
    /** Server-thread only: blocks being broken this tick by players with the Fortune effect. */
    private static final Map<BlockPos, Player> FORTUNE_BREAKERS = new HashMap<>();

    private JuicerEvents() {}

    /**
     * Looting effect: every loot roll that asks for the killer's Looting level (count bonuses, rare-drop chances and
     * equipment drops) sees at least the effect level, e.g. Looting X from Starfruit Juice.
     */
    @SubscribeEvent
    static void onEntityLootEnchantmentLevel(EnchantedEntityLootEvent event) {
        if (!event.getEnchantment().is(Enchantments.LOOTING)) {
            return;
        }
        if (event.getDamageSource() != null && event.getDamageSource().getEntity() instanceof LivingEntity killer) {
            MobEffectInstance looting = killer.getEffect(ModEffects.LOOTING);
            if (looting != null) {
                event.setEnchantmentLevel(Math.max(event.getEnchantmentLevel(), looting.getAmplifier() + 1));
            }
        }
    }

    /**
     * Fortune effect, part 1: the block-loot event below doesn't say who broke the block, so remember players with
     * the effect when their (not cancelled) break starts. The drops are rolled right after, in the same tick.
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    static void onBreakBlock(BreakBlockEvent event) {
        if (!event.getLevel().isClientSide() && event.getPlayer().hasEffect(ModEffects.FORTUNE)) {
            FORTUNE_BREAKERS.put(event.getPos().immutable(), event.getPlayer());
        }
    }

    /**
     * Fortune effect, part 2: every loot roll that asks for the tool's Fortune level (ore drops, crop and flint
     * bonuses, sapling chances, ...) sees at least the effect level, e.g. Fortune X from Starfruit Juice.
     */
    @SubscribeEvent
    static void onBlockLootEnchantmentLevel(EnchantedBlockLootEvent event) {
        if (!event.getEnchantment().is(Enchantments.FORTUNE)) {
            return;
        }
        Player breaker = FORTUNE_BREAKERS.get(event.getPos());
        if (breaker != null && breaker.level() == event.getLevel()) {
            MobEffectInstance fortune = breaker.getEffect(ModEffects.FORTUNE);
            if (fortune != null) {
                event.setEnchantmentLevel(Math.max(event.getEnchantmentLevel(), fortune.getAmplifier() + 1));
            }
        }
    }

    @SubscribeEvent
    static void onServerTickEnd(ServerTickEvent.Post event) {
        FORTUNE_BREAKERS.clear();
    }

    /** Vitality: harmful effects simply don't stick. */
    @SubscribeEvent
    static void onEffectApplicable(MobEffectEvent.Applicable event) {
        MobEffectInstance incoming = event.getEffectInstance();
        if (incoming.getEffect().value().getCategory() == MobEffectCategory.HARMFUL
                && event.getEntity().hasEffect(ModEffects.VITALITY)) {
            event.setResult(MobEffectEvent.Applicable.Result.DO_NOT_APPLY);
        }
    }

    @SubscribeEvent
    static void onEffectExpired(MobEffectEvent.Expired event) {
        startFlightGrace(event.getEntity(), event.getEffectInstance());
    }

    @SubscribeEvent
    static void onEffectRemoved(MobEffectEvent.Remove event) {
        startFlightGrace(event.getEntity(), event.getEffectInstance());
    }

    private static void startFlightGrace(LivingEntity entity, @Nullable MobEffectInstance instance) {
        if (instance != null && instance.is(ModEffects.FLIGHT) && !entity.level().isClientSide()) {
            FLIGHT_GRACE.put(entity.getUUID(), entity.level().getGameTime() + FLIGHT_GRACE_TICKS);
        }
    }

    /** No fall damage for the first landing shortly after Flight wears off. */
    @SubscribeEvent
    static void onFall(LivingFallEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity.level().isClientSide()) {
            return;
        }
        Long safeUntil = FLIGHT_GRACE.get(entity.getUUID());
        if (safeUntil == null) {
            return;
        }
        if (entity.level().getGameTime() > safeUntil) {
            FLIGHT_GRACE.remove(entity.getUUID());
        } else if (event.getDistance() > 3.0) {
            // the real landing (small hops while walking do not use up the protection)
            event.setDamageMultiplier(0.0F);
            FLIGHT_GRACE.remove(entity.getUUID());
        }
    }

    @SubscribeEvent
    static void onServerStopped(ServerStoppedEvent event) {
        FLIGHT_GRACE.clear();
        FORTUNE_BREAKERS.clear();
    }
}
