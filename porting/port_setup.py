"""Creates <Mod>-1.21.8/ (NeoForge 21.8.54 MDK) from a Forge 26.3 mod folder: gradle files, metadata template,
sources (minus GameTests) and resources (minus test instances). Usage: python3 port_setup.py <Mod>"""
import os
import re
import shutil
import sys

SP = os.environ.get('MDK_PARENT', os.path.dirname(os.path.abspath(__file__)))  # folder holding the cloned MDK as mdk/
MDK = os.path.join(SP, 'mdk')
REPO = '/home/user/minecraft-mods'

mod = sys.argv[1]
src = os.path.join(REPO, mod)
dst = os.path.join(REPO, mod + '-1.21.8')
os.makedirs(dst, exist_ok=True)

props = dict(re.findall(r'^(\w+)=(.*)$', open(os.path.join(src, 'gradle.properties')).read(), re.M))
modid = props['mod_id']

for f in ('build.gradle', 'settings.gradle', 'gradlew', 'gradlew.bat'):
    shutil.copy2(os.path.join(MDK, f), dst)
shutil.copytree(os.path.join(MDK, 'gradle'), os.path.join(dst, 'gradle'), dirs_exist_ok=True)

gp = open(os.path.join(MDK, 'gradle.properties')).read()
for key in ('mod_id', 'mod_name', 'mod_license', 'mod_version', 'mod_group_id'):
    gp = re.sub(r'^%s=.*$' % key, '%s=%s' % (key, props[key]), gp, flags=re.M)
gp = re.sub(r'^org.gradle.jvmargs=.*$', 'org.gradle.jvmargs=-Xmx3G', gp, flags=re.M)
gp = re.sub(r'^org.gradle.configuration-cache=.*$', 'org.gradle.configuration-cache=false', gp, flags=re.M)
open(os.path.join(dst, 'gradle.properties'), 'w').write(gp)

bg = open(os.path.join(dst, 'build.gradle')).read()
bg = bg.replace('archivesName = mod_id', 'archivesName = "${mod_id}-${minecraft_version}"')
open(os.path.join(dst, 'build.gradle'), 'w').write(bg)

# Sources and resources.
for sub in ('java', 'resources'):
    shutil.rmtree(os.path.join(dst, 'src/main', sub), ignore_errors=True)
    shutil.copytree(os.path.join(src, 'src/main', sub), os.path.join(dst, 'src/main', sub))
res = os.path.join(dst, 'src/main/resources')
shutil.rmtree(os.path.join(dst, 'src/main/java/com/afjan', modid, 'gametest'), ignore_errors=True)
shutil.rmtree(os.path.join(res, 'data', modid, 'test_instance'), ignore_errors=True)
old_toml = open(os.path.join(res, 'META-INF/mods.toml')).read()
shutil.rmtree(os.path.join(res, 'META-INF'))
if os.path.exists(os.path.join(res, 'pack.mcmeta')):
    os.remove(os.path.join(res, 'pack.mcmeta'))
if os.path.isdir(os.path.join(res, 'data/forge')):
    shutil.move(os.path.join(res, 'data/forge'), os.path.join(res, 'data/neoforge'))

# neoforge.mods.toml template from the Forge mods.toml.
toml = re.sub(r'^(modLoader|loaderVersion)=.*\n', '', old_toml, flags=re.M)
toml = toml.replace('modId="forge"', 'modId="neoforge"').replace('mandatory=true', 'type="required"')
toml = toml.replace('${forge_version_range}', '[${neo_version},)')
mixins = os.path.join(res, modid + '.mixins.json')
if os.path.exists(mixins):
    toml = toml.replace('\n[[dependencies', '\n[[mixins]]\nconfig="%s.mixins.json"\n\n[[dependencies' % modid, 1)
os.makedirs(os.path.join(dst, 'src/main/templates/META-INF'), exist_ok=True)
open(os.path.join(dst, 'src/main/templates/META-INF/neoforge.mods.toml'), 'w').write(toml)
print('set up', dst)
