package com.afjan.hatchery.loot;

import com.afjan.hatchery.registry.ModTags;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.neoforged.neoforge.common.loot.LootModifier;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Adds the dying mob's own spawn egg to its death loot. How rare that is lives in the JSON conditions
 * ({@code data/hatchery/loot_modifiers/spawn_eggs.json}: killed by a player, 0.5%, a little more with Looting),
 * so a datapack can change it.
 */
public final class SpawnEggModifier extends LootModifier {
    public static final MapCodec<SpawnEggModifier> CODEC = RecordCodecBuilder.mapCodec(
            inst -> codecStart(inst).apply(inst, SpawnEggModifier::new));

    /** Mob type -> its egg (looking an egg up scans every item). */
    private static final Map<EntityType<?>, Optional<Holder<Item>>> EGGS = new ConcurrentHashMap<>();

    public SpawnEggModifier(LootItemCondition[] conditions) {
        super(conditions);
    }

    @Override
    public MapCodec<? extends SpawnEggModifier> codec() {
        return CODEC;
    }

    @Override
    protected ObjectArrayList<ItemStack> doApply(ObjectArrayList<ItemStack> loot, LootContext context) {
        Entity entity = context.getOptionalParameter(LootContextParams.THIS_ENTITY);
        if (!(entity instanceof LivingEntity) || entity.getType().is(ModTags.NO_SPAWN_EGG)) return loot;
        // Only the mob's own death loot (not shearing, bartering, gifts ... which also name an entity).
        var id = context.getQueriedLootTableId();
        if (id == null || !entity.getLootTable().map(key -> key.location().equals(id)).orElse(false)) return loot;
        eggOf(entity.getType()).ifPresent(egg -> loot.add(new ItemStack(egg)));
        return loot;
    }

    public static Optional<Holder<Item>> eggOf(EntityType<?> type) {
        return EGGS.computeIfAbsent(type, t -> Optional.ofNullable(SpawnEggItem.byId(t)).map(egg -> (Holder<Item>) egg.builtInRegistryHolder()));
    }
}
