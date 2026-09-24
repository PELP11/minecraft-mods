#!/usr/bin/env python3
"""Generates every Drillworks asset and data file: 3D models + atlases (models.py), 2D item and ore textures, block
states, item definitions, loot tables, recipes, tags, worldgen, lang (en_us + de_de), the GameTest structure and the
mod icon/banner. Deletes and rewrites its output folders, so edit this script, not the generated JSON.
  python3 tools/gen_assets.py            -> everything
  python3 tools/gen_assets.py preview    -> build/texture_preview.png (2D textures) as well
"""
import glob
import gzip
import json
import math
import os
import shutil
import struct
import sys
import zipfile
import zlib

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import models  # noqa: E402
from pixels import Img, hexc, mix, decode_png, preview_sheet  # noqa: E402

NS = 'drillworks'
ROOT = os.path.normpath(os.path.join(HERE, '..'))
RES = os.path.join(ROOT, 'src', 'main', 'resources')
ASSETS = os.path.join(RES, 'assets', NS)
DATA = os.path.join(RES, 'data')
DATA_VERSION = 5023
written = 0

HEADS = ['stone', 'copper', 'iron', 'golden', 'amethyst', 'diamond', 'netherite']
MODULES = ['fortune', 'efficiency', 'silk_touch', 'smelting', 'reinforced', 'wide_bore', 'vein_seeker', 'void_filter',
           'fuel_saver']
FLAT_ITEMS = ['crude_oil', 'drill_engine', 'track', 'blank_module'] + [m + '_module' for m in MODULES]


def write_json(path, obj):
    global written
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, 'w', encoding='utf-8', newline='\n') as f:
        json.dump(obj, f, indent=2, ensure_ascii=False)
        f.write('\n')
    written += 1


def asset(*parts):
    return os.path.join(ASSETS, *parts)


def data(namespace, *parts):
    return os.path.join(DATA, namespace, *parts)


def rl(path):
    return f'{NS}:{path}'


# =====================================================================================================================
# models, block states, item definitions
# =====================================================================================================================

def item_def(name, model):
    write_json(asset('items', name + '.json'), {'model': {'type': 'minecraft:model', 'model': model}})


def assets_json():
    global written
    written += models.write_all(ASSETS)
    for name in FLAT_ITEMS:
        write_json(asset('models', 'item', name + '.json'),
                   {'parent': 'minecraft:item/generated', 'textures': {'layer0': rl('item/' + name)}})
        item_def(name, rl('item/' + name))
    for name in ['mining_drill', 'empty_canister', 'gasoline_canister'] + [h + '_drill_head' for h in HEADS]:
        item_def(name, rl('item/' + name))
    # not items: models the drill's renderer draws through ITEM_MODEL
    item_def('drill_body', rl('item/drill_body'))
    item_def('drill_gauge', rl('item/drill_gauge'))
    for ore in ('crude_oil_ore', 'deepslate_crude_oil_ore'):
        write_json(asset('models', 'block', ore + '.json'),
                   {'parent': 'minecraft:block/cube_all', 'textures': {'all': rl('block/' + ore)}})
        write_json(asset('blockstates', ore + '.json'), {'variants': {'': {'model': rl('block/' + ore)}}})
        item_def(ore, rl('block/' + ore))
    variants = {}
    for facing, y in (('north', 0), ('east', 90), ('south', 180), ('west', 270)):
        for lit in ('false', 'true'):
            v = {'model': rl('block/refinery' + ('_lit' if lit == 'true' else ''))}
            if y:
                v['y'] = y
            variants[f'facing={facing},lit={lit}'] = v
    write_json(asset('blockstates', 'refinery.json'), {'variants': variants})
    item_def('refinery', rl('block/refinery'))
    write_json(asset('blockstates', 'distillation_column.json'), {'variants': {
        'top=false': {'model': rl('block/distillation_column')},
        'top=true': {'model': rl('block/distillation_column_top')}}})
    item_def('distillation_column', rl('block/distillation_column_top'))


# =====================================================================================================================
# 2D textures
# =====================================================================================================================

def noise(x, y, seed):
    return (zlib.crc32(b'%d,%d,%d' % (x, y, seed)) & 0xFFFF) / 65535.0


def vanilla_texture(path):
    jars = glob.glob(os.path.join(ROOT, 'build', 'moddev', 'artifacts', 'minecraft-patched-*-sources.jar'))
    for jar in jars:
        with zipfile.ZipFile(jar) as z:
            try:
                return decode_png(z.read('assets/minecraft/textures/' + path))
            except KeyError:
                pass
    return None


def fallback_stone(dark):
    base = ['#5c5c5c', '#6e6e6e', '#7f7f7f', '#8f8f8f'] if not dark else ['#2e2e36', '#3a3a42', '#46464e', '#55555d']
    img = Img(16, 16)
    for y in range(16):
        for x in range(16):
            n = noise(x // 2, y // 2, 7 if dark else 3) * 0.6 + noise(x, y, 11) * 0.4
            img.set(x, y, hexc(base[min(3, int(n * 4))]))
    return img


def oil_ore(dark):
    """Stone with glossy black oil pockets that seep into the rock around them."""
    img = vanilla_texture('block/%s.png' % ('deepslate' if dark else 'stone')) or fallback_stone(dark)
    img = img.copy() if img.h == 16 else img
    pockets = [(4, 4, 2.6), (11, 6, 2.1), (6, 11, 2.3), (12, 12, 1.6)]
    oil = [hexc('0b0a0c'), hexc('15131a'), hexc('231f2c'), hexc('3a3348')]
    for y in range(16):
        for x in range(16):
            d, near = min((math.hypot(x - px, (y - py) * 1.15) - r, (px, py, r)) for px, py, r in pockets)
            if d < 0:
                px, py, r = near
                lit = ((px - x) + (py - y)) / (r * 1.4)       # light from the upper left
                c = oil[0] if d > -0.8 else oil[1] if lit < 0.1 else oil[2] if lit < 0.55 else oil[3]
                img.set(x, y, c)
            elif d < 1.2:
                img.set(x, y, mix(img.get(x, y), hexc('1a1614'), 0.55))   # oil-soaked rim
    for px, py, r in pockets:     # highlights
        img.set(int(px - r * 0.45), int(py - r * 0.5), hexc('8a80a0'))
        img.set(int(px - r * 0.45) + 1, int(py - r * 0.5), hexc('4c445c'))
    return img


def crude_oil_item():
    img = Img(16, 16)
    body = [hexc('08070a'), hexc('14121a'), hexc('221e2c'), hexc('342e44')]
    for y in range(16):
        for x in range(16):
            # a droplet: round bottom, pointed top
            cx, cy = 8.0, 10.0
            dx, dy = x + 0.5 - cx, y + 0.5 - cy
            inside = (dx * dx + dy * dy < 22) or (dy < 0 and abs(dx) < (dy + 9.5) * 0.52 and dy > -9)
            if inside:
                shade = 1 if dx > 1.5 else 2 if dx > -1.5 else 3
                if dy > 3.2:
                    shade = 0
                img.set(x, y, body[shade])
    img.outline(hexc('000000'))
    for (x, y) in ((6, 8), (6, 9), (7, 7)):
        img.set(x, y, hexc('b4a8d8'))
    img.set(9, 12, hexc('5a4e70'))
    return img


def pcb(symbol_color, glyph):
    """A module: a small circuit board with gold contacts and a glowing glyph."""
    img = Img(16, 16)
    for y in range(2, 14):
        for x in range(1, 15):
            edge = x in (1, 14) or y in (2, 13)
            img.set(x, y, hexc('0e3a24') if edge else (hexc('1a6a3e') if noise(x, y, 5) > 0.2 else hexc('17603a')))
    for x in range(3, 14, 2):
        img.set(x, 13, hexc('f0c030'))
        img.set(x, 14, hexc('c08a10'))
    for (x, y) in ((3, 4), (12, 4), (3, 11), (12, 11)):
        img.set(x, y, hexc('d8d8d8'))
    for (x, y) in ((4, 6), (5, 6), (4, 7), (11, 9), (11, 10), (10, 10)):
        img.set(x, y, hexc('2e8a56'))
    if glyph:
        c = hexc(symbol_color)
        for y, row in enumerate(glyph):
            for x, ch in enumerate(row):
                if ch == '#':
                    img.set(5 + x, 5 + y, c)
                elif ch == '+':
                    img.set(5 + x, 5 + y, mix(c, hexc('ffffff'), 0.5))
    return img


GLYPHS = {
    'fortune': ('3a8ce8', ['..+..', '.###.', '#####', '.###.', '..#..', '.....']),
    'efficiency': ('ff4030', ['...#.', '..##.', '.####', '###..', '.##..', '.#...']),
    'silk_touch': ('f4f0ff', ['....+', '...##', '..##.', '.##..', '##...', '#....']),
    'smelting': ('ff9a20', ['..#..', '.##..', '.###.', '##+##', '#+++#', '.###.']),
    'reinforced': ('b4bcc8', ['#####', '#+++#', '#+++#', '.#+#.', '.###.', '..#..']),
    'wide_bore': ('c070ff', ['#.#.#', '.....', '#.#.#', '.....', '#.#.#', '.....']),
    'vein_seeker': ('40e0d0', ['#...#', '.#.#.', '..#..', '..#..', '.#.#.', '#...#']),
    'void_filter': ('7a3cff', ['.###.', '#...#', '#.+.#', '#...#', '.###.', '.....']),
    'fuel_saver': ('50e060', ['..#..', '..#..', '.###.', '#####', '#####', '.###.']),
}


def engine_item():
    img = Img(16, 16)
    for y in range(5, 14):
        for x in range(2, 14):
            img.set(x, y, hexc('6e757e') if noise(x, y, 3) > 0.15 else hexc('4a5058'))
    for x in range(2, 14):
        img.set(x, 5, hexc('c3cad1'))
        img.set(x, 13, hexc('2c3035'))
    for k, x in enumerate((3, 6, 9)):     # cylinders with yellow heads
        for y in range(1, 6):
            for dx in range(3):
                img.set(x + dx, y, hexc('d9a916') if y < 3 else hexc('949ca5'))
        img.set(x, 1, hexc('ffe27a'))
    for y in range(7, 12, 2):
        for x in range(3, 13):
            img.set(x, y, hexc('22262b'))
    for y in range(8, 12):
        img.set(14, y, hexc('b0602f'))
        img.set(15, y, hexc('7a3e1e'))
    img.outline(hexc('141619'))
    return img


def track_item():
    img = Img(16, 16)
    for y in range(3, 13):
        for x in range(1, 15):
            img.set(x, y, hexc('232326') if (x % 3) else hexc('0d0d0e'))
    for x in range(1, 15):
        img.set(x, 3, hexc('3e3e43'))
        img.set(x, 12, hexc('18181a'))
    for cx in (4, 8, 12):
        for y in range(6, 10):
            for x in range(cx - 1, cx + 2):
                img.set(x, y, hexc('949ca5'))
        img.set(cx, 7, hexc('c3cad1'))
    img.outline(hexc('000000'))
    return img


def textures(preview=False):
    global written
    out = {}
    out['item/crude_oil'] = crude_oil_item()
    out['item/drill_engine'] = engine_item()
    out['item/track'] = track_item()
    out['item/blank_module'] = pcb(None, None)
    for m, (color, glyph) in GLYPHS.items():
        out['item/%s_module' % m] = pcb(color, glyph)
    out['block/crude_oil_ore'] = oil_ore(False)
    out['block/deepslate_crude_oil_ore'] = oil_ore(True)
    for path, img in out.items():
        full = asset('textures', path + '.png')
        os.makedirs(os.path.dirname(full), exist_ok=True)
        img.save(full)
        written += 1
    # mod icon (square) and banner: the drill rendered by the model previewer
    icon = models.view(['mining_drill'], [25, 215, 0], 128, bg=(38, 34, 26, 255))
    icon.save(os.path.join(RES, 'drillworks_icon.png'))
    left = models.view(['mining_drill'], [22, 215, 0], 200, bg=(38, 34, 26, 255))
    right = models.view(['refinery_lit', 'distillation_column', 'distillation_column_top'], [22, 215, 0], 200,
                        bg=(38, 34, 26, 255), offsets=[(0, 0, 0), (0, 16, 0), (0, 32, 0)])
    banner = Img(400, 200, (38, 34, 26, 255))
    banner.paste(left, 0, 0)
    banner.paste(right, 200, 0)
    banner.save(os.path.join(RES, 'drillworks_banner.png'))
    written += 2
    if preview:
        os.makedirs(os.path.join(ROOT, 'build'), exist_ok=True)
        preview_sheet(list(out.values()), scale=6, cols=8).save(os.path.join(ROOT, 'build', 'texture_preview.png'))


# =====================================================================================================================
# loot, recipes, tags, worldgen
# =====================================================================================================================

def self_drop(name):
    write_json(data(NS, 'loot_table', 'blocks', name + '.json'), {
        'type': 'minecraft:block',
        'pools': [{'rolls': 1, 'entries': [{'type': 'minecraft:item', 'name': rl(name)}],
                   'condition': {'type': 'minecraft:survives_explosion'}}],
        'random_sequence': f'{NS}:blocks/{name}',
    })


def ore_loot(block, drop, low, high):
    write_json(data(NS, 'loot_table', 'blocks', block + '.json'), {
        'type': 'minecraft:block',
        'pools': [{'rolls': 1, 'entries': [{
            'type': 'minecraft:alternatives',
            'children': [
                {'type': 'minecraft:item', 'condition': 'minecraft:tool/can_silk_touch', 'name': rl(block)},
                {'type': 'minecraft:item', 'name': rl(drop), 'modifier': [
                    {'type': 'minecraft:set_count', 'count': {'type': 'minecraft:uniform', 'min': low, 'max': high}},
                    {'type': 'minecraft:apply_bonus', 'enchantment': 'minecraft:fortune', 'formula': 'minecraft:ore_drops'},
                    {'type': 'minecraft:explosion_decay'}]},
            ]}]}],
        'random_sequence': f'{NS}:blocks/{block}',
    })


def loot():
    ore_loot('crude_oil_ore', 'crude_oil', 1, 3)
    ore_loot('deepslate_crude_oil_ore', 'crude_oil', 1, 3)
    self_drop('refinery')
    self_drop('distillation_column')


def shaped(name, pattern, key, result, count=1, category='misc'):
    write_json(data(NS, 'recipe', name + '.json'), {
        'type': 'minecraft:crafting_shaped', 'category': category, 'key': key, 'pattern': pattern,
        'result': {'id': result, 'count': count}})


def shapeless(name, ingredients, result, count=1, category='misc'):
    write_json(data(NS, 'recipe', name + '.json'), {
        'type': 'minecraft:crafting_shapeless', 'category': category, 'ingredients': ingredients,
        'result': {'id': result, 'count': count}})


HEAD_MATERIAL = {'stone': '#minecraft:stone_tool_materials', 'copper': 'minecraft:copper_ingot',
                 'iron': 'minecraft:iron_ingot', 'golden': 'minecraft:gold_ingot',
                 'amethyst': 'minecraft:amethyst_shard', 'diamond': 'minecraft:diamond',
                 'netherite': 'minecraft:netherite_ingot'}
MODULE_RECIPES = {
    'fortune': ['minecraft:lapis_block', 'minecraft:emerald'],
    'efficiency': ['minecraft:redstone_block', 'minecraft:sugar'],
    'silk_touch': ['minecraft:slime_ball', 'minecraft:white_wool'],
    'smelting': ['minecraft:blast_furnace'],
    'reinforced': ['minecraft:obsidian', 'minecraft:iron_block'],
    'wide_bore': ['minecraft:piston', 'minecraft:diamond', 'minecraft:piston'],
    'vein_seeker': ['minecraft:ender_eye', 'minecraft:compass'],
    'void_filter': ['minecraft:cactus', 'minecraft:ender_pearl'],
    'fuel_saver': ['minecraft:copper_block', 'minecraft:redstone'],
}


def recipes():
    names = []

    def s(name, *a, **k):
        shaped(name, *a, **k)
        names.append(rl(name))

    def sl(name, *a, **k):
        shapeless(name, *a, **k)
        names.append(rl(name))

    s('mining_drill', ['IEI', 'TCT', 'IBI'], {'I': 'minecraft:iron_ingot', 'E': rl('drill_engine'), 'T': rl('track'),
      'C': 'minecraft:chest', 'B': 'minecraft:iron_block'}, rl('mining_drill'), category='equipment')
    s('drill_engine', ['IPI', 'RFR', 'ICI'], {'I': 'minecraft:iron_ingot', 'P': 'minecraft:piston',
      'R': 'minecraft:redstone', 'F': 'minecraft:furnace', 'C': 'minecraft:copper_ingot'}, rl('drill_engine'))
    s('track', ['NIN', 'KKK', 'NIN'], {'N': 'minecraft:iron_nugget', 'I': 'minecraft:iron_ingot',
      'K': 'minecraft:dried_kelp'}, rl('track'), count=2)
    for h in HEADS:
        if h == 'netherite':
            write_json(data(NS, 'recipe', 'netherite_drill_head.json'), {
                'type': 'minecraft:smithing_transform', 'base': rl('diamond_drill_head'),
                'addition': 'minecraft:netherite_ingot', 'result': {'id': rl('netherite_drill_head')}})
            names.append(rl('netherite_drill_head'))
            continue
        s(h + '_drill_head', [' M ', 'MMM', 'MIM'], {'M': HEAD_MATERIAL[h], 'I': 'minecraft:iron_ingot'},
          rl(h + '_drill_head'), category='equipment')
    s('blank_module', ['NRN', 'GCG', 'NRN'], {'N': 'minecraft:gold_nugget', 'R': 'minecraft:redstone',
      'G': 'minecraft:glass_pane', 'C': 'minecraft:comparator'}, rl('blank_module'), count=2)
    for m, extra in MODULE_RECIPES.items():
        sl(m + '_module', [rl('blank_module')] + extra, rl(m + '_module'))
    s('refinery', ['BIB', 'BFB', 'III'], {'B': 'minecraft:bricks', 'I': 'minecraft:iron_ingot',
      'F': 'minecraft:blast_furnace'}, rl('refinery'))
    s('distillation_column', ['ICI', 'IGI', 'ICI'], {'I': 'minecraft:iron_ingot', 'C': 'minecraft:copper_ingot',
      'G': 'minecraft:glass'}, rl('distillation_column'))
    s('empty_canister', [' II', 'I I', 'III'], {'I': 'minecraft:iron_ingot'}, rl('empty_canister'), count=2)
    write_json(data(NS, 'advancement', 'recipes', 'drillworks.json'), {
        'parent': 'minecraft:recipes/root',
        'criteria': {'has_material': {'trigger': 'minecraft:inventory_changed',
                                      'conditions': {'items': [{'items': ['minecraft:iron_ingot', rl('crude_oil')]}]}}},
        'requirements': [['has_material']],
        'rewards': {'recipes': names},
    })


def tag(namespace, kind, name, values):
    write_json(data(namespace, 'tags', kind, name + '.json'), {'values': values})


def opt(ids):
    return [{'id': i, 'required': False} for i in ids]


def tags():
    ores = [rl('crude_oil_ore'), rl('deepslate_crude_oil_ore')]
    tag('minecraft', 'block', 'mineable/pickaxe', ores + [rl('refinery'), rl('distillation_column')])
    tag('c', 'block', 'ores', ores)
    tag('c', 'item', 'ores', ores)
    for h in HEADS:
        tag(NS, 'item', 'repairs_%s_drill_head' % h, [HEAD_MATERIAL[h]])
    tag(NS, 'item', 'void_filter', opt([
        'minecraft:cobblestone', 'minecraft:cobbled_deepslate', 'minecraft:dirt', 'minecraft:gravel',
        'minecraft:netherrack', 'minecraft:tuff', 'minecraft:granite', 'minecraft:diorite', 'minecraft:andesite',
        'minecraft:stone', 'minecraft:deepslate', 'minecraft:calcite', 'minecraft:blackstone', 'minecraft:basalt',
        'minecraft:smooth_basalt', 'minecraft:dripstone_block', 'minecraft:pointed_dripstone', 'minecraft:end_stone',
        'minecraft:soul_soil', 'minecraft:soul_sand', 'minecraft:coarse_dirt', 'minecraft:rooted_dirt']))
    tag(NS, 'item', 'never_smelt', opt([
        'minecraft:cobblestone', 'minecraft:cobbled_deepslate', 'minecraft:stone', 'minecraft:deepslate',
        'minecraft:sand', 'minecraft:red_sand', 'minecraft:clay_ball', 'minecraft:clay', 'minecraft:netherrack',
        'minecraft:basalt', 'minecraft:cactus', 'minecraft:kelp', 'minecraft:wet_sponge', 'minecraft:sea_pickle',
        'minecraft:chorus_fruit', 'minecraft:stone_bricks', 'minecraft:sandstone', 'minecraft:red_sandstone',
        'minecraft:quartz_block', 'minecraft:nether_bricks', 'minecraft:cobblestone_wall']) + ['#minecraft:logs'])


def ore_targets():
    def rule(block, height, fallback):
        return {'state': rl(block), 'target': {'predicate_type': 'minecraft:any_of', 'rules': [
            {'predicate_type': 'minecraft:all_of', 'rules': [
                {'predicate_type': 'minecraft:tag_match', 'tag': 'minecraft:height_specific_ore_replaceables'},
                {'predicate_type': 'minecraft:height_match', 'min_inclusive': height[0], 'max_inclusive': height[1]}]},
            {'predicate_type': 'minecraft:all_of', 'rules': [
                {'predicate_type': 'minecraft:not', 'rule': {
                    'predicate_type': 'minecraft:tag_match', 'tag': 'minecraft:height_specific_ore_replaceables'}},
                {'predicate_type': 'minecraft:tag_match', 'tag': fallback}]}]}}
    return [rule('crude_oil_ore', (0, 2031), 'minecraft:stone_ore_replaceables'),
            rule('deepslate_crude_oil_ore', (-2032, 8), 'minecraft:deepslate_ore_replaceables')]


def worldgen():
    # about as common as coal: coal ore is 30 + 20 veins of 17 per chunk, crude oil 22 + 16 veins of 12
    write_json(data(NS, 'worldgen', 'feature', 'ore_crude_oil.json'),
               {'type': 'minecraft:ore', 'discard_chance_on_air_exposure': 0.0, 'size': 12, 'targets': ore_targets()})
    write_json(data(NS, 'worldgen', 'feature', 'ore_crude_oil_buried.json'),
               {'type': 'minecraft:ore', 'discard_chance_on_air_exposure': 0.5, 'size': 12, 'targets': ore_targets()})
    write_json(data(NS, 'worldgen', 'placed_feature', 'ore_crude_oil_upper.json'), {
        'feature': rl('ore_crude_oil'),
        'placement': [{'type': 'minecraft:count', 'count': 22}, {'type': 'minecraft:in_square'},
                      {'type': 'minecraft:height_range', 'height': {'type': 'minecraft:uniform',
                       'min_inclusive': {'absolute': 136}, 'max_inclusive': {'below_top': 0}}},
                      {'type': 'minecraft:biome'}]})
    write_json(data(NS, 'worldgen', 'placed_feature', 'ore_crude_oil_lower.json'), {
        'feature': rl('ore_crude_oil_buried'),
        'placement': [{'type': 'minecraft:count', 'count': 16}, {'type': 'minecraft:in_square'},
                      {'type': 'minecraft:height_range', 'height': {'type': 'minecraft:trapezoid',
                       'min_inclusive': {'absolute': -32}, 'max_inclusive': {'absolute': 160}}},
                      {'type': 'minecraft:biome'}]})
    write_json(data(NS, 'neoforge', 'biome_modifier', 'ore_crude_oil.json'), {
        'type': 'neoforge:add_features', 'biomes': '#minecraft:is_overworld',
        'features': [rl('ore_crude_oil_upper'), rl('ore_crude_oil_lower')], 'step': 'underground_ores'})


# =====================================================================================================================
# language
# =====================================================================================================================

HEAD_NAMES = {
    'stone': ('Stone Drill Head', 'Steinbohrkopf'), 'copper': ('Copper Drill Head', 'Kupferbohrkopf'),
    'iron': ('Iron Drill Head', 'Eisenbohrkopf'), 'golden': ('Golden Drill Head', 'Goldbohrkopf'),
    'amethyst': ('Amethyst Drill Head', 'Amethystbohrkopf'), 'diamond': ('Diamond Drill Head', 'Diamantbohrkopf'),
    'netherite': ('Netherite Drill Head', 'Netheritbohrkopf'),
}
MODULE_TEXT = {
    'fortune': (('Fortune Module', '+1 Fortune on everything the drill mines'),
                ('Glücksmodul', '+1 Glück auf alles, was der Bohrer abbaut')),
    'efficiency': (('Efficiency Module', '+40% boring speed'), ('Effizienzmodul', '+40 % Bohrgeschwindigkeit')),
    'silk_touch': (('Silk Touch Module', 'Mined blocks drop themselves'),
                   ('Behutsamkeitsmodul', 'Abgebaute Blöcke droppen sich selbst')),
    'smelting': (('Smelting Module', 'Ores come out smelted'), ('Schmelzmodul', 'Erze kommen geschmolzen heraus')),
    'reinforced': (('Reinforced Module', '-40% drill head wear'), ('Verstärkungsmodul', '-40 % Verschleiß des Bohrkopfs')),
    'wide_bore': (('Wide Bore Module', 'Bores 5x5 instead of 3x3'), ('Breitbohrmodul', 'Bohrt 5x5 statt 3x3')),
    'vein_seeker': (('Vein Seeker Module', 'Follows ore veins out of the tunnel walls'),
                    ('Adersucher-Modul', 'Folgt Erzadern aus den Tunnelwänden heraus')),
    'void_filter': (('Void Filter Module', 'Destroys cobblestone, dirt, gravel and other junk'),
                    ('Leerenfilter-Modul', 'Vernichtet Bruchstein, Erde, Kies und anderen Schutt')),
    'fuel_saver': (('Fuel Saver Module', '-30% fuel use'), ('Spritsparmodul', '-30 % Treibstoffverbrauch')),
}


def lang():
    en = {
        'itemGroup.drillworks': 'Drillworks',
        'item.drillworks.mining_drill': 'Mining Drill',
        'entity.drillworks.mining_drill': 'Mining Drill',
        'item.drillworks.crude_oil': 'Crude Oil',
        'item.drillworks.empty_canister': 'Empty Canister',
        'item.drillworks.gasoline_canister': 'Gasoline Canister',
        'item.drillworks.drill_engine': 'Drill Engine',
        'item.drillworks.track': 'Track',
        'item.drillworks.blank_module': 'Blank Module',
        'block.drillworks.crude_oil_ore': 'Crude Oil Deposit',
        'block.drillworks.deepslate_crude_oil_ore': 'Deepslate Crude Oil Deposit',
        'block.drillworks.refinery': 'Refinery',
        'block.drillworks.distillation_column': 'Distillation Column',
        'container.drillworks.refinery': 'Refinery',
        'tooltip.drillworks.head_stats': 'Speed %s  |  %s module socket(s)',
        'tooltip.drillworks.trait.conductive': 'Conductive: -25% fuel use',
        'tooltip.drillworks.trait.lucky': 'Lucky: +1 Fortune',
        'tooltip.drillworks.trait.resonant': 'Resonant: Silk Touch built in',
        'tooltip.drillworks.trait.molten': 'Molten Core: smelts ores, fireproof',
        'tooltip.drillworks.empty_socket': ' - empty socket',
        'tooltip.drillworks.worn': 'Worn out - repair it on an anvil',
        'tooltip.drillworks.stacks': 'Stacks up to %s times',
        'tooltip.drillworks.fuel': 'Fuel: %s / %s mB',
        'tooltip.drillworks.mining_drill': 'Ride it: W drills where you look, S reverses, A/D turn. Look up/down to bore stairs. Sneak + use: storage & heads.',
        'tooltip.drillworks.mining_drill_pickup': 'Sneak + punch to pick it up (keeps its fuel, drops its cargo)',
        'gui.drillworks.hold': 'Hold',
        'gui.drillworks.fuel': 'Gasoline: %s / %s mB',
        'gui.drillworks.fuel_hint': 'Put Gasoline Canisters in the slot on the right',
        'gui.drillworks.sockets_hint': 'Insert a drill head to use its module sockets',
        'gui.drillworks.gasoline': 'Gasoline: %s / %s mB',
        'gui.drillworks.unformed': 'Needs 2 Distillation Columns on top',
        'message.drillworks.no_fuel': 'Out of gasoline',
        'message.drillworks.no_head': 'No drill head mounted (sneak + use the drill)',
        'message.drillworks.worn': 'The drill head is worn out',
        'message.drillworks.unbreakable': 'Unbreakable block ahead',
        'message.drillworks.too_hard': '%s is too hard for this drill head',
        'message.drillworks.full': 'The hold is full',
        'message.drillworks.tank_full': 'The tank is full',
        'message.drillworks.no_room': 'Not enough room for the Mining Drill here',
        'message.drillworks.sneak_to_pick_up': 'Sneak + punch to pick up the Mining Drill',
    }
    de = {
        'itemGroup.drillworks': 'Drillworks',
        'item.drillworks.mining_drill': 'Bohrmaschine',
        'entity.drillworks.mining_drill': 'Bohrmaschine',
        'item.drillworks.crude_oil': 'Rohöl',
        'item.drillworks.empty_canister': 'Leerer Kanister',
        'item.drillworks.gasoline_canister': 'Benzinkanister',
        'item.drillworks.drill_engine': 'Bohrmotor',
        'item.drillworks.track': 'Kette',
        'item.drillworks.blank_module': 'Leeres Modul',
        'block.drillworks.crude_oil_ore': 'Erdölvorkommen',
        'block.drillworks.deepslate_crude_oil_ore': 'Tiefenschiefer-Erdölvorkommen',
        'block.drillworks.refinery': 'Raffinerie',
        'block.drillworks.distillation_column': 'Destillationskolonne',
        'container.drillworks.refinery': 'Raffinerie',
        'tooltip.drillworks.head_stats': 'Tempo %s  |  %s Modulplätze',
        'tooltip.drillworks.trait.conductive': 'Leitfähig: -25 % Treibstoffverbrauch',
        'tooltip.drillworks.trait.lucky': 'Glücksbringer: +1 Glück',
        'tooltip.drillworks.trait.resonant': 'Resonanz: Behutsamkeit eingebaut',
        'tooltip.drillworks.trait.molten': 'Glutkern: schmilzt Erze, feuerfest',
        'tooltip.drillworks.empty_socket': ' - freier Modulplatz',
        'tooltip.drillworks.worn': 'Abgenutzt - am Amboss reparieren',
        'tooltip.drillworks.stacks': 'Bis zu %s-mal stapelbar',
        'tooltip.drillworks.fuel': 'Treibstoff: %s / %s mB',
        'tooltip.drillworks.mining_drill': 'Aufsteigen: W bohrt in Blickrichtung, S fährt zurück, A/D drehen. Nach oben/unten schauen bohrt Treppen. Schleichen + Benutzen: Lager & Bohrköpfe.',
        'tooltip.drillworks.mining_drill_pickup': 'Schleichen + Schlagen hebt sie auf (Treibstoff bleibt, Ladung fällt heraus)',
        'gui.drillworks.hold': 'Laderaum',
        'gui.drillworks.fuel': 'Benzin: %s / %s mB',
        'gui.drillworks.fuel_hint': 'Benzinkanister in den Platz rechts legen',
        'gui.drillworks.sockets_hint': 'Setze einen Bohrkopf ein, um seine Modulplätze zu nutzen',
        'gui.drillworks.gasoline': 'Benzin: %s / %s mB',
        'gui.drillworks.unformed': 'Braucht 2 Destillationskolonnen darauf',
        'message.drillworks.no_fuel': 'Kein Benzin mehr',
        'message.drillworks.no_head': 'Kein Bohrkopf montiert (Schleichen + Benutzen)',
        'message.drillworks.worn': 'Der Bohrkopf ist abgenutzt',
        'message.drillworks.unbreakable': 'Unzerstörbarer Block voraus',
        'message.drillworks.too_hard': '%s ist zu hart für diesen Bohrkopf',
        'message.drillworks.full': 'Der Laderaum ist voll',
        'message.drillworks.tank_full': 'Der Tank ist voll',
        'message.drillworks.no_room': 'Hier ist nicht genug Platz für die Bohrmaschine',
        'message.drillworks.sneak_to_pick_up': 'Schleichen + Schlagen, um die Bohrmaschine aufzuheben',
    }
    for h, (e, d) in HEAD_NAMES.items():
        en['item.drillworks.%s_drill_head' % h] = e
        de['item.drillworks.%s_drill_head' % h] = d
    for m, ((en_name, en_tip), (de_name, de_tip)) in MODULE_TEXT.items():
        en['item.drillworks.%s_module' % m] = en_name
        en['tooltip.drillworks.module.' + m] = en_tip
        de['item.drillworks.%s_module' % m] = de_name
        de['tooltip.drillworks.module.' + m] = de_tip
    assert set(en) == set(de), set(en) ^ set(de)
    write_json(asset('lang', 'en_us.json'), en)
    write_json(asset('lang', 'de_de.json'), de)


# =====================================================================================================================
# GameTest structure
# =====================================================================================================================

def nbt_string(s):
    b = s.encode('utf-8')
    return struct.pack('>H', len(b)) + b


def nbt_named(tag_type, name, payload):
    return bytes([tag_type]) + nbt_string(name) + payload


def nbt_int_list(values):
    return bytes([3]) + struct.pack('>i', len(values)) + b''.join(struct.pack('>i', v) for v in values)


def nbt_compound_list(compounds):
    return bytes([10]) + struct.pack('>i', len(compounds)) + b''.join(c + b'\x00' for c in compounds)


def structure(name, size, blocks):
    global written
    palette, index, entries = [], {}, []
    for (x, y, z, block) in blocks:
        if block not in index:
            index[block] = len(palette)
            palette.append(block)
        entries.append(nbt_named(9, 'pos', nbt_int_list([x, y, z])) + nbt_named(3, 'state', struct.pack('>i', index[block])))
    root = (nbt_named(9, 'size', nbt_int_list(list(size)))
            + nbt_named(9, 'entities', bytes([0]) + struct.pack('>i', 0))
            + nbt_named(9, 'blocks', nbt_compound_list(entries))
            + nbt_named(9, 'palette', nbt_compound_list([nbt_named(8, 'id', nbt_string(b)) for b in palette]))
            + nbt_named(3, 'DataVersion', struct.pack('>i', DATA_VERSION)))
    raw = bytes([10]) + nbt_string('') + root + b'\x00'
    path = data(NS, 'structure', name + '.nbt')
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with gzip.open(path, 'wb') as f:
        f.write(raw)
    written += 1


def main():
    preview = 'preview' in sys.argv[1:]
    for folder in (asset('blockstates'), asset('models'), asset('items'), asset('lang'), asset('textures'),
                   data(NS, 'loot_table'), data(NS, 'recipe'), data(NS, 'advancement'), data(NS, 'tags'),
                   data(NS, 'worldgen'), data(NS, 'neoforge'), data(NS, 'structure'),
                   data('minecraft', 'tags'), data('c', 'tags')):
        if os.path.isdir(folder):
            shutil.rmtree(folder)
    assets_json()
    textures(preview)
    loot()
    recipes()
    tags()
    worldgen()
    lang()
    structure('quarry', (16, 12, 16), [(x, y, z, 'minecraft:stone') for x in range(16) for y in range(4) for z in range(16)])
    print(f'{written} files written')


if __name__ == '__main__':
    main()
