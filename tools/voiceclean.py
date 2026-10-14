"""Cleans up generated voice takes before they are cut into lines.

    denoise   noise reduction learned from the take's own pauses (decision-directed Wiener
              filter, the classic way to remove hiss without the warbling "musical noise")
    deharsh   a dynamic de-esser: tames the 4-9 kHz fizz only when it spikes
    declick   repairs single-sample crackles
    highpass  removes rumble below the voice
    presence  a gentle lift around 2-5 kHz for voices that came out muffled
    lowpass   soft roll-off of the top octave for fizzy takes

Profiles per speaker are in PROFILES; anything not listed gets a light pass.
"""
import numpy as np

PROFILES = {
    # Heavy hiss in every pause and a muffled top: strong noise reduction, crackle repair, a little presence.
    'marta': dict(denoise=1.0, floor=-24, declick=True, presence=2.5, highpass=70),
    # Rumble under the voice and a harsh, sizzling top: high-pass, de-ess, and a softer top octave.
    'rashid': dict(denoise=0.7, floor=-20, highpass=100, deharsh=7, lowpass=9500, declick=True),
    # A hum and hiss bed under the whole take.
    'ines': dict(denoise=1.0, floor=-26, highpass=95, deharsh=6, lowpass=9500),
    'odo': dict(denoise=1.0, floor=-26, deharsh=5, declick=True, lowpass=10500),
    'wren': dict(denoise=0.35, floor=-16, deharsh=3),
    'king': dict(denoise=0.6, floor=-18),
    'warden': dict(denoise=0.6, floor=-18),
    'mab': dict(denoise=0.6, floor=-18),
}
DEFAULT = dict(denoise=0.35, floor=-16)

N_FFT = 2048
HOP = 512


def _stft(x):
    w = np.hanning(N_FFT + 1)[:-1] ** 0.5
    pad = np.concatenate([np.zeros(N_FFT), x, np.zeros(N_FFT)])
    frames = 1 + (len(pad) - N_FFT) // HOP
    idx = np.arange(N_FFT)[None, :] + HOP * np.arange(frames)[:, None]
    return np.fft.rfft(pad[idx] * w, axis=1), w, len(pad)


def _istft(X, w, n_pad, n):
    frames = np.fft.irfft(X, N_FFT, axis=1) * w
    out = np.zeros(n_pad)
    norm = np.zeros(n_pad)
    for i in range(len(frames)):
        out[i * HOP:i * HOP + N_FFT] += frames[i]
        norm[i * HOP:i * HOP + N_FFT] += w ** 2
    norm[norm < 1e-8] = 1
    return (out / norm)[N_FFT:N_FFT + n]


def _median5(x):
    pad = np.concatenate([x[:2][::-1], x, x[-2:][::-1]])
    st = np.lib.stride_tricks.sliding_window_view(pad, 5)
    return np.median(st, axis=1)


def declick(x, k=7.0):
    """Replaces lone spikes (far from their neighbours' median) with that median."""
    med = _median5(x)
    r = x - med
    mad = np.convolve(np.abs(r), np.ones(441) / 441, mode='same') + 1e-6
    bad = np.abs(r) > k * mad
    # Only isolated spikes: a real transient spans many samples.
    run = np.convolve(bad.astype(float), np.ones(9), mode='same')
    bad &= run <= 3
    y = x.copy()
    y[bad] = med[bad]
    return y


def clean(x, sr, who):
    p = PROFILES.get(who, DEFAULT)
    if p.get('declick'):
        x = declick(x)
    X, w, n_pad = _stft(x)
    mag2 = np.abs(X) ** 2
    f = np.fft.rfftfreq(N_FFT, 1.0 / sr)
    gain = np.ones(mag2.shape)

    if p.get('denoise', 0) > 0:
        # The noise: what the quietest tenth of the take sounds like, per frequency.
        e = mag2.sum(axis=1)
        quiet = e <= np.percentile(e, 10)
        noise = np.median(mag2[quiet], axis=0) * 1.5 + 1e-12
        alpha = 0.98
        g_min = 10 ** (p.get('floor', -18) / 20)
        strength = p['denoise']
        prev = np.zeros(mag2.shape[1])
        for t in range(len(mag2)):
            gamma = mag2[t] / noise
            xi = alpha * prev + (1 - alpha) * np.maximum(gamma - 1, 0)
            g = xi / (1 + xi)
            g = 1 - strength * (1 - g)
            g = np.maximum(g, g_min)
            # A little smoothing across frequency keeps it from twittering.
            g = np.convolve(g, np.ones(3) / 3, mode='same')
            gain[t] *= g
            prev = (g ** 2) * gamma

    if p.get('deharsh'):
        hi = (f >= 4000) & (f < 9000)
        mid = (f >= 800) & (f < 3500)
        ratio = (mag2[:, hi].sum(axis=1) + 1e-12) / (mag2[:, mid].sum(axis=1) + 1e-12)
        ref = np.median(ratio)
        cut_db = p['deharsh']
        over = np.clip(np.log10(ratio / (ref + 1e-12)) * 10 / 6, 0, 1)
        # Smooth the de-esser so it breathes rather than flutters.
        over = np.convolve(over, np.ones(5) / 5, mode='same')
        g_hi = 10 ** (-cut_db * (0.35 + 0.65 * over) / 20)
        gain[:, hi] *= g_hi[:, None]

    shape = np.ones_like(f)
    if p.get('highpass'):
        fc = p['highpass']
        shape *= (f / fc) ** 4 / (1 + (f / fc) ** 4)
    if p.get('lowpass'):
        fc = p['lowpass']
        shape *= 1 / np.sqrt(1 + (f / fc) ** 6)
    if p.get('presence'):
        lift = 10 ** (p['presence'] / 20) - 1
        shape *= 1 + lift * np.exp(-0.5 * ((np.log2(np.maximum(f, 1) / 3200)) / 0.8) ** 2)
    gain *= shape[None, :]
    y = _istft(X * gain, w, n_pad, len(x))
    return y
