#!/usr/bin/env python3
"""Cross-checks the generated resources against the Java registries.

GameTests run on a dedicated server and never load client assets, so a missing texture, model or translation
only shows up in game. This walks the item ids out of the Java sources and makes sure each one has all four.

Run from anywhere:  py tools/check_assets.py     (exit code 1 on any problem)
"""
import json
import os
import re
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.normpath(os.path.join(HERE, '..'))
SRC = os.path.join(ROOT, 'src', 'main', 'java', 'com', 'afjan', 'arsenal')
ASSETS = os.path.join(ROOT, 'src', 'main', 'resources', 'assets', 'arsenal')

problems = []


def read(*parts):
    with open(os.path.join(SRC, *parts), encoding='utf-8') as f:
        return f.read()


def enum_paths(source, marker):
    """Pulls the first string literal out of every enum constant line."""
    body = source.split(marker, 1)[1]
    body = body.split('\n    private final', 1)[0]
    return re.findall(r'^\s{4}[A-Z][A-Z0-9_]*\("([a-z0-9_]+)"', body, re.M)


def main():
    guns = enum_paths(read('gun', 'GunType.java'), 'public enum GunType {')
    calibers = enum_paths(read('gun', 'Caliber.java'), 'public enum Caliber {')
    attachments = enum_paths(read('gun', 'Attachment.java'), 'public enum Attachment {')
    ordnance = enum_paths(read('combat', 'Ordnance.java'), 'public enum Ordnance {')

    mod_items = read('registry', 'ModItems.java')
    materials = re.findall(r'registerSimpleItem\("([a-z0-9_]+)"', mod_items)
    blocks = re.findall(r'registerBlock\("([a-z0-9_]+)"|registerSimpleBlock\("([a-z0-9_]+)"',
                        read('registry', 'ModBlocks.java'))
    blocks = [a or b for (a, b) in blocks]

    # Launched ordnance (40mm grenades, rockets) is ammunition, registered with the calibers, not hand-thrown items.
    thrown = [o for o in ordnance if o not in calibers]

    items = materials + calibers + guns + attachments + thrown
    # The F-14, its stores (the Store enum) and supplies registered one by one.
    stores = enum_paths(read('vehicle', 'Store.java'), 'public enum Store {')
    jet = [n for n in re.findall(r'registerItem\("([a-z0-9_]+)"', mod_items) + stores if n not in items]
    items += jet
    print(f'{len(guns)} guns, {len(calibers)} cartridges, {len(attachments)} attachments, '
          f'{len(thrown)} thrown, {len(materials)} components, {len(jet)} jet items, {len(blocks)} blocks')

    lang_path = os.path.join(ASSETS, 'lang', 'en_us.json')
    with open(lang_path, encoding='utf-8') as f:
        lang = json.load(f)

    for name in items:
        check(os.path.join(ASSETS, 'textures', 'item', name + '.png'), name, 'texture')
        check(os.path.join(ASSETS, 'models', 'item', name + '.json'), name, 'item model')
        check(os.path.join(ASSETS, 'items', name + '.json'), name, 'client item definition')
        if f'item.arsenal.{name}' not in lang:
            problems.append(f'{name}: no translation (item.arsenal.{name})')

    for name in blocks:
        check(os.path.join(ASSETS, 'blockstates', name + '.json'), name, 'blockstate')
        check(os.path.join(ASSETS, 'models', 'item', name + '.json'), name, 'block item model')
        check(os.path.join(ASSETS, 'items', name + '.json'), name, 'client item definition')
        if f'block.arsenal.{name}' not in lang:
            problems.append(f'{name}: no translation (block.arsenal.{name})')

    # Every texture a block model points at must exist.
    models = os.path.join(ASSETS, 'models', 'block')
    for file in os.listdir(models):
        with open(os.path.join(models, file), encoding='utf-8') as f:
            model = json.load(f)
        for key, value in model.get('textures', {}).items():
            if value.startswith('arsenal:'):
                path = os.path.join(ASSETS, 'textures', *value.split(':')[1].split('/'))
                check(path + '.png', file, f'texture "{key}"')

    # Attachments, ordnance and the jet's items carry a tooltip line each.
    for name in attachments + thrown + jet:
        if f'tooltip.arsenal.{name}' not in lang:
            problems.append(f'{name}: no tooltip line')

    # Every translation key and key mapping named in the Java code, and every texture or mesh a renderer loads.
    java = []
    for folder, _, files in os.walk(SRC):
        for file in files:
            if file.endswith('.java'):
                with open(os.path.join(folder, file), encoding='utf-8') as f:
                    java.append(f.read())
    java = '\n'.join(java)
    keys = set(re.findall(r'translatable\("([a-z0-9_.]+[a-z0-9_])"', java))
    keys |= set(re.findall(r'new KeyMapping\("([a-z0-9_.]+)"', java))
    for key in sorted(keys):
        if key not in lang:
            problems.append(f'no translation for "{key}" (used in the Java code)')
    for resource in sorted(set(re.findall(r'Arsenal\.id\("((?:textures|vehicle)/[a-z0-9_/.]+)"\)', java))):
        check(os.path.join(ASSETS, *resource.split('/')), resource, 'client resource')
    print(f'{len(keys)} translation keys in the code')

    # Sounds: every event ModSounds registers is in sounds.json (written by gen_sounds.py) with its .ogg files, the
    # same range as its attenuation distance and a translated subtitle; nothing in sounds.json goes unregistered.
    events = {name: int(r) for name, r in re.findall(r'event\("([a-z0-9_.]+)", (\d+)\)',
                                                      read('registry', 'ModSounds.java'))}
    for gun in guns:
        events[f'gun.{gun}.fire'] = None     # registered in a loop, range by gun family
    with open(os.path.join(ASSETS, 'sounds.json'), encoding='utf-8') as f:
        sounds = json.load(f)
    for name, rng in events.items():
        entry = sounds.get(name)
        if entry is None:
            problems.append(f'sound {name}: not in sounds.json')
            continue
        for sound in entry['sounds']:
            check(os.path.join(ASSETS, 'sounds', *sound['name'].split(':')[1].split('/')) + '.ogg', name, 'sound file')
            if rng is not None and sound.get('attenuation_distance', 16) != rng:
                problems.append(f'sound {name}: range {rng} in ModSounds but attenuation_distance '
                                f'{sound.get("attenuation_distance", 16)} in sounds.json')
        if 'subtitle' in entry and entry['subtitle'] not in lang:
            problems.append(f'sound {name}: no subtitle translation ({entry["subtitle"]})')
    for name in sounds:
        if name not in events:
            problems.append(f'sound {name}: in sounds.json but not registered in ModSounds')
    print(f'{len(events)} sound events')

    if problems:
        print(f'\n{len(problems)} problem(s):')
        for problem in problems:
            print('  -', problem)
        return 1
    print('all item and block assets present')
    return 0


def check(path, name, what):
    if not os.path.isfile(path):
        problems.append(f'{name}: missing {what} ({os.path.relpath(path, ROOT)})')


if __name__ == '__main__':
    sys.exit(main())
