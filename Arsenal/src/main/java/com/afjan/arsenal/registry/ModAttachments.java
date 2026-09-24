package com.afjan.arsenal.registry;

import java.util.function.Supplier;

import com.afjan.arsenal.Arsenal;

import net.minecraft.network.codec.ByteBufCodecs;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

public final class ModAttachments {
    public static final DeferredRegister<AttachmentType<?>> ATTACHMENTS =
            DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, Arsenal.MODID);

    /**
     * Game time a player started charging a railgun, 0 when not charging. Synced to everyone who can see the player,
     * so their clients show the capacitors filling and play the charge whine; never saved.
     */
    public static final Supplier<AttachmentType<Long>> RAIL_CHARGE = ATTACHMENTS.register("rail_charge",
            () -> AttachmentType.builder(() -> 0L).sync(ByteBufCodecs.VAR_LONG).build());

    private ModAttachments() {}
}
