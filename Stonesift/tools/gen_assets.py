#!/usr/bin/env python3
"""Generates every Stonesift asset and data file (models via models.py, 2D textures, block states, item definitions,
loot, recipes, tags, lang en_us + de_de, the GameTest structure, icon). Rewrites its output folders.
  python3 tools/gen_assets.py [preview]
"""
import gzip
import json
import math
import os
import shutil
import struct
import sys
import zlib

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import models  # noqa: E402
from pixels import Img, hexc, mix, preview_sheet  # noqa: E402

NS = 'stonesift'
ROOT = os.path.normpath(os.path.join(HERE, '..'))
RES = os.path.join(ROOT, 'src', 'main', 'resources')
ASSETS = os.path.join(RES, 'assets', NS)
DATA = os.path.join(RES, 'data')
written = 0

ROCKS = list(models.ROCK_COLORS)
ROCK_NAMES = {'cobblestone': ('Cobblestone', 'Bruchstein'), 'andesite': ('Andesite', 'Andesit'),
              'granite': ('Granite', 'Granit'), 'diorite': ('Diorite', 'Diorit'), 'tuff': ('Tuff', 'Tuffstein'),
              'deepslate': ('Deepslate', 'Tiefenschiefer'), 'blackstone': ('Blackstone', 'Schwarzstein')}
ROCK_BLOCK = {'cobblestone': 'minecraft:cobblestone', 'andesite': 'minecraft:andesite', 'granite': 'minecraft:granite',
              'diorite': 'minecraft:diorite', 'tuff': 'minecraft:tuff', 'deepslate': 'minecraft:cobbled_deepslate',
              'blackstone': 'minecraft:blackstone'}
STAGES = [('_gravel', 'Gravel', 'schotter'), ('_rock_flour', 'Rock Flour', 'mehl'), ('_fine_slurry', 'Fine Slurry', 'feinschlamm')]
HAMMERS = [('wooden', '#minecraft:planks', 'Wooden', 'Holz'), ('stone', '#minecraft:stone_tool_materials', 'Stone', 'Stein'),
           ('iron', 'minecraft:iron_ingot', 'Iron', 'Eisen'), ('golden', 'minecraft:gold_ingot', 'Golden', 'Gold'),
           ('diamond', 'minecraft:diamond', 'Diamond', 'Diamant')]
BLOCKS = ['hand_sieve', 'grinder', 'shaker_sieve', 'rock_former', 'sluice', 'flotation_cell', 'coal_generator',
          'deep_drill_frame', 'deep_drill']
FLAT = (['iron_ore_fragment', 'copper_ore_fragment', 'gold_ore_fragment', 'netherite_fragment', 'diamond_shard',
         'emerald_shard', 'iron_concentrate', 'copper_concentrate', 'gold_concentrate', 'diamond_concentrate',
         'string_mesh', 'iron_mesh', 'diamond_mesh', 'geologist_hammer', 'speed_upgrade', 'advanced_speed_upgrade',
         'diamond_drill_bit', 'netherite_drill_bit'] + [h[0] + '_stone_hammer' for h in HAMMERS]
        + [r + s[0] for r in ROCKS for s in STAGES])
HANDHELD = {h[0] + '_stone_hammer' for h in HAMMERS} | {'geologist_hammer'}


def write_json(path, obj):
    global written
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, 'w', encoding='utf-8', newline='\n') as f:
        json.dump(obj, f, indent=2, ensure_ascii=False)
        f.write('\n')
    written += 1


def asset(*p):
    return os.path.join(ASSETS, *p)


def data(ns, *p):
    return os.path.join(DATA, ns, *p)


def rl(p):
    return f'{NS}:{p}'


def item_def(name, model):
    write_json(asset('items', name + '.json'), {'model': {'type': 'minecraft:model', 'model': model}})


# ======================================================================================================================
# models / block states
# ======================================================================================================================

def assets_json():
    global written
    written += models.write_all(ASSETS)
    for n in FLAT:
        write_json(asset('models', 'item', n + '.json'), {
            'parent': 'minecraft:item/handheld' if n in HANDHELD else 'minecraft:item/generated',
            'textures': {'layer0': rl('item/' + n)}})
        item_def(n, rl('item/' + n))
    for r in ROCKS:
        item_def('pile_' + r, rl('item/pile_' + r))
    write_json(asset('blockstates', 'hand_sieve.json'),
               {'variants': {'mesh=%d' % m: {'model': rl('block/hand_sieve_mesh%d' % m)} for m in range(4)}})
    item_def('hand_sieve', rl('block/hand_sieve_mesh1'))
    rot = {'north': 0, 'east': 90, 'south': 180, 'west': 270}
    for m in models.MACHINES:
        v = {}
        for f, y in rot.items():
            for lit in ('false', 'true'):
                e = {'model': rl('block/' + m + ('_lit' if lit == 'true' else ''))}
                if y:
                    e['y'] = y
                v[f'facing={f},lit={lit}'] = e
        write_json(asset('blockstates', m + '.json'), {'variants': v})
        item_def(m, rl('block/' + m + '_lit'))
    write_json(asset('blockstates', 'sluice.json'),
               {'variants': {f'facing={f}': ({'model': rl('block/sluice'), 'y': y} if y else {'model': rl('block/sluice')})
                             for f, y in rot.items()}})
    item_def('sluice', rl('block/sluice'))
    write_json(asset('blockstates', 'deep_drill_frame.json'), {'variants': {'': {'model': rl('block/deep_drill_frame')}}})
    item_def('deep_drill_frame', rl('block/deep_drill_frame'))


# ======================================================================================================================
# 2D textures
# ======================================================================================================================

def noise(x, y, seed):
    return (zlib.crc32(b'%d,%d,%d' % (x, y, seed)) & 0xFFFF) / 65535.0


def pal(name):
    return models.MATERIALS[name][1]


def blob(img, cx, cy, r, ramp, seed, squash=1.0):
    for y in range(16):
        for x in range(16):
            dx, dy = x + 0.5 - cx, (y + 0.5 - cy) / squash
            d = math.hypot(dx, dy)
            if d < r:
                lit = (-dx - dy) / r
                t = 2 + (1 if lit > 0.35 else 0) - (1 if lit < -0.35 else 0)
                if noise(x, y, seed) > 0.85:
                    t = min(4, t + 1)
                img.set(x, y, ramp[max(0, min(4, t))])


def gravel(rock):
    img = Img(16, 16)
    ramp = pal('rock_' + rock)
    for i, (cx, cy, r) in enumerate([(5, 10, 3.2), (10.5, 10.5, 3.4), (8, 6, 3.0), (3.5, 5.5, 2.0), (12.5, 5, 2.2), (8, 12.8, 2.4)]):
        blob(img, cx, cy, r, ramp, zlib.crc32(rock.encode()) + i, 0.85)
    img.outline(hexc('1a1a1a'))
    return img


def flour(rock):
    img = Img(16, 16)
    ramp = [mix(c, hexc('f4efe4'), 0.45) for c in pal('rock_' + rock)]
    for y in range(16):
        for x in range(16):
            h = 13.5 - abs(x - 7.5) * 0.9
            if 5 < y + 0.5 < 14.5 and y + 0.5 > 14.5 - (h - 5) and 1 <= x <= 14:
                t = 2 + (1 if x < 7 else 0) + (1 if noise(x, y, 3) > 0.8 else 0) - (1 if noise(x, y, 5) < 0.15 else 0)
                img.set(x, y, ramp[max(0, min(4, t))])
    img.outline(mix(ramp[0], hexc('000000'), 0.5))
    return img


def slurry(rock):
    img = Img(16, 16)
    ramp = [mix(c, hexc('2a4a6a'), 0.45) for c in pal('rock_' + rock)]
    blob(img, 8, 9.5, 6.2, ramp, 7, 0.7)
    for (x, y) in ((5, 7), (6, 7), (10, 9)):
        img.set(x, y, hexc('d8ecff'))
    img.outline(hexc('0c1620'))
    return img


def chunk(colors, seed):
    img = Img(16, 16)
    poly = [(4, 3), (11, 2), (14, 7), (12, 13), (5, 14), (2, 9)]
    from pixels import point_in_poly
    for y in range(16):
        for x in range(16):
            if point_in_poly(x + 0.5, y + 0.5, poly):
                t = 2 + (1 if x + y < 14 else 0) - (1 if x + y > 20 else 0)
                if noise(x, y, seed) > 0.78:
                    t = 4
                img.set(x, y, hexc(colors[max(0, min(4, t))]))
    img.outline(hexc(colors[0]))
    return img


def shard(colors, seed):
    img = Img(16, 16)
    from pixels import point_in_poly
    poly = [(7, 1), (12, 6), (9, 15), (4, 10)]
    for y in range(16):
        for x in range(16):
            if point_in_poly(x + 0.5, y + 0.5, poly):
                t = 3 if x < 8 else 2
                if abs(x - y * 0.4 - 4) < 0.8:
                    t = 4
                img.set(x, y, hexc(colors[t]))
    img.outline(hexc(colors[0]))
    return img


def concentrate(colors):
    img = Img(16, 16)
    ramp = [hexc(c) for c in colors]
    for (cx, cy, r) in ((6, 10, 4), (10.5, 10.5, 3.6), (8, 7, 3.4)):
        blob(img, cx, cy, r, ramp, 11)
    for (x, y) in ((6, 7), (10, 9), (8, 11), (5, 11)):
        img.set(x, y, hexc('ffffff'))
    img.outline(hexc(colors[0]))
    return img


def mesh(ramp):
    img = Img(16, 16)
    for y in range(1, 15):
        for x in range(1, 15):
            frame = x in (1, 14) or y in (1, 14)
            if frame:
                img.set(x, y, pal('plank')[2 if (x + y) % 3 else 1])
            elif x % 3 == 1 or y % 3 == 1:
                img.set(x, y, ramp[3 if (x + y) % 2 else 2])
    img.outline(pal('darkwood')[0])
    return img


def tool(head_ramp, head_poly_kind):
    img = Img(16, 16)
    for i in range(11):   # handle, bottom-left to middle
        x, y = 2 + i, 13 - i
        img.set(x, y, pal('plank')[2])
        img.set(x + 1, y, pal('plank')[1])
    if head_poly_kind == 'hammer':
        for y in range(1, 8):
            for x in range(8, 15):
                if abs((x - 11) + (y - 4)) <= 4 and abs((x - 11) - (y - 4)) <= 2:
                    img.set(x, y, head_ramp[3 if x + y < 15 else 2])
    else:
        for i in range(-5, 6):
            x, y = 10 + i, 5 + i * -0 + abs(i) // 2
            img.set(x, 5 - (5 - abs(i)) // 3, head_ramp[3])
            img.set(x, 6 - (5 - abs(i)) // 3, head_ramp[2])
    img.outline(hexc('1a1a1a'))
    return img


def drill_bit(colors):
    img = Img(16, 16)
    from pixels import point_in_poly
    poly = [(8, 1), (13, 11), (8, 15), (3, 11)]
    for y in range(16):
        for x in range(16):
            if point_in_poly(x + 0.5, y + 0.5, poly):
                t = 3 if (x + 2 * y) % 5 < 2 else 2
                img.set(x, y, hexc(colors[t if x < 8 else t - 1]))
    img.outline(hexc(colors[0]))
    return img


def chip(color, label):
    img = Img(16, 16)
    for y in range(3, 13):
        for x in range(2, 14):
            img.set(x, y, hexc('1a6a3e') if noise(x, y, 5) > 0.2 else hexc('17603a'))
    for x in range(3, 13, 2):
        img.set(x, 13, hexc('f0c030'))
    for i in range(label):   # arrows
        ox = 4 + i * 4
        for k in range(3):
            img.set(ox + k, 6 + k, hexc(color))
            img.set(ox + k, 10 - k, hexc(color))
    img.outline(hexc('0e3a24'))
    return img


def textures(preview):
    global written
    out = {}
    for r in ROCKS:
        out['item/%s_gravel' % r] = gravel(r)
        out['item/%s_rock_flour' % r] = flour(r)
        out['item/%s_fine_slurry' % r] = slurry(r)
    out['item/iron_ore_fragment'] = chunk(('5a4a3a', '8a7258', 'b89a78', 'd8bc98', 'f0dcc0'), 1)
    out['item/copper_ore_fragment'] = chunk(('5a2a16', '8a4424', 'c06a3a', 'e08e5a', 'f7bd92'), 2)
    out['item/gold_ore_fragment'] = chunk(('6e4a07', 'a87a0e', 'e0b21c', 'f7d548', 'fff3a4'), 3)
    out['item/netherite_fragment'] = chunk(('1c1418', '3a2a2e', '5a4448', '7a6064', 'a08488'), 4)
    out['item/diamond_shard'] = shard(('0e4a4a', '1a7c7a', '2cb8b0', '5ce4d8', 'd8fff8'), 5)
    out['item/emerald_shard'] = shard(('0a4a1e', '12802e', '1cc048', '5ae880', 'c8ffd8'), 6)
    out['item/iron_concentrate'] = concentrate(('4a4d52', '72767c', 'a0a5ab', 'c8ccd1', 'eef0f2'))
    out['item/copper_concentrate'] = concentrate(('5a2a16', '8a4424', 'c06a3a', 'e08e5a', 'f7bd92'))
    out['item/gold_concentrate'] = concentrate(('6e4a07', 'a87a0e', 'e0b21c', 'f7d548', 'fff3a4'))
    out['item/diamond_concentrate'] = concentrate(('0e4a4a', '1a7c7a', '2cb8b0', '5ce4d8', 'b8fff6'))
    out['item/string_mesh'] = mesh(pal('mesh1'))
    out['item/iron_mesh'] = mesh(pal('mesh2'))
    out['item/diamond_mesh'] = mesh(pal('mesh3'))
    heads = {'wooden': pal('plank'), 'stone': pal('stone'), 'iron': pal('steel'), 'golden': pal('hazard'), 'diamond': pal('mesh3')}
    for h, ramp in heads.items():
        out['item/%s_stone_hammer' % h] = tool(ramp, 'hammer')
    out['item/geologist_hammer'] = tool(pal('steel'), 'pick')
    out['item/diamond_drill_bit'] = drill_bit(('0e4a4a', '1a7c7a', '2cb8b0', '5ce4d8', 'b8fff6'))
    out['item/netherite_drill_bit'] = drill_bit(('1c181a', '2e2729', '433a3c', '5a5052', '7a7072'))
    out['item/speed_upgrade'] = chip('5ab0ff', 1)
    out['item/advanced_speed_upgrade'] = chip('c070ff', 2)
    for p, img in out.items():
        full = asset('textures', p + '.png')
        os.makedirs(os.path.dirname(full), exist_ok=True)
        img.save(full)
        written += 1
    icon = models.view(['hand_sieve_mesh1'], [25, 215, 0], 128, bg=(40, 44, 48, 255))
    icon.save(os.path.join(RES, 'stonesift_icon.png'))
    banner = Img(400, 200, (40, 44, 48, 255))
    banner.paste(models.view(['hand_sieve_mesh1'], [25, 215, 0], 200, bg=(40, 44, 48, 255)), 0, 0)
    banner.paste(models.view(['flotation_cell_lit'], [25, 215, 0], 200, bg=(40, 44, 48, 255)), 200, 0)
    banner.save(os.path.join(RES, 'stonesift_banner.png'))
    if preview:
        preview_sheet(list(out.values()), scale=5, cols=12).save(os.path.join(ROOT, 'build', 'texture_preview.png'))


# ======================================================================================================================
# data
# ======================================================================================================================

def shaped(name, pattern, key, result, count=1, cat='misc'):
    write_json(data(NS, 'recipe', name + '.json'), {'type': 'minecraft:crafting_shaped', 'category': cat, 'key': key,
                                                    'pattern': pattern, 'result': {'id': result, 'count': count}})


def shapeless(name, ingredients, result, count=1):
    write_json(data(NS, 'recipe', name + '.json'), {'type': 'minecraft:crafting_shapeless', 'category': 'misc',
                                                    'ingredients': ingredients, 'result': {'id': result, 'count': count}})


def cooking(name, kind, ingredient, result, xp, time):
    write_json(data(NS, 'recipe', name + '.json'), {'type': 'minecraft:' + kind, 'category': 'misc', 'cookingtime': time,
                                                    'experience': xp, 'ingredient': ingredient, 'result': {'id': result}})


def recipes():
    I, S = 'minecraft:iron_ingot', 'minecraft:stick'
    for h, mat, _, _ in HAMMERS:
        shaped(h + '_stone_hammer', ['MMM', 'MSM', ' S '], {'M': mat, 'S': S}, rl(h + '_stone_hammer'), cat='equipment')
    shaped('geologist_hammer', ['CIC', ' S ', ' S '], {'C': 'minecraft:copper_ingot', 'I': I, 'S': S}, rl('geologist_hammer'), cat='equipment')
    shaped('hand_sieve', ['S S', 'PPP', 'S S'], {'S': S, 'P': '#minecraft:planks'}, rl('hand_sieve'))
    shaped('string_mesh', ['S S', ' S ', 'S S'], {'S': 'minecraft:string'}, rl('string_mesh'))
    shaped('iron_mesh', ['B B', ' M ', 'B B'], {'B': 'minecraft:iron_bars', 'M': rl('string_mesh')}, rl('iron_mesh'))
    shaped('diamond_mesh', ['D D', ' M ', 'D D'], {'D': 'minecraft:diamond', 'M': rl('iron_mesh')}, rl('diamond_mesh'))
    shaped('grinder', ['CIC', 'IGI', 'CFC'], {'C': 'minecraft:cobblestone', 'I': I, 'G': 'minecraft:grindstone',
                                             'F': 'minecraft:furnace'}, rl('grinder'))
    shaped('shaker_sieve', ['IHI', 'RPR', 'LCL'], {'I': I, 'H': 'minecraft:hopper', 'R': 'minecraft:redstone',
                                                  'P': 'minecraft:piston', 'L': '#minecraft:logs', 'C': rl('hand_sieve')}, rl('shaker_sieve'))
    shaped('rock_former', ['IGI', 'BCB', 'III'], {'I': I, 'G': 'minecraft:glass', 'B': 'minecraft:bucket',
                                                 'C': 'minecraft:cauldron'}, rl('rock_former'))
    shaped('sluice', ['P P', 'PSP'], {'P': '#minecraft:planks', 'S': '#minecraft:wooden_slabs'}, rl('sluice'), count=3)
    shaped('flotation_cell', ['GIG', 'ICI', 'IRI'], {'G': 'minecraft:glass', 'I': I, 'C': 'minecraft:copper_block',
                                                    'R': 'minecraft:redstone_block'}, rl('flotation_cell'))
    shaped('coal_generator', ['III', 'IFI', 'CRC'], {'I': I, 'F': 'minecraft:furnace', 'C': 'minecraft:copper_ingot',
                                                    'R': 'minecraft:redstone'}, rl('coal_generator'))
    shaped('deep_drill_frame', ['I I', ' B ', 'I I'], {'I': I, 'B': 'minecraft:iron_block'}, rl('deep_drill_frame'), count=4)
    shaped('deep_drill', ['DRD', 'BPB', 'DOD'], {'D': 'minecraft:diamond', 'R': 'minecraft:redstone_block',
                                                'B': 'minecraft:iron_block', 'P': 'minecraft:piston',
                                                'O': 'minecraft:obsidian'}, rl('deep_drill'))
    shaped('diamond_drill_bit', [' D ', 'DID', ' D '], {'D': 'minecraft:diamond', 'I': 'minecraft:iron_block'}, rl('diamond_drill_bit'))
    write_json(data(NS, 'recipe', 'netherite_drill_bit.json'), {
        'type': 'minecraft:smithing_transform', 'base': rl('diamond_drill_bit'), 'addition': 'minecraft:netherite_ingot',
        'result': {'id': rl('netherite_drill_bit')}})
    shaped('speed_upgrade', ['RGR', 'GIG', 'RGR'], {'R': 'minecraft:redstone', 'G': 'minecraft:gold_ingot', 'I': I}, rl('speed_upgrade'))
    shaped('advanced_speed_upgrade', ['RDR', 'DUD', 'RDR'], {'R': 'minecraft:redstone_block', 'D': 'minecraft:diamond',
                                                            'U': rl('speed_upgrade')}, rl('advanced_speed_upgrade'))
    for frag, raw in (('iron_ore_fragment', 'raw_iron'), ('copper_ore_fragment', 'raw_copper'),
                      ('gold_ore_fragment', 'raw_gold'), ('netherite_fragment', 'netherite_scrap')):
        shapeless(raw + '_from_fragments', [rl(frag)] * 4, 'minecraft:' + raw)
    for sh, gem in (('diamond_shard', 'diamond'), ('emerald_shard', 'emerald')):
        shaped(gem + '_from_shards', ['###', '###', '###'], {'#': rl(sh)}, 'minecraft:' + gem)
    for c, out in (('iron', 'iron_ingot'), ('copper', 'copper_ingot'), ('gold', 'gold_ingot'), ('diamond', 'diamond')):
        cooking(c + '_concentrate_smelting', 'smelting', rl(c + '_concentrate'), 'minecraft:' + out, 0.7, 200)
        cooking(c + '_concentrate_blasting', 'blasting', rl(c + '_concentrate'), 'minecraft:' + out, 0.7, 100)
    recipes_dir = data(NS, 'recipe')
    names = sorted(rl(f[:-5]) for f in os.listdir(recipes_dir))
    write_json(data(NS, 'advancement', 'recipes', 'stonesift.json'), {
        'parent': 'minecraft:recipes/root',
        'criteria': {'has': {'trigger': 'minecraft:inventory_changed',
                             'conditions': {'items': [{'items': ['minecraft:cobblestone', 'minecraft:stick']}]}}},
        'requirements': [['has']], 'rewards': {'recipes': names}})


def loot():
    for b in BLOCKS:
        write_json(data(NS, 'loot_table', 'blocks', b + '.json'), {
            'type': 'minecraft:block', 'pools': [{'rolls': 1, 'entries': [{'type': 'minecraft:item', 'name': rl(b)}],
                                                  'condition': {'type': 'minecraft:survives_explosion'}}],
            'random_sequence': f'{NS}:blocks/{b}'})


def tags():
    def tag(ns, kind, name, values):
        write_json(data(ns, 'tags', kind, name + '.json'), {'values': values})
    wooden = ['hand_sieve', 'shaker_sieve', 'sluice']
    tag('minecraft', 'block', 'mineable/axe', [rl(b) for b in wooden])
    tag('minecraft', 'block', 'mineable/pickaxe', [rl(b) for b in BLOCKS if b not in wooden])
    tag('minecraft', 'item', 'pickaxes', [rl(h[0] + '_stone_hammer') for h in HAMMERS])


# ======================================================================================================================
# language
# ======================================================================================================================

RES_NAMES = {'coal': ('Coal', 'Kohle'), 'iron': ('Iron', 'Eisen'), 'copper': ('Copper', 'Kupfer'), 'gold': ('Gold', 'Gold'),
             'redstone': ('Redstone', 'Redstone'), 'lapis': ('Lapis Lazuli', 'Lapislazuli'), 'quartz': ('Quartz', 'Quarz'),
             'diamond': ('Diamond', 'Diamant'), 'emerald': ('Emerald', 'Smaragd'), 'netherite': ('Netherite', 'Netherit')}
RICHNESS_DE = {'coal': 'kohlereich', 'iron': 'eisenreich', 'copper': 'kupferreich', 'gold': 'goldreich',
               'redstone': 'redstonereich', 'lapis': 'lapisreich', 'quartz': 'quarzreich', 'diamond': 'diamantreich',
               'emerald': 'smaragdreich', 'netherite': 'netheritreich'}


def lang():
    en = {'itemGroup.stonesift': 'Stonesift'}
    de = {'itemGroup.stonesift': 'Stonesift'}

    def both(key, e, d):
        en[key] = e
        de[key] = d
    for r, (e, d) in ROCK_NAMES.items():
        both('rock.stonesift.' + r, e, d)
        both(f'item.stonesift.{r}_gravel', e + ' Gravel', d + 'schotter' if not d.endswith('stein') else d[:-5] + 'schotter')
        both(f'item.stonesift.{r}_rock_flour', e + ' Rock Flour', d + 'mehl')
        both(f'item.stonesift.{r}_fine_slurry', 'Fine ' + e + ' Slurry', d + '-Feinschlamm')
    de['item.stonesift.cobblestone_gravel'] = 'Bruchsteinschotter'
    de['item.stonesift.tuff_gravel'] = 'Tuffschotter'
    de['item.stonesift.blackstone_gravel'] = 'Schwarzsteinschotter'
    for k, (e, d) in RES_NAMES.items():
        both('resource.stonesift.' + k, e, d)
        en['richness.stonesift.' + k] = 'rich in ' + e.lower()
        de['richness.stonesift.' + k] = RICHNESS_DE[k]
    for h, _, e, d in HAMMERS:
        both(f'item.stonesift.{h}_stone_hammer', e + ' Stone Hammer', d + '-Steinhammer')
    for k, e, d in [
        ('block.stonesift.hand_sieve', 'Hand Sieve', 'Handsieb'), ('block.stonesift.grinder', 'Grinder', 'Mahlwerk'),
        ('block.stonesift.shaker_sieve', 'Shaker Sieve', 'Rüttelsieb'), ('block.stonesift.rock_former', 'Rock Former', 'Gesteinsformer'),
        ('block.stonesift.sluice', 'Sluice', 'Waschrinne'), ('block.stonesift.flotation_cell', 'Flotation Cell', 'Flotationszelle'),
        ('block.stonesift.coal_generator', 'Coal Generator', 'Kohlegenerator'),
        ('block.stonesift.deep_drill_frame', 'Deep Drill Frame', 'Tiefenbohrer-Rahmen'),
        ('block.stonesift.deep_drill', 'Deep Drill', 'Tiefenbohrer'),
        ('container.stonesift.grinder', 'Grinder', 'Mahlwerk'), ('container.stonesift.shaker_sieve', 'Shaker Sieve', 'Rüttelsieb'),
        ('container.stonesift.rock_former', 'Rock Former', 'Gesteinsformer'),
        ('container.stonesift.flotation_cell', 'Flotation Cell', 'Flotationszelle'),
        ('container.stonesift.coal_generator', 'Coal Generator', 'Kohlegenerator'),
        ('container.stonesift.deep_drill', 'Deep Drill', 'Tiefenbohrer'),
        ('item.stonesift.iron_ore_fragment', 'Iron Ore Fragment', 'Eisenerzsplitter'),
        ('item.stonesift.copper_ore_fragment', 'Copper Ore Fragment', 'Kupfererzsplitter'),
        ('item.stonesift.gold_ore_fragment', 'Gold Ore Fragment', 'Golderzsplitter'),
        ('item.stonesift.netherite_fragment', 'Netherite Fragment', 'Netheritsplitter'),
        ('item.stonesift.diamond_shard', 'Diamond Shard', 'Diamantsplitter'),
        ('item.stonesift.emerald_shard', 'Emerald Shard', 'Smaragdsplitter'),
        ('item.stonesift.iron_concentrate', 'Iron Concentrate', 'Eisenkonzentrat'),
        ('item.stonesift.copper_concentrate', 'Copper Concentrate', 'Kupferkonzentrat'),
        ('item.stonesift.gold_concentrate', 'Gold Concentrate', 'Goldkonzentrat'),
        ('item.stonesift.diamond_concentrate', 'Diamond Concentrate', 'Diamantkonzentrat'),
        ('item.stonesift.string_mesh', 'String Mesh', 'Fadennetz'), ('item.stonesift.iron_mesh', 'Iron Mesh', 'Eisennetz'),
        ('item.stonesift.diamond_mesh', 'Diamond Mesh', 'Diamantnetz'),
        ('item.stonesift.geologist_hammer', "Geologist's Hammer", 'Geologenhammer'),
        ('item.stonesift.speed_upgrade', 'Speed Upgrade (x2)', 'Tempo-Upgrade (x2)'),
        ('item.stonesift.advanced_speed_upgrade', 'Advanced Speed Upgrade (x4)', 'Tempo-Upgrade (x4)'),
        ('item.stonesift.diamond_drill_bit', 'Diamond Drill Bit', 'Diamant-Bohrkopf'),
        ('item.stonesift.netherite_drill_bit', 'Netherite Drill Bit', 'Netherit-Bohrkopf'),
        ('tooltip.stonesift.rich', 'Rich (Deep Drill): triple yield', 'Reich (Tiefenbohrer): dreifache Ausbeute'),
        ('tooltip.stonesift.stone_hammer', 'Rock drops as 2 gravel of its type', 'Gestein droppt als 2 Schotter seiner Art'),
        ('tooltip.stonesift.speed_upgrade', 'Rock Former: twice as fast', 'Gesteinsformer: doppelt so schnell'),
        ('tooltip.stonesift.advanced_speed_upgrade', 'Rock Former: four times as fast', 'Gesteinsformer: viermal so schnell'),
        ('tooltip.stonesift.drill_bit', 'For the Deep Drill; wears as it drills', 'Für den Tiefenbohrer; verschleißt beim Bohren'),
        ('message.stonesift.vein', 'Rock vein of this chunk: %s, %s', 'Gesteinsader dieses Chunks: %s, %s'),
        ('message.stonesift.sluice_head', 'Sluice controller: %s segments', 'Waschrinnen-Steuerung: %s Segmente'),
        ('message.stonesift.sluice_segment', 'Sluice segment (the one with water at its upper end controls the run)',
         'Rinnensegment (das Segment mit Wasser am oberen Ende steuert die Rinne)'),
        ('gui.stonesift.fe', '%s / %s FE', '%s / %s FE'),
        ('gui.stonesift.no_water', 'Needs a water source next to it', 'Braucht eine Wasserquelle daneben'),
        ('gui.stonesift.reagent', 'Reagent left for %s slurry', 'Reagenz reicht für %s Schlamm'),
        ('gui.stonesift.shaker_hint', 'Each redstone pulse sifts once', 'Jeder Redstone-Impuls siebt einmal'),
        ('gui.stonesift.grinder_hint', 'Fuel for %s runs', 'Brennstoff für %s Vorgänge'),
        ('gui.stonesift.former.0', 'Forming (x%s)', 'Formt (x%s)'),
        ('gui.stonesift.former.1', 'Needs a water source next to it', 'Braucht eine Wasserquelle daneben'),
        ('gui.stonesift.former.2', 'Needs a lava source next to it', 'Braucht eine Lavaquelle daneben'),
        ('gui.stonesift.former.3', 'Put a rock block in the pattern slot', 'Gesteinsblock ins Musterfeld legen'),
        ('gui.stonesift.former.4', 'Blackstone only forms in the Nether', 'Schwarzstein nur im Nether'),
        ('gui.stonesift.former.5', 'Buffer full - empty it with a hopper', 'Puffer voll - per Trichter leeren'),
        ('gui.stonesift.drill.0', 'Needs 8 Deep Drill Frames around it', 'Braucht 8 Tiefenbohrer-Rahmen ringsum'),
        ('gui.stonesift.drill.1', 'Insert a drill bit', 'Bohrkopf einsetzen'),
        ('gui.stonesift.drill.2', 'Not enough FE', 'Zu wenig FE'),
        ('gui.stonesift.drill.3', 'Boring to bedrock: %s / %s', 'Bohrt zum Grundgestein: %s / %s'),
        ('gui.stonesift.drill.4', 'Pumping rich rock', 'Fördert reiches Gestein'),
        ('gui.stonesift.drill.5', 'Output full', 'Ausgabe voll'),
    ]:
        both(k, e, d)
    assert set(en) == set(de)
    write_json(asset('lang', 'en_us.json'), en)
    write_json(asset('lang', 'de_de.json'), de)


# ======================================================================================================================
# GameTest structure
# ======================================================================================================================

def structure(name, size, blocks):
    global written

    def s(v):
        b = v.encode()
        return struct.pack('>H', len(b)) + b

    def named(t, n, p):
        return bytes([t]) + s(n) + p

    def ints(vals):
        return bytes([3]) + struct.pack('>i', len(vals)) + b''.join(struct.pack('>i', v) for v in vals)

    def comps(cs):
        return bytes([10]) + struct.pack('>i', len(cs)) + b''.join(c + b'\x00' for c in cs)
    palette, index, entries = [], {}, []
    for (x, y, z, b) in blocks:
        if b not in index:
            index[b] = len(palette)
            palette.append(b)
        entries.append(named(9, 'pos', ints([x, y, z])) + named(3, 'state', struct.pack('>i', index[b])))
    root = (named(9, 'size', ints(list(size))) + named(9, 'entities', bytes([0]) + struct.pack('>i', 0))
            + named(9, 'blocks', comps(entries)) + named(9, 'palette', comps([named(8, 'id', s(b)) for b in palette]))
            + named(3, 'DataVersion', struct.pack('>i', 5023)))
    path = data(NS, 'structure', name + '.nbt')
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with gzip.open(path, 'wb') as f:
        f.write(bytes([10]) + s('') + root + b'\x00')
    written += 1


def main():
    for folder in (asset('blockstates'), asset('models'), asset('items'), asset('lang'), asset('textures'),
                   data(NS), data('minecraft', 'tags')):
        if os.path.isdir(folder):
            shutil.rmtree(folder)
    os.makedirs(os.path.join(ROOT, 'build'), exist_ok=True)
    assets_json()
    textures('preview' in sys.argv[1:])
    recipes()
    loot()
    tags()
    lang()
    structure('yard', (12, 8, 12), [(x, 0, z, 'minecraft:stone') for x in range(12) for z in range(12)])
    print(f'{written} files written')


if __name__ == '__main__':
    main()
