#!/usr/bin/env python3
"""Renders box models (gen_models Part trees) with their textures, the way the game would: a
three-quarter view with simple lighting and the glow layer drawn full-bright. For checking
creature and boss textures without starting the game.

Usage: python3 tools/preview_models.py <out.png> [texture dir] [names...]
"""
import math
import os
import sys

import numpy as np
from PIL import Image

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))


def rot_zyx(rx, ry, rz):
    cx, sx = math.cos(rx), math.sin(rx)
    cy, sy = math.cos(ry), math.sin(ry)
    cz, sz = math.cos(rz), math.sin(rz)
    rxm = np.array([[1, 0, 0], [0, cx, -sx], [0, sx, cx]])
    rym = np.array([[cy, 0, sy], [0, 1, 0], [-sy, 0, cy]])
    rzm = np.array([[cz, -sz, 0], [sz, cz, 0], [0, 0, 1]])
    return rzm @ rym @ rxm


def quads(part, mat=None, off=None, out=None):
    """Every face of every box as (4 model-space corners, (u0, v0, u1, v1) in texels)."""
    if mat is None:
        mat, off, out = np.eye(3), np.zeros(3), []
    m = mat @ rot_zyx(*part.rot)
    o = off + mat @ np.array(part.pivot, dtype=float)
    for b in part.boxes:
        w, h, d = b.size
        g = b.inflate
        x1, y1, z1 = b.origin[0] - g, b.origin[1] - g, b.origin[2] - g
        x2, y2, z2 = b.origin[0] + w + g, b.origin[1] + h + g, b.origin[2] + d + g
        v = [np.array(p, dtype=float) for p in [(x1, y1, z1), (x2, y1, z1), (x2, y2, z1), (x1, y2, z1),
                                                (x1, y1, z2), (x2, y1, z2), (x2, y2, z2), (x1, y2, z2)]]
        v = [o + m @ p for p in v]
        wi, hi, di = (int(math.ceil(s)) for s in b.size)
        u, vv = b.u, b.v
        f4, f5, f6, f7, f8, f9 = u, u + di, u + di + wi, u + di + wi + wi, u + di + wi + di, u + di + wi + di + wi
        f10, f11, f12 = vv, vv + di, vv + di + hi
        # (vertices, u1, v1, u2, v2) exactly as the game's ModelPart.Cube builds them.
        for verts, a, bq, c, dq in [
            ((v[5], v[4], v[0], v[1]), f5, f10, f6, f11),
            ((v[2], v[3], v[7], v[6]), f6, f11, f7, f10),
            ((v[0], v[4], v[7], v[3]), f4, f11, f5, f12),
            ((v[1], v[0], v[3], v[2]), f5, f11, f6, f12),
            ((v[5], v[1], v[2], v[6]), f6, f11, f8, f12),
            ((v[4], v[5], v[6], v[7]), f8, f11, f9, f12),
        ]:
            uvs = [(c, bq), (a, bq), (a, dq), (c, dq)]
            out.append((verts, uvs))
    for ch in part.children:
        quads(ch, m, o, out)
    return out


def render(root, tex, glow, scale=5, yaw=35, pitch=22, size=None):
    tex = np.asarray(tex.convert('RGBA'), dtype=float) / 255.0
    glw = np.asarray(glow.convert('RGBA'), dtype=float) / 255.0 if glow is not None else None
    th, tw = tex.shape[0], tex.shape[1]
    qs = quads(root)
    ya, pa = math.radians(yaw), math.radians(pitch)
    # Model space has y down and faces -z; turn it the way the game draws an entity facing us.
    flip = np.diag([1.0, -1.0, -1.0])
    rot = np.array([[math.cos(ya), 0, math.sin(ya)], [0, 1, 0], [-math.sin(ya), 0, math.cos(ya)]])
    tilt = np.array([[1, 0, 0], [0, math.cos(pa), -math.sin(pa)], [0, math.sin(pa), math.cos(pa)]])
    view = tilt @ rot @ flip
    pts = []
    for verts, uvs in qs:
        pts.append(([view @ p for p in verts], uvs))
    allp = np.array([p for ps, _ in pts for p in ps])
    lo, hi = allp.min(axis=0), allp.max(axis=0)
    pad = 6
    W = int((hi[0] - lo[0]) * scale) + 2 * pad
    H = int((hi[1] - lo[1]) * scale) + 2 * pad
    img = np.zeros((H, W, 4))
    zbuf = np.full((H, W), -1e9)
    light = np.array([0.35, 0.85, 0.4])
    light /= np.linalg.norm(light)

    def sx(p):
        return (p[0] - lo[0]) * scale + pad, (hi[1] - p[1]) * scale + pad, p[2]

    for ps, uvs in pts:
        n = np.cross(ps[1] - ps[0], ps[2] - ps[0])
        nn = np.linalg.norm(n)
        if nn < 1e-9:
            continue
        n /= nn
        if n[2] < 0:
            n = -n
        shade_k = 0.55 + 0.45 * max(0.0, float(n @ light))
        s = [sx(p) for p in ps]
        for tri in ((0, 1, 2), (0, 2, 3)):
            (x0, y0, z0), (x1, y1, z1), (x2, y2, z2) = (s[i] for i in tri)
            (u0, v0), (u1, v1), (u2, v2) = (uvs[i] for i in tri)
            minx, maxx = int(max(0, math.floor(min(x0, x1, x2)))), int(min(W - 1, math.ceil(max(x0, x1, x2))))
            miny, maxy = int(max(0, math.floor(min(y0, y1, y2)))), int(min(H - 1, math.ceil(max(y0, y1, y2))))
            if minx > maxx or miny > maxy:
                continue
            den = (y1 - y2) * (x0 - x2) + (x2 - x1) * (y0 - y2)
            if abs(den) < 1e-9:
                continue
            xs, ys = np.meshgrid(np.arange(minx, maxx + 1) + 0.5, np.arange(miny, maxy + 1) + 0.5)
            a = ((y1 - y2) * (xs - x2) + (x2 - x1) * (ys - y2)) / den
            b = ((y2 - y0) * (xs - x2) + (x0 - x2) * (ys - y2)) / den
            c = 1 - a - b
            inside = (a >= -1e-6) & (b >= -1e-6) & (c >= -1e-6)
            if not inside.any():
                continue
            z = a * z0 + b * z1 + c * z2
            u = a * u0 + b * u1 + c * u2
            v = a * v0 + b * v1 + c * v2
            ui = np.clip(np.floor(u - 1e-4).astype(int), 0, tw - 1)
            vi = np.clip(np.floor(v - 1e-4).astype(int), 0, th - 1)
            col = tex[vi, ui]
            region = (slice(miny, maxy + 1), slice(minx, maxx + 1))
            ok = inside & (col[..., 3] > 0.1) & (z > zbuf[region])
            if not ok.any():
                continue
            rgb = col[..., :3] * shade_k
            if glw is not None:
                gc = glw[vi, ui]
                rgb = rgb * (1 - gc[..., 3:4]) + gc[..., :3] * gc[..., 3:4]
            sub = img[region]
            sub[ok, :3] = rgb[ok]
            sub[ok, 3] = 1.0
            zb = zbuf[region]
            zb[ok] = z[ok]
    return Image.fromarray((np.clip(img, 0, 1) * 255).astype(np.uint8), 'RGBA')


def main():
    import gen_mobs
    from gen_models import pack
    out = sys.argv[1]
    texdir = sys.argv[2] if len(sys.argv) > 2 else gen_mobs.TEX_OUT
    want = set(sys.argv[3:])
    tiles = []
    for cls, tex, fn, width, seed in gen_mobs.SPECS:
        if want and tex not in want:
            continue
        root = fn()
        pack(root, width)
        t = Image.open(os.path.join(texdir, tex + '.png'))
        gp = os.path.join(texdir, tex + '_glow.png')
        g = Image.open(gp) if os.path.exists(gp) else None
        tiles.append((tex, render(root, t, g)))
    cols = 6
    cw = max(t.width for _, t in tiles) + 10
    ch = max(t.height for _, t in tiles) + 10
    rows = (len(tiles) + cols - 1) // cols
    sheet = Image.new('RGBA', (cols * cw, rows * ch), (52, 58, 66, 255))
    for i, (name, t) in enumerate(tiles):
        x = (i % cols) * cw + (cw - t.width) // 2
        y = (i // cols) * ch + (ch - t.height) - 5
        sheet.alpha_composite(t, (x, y))
    sheet.save(out)
    print(len(tiles), 'models ->', out)


if __name__ == '__main__':
    main()
