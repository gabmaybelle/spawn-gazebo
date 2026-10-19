"""Copies the game sounds the videos use out of Loom's asset cache (filled by any game-test run).

python get_sounds.py <out dir>
The sounds are Mojang's, so they're read from the cache each time rather than kept in the repo.
"""
import glob, json, os, shutil, sys

out = sys.argv[1]
os.makedirs(out, exist_ok=True)
assets = os.path.expanduser('~/.gradle/caches/fabric-loom/assets')
index = sorted(glob.glob(os.path.join(assets, 'indexes', '*.json')))[-1]
objects = json.load(open(index))['objects']
SOUNDS = {'chest_open': 'random/chestopen', 'chest_close': 'random/chestclosed', 'fuse': 'random/fuse',
          'boom': 'random/explode1', 'click': 'random/click', 'hit': 'dig/stone1', 'place': 'dig/stone1'}
for name, file in SOUNDS.items():
    h = objects[f'minecraft/sounds/{file}.ogg']['hash']
    source = os.path.join(assets, 'objects', h[:2], h)
    if not os.path.exists(source):
        sys.exit(f'{file}.ogg is not in the asset cache; run a game test with resources.download.minecraft.net reachable')
    shutil.copy(source, os.path.join(out, name + '.ogg'))
    print(name)
