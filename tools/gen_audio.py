#!/usr/bin/env python3
"""Synthesizes every Afterworld sound and the music, and writes sounds.json.
Needs numpy + soundfile (with Vorbis). Run: <venv>/bin/python tools/gen_audio.py"""
import json, math, os
import numpy as np
import soundfile as sf

SR = 44100
HERE = os.path.dirname(os.path.abspath(__file__))
ASSETS = os.path.join(HERE, '..', 'src', 'main', 'resources', 'assets', 'afterworld')
SOUNDS = os.path.join(ASSETS, 'sounds')
RNG = np.random.default_rng(20261010)

# ---------------------------------------------------------------- primitives

def n_of(d):
    return int(SR * d)

def tline(d):
    return np.arange(n_of(d)) / SR

def midi(m):
    return 440.0 * 2 ** ((m - 69) / 12)

def phase_of(freq, n):
    f = np.broadcast_to(np.asarray(freq, dtype=np.float64), (n,))
    return 2 * np.pi * np.cumsum(f) / SR

def sine(freq, d, phase0=0.0):
    n = n_of(d)
    return np.sin(phase_of(freq, n) + phase0)

def saw(freq, d, harmonics=24):
    n = n_of(d)
    ph = phase_of(freq, n)
    out = np.zeros(n)
    f0 = float(np.max(freq)) if np.ndim(freq) else freq
    for k in range(1, harmonics + 1):
        if f0 * k > SR / 2.2:
            break
        out += np.sin(k * ph) / k
    return out * 0.6

def glide(d, *points):
    """Frequency curve through (fraction, hz) points, log-interpolated, exactly n_of(d) samples."""
    x = np.linspace(0, 1, n_of(d))
    return np.exp(np.interp(x, [p[0] for p in points], [math.log(p[1]) for p in points]))

def noise(d):
    return RNG.standard_normal(n_of(d))

def adsr(n, a, d, s, r):
    env = np.ones(n) * s
    na, nd, nr = int(a * SR), int(d * SR), int(r * SR)
    na = min(na, n)
    env[:na] = np.linspace(0, 1, na, endpoint=False) if na else env[:na]
    end_d = min(n, na + nd)
    if end_d > na:
        env[na:end_d] = np.linspace(1, s, end_d - na)
    if nr > 0 and nr < n:
        env[n - nr:] *= np.linspace(1, 0, nr)
    return env

def decay(n, tau):
    return np.exp(-np.arange(n) / (tau * SR))

def fade(x, fin=0.005, fout=0.02):
    x = x.copy()
    a, b = int(fin * SR), int(fout * SR)
    if a:
        x[:a] *= np.linspace(0, 1, a)
    if b:
        x[-b:] *= np.linspace(1, 0, b)
    return x

def spectral(x, response):
    X = np.fft.rfft(x)
    f = np.fft.rfftfreq(len(x), 1 / SR)
    return np.fft.irfft(X * response(f), len(x))

def lowpass(x, cutoff, order=2):
    return spectral(x, lambda f: 1 / np.sqrt(1 + (f / cutoff) ** (2 * order)))

def highpass(x, cutoff, order=2):
    return spectral(x, lambda f: 1 / np.sqrt(1 + (cutoff / np.maximum(f, 1e-3)) ** (2 * order)))

def bandpass(x, lo, hi, order=2):
    return highpass(lowpass(x, hi, order), lo, order)

def sweep_filter(x, c0, c1, mode='low', chunks=48):
    """Time-varying filter by overlapping chunks (cheap and good enough for sweeps)."""
    n = len(x)
    out = np.zeros(n)
    size = max(256, n // chunks)
    hop = size // 2
    win = np.hanning(size)
    for start in range(0, n, hop):
        seg = x[start:start + size]
        if len(seg) < 16:
            break
        t = start / max(1, n)
        c = c0 * (c1 / c0) ** t
        y = lowpass(seg, c) if mode == 'low' else highpass(seg, c) if mode == 'high' else bandpass(seg, c * 0.7, c * 1.4)
        out[start:start + len(seg)] += y * win[:len(seg)]
    return out

def comb(x, D, g):
    y = x.astype(np.float64).copy()
    for s in range(D, len(x), D):
        e = min(s + D, len(x))
        y[s:e] += g * y[s - D:e - D]
    return y

def allpass(x, D, g):
    xd = np.concatenate([np.zeros(D), x[:-D]])
    base = -g * x + xd
    y = base.copy()
    for s in range(D, len(x), D):
        e = min(s + D, len(x))
        y[s:e] = base[s:e] + g * y[s - D:e - D]
    return y

def reverb(x, size=1.0, wet=0.3, tail=2.5, stereo=True, damp=5000):
    pad = np.concatenate([x, np.zeros(n_of(tail))])
    src = lowpass(pad, damp, 1)
    outs = []
    for ch, off in enumerate((0, 37) if stereo else (0,)):
        acc = np.zeros(len(pad))
        for D, g in ((1116, 0.84), (1188, 0.83), (1277, 0.82), (1356, 0.81)):
            acc += comb(src, int((D + off) * size), g)
        acc = allpass(acc, int(556 * size) + off, 0.5)
        acc = allpass(acc, int(441 * size) + off // 2, 0.5)
        outs.append(pad * (1 - wet) + acc * wet * 0.18)
    return np.stack(outs, axis=1) if stereo else outs[0]

def bell(freq, d, partials=((1.0, 1.0, 1.0), (2.756, 0.55, 0.55), (5.404, 0.35, 0.32), (8.933, 0.2, 0.2), (0.5, 0.45, 1.6), (1.19, 0.25, 0.8)), detune=0.0):
    n = n_of(d)
    out = np.zeros(n)
    for ratio, amp, tau in partials:
        f = freq * ratio * (1 + detune * RNG.uniform(-1, 1))
        if f > SR / 2.2:
            continue
        out += amp * np.sin(phase_of(f, n)) * decay(n, tau * d / 2.0)
    att = min(n, int(0.003 * SR))
    out[:att] *= np.linspace(0, 1, att)
    return out

def pluck(freq, d, damping=0.996, bright=6000):
    n = n_of(d)
    P = max(2, int(SR / freq))
    y = np.zeros(n + P + 1)
    y[:P] = lowpass(RNG.uniform(-1, 1, P * 4), bright)[:P]
    for s in range(P, n, P):
        e = min(s + P, n)
        prev = y[s - P:e - P]
        prev2 = np.concatenate([[y[s - P - 1] if s - P - 1 >= 0 else 0.0], y[s - P:e - P - 1]])
        y[s:e] = damping * 0.5 * (prev + prev2[:len(prev)])
    return y[:n]

def fm(carrier, ratio, index, d, index_decay=0.4):
    n = n_of(d)
    t = np.arange(n) / SR
    mod_env = index * np.exp(-t / max(0.01, index_decay))
    mod = np.sin(2 * np.pi * carrier * ratio * t) * mod_env
    return np.sin(2 * np.pi * carrier * t + mod)

def mix(*parts):
    n = max(len(p) for p in parts)
    out = np.zeros(n) if parts[0].ndim == 1 else np.zeros((n, parts[0].shape[1]))
    for p in parts:
        out[:len(p)] += p
    return out

def at(x, offset, total):
    out = np.zeros(n_of(total))
    s = n_of(offset)
    e = min(len(out), s + len(x))
    if e > s:
        out[s:e] += x[:e - s]
    return out

def pan(x, p):
    l = np.cos((p + 1) * np.pi / 4)
    r = np.sin((p + 1) * np.pi / 4)
    return np.stack([x * l, x * r], axis=1)

def norm(x, peak=0.9):
    m = np.max(np.abs(x))
    return x * (peak / m) if m > 0 else x

WRITTEN = {}

def dc_block(x):
    # Zero-padded so the FFT filter's wrap-around can't leak the attack into the tail.
    pad = n_of(0.1)
    y = highpass(np.concatenate([np.zeros(pad), x, np.zeros(pad)]), 20, 1)
    return fade(y[pad:pad + len(x)], 0.001, 0.01)

def write(name, x, peak=0.9, stream=False):
    x = np.asarray(x, dtype=np.float64)
    x = dc_block(x) if x.ndim == 1 else np.stack([dc_block(x[:, c]) for c in range(x.shape[1])], axis=1)
    x = norm(x, peak)
    path = os.path.join(SOUNDS, *name.split('/')) + '.ogg'
    os.makedirs(os.path.dirname(path), exist_ok=True)
    data = x.astype(np.float32)
    channels = 1 if data.ndim == 1 else data.shape[1]
    # libsndfile's Vorbis encoder can crash on one huge write, so feed it in small blocks.
    # Music is streamed and long, so it gets a slightly lower quality to keep the jar small.
    with sf.SoundFile(path, 'w', SR, channels, subtype='VORBIS', format='OGG',
                      compression_level=0.68 if stream else 0.6) as out:
        for i in range(0, len(data), 8192):
            out.write(data[i:i + 8192])
    WRITTEN[name] = stream
    return path

# ---------------------------------------------------------------- music

def pad_chord(notes, d, attack=2.5, release=3.0, voices=3, cents=6, bright=1800):
    n = n_of(d)
    left = np.zeros(n)
    right = np.zeros(n)
    for m in notes:
        f = midi(m)
        for v in range(voices):
            c = (v - (voices - 1) / 2) * cents
            fv = f * 2 ** (c / 1200)
            tone = np.sin(phase_of(fv, n) + RNG.uniform(0, 6.28)) + 0.25 * np.sin(2 * phase_of(fv, n)) + 0.08 * np.sin(3 * phase_of(fv, n))
            if v % 2 == 0:
                left += tone
            else:
                right += tone
            if voices % 2 == 1 and v == voices // 2:
                right += tone * 0.5
                left += tone * 0.5
    env = adsr(n, attack, 0.5, 0.85, release)
    left = lowpass(left * env, bright)
    right = lowpass(right * env, bright)
    return np.stack([left, right], axis=1) / (len(notes) * voices)

def music_title():
    chords = [[50, 57, 60, 64, 65], [46, 53, 57, 62], [41, 48, 52, 57, 60], [48, 55, 62, 64],
              [50, 57, 60, 64, 65], [43, 50, 53, 58], [46, 53, 57, 62, 65], [45, 50, 52, 57],
              [50, 57, 60, 64, 65], [46, 53, 57, 62], [43, 50, 53, 58, 62], [45, 50, 52, 57, 61]]
    seg = 8.0
    total = seg * len(chords) + 6
    out = np.zeros((n_of(total), 2))
    for i, ch in enumerate(chords):
        p = pad_chord(ch, seg + 3.0)
        s = n_of(i * seg)
        out[s:s + len(p)] += p * 0.55
    drone = sine(midi(38), total) * 0.25 + sine(midi(45), total) * 0.1
    swell = 0.6 + 0.4 * np.sin(np.linspace(0, 6 * np.pi, len(drone)))
    out += np.stack([drone * swell, drone * swell], axis=1) * 0.35
    scale = [62, 64, 65, 67, 69, 72, 74, 76, 77, 79, 81]
    mel = np.zeros((n_of(total), 2))
    for i, ch in enumerate(chords):
        tones = [m + 12 for m in ch if m + 12 >= 60] + [m + 24 for m in ch if m + 24 <= 84]
        count = 2 if i % 4 != 3 else 3
        times = sorted(RNG.uniform(0.3, seg - 1.2, count))
        for k, t0 in enumerate(times):
            note = int(RNG.choice(tones)) if RNG.random() < 0.7 else int(RNG.choice(scale))
            b = bell(midi(note), 3.2, detune=0.0005) * (0.5 if note > 76 else 0.6)
            mel += at_stereo(pan(b, float(RNG.uniform(-0.6, 0.6))), i * seg + t0, total)
    mel_l = reverb(mel[:, 0], size=1.3, wet=0.55, tail=0.1, stereo=False)
    mel_r = reverb(mel[:, 1], size=1.35, wet=0.55, tail=0.1, stereo=False)
    out[:len(mel_l), 0] += mel_l[:len(out)] * 0.5
    out[:len(mel_r), 1] += mel_r[:len(out)] * 0.5
    wind = lowpass(noise(total), 500) * (0.5 + 0.5 * np.sin(np.linspace(0, 9, n_of(total))))
    out += np.stack([wind, np.roll(wind, 4410)], axis=1) * 0.03
    out[:n_of(4)] *= np.linspace(0, 1, n_of(4))[:, None]
    out[-n_of(6):] *= np.linspace(1, 0, n_of(6))[:, None]
    write('music/title', out, peak=0.8, stream=True)

def at_stereo(x, offset, total):
    out = np.zeros((n_of(total), 2))
    s = n_of(offset)
    e = min(len(out), s + len(x))
    if e > s:
        out[s:e] += x[:e - s]
    return out

def music_wilds():
    chords = [[45, 52, 55, 60, 64], [41, 48, 52, 57, 60], [48, 55, 60, 64, 67], [43, 50, 55, 59, 62]] * 2
    seg = 9.0
    total = seg * len(chords) + 5
    out = np.zeros((n_of(total), 2))
    for i, ch in enumerate(chords):
        p = pad_chord(ch, seg + 3, attack=3.0, release=3.5, bright=1300)
        s = n_of(i * seg)
        out[s:s + len(p)] += p * 0.45
    penta = [57, 60, 62, 64, 67, 69, 72, 74, 76]
    arp = np.zeros((n_of(total), 2))
    for i, ch in enumerate(chords):
        base = i * seg
        pattern = [0, 2, 4, 3, 1, 4, 2, 5] if i % 2 == 0 else [4, 3, 1, 0, 2, 5, 3, 4]
        for k, idx in enumerate(pattern):
            if RNG.random() < 0.18:
                continue
            note = penta[idx % len(penta)] + (12 if i >= 4 and k % 3 == 0 else 0)
            pl = pluck(midi(note), 2.6, damping=0.9975) * 0.5
            arp += at_stereo(pan(pl, -0.5 + k / 8), base + 0.6 + k * 1.0 + RNG.uniform(0, 0.05), total)
    rl = reverb(arp[:, 0], size=1.1, wet=0.45, tail=0.1, stereo=False)
    rr = reverb(arp[:, 1], size=1.12, wet=0.45, tail=0.1, stereo=False)
    out[:, 0] += rl[:len(out)] * 0.6
    out[:, 1] += rr[:len(out)] * 0.6
    birds = np.zeros(n_of(total))
    for _ in range(14):
        t0 = RNG.uniform(2, total - 2)
        f0 = RNG.uniform(2400, 3800)
        chirp = np.sin(phase_of(np.linspace(f0, f0 * 1.3, n_of(0.12)), n_of(0.12))) * np.hanning(n_of(0.12))
        birds += at(chirp * 0.12, t0, total) + at(chirp * 0.08, t0 + 0.18, total)
    out += np.stack([birds, np.roll(birds, 900)], axis=1) * 0.5
    out[:n_of(4)] *= np.linspace(0, 1, n_of(4))[:, None]
    out[-n_of(5):] *= np.linspace(1, 0, n_of(5))[:, None]
    write('music/wilds', out, peak=0.58, stream=True)

def drum(d=1.2, f0=70, f1=38):
    n = n_of(d)
    f = f1 + (f0 - f1) * np.exp(-np.arange(n) / (0.06 * SR))
    body = np.sin(phase_of(f, n)) * decay(n, 0.35)
    hit = lowpass(noise(d), 1800) * decay(n, 0.03) * 0.6
    return body + hit

def music_boss():
    bpm = 100
    beat = 60 / bpm
    bars = 24
    total = bars * 4 * beat + 3
    out = np.zeros((n_of(total), 2))
    roots = [38, 38, 41, 40, 38, 38, 43, 45] * 3
    for bar in range(bars):
        t0 = bar * 4 * beat
        root = roots[bar]
        for b in range(4):
            if b in (0, 2) or (b == 3 and bar % 2 == 1):
                d = drum(1.0, 75 if b == 0 else 64, 40)
                out += at_stereo(pan(d, 0.0), t0 + b * beat, total) * (0.9 if b == 0 else 0.6)
        for k, step in enumerate([0, 0, 3, 2, 0, 0, 5, 3]):
            f = midi(root + step)
            s = saw(f, beat * 0.5, 14) * adsr(n_of(beat * 0.5), 0.005, 0.1, 0.6, 0.08)
            s = lowpass(s, 900)
            out += at_stereo(pan(s, -0.2 if k % 2 else 0.2), t0 + k * beat * 0.5, total) * 0.28
        if bar % 4 == 0:
            cl = sum(sine(midi(m), 8 * beat) for m in (root + 24, root + 25, root + 31)) * adsr(n_of(8 * beat), 3.0, 0.5, 0.6, 2.0)
            out += at_stereo(pan(lowpass(cl, 2500), 0.0), t0, total) * 0.07
        for b in range(4):
            tick = highpass(noise(0.02), 3000) * decay(n_of(0.02), 0.004)
            out += at_stereo(pan(tick, 0.6), t0 + b * beat + beat / 2, total) * 0.25
    l = reverb(out[:, 0], size=0.9, wet=0.25, tail=0.1, stereo=False)
    r = reverb(out[:, 1], size=0.92, wet=0.25, tail=0.1, stereo=False)
    out = np.stack([l[:len(out)], r[:len(out)]], axis=1)
    out[-n_of(3):] *= np.linspace(1, 0, n_of(3))[:, None]
    write('music/boss', out, peak=0.6, stream=True)

# ---------------------------------------------------------------- sound effects

def page(seed=0):
    d = 0.32
    x = sweep_filter(noise(d), 1800, 5200, 'band') * adsr(n_of(d), 0.02, 0.1, 0.5, 0.15)
    flutter = 1 + 0.5 * np.sin(2 * np.pi * (22 + seed * 4) * tline(d))
    return fade(x * flutter)

def sfx():
    for i in range(3):
        write(f'ui/page{i + 1}', page(i), 0.6)
    click = lowpass(noise(0.03), 2500) * decay(n_of(0.03), 0.006)
    chime = bell(1318, 0.25) * 0.5
    write('ui/select', mix(click, chime), 0.55)
    thump = lowpass(noise(0.15), 300) * decay(n_of(0.15), 0.03)
    write('ui/open', mix(thump * 1.2, at(page(1), 0.06, 0.5)), 0.7)
    arp = np.zeros(n_of(1.6))
    for k, m in enumerate((76, 81, 88)):
        arp += at(bell(midi(m), 1.3) * (0.7 - k * 0.12), k * 0.09, 1.6)
    shimmer = highpass(noise(1.6), 6000) * decay(n_of(1.6), 0.4) * 0.05
    write('ui/discovery', reverb(arp + shimmer, 0.8, 0.35, 0.8, stereo=False), 0.75)
    breath = bandpass(noise(0.9), 400, 2200) * adsr(n_of(0.9), 0.2, 0.2, 0.6, 0.4)
    low = sine(midi(57), 0.9) * adsr(n_of(0.9), 0.05, 0.3, 0.5, 0.4) * 0.3
    write('ui/rumour', reverb(breath * 0.5 + low, 0.7, 0.3, 0.5, stereo=False), 0.6)
    scratch = np.zeros(n_of(1.0))
    for k in range(7):
        burst = highpass(noise(0.05), 3500) * decay(n_of(0.05), 0.012)
        scratch += at(burst * RNG.uniform(0.4, 1.0), k * 0.09 + RNG.uniform(0, 0.03), 1.0)
    write('ui/chronicle', mix(scratch * 0.6, at(bell(midi(62), 0.9) * 0.5, 0.55, 1.6)), 0.6)
    toll = bell(midi(43), 3.0, detune=0.001)
    swell = lowpass(noise(3.0), 900) * adsr(n_of(3.0), 1.2, 0.5, 0.3, 1.2) * 0.12
    write('ui/area', reverb(toll + swell, 1.4, 0.4, 1.0, stereo=False), 0.8)
    brass = lowpass(saw(np.linspace(midi(38), midi(39), n_of(2.5)), 2.5, 20), 700) * adsr(n_of(2.5), 0.9, 0.3, 0.8, 1.0)
    write('ui/boss', reverb(mix(brass, at(drum(1.4), 0.9, 2.6)), 1.2, 0.35, 0.8, stereo=False), 0.85)

    write('edict/ready', bell(midi(88), 0.35) * 0.6, 0.45)
    write('edict/fail', lowpass(saw(110, 0.22, 10), 600) * decay(n_of(0.22), 0.06), 0.45)
    # Return: a boomerang whoosh that comes back.
    d = 0.8
    w = sweep_filter(noise(d), 600, 3800, 'band') * np.hanning(n_of(d))
    w2 = sweep_filter(noise(d), 3600, 700, 'band') * np.hanning(n_of(d))
    write('edict/return', mix(w, at(w2 * 0.6, 0.35, 1.2), at(bell(midi(69), 0.5) * 0.15, 0.0, 1.2)), 0.8)
    echo = np.zeros(n_of(1.5))
    for k in range(4):
        echo += at(bell(midi(81), 0.8) * (0.8 ** k) * 0.7, k * 0.22, 1.5)
    write('edict/echoes', reverb(echo, 1.0, 0.4, 0.6, stereo=False), 0.75)
    rev = bell(midi(74), 1.2)[::-1]
    ticks = np.zeros(n_of(1.4))
    for k in range(8):
        ticks += at(highpass(noise(0.015), 2500) * decay(n_of(0.015), 0.003) * 0.4, 1.3 - k * (0.06 + k * 0.01), 1.4)
    write('edict/borrowed_time', mix(rev * 0.8, ticks), 0.75)
    d = 1.3
    rise = sine(np.geomspace(300, 1400, n_of(d)), d) * adsr(n_of(d), 0.1, 0.4, 0.6, 0.6) * 0.4
    air = sweep_filter(noise(d), 800, 6000, 'band') * adsr(n_of(d), 0.3, 0.3, 0.5, 0.5) * 0.4
    write('edict/weightlessness', reverb(rise + air, 1.1, 0.35, 0.6, stereo=False), 0.75)
    d = 0.9
    growl = lowpass(saw(55 + 8 * np.sin(2 * np.pi * 28 * tline(d)), d, 18), 700) * adsr(n_of(d), 0.05, 0.2, 0.7, 0.3)
    snarl = bandpass(noise(d), 300, 1500) * adsr(n_of(d), 0.02, 0.15, 0.5, 0.3) * 0.5
    write('edict/hunger', growl + snarl, 0.8)
    harp = np.zeros(n_of(1.6))
    for k, m in enumerate((62, 66, 69, 74)):
        harp += at(pluck(midi(m), 1.4, 0.997) * 0.6, k * 0.07, 1.6)
    write('edict/hospitality', reverb(harp, 0.9, 0.3, 0.6, stereo=False), 0.7)
    d = 0.6
    ph = sweep_filter(noise(d), 4000, 400, 'band') * np.hanning(n_of(d))
    fl = ph + np.roll(ph, int(0.003 * SR)) * 0.7
    write('edict/trespass', mix(fl, at(sine(np.geomspace(900, 150, n_of(0.4)), 0.4) * decay(n_of(0.4), 0.15) * 0.4, 0.05, 0.7)), 0.75)
    d = 1.6
    cluster = sum(bell(midi(m), d, partials=((1, 1, 1.6), (2.0, 0.3, 0.8), (3.01, 0.15, 0.5))) for m in (86, 91, 93, 98)) * 0.3
    write('edict/stillness', reverb(cluster, 1.2, 0.45, 0.8, stereo=False), 0.7)
    d = 1.1
    eye = fm(midi(84), 1.41, 4.0, d, 0.3) * adsr(n_of(d), 0.01, 0.3, 0.3, 0.6)
    gleam = sine(np.geomspace(1200, 2600, n_of(d)), d) * adsr(n_of(d), 0.2, 0.3, 0.3, 0.5) * 0.25
    write('edict/witness', reverb(eye * 0.5 + gleam, 1.0, 0.35, 0.6, stereo=False), 0.7)
    d = 1.4
    smoke = sweep_filter(noise(d), 3000, 200, 'low') * adsr(n_of(d), 0.05, 0.3, 0.6, 0.8)
    tone = sine(np.geomspace(523, 330, n_of(d)), d) * adsr(n_of(d), 0.02, 0.4, 0.4, 0.9) * 0.3
    write('edict/forgotten', reverb(smoke * 0.7 + tone, 1.0, 0.3, 0.6, stereo=False), 0.7)

    big = bell(midi(33), 5.0, partials=((1.0, 1.0, 1.4), (2.0, 0.7, 1.0), (2.37, 0.5, 0.9), (3.0, 0.35, 0.7), (4.16, 0.3, 0.5), (5.43, 0.2, 0.4), (0.5, 0.6, 1.8)), detune=0.002)
    write('boss/bell', reverb(big, 1.6, 0.35, 1.5, stereo=False), 0.95)
    d = 1.6
    stomp = drum(d, 60, 28) * 1.2 + lowpass(noise(d), 120) * decay(n_of(d), 0.5) * 0.8
    write('boss/stomp', stomp, 0.95)
    for i, f in enumerate((2600, 2100)):
        tk = bandpass(noise(0.03), f * 0.7, f * 1.3) * decay(n_of(0.03), 0.005)
        write(f'boss/tick{i + 1}', mix(tk, at(tk * 0.5, 0.09, 0.2)), 0.6)
    d = 1.1
    warble = sine(midi(69) * (1 + 0.08 * np.sin(2 * np.pi * np.geomspace(2, 14, n_of(d)) * tline(d))), d) * adsr(n_of(d), 0.05, 0.3, 0.6, 0.4)
    write('boss/loop', reverb(warble[::-1] * 0.7 + warble * 0.3, 0.9, 0.3, 0.5, stereo=False), 0.7)
    d = 1.8
    roar = mix(bandpass(noise(d), 200, 900) * adsr(n_of(d), 0.15, 0.3, 0.7, 0.7),
               bandpass(noise(d), 1100, 2400) * adsr(n_of(d), 0.2, 0.3, 0.4, 0.7) * 0.4,
               lowpass(saw(np.geomspace(70, 50, n_of(d)), d, 20), 400) * adsr(n_of(d), 0.1, 0.3, 0.7, 0.6))
    write('boss/roar', reverb(roar, 1.0, 0.25, 0.5, stereo=False), 0.9)

    d = 3.0
    hum = sum(sine(f * (1 + 0.003 * np.sin(2 * np.pi * 0.4 * tline(d) + k)), d) for k, f in enumerate((110, 165.4, 220.7, 331)))
    hum *= 0.6 + 0.4 * np.sin(2 * np.pi * 0.7 * tline(d))
    write('world/anomaly', fade(hum, 0.4, 0.6), 0.5)
    d = 2.6
    wh = bandpass(noise(d), 1200, 5200)
    syll = np.abs(np.sin(2 * np.pi * 3.2 * tline(d))) ** 3 * adsr(n_of(d), 0.4, 0.3, 0.8, 0.8)
    write('world/whisper', reverb(wh * syll, 1.2, 0.5, 0.6, stereo=False), 0.5)
    write('world/shard', reverb(mix(bell(midi(91), 1.0, detune=0.002), bell(midi(98), 0.8) * 0.4), 0.8, 0.35, 0.5, stereo=False), 0.6)

    for i in range(2):
        d = 0.6
        flap = lowpass(noise(d), 1400) * (0.5 + 0.5 * np.sin(2 * np.pi * (24 + i * 5) * tline(d))) * adsr(n_of(d), 0.05, 0.1, 0.6, 0.3)
        write(f'mob/moth{i + 1}', flap, 0.45)
    for i in range(3):
        d = 0.45 + i * 0.05
        f = np.geomspace(780 + i * 60, 620, n_of(d))
        caw = bandpass(saw(f, d, 30), 700, 3200) * adsr(n_of(d), 0.01, 0.1, 0.6, 0.2)
        caw += bandpass(noise(d), 1500, 3500) * adsr(n_of(d), 0.01, 0.1, 0.3, 0.2) * 0.3
        write(f'mob/crow{i + 1}', caw, 0.7)
    d = 0.8
    whir = sine(220 + 60 * np.sin(2 * np.pi * 9 * tline(d)), d) * adsr(n_of(d), 0.05, 0.2, 0.5, 0.3) * 0.4
    clank = bandpass(noise(0.12), 1500, 6000) * decay(n_of(0.12), 0.02)
    write('mob/sentry', mix(whir, at(clank, 0.5, 0.8)), 0.7)
    d = 1.6
    moan = sine(midi(57) * (1 + 0.02 * np.sin(2 * np.pi * 5 * tline(d))), d) * adsr(n_of(d), 0.4, 0.3, 0.6, 0.7)
    write('mob/wraith', reverb(moan * 0.6 + bandpass(noise(d), 600, 2000) * adsr(n_of(d), 0.3, 0.3, 0.5, 0.7) * 0.2, 1.2, 0.4, 0.6, stereo=False), 0.6)
    d = 1.0
    call = sine(glide(d, (0, 500), (0.3, 700), (1, 520)), d) * adsr(n_of(d), 0.05, 0.2, 0.6, 0.4)
    write('mob/stag', reverb(call * 0.6, 1.0, 0.35, 0.5, stereo=False), 0.6)
    d = 1.0
    grumble = lowpass(noise(d), 160) * adsr(n_of(d), 0.1, 0.2, 0.7, 0.4) + sine(55, d) * adsr(n_of(d), 0.1, 0.2, 0.5, 0.4) * 0.4
    write('mob/mossback', grumble, 0.7)

def sfx_awakening():
    """The cinematic arrival: typewriter blips, menu ticks, a chosen chime, a step swell and the awakening.
    Kept after the music so the shared random stream (and every older sound) stays the same."""
    for i, f in enumerate((1180, 1046, 1318)):
        d = 0.06
        tone = (sine(f, d) + 0.35 * sine(f * 2.01, d)) * decay(n_of(d), 0.009)
        tick = highpass(noise(d), 2500) * decay(n_of(d), 0.002) * 0.25
        write(f'ui/type{i + 1}', mix(tone, tick), 0.5)
    d = 0.09
    knock = lowpass(noise(d), 1400) * decay(n_of(d), 0.006) + sine(392, d) * decay(n_of(d), 0.02) * 0.5
    write('ui/move', knock, 0.5)
    d = 1.4
    chime = at(bell(midi(79), 1.2) * 0.6, 0.0, d) + at(bell(midi(86), 1.1) * 0.45, 0.07, d)
    write('ui/choose', reverb(chime, 0.9, 0.35, 0.7, stereo=False), 0.6)
    d = 1.6
    whoomp = sine(glide(d, (0, 140), (0.4, 62), (1, 48)), d) * adsr(n_of(d), 0.02, 0.4, 0.3, 0.9)
    air = sweep_filter(noise(d), 300, 2600, 'band') * adsr(n_of(d), 0.25, 0.3, 0.4, 0.9) * 0.18
    glint = at(bell(midi(91), 0.9) * 0.12, 0.25, d)
    write('ui/step', reverb(mix(whoomp, air, glint), 1.2, 0.4, 0.9, stereo=False), 0.7)
    d = 5.0
    pad = pad_chord((50, 57, 62, 66, 69), d, attack=2.2, release=2.2, voices=3, cents=7, bright=2400)
    shimmer = np.zeros(n_of(d))
    for k, m in enumerate((86, 90, 93, 98, 93, 90)):
        shimmer += at(bell(midi(m), 1.6) * (0.10 + 0.02 * (k % 3)), 1.0 + k * 0.32, d)
    rise = sweep_filter(noise(d), 200, 3200, 'band') * adsr(n_of(d), 2.4, 0.4, 0.2, 1.8) * 0.06
    mono = reverb(shimmer + rise, 1.6, 0.45, 1.4, stereo=False)
    write('ui/awaken', mix(pad * 2.2, np.stack([mono, mono], axis=1)), 0.75)

def sfx_wave2():
    """The second wave of creatures: whale song, sprite chimes, rimehorn lowing, fox yips.
    Uses its own random stream so it never disturbs the sounds made before it."""
    global RNG
    RNG = np.random.default_rng(2026)
    for i, (f0, f1, f2) in enumerate(((92, 140, 78), (70, 118, 96))):
        d = 6.5
        pitch = glide(d, (0, f0), (0.35, f1), (0.7, f2), (1, f0 * 0.8))
        voice = sum(sine(pitch * h, d) * a for h, a in ((1, 1.0), (2.01, 0.35), (3.02, 0.15)))
        voice *= adsr(n_of(d), 1.2, 0.6, 0.8, 2.4)
        voice *= 0.75 + 0.25 * np.sin(2 * np.pi * 0.9 * tline(d))
        breath = lowpass(noise(d), 500) * adsr(n_of(d), 1.5, 0.5, 0.4, 2.0) * 0.08
        write(f'mob/whale{i + 1}', reverb(mix(voice, breath), 2.4, 0.55, 3.5, stereo=True, damp=2500), 0.8)
    for i, ms in enumerate(((96, 100, 103), (98, 103, 107))):
        d = 1.0
        tw = sum(at(bell(midi(m), 0.6) * 0.3, k * 0.07, d) for k, m in enumerate(ms))
        write(f'mob/sprite{i + 1}', reverb(tw, 0.7, 0.35, 0.5, stereo=False), 0.45)
    for i in range(2):
        d = 1.3 + i * 0.2
        low = lowpass(saw(glide(d, (0, 120 - i * 15), (0.5, 104 - i * 10), (1, 86)), d, 30), 700)
        low = bandpass(low, 90, 900) * adsr(n_of(d), 0.15, 0.3, 0.7, 0.5)
        write(f'mob/rimehorn{i + 1}', low, 0.75)
    for i in range(3):
        d = 0.22
        yip = bandpass(saw(np.geomspace(900 + i * 120, 1500 + i * 90, n_of(d)), d, 18), 700, 4000) * adsr(n_of(d), 0.01, 0.05, 0.5, 0.12)
        write(f'mob/emberfox{i + 1}', yip, 0.6)


def sfx_v2():
    """Afterworld 2.0: coins, purchases, parries, perfect moves and the Aspect's unleashing."""
    def clink(f, d=0.22):
        x = fm(f, 2.76, 3.0, d, 0.08) * decay(n_of(d), 0.05)
        return x + highpass(noise(d), 7000) * decay(n_of(d), 0.006) * 0.25
    write('ui/coin', mix(clink(2637), at(clink(3520, 0.18) * 0.6, 0.05, 0.27)), 0.55)
    cascade = np.zeros(n_of(0.7))
    for k, f in enumerate((2349, 2794, 3136, 2637, 3520)):
        cascade += at(clink(f, 0.2) * (0.8 - k * 0.08), 0.04 + k * 0.07, 0.7)
    thump = lowpass(noise(0.12), 400) * decay(n_of(0.12), 0.03)
    chime = bell(1568, 0.6) * 0.35
    write('ui/purchase', reverb(mix(thump, cascade, at(chime, 0.32, 0.7)), 0.5, 0.2, 0.4, stereo=False), 0.6)
    # The parry: a bright clang with a ringing tail.
    clang = fm(1180, 1.41, 6.0, 0.9, 0.12) * decay(n_of(0.9), 0.18)
    hit = highpass(noise(0.05), 3000) * decay(n_of(0.05), 0.008)
    write('combat/parry', reverb(mix(clang * 0.8, hit), 0.5, 0.25, 0.6, stereo=False), 0.75)
    # The perfect move: a reversed swell into a soft chime.
    swell = highpass(noise(0.45), 2500) * np.linspace(0, 1, n_of(0.45)) ** 2
    sparkle = at(bell(2093, 0.8) * 0.4, 0.42, 1.2)
    write('combat/perfect', reverb(mix(at(swell * 0.35, 0, 1.2), sparkle), 0.7, 0.35, 0.8, stereo=False), 0.6)
    # The Aspect unleashed: a falling boom, a rush of air, and a chord of bells.
    d = 3.2
    f = glide(d, (0, 70), (0.2, 38), (1, 30))
    boom = np.sin(2 * np.pi * np.cumsum(f) / SR) * decay(n_of(d), 0.9) * 0.9
    rush = sweep_filter(noise(d) * adsr(n_of(d), 0.02, 0.4, 0.3, 1.5), 4000, 300, 'low') * 0.4
    chord = np.zeros(n_of(d))
    for k, m in enumerate((57, 64, 69, 76)):
        chord += at(bell(midi(m), 2.6) * (0.5 - k * 0.07), 0.08 + k * 0.05, d)
    write('aspect/unleash', reverb(mix(boom, rush, chord), 1.2, 0.35, 2.2), 0.9)


def sfx_beasts():
    """Calls for the second bestiary's creatures that vanilla has no voice for."""
    def tone(f0, f1, d, harm=((1, 1.0), (2, 0.25), (3, 0.08)), vib=0.0):
        n = n_of(d)
        f = glide(d, (0, f0), (1, f1))
        if vib:
            f = f * (1 + vib * np.sin(2 * np.pi * 6.0 * np.arange(n) / SR))
        ph = 2 * np.pi * np.cumsum(f) / SR
        return sum(a * np.sin(k * ph) for k, a in harm)
    # Owl: two soft, breathy hoots.
    hoot = lambda: lowpass(tone(420, 380, 0.42) * adsr(n_of(0.42), 0.08, 0.1, 0.8, 0.2) + bandpass(noise(0.42), 300, 900) * 0.15, 1200)
    owl = np.zeros(n_of(1.4))
    owl += at(hoot() * 0.8, 0.0, 1.4)
    owl += at(hoot(), 0.62, 1.4)
    write('mob/owl', reverb(owl, 0.8, 0.25, 0.8, stereo=False), 0.6)
    # Eagle: a high, rasping descending cry.
    cry = tone(2600, 1700, 0.75, ((1, 1.0), (2, 0.4), (3, 0.2), (4, 0.1)), vib=0.02) * adsr(n_of(0.75), 0.02, 0.2, 0.6, 0.3)
    cry = cry + highpass(noise(0.75), 2000) * adsr(n_of(0.75), 0.02, 0.2, 0.5, 0.3) * 0.25
    write('mob/eagle', reverb(cry, 1.0, 0.3, 1.0, stereo=False), 0.6)
    # Crane: a bugling, trumpeting call in two parts.
    bugle = lambda f: tone(f, f * 1.15, 0.35, ((1, 1.0), (2, 0.6), (3, 0.35), (5, 0.15))) * adsr(n_of(0.35), 0.03, 0.1, 0.7, 0.15)
    crane = np.zeros(n_of(0.9))
    crane += at(bugle(700), 0.0, 0.9)
    crane += at(bugle(820), 0.42, 0.9)
    write('mob/crane', reverb(lowpass(crane, 4000), 0.9, 0.25, 0.8, stereo=False), 0.55)
    # Seal: a short, wet bark.
    bark = tone(330, 220, 0.28, ((1, 1.0), (2, 0.5), (3, 0.3))) * adsr(n_of(0.28), 0.01, 0.08, 0.5, 0.12)
    bark = bark + bandpass(noise(0.28), 200, 1200) * decay(n_of(0.28), 0.08) * 0.4
    write('mob/seal', lowpass(bark, 2500), 0.6)
    # Hound: a low growl that rolls, then a snap.
    growl = lowpass(saw(70, 1.1) * (0.6 + 0.4 * np.abs(np.sin(2 * np.pi * 11 * np.arange(n_of(1.1)) / SR))), 600)
    growl = growl * adsr(n_of(1.1), 0.15, 0.2, 0.8, 0.3) + bandpass(noise(1.1), 120, 700) * 0.25 * adsr(n_of(1.1), 0.1, 0.2, 0.7, 0.3)
    write('mob/hound', growl, 0.65)


def sounds_json():
    entries = {}
    def ev(event, files, stream=False, volume=None, pitch=None, subtitle=True):
        sounds = []
        for f in files:
            s = {'name': 'afterworld:' + f}
            if stream:
                s['stream'] = True
            if volume:
                s['volume'] = volume
            if pitch:
                s['pitch'] = pitch
            sounds.append(s)
        entries[event] = {'sounds': sounds}
        if subtitle:
            entries[event]['subtitle'] = 'subtitles.afterworld.' + event
    ev('music.title', ['music/title'], stream=True, volume=0.7, subtitle=False)
    ev('music.wilds', ['music/wilds'], stream=True, volume=0.55, subtitle=False)
    ev('music.boss', ['music/boss'], stream=True, volume=0.7, subtitle=False)
    ev('ui.page', ['ui/page1', 'ui/page2', 'ui/page3'], volume=0.6, subtitle=False)
    ev('ui.select', ['ui/select'], volume=0.5, subtitle=False)
    ev('ui.open', ['ui/open'], volume=0.7, subtitle=False)
    ev('ui.discovery', ['ui/discovery'], volume=0.6)
    ev('ui.rumour', ['ui/rumour'], volume=0.6)
    ev('ui.chronicle', ['ui/chronicle'], volume=0.6)
    ev('ui.area', ['ui/area'], volume=0.7, subtitle=False)
    ev('ui.boss', ['ui/boss'], volume=0.8, subtitle=False)
    ev('ui.type', ['ui/type1', 'ui/type2', 'ui/type3'], volume=0.35, subtitle=False)
    ev('ui.move', ['ui/move'], volume=0.45, subtitle=False)
    ev('ui.choose', ['ui/choose'], volume=0.55, subtitle=False)
    ev('ui.step', ['ui/step'], volume=0.7, subtitle=False)
    ev('ui.awaken', ['ui/awaken'], volume=0.8, subtitle=False)
    ev('ui.coin', ['ui/coin'], volume=0.5, subtitle=False)
    ev('ui.purchase', ['ui/purchase'], volume=0.6, subtitle=False)
    ev('combat.parry', ['combat/parry'], volume=0.9)
    ev('combat.perfect', ['combat/perfect'], volume=0.7, subtitle=False)
    ev('aspect.unleash', ['aspect/unleash'], volume=1.0)
    for m in ('owl', 'eagle', 'crane', 'seal', 'hound'):
        ev('mob.' + m, ['mob/' + m], volume=0.8)
    for e in ('ready', 'fail', 'return', 'echoes', 'borrowed_time', 'weightlessness', 'hunger', 'hospitality', 'trespass', 'stillness', 'witness', 'forgotten'):
        ev('edict.' + e, ['edict/' + e])
    ev('boss.bell', ['boss/bell'], volume=1.0)
    ev('boss.stomp', ['boss/stomp'])
    ev('boss.tick', ['boss/tick1', 'boss/tick2'])
    ev('boss.loop', ['boss/loop'])
    ev('boss.roar', ['boss/roar'])
    ev('world.anomaly', ['world/anomaly'], volume=0.6)
    ev('world.whisper', ['world/whisper'], volume=0.6)
    ev('world.shard', ['world/shard'])
    ev('mob.moth', ['mob/moth1', 'mob/moth2'], volume=0.5)
    ev('mob.crow', ['mob/crow1', 'mob/crow2', 'mob/crow3'], volume=0.8)
    ev('mob.sentry', ['mob/sentry'])
    ev('mob.wraith', ['mob/wraith'], volume=0.7)
    ev('mob.stag', ['mob/stag'], volume=0.7)
    ev('mob.mossback', ['mob/mossback'])
    ev('mob.whale', ['mob/whale1', 'mob/whale2'], volume=1.0)
    ev('mob.sprite', ['mob/sprite1', 'mob/sprite2'], volume=0.5)
    ev('mob.rimehorn', ['mob/rimehorn1', 'mob/rimehorn2'], volume=0.8)
    ev('mob.emberfox', ['mob/emberfox1', 'mob/emberfox2', 'mob/emberfox3'], volume=0.6)
    path = os.path.join(ASSETS, 'sounds.json')
    # Voice lines are written by tools/dialogue.py; keep them.
    if os.path.exists(path):
        with open(path) as f:
            for k, v in json.load(f).items():
                if k.startswith('va.'):
                    entries[k] = v
    # Impacts, creature voices and the interface remixed by tools/gen_sfx.py win over ours.
    remixed = os.path.join(HERE, 'sfx_events.json')
    if os.path.exists(remixed):
        with open(remixed) as f:
            entries.update(json.load(f))
    with open(path, 'w') as f:
        json.dump(entries, f, indent=2)
    return entries

if __name__ == '__main__':
    import sys
    os.makedirs(SOUNDS, exist_ok=True)
    only = sys.argv[1:]
    if not only:
        sfx()
        music_title()
        music_wilds()
        music_boss()
    if not only or 'awakening' in only:
        sfx_awakening()
    if not only or 'wave2' in only:
        sfx_wave2()
    if not only or 'v2' in only:
        sfx_v2()
    if not only or 'beasts' in only:
        sfx_beasts()
    e = sounds_json()
    print(len(WRITTEN), 'sound files,', len(e), 'events')
