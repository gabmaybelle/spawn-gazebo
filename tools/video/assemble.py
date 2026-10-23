"""Builds the Short or the listing video from VideoTour's frames.

python assemble.py short|wide|listing <video_tour dir> <sounds dir> <out dir>
(sounds from get_sounds.py; set CAPTION_FONT to a heavy font file if none of the usual ones is found)

short: spawn-gazebo-short.mp4 (game sounds), spawn-gazebo-short-silent.mp4 and
       spawn-gazebo-short-thumbnail.png, 1080x1920. It opens on the gazebo and its last shot ends
       where the first begins, so a replay loops; there is no title or end card.
wide:  spawn-gazebo.mp4, spawn-gazebo-silent.mp4 and spawn-gazebo-thumbnail.png (1280x720), 1920x1080,
       ending on a quiet held shot.

Captions are bold white capitals with a black outline straight on the footage, one word in copper.
A small Spawn Gazebo badge stays in one place all the way through. If a scene changes in VideoTour,
change its caption here too.
"""
import csv, json, os, random, shutil, subprocess, sys
from PIL import Image, ImageDraw, ImageFont

mode, frames_dir, sounds_dir, out = sys.argv[1:5]
FPS = 20
COPPER = (238, 146, 96)
repo = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', '..')

# Scene, the frames kept (first, last; None = to the end), caption lines ("*word*" in copper) and
# where the caption's top sits.
if mode == 'short':
    W, H, SIZE, STROKE, LEAD = 1080, 1920, 84, 7, 104
    NAME = 'spawn-gazebo-short'
    SCENES = [
        ('s_hook', 0, None, ['EVERY NEW WORLD', 'STARTS *HERE*'], 330),
        ('s_wood', 0, None, ['BUILT FROM', 'YOUR *WOODS*'], 330),
        ('s_chest', 8, None, ['A STARTER *KIT*', 'WAITING INSIDE'], 330),
        ('s_creeper', 4, None, ['*CREEPER*-PROOF'], 380),
        ('s_loop', 0, None, None, 0),
    ]
    BADGE_BOX = (300, 1390, 780, 1490)
elif mode == 'wide':
    W, H, SIZE, STROKE, LEAD = 1920, 1080, 64, 6, 80
    NAME = 'spawn-gazebo'
    SCENES = [
        ('w_wake', 0, None, ['NEW WORLDS START', 'IN A *GAZEBO*'], 760),
        ('w_orbit', 0, None, ['A COPPER *DOME*,', 'LANTERNS AND ARCHES'], 760),
        ('w_inside', 0, None, ['BENCHES AND A', 'SPORE *BLOSSOM*'], 760),
        ('w_chest', 8, None, ['A STARTER *KIT*', 'IN THE CHEST'], 70),
        ('w_wood', 0, None, ['BUILT FROM THE *WOOD*', 'THAT GROWS NEAREST'], 760),
        ('w_protect', 0, None, ['SURVIVAL PLAYERS', '*CAN\'T* BREAK IT'], 760),
        ('w_creeper', 4, None, ['AND NEITHER', 'CAN *CREEPERS*'], 760),
        ('w_build', 0, None, ['ADD ONE TO A', 'WORLD YOU *HAVE*'], 170),
        ('w_desert', 0, None, ['*SANDSTONE*', 'IN DESERTS'], 760),
        ('w_closing', 0, None, None, 0),
    ]
    BADGE_BOX = (40, 40, 480, 130)
elif mode == 'listing':
    # The listing video "First steps" (1.3.3; plan: mods-media spawn-gazebo/LISTING-VIDEO-PLAN.md), from
    # ArrivalShort's -PvideoListing frames: no captions or badge (her voice carries it, laid on after with
    # add_voice.py), the end card over the morning's last frames. Frame ranges from take 02 (2026-10-22): the
    # chest screen is open from frame 18 to 101, so the cut keeps its first half.
    W, H, SIZE, STROKE, LEAD = 1920, 1080, 64, 6, 80
    NAME = 'spawn-gazebo-listing'
    SCENES = [
        ('l_snow', 0, None, None, 0),
        ('l_cherry', 0, None, None, 0),
        ('l_desert', 0, None, None, 0),
        ('l_dark', 0, None, None, 0),
        ('l_bamboo', 0, None, None, 0),
        ('l_ice', 0, None, None, 0),
        ('l_birch', 0, None, None, 0),
        ('l_chest', 0, 90, None, 0),
        ('l_creeper', 0, None, None, 0),
        ('l_morning', 0, None, None, 0),
    ]
    BADGE_BOX = (40, 40, 480, 130)
    END = ['Spawn *Gazebo*', 'Fabric and NeoForge, Minecraft 26.3 and 1.21.1', 'Search "Spawn Gazebo" on CurseForge']
else:
    sys.exit('mode is short, wide or listing')

FONTS = [os.environ.get('CAPTION_FONT', ''), 'C:/Windows/Fonts/ariblk.ttf', '/usr/share/fonts/truetype/msttcorefonts/Arial_Black.ttf',
         '/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf']
BADGE_FONTS = [os.environ.get('BADGE_FONT', ''), 'C:/Windows/Fonts/segoeuib.ttf', '/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf']


def font(paths, size):
    for p in paths:
        if p and os.path.exists(p):
            return ImageFont.truetype(p, size)
    sys.exit('no caption font found; set CAPTION_FONT')


def run(*args):
    # -nostdin: run in the background, ffmpeg otherwise sits waiting for keyboard input (2026-10-22).
    if args[0] == 'ffmpeg':
        args = ('ffmpeg', '-nostdin') + args[1:]
    subprocess.run(args, check=True, stdin=subprocess.DEVNULL, stdout=subprocess.DEVNULL, stderr=subprocess.PIPE)


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


BADGE = badge() if mode != 'listing' else None


def end_card():
    """The listing video's close: darkened footage with the icon, the name, where it runs and where to find it."""
    layer = Image.new('RGBA', (W, H), (0, 0, 0, 120))
    icon = Image.open(os.path.join(repo, 'common/src/main/resources/assets/spawn_gazebo/icon.png')).convert('RGBA')
    layer.alpha_composite(icon.resize((200, 200), Image.LANCZOS), ((W - 200) // 2, 170))
    d = ImageDraw.Draw(layer)
    for i, (line, face, stroke, y) in enumerate(((END[0], font(FONTS, 96), 8, 400), (END[1], font(BADGE_FONTS, 50), 4, 540),
                                                 (END[2], font(BADGE_FONTS, 50), 4, 606))):
        parts = [(p, COPPER if k % 2 else (255, 255, 255)) for k, p in enumerate(line.split('*')) if p]
        x = (W - sum(d.textlength(p, font=face) for p, _ in parts)) / 2
        for text, colour in parts:
            d.text((x, y), text, font=face, fill=colour, stroke_width=stroke, stroke_fill=(0, 0, 0))
            x += d.textlength(text, font=face)
    return layer


END_CARD = end_card() if mode == 'listing' else None
END_FRAMES = 90  # the end card fades in over the last scene's last frames

# A Minecraft-ish pixel arrow, drawn at 3x.
ARROW = ["2", "22", "212", "2112", "21112", "211112", "2111112", "21111112", "211111112", "2111112222",
         "2112112", "212 2112", "22  2112", "2    2112", "     2112", "      22"]
cursor = Image.new('RGBA', (30, 48), (0, 0, 0, 0))
d = ImageDraw.Draw(cursor)
for y, row in enumerate(ARROW):
    for x, c in enumerate(row):
        if c in '12':
            d.rectangle([x * 3, y * 3, x * 3 + 2, y * 3 + 2], fill=(255, 255, 255, 255) if c == '1' else (0, 0, 0, 255))

with open(os.path.join(frames_dir, 'frames.tsv')) as f:
    cursor_at = {(r[0], int(r[1])): (r[2], r[3]) for r in csv.reader(f, delimiter='\t')}
with open(os.path.join(frames_dir, 'actions.tsv')) as f:
    actions = [(r[0], int(r[1]), r[2], r[3]) for r in csv.reader(f, delimiter='\t')]

work = os.path.join(out, 'work-' + mode)
shutil.rmtree(work, ignore_errors=True)
os.makedirs(work)

FADE_IN = 4  # frames for a caption to fade in; scenes cut hard
kept = {}
for scene, first, last, lines, top in SCENES:
    names = sorted(n for n in os.listdir(frames_dir) if n.startswith(scene + '_') and n.endswith('.png'))
    if not names:
        print('no frames for', scene, '(skipped)')
        continue
    end = len(names) if last is None else min(last + 1, len(names))
    # The build scene cuts as the gazebo appears (the command leaves the player inside its plinth).
    built = [f for s2, f, k, what in actions if s2 == scene and k == 'enter' and 'confirm' in what]
    if built:
        end = min(end, built[0] + 1)
    kept[scene] = (first, end)
    dest = os.path.join(work, scene)
    os.makedirs(dest)
    cap = caption(lines, top) if lines else None
    for i, n in enumerate(range(first, end)):
        img = Image.open(os.path.join(frames_dir, names[n])).convert('RGBA')
        sx, sy = W / img.width, H / img.height
        if img.size != (W, H):
            img = img.resize((W, H), Image.LANCZOS)
        x, y = cursor_at.get((scene, n), ('-', '-'))
        if x != '-':
            img.alpha_composite(cursor, (int(int(x) * sx), int(int(y) * sy)))
        if BADGE is not None:
            img.alpha_composite(BADGE)
        if END_CARD is not None and scene == SCENES[-1][0] and i >= (end - first) - END_FRAMES:
            a = min(1.0, (i - (end - first - END_FRAMES) + 1) / 12)
            layer = END_CARD.copy()
            layer.putalpha(layer.getchannel('A').point(lambda v: int(v * a)))
            img.alpha_composite(layer)
        if cap is not None:
            a = min(1.0, (i + 1) / FADE_IN)
            layer = cap.copy()
            layer.putalpha(layer.getchannel('A').point(lambda v: int(v * a)))
            img.alpha_composite(layer)
        img.convert('RGB').save(os.path.join(dest, '%04d.png' % i))

clips, starts, t = [], {}, 0.0
for scene, *_ in SCENES:
    if scene not in kept:
        continue
    clip = os.path.join(work, scene + '.mp4')
    run('ffmpeg', '-y', '-framerate', str(FPS), '-i', os.path.join(work, scene, '%04d.png'),
        '-vf', 'format=yuv420p', '-r', '30', '-c:v', 'libx264', '-preset', 'slow', '-crf', '18', clip)
    starts[scene] = t
    first, end = kept[scene]
    t += (end - first) / FPS
    clips.append(clip)
total = t
with open(os.path.join(work, 'list.txt'), 'w') as f:
    f.writelines(f"file '{c.replace(os.sep, '/')}'\n" for c in clips)
silent = os.path.join(out, NAME + '-silent.mp4')
run('ffmpeg', '-y', '-f', 'concat', '-safe', '0', '-i', os.path.join(work, 'list.txt'), '-c', 'copy', '-movflags', '+faststart', silent)

# The game's own sounds at the logged frames: the chest, the creeper's hiss and blast, a click per
# key typed, a pick against stone, and stone settling when the command builds a gazebo.
random.seed(7)
cues = []
mining = {}
for scene, frame, kind, what in actions:
    if scene not in starts:
        continue
    first, end = kept[scene]
    if not first <= frame < end:
        continue
    at = starts[scene] + (frame - first) / FPS
    if kind in ('chest_open', 'chest_close'):
        cues.append((at, kind, 0.95 + random.random() * 0.1, 0.5))
    elif kind == 'fuse':
        cues.append((at, 'fuse', 1.0, 0.6))
    elif kind == 'boom':
        cues.append((at, 'boom', 0.9 + random.random() * 0.1, 0.7))
    elif kind in ('key', 'enter'):
        cues.append((at, 'click', 1.6 + random.random() * 0.3, 0.12))
        if kind == 'enter' and 'confirm' in what:
            # The gazebo going up, on the cut.
            cues.append((at + 0.05, 'place', 0.6, 0.8))
            cues.append((at + 0.13, 'place', 0.8, 0.6))
    elif kind == 'built':
        cues.append((at, 'place', 0.6, 0.8))
        cues.append((at + 0.08, 'place', 0.8, 0.6))
    elif kind == 'mine':
        mining[what] = at
if 'start' in mining and 'stop' in mining:
    at = mining['start']
    while at < mining['stop']:
        cues.append((at, 'hit', 0.5 + random.random() * 0.1, 0.35))
        at += 0.25
inputs, filters = [], []
for i, (at, sound, pitch, volume) in enumerate(cues):
    inputs += ['-i', os.path.join(sounds_dir, sound + '.ogg')]
    ms = int(at * 1000)
    filters.append(f'[{i}:a]aformat=sample_rates=44100:channel_layouts=stereo,asetrate={44100 * pitch:.0f},aresample=44100,'
                   f'volume={volume},adelay={ms}|{ms}[s{i}]')
mix = ''.join(f'[s{i}]' for i in range(len(cues)))
filters.append(f'{mix}amix=inputs={len(cues)}:normalize=0,apad,atrim=0:{total:.3f}[a]')
audio = os.path.join(work, 'sound.wav')
run('ffmpeg', '-y', *inputs, '-filter_complex', ';'.join(filters), '-map', '[a]', audio)
run('ffmpeg', '-y', '-i', silent, '-i', audio, '-map', '0:v', '-map', '1:a', '-c:v', 'copy', '-c:a', 'aac', '-b:a', '160k',
    '-shortest', '-movflags', '+faststart', os.path.join(out, NAME + '.mp4'))

# Each scene's start, for add_voice.py's cues.
with open(os.path.join(out, NAME + '-timeline.json'), 'w') as f:
    json.dump(dict(starts, total=total), f, indent=1)

# The thumbnail: the gazebo from the first scene, with the mod's name large.
# The video's from the warm closing shot, the name on one line in the sky; the Short's from its
# opening shot, the name on two lines above the dome.
opener = SCENES[0][0] if mode == 'short' else 'l_morning' if mode == 'listing' else 'w_closing'
wide = mode in ('wide', 'listing')
tw, th = (1280, 720) if wide else (1080, 1920)
lines = [[('SPAWN ', (255, 255, 255)), ('GAZEBO', COPPER)]] if wide else [[('SPAWN', (255, 255, 255))], [('GAZEBO', COPPER)]]
size, top = (104, 34) if wide else (150, 300)
# A clean frame without the caption, from the raw footage, then the name.
raw =sorted(n for n in os.listdir(frames_dir) if n.startswith(opener + '_'))
thumb = Image.open(os.path.join(frames_dir, raw[len(raw) * 2 // 3])).convert('RGBA').resize((tw, th), Image.LANCZOS)
d = ImageDraw.Draw(thumb)
big = font(FONTS, size)
for i, parts in enumerate(lines):
    x = (tw - sum(d.textlength(t, font=big) for t, _ in parts)) / 2
    for text, colour in parts:
        d.text((x, top + i * size * 1.1), text, font=big, fill=colour, stroke_width=size // 12, stroke_fill=(0, 0, 0))
        x += d.textlength(text, font=big)
thumb.convert('RGB').save(os.path.join(out, NAME + '-thumbnail.png'))
# The video's scenes as GIFs for the listings, each under 5 MB: smaller and slower until it fits.
if mode == 'wide':
    for scene in kept:
        gif = os.path.join(out, f'spawn-gazebo-{scene[2:]}.gif')
        for width, fps in ((720, 15), (640, 12), (560, 10), (480, 10)):
            run('ffmpeg', '-y', '-i', os.path.join(work, scene + '.mp4'), '-vf',
                f'fps={fps},scale={width}:-1:flags=lanczos,split[a][b];[a]palettegen=max_colors=128[p];[b][p]paletteuse=dither=bayer:bayer_scale=4',
                gif)
            if os.path.getsize(gif) < 5_000_000:
                break
        print(f'  {os.path.basename(gif)}: {os.path.getsize(gif) / 1e6:.1f} MB')
print('total seconds', round(total, 2))
for scene in kept:
    first, end = kept[scene]
    print(f'  {scene}: {(end - first) / FPS:.2f} s')
