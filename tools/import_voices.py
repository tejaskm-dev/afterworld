#!/usr/bin/env python3
"""Brings recorded voice lines into the game.

Drop files into voice/incoming/ named <who>__<line id>.<ext> (mp3, wav, ogg or flac), exactly as
voice/VOICE_SCRIPT.md lists them, then run:

    python3 tools/import_voices.py            (needs numpy and soundfile; ffmpeg is used for mp3 if
                                               soundfile can't read it)

For each file it:
  - decodes and mixes to mono, resamples to 44.1 kHz,
  - trims leading and trailing silence, levels the loudness (about -16 LUFS-ish, peak -1 dB),
  - applies the character's treatment from tools/dialogue.py (CAST[who]['fx']): pitch shift, room,
    hall or cathedral reverb, metal ring, sand grain, rasp, air, giant sub-octave,
  - writes assets/afterworld/sounds/va/<who>/<line id>.ogg and its sound event,
  - records its length so subtitles type out in time with the voice,
then reruns tools/dialogue.py so the game's dialogue picks the recordings up. Processed files are
moved to voice/incoming/done/.
"""
import json, os, re, shutil, subprocess, sys, tempfile
import numpy as np

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import dialogue  # noqa: E402

ROOT = os.path.join(HERE, '..')
INCOMING = os.path.join(ROOT, 'voice', 'incoming')
DONE = os.path.join(INCOMING, 'done')
SOUNDS = os.path.join(ROOT, 'src', 'main', 'resources', 'assets', 'afterworld', 'sounds', 'va')
SOUNDS_JSON = os.path.join(ROOT, 'src', 'main', 'resources', 'assets', 'afterworld', 'sounds.json')
TIMING = os.path.join(ROOT, 'voice', 'timing.json')
BEATS = os.path.join(ROOT, 'voice', 'beats.json')
SR = 44100
NAME = re.compile(r'^([a-z_]+)__([a-z0-9_]+)\.(mp3|wav|ogg|flac|m4a)$')


def read_audio(path):
    import soundfile as sf
    try:
        x, sr = sf.read(path, dtype='float64', always_2d=True)
    except Exception:
        # Fall back to ffmpeg for formats libsndfile doesn't know.
        with tempfile.TemporaryDirectory() as d:
            wav = os.path.join(d, 'x.wav')
            subprocess.run(['ffmpeg', '-loglevel', 'error', '-y', '-i', path, wav], check=True)
            x, sr = sf.read(wav, dtype='float64', always_2d=True)
    x = x.mean(axis=1)
    if sr != SR:
        n = int(len(x) * SR / sr)
        x = np.interp(np.arange(n) * sr / SR, np.arange(len(x)), x)
    return x


def spectral(x, gain):
    n = len(x)
    f = np.fft.rfftfreq(n, 1.0 / SR)
    return np.fft.irfft(np.fft.rfft(x) * gain(f), n)


def trim(x, thresh_db=-42):
    env = np.convolve(np.abs(x), np.ones(441) / 441, mode='same')
    thresh = 10 ** (thresh_db / 20) * (np.max(np.abs(x)) + 1e-9)
    idx = np.where(env > thresh)[0]
    if len(idx) == 0:
        return x
    a = max(0, idx[0] - int(0.03 * SR))
    b = min(len(x), idx[-1] + int(0.15 * SR))
    return x[a:b]


def stretch(x, rate, n_fft=2048, hop=512):
    """Phase-vocoder time stretch (rate > 1: longer), with phase locking to the strongest peaks so
    voices stay clear rather than phasey."""
    w = np.hanning(n_fft)
    pad = np.concatenate([np.zeros(n_fft), x, np.zeros(n_fft)])
    frames = 1 + (len(pad) - n_fft) // hop
    idx = np.arange(n_fft)[None, :] + hop * np.arange(frames)[:, None]
    X = np.fft.rfft(pad[idx] * w, axis=1)
    hs = hop * rate
    k = np.arange(X.shape[1])
    omega = 2 * np.pi * k * hop / n_fft
    mag, ph = np.abs(X), np.angle(X)
    out_len = int(frames * hs + n_fft) + 1
    out = np.zeros(out_len)
    norm = np.zeros(out_len)
    acc = ph[0].copy()
    for t in range(frames):
        if t > 0:
            d = ph[t] - ph[t - 1] - omega
            d = (d + np.pi) % (2 * np.pi) - np.pi
            acc = acc + (omega + d) * rate
            # Identity phase locking: bins follow the phase of their nearest spectral peak.
            m = mag[t]
            peaks = np.where((m[1:-1] > m[:-2]) & (m[1:-1] >= m[2:]))[0] + 1
            if len(peaks):
                near = peaks[np.clip(np.searchsorted(peaks, k), 0, len(peaks) - 1)]
                left = peaks[np.clip(np.searchsorted(peaks, k) - 1, 0, len(peaks) - 1)]
                near = np.where(np.abs(left - k) < np.abs(near - k), left, near)
                locked = acc[near] + ph[t] - ph[t][near]
                acc = locked
        y = np.fft.irfft(mag[t] * np.exp(1j * acc), n_fft) * w
        s = int(t * hs)
        out[s:s + n_fft] += y
        norm[s:s + n_fft] += w ** 2
    norm[norm < 1e-6] = 1
    out = out / norm
    a = int(n_fft * rate)
    return out[a:a + int(len(x) * rate)]


def pitch(x, semis):
    """Pitch shift that keeps the length: stretch in time by the ratio, then resample back."""
    if abs(semis) < 0.01:
        return x
    f = 2 ** (semis / 12.0)
    y = stretch(x, f)
    n = len(x)
    return np.interp(np.arange(n) * (len(y) / n), np.arange(len(y)), y)


def reverb(x, length, wet, bright=0.5, seed=3):
    rng = np.random.default_rng(seed)
    n = int(length * SR)
    t = np.arange(n) / SR
    ir = rng.standard_normal(n) * np.exp(-t * 6.9 / length)
    k = max(1, int((1 - bright) * 24))
    ir = np.convolve(ir, np.ones(k) / k, mode='same')
    ir[: int(0.015 * SR)] = 0
    # A few early reflections make a room read as a room.
    for d, g in ((0.011, 0.5), (0.019, 0.35), (0.027, 0.25)):
        ir[int(d * SR)] += g * np.sqrt(np.sum(ir ** 2) / n) * 40
    ir /= np.sqrt(np.sum(ir ** 2)) + 1e-9
    L = len(x) + n
    tail = np.fft.irfft(np.fft.rfft(x, L) * np.fft.rfft(ir, L), L)
    dry = np.concatenate([x, np.zeros(n)])
    return (1 - wet * 0.35) * dry + wet * tail * (np.max(np.abs(x)) / (np.max(np.abs(tail)) + 1e-9))


def metal(x, amount):
    """A comb-filter ring: the voice through a brass throat."""
    d = int(0.0045 * SR)
    y = x.copy()
    for k in range(1, 6):
        y[k * d:] += (0.55 ** k) * x[:-k * d] * (1 if k % 2 else -1)
    return (1 - amount) * x + amount * y / np.max(np.abs(y) + 1e-9) * np.max(np.abs(x))


def sand(x, amount):
    """Grit: granular dropouts and crackle following the voice's envelope."""
    rng = np.random.default_rng(11)
    env = np.convolve(np.abs(x), np.ones(220) / 220, mode='same')
    crackle = rng.standard_normal(len(x)) * env * (rng.random(len(x)) < 0.08)
    grains = np.repeat(rng.random(len(x) // 220 + 1) > amount * 0.25, 220)[:len(x)]
    return x * (0.75 + 0.25 * grains) + amount * 0.6 * crackle


def rasp(x, amount):
    env = np.convolve(np.abs(x), np.ones(441) / 441, mode='same')
    noise = np.random.default_rng(7).standard_normal(len(x)) * env * amount
    sat = np.tanh(x * (2 + 6 * amount)) / np.tanh(2 + 6 * amount)
    return (1 - amount) * x + amount * sat + noise


def air(x):
    up = pitch(x, 12)
    return x + 0.07 * np.convolve(up, np.ones(96) / 96, mode='same')


def giant(x):
    """Weight: a soft sub-octave under the voice, kept low and dark."""
    sub = pitch(x, -12)
    sub = spectral(sub, lambda f: 1 / np.sqrt(1 + (f / 900) ** 4))
    return x + 0.22 * sub


def level(x, target_rms=0.12, peak=0.89):
    rms = np.sqrt(np.mean(x ** 2)) + 1e-9
    x = x * (target_rms / rms)
    p = np.max(np.abs(x))
    if p > peak:
        # Gentle limiting rather than a hard squash.
        x = np.tanh(x / peak * 1.2) / np.tanh(1.2) * peak
    return x


def treat(x, fx):
    x = spectral(x, lambda f: (f / 70) ** 2 / (1 + (f / 70) ** 2))     # high-pass rumble
    if 'pitch' in fx:
        x = pitch(x, fx['pitch'])
    if 'rasp' in fx:
        x = rasp(x, fx['rasp'])
    if 'sand' in fx:
        x = sand(x, fx['sand'])
    if fx.get('giant'):
        x = giant(x)
    if 'metal' in fx:
        x = metal(x, fx['metal'])
    if fx.get('air'):
        x = air(x)
    if 'room' in fx:
        x = reverb(x, 0.35, fx['room'], bright=0.6)
    if 'hall' in fx:
        x = reverb(x, 1.6, fx['hall'], bright=0.45)
    if 'cathedral' in fx:
        x = reverb(x, 3.2, fx['cathedral'], bright=0.35, seed=5)
    return level(x)


def beats(x, steps=10):
    """When each tenth of the line's text should be on screen (ms): text advances with the voice
    and waits while it pauses, so words appear as they are spoken."""
    hop = int(0.01 * SR)
    n = len(x) // hop
    if n < 4:
        return [0] * (steps + 1)
    e = np.array([np.sqrt(np.mean(x[i * hop:(i + 1) * hop] ** 2)) + 1e-9 for i in range(n)])
    db = 20 * np.log10(e)
    lo, hi = np.percentile(db, 10), np.percentile(db, 95)
    voiced = db > lo + (hi - lo) * 0.35
    c = np.cumsum(voiced)
    total = max(1, c[-1])
    out = []
    for p in range(steps + 1):
        target = total * p / steps
        k = int(np.searchsorted(c, max(1, target)))
        out.append(int(min(n, k + 1) * 10))
    out[0] = int(np.argmax(voiced) * 10)
    return out


def write_ogg(path, x):
    import soundfile as sf
    os.makedirs(os.path.dirname(path), exist_ok=True)
    data = x.astype(np.float32)
    with sf.SoundFile(path, 'w', SR, 1, subtype='VORBIS', format='OGG', compression_level=0.6) as out:
        for i in range(0, len(data), 8192):
            out.write(data[i:i + 8192])


def main():
    src = os.path.abspath(sys.argv[1]) if len(sys.argv) > 1 else INCOMING
    keep = src != os.path.abspath(INCOMING)
    if not os.path.isdir(src):
        print(f'No {src} folder yet.')
        return
    timing = json.load(open(TIMING)) if os.path.exists(TIMING) else {}
    beat_map = json.load(open(BEATS)) if os.path.exists(BEATS) else {}
    with open(SOUNDS_JSON) as f:
        sounds = json.load(f)
    known = {(w, k) for (w, k, _) in all_lines()}
    done = 0
    for name in sorted(os.listdir(src)):
        m = NAME.match(name)
        if not m:
            continue
        who, key = m.group(1), m.group(2)
        if (who, key) not in known:
            print(f'  ? {name}: no such line in the script (check the file name)')
            continue
        x = read_audio(os.path.join(src, name))
        x = trim(x)
        x = treat(x, dialogue.CAST[who]['fx'])
        write_ogg(os.path.join(SOUNDS, who, key + '.ogg'), x)
        timing[f'{who}/{key}'] = int(round(len(x) / SR * 20))
        beat_map[f'{who}/{key}'] = beats(x)
        sounds[f'va.{who}.{key}'] = {'sounds': [{'name': f'afterworld:va/{who}/{key}', 'stream': False}]}
        if not keep:
            os.makedirs(DONE, exist_ok=True)
            shutil.move(os.path.join(INCOMING, name), os.path.join(DONE, name))
        done += 1
        print(f'  + {who}/{key} ({len(x) / SR:.1f}s)')
    os.makedirs(os.path.dirname(TIMING), exist_ok=True)
    with open(TIMING, 'w') as f:
        json.dump(timing, f, indent=0, sort_keys=True)
    with open(BEATS, 'w') as f:
        json.dump(beat_map, f, indent=0, sort_keys=True)
    with open(SOUNDS_JSON, 'w') as f:
        json.dump(sounds, f, indent=2)
    dialogue.main()
    print(f'{done} recordings imported')


def all_lines():
    for npc, script in dialogue.SCRIPT.items():
        for node_id, node in script['nodes'].items():
            for i, text in enumerate(node['lines']):
                yield npc, f'{node_id}_{i}', text
    for who, groups in dialogue.BARKS.items():
        for group, lines in groups.items():
            for i, text in enumerate(lines):
                yield who, f'{group}_{i}', text


if __name__ == '__main__':
    main()
