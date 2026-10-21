"""Lays a recorded voice-over on a video, over its game sounds and a music track that dips under the voice.

python add_voice.py <video.mp4> <voice recording> <music track> <out.mp4> <cues>

<cues> is a comma-separated list of "from-to@at": the stretch of the recording (seconds) and where
in the video it starts, e.g. "3.62-6.2@0.15,7.62-9.35@3.1". Each piece fades in and out over 30 ms.
The voice is cleaned up (low rumble cut, gently compressed) and brought to about -16 LUFS; the music
sits softly under everything and ducks further while the voice speaks; the whole mix ends at about
-14 LUFS, as add_music.py does. The video stream is copied as it is.
"""
import json, os, shutil, subprocess, sys, tempfile

video, voice, track, out, cues = sys.argv[1:6]
probe = subprocess.run(['ffprobe', '-v', 'error', '-show_entries', 'format=duration', '-of', 'json', video],
                       check=True, capture_output=True, text=True)
length = float(json.loads(probe.stdout)['format']['duration'])

pieces = []
for cue in cues.split(','):
    span, at = cue.split('@')
    a, b = (float(x) for x in span.split('-'))
    pieces.append((a, b, float(at)))

def loudness(inputs, chain):
    """The integrated loudness (LUFS) of a filter chain's output, measured in a first pass.

    One-pass loudnorm rides the gain and came out loud on the first word, so each part gets one
    fixed gain from this measurement instead."""
    r = subprocess.run(['ffmpeg', '-v', 'info', *inputs, '-filter_complex', chain + ',ebur128', '-f', 'null', '-'],
                       capture_output=True, text=True)
    summary = r.stderr[r.stderr.rfind('Summary:'):]
    return float(summary.split('I:')[1].split('LUFS')[0])


work = tempfile.mkdtemp()
voice_wav, mix_wav = os.path.join(work, 'voice.wav'), os.path.join(work, 'mix.wav')


def ffmpeg(*args):
    subprocess.run(['ffmpeg', '-y', '-v', 'error', *args], check=True)


# 1. The voice pieces, placed and cleaned up (low rumble cut, gently compressed).
graph = []
for i, (a, b, at) in enumerate(pieces):
    d = b - a
    ms = int(at * 1000)
    graph.append(f'[0:a]atrim={a}:{b},asetpts=PTS-STARTPTS,aformat=sample_rates=48000:channel_layouts=stereo,'
                 f'afade=t=in:d=0.03,afade=t=out:st={max(0.0, d - 0.03):.3f}:d=0.03,adelay={ms}|{ms}[v{i}]')
mix = ''.join(f'[v{i}]' for i in range(len(pieces)))
graph.append(f'{mix}amix=inputs={len(pieces)}:normalize=0,apad=whole_dur={length:.3f},'
             'highpass=f=80,acompressor=threshold=-20dB:ratio=3:attack=5:release=120[out]')
ffmpeg('-i', voice, '-filter_complex', ';'.join(graph), '-map', '[out]', '-t', f'{length:.3f}', voice_wav)

# 2. Game sounds, the music (soft, ducking under the voice) and the voice, each at one fixed gain.
music = (f'atrim=duration={length:.3f},asetpts=PTS-STARTPTS,aformat=sample_rates=48000:channel_layouts=stereo,'
         f'afade=t=in:d=0.4,afade=t=out:st={max(0.0, length - 1):.3f}:d=1')
voice_gain = -16 - loudness(['-i', voice_wav], '[0:a]anull')
music_gain = -25 - loudness(['-i', track], '[0:a]' + music)
graph = [f'[1:a]volume={voice_gain:.2f}dB,asplit=2[voice][key]',
         f'[2:a]{music},volume={music_gain:.2f}dB[m0]',
         '[m0][key]sidechaincompress=threshold=0.03:ratio=5:attack=20:release=350[music]',
         '[0:a]aformat=sample_rates=48000:channel_layouts=stereo[game]',
         '[game][music][voice]amix=inputs=3:normalize=0[out]']
ffmpeg('-i', video, '-i', voice_wav, '-i', track, '-filter_complex', ';'.join(graph), '-map', '[out]',
       '-t', f'{length:.3f}', mix_wav)

# 3. The whole mix to about -14 LUFS with one gain and a limiter, and onto the video.
gain = -14 - loudness(['-i', mix_wav], '[0:a]anull')
ffmpeg('-i', video, '-i', mix_wav, '-filter_complex', f'[1:a]volume={gain:.2f}dB,alimiter=limit=0.84:level=false[a]',
       '-map', '0:v', '-map', '[a]', '-c:v', 'copy', '-c:a', 'aac', '-b:a', '192k', '-t', f'{length:.3f}',
       '-movflags', '+faststart', out)
print(out, f'(voice {voice_gain:+.1f} dB, music {music_gain:+.1f} dB, mix {gain:+.1f} dB)')
shutil.rmtree(work, ignore_errors=True)
