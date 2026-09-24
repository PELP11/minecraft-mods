#!/usr/bin/env python3
"""Generates all Oreborn JSON resources (models, blockstates, item definitions, equipment, loot, recipes,
recipe advancements, tags, damage types, worldgen, biome modifiers, lang) and the GameTest structure.
Pure Python, no dependencies.

Run from anywhere:  py tools/gen_data.py
"""
import gzip
import json
import os
import shutil
import struct

HERE = os.path.dirname(os.path.abspath(__file__))
RES = os.path.normpath(os.path.join(HERE, '..', 'src', 'main', 'resources'))
ASSETS = os.path.join(RES, 'assets', 'oreborn')
DATA = os.path.join(RES, 'data')
NS = 'oreborn'
DATA_VERSION = 5023  # Minecraft 26.3 world version (used for the test structure)

CRYSTALS = ['cryolite', 'fulgurite']
INGOTS = ['emberite', 'umbrium']
MATERIALS = CRYSTALS + INGOTS
TITLE = {m: m.capitalize() for m in MATERIALS}
TOOLS = ['sword', 'pickaxe', 'axe', 'shovel', 'hoe']
ARMOR = ['helmet', 'chestplate', 'leggings', 'boots']
GEAR = TOOLS + ARMOR
# what alloys with the scrap into an ingot (like gold for netherite)
ALLOY = {'emberite': 'minecraft:gold_ingot', 'umbrium': 'minecraft:ender_pearl'}

written = 0


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


# ---- naming ----------------------------------------------------------------------------------------------

def main_item(m):
    return f'{m}_crystal' if m in CRYSTALS else f'{m}_ingot'


def drop_item(m):
    return f'{m}_shard' if m in CRYSTALS else f'raw_{m}'


def ores(m):
    """(block id, base) for every ore variant of a material."""
    if m == 'emberite':
        return [('emberite_ore', 'netherrack')]
    return [(f'{m}_ore', 'stone'), (f'deepslate_{m}_ore', 'deepslate')]


def storage(m):
    return f'{m}_block'


# =====================================================================================================
# Block & item models
# =====================================================================================================

def item_def(name, model):
    write_json(asset('items', name + '.json'), {'model': {'type': 'minecraft:model', 'model': model}})


def flat_item(name, parent='minecraft:item/generated'):
    write_json(asset('models', 'item', name + '.json'), {'parent': parent, 'textures': {'layer0': rl('item/' + name)}})
    item_def(name, rl('item/' + name))


def cube_block(name, texture=None):
    write_json(asset('blockstates', name + '.json'), {'variants': {'': {'model': rl('block/' + name)}}})
    write_json(asset('models', 'block', name + '.json'),
               {'parent': 'minecraft:block/cube_all', 'textures': {'all': rl('block/' + (texture or name))}})


def block_item(name):
    item_def(name, rl('block/' + name))


SIDES = ('down', 'up', 'north', 'south', 'west', 'east')


def glowing_ore_parent():
    """The ore cube plus an overlay cube with the glow layer, drawn at full brightness (like vanilla's firefly bush):
    the veins glow in the dark without lighting up the cave. No ambient occlusion or face shading on the glow."""
    def faces(texture):
        return {side: {'texture': texture, 'cullface': side} for side in SIDES}
    write_json(asset('models', 'block', 'glowing_ore.json'), {
        'parent': 'minecraft:block/block',
        'textures': {'particle': '#base'},
        'elements': [
            {'from': [0, 0, 0], 'to': [16, 16, 16], 'faces': faces('#base')},
            {'from': [0, 0, 0], 'to': [16, 16, 16], 'light_emission': 15, 'shade_direction_override': 'up',
             'neoforge_data': {'ambient_occlusion': False}, 'faces': faces('#glow')},
        ],
    })


def glowing_ore_block(name, material):
    write_json(asset('blockstates', name + '.json'), {'variants': {'': {'model': rl('block/' + name)}}})
    write_json(asset('models', 'block', name + '.json'), {
        'parent': rl('block/glowing_ore'),
        'textures': {'base': rl('block/' + name), 'glow': rl(f'block/{material}_ore_glow')},
    })


def models():
    glowing_ore_parent()
    for m in MATERIALS:
        for block, _ in ores(m):
            glowing_ore_block(block, m)
            block_item(block)
        cube_block(storage(m))
        block_item(storage(m))
        flat_item(drop_item(m))
        flat_item(main_item(m))
        if m in INGOTS:
            flat_item(f'{m}_scrap')
        for g in GEAR:
            flat_item(f'{m}_{g}', 'minecraft:item/handheld' if g in TOOLS else 'minecraft:item/generated')
        # armour as worn: the equipment asset points at textures/entity/equipment/<layer>/<material>.png
        write_json(asset('equipment', m + '.json'), {'layers': {
            'humanoid': [{'texture': rl(m)}],
            'humanoid_leggings': [{'texture': rl(m)}],
        }})
    # the Lightning Staff is held like a fishing rod: the force lightning leaves from the rod tip
    flat_item('lightning_staff', 'minecraft:item/handheld_rod')
    # crusted lava: one model per age (the cracks glow brighter as it melts)
    write_json(asset('blockstates', 'crusted_lava.json'),
               {'variants': {f'age={a}': {'model': rl(f'block/crusted_lava_{a}')} for a in range(4)}})
    for a in range(4):
        write_json(asset('models', 'block', f'crusted_lava_{a}.json'),
                   {'parent': 'minecraft:block/cube_all', 'textures': {'all': rl(f'block/crusted_lava_{a}')}})


# =====================================================================================================
# Loot tables
# =====================================================================================================

def self_drop(name):
    write_json(data(NS, 'loot_table', 'blocks', name + '.json'), {
        'type': 'minecraft:block',
        'pools': [{'condition': {'type': 'minecraft:survives_explosion'}, 'entries': [{'type': 'minecraft:item', 'name': rl(name)}], 'rolls': 1}],
        'random_sequence': rl('blocks/' + name),
    })


def ore_loot(block, m):
    modifiers = []
    if m in CRYSTALS:
        modifiers.append({'type': 'minecraft:set_count', 'count': {'type': 'minecraft:uniform', 'min': 2, 'max': 3}})
    modifiers += [{'type': 'minecraft:apply_bonus', 'enchantment': 'minecraft:fortune', 'formula': 'minecraft:ore_drops'},
                  {'type': 'minecraft:explosion_decay'}]
    write_json(data(NS, 'loot_table', 'blocks', block + '.json'), {
        'type': 'minecraft:block',
        'pools': [{'entries': [{'type': 'minecraft:alternatives', 'children': [
            {'type': 'minecraft:item', 'condition': 'minecraft:tool/can_silk_touch', 'name': rl(block)},
            {'type': 'minecraft:item', 'modifier': modifiers, 'name': rl(drop_item(m))},
        ]}], 'rolls': 1}],
        'random_sequence': rl('blocks/' + block),
    })


def loot():
    for m in MATERIALS:
        for block, _ in ores(m):
            ore_loot(block, m)
        self_drop(storage(m))


# =====================================================================================================
# Recipes (+ the advancements that unlock them in the recipe book)
# =====================================================================================================

UNLOCKS = {m: [] for m in MATERIALS}


def recipe(name, obj, material):
    write_json(data(NS, 'recipe', name + '.json'), obj)
    UNLOCKS[material].append(rl(name))


def shaped(name, pattern, key, result, count=1, category='misc', material=None):
    recipe(name, {'type': 'minecraft:crafting_shaped', 'category': category, 'key': key, 'pattern': pattern,
                  'result': {'id': result, 'count': count}}, material)


def cooking(name, kind, ingredient, result, xp, time, material):
    recipe(name, {'type': 'minecraft:' + kind, 'category': 'misc', 'cookingtime': time, 'experience': xp,
                  'ingredient': ingredient, 'result': {'id': result}}, material)


TOOL_PATTERNS = {
    'sword': ['X', 'X', '#'],
    'pickaxe': ['XXX', ' # ', ' # '],
    'axe': ['XX', 'X#', ' #'],
    'shovel': ['X', '#', '#'],
    'hoe': ['XX', ' #', ' #'],
    'helmet': ['XXX', 'X X'],
    'chestplate': ['X X', 'XXX', 'XXX'],
    'leggings': ['XXX', 'X X', 'X X'],
    'boots': ['X X', 'X X'],
}


def recipes():
    for m in MATERIALS:
        main = rl(main_item(m))
        shaped(storage(m), ['###', '###', '###'], {'#': main}, rl(storage(m)), category='building', material=m)
        recipe(f'{main_item(m)}_from_block', {'type': 'minecraft:crafting_shapeless', 'category': 'misc',
                                                'ingredients': [rl(storage(m))], 'result': {'id': main, 'count': 9}}, m)
        if m in CRYSTALS:
            # the shards craft straight into a crystal
            shaped(main_item(m), ['##', '##'], {'#': rl(drop_item(m))}, main, material=m)
            for g in GEAR:
                key = {'X': main}
                if g in TOOLS:
                    key['#'] = 'minecraft:stick'
                shaped(f'{m}_{g}', TOOL_PATTERNS[g], key, rl(f'{m}_{g}'), category='equipment', material=m)
        else:
            # melted into scrap, then alloyed into an ingot (like netherite)
            scrap = rl(f'{m}_scrap')
            for source in [drop_item(m)] + [b for b, _ in ores(m)]:
                cooking(f'{m}_scrap_from_smelting_{source}', 'smelting', rl(source), scrap, 2.0, 200, m)
                cooking(f'{m}_scrap_from_blasting_{source}', 'blasting', rl(source), scrap, 2.0, 100, m)
            recipe(main_item(m), {'type': 'minecraft:crafting_shapeless', 'category': 'misc', 'group': main_item(m),
                                  'ingredients': [scrap] * 4 + [ALLOY[m]] * 4, 'result': {'id': main}}, m)
            for g in GEAR:
                recipe(f'{m}_{g}_smithing', {'type': 'minecraft:smithing_transform', 'base': f'minecraft:diamond_{g}',
                                              'addition': main, 'result': {'id': rl(f'{m}_{g}')}}, m)

    # Lightning Staff: a Block of Fulgurite on a shaft of two lightning rods
    shaped('lightning_staff', ['  B', ' R ', 'R  '], {'B': rl('fulgurite_block'), 'R': '#minecraft:lightning_rods'},
           rl('lightning_staff'), category='equipment', material='fulgurite')

    for m in MATERIALS:
        items = [rl(drop_item(m)), rl(main_item(m))] + [rl(b) for b, _ in ores(m)]
        if m in INGOTS:
            items.append(rl(f'{m}_scrap'))
        write_json(data(NS, 'advancement', 'recipes', m + '.json'), {
            'parent': 'minecraft:recipes/root',
            'criteria': {'has_material': {'conditions': {'items': [{'items': items}]}, 'trigger': 'minecraft:inventory_changed'}},
            'requirements': [['has_material']],
            'rewards': {'recipes': UNLOCKS[m]},
        })


# =====================================================================================================
# Tags
# =====================================================================================================

def tag(namespace, kind, name, values):
    write_json(data(namespace, 'tags', kind, name + '.json'), {'values': values})


def tags():
    all_ores = [rl(b) for m in MATERIALS for b, _ in ores(m)]
    blocks = [rl(storage(m)) for m in MATERIALS]
    crystal_blocks = [rl(b) for m in CRYSTALS for b, _ in ores(m)] + [rl(storage(m)) for m in CRYSTALS]
    ingot_blocks = [rl(b) for m in INGOTS for b, _ in ores(m)] + [rl(storage(m)) for m in INGOTS]

    tag('minecraft', 'block', 'mineable/pickaxe', all_ores + blocks + [rl('crusted_lava')])
    tag('minecraft', 'block', 'needs_iron_tool', crystal_blocks)      # like diamond ore
    tag('minecraft', 'block', 'needs_diamond_tool', ingot_blocks)     # like ancient debris
    tag('minecraft', 'block', 'beacon_base_blocks', blocks)
    tag('minecraft', 'item', 'beacon_payment_items', [rl(main_item(m)) for m in MATERIALS])

    for kind in ('block', 'item'):
        tag('c', kind, 'ores', [f'#c:ores/{m}' for m in MATERIALS])
        for m in MATERIALS:
            tag('c', kind, f'ores/{m}', [rl(b) for b, _ in ores(m)])
            tag('c', kind, f'storage_blocks/{m}', [rl(storage(m))])
        tag('c', kind, 'storage_blocks', [f'#c:storage_blocks/{m}' for m in MATERIALS])
        tag('c', kind, 'ores_in_ground/stone', [rl(f'{m}_ore') for m in MATERIALS if m != 'emberite'])
        tag('c', kind, 'ores_in_ground/deepslate', [rl(f'deepslate_{m}_ore') for m in MATERIALS if m != 'emberite'])
        tag('c', kind, 'ores_in_ground/netherrack', [rl('emberite_ore')])
        tag('c', kind, 'ore_rates/dense', [rl(b) for m in CRYSTALS for b, _ in ores(m)])
        tag('c', kind, 'ore_rates/singular', [rl(b) for m in INGOTS for b, _ in ores(m)])

    tag('c', 'item', 'gems', [f'#c:gems/{m}' for m in CRYSTALS])
    tag('c', 'item', 'ingots', [f'#c:ingots/{m}' for m in INGOTS])
    tag('c', 'item', 'raw_materials', [f'#c:raw_materials/{m}' for m in INGOTS])
    for m in CRYSTALS:
        tag('c', 'item', f'gems/{m}', [rl(main_item(m))])
    for m in INGOTS:
        tag('c', 'item', f'ingots/{m}', [rl(main_item(m))])
        tag('c', 'item', f'raw_materials/{m}', [rl(drop_item(m))])

    # tool / armour kinds: enchanting, repairing and everything else that asks "is this a sword?"
    plural = {'sword': 'swords', 'pickaxe': 'pickaxes', 'axe': 'axes', 'shovel': 'shovels', 'hoe': 'hoes',
              'helmet': 'head_armor', 'chestplate': 'chest_armor', 'leggings': 'leg_armor', 'boots': 'foot_armor'}
    for g in GEAR:
        tag('minecraft', 'item', plural[g], [rl(f'{m}_{g}') for m in MATERIALS])
    tag('minecraft', 'item', 'freeze_immune_wearables', [rl(f'cryolite_{a}') for a in ARMOR])

    for m in MATERIALS:
        tag(NS, 'item', f'{m}_repair_materials', [rl(main_item(m))])

    # what Emberite tools smelt on the spot
    tag(NS, 'item', 'emberite_smeltable', ['#c:raw_materials', '#c:ores', '#c:sands', 'minecraft:potato', 'minecraft:kelp',
                                           'minecraft:chorus_fruit', 'minecraft:wet_sponge', 'minecraft:clay_ball', 'minecraft:cactus'])

    tag('minecraft', 'damage_type', 'is_freezing', [rl('frostbite')])   # blazes and magma cubes take 5x
    tag('minecraft', 'damage_type', 'is_fire', [rl('combustion')])      # fire-immune mobs shrug it off
    # force lightning hits 5 times a second without flinging its targets out of the stream
    tag('minecraft', 'damage_type', 'bypasses_cooldown', [rl('force_lightning')])
    tag('minecraft', 'damage_type', 'no_knockback', [rl('force_lightning')])
    tag('minecraft', 'item', 'enchantable/durability', [rl('lightning_staff')])   # Unbreaking and Mending
    tag(NS, 'block', 'reflects_lightning', ['#c:glass_blocks', '#c:glass_panes'])   # force lightning bounces off glass


def damage_types():
    for name, effect in (('frostbite', 'freezing'), ('electrocution', None), ('combustion', 'burning'), ('force_lightning', None)):
        obj = {'exhaustion': 0.1, 'message_id': f'{NS}.{name}', 'scaling': 'when_caused_by_living_non_player'}
        if effect:
            obj['effects'] = effect
        write_json(data(NS, 'damage_type', name + '.json'), obj)


# =====================================================================================================
# Worldgen: as rare as diamonds (the same vein counts and sizes, each ore in its own layer)
# =====================================================================================================

def overworld_targets(m):
    """Stone variant in stone, deepslate variant in deepslate (same rules as vanilla diamond ore)."""
    def rules(block, height, replaceables):
        return {'state': rl(block), 'target': {'predicate_type': 'minecraft:any_of', 'rules': [
            {'predicate_type': 'minecraft:all_of', 'rules': [
                {'predicate_type': 'minecraft:tag_match', 'tag': 'minecraft:height_specific_ore_replaceables'},
                {'predicate_type': 'minecraft:height_match', **height}]},
            {'predicate_type': 'minecraft:all_of', 'rules': [
                {'predicate_type': 'minecraft:not', 'rule': {'predicate_type': 'minecraft:tag_match', 'tag': 'minecraft:height_specific_ore_replaceables'}},
                {'predicate_type': 'minecraft:tag_match', 'tag': replaceables}]}]}}
    return [rules(f'{m}_ore', {'min_inclusive': 0, 'max_inclusive': 2031}, 'minecraft:stone_ore_replaceables'),
            rules(f'deepslate_{m}_ore', {'min_inclusive': -2032, 'max_inclusive': 8}, 'minecraft:deepslate_ore_replaceables')]


HEIGHTS = {
    # frozen deep caves: most common around y -16
    'cryolite': {'type': 'minecraft:trapezoid', 'min_inclusive': {'absolute': -64}, 'max_inclusive': {'absolute': 32}},
    # storm-charged mountains: y 0 to 160, most common around y 80
    'fulgurite': {'type': 'minecraft:trapezoid', 'min_inclusive': {'absolute': 0}, 'max_inclusive': {'absolute': 160}},
    # at the edge of the void, right above the bedrock
    'umbrium': {'type': 'minecraft:uniform', 'min_inclusive': {'above_bottom': 0}, 'max_inclusive': {'absolute': -44}},
    # anywhere in the Nether (below its bedrock roof at y 127)
    'emberite': {'type': 'minecraft:uniform', 'min_inclusive': {'absolute': 8}, 'max_inclusive': {'absolute': 120}},
}

# (suffix, vein size, discard chance on air exposure, veins per chunk) - diamonds: ~3.5 small, 2 medium, 2 buried
VEINS = [('', 4, 0.5, 4), ('_medium', 8, 0.5, 2), ('_buried', 8, 1.0, 2)]


def worldgen():
    for m in MATERIALS:
        if m == 'emberite':
            targets = [{'state': rl('emberite_ore'), 'target': {'predicate_type': 'minecraft:block_match', 'block': 'minecraft:netherrack'}}]
        else:
            targets = overworld_targets(m)
        placed = []
        for suffix, size, discard, count in VEINS:
            name = f'ore_{m}{suffix}'
            write_json(data(NS, 'worldgen', 'feature', name + '.json'),
                       {'type': 'minecraft:ore', 'discard_chance_on_air_exposure': discard, 'size': size, 'targets': targets})
            write_json(data(NS, 'worldgen', 'placed_feature', name + '.json'), {'feature': rl(name), 'placement': [
                {'type': 'minecraft:count', 'count': count},
                {'type': 'minecraft:in_square'},
                {'type': 'minecraft:height_range', 'height': HEIGHTS[m]},
                {'type': 'minecraft:biome'}]})
            placed.append(rl(name))
        write_json(data(NS, 'neoforge', 'biome_modifier', f'ore_{m}.json'), {
            'type': 'neoforge:add_features',
            'biomes': '#minecraft:is_nether' if m == 'emberite' else '#minecraft:is_overworld',
            'features': placed,
            'step': 'underground_ores',
        })


# =====================================================================================================
# Lang
# =====================================================================================================

ABILITIES = {
    'cryolite_sword': ('Frostbite', ['Hits freeze enemies solid.', 'Hitting a frozen enemy shatters the ice:',
                                     '+6 damage, and everything nearby freezes.']),
    'cryolite_pickaxe': ('Cryo Seal', ['Freezes the liquids around every block you mine:',
                                       'lava turns to obsidian or cobblestone,', 'still water to ice. No more lava surprises.']),
    'cryolite_axe': ('Frost Nova', ['Right-click: freezes every monster within', '6 blocks and deals 4 frost damage. (12 s)']),
    'cryolite_shovel': ('Frost Heave', ['Digs 3x3. Sneak to dig a single block.']),
    'cryolite_hoe': ('Glacial Irrigation', ['Tills 3x3.', 'Sneak + right-click: carves a hydrated 9x9',
                                            'farm plot around a new water source.']),
    'cryolite_helmet': ('Glacial Lungs', ['Breathe under water.']),
    'cryolite_chestplate': ('Rime Plating', ['Melee attackers are frozen.']),
    'cryolite_leggings': ('Brace', ['While sneaking: no knockback', 'and 25% less damage.']),
    'cryolite_boots': ('Frost Walker', ['Water freezes beneath your feet.', 'Walk on powder snow, never freeze.']),
    'cryolite_set': ('Full set: Permafrost', ['Nearby monsters are slowed.', 'Below 30% health you are encased in ice:',
                                              'Resistance IV + Regeneration II, and', 'attackers freeze. (90 s cooldown)']),

    'fulgurite_sword': ('Chain Lightning', ['Fully charged hits arc lightning to', 'up to 3 more enemies (half damage).']),
    'fulgurite_pickaxe': ('Ore Radar', ['Right-click: every ore within 12 blocks', 'glows through the walls for 10 s. (10 s)']),
    'fulgurite_axe': ('Stormcaller', ['Right-click: calls lightning down where', 'you look (32 blocks, 8 damage). (8 s)',
                                      'Creepers it strikes become charged.']),
    'fulgurite_shovel': ('Landslide', ['Digging sand or gravel brings down', 'the whole column above it.']),
    'fulgurite_hoe': ('Charged Soil', ['Right-click: lightning makes every crop', 'within 4 blocks grow. (5 s)']),
    'fulgurite_helmet': ('Static Sense', ['Monsters within 24 blocks glow.']),
    'fulgurite_chestplate': ('Storm Shield', ['2 absorption hearts that recharge', '5 s after you last took damage.']),
    'fulgurite_leggings': ('Swiftness', ['Run 20% faster.']),
    'fulgurite_boots': ('Double Jump', ['Press jump again in mid-air.']),
    'fulgurite_set': ('Full set: Static Charge', ['Running builds up static charge. Charged,',
                                                  'your next hit calls down a thunderbolt', '(+8 damage, zaps enemies around).']),

    'emberite_sword': ('Combustion', ['Sets enemies ablaze. Burning enemies', 'explode in flames when they die,',
                                      'which can chain through a whole crowd.']),
    'emberite_pickaxe': ('Molten Core', ['Mines a whole ore vein at once', '(up to 48 blocks). Sneak to mine one.']),
    'emberite_axe': ('Meteor Slam', ['Right-click: leap up, or plunge down in mid-air.', 'Landing unleashes a fiery shockwave:',
                                     'the higher the fall, the harder it hits. (5 s)']),
    'emberite_shovel': ('Kiln Touch', ['Right-click: smelts blocks in place, 3x3', '(sand to glass, cobblestone to stone...).',
                                       'Sneak for a single block.']),
    'emberite_hoe': ('Ember Scythe', ['A real weapon: charged swings reap', 'and ignite every enemy around the target.']),
    'emberite_helmet': ('Searing Gaze', ['Monsters you look at catch fire.']),
    'emberite_chestplate': ('Fireproof', ['Immune to fire and lava.', 'Melee attackers catch fire.']),
    'emberite_leggings': ('Blast Guard', ['Explosions deal half damage.']),
    'emberite_boots': ('Lava Walker', ['Lava crusts over beneath your feet.']),
    'emberite_set': ('Full set: Phoenix Rebirth', ['Once every 10 minutes a fatal blow instead', 'makes you rise from the flames with half',
                                                   'health, blasting fire at enemies around you.']),

    'umbrium_sword': ('Void Strike', ['Right-click: teleport behind the enemy you look at;', 'your next hit deals double damage.',
                                      'No enemy in sight: blink 8 blocks ahead. (5 s)']),
    'umbrium_pickaxe': ('Void Patterns', ['Sneak + right-click to switch the pattern:', 'X Cross, Strip-Mine Tunnel (1x2, 8 deep)',
                                          'or 3x3. Sneak while mining for one block.']),
    'umbrium_axe': ('Worldfeller', ['Fells the whole tree at once.', 'Sneak to chop a single log.']),
    'umbrium_shovel': ('Rift Burrow', ['Right-click: phase through up to 10 blocks', 'of wall, floor or ceiling. (3 s)']),
    'umbrium_hoe': ('Void Harvest', ['Right-click a ripe crop: harvests and', 'replants every ripe crop in 9x9.']),
    'umbrium_helmet': ('Void Sight', ['Night vision. Immune to Darkness', 'and Blindness.']),
    'umbrium_chestplate': ('Phase Shift', ['20% chance to phase through an attack.']),
    'umbrium_leggings': ('Void Step', ['Walk straight up full blocks.']),
    'umbrium_boots': ('Featherfall', ['No fall damage.']),
    'umbrium_set': ('Full set: Umbral Wings', ['Fly like in creative mode.']),
}

TRAITS = {
    'fulgurite': ('Trait: Momentum', ['Mining quickly stacks Haste (up to III).']),
    'emberite': ('Trait: Molten', ['Ores, raw metal, sand and crops drop', 'already smelted, with the furnace XP.']),
    'umbrium': ('Trait: Void Pocket', ['Everything you break goes straight', 'into your inventory, XP too.']),
}

LORE = {
    'cryolite_shard': 'Four shards make a Cryolite Crystal',
    'fulgurite_shard': 'Four shards make a Fulgurite Crystal',
    'cryolite_crystal': 'Crafts Cryolite tools and armour',
    'fulgurite_crystal': 'Crafts Fulgurite tools and armour',
    'raw_emberite': 'Smelt it into Emberite Scrap',
    'raw_umbrium': 'Smelt it into Umbrium Scrap',
    'emberite_scrap': '4 scrap + 4 gold ingots = 1 Emberite Ingot',
    'umbrium_scrap': '4 scrap + 4 ender pearls = 1 Umbrium Ingot',
    'emberite_ingot': 'Upgrades diamond gear at a Smithing Table',
    'umbrium_ingot': 'Upgrades diamond gear at a Smithing Table',
}


def lang():
    L = {'itemGroup.oreborn': 'Oreborn', 'effect.oreborn.frozen': 'Frozen', 'block.oreborn.crusted_lava': 'Crusted Lava'}
    for m in MATERIALS:
        t = TITLE[m]
        for block, base in ores(m):
            L[f'block.{NS}.{block}'] = ('Deepslate ' if base == 'deepslate' else '') + f'{t} Ore'
        L[f'block.{NS}.{storage(m)}'] = f'Block of {t}'
        L[f'item.{NS}.{drop_item(m)}'] = f'{t} Shard' if m in CRYSTALS else f'Raw {t}'
        L[f'item.{NS}.{main_item(m)}'] = f'{t} Crystal' if m in CRYSTALS else f'{t} Ingot'
        if m in INGOTS:
            L[f'item.{NS}.{m}_scrap'] = f'{t} Scrap'
        for g in GEAR:
            L[f'item.{NS}.{m}_{g}'] = f'{t} {g.capitalize()}'
    for key, (name, lines) in ABILITIES.items():
        L[f'tooltip.{NS}.{key}.name'] = ('' if key.endswith('_set') else '✦ ') + name
        for i, line in enumerate(lines, 1):
            L[f'tooltip.{NS}.{key}.line{i}'] = line
    for m, (name, lines) in TRAITS.items():
        L[f'tooltip.{NS}.trait.{m}.name'] = name
        for i, line in enumerate(lines, 1):
            L[f'tooltip.{NS}.trait.{m}.line{i}'] = line
    for item, text in LORE.items():
        L[f'lore.{NS}.{item}'] = text
    L.update({
        'item.oreborn.lightning_staff': 'Lightning Staff',
        'tooltip.oreborn.lightning_staff.name': '✦ Force Lightning',
        'tooltip.oreborn.lightning_staff.line1': 'Hold right-click to pour lightning out of the staff',
        'tooltip.oreborn.lightning_staff.line2': 'into up to 4 targets within 16 blocks (20 damage',
        'tooltip.oreborn.lightning_staff.line3': 'a second to the main one); it arcs on to nearby monsters.',
        'tooltip.oreborn.lightning_staff.line4': 'Bounces off glass, sets wood and leaves on fire and',
        'tooltip.oreborn.lightning_staff.line5': 'electrifies water: fish in it come out cooked.',
        'tooltip.oreborn.lightning_staff.line6': 'Drains half a hunger shank per second.',
        'message.oreborn.staff_exhausted': 'Too hungry to channel lightning',
        'death.attack.oreborn.force_lightning': '%1$s was fried by lightning',
        'death.attack.oreborn.force_lightning.player': '%1$s was fried by %2$s\'s force lightning',
        'death.attack.oreborn.force_lightning.item': '%1$s was fried by %2$s using %3$s',
        'tooltip.oreborn.mode': 'Pattern: %s',
        'mode.oreborn.single': 'Single Block',
        'mode.oreborn.cross': 'X Cross',
        'mode.oreborn.tunnel': 'Strip-Mine Tunnel',
        'mode.oreborn.excavate': 'Excavate 3x3',
        'message.oreborn.mode': 'Mining pattern: %s',
        'message.oreborn.radar': '⚡ %s ores detected',
        'message.oreborn.radar_none': 'No ores within %s blocks',
        'message.oreborn.no_crops': 'No crops nearby to charge',
        'message.oreborn.no_rift': 'Nothing to burrow through here',
        'message.oreborn.static_charge': '⚡ Static charge ready: your next hit calls down lightning!',
        'message.oreborn.ice_block': '❄ Ice Block!',
        'message.oreborn.phoenix': '✦ Phoenix Rebirth!',
        'death.attack.oreborn.frostbite': '%1$s was frozen solid',
        'death.attack.oreborn.frostbite.player': '%1$s was shattered by %2$s',
        'death.attack.oreborn.frostbite.item': '%1$s was shattered by %2$s using %3$s',
        'death.attack.oreborn.electrocution': '%1$s was electrocuted',
        'death.attack.oreborn.electrocution.player': '%1$s was struck down by %2$s\'s lightning',
        'death.attack.oreborn.electrocution.item': '%1$s was struck down by %2$s using %3$s',
        'death.attack.oreborn.combustion': '%1$s went up in flames',
        'death.attack.oreborn.combustion.player': '%1$s was blown to embers by %2$s',
        'death.attack.oreborn.combustion.item': '%1$s was blown to embers by %2$s using %3$s',
    })
    write_json(asset('lang', 'en_us.json'), dict(sorted(L.items())))


# =====================================================================================================
# GameTest structure (NBT)
# =====================================================================================================

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
    """blocks: list of (x, y, z, block_id)."""
    palette = []
    index = {}
    entries = []
    for (x, y, z, block) in blocks:
        if block not in index:
            index[block] = len(palette)
            palette.append(block)
        entries.append(nbt_named(9, 'pos', nbt_int_list([x, y, z])) + nbt_named(3, 'state', struct.pack('>i', index[block])))
    root = (
        nbt_named(9, 'size', nbt_int_list(list(size)))
        + nbt_named(9, 'entities', bytes([0]) + struct.pack('>i', 0))
        + nbt_named(9, 'blocks', nbt_compound_list(entries))
        + nbt_named(9, 'palette', nbt_compound_list([nbt_named(8, 'id', nbt_string(b)) for b in palette]))
        + nbt_named(3, 'DataVersion', struct.pack('>i', DATA_VERSION))
    )
    raw = bytes([10]) + nbt_string('') + root + b'\x00'
    path = data(NS, 'structure', name + '.nbt')
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with gzip.open(path, 'wb') as f:
        f.write(raw)
    global written
    written += 1


def test_structures():
    # a 16x12x16 box of air on a stone floor: every test builds what it needs
    floor = [(x, 0, z, 'minecraft:stone') for x in range(16) for z in range(16)]
    structure('test_arena', (16, 12, 16), floor)


def main():
    # start from a clean slate for generated folders so renamed files don't linger
    for folder in (asset('blockstates'), asset('models'), asset('items'), asset('equipment'), asset('lang'),
                   data(NS, 'loot_table'), data(NS, 'recipe'), data(NS, 'advancement'), data(NS, 'tags'),
                   data(NS, 'damage_type'), data(NS, 'worldgen'), data(NS, 'neoforge'), data(NS, 'structure'),
                   data('minecraft', 'tags'), data('c', 'tags')):
        if os.path.isdir(folder):
            shutil.rmtree(folder)
    models()
    loot()
    recipes()
    tags()
    damage_types()
    worldgen()
    lang()
    test_structures()
    print(f'wrote {written} files under {RES}')


if __name__ == '__main__':
    main()
