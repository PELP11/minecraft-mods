package com.afjan.arsenal.registry;

import com.afjan.arsenal.Arsenal;
import com.afjan.arsenal.entity.OrdnanceEntity;
import com.afjan.arsenal.vehicle.F14Entity;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModEntities {
    public static final DeferredRegister.Entities ENTITIES = DeferredRegister.createEntities(Arsenal.MODID);

    public static final DeferredHolder<EntityType<?>, EntityType<OrdnanceEntity>> ORDNANCE =
            ENTITIES.registerEntityType("ordnance", OrdnanceEntity::new, MobCategory.MISC,
                    builder -> builder.noLootTable().sized(0.3F, 0.3F).clientTrackingRange(10).updateInterval(1));

    /** 1:1 F-14: the box is only the fuselage centre; the multipart hitboxes cover the rest of the airframe. */
    public static final DeferredHolder<EntityType<?>, EntityType<F14Entity>> F14 =
            ENTITIES.registerEntityType("f14_tomcat", F14Entity::new, MobCategory.MISC,
                    builder -> builder.noLootTable().sized(4.0F, 1.9F).clientTrackingRange(24).updateInterval(1)
                            .fireImmune());

    private ModEntities() {}
}
