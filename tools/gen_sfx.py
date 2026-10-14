#!/usr/bin/env python3
"""Reworks the public-domain recordings in sources/sfx into Afterworld's own sounds: a hit for
every material, voices for the creatures that had none of their own, and the interface.

Nothing ships as it was recorded. Every sound is built from two or more sources, pitched (often by
an octave), filtered, saturated and given its own space, with a synthesized layer where it needs
weight or sparkle. Writes the .ogg files, tools/sfx_events.json and merges those events into
sounds.json (gen_audio.py merges the same file, so the two can run in either order).

    <venv>/bin/python tools/gen_sfx.py          (numpy + soundfile, like gen_audio.py)
"""
import json
import os
import sys
import zlib

import numpy as np
import soundfile as sf

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import gen_audio as A  # noqa: E402
from import_voices import pitch as pv_pitch  # noqa: E402

SR = A.SR
ROOT = os.path.join(HERE, '..')
SRC = os.path.join(ROOT, 'sources', 'sfx')
EVENTS_FILE = os.path.join(HERE, 'sfx_events.json')
SOUNDS_JSON = os.path.join(A.ASSETS, 'sounds.json')
EVENTS = {}
_CACHE = {}


# ---------------------------------------------------------------- sources and tools

def src(folder, name):
    """A source recording, mono at 44.1 kHz with its silent ends trimmed."""
    key = (folder, name)
    if key not in _CACHE:
        x, sr = sf.read(os.path.join(SRC, folder, name + '.ogg'), always_2d=True)
        x = x.mean(axis=1)
        if sr != SR:
            n = int(len(x) * SR / sr)
            x = np.interp(np.arange(n) * (sr / SR), np.arange(len(x)), x)
        env = np.convolve(np.abs(x), np.ones(64) / 64, mode='same')
        live = np.where(env > np.max(env) * 0.01)[0]
        if len(live):
            x = x[max(0, live[0] - 32):live[-1] + 256]
        _CACHE[key] = A.fade(x / (np.max(np.abs(x)) + 1e-9), 0.0005, 0.008)
    return _CACHE[key].copy()


def K(name):
    return src('kenney_impact', name)


def R(name):
    return src('kenney_rpg', name)


def UI(name):
    return src('kenney_interface', name)


def C(name):
    return src('oga_creatures', name)


def vari(x, semis):
    """Varispeed: pitch and length move together, the way a tape slows (heavier when lower)."""
    ratio = 2 ** (semis / 12.0)
    n = max(8, int(len(x) / ratio))
    return np.interp(np.arange(n) * ratio, np.arange(len(x)), x)


def sat(x, drive):
    return np.tanh(x * drive) / np.tanh(drive)


def cut(x, start, length):
    a = int(start * SR)
    b = min(len(x), a + int(length * SR))
    return A.fade(x[a:b], 0.002, min(0.04, (b - a) / SR / 3))


def lay(*parts):
    """Mix (sound, offset seconds, gain) layers."""
    n = max(int(o * SR) + len(p) for p, o, g in parts)
    out = np.zeros(n)
    for p, o, g in parts:
        s = int(o * SR)
        out[s:s + len(p)] += p * g
    return out


def thump(f0, f1, d, tau):
    """A falling sine: the weight under a heavy hit."""
    n = A.n_of(d)
    f = np.geomspace(f0, f1, n)
    return A.fade(np.sin(2 * np.pi * np.cumsum(f) / SR) * A.decay(n, tau), 0.0005, 0.02)


def click(d, lo, hi, rng):
    n = A.n_of(d)
    return A.fade(A.bandpass(rng.standard_normal(n), lo, hi) * A.decay(n, d / 4), 0.0005, d / 4)


def swish(d, lo, hi, rng, peak_at=0.35):
    """Air moving: band-limited noise that swells and dies away."""
    n = A.n_of(d)
    t = np.linspace(0, 1, n)
    env = np.where(t < peak_at, (t / peak_at) ** 2, ((1 - t) / (1 - peak_at)) ** 1.5)
    return A.fade(A.sweep_filter(rng.standard_normal(n), hi, lo, mode='band') * env, 0.002, 0.02)


def ring(freqs, d, tau, rng):
    n = A.n_of(d)
    out = np.zeros(n)
    for i, f in enumerate(freqs):
        out += np.sin(2 * np.pi * f * np.arange(n) / SR + rng.uniform(0, 6.28)) * A.decay(n, tau / (1 + i * 0.35)) / (1 + i * 0.6)
    att = A.n_of(0.002)
    out[:att] *= np.linspace(0, 1, att)
    return A.fade(out, 0, min(0.06, d / 4))


def flutter(d, rate, rng):
    """Wings: noise chopped at the wingbeat."""
    n = A.n_of(d)
    t = np.arange(n) / SR
    beat = 0.5 + 0.5 * np.sin(2 * np.pi * rate * t) ** 7
    return A.fade(A.bandpass(rng.standard_normal(n), 400, 3000) * beat * A.decay(n, d / 2), 0.005, d / 4)


def echo(x, delay, feedback, taps=4):
    out = x.copy()
    pad = np.concatenate([out, np.zeros(int(delay * SR * taps))])
    for k in range(1, taps + 1):
        s = int(delay * SR * k)
        pad[s:s + len(x)] += x * feedback ** k
    return pad


def room(x, wet, size=0.6, tail=0.8):
    y = A.reverb(x, size=size, wet=wet, tail=tail, stereo=False, damp=4500)
    live = np.where(np.abs(y) > np.max(np.abs(y)) * 0.003)[0]
    return y[:live[-1] + 1] if len(live) else y


def rev(x):
    return x[::-1].copy()


def rng_for(name, i):
    return np.random.default_rng(zlib.crc32(f'{name}:{i}'.encode()))


def emit(path, x, peak=0.9):
    """Writes one mono file (positional in game) and returns its sound path."""
    x = np.asarray(x, dtype=np.float64)
    x = A.fade(x, 0.001, min(0.03, len(x) / SR / 4))
    A.write(path, x, peak=peak)
    return path


def event(name, files, volume=1.0, subtitle=True):
    sounds = [{'name': 'afterworld:' + f, 'volume': round(volume, 2)} if volume != 1.0 else {'name': 'afterworld:' + f} for f in files]
    EVENTS[name] = {'sounds': sounds}
    if subtitle:
        EVENTS[name]['subtitle'] = 'subtitles.afterworld.' + name


def variants(event_name, folder, build, count, volume=1.0, subtitle=True, peak=0.9):
    files = []
    for i in range(count):
        files.append(emit(f'{folder}{i + 1}', build(i, rng_for(event_name, i)), peak))
    event(event_name, files, volume, subtitle)


# ---------------------------------------------------------------- impacts

def impacts():
    """One hit per material, four takes each, so a fight never repeats itself exactly."""

    def flesh(i, r):
        return A.lowpass(sat(lay((vari(K(f'impactPunch_medium_{i:03d}'), r.uniform(-3, -1)), 0, 1.0),
                                 (vari(K(f'impactSoft_heavy_{(i + 2) % 5:03d}'), r.uniform(-5, -3)), 0.004, 0.55),
                                 (thump(r.uniform(95, 120), 48, 0.14, 0.05), 0, 0.5)) * 1.3, 1.6), 5200)

    def hide(i, r):
        return A.lowpass(sat(lay((vari(K(f'impactSoft_heavy_{i:03d}'), r.uniform(-4, -2)), 0, 1.0),
                                 (vari(K(f'footstep_carpet_{(i + 1) % 5:03d}'), r.uniform(-6, -3)), 0.003, 0.6),
                                 (vari(K(f'impactPunch_heavy_{(i + 3) % 5:03d}'), -5), 0, 0.45),
                                 (thump(80, 40, 0.18, 0.07), 0, 0.55)) * 1.2, 1.4), 4200)

    def chitin(i, r):
        return A.highpass(lay((vari(K(f'impactPlate_light_{i:03d}'), r.uniform(1, 4)), 0, 1.0),
                              (vari(K(f'impactGeneric_light_{(i + 1) % 5:03d}'), r.uniform(-2, 1)), 0.002, 0.7),
                              (click(0.02, 2500, 8000, r), 0, 0.5),
                              (vari(K(f'impactPunch_medium_{(i + 2) % 5:03d}'), 2), 0.003, 0.3)), 300)

    def stone(i, r):
        return A.lowpass(lay((vari(K(f'impactMining_{i:03d}'), r.uniform(-3, -1)), 0, 1.0),
                             (vari(K(f'impactGeneric_light_{(i + 2) % 5:03d}'), r.uniform(-7, -5)), 0.002, 0.6),
                             (vari(K(f'footstep_concrete_{(i + 1) % 5:03d}'), -4), 0.012, 0.55),
                             (thump(70, 45, 0.16, 0.06), 0, 0.35)), 6500)

    def metal(i, r):
        f0 = r.uniform(820, 1150)
        return lay((vari(K(f'impactMetal_medium_{i:03d}'), r.uniform(-2, 1)), 0, 1.0),
                   (vari(K(f'impactPlate_medium_{(i + 1) % 5:03d}'), r.uniform(-3, -1)), 0.002, 0.55),
                   (ring([f0, f0 * 2.37, f0 * 3.91, f0 * 5.2], 0.45, 0.16, r), 0.001, 0.22),
                   (vari(K(f'impactPunch_medium_{(i + 3) % 5:03d}'), -4), 0, 0.3))

    def crystal(i, r):
        f0 = r.uniform(1900, 2600)
        return room(lay((vari(K(f'impactGlass_light_{i:03d}'), r.uniform(0, 3)), 0, 1.0),
                        (ring([f0, f0 * 1.51, f0 * 2.03, f0 * 2.76], 0.9, 0.35, r), 0.002, 0.42),
                        (vari(K(f'impactPlate_light_{(i + 2) % 5:03d}'), 5), 0, 0.35)), 0.22, 0.5, 0.6)

    def wood(i, r):
        return lay((vari(K(f'impactWood_medium_{i:03d}'), r.uniform(-3, 0)), 0, 1.0),
                   (vari(K(f'impactPlank_medium_{(i + 1) % 5:03d}'), r.uniform(-2, 1)), 0.003, 0.6),
                   (thump(110, 60, 0.1, 0.04), 0, 0.3))

    def bone(i, r):
        return A.highpass(lay((vari(K(f'impactWood_light_{i:03d}'), r.uniform(4, 7)), 0, 1.0),
                              (vari(K(f'impactPlate_light_{(i + 3) % 5:03d}'), r.uniform(2, 4)), 0.004, 0.45),
                              (click(0.015, 1800, 6000, r), 0.008, 0.5),
                              (vari(K(f'impactGeneric_light_{(i + 1) % 5:03d}'), 3), 0.012, 0.35)), 250)

    def spirit(i, r):
        glass = rev(vari(K(f'impactGlass_light_{i:03d}'), r.uniform(-7, -4)))
        return room(lay((A.lowpass(vari(K(f'impactSoft_medium_{i:03d}'), -2), 3000), 0, 0.6),
                        (glass, 0, 0.5),
                        (swish(0.32, 700, 4200, r, 0.25), 0, 0.55)), 0.45, 1.0, 1.0)

    def slime(i, r):
        return A.lowpass(lay((vari(K(f'impactSoft_medium_{i:03d}'), r.uniform(-6, -3)), 0, 1.0),
                             (vari(C(f'eat_0{(i % 4) + 1}'), r.uniform(-5, -2)), 0.01, 0.7),
                             (thump(90, 50, 0.12, 0.05), 0, 0.3)), 3200)

    def ice(i, r):
        return lay((vari(K(f'impactGlass_heavy_{i:03d}'), r.uniform(-3, -1)), 0, 1.0),
                   (vari(K(f'footstep_snow_{(i + 1) % 5:03d}'), r.uniform(-2, 1)), 0.006, 0.75),
                   (A.highpass(click(0.03, 4000, 11000, r), 3000), 0, 0.35))

    def scale(i, r):
        return A.lowpass(lay((vari(K(f'impactPlate_light_{i:03d}'), r.uniform(-4, -2)), 0, 0.8),
                             (vari(K(f'impactSoft_medium_{(i + 1) % 5:03d}'), r.uniform(-3, -1)), 0.002, 0.9),
                             (thump(100, 55, 0.12, 0.05), 0, 0.35)), 6000)

    def crit(i, r):
        return A.highpass(lay((swish(0.11, 2200, 9500, r, 0.6), 0, 0.9),
                              (vari(K(f'impactMetal_light_{i:03d}'), r.uniform(4, 7)), 0.07, 0.6),
                              (ring([r.uniform(3200, 3900)], 0.35, 0.12, r), 0.075, 0.25)), 600)

    def kill(i, r):
        chime = ring([523.0 * (1 + i * 0.06), 784.0 * (1 + i * 0.06), 1046.0 * (1 + i * 0.06)], 0.9, 0.35, r)
        return room(lay((thump(68, 34, 0.45, 0.16), 0, 0.85),
                        (vari(K(f'impactSoft_heavy_{i:03d}'), -7), 0, 0.55),
                        (chime, 0.04, 0.16)), 0.25, 0.7, 0.9)

    def guard(i, r):
        f0 = r.uniform(600, 760)
        return lay((vari(K(f'impactMetal_heavy_{i:03d}'), r.uniform(-4, -2)), 0, 1.0),
                   (vari(K(f'impactPlate_heavy_{(i + 2) % 5:03d}'), -3), 0.002, 0.6),
                   (ring([f0, f0 * 2.6, f0 * 4.1], 0.6, 0.22, r), 0.002, 0.25))

    for name, build, volume in (('flesh', flesh, 0.85), ('hide', hide, 0.85), ('chitin', chitin, 0.75), ('stone', stone, 0.9),
                                ('metal', metal, 0.8), ('crystal', crystal, 0.75), ('wood', wood, 0.85), ('bone', bone, 0.8),
                                ('spirit', spirit, 0.7), ('slime', slime, 0.8), ('ice', ice, 0.8), ('scale', scale, 0.8)):
        variants('impact.' + name, f'impact/{name}', build, 4, volume, subtitle=False)
    variants('impact.crit', 'impact/crit', crit, 3, 0.6, subtitle=False)
    variants('impact.kill', 'impact/kill', kill, 3, 0.55, subtitle=False)
    variants('impact.guard', 'impact/guard', guard, 3, 0.8, subtitle=False)


# ---------------------------------------------------------------- creature voices

def voices():
    """Voices for the creatures that borrowed a vanilla animal's or had none. Each set is three
    events, creature.<set>.ambient/hurt/death; the game's pitch per species varies them further."""

    def voice(name, ambient, hurt, death, n=(2, 2, 2), volume=(0.8, 0.9, 1.0)):
        for part, build, count, vol in (('ambient', ambient, n[0], volume[0]), ('hurt', hurt, n[1], volume[1]), ('death', death, n[2], volume[2])):
            if build is None:
                continue
            if isinstance(build, list):  # existing files
                event(f'creature.{name}.{part}', build, vol)
            else:
                variants(f'creature.{name}.{part}', f'creature/{name}/{part}', build, count, vol)

    # The sky whale: moans an octave and more below a person's, in a hall of air.
    voice('whale',
          ['mob/whale1', 'mob/whale2'],
          lambda i, r: room(A.lowpass(lay((vari(C('ooh'), -12 - 2 * i), 0, 1.0), (vari(C('howl'), -15 - i), 0.05, 0.45)), 2400), 0.5, 1.4, 1.6),
          lambda i, r: room(A.lowpass(lay((vari(C('ooh'), -15), 0, 1.0), (vari(C('howl'), -17), 0.15, 0.6), (vari(C('snore'), -9), 0.4, 0.35)), 2000), 0.6, 1.6, 2.2),
          n=(0, 2, 1), volume=(1.0, 1.0, 1.0))
    # Ash wyrmling: a young dragon's roar, sharp and crackling.
    voice('wyrmling',
          lambda i, r: lay((sat(vari(C(('roar_01', 'roar_02')[i]), 4 + i), 2.2), 0, 1.0), (A.highpass(click(0.4, 900, 5000, r), 600), 0.05, 0.2)),
          lambda i, r: lay((sat(vari(C('roar_03'), 5 + i), 2.0), 0, 1.0), (vari(C(('hurt_02', 'hurt_05')[i]), 2), 0.02, 0.4)),
          lambda i, r: room(lay((sat(vari(C('roar_02'), 2), 1.8), 0, 1.0), (vari(C('monster_01'), -2), 0.1, 0.5), (swish(0.9, 200, 1800, r, 0.2), 0.05, 0.4)), 0.25, 0.8, 1.0),
          n=(2, 2, 1))
    # Rime brute: a great ice-hide beast, grunting low.
    voice('brute',
          lambda i, r: A.lowpass(lay((vari(C(('troll_01', 'troll_02')[i]), -4), 0, 1.0), (vari(C('grunt_02'), -5), 0.03, 0.5)), 3200),
          lambda i, r: A.lowpass(lay((sat(vari(C('troll_03'), -2 - i), 1.6), 0, 1.0), (vari(C('grunt_04'), -3), 0.01, 0.6)), 3800),
          lambda i, r: room(A.lowpass(lay((vari(C('troll_02'), -6), 0, 1.0), (vari(C('roar_02'), -7), 0.12, 0.6)), 3000), 0.2, 0.8, 0.9),
          n=(2, 2, 1))
    # Mire shambler: a drowned thing that bubbles as it breathes.
    voice('shambler',
          lambda i, r: A.lowpass(lay((vari(C(('burble_01', 'burble_02')[i]), -5), 0, 1.0), (vari(C('monster_03'), -4), 0.08, 0.5)), 2600),
          lambda i, r: A.lowpass(lay((vari(C('burp_01'), -3 - i), 0, 1.0), (vari(C('grunt_03'), -3), 0.02, 0.5)), 3000),
          lambda i, r: A.lowpass(lay((vari(C('burble_02'), -7), 0, 1.0), (vari(C('monster_06'), -5), 0.05, 0.7)), 2400),
          n=(2, 2, 1))
    # Gloomweaver: a huge spider's hiss and clatter.
    voice('weaver',
          lambda i, r: lay((vari(C(('bug_01', 'bug_02')[i]), -5), 0, 1.0), (A.highpass(vari(C('alien_04'), -7), 900), 0.05, 0.4)),
          lambda i, r: lay((vari(C('bug_03'), -2 - i), 0, 1.0), (vari(C('spit_01'), -2), 0.02, 0.45)),
          lambda i, r: lay((vari(C('bug_04'), -6), 0, 1.0), (vari(C('alien_03'), -8), 0.06, 0.5)),
          n=(2, 2, 1))
    # Mourning shade: grief with no throat left, smeared through a cold hall.
    voice('shade',
          lambda i, r: room(lay((A.lowpass(pv_pitch(C(('scream_02', 'weird_01')[i]), -7), 3200), 0, 1.0), (rev(vari(C('breath'), -3)), 0.05, 0.5)), 0.6, 1.5, 1.8),
          lambda i, r: room(lay((vari(C('weird_02'), -3 - i), 0, 1.0), (cut(vari(C('scream_01'), -5), 0, 0.4), 0.01, 0.45)), 0.45, 1.2, 1.2),
          lambda i, r: room(lay((A.lowpass(pv_pitch(C('scream_01'), -9), 2800), 0, 1.0), (rev(vari(C('scream_02'), -10)), 0, 0.35)), 0.7, 1.7, 2.4),
          n=(2, 2, 1), volume=(0.7, 0.85, 1.0))
    # Dusk hound: the pack's yelps and its last howl (its call is mob.hound).
    voice('hound',
          ['mob/hound'],
          lambda i, r: lay((vari(C(('barking_01', 'barking_02')[i]), -4), 0, 1.0), (vari(C('hurt_03'), -3), 0.02, 0.4)),
          lambda i, r: room(lay((vari(C('howl'), -5), 0, 1.0), (vari(C('barking_02'), -6), 0, 0.35)), 0.3, 1.0, 1.4),
          n=(0, 2, 1))
    # Abyssal angler: heard through deep water.
    voice('angler',
          lambda i, r: A.lowpass(lay((vari(C(('monster_04', 'monster_07')[i]), -3), 0, 1.0), (vari(C('burble_01'), -7), 0.1, 0.5)), 1500),
          lambda i, r: A.lowpass(lay((vari(C('monster_05'), -4 - i), 0, 1.0), (vari(C('burp_01'), -6), 0, 0.4)), 1900),
          lambda i, r: A.lowpass(lay((vari(C('monster_07'), -5), 0, 1.0), (vari(C('burble_02'), -9), 0.1, 0.6)), 1400),
          n=(2, 2, 1))
    # Fungal brute: a slow giant that coughs spores.
    voice('fungal',
          lambda i, r: lay((A.lowpass(vari(C(('grunt_01', 'misc_09')[i]), -5), 3000), 0, 1.0), (swish(0.5, 300, 2400, r, 0.15), 0.1, 0.3)),
          lambda i, r: lay((vari(C('grunt_05'), -4 - i), 0, 1.0), (vari(C('cough_02'), -3), 0.03, 0.45)),
          lambda i, r: lay((vari(C('monster_06'), -6), 0, 1.0), (vari(C('cough_03'), -5), 0.15, 0.5), (swish(1.0, 200, 2000, r, 0.1), 0.2, 0.35)),
          n=(2, 2, 1))
    # Beetles, sifters and scarabs: a chitinous chitter (each species pitches it its own way).
    voice('chitter',
          lambda i, r: lay((vari(C(('bug_03', 'bug_01')[i]), 1 + i), 0, 1.0), (click(0.02, 3000, 9000, r), 0.08, 0.4), (click(0.02, 3000, 9000, r), 0.14, 0.3)),
          lambda i, r: lay((cut(vari(C('bug_02'), 2 + i), 0, 0.3), 0, 1.0), (click(0.02, 2500, 8000, r), 0, 0.5)),
          lambda i, r: lay((vari(C('bug_04'), -1), 0, 1.0), (vari(K('impactPlate_light_002'), -3), 0.05, 0.5)),
          n=(2, 2, 1), volume=(0.6, 0.8, 0.9))
    # Moths: a papery flutter and a thin chirp.
    voice('moth',
          ['mob/moth1', 'mob/moth2'],
          lambda i, r: A.highpass(lay((cut(vari(C(('cute_07', 'cute_05')[i]), 6), 0, 0.25), 0, 0.7), (flutter(0.3, 28, r), 0, 0.6)), 700),
          lambda i, r: A.highpass(lay((vari(C('cute_03'), 5), 0, 0.7), (flutter(0.6, 24, r), 0, 0.7)), 600),
          n=(0, 2, 1), volume=(0.5, 0.6, 0.7))
    # Sprites and wisps: tiny voices with a chime in them.
    voice('sprite',
          lambda i, r: room(lay((vari(C(('cute_01', 'cute_10')[i]), 7), 0, 0.8), (ring([r.uniform(2400, 3200), 4700], 0.6, 0.25, r), 0.04, 0.3)), 0.35, 0.7, 0.9),
          lambda i, r: lay((vari(C(('cute_04', 'cute_06')[i]), 6), 0, 0.9), (ring([r.uniform(2000, 2600), 5200], 0.35, 0.12, r), 0, 0.3)),
          lambda i, r: room(lay((vari(C('cute_09'), 5), 0, 0.9), (ring([1760, 2640, 3520], 1.2, 0.5, r), 0.05, 0.35)), 0.45, 0.9, 1.4),
          n=(2, 2, 1), volume=(0.45, 0.6, 0.7))
    # Echo stalker: a hunter in the dark, its voice coming back off the walls.
    voice('stalker',
          lambda i, r: echo(lay((vari(C(('monster_03', 'weird_01')[i]), -5), 0, 1.0), (vari(C('weird_03'), -4), 0.1, 0.4)), 0.19, 0.42),
          lambda i, r: echo(vari(C(('weird_05', 'weird_04')[i]), -3), 0.17, 0.35, 3),
          lambda i, r: room(echo(lay((vari(C('monster_06'), -7), 0, 1.0), (vari(C('weird_04'), -5), 0.1, 0.5)), 0.22, 0.45), 0.4, 1.3, 1.8),
          n=(2, 2, 1))
    # Bog lurker: a toad the size of a cart.
    voice('lurker',
          lambda i, r: lay((vari(C(('burp_02', 'burp_01')[i]), -7), 0, 1.0), (vari(C('burble_01'), -5), 0.05, 0.4)),
          lambda i, r: cut(vari(C('burp_01'), -4 - i), 0, 0.45),
          lambda i, r: lay((vari(C('burble_02'), -8), 0, 1.0), (vari(C('burp_02'), -9), 0.1, 0.5)),
          n=(2, 2, 1))
    # Ashen husk: dry, dusty breath.
    voice('husk',
          lambda i, r: lay((vari(C(('grunt_03', 'breath')[i]), -2 - i), 0, 1.0), (A.highpass(r.standard_normal(A.n_of(0.6)) * A.decay(A.n_of(0.6), 0.25), 2500), 0, 0.12)),
          lambda i, r: lay((vari(C(('cough_01', 'cough_02')[i]), -3), 0, 1.0), (vari(C('grunt_04'), -2), 0.02, 0.4)),
          lambda i, r: lay((vari(C('cough_03'), -4), 0, 1.0), (vari(C('grunt_01'), -4), 0.12, 0.5), (A.highpass(r.standard_normal(A.n_of(1.0)) * A.decay(A.n_of(1.0), 0.4), 2500), 0.2, 0.15)),
          n=(2, 2, 1))
    # Sporeling: a little puffball.
    voice('sporeling',
          lambda i, r: lay((vari(C(('cute_01', 'cute_08')[i]), 2), 0, 0.8), (cut(vari(C('burble_01'), 1), 0, 0.2), 0.05, 0.3)),
          lambda i, r: lay((vari(C(('cute_06', 'cute_02')[i]), 3), 0, 0.9), (swish(0.25, 400, 3000, r, 0.1), 0, 0.35)),
          lambda i, r: lay((vari(C('cute_02'), 1), 0, 0.9), (swish(0.6, 300, 2600, r, 0.1), 0.05, 0.5)),
          n=(2, 2, 1), volume=(0.5, 0.7, 0.8))
    # Living wood: stranglers and rootlings creak and groan.
    voice('treant',
          lambda i, r: A.lowpass(lay((vari(R(('creak1', 'creak2')[i]), -5), 0, 1.0), (vari(C('grunt_02'), -7), 0.1, 0.3)), 3200),
          lambda i, r: lay((vari(R('creak3'), -3 - i), 0, 1.0), (vari(K(f'impactWood_medium_00{i}'), -2), 0, 0.5)),
          lambda i, r: lay((vari(R('creak2'), -7), 0, 1.0), (vari(K('impactWood_heavy_003'), -4), 0.25, 0.8), (vari(K('impactPlank_medium_001'), -6), 0.32, 0.5)),
          n=(2, 2, 1))
    # Rime wight: a frozen ghost that rings like ice.
    voice('wight',
          lambda i, r: room(lay((pv_pitch(C(('weird_03', 'ooh')[i]), -3), 0, 0.8), (ring([r.uniform(2600, 3400), 5100], 1.0, 0.4, r), 0.1, 0.25)), 0.5, 1.2, 1.4),
          lambda i, r: lay((cut(vari(C('scream_02'), -2 - i), 0, 0.32), 0, 0.7), (vari(K(f'impactGlass_light_00{i}'), 0), 0, 0.6)),
          lambda i, r: room(lay((pv_pitch(C('scream_01'), -5), 0, 0.8), (vari(K('impactGlass_heavy_002'), -3), 0.2, 0.8)), 0.5, 1.3, 1.6),
          n=(2, 2, 1))


# ---------------------------------------------------------------- interface

def interface():
    """The interface: paper, brass and leather rather than plastic clicks."""
    variants('ui.page', 'ui2/page', lambda i, r: A.highpass(vari(R(f'bookFlip{i + 1}'), r.uniform(-1.5, 0.5)), 180), 3, 0.55, subtitle=False)
    variants('ui.select', 'ui2/select', lambda i, r: A.lowpass(lay((vari(UI(f'click_00{i + 1}'), -3), 0, 1.0), (vari(K(f'impactWood_light_00{i}'), 7), 0, 0.25)), 7000), 2, 0.45, subtitle=False)
    variants('ui.hover', 'ui2/hover', lambda i, r: A.lowpass(vari(UI(f'tick_00{i + 1}'), -2), 6000), 2, 0.22, subtitle=False)
    # A short wooden tock for moving between choices (it plays at every step of a sweep across a
    # list, so it must be over before the next one starts).
    variants('ui.move', 'ui2/move', lambda i, r: A.lowpass(lay((cut(vari(UI(f'select_00{i + 1}'), -2), 0, 0.09), 0, 1.0),
                                                             (cut(vari(UI(f'drop_00{i + 2}'), 4), 0, 0.08), 0, 0.3)), 6500), 2, 0.4, subtitle=False)
    variants('ui.open', 'ui2/open', lambda i, r: lay((vari(R('bookOpen'), -1 - i), 0, 1.0), (swish(0.25, 500, 3500, r, 0.4), 0, 0.25)), 2, 0.6, subtitle=False)
    variants('ui.close', 'ui2/close', lambda i, r: lay((vari(R('bookClose'), -1 - i), 0, 1.0), (swish(0.2, 400, 3000, r, 0.5), 0, 0.2)), 2, 0.55, subtitle=False)
    variants('ui.tab', 'ui2/tab', lambda i, r: lay((vari(UI(f'switch_00{i + 2}'), -3), 0, 0.8), (vari(R(f'bookPlace{i + 1}'), 2), 0.01, 0.4)), 2, 0.45, subtitle=False)
    variants('ui.choose', 'ui2/choose', lambda i, r: lay((vari(UI(f'confirmation_00{i + 1}'), -2), 0, 0.8), (vari(R('metalClick'), -2), 0, 0.35)), 2, 0.5, subtitle=False)
    variants('ui.error', 'ui2/error', lambda i, r: A.lowpass(vari(UI(f'error_00{i + 2}'), -4), 3500), 2, 0.45, subtitle=False)
    variants('ui.equip', 'ui2/equip', lambda i, r: lay((vari(R(('clothBelt', 'beltHandle1', 'handleSmallLeather')[i]), r.uniform(-2, 0)), 0, 1.0), (vari(R('metalLatch'), 1), 0.04, 0.3)), 3, 0.6, subtitle=False)
    variants('ui.coin', 'ui2/coin', lambda i, r: vari(R(('handleCoins', 'handleCoins2')[i]), r.uniform(-1, 1)), 2, 0.5, subtitle=False)
    variants('ui.purchase', 'ui2/purchase', lambda i, r: lay((vari(R('handleCoins2'), -1), 0, 1.0), (ring([1318.5, 1975.5], 0.6, 0.25, r), 0.08, 0.18)), 1, 0.6, subtitle=False)


def write_events():
    with open(EVENTS_FILE, 'w') as f:
        json.dump(EVENTS, f, indent=1, sort_keys=True)
    merge_into_sounds_json()


def merge_into_sounds_json():
    """Our events win over gen_audio.py's of the same name; everything else is kept."""
    with open(SOUNDS_JSON) as f:
        sounds = json.load(f)
    if os.path.exists(EVENTS_FILE):
        with open(EVENTS_FILE) as f:
            sounds.update(json.load(f))
    with open(SOUNDS_JSON, 'w') as f:
        json.dump(sounds, f, indent=2)
    return sounds


if __name__ == '__main__':
    impacts()
    voices()
    interface()
    write_events()
    print(len(A.WRITTEN), 'files,', len(EVENTS), 'events')
