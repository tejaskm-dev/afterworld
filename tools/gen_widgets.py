#!/usr/bin/env python3
"""Themed vanilla widget sprites: slate buttons with bronze trim and amber highlights.
They override minecraft:gui/sprites/widget/*, so every menu matches Afterworld's title screen."""
import json, os
from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(HERE, '..', 'src', 'main', 'resources', 'assets', 'minecraft', 'textures', 'gui', 'sprites', 'widget')


def hexc(h, a=255):
    h = h.lstrip('#')
    return (int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16), a)


def lerp(c1, c2, t):
    return tuple(int(round(c1[i] + (c2[i] - c1[i]) * t)) for i in range(4))


def button(top, bottom, outer, trim, shine, stud):
    W, H = 200, 20
    img = Image.new('RGBA', (W, H))
    px = img.load()
    for y in range(H):
        for x in range(W):
            px[x, y] = lerp(top, bottom, y / (H - 1))
    for x in range(W):
        px[x, 0] = outer
        px[x, H - 1] = outer
        px[x, 1] = trim
        px[x, H - 2] = lerp(trim, outer, 0.45)
        px[x, 2] = shine
    for y in range(H):
        px[0, y] = outer
        px[W - 1, y] = outer
        if 0 < y < H - 1:
            px[1, y] = trim
            px[W - 2, y] = lerp(trim, outer, 0.3)
    if stud:
        for (x, y) in ((2, 3), (W - 3, 3), (2, H - 4), (W - 3, H - 4)):
            px[x, y] = stud
    return img


def slider_handle(face, edge, shine):
    W, H = 8, 20
    img = Image.new('RGBA', (W, H))
    px = img.load()
    for y in range(H):
        for x in range(W):
            edge_px = x in (0, W - 1) or y in (0, H - 1)
            px[x, y] = edge if edge_px else lerp(face, lerp(face, edge, 0.5), y / H)
    for x in range(1, W - 1):
        px[x, 1] = shine
    for y in range(6, 14, 3):
        px[3, y] = edge
        px[4, y] = edge
    return img


def write(name, img, border):
    os.makedirs(OUT, exist_ok=True)
    img.save(os.path.join(OUT, name + '.png'))
    w, h = img.size
    meta = {'gui': {'scaling': {'type': 'nine_slice', 'width': w, 'height': h, 'border': border}}}
    with open(os.path.join(OUT, name + '.png.mcmeta'), 'w') as f:
        json.dump(meta, f, indent=2)


if __name__ == '__main__':
    outer = hexc('#06080B')
    write('button', button(hexc('#232B34'), hexc('#141A20'), outer, hexc('#7A5A2E'), hexc('#34404C'), hexc('#C08A2E')), 3)
    write('button_highlighted', button(hexc('#3A3020'), hexc('#1E1912'), outer, hexc('#E8A33D'), hexc('#6A5228'), hexc('#FFD27A')), 3)
    write('button_disabled', button(hexc('#15191E'), hexc('#101317'), outer, hexc('#2E3238'), hexc('#1C2128'), None), 3)
    write('slider', button(hexc('#12161B'), hexc('#191F26'), outer, hexc('#4A3A22'), hexc('#0E1115'), None), 3)
    write('slider_highlighted', button(hexc('#161B21'), hexc('#1E252D'), outer, hexc('#9A6E2E'), hexc('#121619'), None), 3)
    handle_border = {'left': 2, 'top': 2, 'right': 2, 'bottom': 3}
    write('slider_handle', slider_handle(hexc('#9A7A44'), hexc('#3A2A14'), hexc('#D8B070')), handle_border)
    write('slider_handle_highlighted', slider_handle(hexc('#E8A33D'), hexc('#5A3A12'), hexc('#FFE0A0')), handle_border)
    print('widgets written')
