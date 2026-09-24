package com.afjan.arsenal.vehicle;

import com.afjan.arsenal.Arsenal;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Client to server, every tick while flying: how the pilot's client flew the jet (orientation, velocity, stick,
 * throttle) and what the pilot is pressing. The position itself travels in the vanilla vehicle-move packet.
 * Button presses are counters, not booleans, so each press is handled exactly once even if a packet is lost.
 *
 * @param flags  F14Entity flag bits: GEAR, AIRBRAKE, CANOPY, GUN (trigger held), BRAKES, ON_GROUND
 * @param target the entity the seeker has locked (-1: none)
 */
public record JetControl(float pitch, float roll, float yaw, float throttle, int flags, float qx, float qy, float qz,
        float qw, float vx, float vy, float vz, int fireSeq, int cycleSeq, int flareSeq, int target, boolean crashed)
        implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<JetControl> TYPE = new CustomPacketPayload.Type<>(Arsenal.id("jet_control"));
    public static final StreamCodec<RegistryFriendlyByteBuf, JetControl> STREAM_CODEC =
            StreamCodec.of((buf, c) -> c.write(buf), JetControl::read);

    private void write(RegistryFriendlyByteBuf buf) {
        buf.writeFloat(this.pitch);
        buf.writeFloat(this.roll);
        buf.writeFloat(this.yaw);
        buf.writeFloat(this.throttle);
        buf.writeVarInt(this.flags);
        buf.writeFloat(this.qx);
        buf.writeFloat(this.qy);
        buf.writeFloat(this.qz);
        buf.writeFloat(this.qw);
        buf.writeFloat(this.vx);
        buf.writeFloat(this.vy);
        buf.writeFloat(this.vz);
        buf.writeByte(this.fireSeq);
        buf.writeByte(this.cycleSeq);
        buf.writeByte(this.flareSeq);
        buf.writeVarInt(this.target + 1);
        buf.writeBoolean(this.crashed);
    }

    private static JetControl read(RegistryFriendlyByteBuf buf) {
        return new JetControl(buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readVarInt(),
                buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat(),
                buf.readFloat(), buf.readUnsignedByte(), buf.readUnsignedByte(), buf.readUnsignedByte(),
                buf.readVarInt() - 1, buf.readBoolean());
    }

    @Override
    public CustomPacketPayload.Type<JetControl> type() {
        return TYPE;
    }
}
