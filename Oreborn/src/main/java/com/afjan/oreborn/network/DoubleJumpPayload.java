package com.afjan.oreborn.network;

import com.afjan.oreborn.Oreborn;
import com.afjan.oreborn.ability.ArmorSets;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/** Client -> server: "I just double-jumped with Fulgurite boots" (the client moves itself; the server resets the fall). */
public record DoubleJumpPayload() implements CustomPacketPayload {
    public static final DoubleJumpPayload INSTANCE = new DoubleJumpPayload();
    public static final CustomPacketPayload.Type<DoubleJumpPayload> TYPE = new CustomPacketPayload.Type<>(Oreborn.id("double_jump"));
    public static final StreamCodec<RegistryFriendlyByteBuf, DoubleJumpPayload> STREAM_CODEC = StreamCodec.unit(INSTANCE);

    @Override
    public CustomPacketPayload.Type<DoubleJumpPayload> type() {
        return TYPE;
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToServer(TYPE, STREAM_CODEC, (payload, context) -> {
            if (context.player() instanceof ServerPlayer player) {
                ArmorSets.onDoubleJump(player);
            }
        });
    }
}
