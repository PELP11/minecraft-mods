package com.afjan.tempered.client;

import com.afjan.tempered.mastery.Mastery;
import com.afjan.tempered.mastery.Milestone;
import com.afjan.tempered.mastery.Track;
import com.afjan.tempered.mastery.Tracks;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.HashMap;
import java.util.Map;

/** The most advanced copy of every vanilla tool in the player's inventory. */
final class OwnedTools {
    private OwnedTools() {
    }

    static Map<Track, ItemStack> best(Player player) {
        Map<Track, ItemStack> best = new HashMap<>();
        var inventory = player.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) consider(best, inventory.getItem(i));
        consider(best, player.containerMenu.getCarried());
        return best;
    }

    private static void consider(Map<Track, ItemStack> best, ItemStack stack) {
        Track track = Tracks.get(stack);
        if (track == null || Tracks.of(track.kind, track.tier) != track) return;
        ItemStack current = best.get(track);
        if (current == null || score(track, stack) > score(track, current)) best.put(track, stack);
    }

    /** Level first, then how far the next milestone is. */
    static double score(Track track, ItemStack stack) {
        Mastery mastery = Mastery.of(stack);
        if (mastery == null) return 0;
        int level = track.level(mastery);
        if (level >= track.maxLevel()) return track.maxLevel() + 1;
        Milestone next = track.milestones.get(level);
        return level + next.progress(mastery) * 0.99;
    }
}
