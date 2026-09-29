package com.afjan.hatchery.event;

import com.afjan.hatchery.spawner.Messages;
import com.afjan.hatchery.spawner.Module;
import com.afjan.hatchery.spawner.SpawnerModules;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.BaseSpawner;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.SpawnerBlockEntity;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.MobSpawnEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.listener.Priority;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Frailty and Redstone act on each mob a spawner makes: FinalizeSpawn (which knows the spawner) marks it, and when
 * the mob joins the world (after finalizeSpawn, which can reset health) it is weakened or turned away. A spawner
 * turned away this way just waits for its next wave. An empty hand on a spawner shows its modules.
 */
public final class SpawnerEvents {
    private static final Set<Mob> BLOCKED = Collections.newSetFromMap(new WeakHashMap<>());
    private static final Map<Mob, SpawnerModules> FRAIL = new WeakHashMap<>();

    private SpawnerEvents() {
    }

    public static void register() {
        MobSpawnEvent.FinalizeSpawn.BUS.addListener(Priority.LOWEST, e -> {
            onFinalize(e);
            return false;
        });
        EntityJoinLevelEvent.BUS.addListener(Priority.HIGH, SpawnerEvents::onJoin);
        PlayerInteractEvent.RightClickBlock.BUS.addListener(e -> {
            onInspect(e);
            return false;
        });
    }

    static void onFinalize(MobSpawnEvent.FinalizeSpawn event) {
        BaseSpawner spawner = event.getSpawner();
        BlockEntity entity = spawner == null ? null : spawner.getSpawnerBlockEntity();
        if (entity == null || entity.getLevel() == null) return;
        SpawnerModules modules = SpawnerModules.of(entity);
        if (modules.redstone() && entity.getLevel().hasNeighborSignal(entity.getBlockPos())) {
            BLOCKED.add(event.getEntity());
        } else if (modules.frailty() > 0) {
            FRAIL.put(event.getEntity(), modules);
        }
    }

    private static boolean onJoin(EntityJoinLevelEvent event) {
        if (!(event.getEntity() instanceof Mob mob) || event.getLevel().isClientSide()) return false;
        if (BLOCKED.remove(mob)) return true;
        SpawnerModules modules = FRAIL.remove(mob);
        if (modules != null) mob.setHealth(modules.frailHealth(mob.getMaxHealth()));
        return false;
    }

    private static void onInspect(PlayerInteractEvent.RightClickBlock event) {
        if (event.getLevel().isClientSide() || event.getHand() != InteractionHand.MAIN_HAND || !event.getEntity().getMainHandItem().isEmpty()) return;
        if (!(event.getLevel().getBlockEntity(event.getPos()) instanceof SpawnerBlockEntity spawner)) return;
        SpawnerModules modules = SpawnerModules.of(spawner);
        MutableComponent list = Component.empty();
        for (Module module : Module.values()) {
            if (module.ordinal() > 0) list.append("  ");
            int level = module.level(modules);
            list.append(Component.translatable(module.translationKey()).withStyle(level > 0 ? ChatFormatting.GREEN : ChatFormatting.GRAY))
                    .append(Component.literal(module.maxLevel > 1 ? " " + level + "/" + module.maxLevel : level > 0 ? " ✔" : " ✘")
                            .withStyle(level > 0 ? ChatFormatting.WHITE : ChatFormatting.DARK_GRAY));
        }
        event.getEntity().sendOverlayMessage(Component.translatable(Messages.OVERVIEW, list));
    }

    /** Test hook: whether this mob is waiting to be turned away. */
    public static boolean isBlocked(Mob mob) {
        return BLOCKED.contains(mob);
    }
}
