package com.afjan.tempered.gametest;

import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A real, connected survival player (Forge 66 has no FakePlayer): ServerPlayerGameMode#destroyBlock, chat
 * messages and inventory syncing all work, so tests go through the same code paths as the game.
 */
final class TestPlayers {
    private static final AtomicInteger COUNT = new AtomicInteger();

    private TestPlayers() {
    }

    /** Stands at the given relative position, facing +z (yaw 0), holding the stack. */
    static ServerPlayer survival(GameTestHelper helper, BlockPos feet, ItemStack held) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        CommonListenerCookie cookie = CommonListenerCookie.createInitial(
                new GameProfile(UUID.randomUUID(), "tempered" + COUNT.incrementAndGet()), false);
        ServerPlayer player = new ServerPlayer(server, level, cookie.gameProfile(), cookie.clientInformation());
        Connection connection = new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(connection);
        server.getPlayerList().placeNewPlayer(connection, player, cookie);
        player.setGameMode(GameType.SURVIVAL);
        Vec3 pos = Vec3.atBottomCenterOf(helper.absolutePos(feet));
        player.snapTo(pos.x, pos.y, pos.z, 0.0F, 0.0F);
        player.setItemInHand(InteractionHand.MAIN_HAND, held);
        helper.addCleanup(passed -> server.getPlayerList().remove(player));
        return player;
    }
}
