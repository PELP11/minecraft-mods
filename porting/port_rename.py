"""Mechanical Forge 26.3 -> NeoForge 1.21.8 renames over a source tree. Usage: python3 port_rename.py <dir>"""
import os
import re
import sys

SUBS = [
    (r'\bnet\.minecraft\.resources\.Identifier\b', 'net.minecraft.resources.ResourceLocation'),
    (r'\bIdentifier\b', 'ResourceLocation'),
    (r'\.identifier\(\)', '.location()'),
    (r'\bnet\.minecraft\.world\.entity\.EntityTypes\b', 'net.minecraft.world.entity.EntityType'),
    (r'\bEntityTypes\b', 'EntityType'),
    (r'\bnet\.minecraft\.world\.level\.block\.entity\.BlockEntityTypes\b', 'net.minecraft.world.level.block.entity.BlockEntityType'),
    (r'\bBlockEntityTypes\b', 'BlockEntityType'),
    (r'net\.minecraft\.world\.entity\.npc\.villager\.', 'net.minecraft.world.entity.npc.'),
    (r'\.sendOverlayMessage\((.*)\);', r'.displayClientMessage(\1, true);'),
    (r'\bGuiGraphicsExtractor\b', 'GuiGraphics'),
    (r'net\.minecraftforge\.fml\.common\.Mod\b', 'net.neoforged.fml.common.Mod'),
    (r'net\.minecraftforge\.api\.distmarker\.', 'net.neoforged.api.distmarker.'),
    (r'net\.minecraftforge\.fml\.loading\.FMLEnvironment', 'net.neoforged.fml.loading.FMLEnvironment'),
    (r'net\.minecraftforge\.event\.entity\.', 'net.neoforged.neoforge.event.entity.'),
    (r'net\.minecraftforge\.event\.level\.', 'net.neoforged.neoforge.event.level.'),
    (r'net\.minecraftforge\.common\.loot\.', 'net.neoforged.neoforge.common.loot.'),
    (r'net\.minecraftforge\.registries\.DeferredRegister', 'net.neoforged.neoforge.registries.DeferredRegister'),
    (r'net\.minecraftforge\.client\.event\.', 'net.neoforged.neoforge.client.event.'),
    (r'net\.minecraftforge\.client\.', 'net.neoforged.neoforge.client.'),
    (r'net\.minecraftforge\.event\.', 'net.neoforged.neoforge.event.'),
    (r'org\.jspecify\.annotations\.Nullable', 'javax.annotation.Nullable'),
    (r'BlockItemTags\.(\w+)\.block\(\)', r'net.minecraft.tags.BlockTags.\1'),
    (r'BlockItemTags\.(\w+)\.item\(\)', r'net.minecraft.tags.ItemTags.\1'),
    (r'^import net\.minecraft\.tags\.BlockItemTags;\n', ''),
    (r'net\.minecraft\.world\.entity\.projectile\.(arrow|throwableitemprojectile|hurtingprojectile)\.', 'net.minecraft.world.entity.projectile.'),
    (r'net\.minecraft\.advancements\.triggers\.CriteriaTriggers', 'net.minecraft.advancements.CriteriaTriggers'),
    (r'net\.minecraftforge\.common\.util\.Result', 'net.neoforged.neoforge.common.util.TriState'),
]

for root, _, files in os.walk(sys.argv[1]):
    for name in files:
        if not name.endswith('.java'):
            continue
        path = os.path.join(root, name)
        text = open(path, encoding='utf-8').read()
        new = text
        for pattern, repl in SUBS:
            new = re.sub(pattern, repl, new, flags=re.M)
        if new != text:
            open(path, 'w', encoding='utf-8').write(new)
            print('renamed in', os.path.relpath(path, sys.argv[1]))
