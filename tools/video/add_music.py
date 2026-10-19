"""Mixes a music track softly under a video's game sounds and brings the whole mix to YouTube's
loudness (about -14 LUFS).

python add_music.py <video.mp4> <track> <out.mp4> [start seconds]

The track starts at `start` (default 0; skip a quiet intro), is levelled to sit under the game's
own sounds, fades in briefly and out over the last second, so a looping Short doesn't jump. The
video stream is copied as it is. Use music from YouTube's Audio Library only, never Minecraft's
music or popular songs.
"""
import json, subprocess, sys

video, track, out = sys.argv[1:4]
start = float(sys.argv[4]) if len(sys.argv) > 4 else 0.0
probe = subprocess.run(['ffprobe', '-v', 'error', '-show_entries', 'format=duration', '-of', 'json', video],
                       check=True, capture_output=True, text=True)
length = float(json.loads(probe.stdout)['format']['duration'])
fade = 1.0
music = (f'[1:a]atrim=start={start}:duration={length},asetpts=PTS-STARTPTS,'
         f'aformat=sample_rates=44100:channel_layouts=stereo,loudnorm=I=-26:TP=-6,'
         f'afade=t=in:d=0.4,afade=t=out:st={max(0.0, length - fade):.3f}:d={fade}[m]')
game = '[0:a]aformat=sample_rates=44100:channel_layouts=stereo[g]'
mix = '[g][m]amix=inputs=2:normalize=0,loudnorm=I=-14:TP=-1.5:LRA=11,aresample=44100[a]'
subprocess.run(['ffmpeg', '-y', '-v', 'error', '-i', video, '-i', track, '-filter_complex', ';'.join([music, game, mix]),
                '-map', '0:v', '-map', '[a]', '-c:v', 'copy', '-c:a', 'aac', '-b:a', '192k', '-shortest',
                '-movflags', '+faststart', out], check=True)
print(out)
