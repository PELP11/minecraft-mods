"""Writes one data/tempered/test_instance/mastery/<name>.json per @GameTest method in TemperedGameTests.java.

Run from the project folder: python3 tools/gen_tests.py (Windows: py tools/gen_tests.py).
Structures are Forge's generated empty boxes (forge:emptyWxHxD), so no .nbt files are needed.
The files are excluded from the release jar (build.gradle), they only exist for runGameTestServer.
"""
import json
import os
import re
import shutil

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SOURCE = os.path.join(ROOT, 'src/main/java/com/afjan/tempered/gametest/TemperedGameTests.java')
OUT = os.path.join(ROOT, 'src/main/resources/data/tempered/test_instance/mastery')
DEFAULT_STRUCTURE = 'forge:empty5x4x5'


def snake(name):
    return re.sub(r'(?<!^)([A-Z])', r'_\1', name).lower()


def main():
    text = open(SOURCE, encoding='utf-8').read()
    tests = re.findall(r'@GameTest(?:\((.*?)\))?\s*public static void (\w+)\(', text, re.S)
    if os.path.isdir(OUT):
        shutil.rmtree(OUT)
    os.makedirs(OUT)
    for args, method in tests:
        opts = dict(re.findall(r'(\w+)\s*=\s*("[^"]*"|\w+)', args or ''))
        data = {
            'type': 'minecraft:function',
            'environment': 'minecraft:default',
            'function': 'tempered:mastery/' + snake(method),
            'max_ticks': int(opts.get('maxTicks', '100')),
            'structure': opts.get('structure', '"%s"' % DEFAULT_STRUCTURE).strip('"'),
        }
        if opts.get('skyAccess') == 'true':
            data['sky_access'] = True
        with open(os.path.join(OUT, snake(method) + '.json'), 'w', encoding='utf-8', newline='\n') as f:
            json.dump(data, f, indent=2)
            f.write('\n')
    print('wrote %d test instances' % len(tests))


if __name__ == '__main__':
    main()
