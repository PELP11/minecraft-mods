package com.afjan.drillworks.drill;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.jetbrains.annotations.Nullable;

import com.afjan.drillworks.item.DrillHeadItem;
import com.afjan.drillworks.registry.ModTags;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.Tags;

/** What the drill bores, how fast, what it costs and what it collects. Shared by the drill entity and the tests. */
public final class Boring {
    /** Bore work done per tick at speed 1.0 (a layer of 9 stone = 13.5 work). */
    public static final float WORK_PER_TICK = 0.6F;
    public static final int VEIN_LIMIT = 48;

    private Boring() {}

    /** Everything a drill head (with its modules) does. */
    public record Setup(HeadMaterial material, int fortune, int efficiency, boolean silkTouch, boolean smelting,
            int reinforced, boolean wideBore, boolean veinSeeker, boolean voidFilter, int fuelSaver) {

        public static @Nullable Setup of(ItemStack head) {
            if (!(head.getItem() instanceof DrillHeadItem item)) {
                return null;
            }
            HeadMaterial material = item.material();
            DrillModules m = DrillHeadItem.modules(head);
            int sockets = material.sockets();
            int fortune = m.count(Module.FORTUNE, sockets) + (material.trait() == HeadMaterial.Trait.LUCKY ? 1 : 0);
            boolean silk = m.count(Module.SILK_TOUCH, sockets) > 0 || material.trait() == HeadMaterial.Trait.RESONANT;
            boolean smelt = m.count(Module.SMELTING, sockets) > 0 || material.trait() == HeadMaterial.Trait.MOLTEN;
            return new Setup(material, fortune, m.count(Module.EFFICIENCY, sockets), silk, smelt,
                    m.count(Module.REINFORCED, sockets), m.count(Module.WIDE_BORE, sockets) > 0,
                    m.count(Module.VEIN_SEEKER, sockets) > 0, m.count(Module.VOID_FILTER, sockets) > 0,
                    m.count(Module.FUEL_SAVER, sockets));
        }

        /** Bore work per tick. */
        public float rate() {
            return this.material.speed() * (1.0F + 0.4F * this.efficiency) * WORK_PER_TICK;
        }

        public float fuelFactor() {
            float f = this.material.trait() == HeadMaterial.Trait.CONDUCTIVE ? 0.75F : 1.0F;
            return f * (float) Math.pow(0.7, this.fuelSaver);
        }

        /** Chance that a drilled block costs the head one point of durability. */
        public float wearChance() {
            return (float) Math.pow(0.6, this.reinforced);
        }

        /** Bore width and height in blocks. */
        public int size() {
            return this.wideBore ? 5 : 3;
        }
    }

    public static Vec3 forward(float yaw) {
        float r = yaw * Mth.DEG_TO_RAD;
        return new Vec3(-Mth.sin(r), 0.0, Mth.cos(r));
    }

    /**
     * The cells of the next bore layer in front of a drill standing at {@code bottom} (centre of its base): {@code size}
     * wide and high, {@code reach} blocks ahead. tilt 1 = boring upwards (the floor stays as a step to climb, one
     * extra row of head room), -1 = downwards (the floor is dug one deeper, the machine drops onto it).
     */
    public static List<BlockPos> layer(Vec3 bottom, float yaw, double reach, int size, int tilt) {
        Vec3 f = forward(yaw);
        Vec3 right = new Vec3(-f.z, 0.0, f.x);
        int half = size / 2;
        int floor = Mth.floor(bottom.y + 0.05);
        int low = tilt > 0 ? 1 : tilt < 0 ? -1 : 0;
        int high = tilt > 0 ? size : size - 1;
        Set<BlockPos> cells = new LinkedHashSet<>();
        Vec3 ahead = bottom.add(f.scale(reach));
        for (int h = low; h <= high; h++) {
            for (int a = -half; a <= half; a++) {
                Vec3 p = ahead.add(right.scale(a));
                cells.add(new BlockPos(Mth.floor(p.x), floor + h, Mth.floor(p.z)));
            }
        }
        return new ArrayList<>(cells);
    }

    /** Anything solid, plants and snow included; air and liquids are left alone. */
    public static boolean needsDrilling(BlockState state) {
        return !state.isAir() && !(state.getBlock() instanceof LiquidBlock);
    }

    public static boolean isUnbreakable(ServerLevel level, BlockPos pos, BlockState state) {
        return state.getDestroySpeed(level, pos) < 0.0F;
    }

    public static boolean tooHard(Setup setup, BlockState state) {
        return state.requiresCorrectToolForDrops() && state.is(setup.material().incorrectBlocks());
    }

    public static float hardness(ServerLevel level, BlockPos pos, BlockState state) {
        return Math.max(0.05F, state.getDestroySpeed(level, pos));
    }

    /** The pickaxe the loot tables see: a netherite pickaxe with the head's Fortune or Silk Touch. */
    public static ItemStack tool(ServerLevel level, Setup setup) {
        ItemStack tool = new ItemStack(Items.NETHERITE_PICKAXE);
        var enchantments = level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
        if (setup.silkTouch()) {
            tool.enchant(enchantments.getOrThrow(Enchantments.SILK_TOUCH), 1);
        } else if (setup.fortune() > 0) {
            tool.enchant(enchantments.getOrThrow(Enchantments.FORTUNE), setup.fortune());
        }
        return tool;
    }

    /** What the drill keeps from one block: loot with Fortune/Silk Touch, smelted, junk filtered out. */
    public static List<ItemStack> drops(ServerLevel level, BlockPos pos, BlockState state, Setup setup, ItemStack tool,
            @Nullable Entity breaker) {
        BlockEntity blockEntity = level.getBlockEntity(pos);
        List<ItemStack> out = new ArrayList<>();
        for (ItemStack stack : Block.getDrops(state, level, pos, blockEntity, breaker, tool)) {
            if (setup.voidFilter() && stack.is(ModTags.VOID_FILTER)) {
                continue;
            }
            out.add(setup.smelting() ? smelted(level, stack) : stack);
        }
        return out;
    }

    public static ItemStack smelted(ServerLevel level, ItemStack stack) {
        if (stack.is(ModTags.NEVER_SMELT)) {
            return stack;
        }
        SingleRecipeInput input = new SingleRecipeInput(stack);
        return level.recipeAccess().getRecipeFor(RecipeType.SMELTING, input, level).map(recipe -> {
            ItemStack result = recipe.value().assemble(input);
            result.setCount(result.getCount() * stack.getCount());
            return result;
        }).orElse(stack);
    }

    /** Vein Seeker: the ores touching the bore, followed through the rock up to VEIN_LIMIT blocks. */
    public static List<BlockPos> vein(ServerLevel level, Collection<BlockPos> bore, Setup setup) {
        Set<BlockPos> seen = new LinkedHashSet<>(bore);
        ArrayDeque<BlockPos> queue = new ArrayDeque<>(bore);
        List<BlockPos> found = new ArrayList<>();
        while (!queue.isEmpty() && found.size() < VEIN_LIMIT) {
            BlockPos at = queue.poll();
            for (Direction d : Direction.values()) {
                BlockPos next = at.relative(d);
                if (!seen.add(next)) {
                    continue;
                }
                BlockState state = level.getBlockState(next);
                if (state.is(Tags.Blocks.ORES) && !isUnbreakable(level, next, state) && !tooHard(setup, state)) {
                    found.add(next);
                    queue.add(next);
                    if (found.size() >= VEIN_LIMIT) {
                        break;
                    }
                }
            }
        }
        return found;
    }
}
