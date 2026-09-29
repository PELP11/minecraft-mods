package com.afjan.tempered.loot;

import com.afjan.tempered.ability.Replanter;
import com.afjan.tempered.event.PlacedBlocks;
import com.afjan.tempered.event.ProgressEvents;
import com.afjan.tempered.mastery.Kind;
import com.afjan.tempered.mastery.Mastery;
import com.afjan.tempered.mastery.Perk;
import com.afjan.tempered.mastery.Track;
import com.afjan.tempered.mastery.Tracks;
import com.afjan.tempered.registry.ModTags;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemInstance;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.loot.LootModifier;

/**
 * Bonus drops from mastered tools: double ores / logs / crops / wool / catches (Yield), finds while digging
 * (Treasure), and the seed that Replant puts back into the ground.
 */
public final class MasteryLootModifier extends LootModifier {
    public static final MapCodec<MasteryLootModifier> CODEC = RecordCodecBuilder.mapCodec(
            inst -> codecStart(inst).apply(inst, MasteryLootModifier::new));

    /** Digging finds, weighted. */
    private static final Object[] TREASURE = {
            Items.FLINT, 20, Items.CLAY_BALL, 14, Items.BONE, 12, Items.GOLD_NUGGET, 12, Items.IRON_NUGGET, 10,
            Items.COAL, 8, Items.STRING, 6, Items.FEATHER, 5, Items.ARROW, 4, Items.AMETHYST_SHARD, 3,
            Items.EMERALD, 2, Items.EXPERIENCE_BOTTLE, 2, Items.DIAMOND, 1, Items.NAME_TAG, 1};

    public MasteryLootModifier(LootItemCondition[] conditions) {
        super(conditions);
    }

    @Override
    public MapCodec<? extends MasteryLootModifier> codec() {
        return CODEC;
    }

    @Override
    protected ObjectArrayList<ItemStack> doApply(LootTable table, ObjectArrayList<ItemStack> loot, LootContext context) {
        ItemInstance toolParam = context.getOptional(LootContextParams.TOOL);
        if (!(toolParam instanceof ItemStack tool)) return loot;
        Track track = Tracks.get(tool);
        if (track == null) return loot;
        RandomSource random = context.getRandom();
        BlockState state = context.getOptional(LootContextParams.BLOCK_STATE);
        if (state != null) {
            Vec3 origin = context.getOptional(LootContextParams.ORIGIN);
            BlockPos pos = origin == null ? null : BlockPos.containing(origin);
            // Blocks a player placed give no bonus at all (no place-and-break loops); crops are never "placed".
            if (pos != null && PlacedBlocks.wasJustBroken(context.getLevel(), pos)) return loot;
            switch (track.kind) {
                case PICKAXE -> {
                    if (state.is(ModTags.ORES)) doubleUp(loot, tool, random);
                }
                case AXE -> {
                    if (state.is(BlockTags.LOGS)) doubleUp(loot, tool, random);
                }
                case HOE -> {
                    if (ProgressEvents.isMatureCrop(state)) {
                        doubleUp(loot, tool, random);
                        if (pos != null && Mastery.perk(tool, Perk.REPLANT) > 0) replant(loot, context.getLevel(), pos, state);
                    }
                }
                case SHEARS -> doubleUp(loot, tool, random);
                case SHOVEL -> {
                    double chance = Mastery.perk(tool, Perk.TREASURE_HUNTER);
                    if (chance > 0 && ProgressEvents.isEffective(tool, state) && random.nextDouble() * 100 < chance) {
                        loot.add(treasure(random));
                    }
                }
                default -> {
                }
            }
        } else {
            Identifier id = context.getQueriedLootTableId();
            String path = id == null ? "" : id.getPath();
            if (track.kind == Kind.SHEARS && path.startsWith("shearing/")) doubleUp(loot, tool, random);
            if (track.kind == Kind.FISHING_ROD && path.equals("gameplay/fishing")) doubleUp(loot, tool, random);
        }
        return loot;
    }

    /** Yield: each drop has the perk's chance to come twice. */
    private static void doubleUp(ObjectArrayList<ItemStack> loot, ItemStack tool, RandomSource random) {
        double chance = Mastery.perk(tool, Perk.YIELD);
        if (chance <= 0) return;
        int size = loot.size();
        for (int i = 0; i < size; i++) {
            ItemStack drop = loot.get(i);
            if (!drop.isEmpty() && random.nextDouble() * 100 < chance) loot.add(drop.copy());
        }
    }

    private static void replant(ObjectArrayList<ItemStack> loot, ServerLevel level, BlockPos pos, BlockState state) {
        Item seed = state.getBlock().asItem();
        if (seed == Items.AIR) return;
        for (ItemStack drop : loot) {
            if (drop.is(seed)) {
                drop.shrink(1);
                Replanter.queue(level, pos, state);
                break;
            }
        }
        loot.removeIf(ItemStack::isEmpty);
    }

    private static ItemStack treasure(RandomSource random) {
        int total = 0;
        for (int i = 1; i < TREASURE.length; i += 2) total += (Integer) TREASURE[i];
        int roll = random.nextInt(total);
        for (int i = 0; i < TREASURE.length; i += 2) {
            roll -= (Integer) TREASURE[i + 1];
            if (roll < 0) return new ItemStack((Item) TREASURE[i]);
        }
        return new ItemStack(Items.FLINT);
    }
}
