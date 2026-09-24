package com.afjan.arsenal.network;

import com.afjan.arsenal.Arsenal;
import com.afjan.arsenal.combat.Ballistics;
import com.afjan.arsenal.combat.RailCharge;
import com.afjan.arsenal.combat.Reloading;
import com.afjan.arsenal.vehicle.F14Entity;
import com.afjan.arsenal.vehicle.JetControl;

import java.util.List;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/**
 * Client to server: "I am holding the trigger" and "I pressed reload". Server to client: {@link ShotFx}, what a shot
 * looked like (muzzle flash and where each round went), drawn by {@code client.GunFeel}.
 *
 * <p>Firing is driven from the client rather than from {@code Item#use} so that automatic weapons keep firing while
 * the button is held without the vanilla item-use slowdown. The server re-checks the rate of fire and the magazine,
 * so a spammed packet still cannot shoot faster than the weapon allows.
 */
public final class ArsenalNetwork {
    public record Fire() implements CustomPacketPayload {
        public static final Fire INSTANCE = new Fire();
        public static final CustomPacketPayload.Type<Fire> TYPE = new CustomPacketPayload.Type<>(Arsenal.id("fire"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Fire> STREAM_CODEC = StreamCodec.unit(INSTANCE);

        @Override
        public CustomPacketPayload.Type<Fire> type() {
            return TYPE;
        }
    }

    public record Reload(boolean switchCaliber) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<Reload> TYPE = new CustomPacketPayload.Type<>(Arsenal.id("reload"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Reload> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.BOOL, Reload::switchCaliber, Reload::new);

        @Override
        public CustomPacketPayload.Type<Reload> type() {
            return TYPE;
        }
    }

    /**
     * One shot as everyone nearby sees it: the shooter's entity id, the tracer style ({@link #STYLE_BULLET} ...),
     * whether the muzzle flashed (suppressors hide it), the shot's power (1 for ordinary guns; a charged railgun
     * shot from 0.25 to 1.5, which sizes its beam) and where each round stopped.
     */
    public record ShotFx(int shooter, byte style, boolean flash, float power, List<Vec3> ends)
            implements CustomPacketPayload {
        public static final byte STYLE_BULLET = 0;
        public static final byte STYLE_HEAVY = 1;
        public static final byte STYLE_RAIL = 2;
        public static final byte STYLE_PELLET = 3;
        public static final int MAX_ENDS = 16;
        public static final CustomPacketPayload.Type<ShotFx> TYPE = new CustomPacketPayload.Type<>(Arsenal.id("shot_fx"));
        public static final StreamCodec<RegistryFriendlyByteBuf, ShotFx> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, ShotFx::shooter,
                ByteBufCodecs.BYTE, ShotFx::style,
                ByteBufCodecs.BOOL, ShotFx::flash,
                ByteBufCodecs.FLOAT, ShotFx::power,
                Vec3.STREAM_CODEC.apply(ByteBufCodecs.list(MAX_ENDS)), ShotFx::ends,
                ShotFx::new);

        @Override
        public CustomPacketPayload.Type<ShotFx> type() {
            return TYPE;
        }
    }

    /** Client to server: the trigger went down on a charged weapon ({@code start}), or the charge was abandoned. */
    public record Charge(boolean start) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<Charge> TYPE = new CustomPacketPayload.Type<>(Arsenal.id("charge"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Charge> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.BOOL, Charge::start, Charge::new);

        @Override
        public CustomPacketPayload.Type<Charge> type() {
            return TYPE;
        }
    }

    private ArsenalNetwork() {}

    public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("3")
                .playToServer(JetControl.TYPE, JetControl.STREAM_CODEC, (payload, context) -> {
                    if (context.player() instanceof ServerPlayer player && player.getVehicle() instanceof F14Entity jet
                            && jet.getControllingPassenger() == player) {
                        jet.applyControl(player, payload);
                    }
                })
                .playToServer(Fire.TYPE, Fire.STREAM_CODEC, (payload, context) -> {
                    if (context.player() instanceof ServerPlayer player) {
                        Ballistics.fire(player, InteractionHand.MAIN_HAND);
                    }
                })
                .playToServer(Charge.TYPE, Charge.STREAM_CODEC, (payload, context) -> {
                    if (context.player() instanceof ServerPlayer player) {
                        if (payload.start()) {
                            RailCharge.begin(player);
                        } else {
                            RailCharge.cancel(player);
                        }
                    }
                })
                .playToServer(Reload.TYPE, Reload.STREAM_CODEC, (payload, context) -> {
                    if (context.player() instanceof ServerPlayer player) {
                        Reloading.start(player, InteractionHand.MAIN_HAND, payload.switchCaliber());
                    }
                })
                // handled in client.GunFeel (RegisterClientPayloadHandlersEvent)
                .playToClient(ShotFx.TYPE, ShotFx.STREAM_CODEC);
    }
}
