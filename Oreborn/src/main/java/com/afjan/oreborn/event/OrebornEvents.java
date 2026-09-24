package com.afjan.oreborn.event;

import com.afjan.oreborn.Oreborn;
import com.afjan.oreborn.ability.AreaMining;
import com.afjan.oreborn.ability.ArmorSets;
import com.afjan.oreborn.ability.Combat;
import com.afjan.oreborn.ability.ForceLightning;
import com.afjan.oreborn.ability.OreRadar;
import com.afjan.oreborn.ability.Shock;
import com.afjan.oreborn.ability.ToolActions;
import com.afjan.oreborn.ability.Traits;
import com.afjan.oreborn.item.OrebornToolItem;
import com.afjan.oreborn.material.GearType;
import com.afjan.oreborn.material.OreMaterial;
import com.afjan.oreborn.registry.ModDamageTypes;
import com.afjan.oreborn.registry.ModEffects;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.fish.AbstractFish;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingBreatheEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.living.LivingFallEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingKnockBackEvent;
import net.neoforged.neoforge.event.entity.living.MobEffectEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.BlockDropsEvent;
import net.neoforged.neoforge.event.level.block.BreakBlockEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Game-bus hooks for the abilities that aren't plain item callbacks. */
@EventBusSubscriber(modid = Oreborn.MODID)
public final class OrebornEvents {
    private OrebornEvents() {}

    // ---- tools ---------------------------------------------------------------------------------------------------

    /** Mining patterns, vein/tree felling, landslides, Cryo Seal and Momentum (after protection mods had their say). */
    @SubscribeEvent(priority = EventPriority.LOW)
    static void onBreakBlock(BreakBlockEvent event) {
        if (!event.isCanceled() && event.getPlayer() instanceof ServerPlayer player) {
            AreaMining.onBreak(player, event.getPos(), event.getState());
        }
    }

    /** Material traits: Emberite smelts the drops, Umbrium pockets them. */
    @SubscribeEvent
    static void onBlockDrops(BlockDropsEvent event) {
        if (!(event.getBreaker() instanceof Player player) || !(event.getTool().getItem() instanceof OrebornToolItem tool)) {
            return;
        }
        if (tool.material() == OreMaterial.EMBERITE) {
            Traits.autoSmelt(event);
        } else if (tool.material() == OreMaterial.UMBRIUM) {
            Traits.voidPocket(event, player);
        }
    }

    // ---- damage --------------------------------------------------------------------------------------------------

    @SubscribeEvent
    static void onIncomingDamage(LivingIncomingDamageEvent event) {
        LivingEntity victim = event.getEntity();
        if (victim.level().isClientSide()) {
            return;
        }
        DamageSource source = event.getSource();
        if (victim instanceof ServerPlayer player) {
            if (ArmorSets.tryPhase(player, source)) {
                event.setCanceled(true);
                return;
            }
            event.setAmount(ArmorSets.reduceIncoming(player, source, event.getAmount()));
        }
        if (source.getEntity() instanceof ServerPlayer attacker && source.getDirectEntity() == attacker && victim != attacker
                && source.is(DamageTypes.PLAYER_ATTACK)) {
            event.setAmount(ArmorSets.onMeleeHit(attacker, victim, event.getAmount()));
        }
    }

    @SubscribeEvent
    static void afterDamage(LivingDamageEvent.Post event) {
        LivingEntity entity = event.getEntity();
        if (entity.level().isClientSide()) {
            return;
        }
        // anything hit by electric damage (ours or a real lightning bolt) convulses for a moment (see Shock)
        DamageSource source = event.getSource();
        if (source.is(ModDamageTypes.FORCE_LIGHTNING) || source.is(ModDamageTypes.ELECTROCUTION) || source.is(DamageTypeTags.IS_LIGHTNING)) {
            Shock.shock(entity);
        }
        if (entity instanceof ServerPlayer player && event.getInflictedDamage() > 0.0F) {
            ArmorSets.afterDamage(player, event.getSource());
        }
    }

    /** Emberite set: Phoenix Rebirth (after totems). Emberite weapons: burning enemies explode. */
    @SubscribeEvent
    static void onDeath(LivingDeathEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity.level().isClientSide()) {
            return;
        }
        if (entity instanceof ServerPlayer player && ArmorSets.tryPhoenix(player)) {
            event.setCanceled(true);
            return;
        }
        Combat.onDeath(entity);
    }

    /** Fish fried by the Lightning Staff drop cooked. */
    @SubscribeEvent
    static void onLivingDrops(LivingDropsEvent event) {
        if (event.getEntity() instanceof AbstractFish fish && event.getSource().is(ModDamageTypes.FORCE_LIGHTNING)
                && fish.level() instanceof ServerLevel level) {
            ForceLightning.cookFish(level, fish, event.getDrops());
        }
    }

    @SubscribeEvent
    static void onFall(LivingFallEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (ToolActions.onLanding(player, event.getDistance())) {
            event.setDamageMultiplier(0.0F);
            return;
        }
        if (ArmorSets.wears(player, OreMaterial.UMBRIUM, GearType.BOOTS)) {
            event.setDamageMultiplier(0.0F);
        } else if (event.getDistance() > 3.0 && ArmorSets.hasFlightGrace(player)) {
            event.setDamageMultiplier(0.0F);
            ArmorSets.endFlightGrace(player);
        }
    }

    /** Cryolite leggings: braced while sneaking, nothing can push you around. */
    @SubscribeEvent
    static void onKnockBack(LivingKnockBackEvent event) {
        if (event.getEntity() instanceof Player player && player.isShiftKeyDown() && ArmorSets.wears(player, OreMaterial.CRYOLITE, GearType.LEGGINGS)) {
            event.setCanceled(true);
        }
    }

    /** Cryolite helmet: breathe under water. */
    @SubscribeEvent
    static void onBreathe(LivingBreatheEvent event) {
        if (event.getEntity() instanceof Player player && ArmorSets.wears(player, OreMaterial.CRYOLITE, GearType.HELMET)) {
            event.setCanBreathe(true);
        }
    }

    /** Umbrium helmet: no Darkness or Blindness. Any Cryolite armour: can't be frozen. */
    @SubscribeEvent
    static void onEffectApplicable(MobEffectEvent.Applicable event) {
        LivingEntity entity = event.getEntity();
        var effect = event.getEffectInstance().getEffect();
        if ((effect.is(MobEffects.DARKNESS) || effect.is(MobEffects.BLINDNESS)) && ArmorSets.wears(entity, OreMaterial.UMBRIUM, GearType.HELMET)
                || effect.is(ModEffects.FROZEN) && ArmorSets.wearsAny(entity, OreMaterial.CRYOLITE)) {
            event.setResult(MobEffectEvent.Applicable.Result.DO_NOT_APPLY);
        }
    }

    // ---- ticking and bookkeeping ---------------------------------------------------------------------------------

    @SubscribeEvent
    static void onPlayerTick(PlayerTickEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            ArmorSets.tick(player);
        }
    }

    @SubscribeEvent
    static void onServerTick(ServerTickEvent.Post event) {
        OreRadar.tick(event.getServer());
        long time = event.getServer().overworld().getGameTime();
        if (time % 200 == 0) {
            Combat.prune(time);
            ToolActions.prune(time);
        }
    }

    /** Ore Radar outlines that were saved to disk (game closed while they glowed) are dropped on load. */
    @SubscribeEvent
    static void onEntityJoin(EntityJoinLevelEvent event) {
        if (!event.getLevel().isClientSide() && OreRadar.isStale(event.getEntity())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        ArmorSets.forget(event.getEntity().getUUID());
        AreaMining.forget(event.getEntity().getUUID());
        ForceLightning.forget(event.getEntity().getUUID());
    }

    @SubscribeEvent
    static void onServerStopped(ServerStoppedEvent event) {
        ArmorSets.clear();
        AreaMining.clear();
        Combat.clear();
        ToolActions.clear();
        OreRadar.clear();
        ForceLightning.clear();
    }
}
