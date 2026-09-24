package com.afjan.juicer.client;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

import com.afjan.juicer.Juicer;
import com.afjan.juicer.block.FruitLeavesBlock;
import com.afjan.juicer.block.FruitSaplingBlock;
import com.afjan.juicer.block.InfuserBlock;
import com.afjan.juicer.block.MixerBlock;
import com.afjan.juicer.block.entity.InfuserBlockEntity;
import com.afjan.juicer.block.entity.MixerBlockEntity;
import com.afjan.juicer.fruit.Fruit;
import com.afjan.juicer.registry.ModBlocks;
import com.afjan.juicer.registry.ModItems;

import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.FileUtil;
import net.minecraft.world.Difficulty;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * Development-only visual check, enabled with {@code -Djuicer.showcase=true} (the {@code runShowcase} Gradle task):
 * creates a flat creative world, builds a scene with every block of the mod, takes screenshots into
 * {@code run/screenshots/} and closes the game. Does nothing in a normal game.
 */
@EventBusSubscriber(modid = Juicer.MODID, value = Dist.CLIENT)
public final class DevShowcase {
    private static final boolean ENABLED = !FMLEnvironment.isProduction() && Boolean.getBoolean("juicer.showcase");

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
        view(0.5, 2.6, 5.0, 180F, 22F, "juicer_01_overview");
        view(-1.5, 0.8, 1.75, 180F, 12F, "juicer_02_mixer_front");
        view(-0.45, 1.35, 1.55, 135F, 32F, "juicer_03_mixer_angle");
        view(2.5, 1.35, 1.8, 180F, 30F, "juicer_04_infuser");
        view(3.55, 1.5, 1.45, 135F, 38F, "juicer_05_infuser_angle");
        view(0.5, 0.75, 1.6, 180F, 22F, "juicer_06_tubing");
        view(0.5, 3.2, -3.0, 180F, 12F, "juicer_07_leaves");
        view(0.5, 8.5, -10.0, 180F, 12F, "juicer_08_trees");
        STEPS.add(new Step(mc -> server(mc, server -> player(server).setGameMode(GameType.CREATIVE)), 10));
        STEPS.add(new Step(mc -> hideHud(mc, false), 1));
        view(0.5, 2.6, 5.0, 180F, 22F, "juicer_09_hud");
        STEPS.add(new Step(mc -> server(mc, server -> openMachine(server, origin.offset(-2, 0, 0))), 30));
        STEPS.add(new Step(mc -> shot(mc, "juicer_10_mixer_gui"), 5));
        STEPS.add(new Step(mc -> mc.player.closeContainer(), 20));
        STEPS.add(new Step(mc -> server(mc, server -> openMachine(server, origin.offset(2, 0, 0))), 30));
        STEPS.add(new Step(mc -> shot(mc, "juicer_11_infuser_gui"), 5));
        STEPS.add(new Step(mc -> mc.player.closeContainer(), 20));
        STEPS.add(new Step(mc -> server(mc, server -> player(server).setGameMode(GameType.SURVIVAL)), 20));
        STEPS.add(new Step(mc -> mc.gui.setScreen(new InventoryScreen(mc.player)), 30));
        STEPS.add(new Step(mc -> shot(mc, "juicer_12_effects"), 5));
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
            LevelSettings settings = new LevelSettings("Juicer Showcase", GameType.CREATIVE,
                    new LevelSettings.DifficultySettings(Difficulty.PEACEFUL, false, false), true, WorldDataConfiguration.DEFAULT);
            String folder = FileUtil.findAvailableName(mc.getLevelSource().getBaseDir(), "JuicerShowcase", "");
            mc.createWorldOpenFlows().createFreshLevel(folder, settings, new WorldOptions(20260921L, false, false),
                    WorldPresets::createTestWorldDimensions, mc.gui.screen());
        } catch (IOException e) {
            Juicer.LOGGER.error("Showcase: could not create world", e);
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

    private static void buildScene(ServerLevel level, ServerPlayer player) {
        var commands = level.getServer().getCommands();
        commands.performPrefixedCommand(level.getServer().createCommandSourceStack(), "time set noon");
        commands.performPrefixedCommand(level.getServer().createCommandSourceStack(), "weather clear");
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING, 0, 0);
        origin = new BlockPos(0, y, 0);

        // machines: mixer -> tubing -> infuser, fronts facing the camera (south)
        BlockPos mixerPos = origin.offset(-2, 0, 0);
        BlockPos infuserPos = origin.offset(2, 0, 0);
        level.setBlockAndUpdate(mixerPos, ModBlocks.MIXER.get().defaultBlockState().setValue(MixerBlock.FACING, Direction.SOUTH));
        level.setBlockAndUpdate(infuserPos, ModBlocks.INFUSER.get().defaultBlockState().setValue(InfuserBlock.FACING, Direction.SOUTH));
        BlockPos[] tubes = { origin.offset(-1, 0, 0), origin, origin.offset(1, 0, 0),
                origin.offset(0, 0, -1), origin.offset(0, 1, -1), origin.offset(0, 2, -1) };
        for (BlockPos tube : tubes) {
            level.setBlockAndUpdate(tube, ModBlocks.TUBING.get().defaultBlockState());
        }
        for (BlockPos tube : tubes) {
            level.setBlockAndUpdate(tube, ModBlocks.TUBING.get().withConnections(level.getBlockState(tube), level, tube));
        }
        // the infuser is full (and has no sugar), so the mixer keeps blending and its jar stays filled
        if (level.getBlockEntity(infuserPos) instanceof InfuserBlockEntity infuser) {
            infuser.receiveConcentrate(Fruit.ORANGE, InfuserBlockEntity.CAPACITY);
            infuser.setItem(InfuserBlockEntity.SLOT_BOTTLE, new ItemStack(Items.GLASS_BOTTLE, 16));
            infuser.setItem(InfuserBlockEntity.SLOT_OUTPUT, new ItemStack(Fruit.ORANGE.juiceItem(), 3));
        }
        if (level.getBlockEntity(mixerPos) instanceof MixerBlockEntity mixer) {
            mixer.setItem(0, new ItemStack(Fruit.ORANGE.fruitItem(), 40));
            mixer.getTank().fill(Fruit.ORANGE, 2400, false);
        }

        // a wall of leaves: one column per fruit, growth stages 0..3 from bottom to top, saplings in front
        Fruit[] fruits = Fruit.values();
        for (int i = 0; i < fruits.length; i++) {
            int x = -4 + i * 2;
            FruitLeavesBlock leaves = ModBlocks.LEAVES.get(fruits[i]).get();
            for (int age = 0; age <= FruitLeavesBlock.MAX_AGE; age++) {
                level.setBlockAndUpdate(origin.offset(x, age, -8), leaves.defaultBlockState()
                        .setValue(FruitLeavesBlock.AGE, age).setValue(LeavesBlock.PERSISTENT, true));
            }
            level.setBlockAndUpdate(origin.offset(x, 0, -7), ModBlocks.SAPLINGS.get(fruits[i]).get().defaultBlockState());
        }

        // five grown trees further back
        for (int i = 0; i < fruits.length; i++) {
            BlockPos pos = origin.offset(-12 + i * 6, 0, -24);
            FruitSaplingBlock sapling = ModBlocks.SAPLINGS.get(fruits[i]).get();
            level.setBlockAndUpdate(pos, sapling.defaultBlockState());
            sapling.advanceTree(level, pos, level.getBlockState(pos), level.getRandom());
            sapling.advanceTree(level, pos, level.getBlockState(pos), level.getRandom());
        }

        // hotbar with the mod's items, and every juice buff active for the HUD / inventory screenshots
        Inventory inventory = player.getInventory();
        inventory.clearContent();
        for (int i = 0; i < fruits.length; i++) {
            inventory.setItem(i, new ItemStack(ModItems.JUICES.get(fruits[i]).get()));
            inventory.setItem(9 + i, new ItemStack(fruits[i].fruitItem(), 8));
            inventory.setItem(18 + i, new ItemStack(ModItems.SAPLINGS.get(fruits[i]).get()));
            inventory.setItem(27 + i, new ItemStack(ModItems.LEAVES.get(fruits[i]).get()));
        }
        inventory.setItem(5, new ItemStack(ModItems.MIXER.get()));
        inventory.setItem(6, new ItemStack(ModItems.TUBING.get(), 64));
        inventory.setItem(7, new ItemStack(ModItems.INFUSER.get()));
        inventory.setItem(8, new ItemStack(Fruit.STARFRUIT.fruitItem(), 12));
        for (Fruit fruit : fruits) {
            new ItemStack(fruit.juiceItem()).finishUsingItem(level, player);
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

    private static void openMachine(MinecraftServer server, BlockPos pos) {
        ServerPlayer player = player(server);
        if (player.level().getBlockEntity(pos) instanceof MenuProvider provider) {
            player.openMenu(provider);
        }
    }

    private static void shot(Minecraft mc, String name) {
        Screenshot.grab(mc.gameDirectory, name + ".png", mc.gameRenderer.mainRenderTarget(), 1,
                message -> Juicer.LOGGER.info("Showcase screenshot {}: {}", name, message.getString()));
    }
}
