package com.afjan.tempered.event;

import com.afjan.tempered.event.Events;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import com.afjan.tempered.ability.Abilities;
import com.afjan.tempered.mastery.Kind;
import com.afjan.tempered.mastery.Mastery;
import com.afjan.tempered.mastery.Perk;
import com.afjan.tempered.mastery.Track;
import com.afjan.tempered.mastery.Tracks;
import com.afjan.tempered.mixin.FishingHookAccessor;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.ThrownTrident;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.MaceItem;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;
import net.neoforged.neoforge.event.entity.living.LivingExperienceDropEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.enchanting.GetEnchantmentLevelEvent;
import net.neoforged.neoforge.event.level.BlockDropsEvent;
import net.minecraft.world.item.enchantment.Enchantments;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.BlockEvent;

import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.ThreadLocalRandom;

/** What milestones give: faster mining, more damage, looting, lifesteal, faster draw, better fishing ... */
public final class PerkEvents {
    /** Fractional extra ticks of a draw/wind-up, per entity (client and server keep their own). */
    private static final Map<LivingEntity, float[]> DRAW = new WeakHashMap<>();

    private PerkEvents() {
    }

    public static void register() {
        Events.listen(PlayerEvent.BreakSpeed.class, PerkEvents::onBreakSpeed);
        Events.listen(BlockDropsEvent.class, PerkEvents::onBlockDrops);
        Events.listen(LivingIncomingDamageEvent.class, PerkEvents::onHurt);
        Events.listen(EventPriority.LOWEST, LivingDamageEvent.Post.class, PerkEvents::onDamaged);
        Events.listen(EventPriority.LOWEST, LivingDeathEvent.class, e -> {
            onKill(e);
            return false;
        });
        Events.listen(GetEnchantmentLevelEvent.class, PerkEvents::onEnchantmentLevel);
        Events.listen(LivingExperienceDropEvent.class, PerkEvents::onExperienceDrop);
        Events.listen(LivingEntityUseItemEvent.Start.class, PerkEvents::onUseStart);
        Events.listen(LivingEntityUseItemEvent.Tick.class, PerkEvents::onUseTick);
        Events.listen(EntityJoinLevelEvent.class, PerkEvents::onJoin);
        Events.listen(PlayerTickEvent.Post.class, PerkEvents::onPlayerTick);
    }

    // ------------------------------------------------------------------------------------------------ mining

    private static boolean onBreakSpeed(PlayerEvent.BreakSpeed event) {
        ItemStack tool = event.getEntity().getMainHandItem();
        double speed = Mastery.perk(tool, Perk.SPEED);
        if (speed > 0 && ProgressEvents.isEffective(tool, event.getState())) {
            event.setNewSpeed(event.getNewSpeed() * (float) (1.0 + speed / 100.0));
        }
        return false;
    }

    /** Gilded (golden tools): more experience from ores. */
    private static void onBlockDrops(BlockDropsEvent event) {
        if (event.getDroppedExperience() > 0 && event.getBreaker() instanceof Player) {
            double xp = Mastery.perk(event.getTool(), Perk.XP);
            if (xp > 0) event.setDroppedExperience(scaleRandomly(event.getDroppedExperience(), 1.0 + xp / 100.0));
        }
    }

    // ------------------------------------------------------------------------------------------------ combat

    private static boolean onHurt(LivingIncomingDamageEvent event) {
        LivingEntity victim = event.getEntity();
        DamageSource source = event.getSource();
        if (!(source.getEntity() instanceof Player player) || Abilities.isShockwaveActive()) return false;
        Weapons.Hit hit = Weapons.find(player, source);
        if (hit == null) return false;
        ItemStack weapon = hit.perks();
        if (hit.how() == Weapons.How.MELEE && BrokenTools.isBrokenTool(weapon)) {
            // A broken weapon hits like a fist (vanilla already removed its attack bonus; this also stops Sharpness).
            event.setAmount(Math.min(event.getAmount(), 1.0F));
            return false;
        }
        float multiplier = (float) (1.0 + Mastery.perk(weapon, Perk.DAMAGE) / 100.0);
        if (Mastery.perk(weapon, Perk.EXECUTIONER) > 0 && victim.getHealth() < victim.getMaxHealth() * 0.35F) multiplier *= 1.5F;
        event.setAmount(event.getAmount() * multiplier);

        if (hit.how() == Weapons.How.MELEE && Mastery.perk(weapon, Perk.SHOCKWAVE) > 0 && MaceItem.canSmashAttack(player)
                && player instanceof ServerPlayer serverPlayer) {
            Abilities.shockwave(serverPlayer, victim, event.getAmount() * 0.5F);
        }
        return false;
    }

    /** Soul Harvest (sword capstone): every kill heals two hearts and gives Strength for five seconds. */
    private static void onKill(LivingDeathEvent event) {
        LivingEntity victim = event.getEntity();
        if (victim.level().isClientSide() || victim instanceof ArmorStand) return;
        if (!(event.getSource().getEntity() instanceof ServerPlayer player) || player == victim || !player.isAlive()) return;
        Weapons.Hit hit = Weapons.find(player, event.getSource());
        if (hit == null || Mastery.perk(hit.perks(), Perk.SOUL_HARVEST) <= 0) return;
        player.heal(4.0F);
        player.addEffect(new MobEffectInstance(MobEffects.STRENGTH, 100, 0, false, false, true));
        player.level().sendParticles(ParticleTypes.SOUL, victim.getX(), victim.getY() + victim.getBbHeight() * 0.5, victim.getZ(),
                8, 0.3, 0.4, 0.3, 0.02);
    }

    /** Lifesteal, from the damage that got through armour. */
    private static void onDamaged(LivingDamageEvent.Post event) {
        if (event.getNewDamage() <= 0) return;
        DamageSource source = event.getSource();
        if (!(source.getEntity() instanceof Player player) || source.getDirectEntity() != player) return;
        double lifesteal = Mastery.perk(player.getMainHandItem(), Perk.LIFESTEAL);
        if (lifesteal > 0 && player.isAlive()) player.heal(event.getNewDamage() * (float) (lifesteal / 100.0));
    }

    /** Looting perk: the weapon counts as having that many more levels of Looting (loot tables ask the held weapon). */
    private static void onEnchantmentLevel(GetEnchantmentLevelEvent event) {
        if (!event.isTargetting(Enchantments.LOOTING)) return;
        int bonus = (int) Mastery.perk(event.getStack(), Perk.LOOTING);
        if (bonus <= 0) return;
        event.getHolder(Enchantments.LOOTING).ifPresent(looting ->
                event.getEnchantments().set(looting, event.getEnchantments().getLevel(looting) + bonus));
    }

    private static boolean onExperienceDrop(LivingExperienceDropEvent event) {
        Player player = event.getAttackingPlayer();
        if (player != null && event.getDroppedExperience() > 0) {
            double xp = Mastery.perk(player.getMainHandItem(), Perk.XP);
            if (xp > 0) event.setDroppedExperience(scaleRandomly(event.getDroppedExperience(), 1.0 + xp / 100.0));
        }
        return false;
    }

    // ------------------------------------------------------------------------------------------------ bows, crossbows, tridents

    private static boolean onUseStart(LivingEntityUseItemEvent.Start event) {
        ItemStack stack = event.getItem();
        Track track = Tracks.get(stack);
        if (track != null && track.kind.charges()) {
            // Arrows carry a copy of their bow: the id lets a kill find the real bow again.
            if (!event.getEntity().level().isClientSide()) Mastery.getOrCreate(stack);
            synchronized (DRAW) {
                DRAW.put(event.getEntity(), new float[1]);
            }
        }
        return false;
    }

    /** Faster draw: skip extra ticks of the use countdown, deterministically on both sides. */
    private static boolean onUseTick(LivingEntityUseItemEvent.Tick event) {
        double draw = Mastery.perk(event.getItem(), Perk.DRAW);
        if (draw <= 0 || event.getDuration() <= 1) return false;
        float[] acc;
        synchronized (DRAW) {
            acc = DRAW.computeIfAbsent(event.getEntity(), e -> new float[1]);
        }
        acc[0] += (float) (draw / 100.0);
        int extra = (int) acc[0];
        if (extra > 0) {
            acc[0] -= extra;
            event.setDuration(Math.max(1, event.getDuration() - extra));
        }
        return false;
    }

    private static boolean onJoin(EntityJoinLevelEvent event) {
        Entity entity = event.getEntity();
        if (event.getLevel().isClientSide() || event.loadedFromDisk()) return false;
        if (entity instanceof AbstractArrow arrow && !(entity instanceof ThrownTrident)
                && arrow.getOwner() instanceof ServerPlayer player && !Abilities.isSpawningVolley()) {
            ItemStack weapon = arrow.getWeaponItem();
            if (weapon == null) return false;
            double saver = Mastery.perk(weapon, Perk.ARROW_SAVER);
            if (saver > 0 && arrow.pickup == AbstractArrow.Pickup.ALLOWED && ThreadLocalRandom.current().nextDouble() * 100 < saver) {
                ItemStack back = arrow.getPickupItemStackOrigin().copyWithCount(1);
                arrow.pickup = AbstractArrow.Pickup.CREATIVE_ONLY;
                player.getInventory().placeItemBackInInventory(back);
            }
            if (Mastery.perk(weapon, Perk.VOLLEY) > 0 && arrow.isCritArrow()) Abilities.volley(player, arrow, weapon);
        } else if (entity instanceof FishingHook hook && hook.getPlayerOwner() instanceof ServerPlayer player) {
            ItemStack rod = ProgressEvents.rodOf(player);
            if (rod == null) return false;
            FishingHookAccessor access = (FishingHookAccessor) hook;
            int lure = (int) Mastery.perk(rod, Perk.LURE);
            // Vanilla waits 100..600 ticks minus lure; 600 or more would mean no fish ever bites.
            if (lure > 0) access.tempered$setLureSpeed(Math.min(500, access.tempered$getLureSpeed() + 100 * lure));
            int luck = (int) Mastery.perk(rod, Perk.LUCK);
            if (luck > 0) access.tempered$setLuck(access.tempered$getLuck() + luck);
        }
        return false;
    }

    // ------------------------------------------------------------------------------------------------ per tick

    private static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.tickCount % 10 != 0) return;
        Abilities.tickAutoload(player);
    }

    static int scaleRandomly(int amount, double factor) {
        double scaled = amount * factor;
        int whole = (int) scaled;
        return whole + (ThreadLocalRandom.current().nextDouble() < scaled - whole ? 1 : 0);
    }

    static boolean isKind(ItemStack stack, Kind kind) {
        Track track = Tracks.get(stack);
        return track != null && track.kind == kind;
    }
}
