package com.afjan.oreborn.registry;

import java.util.function.Supplier;

import com.afjan.oreborn.Oreborn;
import com.mojang.serialization.Codec;

import net.minecraft.network.codec.ByteBufCodecs;

import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/** Per-player cooldowns of the set bonuses (game time when usable again), saved with the player and kept on death. */
public final class ModAttachments {
    public static final DeferredRegister<AttachmentType<?>> ATTACHMENTS = DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, Oreborn.MODID);

    public static final Supplier<AttachmentType<Long>> ICE_BLOCK_READY = ATTACHMENTS.register("ice_block_ready",
            () -> AttachmentType.builder(() -> 0L).serialize(Codec.LONG.fieldOf("ready_at")).copyOnDeath().build());
    public static final Supplier<AttachmentType<Long>> PHOENIX_READY = ATTACHMENTS.register("phoenix_ready",
            () -> AttachmentType.builder(() -> 0L).serialize(Codec.LONG.fieldOf("ready_at")).copyOnDeath().build());

    /** Game time until which an entity is being electrocuted (synced to everyone who sees it, drives the animation). */
    public static final Supplier<AttachmentType<Long>> ELECTROCUTED = ATTACHMENTS.register("electrocuted",
            () -> AttachmentType.builder(() -> 0L).sync(ByteBufCodecs.VAR_LONG).build());

    private ModAttachments() {}
}
