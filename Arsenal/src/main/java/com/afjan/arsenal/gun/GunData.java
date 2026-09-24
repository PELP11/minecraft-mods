package com.afjan.arsenal.gun;

import java.util.ArrayList;
import java.util.List;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * What a particular gun in a particular stack is carrying: rounds left, which cartridge is in it and which parts are
 * bolted on (one bit per {@link Attachment}). Everything is a plain int so the codec and the network codec stay
 * trivial and old saves keep working.
 */
public record GunData(int ammo, int caliberId, int mask) {
    public static final GunData EMPTY = new GunData(0, 0, 0);

    public static final Codec<GunData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.optionalFieldOf("ammo", 0).forGetter(GunData::ammo),
            Codec.INT.optionalFieldOf("caliber", 0).forGetter(GunData::caliberId),
            Codec.INT.optionalFieldOf("attachments", 0).forGetter(GunData::mask))
            .apply(instance, GunData::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, GunData> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, GunData::ammo,
            ByteBufCodecs.VAR_INT, GunData::caliberId,
            ByteBufCodecs.VAR_INT, GunData::mask,
            GunData::new);

    public Caliber caliber() {
        return Caliber.byIndex(this.caliberId);
    }

    public GunData withAmmo(int rounds) {
        return new GunData(Math.max(0, rounds), this.caliberId, this.mask);
    }

    public GunData loaded(Caliber loaded, int rounds) {
        return new GunData(Math.max(0, rounds), loaded.ordinal(), this.mask);
    }

    public boolean has(Attachment attachment) {
        return (this.mask & attachment.bit()) != 0;
    }

    /** The parts currently fitted, at most one per slot. */
    public List<Attachment> attachments() {
        if (this.mask == 0) {
            return List.of();
        }
        List<Attachment> fitted = new ArrayList<>(4);
        for (Attachment attachment : Attachment.values()) {
            if (this.has(attachment)) {
                fitted.add(attachment);
            }
        }
        return fitted;
    }

    public Attachment inSlot(Attachment.Slot slot) {
        for (Attachment attachment : Attachment.values()) {
            if (attachment.slot() == slot && this.has(attachment)) {
                return attachment;
            }
        }
        return null;
    }

    /** Fits a part, throwing out whatever occupied that slot before. */
    public GunData with(Attachment attachment) {
        int mask = this.mask;
        Attachment previous = this.inSlot(attachment.slot());
        if (previous != null) {
            mask &= ~previous.bit();
        }
        return new GunData(this.ammo, this.caliberId, mask | attachment.bit());
    }

    public GunData without(Attachment attachment) {
        return new GunData(this.ammo, this.caliberId, this.mask & ~attachment.bit());
    }

    public GunData withNoAttachments() {
        return new GunData(this.ammo, this.caliberId, 0);
    }

    /** Builds a mask out of the four workbench attachment slots. */
    public static int maskOf(List<Attachment> attachments) {
        int mask = 0;
        for (Attachment attachment : attachments) {
            mask |= attachment.bit();
        }
        return mask;
    }
}
