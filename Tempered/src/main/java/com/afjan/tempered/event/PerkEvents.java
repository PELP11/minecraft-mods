package com.afjan.tempered.event;

import com.afjan.tempered.ability.Abilities;
import com.afjan.tempered.mastery.Kind;
import com.afjan.tempered.mastery.Mastery;
import com.afjan.tempered.mastery.Perk;
import com.afjan.tempered.mastery.Track;
import com.afjan.tempered.mastery.Tracks;
import com.afjan.tempered.mixin.FishingHookAccessor;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Prediction;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.entity.projectile.arrow.ThrownTrident;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.MaceItem;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingEntityUseItemEvent;
import net.minecraftforge.event.entity.living.LivingExperienceDropEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.living.LootingLevelEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.listener.Priority;

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
        PlayerEvent.BreakSpeed.BUS.addListener(PerkEvents::onBreakSpeed);
        BlockEvent.BreakEvent.BUS.addListener(PerkEvents::onBreakXp);
        LivingHurtEvent.BUS.addListener(PerkEvents::onHurt);
        LivingDamageEvent.BUS.addListener(Priority.LOWEST, e -> {
            onDamaged(e);
            return false;
        });
        LootingLevelEvent.BUS.addListener(PerkEvents::onLooting);
        LivingExperienceDropEvent.BUS.addListener(PerkEvents::onExperienceDrop);
        LivingEntityUseItemEvent.Start.BUS.addListener(PerkEvents::onUseStart);
        LivingEntityUseItemEvent.Tick.BUS.addListener(PerkEvents::onUseTick);
        EntityJoinLevelEvent.BUS.addListener(PerkEvents::onJoin);
        TickEvent.PlayerTickEvent.Post.BUS.addListener(PerkEvents::onPlayerTick);
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
    private static boolean onBreakXp(BlockEvent.BreakEvent event) {
        if (event.getExpToDrop() > 0) {
            double xp = Mastery.perk(event.getPlayer().getMainHandItem(), Perk.XP);
            if (xp > 0) event.setExpToDrop(scaleRandomly(event.getExpToDrop(), 1.0 + xp / 100.0));
        }
        return false;
    }

    // ------------------------------------------------------------------------------------------------ combat

    private static boolean onHurt(LivingHurtEvent event) {
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
        LivingEntity victim = event.getEntity();
        float multiplier = (float) (1.0 + Mastery.perk(weapon, Perk.DAMAGE) / 100.0);
        if (Mastery.perk(weapon, Perk.EXECUTIONER) > 0 && victim.getHealth() < victim.getMaxHealth() * 0.35F) multiplier *= 1.5F;
        if (Mastery.perk(weapon, Perk.CAVALRY) > 0 && hit.how() == Weapons.How.MELEE && player.getVehicle() != null) multiplier *= 1.4F;
        event.setAmount(event.getAmount() * multiplier);

        if (hit.how() == Weapons.How.MELEE && Mastery.perk(weapon, Perk.SHOCKWAVE) > 0 && MaceItem.canSmashAttack(player)
                && player instanceof ServerPlayer serverPlayer) {
            Abilities.shockwave(serverPlayer, victim, event.getAmount() * 0.5F);
        }
        return false;
    }

    /** Lifesteal, from the damage that got through armour. */
    private static void onDamaged(LivingDamageEvent event) {
        if (event.getAmount() <= 0) return;
        DamageSource source = event.getSource();
        if (!(source.getEntity() instanceof Player player) || source.getDirectEntity() != player) return;
        double lifesteal = Mastery.perk(player.getMainHandItem(), Perk.LIFESTEAL);
        if (lifesteal > 0 && player.isAlive()) player.heal(event.getAmount() * (float) (lifesteal / 100.0));
    }

    private static void onLooting(LootingLevelEvent event) {
        DamageSource source = event.getDamageSource();
        if (source == null || !(source.getEntity() instanceof Player player)) return;
        Weapons.Hit hit = Weapons.find(player, source);
        if (hit != null) event.setLootingLevel(event.getLootingLevel() + (int) Mastery.perk(hit.perks(), Perk.LOOTING));
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
                player.getInventory().placeItemBackInInventory(back, Prediction.SERVER_ONLY);
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

    private static void onPlayerTick(TickEvent.PlayerTickEvent.Post event) {
        if (!(event.player() instanceof ServerPlayer player) || player.tickCount % 10 != 0) return;
        Abilities.tickCavalry(player);
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
