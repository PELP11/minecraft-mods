package com.afjan.oreborn.ability;

import java.util.Iterator;
import java.util.Optional;

import com.afjan.oreborn.registry.ModTags;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.item.crafting.SmeltingRecipe;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.level.BlockDropsEvent;

/** Material traits shared by every tool of a material, applied to block drops. */
public final class Traits {
    private Traits() {}

    /** What a stack turns into in a furnace (whole stack), or the stack itself if it doesn't smelt. */
    public static ItemStack smelted(ServerLevel level, ItemStack stack) {
        SingleRecipeInput input = new SingleRecipeInput(stack);
        return level.recipeAccess().getRecipeFor(RecipeType.SMELTING, input, level).map(recipe -> {
            ItemStack result = recipe.value().assemble(input);
            result.setCount(result.getCount() * stack.getCount());
            return result;
        }).orElse(stack);
    }

    /** Emberite ("Molten"): raw ores, ores, sand, potatoes, kelp... drop already smelted, with the furnace XP. */
    public static void autoSmelt(BlockDropsEvent event) {
        ServerLevel level = event.getLevel();
        float experience = 0.0F;
        boolean smelted = false;
        for (ItemEntity drop : event.getDrops()) {
            ItemStack stack = drop.getItem();
            if (!stack.is(ModTags.EMBERITE_SMELTABLE)) {
                continue;
            }
            SingleRecipeInput input = new SingleRecipeInput(stack);
            Optional<RecipeHolder<SmeltingRecipe>> recipe = level.recipeAccess().getRecipeFor(RecipeType.SMELTING, input, level);
            if (recipe.isEmpty()) {
                continue;
            }
            ItemStack result = recipe.get().value().assemble(input);
            result.setCount(result.getCount() * stack.getCount());
            experience += recipe.get().value().experience() * stack.getCount();
            drop.setItem(result);
            smelted = true;
        }
        if (smelted) {
            int xp = Mth.floor(experience);
            if (level.getRandom().nextFloat() < experience - xp) {
                xp++;
            }
            event.setDroppedExperience(event.getDroppedExperience() + xp);
            Vec3 at = Vec3.atCenterOf(event.getPos());
            level.sendParticles(ParticleTypes.FLAME, at.x, at.y, at.z, 6, 0.25, 0.25, 0.25, 0.01);
        }
    }

    /** Umbrium ("Void Pocket"): drops and experience go straight to the player; only what doesn't fit stays behind. */
    public static void voidPocket(BlockDropsEvent event, Player player) {
        boolean pocketed = false;
        Iterator<ItemEntity> it = event.getDrops().iterator();
        while (it.hasNext()) {
            ItemEntity drop = it.next();
            ItemStack stack = drop.getItem().copy();
            int before = stack.getCount();
            player.getInventory().add(stack);
            pocketed |= stack.getCount() != before;
            if (stack.isEmpty()) {
                it.remove();
            } else {
                drop.setItem(stack);
            }
        }
        int xp = event.getDroppedExperience();
        if (xp > 0) {
            player.giveExperiencePoints(xp);
            event.setDroppedExperience(0);
        }
        if (pocketed && !AreaMining.isBreakingExtra()) {
            ServerLevel level = event.getLevel();
            Vec3 at = Vec3.atCenterOf(event.getPos());
            level.sendParticles(ParticleTypes.REVERSE_PORTAL, at.x, at.y, at.z, 6, 0.25, 0.25, 0.25, 0.02);
            level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ITEM_PICKUP, player.getSoundSource(), 0.2F,
                    (level.getRandom().nextFloat() - level.getRandom().nextFloat()) * 1.4F + 2.0F);
        }
    }
}
