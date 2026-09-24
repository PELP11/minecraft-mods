package com.afjan.drillworks.registry;

import com.afjan.drillworks.Drillworks;
import com.afjan.drillworks.entity.MiningDrillEntity;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModEntities {
    public static final DeferredRegister.Entities ENTITIES = DeferredRegister.createEntities(Drillworks.MODID);

    /** 2.2 wide so it drives through its own 3-wide tunnel with room to spare; the model is about 2.7 x 3. */
    public static final DeferredHolder<EntityType<?>, EntityType<MiningDrillEntity>> MINING_DRILL =
            ENTITIES.registerEntityType("mining_drill", MiningDrillEntity::new, MobCategory.MISC,
                    builder -> builder.noLootTable().sized(2.2F, 1.9F).clientTrackingRange(10).updateInterval(1)
                            .fireImmune());

    private ModEntities() {}
}
