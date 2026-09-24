package com.afjan.oreborn.registry;

import java.util.EnumMap;
import java.util.Map;

import com.afjan.oreborn.Oreborn;
import com.afjan.oreborn.block.CrustedLavaBlock;
import com.afjan.oreborn.material.OreMaterial;

import net.minecraft.util.valueproviders.UniformInt;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DropExperienceBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModBlocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(Oreborn.MODID);

    /** Stone ore (netherrack ore for Emberite). */
    public static final Map<OreMaterial, DeferredBlock<Block>> ORES = new EnumMap<>(OreMaterial.class);
    /** Deepslate ore (none for Emberite, which only generates in the Nether). */
    public static final Map<OreMaterial, DeferredBlock<Block>> DEEPSLATE_ORES = new EnumMap<>(OreMaterial.class);
    /** Block of crystal / ingot. */
    public static final Map<OreMaterial, DeferredBlock<Block>> STORAGE = new EnumMap<>(OreMaterial.class);

    static {
        for (OreMaterial material : OreMaterial.values()) {
            String id = material.id();
            // crystal ores drop experience like diamond ore; ingot ores give theirs in the furnace (like ancient debris)
            UniformInt xp = material.isCrystal() ? UniformInt.of(3, 7) : UniformInt.of(0, 0);
            if (material == OreMaterial.EMBERITE) {
                ORES.put(material, BLOCKS.registerBlock(id + "_ore", p -> new DropExperienceBlock(xp, p), () -> BlockBehaviour.Properties.of()
                        .mapColor(MapColor.NETHER).instrument(NoteBlockInstrument.BASEDRUM)
                        .requiresCorrectToolForDrops().strength(4.0F, 6.0F).sound(SoundType.NETHER_ORE)));
            } else {
                float hardness = material.isCrystal() ? 3.0F : 4.0F;
                ORES.put(material, BLOCKS.registerBlock(id + "_ore", p -> new DropExperienceBlock(xp, p), () -> BlockBehaviour.Properties.of()
                        .mapColor(MapColor.STONE).instrument(NoteBlockInstrument.BASEDRUM)
                        .requiresCorrectToolForDrops().strength(hardness, 3.0F)));
                DEEPSLATE_ORES.put(material, BLOCKS.registerBlock("deepslate_" + id + "_ore", p -> new DropExperienceBlock(xp, p), () -> BlockBehaviour.Properties.of()
                        .mapColor(MapColor.DEEPSLATE).instrument(NoteBlockInstrument.BASEDRUM)
                        .requiresCorrectToolForDrops().strength(hardness + 1.5F, 3.0F).sound(SoundType.DEEPSLATE)));
            }
        }
        STORAGE.put(OreMaterial.CRYOLITE, BLOCKS.registerSimpleBlock("cryolite_block", () -> BlockBehaviour.Properties.of()
                .mapColor(MapColor.ICE).requiresCorrectToolForDrops().strength(5.0F, 6.0F).sound(SoundType.AMETHYST)));
        STORAGE.put(OreMaterial.FULGURITE, BLOCKS.registerSimpleBlock("fulgurite_block", () -> BlockBehaviour.Properties.of()
                .mapColor(MapColor.COLOR_YELLOW).requiresCorrectToolForDrops().strength(5.0F, 6.0F).sound(SoundType.AMETHYST)));
        STORAGE.put(OreMaterial.EMBERITE, BLOCKS.registerSimpleBlock("emberite_block", () -> BlockBehaviour.Properties.of()
                .mapColor(MapColor.COLOR_ORANGE).requiresCorrectToolForDrops().strength(50.0F, 1200.0F).sound(SoundType.NETHERITE_BLOCK)
                .lightLevel(state -> 6)));
        STORAGE.put(OreMaterial.UMBRIUM, BLOCKS.registerSimpleBlock("umbrium_block", () -> BlockBehaviour.Properties.of()
                .mapColor(MapColor.COLOR_PURPLE).requiresCorrectToolForDrops().strength(50.0F, 1200.0F).sound(SoundType.NETHERITE_BLOCK)));
    }

    /** Temporary crust that Emberite boots form on lava; melts back into lava once nobody wearing them is near. */
    public static final DeferredBlock<CrustedLavaBlock> CRUSTED_LAVA = BLOCKS.registerBlock("crusted_lava", CrustedLavaBlock::new,
            () -> BlockBehaviour.Properties.of()
                    .mapColor(MapColor.NETHER).strength(0.5F).sound(SoundType.BASALT).lightLevel(state -> 7)
                    .noLootTable().pushReaction(PushReaction.IMMOVEABLE).isValidSpawn((state, level, pos, type) -> false));

    private ModBlocks() {}
}
