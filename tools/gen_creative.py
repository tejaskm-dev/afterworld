#!/usr/bin/env python3
"""Parchment backgrounds for Afterworld's creative tabs, recoloured from vanilla's item tab so every
slot stays exactly where the screen expects it.
usage: gen_creative.py <vanilla textures/gui/container/creative_inventory/tab_items.png>"""
import os, sys
from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(HERE, '..', 'src', 'main', 'resources', 'assets', 'afterworld', 'textures', 'gui', 'container', 'creative_inventory')


def hexc(h, a=255):
    h = h.lstrip('#')
    return (int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16), a)


# vanilla grey -> aged parchment, bronze-dark edges
PALETTE = {
    (198, 198, 198, 255): hexc('#CDBE9A'),
    (139, 139, 139, 255): hexc('#8C7A58'),
    (255, 255, 255, 255): hexc('#F2E8CF'),
    (55, 55, 55, 255): hexc('#3E3222'),
    (0, 0, 0, 255): hexc('#1E170F'),
    (85, 85, 85, 255): hexc('#5A4A33'),
}

if __name__ == '__main__':
    src = Image.open(sys.argv[1]).convert('RGBA')
    px = src.load()
    w, h = src.size
    out = Image.new('RGBA', (w, h))
    po = out.load()
    for y in range(h):
        for x in range(w):
            c = px[x, y]
            if c[3] == 0:
                po[x, y] = c
                continue
            m = PALETTE.get(c)
            if m is None:
                # Anything else: tint toward parchment keeping its brightness.
                g = sum(c[:3]) / 3 / 255
                m = (int(0x30 + g * 0xC2), int(0x26 + g * 0xB4), int(0x18 + g * 0x9A), c[3])
            # A faint grain on the panel so it reads as paper, not plastic.
            if m == PALETTE[(198, 198, 198, 255)] and (x * 7 + y * 13) % 11 == 0:
                m = hexc('#C6B690')
            po[x, y] = m
    os.makedirs(OUT, exist_ok=True)
    out.save(os.path.join(OUT, 'tab_afterworld.png'))
    print('creative tab background written')
