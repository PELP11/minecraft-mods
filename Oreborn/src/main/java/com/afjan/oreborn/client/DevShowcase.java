package com.afjan.oreborn.client;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

import com.afjan.oreborn.Oreborn;
import com.afjan.oreborn.ability.OreRadar;
import com.afjan.oreborn.block.CrustedLavaBlock;
import com.afjan.oreborn.material.GearType;
import com.afjan.oreborn.material.OreMaterial;
import com.afjan.oreborn.registry.ModBlocks;
import com.afjan.oreborn.registry.ModItems;

import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.FileUtil;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * Development-only visual check, enabled with {@code -Doreborn.showcase=true} (the {@code runShowcase} Gradle task):
 * creates a flat creative world, builds a scene with every ore, block and armour set, takes screenshots into
 * {@code run/screenshots/} and closes the game. Does nothing in a normal game.
 */
@EventBusSubscriber(modid = Oreborn.MODID, value = Dist.CLIENT)
public final class DevShowcase {
    private static final boolean ENABLED = !FMLEnvironment.isProduction() && Boolean.getBoolean("oreborn.showcase");

    /** One step of the tour: an action followed by a number of ticks to wait. */
    private record Step(Consumer<Minecraft> action, int waitTicks) {}

    private static final List<Step> STEPS = new ArrayList<>();
    private static int ticks;
    private static int stepIndex;
    private static int wait;
    private static boolean worldRequested;
    private static BlockPos origin = BlockPos.ZERO;
    private static boolean lockCamera;
    private static float cameraYaw;
    private static float cameraPitch;

    private DevShowcase() {}

    private static void buildTour() {
        STEPS.add(new Step(mc -> {}, 60)); // let the world settle
        STEPS.add(new Step(mc -> server(mc, server -> buildScene(server.overworld(), player(server))), 120));
        STEPS.add(new Step(mc -> hideHud(mc, true), 1));
        STEPS.add(new Step(mc -> server(mc, server -> player(server).setGameMode(GameType.SPECTATOR)), 10));
        // camera positions are eye positions relative to the scene origin (yaw 180 = looking north)
        view(0.5, 3.2, 4.0, 180F, 18F, "oreborn_01_overview");
        view(0.5, 1.6, 0.6, 180F, 8F, "oreborn_02_armor");
        view(0.5, 2.0, -4.5, 180F, 4F, "oreborn_03_blocks");
        view(10.5, 2.2, -1.0, 180F, 40F, "oreborn_04_crusted_lava");
        // Ore Radar: the pulse outlines the ores hidden inside the stone block
        STEPS.add(new Step(mc -> look(mc, -11.5, 2.2, 0.5, 180F, 12F), 40));
        STEPS.add(new Step(mc -> server(mc, server -> OreRadar.ping(server.overworld(), player(server))), 15));
        STEPS.add(new Step(mc -> shot(mc, "oreborn_05_ore_radar"), 3));
        STEPS.add(new Step(mc -> server(mc, server -> player(server).setGameMode(GameType.CREATIVE)), 10));
        STEPS.add(new Step(mc -> hideHud(mc, false), 1));
        view(0.5, 1.7, 2.5, 180F, 10F, "oreborn_06_in_hand");
        STEPS.add(new Step(mc -> server(mc, server -> player(server).setGameMode(GameType.SURVIVAL)), 20));
        STEPS.add(new Step(mc -> mc.gui.setScreen(new InventoryScreen(mc.player)), 30));
        STEPS.add(new Step(mc -> shot(mc, "oreborn_07_inventory"), 5));
        STEPS.add(new Step(mc -> mc.gui.setScreen(null), 40));
        STEPS.add(new Step(Minecraft::stop, 1000));
    }

    /** Teleports the camera, waits for chunks to redraw and takes a screenshot. */
    private static void view(double dx, double dy, double dz, float yaw, float pitch, String name) {
        STEPS.add(new Step(mc -> look(mc, dx, dy, dz, yaw, pitch), 40));
        STEPS.add(new Step(mc -> shot(mc, name), 3));
    }

    @SubscribeEvent
    static void onClientTick(ClientTickEvent.Post event) {
        if (!ENABLED) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        ticks++;
        if (mc.level == null || mc.player == null) {
            if (!worldRequested && mc.gui.overlay() == null && ticks > 40) {
                worldRequested = true;
                buildTour();
                createWorld(mc);
            }
            return;
        }
        if (lockCamera) {
            // keep the view steady even if the mouse moves over the window
            mc.player.setYRot(cameraYaw);
            mc.player.setXRot(cameraPitch);
            mc.player.yRotO = cameraYaw;
            mc.player.xRotO = cameraPitch;
        }
        if (wait > 0) {
            wait--;
            return;
        }
        if (stepIndex < STEPS.size() && mc.getSingleplayerServer() != null) {
            Step step = STEPS.get(stepIndex++);
            step.action().accept(mc);
            wait = step.waitTicks();
        }
    }

    private static void createWorld(Minecraft mc) {
        try {
            LevelSettings settings = new LevelSettings("Oreborn Showcase", GameType.CREATIVE,
                    new LevelSettings.DifficultySettings(Difficulty.PEACEFUL, false, false), true, WorldDataConfiguration.DEFAULT);
            String folder = FileUtil.findAvailableName(mc.getLevelSource().getBaseDir(), "OrebornShowcase", "");
            mc.createWorldOpenFlows().createFreshLevel(folder, settings, new WorldOptions(20260921L, false, false),
                    WorldPresets::createTestWorldDimensions, mc.gui.screen());
        } catch (IOException e) {
            Oreborn.LOGGER.error("Showcase: could not create world", e);
        }
    }

    private static void server(Minecraft mc, Consumer<MinecraftServer> action) {
        MinecraftServer server = mc.getSingleplayerServer();
        if (server != null) {
            server.execute(() -> action.accept(server));
        }
    }

    private static ServerPlayer player(MinecraftServer server) {
        return server.getPlayerList().getPlayers().getFirst();
    }

    private static void hideHud(Minecraft mc, boolean hidden) {
        if (mc.gui.hud.isHidden() != hidden) {
            mc.gui.hud.toggle();
        }
    }

    private static ItemStack gear(OreMaterial material, GearType type) {
        return new ItemStack(ModItems.gear(material, type));
    }

    private static void buildScene(ServerLevel level, ServerPlayer player) {
        var commands = level.getServer().getCommands();
        commands.performPrefixedCommand(level.getServer().createCommandSourceStack(), "time set noon");
        commands.performPrefixedCommand(level.getServer().createCommandSourceStack(), "weather clear");
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING, 0, 0);
        origin = new BlockPos(0, y, 0);

        // an armour stand per material, wearing the full set and holding the sword and pickaxe
        OreMaterial[] materials = OreMaterial.values();
        for (int i = 0; i < materials.length; i++) {
            ArmorStand stand = EntityTypes.ARMOR_STAND.create(level, EntitySpawnReason.COMMAND);
            if (stand == null) {
                continue;
            }
            BlockPos at = origin.offset(-3 + i * 2, 0, -4);
            stand.snapTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0.0F, 0.0F);
            stand.setShowArms(true);
            stand.setNoBasePlate(true);
            for (GearType piece : GearType.ARMOR) {
                stand.setItemSlot(piece.armorType().getSlot(), gear(materials[i], piece));
            }
            stand.setItemSlot(EquipmentSlot.MAINHAND, gear(materials[i], GearType.SWORD));
            stand.setItemSlot(EquipmentSlot.OFFHAND, gear(materials[i], GearType.PICKAXE));
            level.addFreshEntity(stand);
        }

        // every ore and storage block on two shelves behind them
        List<Block> ores = new ArrayList<>();
        List<Block> storage = new ArrayList<>();
        for (OreMaterial m : materials) {
            ores.add(ModBlocks.ORES.get(m).get());
            if (ModBlocks.DEEPSLATE_ORES.containsKey(m)) {
                ores.add(ModBlocks.DEEPSLATE_ORES.get(m).get());
            }
            storage.add(ModBlocks.STORAGE.get(m).get());
        }
        for (int i = 0; i < ores.size(); i++) {
            level.setBlockAndUpdate(origin.offset(-3 + i, 1, -8), ores.get(i).defaultBlockState());
        }
        for (int i = 0; i < storage.size(); i++) {
            level.setBlockAndUpdate(origin.offset(-2 + i + (i >= 2 ? 1 : 0), 0, -8), storage.get(i).defaultBlockState());
        }

        // a lava pool with crusts of every age (no walker around: they keep their look since no melt is scheduled)
        for (BlockPos p : BlockPos.betweenClosed(origin.offset(8, -2, -6), origin.offset(13, -2, -3))) {
            level.setBlockAndUpdate(p, Blocks.STONE.defaultBlockState());
        }
        for (BlockPos p : BlockPos.betweenClosed(origin.offset(8, -1, -6), origin.offset(13, -1, -3))) {
            level.setBlockAndUpdate(p, Blocks.LAVA.defaultBlockState());
        }
        for (int age = 0; age <= CrustedLavaBlock.MAX_AGE; age++) {
            level.setBlockAndUpdate(origin.offset(9 + age, -1, -4), ModBlocks.CRUSTED_LAVA.get().defaultBlockState().setValue(CrustedLavaBlock.AGE, age));
            level.setBlockAndUpdate(origin.offset(9 + age, -1, -5), ModBlocks.CRUSTED_LAVA.get().defaultBlockState().setValue(CrustedLavaBlock.AGE, age));
        }

        // a solid stone block with ores hidden inside, for the Ore Radar
        for (BlockPos p : BlockPos.betweenClosed(origin.offset(-15, 0, -7), origin.offset(-8, 5, -3))) {
            level.setBlockAndUpdate(p, Blocks.STONE.defaultBlockState());
        }
        level.setBlockAndUpdate(origin.offset(-13, 1, -5), Blocks.DIAMOND_ORE.defaultBlockState());
        level.setBlockAndUpdate(origin.offset(-10, 3, -5), Blocks.GOLD_ORE.defaultBlockState());
        level.setBlockAndUpdate(origin.offset(-12, 4, -6), Blocks.REDSTONE_ORE.defaultBlockState());
        level.setBlockAndUpdate(origin.offset(-11, 2, -4), ModBlocks.ORES.get(OreMaterial.CRYOLITE).get().defaultBlockState());
        level.setBlockAndUpdate(origin.offset(-9, 1, -6), ModBlocks.ORES.get(OreMaterial.FULGURITE).get().defaultBlockState());
        level.setBlockAndUpdate(origin.offset(-14, 3, -4), ModBlocks.ORES.get(OreMaterial.UMBRIUM).get().defaultBlockState());
        level.setBlockAndUpdate(origin.offset(-12, 2, -5), ModBlocks.ORES.get(OreMaterial.EMBERITE).get().defaultBlockState());

        // hotbar and inventory: all the gear (the Emberite set is worn for the inventory screenshot)
        Inventory inventory = player.getInventory();
        inventory.clearContent();
        List<ItemStack> items = new ArrayList<>();
        items.add(gear(OreMaterial.UMBRIUM, GearType.PICKAXE));
        items.add(gear(OreMaterial.CRYOLITE, GearType.SWORD));
        items.add(gear(OreMaterial.FULGURITE, GearType.AXE));
        items.add(gear(OreMaterial.EMBERITE, GearType.SHOVEL));
        for (OreMaterial m : materials) {
            items.add(new ItemStack(ModItems.MAIN.get(m).get(), 16));
        }
        items.add(new ItemStack(ModItems.DROPS.get(OreMaterial.CRYOLITE).get(), 8));
        for (OreMaterial m : materials) {
            for (GearType type : GearType.values()) {
                boolean alreadyIn = m == OreMaterial.UMBRIUM && type == GearType.PICKAXE || m == OreMaterial.CRYOLITE && type == GearType.SWORD
                        || m == OreMaterial.FULGURITE && type == GearType.AXE || m == OreMaterial.EMBERITE && type == GearType.SHOVEL;
                if (!alreadyIn && !(m == OreMaterial.EMBERITE && type.isArmor())) {
                    items.add(gear(m, type));
                }
            }
        }
        for (int slot = 0; slot < Math.min(36, items.size()); slot++) {
            inventory.setItem(slot, items.get(slot));
        }
        inventory.setSelectedSlot(0);
        for (GearType piece : GearType.ARMOR) {
            player.setItemSlot(piece.armorType().getSlot(), gear(OreMaterial.EMBERITE, piece));
        }
    }

    private static void look(Minecraft mc, double dx, double dy, double dz, float yaw, float pitch) {
        double x = origin.getX() + dx;
        double y = origin.getY() + dy - 1.62;
        double z = origin.getZ() + dz;
        cameraYaw = yaw;
        cameraPitch = pitch;
        lockCamera = true;
        server(mc, server -> {
            ServerPlayer player = player(server);
            player.getAbilities().flying = true;
            player.onUpdateAbilities();
            player.teleportTo(player.level(), x, y, z, Set.of(), yaw, pitch, true);
        });
    }

    private static void shot(Minecraft mc, String name) {
        Screenshot.grab(mc.gameDirectory, name + ".png", mc.gameRenderer.mainRenderTarget(), 1,
                message -> Oreborn.LOGGER.info("Showcase screenshot {}: {}", name, message.getString()));
    }
}
