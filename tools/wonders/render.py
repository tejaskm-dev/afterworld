"""Textured isometric renders of a vox.Grid, for checking builds without starting the game.

Blocks are drawn with their real textures (a texture pack's if given, else vanilla's) and as the
shapes they have in the game: slabs, stairs, trapdoors, doors, panes, fences, walls, lanterns,
plants and carpets, not all as cubes. Seen from the south-east (yaw turns the view by quarters).

    render.TEXTURES = ['<pack>/assets/minecraft/textures/block', '<vanilla>/.../block', '<mod>/textures/block']
    render.render(grid, 'out.png', scale=10, crop=(x0, z0, x1, z1))
"""
import os
import re

import numpy as np
from PIL import Image

TEXTURES = []
_TEX = {}
_SPRITE = {}

GREEN = np.array([0.50, 0.72, 0.32])
TINTS = {'grass_block_top': GREEN, 'short_grass': GREEN, 'tall_grass_top': GREEN, 'tall_grass_bottom': GREEN, 'fern': GREEN,
         'large_fern_top': GREEN, 'large_fern_bottom': GREEN, 'vine': GREEN, 'oak_leaves': GREEN, 'jungle_leaves': GREEN,
         'acacia_leaves': GREEN, 'dark_oak_leaves': GREEN, 'mangrove_leaves': GREEN, 'birch_leaves': np.array([0.55, 0.68, 0.36]),
         'spruce_leaves': np.array([0.38, 0.58, 0.38]), 'water_still': np.array([0.25, 0.45, 0.85]), 'lily_pad': GREEN,
         'grass_block_side_overlay': GREEN}


def parse(state):
    state = state.split(':', 1)[1] if ':' in state.split('[')[0] else state
    m = re.match(r'([a-z0-9_:]+)(?:\[(.*)\])?$', state)
    name = m.group(1)
    props = dict(kv.split('=') for kv in m.group(2).split(',')) if m.group(2) else {}
    return name, props


def tex(name):
    """A texture as an RGBA float array, tinted where the game tints it; None if not found."""
    if name in _TEX:
        return _TEX[name]
    arr = None
    for d in TEXTURES:
        p = os.path.join(d, name + '.png')
        if os.path.exists(p):
            im = Image.open(p).convert('RGBA')
            w, h = im.size
            if h > w:
                im = im.crop((0, 0, w, w))
            arr = np.asarray(im).astype(np.float32) / 255.0
            if name in TINTS:
                # The game colours these by biome; packs ship them grey (or nearly).
                grey = arr[:, :, :3].mean(axis=2, keepdims=True)
                arr[:, :, :3] = np.clip(grey * TINTS[name] * 1.35, 0, 1)
            break
    _TEX[name] = arr
    return arr


MATERIAL = {'stone_brick': 'stone_bricks', 'brick': 'bricks', 'mud_brick': 'mud_bricks', 'deepslate_tile': 'deepslate_tiles',
            'deepslate_brick': 'deepslate_bricks', 'nether_brick': 'nether_bricks', 'end_stone_brick': 'end_stone_bricks',
            'prismarine_brick': 'prismarine_bricks', 'polished_blackstone_brick': 'polished_blackstone_bricks', 'tuff_brick': 'tuff_bricks',
            'mossy_stone_brick': 'mossy_stone_bricks', 'red_nether_brick': 'red_nether_bricks', 'quartz': 'quartz_block_side',
            'smooth_quartz': 'quartz_block_bottom', 'smooth_stone': 'smooth_stone', 'sandstone': 'sandstone', 'smooth_sandstone': 'sandstone_top',
            'cut_copper': 'cut_copper', 'purpur': 'purpur_block', 'petrified_oak': 'oak_planks', 'bamboo_mosaic': 'bamboo_mosaic'}
WOODS = ('oak', 'spruce', 'birch', 'jungle', 'acacia', 'dark_oak', 'mangrove', 'cherry', 'bamboo', 'crimson', 'warped', 'pale_oak')


def material_texture(m):
    if m in MATERIAL:
        return MATERIAL[m]
    if m in WOODS:
        return m + '_planks'
    for cand in (m, m + 's', m + '_planks', m + '_block'):
        if tex(cand) is not None:
            return cand
    return m


def faces(name, props):
    """(top, side) texture names for a block."""
    if name.endswith('_stairs') or name.endswith('_slab'):
        t = material_texture(name.rsplit('_', 1)[0])
        return t, t
    if name.endswith('_wall') and not name.endswith('_banner'):
        t = material_texture(name[:-5])
        return t, t
    if name.endswith('_fence') or name.endswith('_fence_gate'):
        t = name.replace('_fence_gate', '').replace('_fence', '')
        return material_texture(t), material_texture(t)
    if name.endswith('_log') or name.endswith('_stem') or name.endswith('_wood') or name.endswith('_hyphae'):
        base = name.replace('_wood', '_log').replace('_hyphae', '_stem')
        if props.get('axis', 'y') == 'y':
            return base + '_top', base
        return base, base
    if name.endswith('_door'):
        return name + '_top', name + '_bottom'
    if name.endswith('_pane'):
        g = name[:-5] if name != 'glass_pane' else 'glass'
        return g, g
    special = {'grass_block': ('grass_block_top', 'grass_block_side'), 'dirt_path': ('dirt_path_top', 'dirt_path_side'),
               'podzol': ('podzol_top', 'podzol_side'), 'mycelium': ('mycelium_top', 'mycelium_side'), 'hay_block': ('hay_block_top', 'hay_block_side'),
               'barrel': ('barrel_top', 'barrel_side'), 'furnace': ('furnace_top', 'furnace_front'), 'blast_furnace': ('blast_furnace_top', 'blast_furnace_front'),
               'smoker': ('smoker_top', 'smoker_front'), 'crafting_table': ('crafting_table_top', 'crafting_table_front'),
               'bookshelf': ('oak_planks', 'bookshelf'), 'chiseled_bookshelf': ('chiseled_bookshelf_top', 'chiseled_bookshelf_occupied'),
               'smithing_table': ('smithing_table_top', 'smithing_table_front'), 'cartography_table': ('cartography_table_top', 'cartography_table_side1'),
               'fletching_table': ('fletching_table_top', 'fletching_table_front'), 'lectern': ('lectern_top', 'lectern_front'),
               'pumpkin': ('pumpkin_top', 'pumpkin_side'), 'carved_pumpkin': ('pumpkin_top', 'carved_pumpkin'), 'melon': ('melon_top', 'melon_side'),
               'chest': ('oak_planks', 'oak_planks'), 'trapped_chest': ('oak_planks', 'oak_planks'), 'ender_chest': ('obsidian', 'obsidian'),
               'sandstone': ('sandstone_top', 'sandstone'), 'red_sandstone': ('red_sandstone_top', 'red_sandstone'),
               'chiseled_sandstone': ('sandstone_top', 'chiseled_sandstone'), 'cut_sandstone': ('sandstone_top', 'cut_sandstone'),
               'smooth_sandstone': ('sandstone_top', 'sandstone_top'), 'quartz_block': ('quartz_block_top', 'quartz_block_side'),
               'smooth_quartz': ('quartz_block_bottom', 'quartz_block_bottom'), 'composter': ('composter_top', 'composter_side'),
               'cauldron': ('cauldron_top', 'cauldron_side'), 'anvil': ('anvil_top', 'anvil'), 'grindstone': ('grindstone_round', 'grindstone_side'),
               'campfire': ('campfire_log_lit', 'campfire_log_lit'), 'soul_campfire': ('campfire_log_lit', 'campfire_log_lit'),
               'water': ('water_still', 'water_still'), 'lava': ('lava_still', 'lava_still'), 'snow': ('snow', 'snow'),
               'decorated_pot': ('terracotta', 'terracotta'), 'flower_pot': ('flower_pot', 'flower_pot'), 'bell': ('gold_block', 'gold_block'),
               'lantern': ('lantern', 'lantern'), 'soul_lantern': ('soul_lantern', 'soul_lantern'), 'chain': ('iron_chain', 'iron_chain'),
               'iron_bars': ('iron_bars', 'iron_bars'), 'ladder': ('ladder', 'ladder'), 'torch': ('torch', 'torch'),
               'tall_grass': ('tall_grass_top', 'tall_grass_bottom'), 'large_fern': ('large_fern_top', 'large_fern_bottom'),
               'basalt': ('basalt_top', 'basalt_side'), 'polished_basalt': ('polished_basalt_top', 'polished_basalt_side'),
               'bone_block': ('bone_block_top', 'bone_block_side'), 'deepslate': ('deepslate_top', 'deepslate'),
               'mud_bricks': ('mud_bricks', 'mud_bricks'), 'target': ('target_top', 'target_side'), 'tnt': ('tnt_top', 'tnt_side'),
               'loom': ('loom_top', 'loom_front'), 'beehive': ('beehive_end', 'beehive_side'), 'jukebox': ('jukebox_top', 'jukebox_side'),
               'lightning_rod': ('lightning_rod', 'lightning_rod'), 'end_rod': ('end_rod', 'end_rod'), 'stonecutter': ('stonecutter_top', 'stonecutter_side'),
               'brewing_stand': ('brewing_stand', 'brewing_stand'), 'scaffolding': ('scaffolding_top', 'scaffolding_side'),
               'dried_kelp_block': ('dried_kelp_top', 'dried_kelp_side'), 'respawn_anchor': ('respawn_anchor_top_off', 'respawn_anchor_side0'),
               'lodestone': ('lodestone_top', 'lodestone_side'), 'piston': ('piston_top', 'piston_side'), 'observer': ('observer_top', 'observer_side'),
               'note_block': ('note_block', 'note_block'), 'cactus': ('cactus_top', 'cactus_side'), 'sea_lantern': ('sea_lantern', 'sea_lantern')}
    if name in special:
        return special[name]
    if name.endswith('_bed'):
        return name.replace('_bed', '_wool'), name.replace('_bed', '_wool')
    if name.endswith('_carpet'):
        t = name.replace('_carpet', '_wool') if name != 'moss_carpet' else 'moss_block'
        return t, t
    if name.endswith('_wall_banner') or name.endswith('_banner'):
        t = name.replace('_wall_banner', '_wool').replace('_banner', '_wool')
        return t, t
    if name.startswith('potted_'):
        return 'flower_pot', 'flower_pot'
    if name.endswith('_candle'):
        return name, name
    for cand in (name, name + '_top'):
        if tex(cand) is not None:
            side = name + '_side' if tex(name + '_side') is not None else name
            return (name + '_top' if tex(name + '_top') is not None else name), side
    return name, name


PLANTS = ('short_grass', 'fern', 'dandelion', 'poppy', 'blue_orchid', 'allium', 'azure_bluet', 'red_tulip', 'orange_tulip', 'white_tulip',
          'pink_tulip', 'oxeye_daisy', 'cornflower', 'lily_of_the_valley', 'sweet_berry_bush', 'dead_bush', 'short_dry_grass', 'torch',
          'brown_mushroom', 'red_mushroom', 'wheat', 'carrots', 'potatoes', 'beetroots', 'sugar_cane', 'bamboo', 'firefly_bush', 'bush')


def boxes(name, props):
    """The block's shape as boxes in its cell (fractions 0..1)."""
    if name in ('air', 'cave_air', 'void_air', 'light', 'barrier', 'structure_void') or name.endswith('sign'):
        return []
    if name.endswith('_slab'):
        t = props.get('type', 'bottom')
        return [(0, 0, 0, 1, 1, 1)] if t == 'double' else [(0, 0.5, 0, 1, 1, 1)] if t == 'top' else [(0, 0, 0, 1, 0.5, 1)]
    if name.endswith('_stairs'):
        top = props.get('half', 'bottom') == 'top'
        f = props.get('facing', 'north')
        base = (0, 0.5, 0, 1, 1, 1) if top else (0, 0, 0, 1, 0.5, 1)
        y0, y1 = (0, 0.5) if top else (0.5, 1)
        step = {'north': (0, y0, 0, 1, y1, 0.5), 'south': (0, y0, 0.5, 1, y1, 1), 'east': (0.5, y0, 0, 1, y1, 1), 'west': (0, y0, 0, 0.5, y1, 1)}[f]
        return [base, step]
    if name.endswith('_trapdoor'):
        if props.get('open') == 'true':
            f = props.get('facing', 'north')
            return [{'north': (0, 0, 0.81, 1, 1, 1), 'south': (0, 0, 0, 1, 1, 0.19), 'east': (0, 0, 0, 0.19, 1, 1), 'west': (0.81, 0, 0, 1, 1, 1)}[f]]
        return [(0, 0.81, 0, 1, 1, 1)] if props.get('half') == 'top' else [(0, 0, 0, 1, 0.19, 1)]
    if name.endswith('_door'):
        f = props.get('facing', 'north')
        return [{'north': (0, 0, 0.81, 1, 1, 1), 'south': (0, 0, 0, 1, 1, 0.19), 'east': (0, 0, 0, 0.19, 1, 1), 'west': (0.81, 0, 0, 1, 1, 1)}[f]]
    if name.endswith('_fence') or name.endswith('_pane') or name == 'iron_bars' or (name.endswith('_wall') and 'banner' not in name):
        w = 0.25 if name.endswith('_wall') else 0.125 if name.endswith('_fence') else 0.06
        h = 1.0 if not name.endswith('_wall') else 0.81
        out = [(0.5 - w, 0, 0.5 - w, 0.5 + w, 1 if name.endswith('_wall') and props.get('up') == 'true' else h, 0.5 + w)]
        arm = w * 0.8 if not name.endswith('_pane') and name != 'iron_bars' else w
        for f, box in (('north', (0.5 - arm, 0, 0, 0.5 + arm, h, 0.5)), ('south', (0.5 - arm, 0, 0.5, 0.5 + arm, h, 1)),
                       ('east', (0.5, 0, 0.5 - arm, 1, h, 0.5 + arm)), ('west', (0, 0, 0.5 - arm, 0.5, h, 0.5 + arm))):
            if props.get(f) in ('true', 'low', 'tall'):
                if name.endswith('_fence'):
                    out.append((box[0], 0.4, box[2], box[3], 0.55, box[5]))
                    out.append((box[0], 0.75, box[2], box[3], 0.9, box[5]))
                else:
                    out.append(box)
        return out
    if name.endswith('_carpet') or name == 'snow':
        return [(0, 0, 0, 1, 0.0625 if name.endswith('_carpet') else 0.125 * int(props.get('layers', 1)), 1)]
    if name.endswith('lantern'):
        return [(0.31, 0.0 if props.get('hanging') != 'true' else 0.12, 0.31, 0.69, 0.5 if props.get('hanging') != 'true' else 0.62, 0.69)]
    if name == 'chain' or name.endswith('_chain'):
        return [(0.44, 0, 0.44, 0.56, 1, 0.56)]
    if name in ('campfire', 'soul_campfire'):
        return [(0, 0, 0, 1, 0.44, 1)]
    if name == 'ladder' or name.endswith('_wall_banner'):
        f = props.get('facing', 'north')
        return [{'north': (0, 0, 0.88, 1, 1, 1), 'south': (0, 0, 0, 1, 1, 0.12), 'east': (0, 0, 0, 0.12, 1, 1), 'west': (0.88, 0, 0, 1, 1, 1)}[f]]
    if name.endswith('_bed'):
        return [(0, 0, 0, 1, 0.56, 1)]
    if name.startswith('potted_') or name == 'flower_pot' or name == 'decorated_pot':
        return [(0.31, 0, 0.31, 0.69, 0.38 if name != 'decorated_pot' else 1.0, 0.69)]
    if name in ('lectern', 'grindstone', 'anvil', 'brewing_stand', 'cauldron', 'composter', 'chest', 'trapped_chest', 'ender_chest', 'bell'):
        return [(0.06, 0, 0.06, 0.94, 0.88, 0.94)]
    if name.endswith('_candle') or name == 'candle':
        return [(0.4, 0, 0.4, 0.6, 0.4, 0.6)]
    if name in PLANTS or name.endswith('_sapling') or name.endswith('_tulip') or name in ('tall_grass', 'large_fern', 'lilac', 'rose_bush', 'peony', 'sunflower'):
        return [('plant',)]
    if name in ('lightning_rod', 'end_rod'):
        return [(0.44, 0, 0.44, 0.56, 1, 0.56)]
    if name.endswith('_pressure_plate'):
        return [(0.06, 0, 0.06, 0.94, 0.06, 0.94)]
    return [(0, 0, 0, 1, 1, 1)]


def _face_sprite(texname, w_px, h_px, shade, kind, u_extent, v_extent, s):
    """A face of a box as a sprite: the texture (cropped to the part the box shows) warped onto its
    parallelogram. kind 0 = top, 1 = east (+x), 2 = south (+z)."""
    key = (texname, kind, u_extent, v_extent, s, shade)
    if key in _SPRITE:
        return _SPRITE[key]
    t = tex(texname)
    if t is None:
        t = np.ones((16, 16, 4), np.float32) * np.array([0.8, 0.2, 0.8, 1.0], np.float32)
    n = t.shape[0]
    u0, u1 = u_extent
    v0, v1 = v_extent
    # Screen edge vectors for one block: +x -> (s, s/2), +z -> (-s, s/2), +y -> (0, -s).
    if kind == 0:
        U = np.array([s, s / 2]) * (u1 - u0)        # along x
        V = np.array([-s, s / 2]) * (v1 - v0)       # along z
    elif kind == 1:
        U = np.array([-s, s / 2]) * (u1 - u0)       # along z (east face, seen from +x)
        V = np.array([0, s]) * (v1 - v0)            # downward
    else:
        U = np.array([s, s / 2]) * (u1 - u0)        # along x
        V = np.array([0, s]) * (v1 - v0)
    corners = np.array([[0, 0], U, V, U + V])
    mn = np.floor(corners.min(axis=0)).astype(int)
    mx = np.ceil(corners.max(axis=0)).astype(int)
    W, H = int(mx[0] - mn[0]) + 1, int(mx[1] - mn[1]) + 1
    ys, xs = np.mgrid[0:H, 0:W]
    px = xs + mn[0] + 0.5
    py = ys + mn[1] + 0.5
    M = np.array([[U[0], V[0]], [U[1], V[1]]])
    inv = np.linalg.inv(M) if abs(np.linalg.det(M)) > 1e-6 else np.zeros((2, 2))
    uu = inv[0, 0] * px + inv[0, 1] * py
    vv = inv[1, 0] * px + inv[1, 1] * py
    inside = (uu >= 0) & (uu < 1) & (vv >= 0) & (vv < 1)
    tu = np.clip(((u0 + uu * (u1 - u0)) * n).astype(int), 0, n - 1)
    tv = np.clip(((v0 + vv * (v1 - v0)) * n).astype(int), 0, n - 1)
    if kind == 1:
        tu = n - 1 - tu
    sprite = t[tv, tu].copy()
    sprite[:, :, :3] *= shade
    sprite[:, :, 3] *= inside
    out = (sprite, int(mn[0]), int(mn[1]))
    _SPRITE[key] = out
    return out


def render(grid, path, scale=10, yaw=0, crop=None, background=(18, 20, 26)):
    a = grid.a
    if crop:
        x0, z0, x1, z1 = crop
        a = a[x0:x1 + 1, :, z0:z1 + 1]
    if yaw:
        a = np.rot90(a, k=yaw, axes=(0, 2))
    sx, sy, sz = a.shape
    s = scale
    W = int((sx + sz) * s + 4 * s)
    H = int((sx + sz) * s / 2 + sy * s + 4 * s)
    ox, oy = sz * s + 2 * s, sy * s + 2 * s
    canvas = np.zeros((H, W, 4), np.float32)
    canvas[:, :, :3] = np.array(background) / 255.0
    canvas[:, :, 3] = 1
    parsed = [None] + [parse(p) for p in grid.palette[1:]]
    # Rotate facing props with the view (quarter turns).
    rot = {0: {}, 1: {'north': 'west', 'west': 'south', 'south': 'east', 'east': 'north'},
           2: {'north': 'south', 'south': 'north', 'east': 'west', 'west': 'east'}, 3: {'north': 'east', 'east': 'south', 'south': 'west', 'west': 'north'}}[yaw % 4]
    xs, ys, zs = np.nonzero(a)
    order = np.argsort(xs + zs + ys * 1.0001, kind='stable')
    for i in order:
        x, y, z = int(xs[i]), int(ys[i]), int(zs[i])
        name, props = parsed[a[x, y, z]]
        if rot and 'facing' in props:
            props = dict(props, facing=rot.get(props['facing'], props['facing']))
        if rot and props.get('axis') in ('x', 'z') and yaw % 2:
            props = dict(props, axis='z' if props['axis'] == 'x' else 'x')
        bx = boxes(name, props)
        if not bx:
            continue
        top_t, side_t = faces(name, props)
        if bx[0] == ('plant',):
            # A plant: two crossed sprites, drawn as one front face.
            spr, mx_, my_ = _face_sprite(side_t, s, s, 1.0, 2, (0, 1), (0, 1), s)
            _blit(canvas, spr, ox + (x - z) * s + mx_ - s / 2, oy + (x + z) * s / 2 - (y + 1) * s + s / 2 + my_ + s / 2)
            continue
        # Hide faces covered by a full neighbour.
        full_e = x + 1 < sx and _full(parsed[a[x + 1, y, z]])
        full_s = z + 1 < sz and _full(parsed[a[x, y, z + 1]])
        full_u = y + 1 < sy and _full(parsed[a[x, y + 1, z]])
        for (bx0, by0, bz0, bx1, by1, bz1) in sorted(bx, key=lambda b: (b[0] + b[2] + b[1])):
            X0, Y1, Z0 = x + bx0, y + by1, z + bz0
            px = ox + (X0 - Z0) * s
            py = oy + (X0 + Z0) * s / 2 - Y1 * s
            if not (full_u and by1 >= 1):
                spr, mx_, my_ = _face_sprite(top_t, 0, 0, 1.0, 0, (bx0, bx1), (bz0, bz1), s)
                _blit(canvas, spr, px + mx_, py + my_)
            if not (full_e and bx1 >= 1):
                spr, mx_, my_ = _face_sprite(side_t, 0, 0, 0.8, 1, (1 - bz1, 1 - bz0), (1 - by1, 1 - by0), s)
                ex, ey = ox + ((x + bx1) - (z + bz1)) * s, oy + ((x + bx1) + (z + bz1)) * s / 2 - Y1 * s
                # The east face's own origin is its far-top corner going toward -z: shift by its z extent.
                _blit(canvas, spr, ex + mx_ + (bz1 - bz0) * s, ey + my_ - (bz1 - bz0) * s / 2)
            if not (full_s and bz1 >= 1):
                spr, mx_, my_ = _face_sprite(side_t, 0, 0, 0.64, 2, (bx0, bx1), (1 - by1, 1 - by0), s)
                sx_, sy_ = ox + ((x + bx0) - (z + bz1)) * s, oy + ((x + bx0) + (z + bz1)) * s / 2 - Y1 * s
                _blit(canvas, spr, sx_ + mx_, sy_ + my_)
    img = (np.clip(canvas[:, :, :3], 0, 1) * 255).astype(np.uint8)
    rows = np.where((img != np.array(background, np.uint8)).any(axis=2).any(axis=1))[0]
    cols = np.where((img != np.array(background, np.uint8)).any(axis=2).any(axis=0))[0]
    if len(rows) and len(cols):
        img = img[max(0, rows[0] - 6):rows[-1] + 7, max(0, cols[0] - 6):cols[-1] + 7]
    Image.fromarray(img).save(path)


def _full(p):
    if p is None:
        return False
    name, props = p
    b = boxes(name, props)
    return len(b) == 1 and b[0] == (0, 0, 0, 1, 1, 1) and not name.endswith('glass') and 'leaves' not in name


def _blit(canvas, spr, x, y):
    x, y = int(round(x)), int(round(y))
    h, w = spr.shape[:2]
    H, W = canvas.shape[:2]
    if x >= W or y >= H or x + w <= 0 or y + h <= 0:
        return
    sx0, sy0 = max(0, -x), max(0, -y)
    sx1, sy1 = min(w, W - x), min(h, H - y)
    region = canvas[y + sy0:y + sy1, x + sx0:x + sx1]
    sp = spr[sy0:sy1, sx0:sx1]
    alpha = sp[:, :, 3:4]
    region[:, :, :3] = region[:, :, :3] * (1 - alpha) + sp[:, :, :3] * alpha
