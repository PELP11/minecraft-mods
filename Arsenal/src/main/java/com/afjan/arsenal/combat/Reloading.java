package com.afjan.arsenal.combat;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.afjan.arsenal.gun.Caliber;
import com.afjan.arsenal.gun.GunData;
import com.afjan.arsenal.gun.GunType;
import com.afjan.arsenal.item.Guns;
import com.afjan.arsenal.registry.ModItems;
import com.afjan.arsenal.registry.ModSounds;

import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Prediction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * Magazine changes. A reload runs for a while and then completes, so it is kept out of the item stack: the state
 * lives here and is ticked once per player per tick from {@link com.afjan.arsenal.event.ArsenalEvents}.
 *
 * <p>While it runs, the reload plays its foley at fixed points of its length ({@link #plan}): magazine out, magazine
 * in, charging handle; one shell at a time for tube-fed shotguns and the M32; the bolt for the AWP.
 */
public final class Reloading {
    /** A sound at a fraction (0..1) of the reload's length. */
    private record Cue(float at, Holder<SoundEvent> sound) {}

    private static final class State {
        final InteractionHand hand;
        final ItemStack stack;
        final int startTick;
        final int finishTick;
        final Caliber caliber;
        final int rounds;
        final List<Cue> cues;
        int nextCue;

        State(InteractionHand hand, ItemStack stack, int startTick, int finishTick, Caliber caliber, int rounds,
                List<Cue> cues) {
            this.hand = hand;
            this.stack = stack;
            this.startTick = startTick;
            this.finishTick = finishTick;
            this.caliber = caliber;
            this.rounds = rounds;
            this.cues = cues;
        }

        InteractionHand hand() {
            return this.hand;
        }

        ItemStack stack() {
            return this.stack;
        }

        int finishTick() {
            return this.finishTick;
        }

        Caliber caliber() {
            return this.caliber;
        }

        int rounds() {
            return this.rounds;
        }
    }

    private static final Map<UUID, State> ACTIVE = new HashMap<>();

    private Reloading() {}

    public static boolean isReloading(ServerPlayer player) {
        return ACTIVE.containsKey(player.getUUID());
    }

    public static void cancel(ServerPlayer player) {
        ACTIVE.remove(player.getUUID());
    }

    public static void start(ServerPlayer player, InteractionHand hand) {
        start(player, hand, false);
    }

    /**
     * @param switchCaliber when true the gun is loaded with the next cartridge it accepts instead of the one already
     *                      in it, which is how a shotgun moves between buckshot and slugs.
     */
    public static void start(ServerPlayer player, InteractionHand hand, boolean switchCaliber) {
        ItemStack stack = player.getItemInHand(hand);
        GunType type = Guns.typeOf(stack);
        if (type == null || isReloading(player)) {
            return;
        }
        GunData data = Guns.dataOf(stack);
        Caliber caliber = choose(player, type, data, switchCaliber);
        if (caliber == null) {
            play(player, ModSounds.DRY_FIRE);
            return;
        }
        int capacity = type.effectiveMagazine(data);
        int kept = caliber == data.caliber() ? data.ammo() : 0;
        if (kept >= capacity) {
            return;
        }
        int available = player.getAbilities().instabuild ? capacity : count(player, caliber);
        if (available <= 0) {
            return;
        }
        int rounds = Math.min(capacity - kept, available);

        ACTIVE.put(player.getUUID(), new State(hand, stack, player.tickCount, player.tickCount + type.effectiveReload(data),
                caliber, kept + rounds, plan(type, rounds)));
        RailCharge.cancel(player); // reloading drains whatever was charging
    }

    /** The foley of a reload, by gun; `loading` is how many rounds go in. */
    private static List<Cue> plan(GunType type, int loading) {
        List<Cue> cues = new ArrayList<>();
        switch (type) {
            case AK47, M4A1, SCAR_H, AUG, SVD -> {
                cues.add(new Cue(0.02F, ModSounds.RIFLE_MAG_OUT));
                cues.add(new Cue(0.45F, ModSounds.RIFLE_MAG_IN));
                cues.add(new Cue(0.82F, ModSounds.RIFLE_CHARGE));
            }
            case GLOCK17, M1911, M9, DEAGLE -> {
                cues.add(new Cue(0.02F, ModSounds.PISTOL_MAG_OUT));
                cues.add(new Cue(0.5F, ModSounds.PISTOL_MAG_IN));
                cues.add(new Cue(0.85F, ModSounds.PISTOL_SLIDE));
            }
            case BARRETT, AA12 -> {
                cues.add(new Cue(0.02F, ModSounds.HEAVY_MAG_OUT));
                cues.add(new Cue(0.45F, ModSounds.HEAVY_MAG_IN));
                cues.add(new Cue(0.82F, type == GunType.BARRETT ? ModSounds.HEAVY_CHARGE : ModSounds.RIFLE_CHARGE));
            }
            case AWP -> {
                cues.add(new Cue(0.02F, ModSounds.BOLT_OPEN));
                cues.add(new Cue(0.25F, ModSounds.RIFLE_MAG_OUT));
                cues.add(new Cue(0.55F, ModSounds.RIFLE_MAG_IN));
                cues.add(new Cue(0.85F, ModSounds.BOLT_CLOSE));
            }
            case REMINGTON870, SPAS12 -> {
                shells(cues, loading, 0.06F, 0.8F, ModSounds.SHOTGUN_SHELL);
                cues.add(new Cue(0.9F, ModSounds.SHOTGUN_PUMP));
            }
            case SAWED_OFF -> {
                cues.add(new Cue(0.02F, ModSounds.BREAK_OPEN));
                shells(cues, Math.min(loading, 2), 0.3F, 0.65F, ModSounds.SHOTGUN_SHELL);
                cues.add(new Cue(0.85F, ModSounds.BREAK_CLOSE));
            }
            case RPG7 -> cues.add(new Cue(0.2F, ModSounds.ROCKET_LOAD));
            case M32 -> {
                cues.add(new Cue(0.02F, ModSounds.CYLINDER_OPEN));
                shells(cues, loading, 0.14F, 0.8F, ModSounds.CYLINDER_ROUND);
                cues.add(new Cue(0.88F, ModSounds.CYLINDER_CLOSE));
            }
            case RAILGUN -> {
                cues.add(new Cue(0.02F, ModSounds.HEAVY_MAG_OUT));
                cues.add(new Cue(0.3F, ModSounds.HEAVY_MAG_IN));
                cues.add(new Cue(0.55F, ModSounds.RAIL_CHARGE));
            }
        }
        return cues;
    }

    /** One loading sound per round, spread evenly between two points of the reload (at most eight). */
    private static void shells(List<Cue> cues, int count, float from, float to, Holder<SoundEvent> sound) {
        int n = Math.max(1, Math.min(count, 8));
        for (int i = 0; i < n; i++) {
            cues.add(new Cue(from + (to - from) * (i + 0.5F) / n, sound));
        }
    }

    private static void play(ServerPlayer player, Holder<SoundEvent> sound) {
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(), sound.value(), SoundSource.PLAYERS,
                1.0F, 0.96F + player.getRandom().nextFloat() * 0.08F);
    }

    /** Called once per tick per player; finishes or aborts a reload in progress. */
    public static void tick(ServerPlayer player) {
        State state = ACTIVE.get(player.getUUID());
        if (state == null) {
            return;
        }
        if (player.getItemInHand(state.hand()) != state.stack() || !player.isAlive()) {
            ACTIVE.remove(player.getUUID());
            return;
        }
        int elapsed = player.tickCount - state.startTick;
        int duration = Math.max(1, state.finishTick - state.startTick);
        while (state.nextCue < state.cues.size() && elapsed >= state.cues.get(state.nextCue).at() * duration) {
            play(player, state.cues.get(state.nextCue++).sound());
        }
        if (player.tickCount < state.finishTick()) {
            return;
        }
        ACTIVE.remove(player.getUUID());

        ItemStack stack = state.stack();
        GunType type = Guns.typeOf(stack);
        if (type == null) {
            return;
        }
        GunData data = Guns.dataOf(stack);
        int kept = state.caliber() == data.caliber() ? data.ammo() : 0;
        int wanted = state.rounds() - kept;
        if (kept == 0 && data.ammo() > 0) {
            // Swapping cartridge: the rounds already in the gun go back into the player's pack rather than vanishing.
            give(player, data.caliber(), data.ammo());
        }
        int taken = player.getAbilities().instabuild ? wanted : take(player, state.caliber(), wanted);
        Guns.set(stack, data.loaded(state.caliber(), kept + taken));
    }

    private static Caliber choose(ServerPlayer player, GunType type, GunData data, boolean switchCaliber) {
        if (player.getAbilities().instabuild) {
            if (!switchCaliber && type.accepts(data.caliber())) {
                return data.caliber();
            }
            int index = type.calibers().indexOf(data.caliber());
            return type.calibers().get((index + 1) % type.calibers().size());
        }
        if (!switchCaliber && type.accepts(data.caliber()) && count(player, data.caliber()) > 0) {
            return data.caliber();
        }
        int start = Math.max(0, type.calibers().indexOf(data.caliber()));
        for (int offset = 1; offset <= type.calibers().size(); offset++) {
            Caliber candidate = type.calibers().get((start + offset) % type.calibers().size());
            if (count(player, candidate) > 0) {
                return candidate;
            }
        }
        return null;
    }

    private static int count(ServerPlayer player, Caliber caliber) {
        Item item = ModItems.AMMO.get(caliber).get();
        int total = 0;
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (stack.is(item)) {
                total += stack.getCount();
            }
        }
        return total;
    }

    private static int take(ServerPlayer player, Caliber caliber, int wanted) {
        Item item = ModItems.AMMO.get(caliber).get();
        int taken = 0;
        for (int slot = 0; slot < player.getInventory().getContainerSize() && taken < wanted; slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (stack.is(item)) {
                int move = Math.min(stack.getCount(), wanted - taken);
                stack.shrink(move);
                taken += move;
            }
        }
        return taken;
    }

    private static void give(ServerPlayer player, Caliber caliber, int rounds) {
        if (player.getAbilities().instabuild) {
            return;
        }
        ItemStack stack = new ItemStack(ModItems.AMMO.get(caliber).get(), rounds);
        if (!player.getInventory().add(stack) && !stack.isEmpty()) {
            player.drop(stack, false, Prediction.SERVER_ONLY);
        }
    }
}
