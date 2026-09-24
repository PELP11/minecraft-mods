#!/usr/bin/env python3
"""Generates all Juicer JSON resources (models, blockstates, loot, recipes, tags, worldgen, lang)
and the two GameTest structures. Pure Python, no dependencies.

Run from anywhere:  py tools/gen_data.py
"""
import gzip
import json
import os
import shutil
import struct

HERE = os.path.dirname(os.path.abspath(__file__))
RES = os.path.normpath(os.path.join(HERE, '..', 'src', 'main', 'resources'))
ASSETS = os.path.join(RES, 'assets', 'juicer')
DATA = os.path.join(RES, 'data')
NS = 'juicer'

FRUITS = ['starfruit', 'dragonfruit', 'pomegranate', 'orange', 'lime']
TITLES = {'starfruit': 'Starfruit', 'dragonfruit': 'Dragonfruit', 'pomegranate': 'Pomegranate',
          'orange': 'Orange', 'lime': 'Lime'}
FACINGS = {'north': 0, 'east': 90, 'south': 180, 'west': 270}
DATA_VERSION = 5023  # Minecraft 26.3 world version (used for the test structures)

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


def face(uv, texture, cull=None, rotation=None):
    f = {'uv': uv, 'texture': texture}
    if cull:
        f['cullface'] = cull
    if rotation:
        f['rotation'] = rotation
    return f


def box(frm, to, faces, name=None, rotation=None):
    e = {'from': frm, 'to': to, 'faces': faces}
    if name:
        e = {'name': name, **e}
    if rotation:
        e['rotation'] = rotation
    return e


# =====================================================================================================
# Block & item models
# =====================================================================================================

def item_def(name, model):
    write_json(asset('items', name + '.json'), {'model': {'type': 'minecraft:model', 'model': model}})


def generated_item(name, texture):
    write_json(asset('models', 'item', name + '.json'),
               {'parent': 'minecraft:item/generated', 'textures': {'layer0': texture}})
    item_def(name, f'{NS}:item/{name}')


def fruit_models():
    for f in FRUITS:
        generated_item(f, f'{NS}:item/{f}')
        generated_item(f + '_juice', f'{NS}:item/{f}_juice')

        # sapling
        write_json(asset('models', 'block', f + '_sapling.json'),
                   {'parent': 'minecraft:block/cross', 'textures': {'cross': f'{NS}:block/{f}_sapling'}})
        write_json(asset('blockstates', f + '_sapling.json'),
                   {'variants': {'': {'model': f'{NS}:block/{f}_sapling'}}})
        generated_item(f + '_sapling', f'{NS}:block/{f}_sapling')

        # leaves: one model per growth stage
        for age in range(4):
            write_json(asset('models', 'block', f'{f}_leaves_{age}.json'),
                       {'parent': 'minecraft:block/cube_all', 'textures': {'all': f'{NS}:block/{f}_leaves_{age}'}})
        write_json(asset('blockstates', f + '_leaves.json'),
                   {'variants': {f'age={a}': {'model': f'{NS}:block/{f}_leaves_{a}'} for a in range(4)}})
        item_def(f + '_leaves', f'{NS}:block/{f}_leaves_3')


def mixer_models():
    side, front, top, bottom = '#side', '#front', '#top', '#bottom'
    glass, lid = '#glass', '#lid'
    elements = [
        box([2, 0, 2], [14, 6, 14], {
            'north': face([2, 10, 14, 16], front),
            'east': face([2, 10, 14, 16], side),
            'south': face([2, 10, 14, 16], side),
            'west': face([2, 10, 14, 16], side),
            'up': face([2, 2, 14, 14], top),
            'down': face([2, 2, 14, 14], bottom, 'down'),
        }, 'motor_base'),
        box([3.5, 6, 3.5], [12.5, 7, 12.5], {
            d: face([3.5, 0, 12.5, 1], lid) for d in ('north', 'east', 'south', 'west')
        } | {'up': face([3.5, 3.5, 12.5, 12.5], lid)}, 'jar_collar'),
        # four thin glass walls so the far side of the jar is visible through the near side
        box([4, 7, 4], [12, 15, 4.5], {
            'north': face([0, 0, 8, 8], glass), 'south': face([0, 0, 8, 8], glass),
            'up': face([0, 0, 8, 0.5], glass), 'west': face([0, 0, 0.5, 8], glass), 'east': face([0, 0, 0.5, 8], glass),
        }, 'glass_north'),
        box([4, 7, 11.5], [12, 15, 12], {
            'north': face([0, 0, 8, 8], glass), 'south': face([0, 0, 8, 8], glass),
            'up': face([0, 0, 8, 0.5], glass), 'west': face([0, 0, 0.5, 8], glass), 'east': face([0, 0, 0.5, 8], glass),
        }, 'glass_south'),
        box([4, 7, 4.5], [4.5, 15, 11.5], {
            'west': face([0, 0, 8, 8], glass), 'east': face([0, 0, 8, 8], glass), 'up': face([0, 0, 0.5, 7], glass),
        }, 'glass_west'),
        box([11.5, 7, 4.5], [12, 15, 11.5], {
            'west': face([0, 0, 8, 8], glass), 'east': face([0, 0, 8, 8], glass), 'up': face([0, 0, 0.5, 7], glass),
        }, 'glass_east'),
        box([3.5, 15, 3.5], [12.5, 16, 12.5], {
            d: face([3.5, 0, 12.5, 1], lid) for d in ('north', 'east', 'south', 'west')
        } | {'up': face([3.5, 3.5, 12.5, 12.5], lid), 'down': face([3.5, 3.5, 12.5, 12.5], lid)}, 'lid'),
        box([7, 16, 7], [9, 17, 9], {
            d: face([6, 6, 8, 7], lid) for d in ('north', 'east', 'south', 'west')
        } | {'up': face([6, 6, 8, 8], lid)}, 'lid_knob'),
    ]
    write_json(asset('models', 'block', 'mixer.json'), {
        'parent': 'minecraft:block/block',
        'textures': {
            'particle': f'{NS}:block/mixer_side', 'side': f'{NS}:block/mixer_side', 'front': f'{NS}:block/mixer_front',
            'top': f'{NS}:block/mixer_top', 'bottom': f'{NS}:block/mixer_bottom',
            'glass': f'{NS}:block/mixer_glass', 'lid': f'{NS}:block/mixer_lid',
        },
        'elements': elements,
    })

    for name, texture in (('mixer_blades', 'mixer_blades'), ('mixer_blades_spinning', 'mixer_blades_spinning')):
        write_json(asset('models', 'block', name + '.json'), {
            'textures': {'particle': f'{NS}:block/mixer_side', 'blades': f'{NS}:block/{texture}'},
            'elements': [
                box([4.5, 7.5, 4.5], [11.5, 7.5, 11.5], {
                    'up': face([0, 0, 16, 16], '#blades'), 'down': face([0, 0, 16, 16], '#blades'),
                }, 'blades'),
            ],
        })

    heights = [9.0, 11.0, 13.0, 14.75]
    for level in range(1, 5):
        h = heights[level - 1]
        write_json(asset('models', 'block', f'mixer_liquid_{level}.json'), {
            'textures': {'particle': '#liquid'},
            'elements': [box([4.5, 7, 4.5], [11.5, h, 11.5], {
                'up': face([4.5, 4.5, 11.5, 11.5], '#liquid'),
                'north': face([4.5, 16 - h, 11.5, 9], '#liquid'), 'south': face([4.5, 16 - h, 11.5, 9], '#liquid'),
                'west': face([4.5, 16 - h, 11.5, 9], '#liquid'), 'east': face([4.5, 16 - h, 11.5, 9], '#liquid'),
            }, 'concentrate')],
        })
        for f in FRUITS:
            write_json(asset('models', 'block', f'mixer_liquid_{f}_{level}.json'), {
                'parent': f'{NS}:block/mixer_liquid_{level}',
                'textures': {'liquid': f'{NS}:block/{f}_concentrate'},
            })

    parts = []
    for facing, rot in FACINGS.items():
        apply = {'model': f'{NS}:block/mixer'}
        if rot:
            apply['y'] = rot
        parts.append({'when': {'facing': facing}, 'apply': apply})
    parts.append({'when': {'active': 'false'}, 'apply': {'model': f'{NS}:block/mixer_blades'}})
    parts.append({'when': {'active': 'true'}, 'apply': {'model': f'{NS}:block/mixer_blades_spinning'}})
    for f in FRUITS:
        for level in range(1, 5):
            parts.append({'when': {'contents': f, 'level': str(level)},
                          'apply': {'model': f'{NS}:block/mixer_liquid_{f}_{level}'}})
    write_json(asset('blockstates', 'mixer.json'), {'multipart': parts})

    write_json(asset('items', 'mixer.json'), {'model': {'type': 'minecraft:composite', 'models': [
        {'type': 'minecraft:model', 'model': f'{NS}:block/mixer'},
        {'type': 'minecraft:model', 'model': f'{NS}:block/mixer_blades'},
    ]}})


def infuser_models():
    side, front, inner, rim, stand, tap = '#side', '#front', '#inner', '#rim', '#stand', '#tap'
    wall_uv = [1, 6, 15, 16]
    elements = [
        box([2, 0, 2], [14, 5, 14], {
            'north': face([2, 11, 14, 16], stand), 'east': face([2, 11, 14, 16], stand),
            'south': face([2, 11, 14, 16], stand), 'west': face([2, 11, 14, 16], stand),
            'down': face([2, 2, 14, 14], stand, 'down'),
        }, 'stand'),
        box([1, 5, 1], [15, 15, 2.5], {
            'north': face(wall_uv, front), 'south': face(wall_uv, inner),
            'west': face([1, 6, 2.5, 16], side), 'east': face([13.5, 6, 15, 16], side),
            'up': face([1, 1, 15, 2.5], rim), 'down': face([1, 1, 15, 2.5], side),
        }, 'wall_north'),
        box([1, 5, 13.5], [15, 15, 15], {
            'south': face(wall_uv, side), 'north': face(wall_uv, inner),
            'west': face([13.5, 6, 15, 16], side), 'east': face([1, 6, 2.5, 16], side),
            'up': face([1, 13.5, 15, 15], rim), 'down': face([1, 13.5, 15, 15], side),
        }, 'wall_south'),
        box([1, 5, 2.5], [2.5, 15, 13.5], {
            'west': face([2.5, 6, 13.5, 16], side), 'east': face([2.5, 6, 13.5, 16], inner),
            'up': face([1, 2.5, 2.5, 13.5], rim), 'down': face([1, 2.5, 2.5, 13.5], side),
        }, 'wall_west'),
        box([13.5, 5, 2.5], [15, 15, 13.5], {
            'east': face([2.5, 6, 13.5, 16], side), 'west': face([2.5, 6, 13.5, 16], inner),
            'up': face([13.5, 2.5, 15, 13.5], rim), 'down': face([13.5, 2.5, 15, 13.5], side),
        }, 'wall_east'),
        box([2.5, 5, 2.5], [13.5, 6, 13.5], {
            'up': face([2.5, 2.5, 13.5, 13.5], inner),
        }, 'tub_floor'),
        box([7, 7, 0], [9, 9, 1], {
            'north': face([0, 0, 2, 2], tap), 'up': face([0, 0, 2, 1], tap), 'down': face([0, 0, 2, 1], tap),
            'west': face([0, 0, 1, 2], tap), 'east': face([0, 0, 1, 2], tap),
        }, 'tap_pipe'),
        box([7.25, 5.75, 0.25], [8.75, 7, 0.75], {
            'north': face([0, 0, 1.5, 1.25], tap), 'south': face([0, 0, 1.5, 1.25], tap),
            'west': face([0, 0, 0.5, 1.25], tap), 'east': face([0, 0, 0.5, 1.25], tap), 'down': face([0, 0, 1.5, 0.5], tap),
        }, 'tap_spout'),
        box([7.5, 9, 0.25], [8.5, 10.5, 0.75], {
            'north': face([0, 0, 1, 1.5], tap), 'south': face([0, 0, 1, 1.5], tap), 'up': face([0, 0, 1, 0.5], tap),
            'west': face([0, 0, 0.5, 1.5], tap), 'east': face([0, 0, 0.5, 1.5], tap),
        }, 'tap_handle'),
        box([7.5, 7, 9.5], [8.5, 20, 10.5], {
            d: face([7, 0, 8, 13], '#paddle') for d in ('north', 'east', 'south', 'west')
        } | {'up': face([7, 7, 8, 8], '#paddle')}, 'stirring_paddle',
            rotation={'origin': [8, 8, 10], 'axis': 'x', 'angle': 22.5}),
    ]
    write_json(asset('models', 'block', 'infuser.json'), {
        'parent': 'minecraft:block/block',
        'textures': {
            'particle': f'{NS}:block/infuser_side', 'side': f'{NS}:block/infuser_side',
            'front': f'{NS}:block/infuser_front', 'inner': f'{NS}:block/infuser_inner',
            'rim': f'{NS}:block/infuser_rim', 'stand': f'{NS}:block/infuser_stand',
            'tap': f'{NS}:block/infuser_tap', 'paddle': 'minecraft:block/stripped_oak_log',
        },
        'elements': elements,
    })

    heights = [8.0, 10.0, 12.0, 14.5]
    for level in range(1, 5):
        h = heights[level - 1]
        write_json(asset('models', 'block', f'infuser_liquid_{level}.json'), {
            'textures': {'particle': '#liquid'},
            'elements': [box([2.5, 6, 2.5], [13.5, h, 13.5], {
                'up': face([2.5, 2.5, 13.5, 13.5], '#liquid'),
            }, 'concentrate')],
        })
        for f in FRUITS:
            write_json(asset('models', 'block', f'infuser_liquid_{f}_{level}.json'), {
                'parent': f'{NS}:block/infuser_liquid_{level}',
                'textures': {'liquid': f'{NS}:block/{f}_concentrate'},
            })

    parts = []
    for facing, rot in FACINGS.items():
        apply = {'model': f'{NS}:block/infuser'}
        if rot:
            apply['y'] = rot
        parts.append({'when': {'facing': facing}, 'apply': apply})
    for f in FRUITS:
        for level in range(1, 5):
            parts.append({'when': {'contents': f, 'level': str(level)},
                          'apply': {'model': f'{NS}:block/infuser_liquid_{f}_{level}'}})
    write_json(asset('blockstates', 'infuser.json'), {'multipart': parts})
    item_def('infuser', f'{NS}:block/infuser')


def tubing_models():
    tex = {'particle': f'{NS}:block/tubing', 'tube': f'{NS}:block/tubing',
           'end': f'{NS}:block/tubing_end', 'joint': f'{NS}:block/tubing_joint'}

    def model(name, elements, extra=None):
        m = {'textures': tex, 'elements': elements}
        if extra:
            m.update(extra)
        write_json(asset('models', 'block', name + '.json'), m)

    core = box([5.5, 0.5, 5.5], [10.5, 5.5, 10.5], {
        d: face([5.5, 5.5, 10.5, 10.5], '#joint') for d in ('north', 'east', 'south', 'west', 'up', 'down')
    }, 'joint')

    def north_arm(z0):
        # pipe body running from z0 to the core; side faces use the horizontal stripe rows 0-4,
        # top/bottom use the vertical stripe columns 12-16
        length = 5.5 - z0
        return box([6, 1, z0], [10, 5, 5.5], {
            'north': face([6, 6, 10, 10], '#end'),
            'west': face([0, 0, length, 4], '#tube'),
            'east': face([0, 0, length, 4], '#tube'),
            'up': face([12, 0, 16, length], '#tube'),
            'down': face([12, 0, 16, length], '#tube'),
        }, 'pipe')

    flange_n = box([5, 0, -2], [11, 6, -1], {
        d: face([5, 5, 11, 11] if d in ('north', 'south') else [5, 5, 6, 11], '#joint') for d in ('north', 'south', 'west', 'east')
    } | {'up': face([5, 5, 11, 6], '#joint'), 'down': face([5, 5, 11, 6], '#joint')}, 'flange')

    model('tubing_core', [core])
    model('tubing_side', [north_arm(0)])
    model('tubing_side_machine', [north_arm(-2), flange_n])

    def up_arm(y1):
        h = y1 - 5.5
        return box([6, 5.5, 6], [10, y1, 10], {
            'north': face([12, 0, 16, h], '#tube'), 'south': face([12, 0, 16, h], '#tube'),
            'west': face([12, 0, 16, h], '#tube'), 'east': face([12, 0, 16, h], '#tube'),
            'up': face([6, 6, 10, 10], '#end'),
        }, 'pipe_up')

    model('tubing_up', [up_arm(16)])
    model('tubing_up_machine', [up_arm(16), box([5, 15, 5], [11, 16, 11], {
        d: face([5, 5, 11, 6], '#joint') for d in ('north', 'south', 'west', 'east')
    } | {'down': face([5, 5, 11, 11], '#joint')}, 'flange')])

    def down_arm(y0):
        h = 0.5 - y0
        return box([6, y0, 6], [10, 0.5, 10], {
            'north': face([12, 0, 16, h], '#tube'), 'south': face([12, 0, 16, h], '#tube'),
            'west': face([12, 0, 16, h], '#tube'), 'east': face([12, 0, 16, h], '#tube'),
            'down': face([6, 6, 10, 10], '#end'),
        }, 'pipe_down')

    model('tubing_down', [down_arm(0)])
    model('tubing_down_machine', [down_arm(-1)])

    # inventory model: a straight east-west piece, lifted a little in the GUI
    ew = box([0, 1, 6], [16, 5, 10], {
        'north': face([0, 0, 16, 4], '#tube'), 'south': face([0, 0, 16, 4], '#tube'),
        'up': face([0, 0, 16, 4], '#tube'), 'down': face([0, 0, 16, 4], '#tube'),
        'west': face([6, 6, 10, 10], '#end'), 'east': face([6, 6, 10, 10], '#end'),
    }, 'pipe')
    model('tubing_inventory', [core, ew], {
        'parent': 'minecraft:block/block',
        'display': {
            'gui': {'rotation': [30, 225, 0], 'translation': [0, 3, 0], 'scale': [0.75, 0.75, 0.75]},
            'fixed': {'rotation': [0, 0, 0], 'translation': [0, 3, 0], 'scale': [0.6, 0.6, 0.6]},
        },
    })
    item_def('tubing', f'{NS}:block/tubing_inventory')

    parts = [{'apply': {'model': f'{NS}:block/tubing_core'}}]
    for facing, rot in FACINGS.items():
        for kind, suffix in (('tube', ''), ('machine', '_machine')):
            apply = {'model': f'{NS}:block/tubing_side{suffix}'}
            if rot:
                apply['y'] = rot
            parts.append({'when': {facing: kind}, 'apply': apply})
    for kind, suffix in (('tube', ''), ('machine', '_machine')):
        parts.append({'when': {'up': kind}, 'apply': {'model': f'{NS}:block/tubing_up{suffix}'}})
        parts.append({'when': {'down': kind}, 'apply': {'model': f'{NS}:block/tubing_down{suffix}'}})
    write_json(asset('blockstates', 'tubing.json'), {'multipart': parts})


# =====================================================================================================
# Loot tables
# =====================================================================================================

SHEARS_OR_SILK = {'type': 'minecraft:any_of', 'terms': ['minecraft:tool/can_shear', 'minecraft:tool/can_silk_touch']}


def self_drop(name):
    write_json(data(NS, 'loot_table', 'blocks', name + '.json'), {
        'type': 'minecraft:block',
        'pools': [{
            'rolls': 1,
            'condition': {'type': 'minecraft:survives_explosion'},
            'entries': [{'type': 'minecraft:item', 'name': f'{NS}:{name}'}],
        }],
        'random_sequence': f'{NS}:blocks/{name}',
    })


def leaves_loot(f):
    write_json(data(NS, 'loot_table', 'blocks', f + '_leaves.json'), {
        'type': 'minecraft:block',
        'pools': [
            {
                'rolls': 1,
                'entries': [{
                    'type': 'minecraft:alternatives',
                    'children': [
                        {'type': 'minecraft:item', 'condition': SHEARS_OR_SILK, 'name': f'{NS}:{f}_leaves'},
                        {'type': 'minecraft:item', 'condition': {'type': 'minecraft:all_of', 'terms': [
                            {'type': 'minecraft:survives_explosion'},
                            {'type': 'minecraft:table_bonus', 'enchantment': 'minecraft:fortune',
                             'chances': [0.05, 0.0625, 0.083333336, 0.1]},
                        ]}, 'name': f'{NS}:{f}_sapling'},
                    ],
                }],
            },
            {
                'rolls': 1,
                'condition': {'type': 'minecraft:inverted', 'term': SHEARS_OR_SILK},
                'entries': [{
                    'type': 'minecraft:item',
                    'name': 'minecraft:stick',
                    'condition': {'type': 'minecraft:table_bonus', 'enchantment': 'minecraft:fortune',
                                  'chances': [0.02, 0.022222223, 0.025, 0.033333335, 0.1]},
                    'modifier': [
                        {'type': 'minecraft:set_count', 'count': {'type': 'minecraft:uniform', 'min': 1, 'max': 2}},
                        {'type': 'minecraft:explosion_decay'},
                    ],
                }],
            },
            {
                # ripe fruit always drops when the leaves are broken
                'rolls': 1,
                'condition': {'type': 'minecraft:match_block', 'blocks': f'{NS}:{f}_leaves', 'state': {'age': '3'}},
                'entries': [{
                    'type': 'minecraft:item',
                    'name': f'{NS}:{f}',
                    'modifier': [
                        {'type': 'minecraft:set_count', 'count': {'type': 'minecraft:uniform', 'min': 1, 'max': 2}},
                        {'type': 'minecraft:explosion_decay'},
                    ],
                }],
            },
        ],
        'random_sequence': f'{NS}:blocks/{f}_leaves',
    })


# =====================================================================================================
# Recipes, tags
# =====================================================================================================

def recipes():
    write_json(data(NS, 'recipe', 'mixer.json'), {
        'type': 'minecraft:crafting_shaped',
        'category': 'misc',
        'key': {'G': 'minecraft:glass', 'I': 'minecraft:iron_ingot', 'C': 'minecraft:copper_ingot', 'R': 'minecraft:redstone'},
        'pattern': ['GGG', 'GIG', 'CRC'],
        'result': {'id': f'{NS}:mixer'},
    })
    write_json(data(NS, 'recipe', 'infuser.json'), {
        'type': 'minecraft:crafting_shaped',
        'category': 'misc',
        'key': {'C': 'minecraft:copper_ingot', 'K': 'minecraft:cauldron', 'I': 'minecraft:iron_ingot'},
        'pattern': ['C C', 'CKC', 'III'],
        'result': {'id': f'{NS}:infuser'},
    })
    write_json(data(NS, 'recipe', 'tubing.json'), {
        'type': 'minecraft:crafting_shaped',
        'category': 'misc',
        'key': {'C': 'minecraft:copper_ingot', 'G': 'minecraft:glass'},
        'pattern': ['CGC'],
        'result': {'id': f'{NS}:tubing', 'count': 6},
    })


def tag(namespace, kind, name, values):
    write_json(data(namespace, 'tags', kind, name + '.json'), {'replace': False, 'values': values})


def tags():
    leaves = [f'{NS}:{f}_leaves' for f in FRUITS]
    saplings = [f'{NS}:{f}_sapling' for f in FRUITS]
    machines = [f'{NS}:mixer', f'{NS}:infuser', f'{NS}:tubing']
    tag('minecraft', 'block', 'leaves', leaves)
    tag('minecraft', 'block', 'saplings', saplings)
    tag('minecraft', 'block', 'mineable/hoe', leaves)
    tag('minecraft', 'block', 'mineable/pickaxe', machines)
    tag('minecraft', 'item', 'leaves', leaves)
    tag('minecraft', 'item', 'saplings', saplings)
    tag('c', 'item', 'foods/fruit', [f'{NS}:{f}' for f in FRUITS])
    tag('c', 'item', 'drinks/juice', [f'{NS}:{f}_juice' for f in FRUITS])


# =====================================================================================================
# World generation
# =====================================================================================================

TREE_BIOMES = {
    'orange': ['minecraft:plains', 'minecraft:sunflower_plains', 'minecraft:forest', 'minecraft:flower_forest',
               'minecraft:dappled_forest'],
    'lime': ['minecraft:swamp', 'minecraft:mangrove_swamp', 'minecraft:jungle', 'minecraft:sparse_jungle',
             'minecraft:bamboo_jungle'],
    'starfruit': ['minecraft:jungle', 'minecraft:sparse_jungle', 'minecraft:bamboo_jungle', 'minecraft:cherry_grove',
                  'minecraft:meadow'],
    'pomegranate': ['minecraft:savanna', 'minecraft:savanna_plateau', 'minecraft:windswept_savanna',
                    'minecraft:birch_forest', 'minecraft:old_growth_birch_forest', 'minecraft:dark_forest'],
    'dragonfruit': ['minecraft:desert', 'minecraft:badlands', 'minecraft:wooded_badlands', 'minecraft:eroded_badlands',
                    'minecraft:savanna'],
}


def foliage(f):
    def state(age):
        return {'id': f'{NS}:{f}_leaves',
                'properties': {'age': str(age), 'distance': '7', 'persistent': 'false', 'waterlogged': 'false'}}
    return {'type': 'minecraft:weighted', 'entries': [
        {'data': state(0), 'weight': 10},
        {'data': state(1), 'weight': 2},
        {'data': state(2), 'weight': 2},
        {'data': state(3), 'weight': 4},
    ]}


def log(block):
    return {'id': block, 'properties': {'axis': 'y'}}


def tree_features():
    trees = {
        'orange': {
            'trunk_provider': log('minecraft:oak_log'),
            'trunk_placer': {'type': 'minecraft:straight_trunk_placer', 'base_height': 4, 'height_rand_a': 2, 'height_rand_b': 0},
            'foliage_placer': {'type': 'minecraft:blob_foliage_placer', 'radius': 2, 'offset': 0, 'height': 3},
            'minimum_size': {'type': 'minecraft:two_layers_feature_size'},
        },
        'lime': {
            'trunk_provider': log('minecraft:birch_log'),
            'trunk_placer': {'type': 'minecraft:straight_trunk_placer', 'base_height': 5, 'height_rand_a': 2, 'height_rand_b': 0},
            'foliage_placer': {'type': 'minecraft:blob_foliage_placer', 'radius': 2, 'offset': 0, 'height': 3},
            'minimum_size': {'type': 'minecraft:two_layers_feature_size'},
        },
        'starfruit': {
            'trunk_provider': log('minecraft:jungle_log'),
            'trunk_placer': {
                'type': 'minecraft:cherry_trunk_placer',
                'base_height': 5, 'height_rand_a': 1, 'height_rand_b': 0,
                'branch_count': {'type': 'minecraft:weighted_list', 'distribution': [
                    {'data': 1, 'weight': 1}, {'data': 2, 'weight': 2}]},
                'branch_horizontal_length': {'type': 'minecraft:uniform', 'min_inclusive': 2, 'max_inclusive': 3},
                'branch_start_offset_from_top': {'min_inclusive': -3, 'max_inclusive': -2},
                'branch_end_offset_from_top': {'type': 'minecraft:uniform', 'min_inclusive': -1, 'max_inclusive': 0},
            },
            'foliage_placer': {
                'type': 'minecraft:cherry_foliage_placer', 'radius': 3, 'offset': 0, 'height': 4,
                'wide_bottom_layer_hole_chance': 0.25, 'corner_hole_chance': 0.25,
                'hanging_leaves_chance': 0.16666667, 'hanging_leaves_extension_chance': 0.33333334,
            },
            'minimum_size': {'type': 'minecraft:two_layers_feature_size', 'upper_size': 2},
        },
        'pomegranate': {
            'trunk_provider': log('minecraft:mangrove_log'),
            'trunk_placer': {'type': 'minecraft:bending_trunk_placer', 'base_height': 4, 'height_rand_a': 2, 'height_rand_b': 0,
                             'min_height_for_leaves': 3,
                             'bend_length': {'type': 'minecraft:uniform', 'min_inclusive': 1, 'max_inclusive': 2}},
            'foliage_placer': {'type': 'minecraft:random_spread_foliage_placer', 'radius': 3, 'offset': 0,
                               'foliage_height': 2, 'leaf_placement_attempts': 60},
            'minimum_size': {'type': 'minecraft:two_layers_feature_size'},
        },
        'dragonfruit': {
            'trunk_provider': log('minecraft:acacia_log'),
            'trunk_placer': {'type': 'minecraft:forking_trunk_placer', 'base_height': 5, 'height_rand_a': 2, 'height_rand_b': 1},
            'foliage_placer': {'type': 'minecraft:acacia_foliage_placer', 'radius': 2, 'offset': 0},
            'minimum_size': {'type': 'minecraft:two_layers_feature_size', 'upper_size': 2},
        },
    }
    for f, spec in trees.items():
        feature = {
            'type': 'minecraft:tree',
            'below_trunk_provider': 'minecraft:soil_beneath_tree',
            'decorators': [],
            'foliage_placer': spec['foliage_placer'],
            'foliage_provider': foliage(f),
            'ignore_vines': True,
            'minimum_size': spec['minimum_size'],
            'trunk_placer': spec['trunk_placer'],
            'trunk_provider': spec['trunk_provider'],
        }
        write_json(data(NS, 'worldgen', 'feature', f + '_tree.json'), feature)

        write_json(data(NS, 'worldgen', 'placed_feature', f + '_tree.json'), {
            'feature': f'{NS}:{f}_tree',
            'placement': [
                {'type': 'minecraft:rarity_filter', 'chance': 8},
                {'type': 'minecraft:in_square'},
                {'type': 'minecraft:surface_water_depth_filter', 'max_water_depth': 0},
                {'type': 'minecraft:heightmap', 'heightmap': 'OCEAN_FLOOR'},
                {'type': 'minecraft:block_predicate_filter',
                 'predicate': {'type': 'minecraft:would_survive', 'state': f'{NS}:{f}_sapling'}},
                {'type': 'minecraft:biome'},
            ],
        })

        write_json(data(NS, 'neoforge', 'biome_modifier', f + '_trees.json'), {
            'type': 'neoforge:add_features',
            'biomes': TREE_BIOMES[f],
            'features': f'{NS}:{f}_tree',
            'step': 'vegetal_decoration',
        })


# =====================================================================================================
# Language
# =====================================================================================================

JUICE_DESC = {
    'starfruit': 'Looting X and Fortune X - 10:00',
    'dragonfruit': 'Creative flight and fire immunity - 8:00',
    'pomegranate': "Titan's Might and Resistance II - 8:00",
    'orange': '+10 hearts, fast regeneration, never hungry, immune to harmful effects - 10:00',
    'lime': 'Speed, auto step-up, double mining speed and reach - 10:00',
}


def lang():
    en = {
        'itemGroup.juicer': 'Juicer',
        'block.juicer.mixer': 'Fruit Mixer',
        'block.juicer.infuser': 'Juice Infuser',
        'block.juicer.tubing': 'Juice Tubing',
        'container.juicer.mixer': 'Fruit Mixer',
        'container.juicer.infuser': 'Juice Infuser',
        'block.juicer.mixer.desc1': 'Blends fruit into concentrate (right-click with fruit).',
        'block.juicer.mixer.desc2': 'Connect it to a Juice Infuser with Juice Tubing.',
        'block.juicer.infuser.desc1': 'Mixes 1000 mB concentrate + sugar into a juice bottle.',
        'block.juicer.infuser.desc2': 'Needs sugar and glass bottles.',
        'block.juicer.tubing.desc1': 'Carries concentrate from Fruit Mixers to Juice Infusers.',
        'gui.juicer.empty_tank': 'Empty the tank (the concentrate is lost)',
        'gui.juicer.tank.empty': 'Empty',
        'gui.juicer.tank.amount': '%s / %s mB',
        'effect.juicer.looting': 'Looting',
        'effect.juicer.fortune': 'Fortune',
        'effect.juicer.flight': 'Flight',
        'effect.juicer.titan': "Titan's Might",
        'effect.juicer.vitality': 'Vitality',
        'effect.juicer.zest': 'Zest',
        # vanilla only ships potency names up to VI; juices go up to X
        'potion.potency.6': 'VII',
        'potion.potency.7': 'VIII',
        'potion.potency.8': 'IX',
        'potion.potency.9': 'X',
    }
    for f in FRUITS:
        t = TITLES[f]
        en[f'item.juicer.{f}'] = t
        en[f'item.juicer.{f}_juice'] = f'{t} Juice'
        en[f'item.juicer.{f}_juice.desc'] = JUICE_DESC[f]
        en[f'block.juicer.{f}_sapling'] = f'{t} Sapling'
        en[f'block.juicer.{f}_leaves'] = f'{t} Leaves'
        en[f'concentrate.juicer.{f}'] = f'{t} Concentrate'
    write_json(asset('lang', 'en_us.json'), en)


# =====================================================================================================
# GameTest structures (NBT)
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
    platform = [(x, 0, z, 'minecraft:stone') for x in range(9) for z in range(5)]
    structure('test_platform', (9, 4, 5), platform)
    orchard = [(x, 0, z, 'minecraft:grass_block') for x in range(15) for z in range(15)]
    structure('test_orchard', (15, 16, 15), orchard)


def main():
    # start from a clean slate for generated folders so renamed files don't linger
    for folder in (asset('blockstates'), asset('models'), asset('items'), data(NS, 'loot_table'), data(NS, 'recipe'),
                   data(NS, 'worldgen'), data(NS, 'neoforge'), data('minecraft', 'tags'), data('c', 'tags')):
        if os.path.isdir(folder):
            shutil.rmtree(folder)

    fruit_models()
    mixer_models()
    infuser_models()
    tubing_models()
    for f in FRUITS:
        leaves_loot(f)
        self_drop(f + '_sapling')
    for name in ('mixer', 'infuser', 'tubing'):
        self_drop(name)
    recipes()
    tags()
    tree_features()
    lang()
    test_structures()
    print(f'wrote {written} files under {RES}')


if __name__ == '__main__':
    main()
