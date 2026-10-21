"""Builds the second YouTube Short, "the world-load reveal", from ArrivalShort's frames.

python assemble_short2.py <video_tour dir> <sounds dir> <out dir>
(sounds from get_sounds.py; set CAPTION_FONT to a heavy font file if none of the usual ones is found)

Makes spawn-gazebo-short-2.mp4 (the game's own sounds), spawn-gazebo-short-2-silent.mp4 and
spawn-gazebo-short-2-thumbnail.png, 1080x1920. It opens on the first frames of a brand-new world,
already standing in the gazebo, cuts through three more new worlds, opens the starter chest and
lets a creeper go off beside it, then arrives in one more new world, so a replay loops into the
first. No title or end card; the last seconds carry the call to follow.

Captions are bold white capitals with a black outline straight on the footage, one word in the
dome's copper. A small Spawn Gazebo badge stays in one place all the way through. If a scene
changes in ArrivalShort, change its caption here too.
"""
import csv, os, random, shutil, subprocess, sys
from concurrent.futures import ThreadPoolExecutor
from PIL import Image, ImageDraw, ImageFont

frames_dir, sounds_dir, out = sys.argv[1:4]
FPS = 20
COPPER = (238, 146, 96)
repo = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', '..')

W, H, SIZE, STROKE, LEAD = 1080, 1920, 84, 7, 104
NAME = 'spawn-gazebo-short-2'
# Scene, the frames kept (first, last; None = to the end), caption lines ("*word*" in copper) and
# where the caption's top sits. The three woods share one caption, shown across all three.
SCENES = [
    ('s_arrive', 0, None, ['NEW WORLD.', 'YOU *SPAWN* HERE'], 520),
    ('s_cherry', 0, None, ['BUILT FROM', 'YOUR *WOODS*'], 520),
    ('s_birch', 0, None, ['BUILT FROM', 'YOUR *WOODS*'], 520),
    ('s_desert', 0, None, ['BUILT FROM', 'YOUR *WOODS*'], 520),
    ('s_chest', 6, 78, ['A STARTER', '*KIT* INSIDE'], 1360),
    ('s_creeper', 0, 60, ['CREEPER-*PROOF*'], 520),
    ('s_loop', 0, None, ['FOLLOW FOR MORE', '*COZY* MODS'], 1100),
]
# Captions that carry on from the scene before don't fade in again.
CARRIED = {'s_birch', 's_desert'}
BADGE_BOX = (40, 250, 520, 350)

FONTS = [os.environ.get('CAPTION_FONT', ''), 'C:/Windows/Fonts/ariblk.ttf', '/usr/share/fonts/truetype/msttcorefonts/Arial_Black.ttf',
         '/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf']
BADGE_FONTS = [os.environ.get('BADGE_FONT', ''), 'C:/Windows/Fonts/segoeuib.ttf', '/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf']


def font(paths, size):
    for p in paths:
        if p and os.path.exists(p):
            return ImageFont.truetype(p, size)
    sys.exit('no caption font found; set CAPTION_FONT')


def run(*args):
    subprocess.run(args, check=True, stdout=subprocess.DEVNULL, stderr=subprocess.PIPE)


CAPTION = font(FONTS, SIZE)


def caption(lines, top):
    """A transparent frame-sized layer with the caption lines, centred."""
    layer = Image.new('RGBA', (W, H), (0, 0, 0, 0))
    d = ImageDraw.Draw(layer)
    y = top
    for line in lines:
        parts = [(p, COPPER if i % 2 else (255, 255, 255)) for i, p in enumerate(line.split('*')) if p]
        x = (W - sum(d.textlength(p, font=CAPTION) for p, _ in parts)) / 2
        for text, colour in parts:
            d.text((x, y), text, font=CAPTION, fill=colour, stroke_width=STROKE, stroke_fill=(0, 0, 0))
            x += d.textlength(text, font=CAPTION)
        y += LEAD
    return layer


def badge():
    """A small mod-list style badge: the icon, the name, the loaders."""
    layer = Image.new('RGBA', (W, H), (0, 0, 0, 0))
    d = ImageDraw.Draw(layer)
    box = BADGE_BOX
    d.rounded_rectangle(box, 18, fill=(20, 20, 24, 225))
    side = box[3] - box[1] - 28
    icon = Image.open(os.path.join(repo, 'common/src/main/resources/assets/spawn_gazebo/icon.png')).convert('RGBA')
    icon = icon.resize((side, side), Image.LANCZOS)
    layer.alpha_composite(icon, (box[0] + 14, box[1] + 14))
    x = box[0] + side + 32
    d.text((x, box[1] + 12), 'Spawn Gazebo', font=font(BADGE_FONTS, 40), fill=(255, 255, 255))
    d.text((x, box[1] + 60), 'Fabric · NeoForge', font=font(BADGE_FONTS, 24), fill=(170, 170, 180))
    return layer


BADGE = badge()

with open(os.path.join(frames_dir, 'actions.tsv')) as f:
    actions = [(r[0], int(r[1]), r[2], r[3]) for r in csv.reader(f, delimiter='\t')]

work = os.path.join(out, 'work-short-2')
shutil.rmtree(work, ignore_errors=True)
os.makedirs(work)

FADE_IN = 4  # frames for a caption to fade in; scenes cut hard
kept, jobs = {}, []
for scene, first, last, lines, top in SCENES:
    names = sorted(n for n in os.listdir(frames_dir) if n.startswith(scene + '_') and n.endswith('.png'))
    if not names:
        print('no frames for', scene, '(skipped)')
        continue
    end = len(names) if last is None else min(last + 1, len(names))
    kept[scene] = (first, end)
    dest = os.path.join(work, scene)
    os.makedirs(dest)
    cap = caption(lines, top) if lines else None
    for i, n in enumerate(range(first, end)):
        jobs.append((os.path.join(frames_dir, names[n]), os.path.join(dest, '%04d.png' % i), cap,
                     1.0 if scene in CARRIED else min(1.0, (i + 1) / FADE_IN)))


def draw(job):
    source, dest, cap, a = job
    img = Image.open(source).convert('RGBA')
    if img.size != (W, H):
        img = img.resize((W, H), Image.LANCZOS)
    img.alpha_composite(BADGE)
    if cap is not None:
        layer = cap.copy()
        if a < 1:
            layer.putalpha(layer.getchannel('A').point(lambda v: int(v * a)))
        img.alpha_composite(layer)
    # Read once by ffmpeg and deleted, so light compression.
    img.convert('RGB').save(dest, compress_level=1)


with ThreadPoolExecutor(os.cpu_count()) as pool:
    list(pool.map(draw, jobs))

clips, starts, t = [], {}, 0.0
for scene, *_ in SCENES:
    if scene not in kept:
        continue
    starts[scene] = t
    first, end = kept[scene]
    t += (end - first) / FPS
    clips.append(os.path.join(work, scene + '.mp4'))
total = t


def encode(scene):
    run('ffmpeg', '-y', '-framerate', str(FPS), '-i', os.path.join(work, scene, '%04d.png'),
        '-vf', 'format=yuv420p', '-r', '30', '-c:v', 'libx264', '-preset', 'slow', '-crf', '18', os.path.join(work, scene + '.mp4'))


with ThreadPoolExecutor(4) as pool:
    list(pool.map(encode, list(kept)))
with open(os.path.join(work, 'list.txt'), 'w') as f:
    f.writelines(f"file '{os.path.abspath(c).replace(os.sep, '/')}'\n" for c in clips)
silent = os.path.join(out, NAME + '-silent.mp4')
run('ffmpeg', '-y', '-f', 'concat', '-safe', '0', '-i', os.path.join(work, 'list.txt'), '-c', 'copy', '-movflags', '+faststart', silent)

# The game's own sounds at the logged frames: the chest, the creeper's hiss and its blast.
random.seed(7)
SOUND = {'chest_open': ('chest_open', 0.7), 'chest_close': ('chest_close', 0.6), 'fuse': ('fuse', 0.8), 'boom': ('boom', 0.9)}
cues = []
for scene, frame, kind, what in actions:
    if scene not in starts or kind not in SOUND:
        continue
    first, end = kept[scene]
    if not first <= frame < end:
        continue
    sound, volume = SOUND[kind]
    cues.append((starts[scene] + (frame - first) / FPS, sound, 0.95 + random.random() * 0.1, volume))
inputs, filters = [], []
for i, (at, sound, pitch, volume) in enumerate(cues):
    inputs += ['-i', os.path.join(sounds_dir, sound + '.ogg')]
    ms = int(at * 1000)
    filters.append(f'[{i}:a]aformat=sample_rates=44100:channel_layouts=stereo,asetrate={44100 * pitch:.0f},aresample=44100,'
                   f'volume={volume},adelay={ms}|{ms}[s{i}]')
mix = ''.join(f'[s{i}]' for i in range(len(cues)))
filters.append(f'{mix}amix=inputs={len(cues)}:normalize=0,apad=whole_dur={total:.3f}[a]')
audio = os.path.join(work, 'sound.wav')
run('ffmpeg', '-y', *inputs, '-filter_complex', ';'.join(filters), '-map', '[a]', '-t', f'{total:.3f}', audio)
run('ffmpeg', '-y', '-i', silent, '-i', audio, '-map', '0:v', '-map', '1:a', '-c:v', 'copy', '-c:a', 'aac', '-b:a', '160k',
    '-shortest', '-movflags', '+faststart', os.path.join(out, NAME + '.mp4'))

# The thumbnail: the opening arrival, looking out of the arch, with the mod's name large.
raw = sorted(n for n in os.listdir(frames_dir) if n.startswith('s_arrive_'))
thumb = Image.open(os.path.join(frames_dir, raw[len(raw) * 3 // 4])).convert('RGBA').resize((W, H), Image.LANCZOS)
d = ImageDraw.Draw(thumb)
size, top = 150, 300
big = font(FONTS, size)
for i, parts in enumerate([[('SPAWN', (255, 255, 255))], [('GAZEBO', COPPER)]]):
    x = (W - sum(d.textlength(text, font=big) for text, _ in parts)) / 2
    for text, colour in parts:
        d.text((x, top + i * size * 1.1), text, font=big, fill=colour, stroke_width=size // 12, stroke_fill=(0, 0, 0))
        x += d.textlength(text, font=big)
thumb.convert('RGB').save(os.path.join(out, NAME + '-thumbnail.png'))
print('total seconds', round(total, 2))
for scene in kept:
    first, end = kept[scene]
    print(f'  {scene}: {(end - first) / FPS:.2f} s')
