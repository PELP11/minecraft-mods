#!/usr/bin/env python3
"""Generates every Arsenal JSON resource (blockstates, models, client item definitions, loot, recipes,
recipe advancements, tags, damage types, uranium worldgen, biome modifier, lang) and the GameTest structure.
Pure Python, no dependencies.

The item lists here must stay in step with the Java enums (GunType, Caliber, Attachment, Ordnance, ModItems);
the `every_item_has_a_blueprint` game test catches the Java side, this file covers names and models.

Run from anywhere:  py tools/gen_data.py
"""
import gzip
import json
import os
import shutil
import struct
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import gen_sounds  # noqa: E402
import gun_models  # noqa: E402

HERE = os.path.dirname(os.path.abspath(__file__))
RES = os.path.normpath(os.path.join(HERE, '..', 'src', 'main', 'resources'))
ASSETS = os.path.join(RES, 'assets', 'arsenal')
DATA = os.path.join(RES, 'data')
NS = 'arsenal'
DATA_VERSION = 5023  # Minecraft 26.3 world version (used for the test structure)

# ---- catalogue -------------------------------------------------------------------------------------------

GUNS = [
    ('ak47', 'AK-47'),
    ('m4a1', 'M4A1 Carbine'),
    ('scar_h', 'FN SCAR-H'),
    ('aug', 'Steyr AUG'),
    ('glock17', 'Glock 17'),
    ('m1911', 'M1911'),
    ('m9', 'Beretta M9'),
    ('deagle', 'Desert Eagle'),
    ('barrett_m82', 'Barrett M82'),
    ('svd_dragunov', 'SVD Dragunov'),
    ('awp', 'AWP Magnum'),
    ('remington_870', 'Remington 870'),
    ('spas12', 'SPAS-12'),
    ('aa12', 'AA-12 Automatic Shotgun'),
    ('sawed_off', 'Sawed-Off Shotgun'),
    ('rpg7', 'RPG-7'),
    ('m32_launcher', 'M32 Grenade Launcher'),
    ('railgun', 'MK-IV Railgun'),
]

AMMO = [
    ('ammo_9mm', '9mm Round'),
    ('ammo_45acp', '.45 ACP Round'),
    ('ammo_50ae', '.50 AE Round'),
    ('ammo_556', '5.56mm Round'),
    ('ammo_762', '7.62mm Round'),
    ('ammo_338', '.338 Lapua Round'),
    ('ammo_50bmg', '.50 BMG Round'),
    ('shell_buckshot', '12 Gauge Buckshot'),
    ('shell_slug', '12 Gauge Slug'),
    ('grenade_40mm', '40mm Grenade'),
    ('rocket_round', 'PG-7V Rocket'),
    ('rocket_thermobaric', 'TBG-7V Thermobaric Rocket'),
    ('rail_slug', 'Tungsten Slug'),
]

ATTACHMENTS = [
    ('suppressor', 'Suppressor', 'Silences the shot: nearby mobs are not alerted.'),
    ('muzzle_brake', 'Muzzle Brake', 'Much tighter groups and far less kick.'),
    ('heavy_barrel', 'Heavy Barrel', 'Hits harder and further, but fires slower.'),
    ('red_dot_sight', 'Red Dot Sight', 'Faster aim and a steadier shot.'),
    ('acog_scope', 'ACOG Scope', '4x glass: crouch to aim down the sights.'),
    ('thermal_scope', 'Thermal Scope', '8x thermal: everything alive glows while you aim.'),
    ('extended_mag', 'Extended Magazine', 'Half again as many rounds.'),
    ('drum_mag', 'Drum Magazine', 'More than double the rounds, slower to change.'),
    ('quickdraw_mag', 'Quickdraw Magazine', 'Changes magazines almost twice as fast.'),
    ('laser_sight', 'Laser Sight', 'Considerably tighter groups from the hip.'),
    ('foregrip', 'Foregrip', 'Steadier fire and less muzzle climb.'),
    ('bipod', 'Bipod', 'Deploys when you crouch: near perfect accuracy.'),
]

ORDNANCE = [
    ('frag_grenade', 'Frag Grenade', 'Blast and splinters out to twelve blocks.'),
    ('incendiary_grenade', 'Incendiary Grenade', 'Sets everything around it alight.'),
    ('flashbang', 'Flashbang', 'Blinds and disorients without wounding.'),
    ('smoke_grenade', 'Smoke Grenade', 'Twenty seconds of cover nobody can see through.'),
    ('singularity_charge', 'Singularity Charge', 'Forms a black hole, then swallows the ground with it.'),
    ('thermobaric_bomb', 'Thermobaric Bomb', 'A firestorm that flattens and burns forty blocks of everything.'),
    ('ion_cannon_beacon', 'Ion Cannon Beacon', 'Paints a target for an orbital gun that walks the beam outwards.'),
    ('chemical_warhead', 'Chemical Warhead', 'Releases a nerve agent: the terrain survives, nothing breathing does.'),
]

# the F-14 and what it carries (ModItems: F14_TOMCAT, JET_STORES, CANNON_SHELLS, FLARE_CARTRIDGES)
JET = [
    ('f14_tomcat', 'F-14 Tomcat',
     'Swing-wing fleet fighter for two. Use it on open ground to park it, right-click to climb in, sneak-right-click '
     'with an empty hand to pack it up again.'),
    ('aim9_sidewinder', 'AIM-9 Sidewinder',
     'Heat-seeking missile: hold the target in the seeker circle until the tone locks, then fire. Flares can fool it.'),
    ('aim54_phoenix', 'AIM-54 Phoenix',
     'Long-range radar missile with a heavy warhead: a wider seeker and twice the reach, but slower to lock and turn.'),
    ('zuni_rocket', 'Zuni Rocket', 'Unguided 5-inch rocket for the LAU-10 pods. Hold fire to ripple them.'),
    ('mk82_bomb', 'Mk 82 Bomb', '500 lb free-fall bomb. The HUD circle shows where it will land.'),
    ('cannon_shells_20mm', '20mm Cannon Shells', "225 rounds for the jet's M61 Vulcan."),
    ('flare_cartridges', 'Flare Cartridges', 'Twelve decoy flares for the dispensers under the tail.'),
]

MATERIALS = [
    ('steel_blend', 'Crude Steel Blend'),
    ('steel_ingot', 'Steel Ingot'),
    ('brass_casing', 'Brass Casing'),
    ('bullet_tip', 'Bullet Tip'),
    ('propellant', 'Propellant Charge'),
    ('gun_barrel', 'Gun Barrel'),
    ('weapon_receiver', 'Weapon Receiver'),
    ('trigger_assembly', 'Trigger Assembly'),
    ('weapon_stock', 'Weapon Stock'),
    ('precision_parts', 'Precision Parts'),
    ('optical_lens', 'Optical Lens'),
    ('laser_module', 'Laser Module'),
    ('circuit_board', 'Circuit Board'),
    ('explosive_compound', 'Explosive Compound'),
    ('rocket_motor', 'Rocket Motor'),
    ('warhead_casing', 'Warhead Casing'),
    ('raw_uranium', 'Raw Uranium'),
    ('uranium_ingot', 'Uranium Ingot'),
    ('enriched_uranium', 'Enriched Uranium'),
    ('plutonium_core', 'Plutonium Core'),
]

SIMPLE_BLOCKS = [
    ('uranium_ore', 'Uranium Ore'),
    ('deepslate_uranium_ore', 'Deepslate Uranium Ore'),
    ('raw_uranium_block', 'Block of Raw Uranium'),
    ('uranium_block', 'Block of Uranium'),
    ('steel_block', 'Block of Steel'),
]

DAMAGE_TYPES = [
    ('bullet', 'was shot by %1$s', 0.1, None),
    ('headshot', 'was shot through the head by %1$s', 0.1, None),
    ('buckshot', 'was torn apart by %1$s', 0.1, None),
    ('railgun', 'was vaporised by %1$s', 0.1, None),
    ('shrapnel', 'was shredded by shrapnel from %1$s', 0.1, None),
    ('blast', 'was blown up by %1$s', 0.1, 'burning'),
    ('radiation', 'died of radiation sickness', 0.0, None),
    ('nerve_agent', 'choked on nerve agent', 0.0, None),
    ('singularity', 'was pulled apart by a singularity', 0.1, None),
    ('ion_beam', 'was cut in half by an orbital strike', 0.1, 'burning'),
    ('nuke', 'was at ground zero', 0.0, 'burning'),
    ('cannon', 'was cut down by cannon fire from %1$s', 0.1, None),
    ('missile', 'was hit by a missile fired by %1$s', 0.1, 'burning'),
]

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


def all_items():
    """Every registered item id, in registration order."""
    ids = [m[0] for m in MATERIALS]
    ids += ['weapon_workbench', 'tactical_nuke'] + [b[0] for b in SIMPLE_BLOCKS]
    ids += [a[0] for a in AMMO] + [g[0] for g in GUNS]
    ids += [a[0] for a in ATTACHMENTS] + [o[0] for o in ORDNANCE] + [j[0] for j in JET]
    return ids


BLOCK_ITEMS = ['weapon_workbench', 'tactical_nuke'] + [b[0] for b in SIMPLE_BLOCKS]


# =====================================================================================================
# models, blockstates and client item definitions
# =====================================================================================================

def item_def(name, model):
    write_json(asset('items', name + '.json'), {'model': {'type': 'minecraft:model', 'model': model}})


def model_3d(name):
    """A 3D gun or throwable (tools/gun_models.py): the cuboid model everywhere, in hand, in the GUI and on the
    ground; guns with a loaded/empty look get a second model and a conditional item definition."""
    global written
    models_dir, items_dir = asset('models', 'item'), asset('items')
    os.makedirs(models_dir, exist_ok=True)
    os.makedirs(items_dir, exist_ok=True)
    written += gun_models.write_models(name, models_dir, items_dir)


def flat_item(name):
    write_json(asset('models', 'item', name + '.json'),
               {'parent': 'minecraft:item/generated', 'textures': {'layer0': rl('item/' + name)}})
    item_def(name, rl('item/' + name))


def cube_all(name, texture=None):
    write_json(asset('models', 'block', name + '.json'),
               {'parent': 'minecraft:block/cube_all', 'textures': {'all': rl('block/' + (texture or name))}})
    write_json(asset('blockstates', name + '.json'), {'variants': {'': {'model': rl('block/' + name)}}})
    write_json(asset('models', 'item', name + '.json'), {'parent': rl('block/' + name)})
    item_def(name, rl('block/' + name))


def models():
    for name, _ in MATERIALS:
        flat_item(name)
    for name, _ in AMMO:
        model_3d(name) if name in gun_models.MODELS else flat_item(name)
    for name, _ in GUNS:
        model_3d(name) if name in gun_models.MODELS else flat_item(name)
    for name, _, _ in ATTACHMENTS:
        flat_item(name)
    for name, _, _ in ORDNANCE:
        model_3d(name) if name in gun_models.MODELS else flat_item(name)
    for name, _, _ in JET:
        # the stores are 3D (tools/gun_models.py); the jet's icon is rendered from its mesh (tools/vehicle_models.py)
        model_3d(name) if name in gun_models.MODELS else flat_item(name)
    for name, _ in SIMPLE_BLOCKS:
        cube_all(name)

    # Weapon Workbench: a directional bench with its own front.
    write_json(asset('models', 'block', 'weapon_workbench.json'), {
        'parent': 'minecraft:block/orientable',
        'textures': {
            'top': rl('block/weapon_workbench_top'),
            'front': rl('block/weapon_workbench_front'),
            'side': rl('block/weapon_workbench_side'),
        },
    })
    write_json(asset('blockstates', 'weapon_workbench.json'), {'variants': {
        'facing=north': {'model': rl('block/weapon_workbench')},
        'facing=east': {'model': rl('block/weapon_workbench'), 'y': 90},
        'facing=south': {'model': rl('block/weapon_workbench'), 'y': 180},
        'facing=west': {'model': rl('block/weapon_workbench'), 'y': 270},
    }})
    write_json(asset('models', 'item', 'weapon_workbench.json'), {'parent': rl('block/weapon_workbench')})
    item_def('weapon_workbench', rl('block/weapon_workbench'))

    # The nuke: the armed model swaps in a lit warning stripe.
    for state, suffix in (('tactical_nuke', ''), ('tactical_nuke_armed', '_armed')):
        write_json(asset('models', 'block', state + '.json'), {
            'parent': 'minecraft:block/cube_bottom_top',
            'textures': {
                'top': rl('block/tactical_nuke_top'),
                'bottom': rl('block/tactical_nuke_top'),
                'side': rl('block/tactical_nuke_side' + suffix),
            },
        })
    write_json(asset('blockstates', 'tactical_nuke.json'), {'variants': {
        'armed=false': {'model': rl('block/tactical_nuke')},
        'armed=true': {'model': rl('block/tactical_nuke_armed')},
    }})
    write_json(asset('models', 'item', 'tactical_nuke.json'), {'parent': rl('block/tactical_nuke')})
    item_def('tactical_nuke', rl('block/tactical_nuke'))


# =====================================================================================================
# loot tables
# =====================================================================================================

def self_drop(name):
    write_json(data(NS, 'loot_table', 'blocks', name + '.json'), {
        'type': 'minecraft:block',
        'pools': [{
            'rolls': 1,
            'entries': [{'type': 'minecraft:item', 'name': rl(name)}],
            'condition': {'type': 'minecraft:survives_explosion'},
        }],
        'random_sequence': f'{NS}:blocks/{name}',
    })


def ore_loot(block, drop, low, high):
    write_json(data(NS, 'loot_table', 'blocks', block + '.json'), {
        'type': 'minecraft:block',
        'pools': [{
            'rolls': 1,
            'entries': [{
                'type': 'minecraft:alternatives',
                'children': [
                    {'type': 'minecraft:item', 'condition': 'minecraft:tool/can_silk_touch', 'name': rl(block)},
                    {
                        'type': 'minecraft:item',
                        'name': rl(drop),
                        'modifier': [
                            {'type': 'minecraft:set_count',
                             'count': {'type': 'minecraft:uniform', 'min': low, 'max': high}},
                            {'type': 'minecraft:apply_bonus', 'enchantment': 'minecraft:fortune',
                             'formula': 'minecraft:ore_drops'},
                            {'type': 'minecraft:explosion_decay'},
                        ],
                    },
                ],
            }],
        }],
        'random_sequence': f'{NS}:blocks/{block}',
    })


def loot():
    ore_loot('uranium_ore', 'raw_uranium', 1, 2)
    ore_loot('deepslate_uranium_ore', 'raw_uranium', 1, 2)
    for name in ('weapon_workbench', 'raw_uranium_block', 'uranium_block', 'steel_block'):
        self_drop(name)
    # A nuke that is mined comes back in one piece; one that is armed has already left the world.
    self_drop('tactical_nuke')


# =====================================================================================================
# recipes (only the handful that belong on a vanilla bench; everything else is a workbench blueprint)
# =====================================================================================================

def shaped(name, pattern, key, result, count=1, category='misc'):
    write_json(data(NS, 'recipe', name + '.json'), {
        'type': 'minecraft:crafting_shaped',
        'category': category,
        'key': key,
        'pattern': pattern,
        'result': {'id': result, 'count': count},
    })


def shapeless(name, ingredients, result, count=1, category='misc'):
    write_json(data(NS, 'recipe', name + '.json'), {
        'type': 'minecraft:crafting_shapeless',
        'category': category,
        'ingredients': ingredients,
        'result': {'id': result, 'count': count},
    })


def cooking(name, kind, ingredient, result, xp, time):
    write_json(data(NS, 'recipe', name + '.json'), {
        'type': 'minecraft:' + kind,
        'category': 'misc',
        'cookingtime': time,
        'experience': xp,
        'ingredient': ingredient,
        'result': {'id': result},
    })


def storage(item, block, name):
    shaped(name + '_block', ['###', '###', '###'], {'#': rl(item)}, rl(block), category='building')
    shapeless(name + '_from_block', [rl(block)], rl(item), count=9)


def recipes():
    shaped('weapon_workbench', ['III', 'ICI', 'SSS'],
           {'I': 'minecraft:iron_ingot', 'C': 'minecraft:crafting_table', 'S': 'minecraft:smooth_stone'},
           rl('weapon_workbench'), category='equipment')
    shapeless('steel_blend', ['minecraft:iron_ingot', 'minecraft:coal', 'minecraft:coal'], rl('steel_blend'))
    cooking('steel_ingot_from_smelting', 'smelting', rl('steel_blend'), rl('steel_ingot'), 0.8, 200)
    cooking('steel_ingot_from_blasting', 'blasting', rl('steel_blend'), rl('steel_ingot'), 0.8, 100)
    cooking('uranium_ingot_from_smelting', 'smelting', rl('raw_uranium'), rl('uranium_ingot'), 1.2, 200)
    cooking('uranium_ingot_from_blasting', 'blasting', rl('raw_uranium'), rl('uranium_ingot'), 1.2, 100)
    storage('steel_ingot', 'steel_block', 'steel')
    storage('uranium_ingot', 'uranium_block', 'uranium')
    storage('raw_uranium', 'raw_uranium_block', 'raw_uranium')

    # Recipe-book unlocks: the bench as soon as you have iron, the rest once uranium or steel turns up.
    advancement('weapon_workbench', ['minecraft:iron_ingot'], [rl('weapon_workbench')])
    advancement('steel', ['minecraft:iron_ingot', rl('steel_blend'), rl('steel_ingot')],
                [rl('steel_blend'), rl('steel_ingot_from_smelting'), rl('steel_ingot_from_blasting'),
                 rl('steel_block'), rl('steel_from_block')])
    advancement('uranium', [rl('raw_uranium'), rl('uranium_ingot'), rl('uranium_ore'), rl('deepslate_uranium_ore')],
                [rl('uranium_ingot_from_smelting'), rl('uranium_ingot_from_blasting'), rl('uranium_block'),
                 rl('uranium_from_block'), rl('raw_uranium_block'), rl('raw_uranium_from_block')])


def advancement(name, trigger_items, unlocked):
    write_json(data(NS, 'advancement', 'recipes', name + '.json'), {
        'parent': 'minecraft:recipes/root',
        'criteria': {
            'has_material': {
                'trigger': 'minecraft:inventory_changed',
                'conditions': {'items': [{'items': trigger_items}]},
            },
        },
        'requirements': [['has_material']],
        'rewards': {'recipes': unlocked},
    })


# =====================================================================================================
# tags
# =====================================================================================================

def tag(namespace, kind, name, values):
    write_json(data(namespace, 'tags', kind, name + '.json'), {'values': values})


def tags():
    ores = [rl('uranium_ore'), rl('deepslate_uranium_ore')]
    blocks = ores + [rl('raw_uranium_block'), rl('uranium_block'), rl('steel_block'), rl('weapon_workbench')]
    tag('minecraft', 'block', 'mineable/pickaxe', blocks + [rl('tactical_nuke')])
    tag('minecraft', 'block', 'needs_iron_tool', [rl('steel_block'), rl('weapon_workbench')])
    tag('minecraft', 'block', 'needs_diamond_tool', ores + [rl('raw_uranium_block'), rl('uranium_block')])

    tag('c', 'block', 'ores', ores)
    tag('c', 'block', 'ores/uranium', ores)
    tag('c', 'block', 'storage_blocks', [rl('raw_uranium_block'), rl('uranium_block'), rl('steel_block')])
    tag('c', 'item', 'ingots/steel', [rl('steel_ingot')])
    tag('c', 'item', 'ingots/uranium', [rl('uranium_ingot')])
    tag('c', 'item', 'raw_materials/uranium', [rl('raw_uranium')])

    # Anything a gun can fire, for other mods and for datapacks.
    tag(NS, 'item', 'ammunition', [rl(a[0]) for a in AMMO])
    tag(NS, 'item', 'firearms', [rl(g[0]) for g in GUNS])
    tag(NS, 'item', 'attachments', [rl(a[0]) for a in ATTACHMENTS])
    tag(NS, 'item', 'ordnance', [rl(o[0]) for o in ORDNANCE])
    tag(NS, 'item', 'jet_stores', [rl(j[0]) for j in JET if j[0] != 'f14_tomcat'])


# =====================================================================================================
# damage types
# =====================================================================================================

def damage_types():
    for name, _, exhaustion, effects in DAMAGE_TYPES:
        obj = {
            'exhaustion': exhaustion,
            'message_id': f'{NS}.{name}',
            'scaling': 'when_caused_by_living_non_player',
        }
        if effects:
            obj['effects'] = effects
        write_json(data(NS, 'damage_type', name + '.json'), obj)


# =====================================================================================================
# worldgen: uranium, about as rare as diamond and only deep down
# =====================================================================================================

def ore_targets():
    return [
        {
            'state': rl('uranium_ore'),
            'target': {
                'predicate_type': 'minecraft:any_of',
                'rules': [
                    {'predicate_type': 'minecraft:all_of', 'rules': [
                        {'predicate_type': 'minecraft:tag_match',
                         'tag': 'minecraft:height_specific_ore_replaceables'},
                        {'predicate_type': 'minecraft:height_match', 'min_inclusive': 0, 'max_inclusive': 2031},
                    ]},
                    {'predicate_type': 'minecraft:all_of', 'rules': [
                        {'predicate_type': 'minecraft:not', 'rule': {
                            'predicate_type': 'minecraft:tag_match',
                            'tag': 'minecraft:height_specific_ore_replaceables'}},
                        {'predicate_type': 'minecraft:tag_match', 'tag': 'minecraft:stone_ore_replaceables'},
                    ]},
                ],
            },
        },
        {
            'state': rl('deepslate_uranium_ore'),
            'target': {
                'predicate_type': 'minecraft:any_of',
                'rules': [
                    {'predicate_type': 'minecraft:all_of', 'rules': [
                        {'predicate_type': 'minecraft:tag_match',
                         'tag': 'minecraft:height_specific_ore_replaceables'},
                        {'predicate_type': 'minecraft:height_match', 'min_inclusive': -2032, 'max_inclusive': 8},
                    ]},
                    {'predicate_type': 'minecraft:all_of', 'rules': [
                        {'predicate_type': 'minecraft:not', 'rule': {
                            'predicate_type': 'minecraft:tag_match',
                            'tag': 'minecraft:height_specific_ore_replaceables'}},
                        {'predicate_type': 'minecraft:tag_match', 'tag': 'minecraft:deepslate_ore_replaceables'},
                    ]},
                ],
            },
        },
    ]


def worldgen():
    write_json(data(NS, 'worldgen', 'feature', 'ore_uranium.json'), {
        'type': 'minecraft:ore',
        'discard_chance_on_air_exposure': 0.6,
        'size': 4,
        'targets': ore_targets(),
    })
    write_json(data(NS, 'worldgen', 'feature', 'ore_uranium_buried.json'), {
        'type': 'minecraft:ore',
        'discard_chance_on_air_exposure': 1.0,
        'size': 6,
        'targets': ore_targets(),
    })
    write_json(data(NS, 'worldgen', 'placed_feature', 'ore_uranium.json'), {
        'feature': rl('ore_uranium'),
        'placement': [
            {'type': 'minecraft:count', 'count': 3},
            {'type': 'minecraft:in_square'},
            {'type': 'minecraft:height_range', 'height': {
                'type': 'minecraft:trapezoid',
                'min_inclusive': {'absolute': -64},
                'max_inclusive': {'absolute': 16},
            }},
            {'type': 'minecraft:biome'},
        ],
    })
    write_json(data(NS, 'worldgen', 'placed_feature', 'ore_uranium_buried.json'), {
        'feature': rl('ore_uranium_buried'),
        'placement': [
            {'type': 'minecraft:count', 'count': 2},
            {'type': 'minecraft:in_square'},
            {'type': 'minecraft:height_range', 'height': {
                'type': 'minecraft:uniform',
                'min_inclusive': {'absolute': -64},
                'max_inclusive': {'absolute': -8},
            }},
            {'type': 'minecraft:biome'},
        ],
    })
    write_json(data(NS, 'neoforge', 'biome_modifier', 'ore_uranium.json'), {
        'type': 'neoforge:add_features',
        'biomes': '#minecraft:is_overworld',
        'features': [rl('ore_uranium'), rl('ore_uranium_buried')],
        'step': 'underground_ores',
    })


# =====================================================================================================
# language
# =====================================================================================================

def lang():
    out = {
        'itemGroup.arsenal': 'Arsenal',
        'key.arsenal.reload': 'Reload weapon',
        'key.categories.arsenal': 'Arsenal',

        'block.arsenal.weapon_workbench': 'Weapon Workbench',
        'block.arsenal.tactical_nuke': 'Tactical Nuke',
        'container.arsenal.weapon_workbench': 'Weapon Workbench',

        'effect.arsenal.radiation': 'Radiation',
        'effect.arsenal.nerve_agent': 'Nerve Agent',

        'entity.arsenal.ordnance': 'Ordnance',
        'entity.arsenal.f14_tomcat': 'F-14 Tomcat',

        'gui.arsenal.build': 'Build',
        'gui.arsenal.build.hint': 'Builds eight at once.',
        'gui.arsenal.needs': 'Materials',
        'gui.arsenal.modify': 'Fit attachments',
        'gui.arsenal.have': 'You have %s of %s',
        'gui.arsenal.category.materials': 'Components',
        'gui.arsenal.category.materials.short': 'MAT',
        'gui.arsenal.category.ammo': 'Ammunition',
        'gui.arsenal.category.ammo.short': 'AMM',
        'gui.arsenal.category.attachments': 'Attachments',
        'gui.arsenal.category.attachments.short': 'ATT',
        'gui.arsenal.category.weapons': 'Weapons',
        'gui.arsenal.category.weapons.short': 'GUN',
        'gui.arsenal.category.ordnance': 'Ordnance',
        'gui.arsenal.category.ordnance.short': 'ORD',
        'gui.arsenal.category.aircraft': 'Aircraft and stores',
        'gui.arsenal.category.aircraft.short': 'AIR',
        'gui.arsenal.slot.muzzle': 'Muzzle',
        'gui.arsenal.slot.optic': 'Optic',
        'gui.arsenal.slot.magazine': 'Magazine',
        'gui.arsenal.slot.underbarrel': 'Underbarrel',

        'hud.arsenal.reloading': 'RELOADING',
        'message.arsenal.nuke_armed': 'WARHEAD ARMED - %s SECONDS',
        'message.arsenal.f14_controls': 'Mouse steers · W/S throttle (hold W for afterburner) · A/D roll · '
                                        'LMB gun · RMB fire · V weapon · B flares · G gear · Space brakes · '
                                        'hold Shift to eject',
        'message.arsenal.f14_no_room': 'Not enough room to park the F-14 here',

        'key.arsenal.jet_gear': 'Jet: landing gear',
        'key.arsenal.jet_weapon': 'Jet: next weapon',
        'key.arsenal.jet_flares': 'Jet: flares',
        'key.arsenal.jet_canopy': 'Jet: canopy',
        'key.arsenal.jet_free_look': 'Jet: free look',

        'tooltip.arsenal.ammo': 'Loaded: %s  %s',
        'tooltip.arsenal.stats': 'Damage %s  ·  %s RPM  ·  %s blocks',
        'tooltip.arsenal.action': 'Action: %s',
        'tooltip.arsenal.action.semi': 'Semi-automatic',
        'tooltip.arsenal.action.auto': 'Fully automatic',
        'tooltip.arsenal.action.pump': 'Pump-action',
        'tooltip.arsenal.action.bolt': 'Bolt-action',
        'tooltip.arsenal.reload_hint': 'Right-click to fire, R to reload, sneak+R to change ammunition.',
        'tooltip.arsenal.slot': 'Fits the %s slot',
        'tooltip.arsenal.fuse': 'Fuse: %s',
        'tooltip.arsenal.f14_airframe': 'Airframe: %s%%',
        'tooltip.arsenal.jet_store': 'Right-click a parked F-14 to load it.',

        'death.attack.arsenal.radiation': '%1$s died of radiation sickness',
        'death.attack.arsenal.nerve_agent': '%1$s choked on nerve agent',
    }
    for name, message, _, _ in DAMAGE_TYPES:
        if name in ('radiation', 'nerve_agent'):
            continue
        out[f'death.attack.{NS}.{name}'] = '%1$s ' + message.replace('%1$s', '%2$s')
        out[f'death.attack.{NS}.{name}.player'] = '%1$s ' + message.replace('%1$s', '%2$s')

    for name, title in MATERIALS + AMMO + GUNS:
        out[f'item.{NS}.{name}'] = title
    for name, title, description in ATTACHMENTS + ORDNANCE + JET:
        out[f'item.{NS}.{name}'] = title
        out[f'tooltip.{NS}.{name}'] = description
    for name, title in SIMPLE_BLOCKS:
        out[f'block.{NS}.{name}'] = title
    # subtitles for the synthesised sounds (tools/gen_sounds.py owns the sound catalogue)
    out.update(gen_sounds.SUBTITLES)

    write_json(asset('lang', 'en_us.json'), out)


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
        entries.append(nbt_named(9, 'pos', nbt_int_list([x, y, z]))
                       + nbt_named(3, 'state', struct.pack('>i', index[block])))
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
    floor = [(x, 0, z, 'minecraft:stone') for x in range(16) for z in range(16)]
    structure('test_arena', (16, 14, 16), floor)
    # room to park an F-14 (19 m long, 19.5 m span with the wings spread)
    big = [(x, 0, z, 'minecraft:stone') for x in range(40) for z in range(40)]
    structure('jet_arena', (40, 12, 40), big)


def main():
    for folder in (asset('blockstates'), asset('models'), asset('items'), asset('lang'),
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
    # the muzzle positions measured on the 3D models, for the client's muzzle flash and tracers
    gun_models.write_java(os.path.normpath(os.path.join(HERE, '..', 'src', 'main', 'java', 'com', 'afjan', 'arsenal',
                                                        'client', 'GunViewmodels.java')))
    print(f'{written} files written; {len(all_items())} items in the catalogue')


if __name__ == '__main__':
    main()
