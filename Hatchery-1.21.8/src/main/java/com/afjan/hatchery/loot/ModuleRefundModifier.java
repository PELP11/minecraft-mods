package com.afjan.hatchery.loot;

import com.afjan.hatchery.registry.ModBlocks;
import com.afjan.hatchery.spawner.Module;
import com.afjan.hatchery.spawner.SpawnerModules;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.SpawnerBlockEntity;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.neoforged.neoforge.common.loot.LootModifier;

/** A mined spawner gives back every module in it (all of them, so upgrades can move with the spawner). */
public final class ModuleRefundModifier extends LootModifier {
    public static final MapCodec<ModuleRefundModifier> CODEC = RecordCodecBuilder.mapCodec(
            inst -> codecStart(inst).apply(inst, ModuleRefundModifier::new));

    public ModuleRefundModifier(LootItemCondition[] conditions) {
        super(conditions);
    }

    @Override
    public MapCodec<? extends ModuleRefundModifier> codec() {
        return CODEC;
    }

    @Override
    protected ObjectArrayList<ItemStack> doApply(ObjectArrayList<ItemStack> loot, LootContext context) {
        if (!(context.getOptionalParameter(LootContextParams.BLOCK_ENTITY) instanceof SpawnerBlockEntity spawner)) return loot;
        SpawnerModules modules = SpawnerModules.of(spawner);
        for (Module module : Module.values()) {
            Item item = ModBlocks.MODULES.get(module).get();
            for (int left = module.totalCost(module.level(modules)); left > 0; left -= 64) {
                loot.add(new ItemStack(item, Math.min(64, left)));
            }
        }
        return loot;
    }
}
