#!/usr/bin/env python3
"""Splits whole-character voice recordings into the script's single lines.

When a character's lines are generated in one go (one long take per character), this finds
where each line starts and ends, the way audiobook aligners do: it reads the same text with the
system's own speech voice, lines the two readings up (dynamic time warping over MFCCs, coarse
then fine), maps every line boundary across, and snaps each cut into the nearest real pause.

    python3 tools/split_voices.py              (reads voice/incoming/*.mp3|wav|ogg|flac|m4a)

Recordings are matched to characters by file name (isolde_1.mp3, isolde_2.mp3 ... are joined in
order). Writes voice/split/<who>__<line>.wav for tools/import_voices.py, plus a report and a
picture of every cut in voice/split/check/ for a quick look.
"""
import csv
import os
import re
import subprocess
import sys
import tempfile

import numpy as np

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import voiceclean  # noqa: E402

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.join(HERE, '..')
INCOMING = os.path.join(ROOT, 'voice', 'incoming')
OUT = os.path.join(ROOT, 'voice', 'split')
LINES = os.path.join(ROOT, 'voice', 'lines.csv')
AR = 16000          # analysis rate
HOP = 0.01
# File names as recorded, to the script's speaker ids.
ALIASES = {'martha': 'marta', 'toravld': 'torvald', 'litch': 'lich', 'bellringer': 'bell', 'bell_ringer': 'bell', 'layla': 'leyla'}
REF_VOICES = ['Samantha', 'Daniel']


def read(path):
    import soundfile as sf
    x, sr = sf.read(path, dtype='float64', always_2d=True)
    return x.mean(axis=1), sr


def resample(x, sr, to):
    if sr == to:
        return x
    n = int(len(x) * to / sr)
    return np.interp(np.arange(n) * sr / to, np.arange(len(x)), x)


def speaker_of(fname):
    base = os.path.splitext(fname)[0].lower()
    m = re.match(r'^([a-z_]+?)(?:[_ -]?(\d+))?$', base)
    if not m or '__' in base:
        return None, 0
    who = ALIASES.get(m.group(1), m.group(1))
    return who, int(m.group(2) or 1)


# ---------------------------------------------------------------- features

def mel_bank(n_mel, n_fft, sr, fmin=60, fmax=7600):
    def hz2mel(f):
        return 2595 * np.log10(1 + f / 700)

    def mel2hz(m):
        return 700 * (10 ** (m / 2595) - 1)
    mels = np.linspace(hz2mel(fmin), hz2mel(fmax), n_mel + 2)
    hz = mel2hz(mels)
    bins = np.floor((n_fft + 1) * hz / sr).astype(int)
    fb = np.zeros((n_mel, n_fft // 2 + 1))
    for i in range(1, n_mel + 1):
        a, b, c = bins[i - 1], bins[i], bins[i + 1]
        for k in range(a, b):
            fb[i - 1, k] = (k - a) / max(1, b - a)
        for k in range(b, c):
            fb[i - 1, k] = (c - k) / max(1, c - b)
    return fb


def features(x):
    """MFCC c1..c12 plus log energy, 10 ms hop, normalised per recording."""
    win, hop, nfft = int(0.025 * AR), int(HOP * AR), 512
    n = max(1, 1 + (len(x) - win) // hop)
    fb = mel_bank(40, nfft, AR)
    dct = np.cos(np.pi / 40 * (np.arange(40)[None, :] + 0.5) * np.arange(13)[:, None])
    w = np.hanning(win).astype(np.float32)
    cep = np.zeros((n, 12), dtype=np.float32)
    energy = np.zeros(n, dtype=np.float32)
    for s in range(0, n, 4000):
        e = min(n, s + 4000)
        idx = np.arange(win)[None, :] + hop * np.arange(s, e)[:, None]
        idx = np.minimum(idx, len(x) - 1)
        fr = x[idx].astype(np.float32) * w[None, :]
        spec = np.abs(np.fft.rfft(fr, nfft)) ** 2
        logmel = np.log(spec @ fb.T + 1e-8)
        c = logmel @ dct.T
        cep[s:e] = c[:, 1:13]
        energy[s:e] = np.log(np.sum(fr ** 2, axis=1) + 1e-8)
    cep = (cep - cep.mean(axis=0)) / (cep.std(axis=0) + 1e-6)
    en = (energy - energy.mean()) / (energy.std() + 1e-6)
    return np.concatenate([cep, 0.8 * en[:, None]], axis=1), energy


def pool(f, k):
    n = len(f) // k
    return f[:n * k].reshape(n, k, -1).mean(axis=1)


# ---------------------------------------------------------------- alignment

def dtw(a, b, lo, hi):
    """DTW of a (rows) against b (columns) inside per-row bands [lo[i], hi[i]). Returns the path."""
    n = len(a)
    inf = np.float32(1e18)
    prev = None
    prev_lo = 0
    moves = []
    for i in range(n):
        l, h = int(lo[i]), int(hi[i])
        c = np.sqrt(np.sum((b[l:h] - a[i]) ** 2, axis=1)).astype(np.float64)
        if prev is None:
            # The first row can only start at its first column, then walk right.
            A = np.full(h - l, inf)
            if l == 0:
                A[0] = c[0]
            up = np.zeros(h - l, dtype=bool)
        else:
            jj = np.arange(l, h)
            def get(col):
                k = col - prev_lo
                ok = (k >= 0) & (k < len(prev))
                out = np.full(len(col), inf)
                out[ok] = prev[k[ok]]
                return out
            diag = get(jj - 1)
            vert = get(jj)
            up = vert < diag
            A = c + np.minimum(diag, vert)
        S = np.cumsum(c)
        # A run of horizontal steps: D[j] = min over k<=j of (A[k] + sum c[k+1..j]).
        M = np.minimum.accumulate(A - S)
        D = S + M
        left = (A - S) > M + 1e-9
        moves.append((l, up, left))
        prev, prev_lo = D, l
    # Walk back from the corner.
    path = []
    i, j = n - 1, len(b) - 1
    while i >= 0:
        l, up, left = moves[i]
        k = j - l
        if k < 0 or k >= len(up):
            break
        path.append((i, j))
        if left[k]:
            j -= 1
        elif i == 0:
            if j == 0:
                break
            j -= 1
        elif up[k]:
            i -= 1
        else:
            i -= 1
            j -= 1
    path.reverse()
    return np.array(path)


def band_from_path(path, n, m, scale, width):
    lo = np.full(n, m, dtype=int)
    hi = np.zeros(n, dtype=int)
    for i, j in path:
        for ii in range(i * scale, min(n, (i + 1) * scale)):
            lo[ii] = min(lo[ii], j * scale - width)
            hi[ii] = max(hi[ii], (j + 1) * scale + width)
    # Fill rows the coarse path skipped and keep the band monotone.
    for ii in range(1, n):
        if lo[ii] > hi[ii]:
            lo[ii], hi[ii] = lo[ii - 1], hi[ii - 1]
    lo = np.maximum.accumulate(np.clip(lo, 0, m - 1))
    hi = np.minimum.accumulate(np.clip(hi, 1, m)[::-1])[::-1]
    hi = np.maximum(hi, lo + 1)
    return lo, hi


def align(rec_f, ref_f):
    """Coarse (100 ms) full DTW, then fine (20 ms) DTW in a band around it. Path in 20 ms frames."""
    rf, ff = pool(rec_f, 2), pool(ref_f, 2)
    rc, fc = pool(rf, 5), pool(ff, 5)
    n, m = len(rc), len(fc)
    path = dtw(rc, fc, np.zeros(n, dtype=int), np.full(n, m, dtype=int))
    lo, hi = band_from_path(path, len(rf), len(ff), 5, 60)
    return dtw(rf, ff, lo, hi)


# ---------------------------------------------------------------- pauses

def pauses(energy_db):
    floor = np.percentile(energy_db, 5)
    speech = np.percentile(energy_db, 70)
    thr = floor + (speech - floor) * 0.3
    sil = energy_db < thr
    runs, i, n = [], 0, len(sil)
    while i < n:
        if sil[i]:
            j = i
            while j < n and sil[j]:
                j += 1
            runs.append((i * HOP, j * HOP))
            i = j
        else:
            i += 1
    return [r for r in runs if r[1] - r[0] >= 0.05], thr


def choose_cuts(est, gaps, joins, spread=None, quietest=None):
    """Picks one pause per boundary, all together: each cut wants to sit in a long pause close to
    where the alignment put it, and neighbouring cuts want to be off by about the same amount (the
    alignment drifts smoothly, it doesn't jump about)."""
    cands = []
    for k, e in enumerate(est):
        forced = [jn for jn in joins if abs(jn - e) < 4.0]
        cs = []
        for (g0, g1) in gaps:
            c = (g0 + g1) / 2
            if forced:
                if abs(c - forced[0]) < 0.7:
                    cs.append((c, g1 - g0, -50.0))
                continue
            sigma = max(0.5, (spread[k] if spread else 0.0) * 0.75)
            if abs(c - e) <= 2.2 + sigma:
                u = ((c - e) / sigma) ** 2 - 1.2 * np.log(max(g1 - g0, 0.03) / 0.12)
                cs.append((c, g1 - g0, u))
        if not forced:
            # No pause in reach: cut at the quietest instant near the estimate rather than mid-word.
            q = quietest(e) if quietest else e
            cs.append((q, 0.0, 1.2 * np.log(0.12 / 0.03) + 0.5 + ((q - e) / 0.5) ** 2))
        if not cs:
            cs.append((forced[0] if forced else e, 0.0, 0.0))
        cands.append(cs)
    n = len(est)
    if n == 0:
        return [], []
    cost = [np.array([c[2] for c in cands[0]])]
    back = []
    for k in range(1, n):
        cur = np.array([c[2] for c in cands[k]])
        prev_c = np.array([c[0] for c in cands[k - 1]])
        prev_o = prev_c - est[k - 1]
        here_c = np.array([c[0] for c in cands[k]])
        here_o = here_c - est[k]
        pair = ((here_o[:, None] - prev_o[None, :]) / 0.6) ** 2
        pair[here_c[:, None] - prev_c[None, :] < 0.5] = 1e9
        total = pair + cost[-1][None, :]
        b = np.argmin(total, axis=1)
        cost.append(cur + total[np.arange(len(cur)), b])
        back.append(b)
    j = int(np.argmin(cost[-1]))
    pick = [j]
    for k in range(n - 2, -1, -1):
        j = int(back[k][j])
        pick.append(j)
    pick.reverse()
    cuts, conf = [], []
    for k, j in enumerate(pick):
        c, length, _ = cands[k][j]
        cuts.append(c)
        d = abs(c - est[k])
        conf.append(('ok' if d < 0.6 and length > 0 else 'no pause' if length == 0 else 'far', d))
    return cuts, conf


def reference_estimates(lines, rec_dur, rec_f, voice):
    """Reads the lines with a system voice, aligns that reading to the recording, and returns where
    each line boundary falls in the recording (seconds), plus the reading's audio per line."""
    with tempfile.TemporaryDirectory() as tmp:
        parts, bounds, t = [], [], 0.0
        total_chars = sum(len(text) for _, text in lines)
        rate = int(np.clip(total_chars / max(1.0, rec_dur) * 60 / 5.6, 120, 260))
        for k, (key, text) in enumerate(lines):
            p = os.path.join(tmp, f'{k}.wav')
            say(text, p, rate, voice)
            y, ys = read(p)
            y = resample(y, ys, AR)
            parts.append(y)
            t += len(y) / AR
            gap = 0.45
            parts.append(np.zeros(int(gap * AR)))
            bounds.append(t + gap / 2)
            t += gap
        ref = np.concatenate(parts)
    ref_f, _ = features(ref)
    path = align(rec_f, ref_f)
    step = 0.02
    est = []
    for b in bounds[:-1]:
        j = int(b / step)
        rows_at = path[path[:, 1] == j][:, 0]
        if len(rows_at) == 0:
            k = np.argmin(np.abs(path[:, 1] - j))
            rows_at = np.array([path[k, 0]])
        est.append(float(np.median(rows_at)) * step)
    return est, parts


def say(text, path, rate, voice):
    subprocess.run(['say', '-v', voice, '-r', str(rate), '--file-format=WAVE', '--data-format=LEI16@16000', '-o', path, text],
                   check=True)


def clean(text):
    return re.sub(r'\s+', ' ', re.sub(r'\[[^\]]*\]', ' ', text)).strip()


def picture(path, rec_db, cuts, est, labels, thr, dur):
    from PIL import Image, ImageDraw
    secs = 60
    rows = int(np.ceil(dur / secs))
    W, RH = 2400, 90
    img = Image.new('RGB', (W, rows * RH + 10), (24, 26, 30))
    d = ImageDraw.Draw(img)
    pps = W / secs
    lo, hi = np.percentile(rec_db, 2), np.percentile(rec_db, 99.5)
    for k, v in enumerate(rec_db):
        t = k * HOP
        r, x = int(t // secs), int((t % secs) * pps)
        y0 = r * RH + RH - 12
        hgt = int(max(0, (v - lo) / (hi - lo)) * (RH - 26))
        d.line([(x, y0), (x, y0 - hgt)], fill=(90, 140, 160) if v >= thr else (60, 64, 70))
    for t in est:
        r, x = int(t // secs), int((t % secs) * pps)
        d.line([(x, r * RH + 4), (x, r * RH + RH - 10)], fill=(80, 120, 255))
    for t, lab in zip(cuts, labels):
        r, x = int(t // secs), int((t % secs) * pps)
        d.line([(x, r * RH + 2), (x, r * RH + RH - 8)], fill=(255, 90, 70), width=2)
        d.text((x + 3, r * RH + 2), lab, fill=(255, 220, 200))
    img.save(path)


def main():
    rows = list(csv.DictReader(open(LINES, encoding='utf-8')))
    script = {}
    for r in rows:
        who, key = r['file'].rsplit('.', 1)[0].split('__')
        script.setdefault(who, []).append((key, clean(r['subtitle'])))
    takes = {}
    for f in sorted(os.listdir(INCOMING)):
        if not f.lower().endswith(('.mp3', '.wav', '.ogg', '.flac', '.m4a')):
            continue
        who, part = speaker_of(f)
        if who is None:
            continue
        if who not in script:
            print(f'  ? {f}: no speaker "{who}" in the script')
            continue
        takes.setdefault(who, []).append((part, f))
    only = set(sys.argv[1:])
    os.makedirs(os.path.join(OUT, 'check'), exist_ok=True)
    report = []
    import soundfile as sf
    for who in sorted(takes):
        if only and who not in only:
            continue
        lines = script[who]
        # The takes, joined in order with a second of silence (a pause the aligner can't miss).
        audio, sr = None, None
        joins = []
        for part, f in sorted(takes[who]):
            x, s = read(os.path.join(INCOMING, f))
            x = voiceclean.clean(x, s, who)
            if sr is None:
                sr, audio = s, x
            else:
                x = resample(x, s, sr)
                joins.append(len(audio) / sr + 0.5)
                audio = np.concatenate([audio, np.zeros(int(sr * 1.0)), x])
        rec = resample(audio, sr, AR)
        rec_f, rec_e = features(rec)
        rec_db = 10 * np.log10(np.exp(rec_e) + 1e-12)
        gaps, thr = pauses(rec_db)
        # Two reference readings by different voices; where they agree on a boundary it's solid,
        # where they don't, the cut leans harder on the real pauses.
        estimates = []
        parts = None
        for voice in REF_VOICES:
            e, p_ = reference_estimates(lines, len(audio) / sr, rec_f, voice)
            estimates.append(e)
            if parts is None:
                parts = p_
        est = list(np.mean(estimates, axis=0))
        spread = list(np.ptp(estimates, axis=0)) if len(estimates) > 1 else [0.0] * len(est)
        def quietest(t, reach=0.35):
            a = max(0, int((t - reach) / HOP))
            b = min(len(rec_db), int((t + reach) / HOP) + 1)
            if b <= a:
                return t
            # The quietest 60 ms run, so a single dip between syllables isn't mistaken for a pause.
            sm = np.convolve(rec_db[a:b], np.ones(6) / 6, mode='same')
            return (a + int(np.argmin(sm))) * HOP

        cuts, conf = choose_cuts(est, gaps, joins, spread, quietest)
        dur = len(rec) / AR
        edges = [0.0] + cuts + [dur]
        # Write each line from the original-rate audio.
        ref_total = sum(len(p) for p in parts[::2]) / AR
        for k, (key, text) in enumerate(lines):
            a, b = edges[k], edges[k + 1]
            seg = audio[int(a * sr):int(b * sr)]
            sf.write(os.path.join(OUT, f'{who}__{key}.wav'), seg.astype(np.float32), sr)
            ratio = (b - a) / max(0.2, len(parts[2 * k]) / AR * dur / ref_total)
            flag = '' if 0.55 < ratio < 1.8 else '  <-- check length'
            note = '' if k == 0 or conf[k - 1][0] == 'ok' else f'  <-- cut {conf[k - 1][0]} ({conf[k - 1][1]:.2f}s)'
            report.append(f'{who}__{key:24s} {a:7.2f}-{b:7.2f}s  {b - a:5.2f}s  x{ratio:4.2f}{flag}{note}  | {text[:60]}')
        picture(os.path.join(OUT, 'check', f'{who}.png'), rec_db, cuts, est, [k for k, _ in lines[1:]], thr, dur)
        print(f'{who}: {len(lines)} lines from {len(takes[who])} take(s), {dur:.1f}s')
    path = os.path.join(OUT, 'report.txt')
    if only and os.path.exists(path):
        # Keep the other speakers' lines from the last full run.
        kept = [ln.rstrip('\n') for ln in open(path) if ln.split('__')[0] not in only]
        report = sorted(kept + report)
    with open(path, 'w') as f:
        f.write('\n'.join(report) + '\n')
    print('report: voice/split/report.txt')


if __name__ == '__main__':
    main()
