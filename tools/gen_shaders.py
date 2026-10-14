#!/usr/bin/env python3
"""Afterworld Shaders: writes the program wrappers (one per Iris program, for the Overworld, the
Nether and the End), the settings screen, block IDs and option names, then zips the pack into the
modpack's shaderpacks folder. The GLSL itself lives in pack/shaderpack/shaders/{lib,program}."""
import os
import zipfile

HERE = os.path.dirname(os.path.abspath(__file__))
PACK = os.path.join(HERE, '..', 'pack', 'shaderpack')
SH = os.path.join(PACK, 'shaders')
OUT = os.path.join(HERE, '..', 'pack', 'overrides', 'shaderpacks', 'Afterworld-Shaders.zip')

# program name: (source, defines)
PROGRAMS = {
    'shadow': ('shadow', []),
    'gbuffers_terrain': ('lit', ['TERRAIN']),
    'gbuffers_block': ('lit', ['BLOCK']),
    'gbuffers_entities': ('lit', ['ENTITIES']),
    'gbuffers_hand': ('lit', ['HAND']),
    'gbuffers_textured_lit': ('lit', ['PARTICLES']),
    'gbuffers_textured': ('lit', ['PARTICLES']),
    'gbuffers_water': ('water', []),
    'gbuffers_skybasic': ('sky', []),
    'gbuffers_skytextured': ('skytex', []),
    'gbuffers_clouds': ('clouds', []),
    'gbuffers_weather': ('weather', []),
    'gbuffers_spidereyes': ('emissive', []),
    'gbuffers_beaconbeam': ('emissive', []),
    # Lightning, and Afterworld's shapes of light (their bodies and their glowing cores, told apart by alpha).
    'gbuffers_lightning': ('plain', ['GLOW']),
    'composite': ('composite', []),
    'final': ('final', []),
    'dh_terrain': ('dh', ['DH_TERRAIN']),
    'dh_water': ('dh', ['DH_WATER']),
}
WORLDS = {'': [], 'world-1': ['NETHER'], 'world1': ['END']}

BLOCKS = {
    10001: ['short_grass', 'tall_grass', 'fern', 'large_fern', 'dead_bush', 'sweet_berry_bush', 'wheat', 'carrots', 'potatoes', 'beetroots',
            'dandelion', 'poppy', 'blue_orchid', 'allium', 'azure_bluet', 'red_tulip', 'orange_tulip', 'white_tulip', 'pink_tulip', 'oxeye_daisy',
            'cornflower', 'lily_of_the_valley', 'sunflower', 'lilac', 'rose_bush', 'peony', 'torchflower', 'pitcher_plant', 'short_dry_grass',
            'tall_dry_grass', 'bush', 'firefly_bush', 'red_mushroom', 'brown_mushroom', 'crimson_roots', 'warped_roots', 'nether_sprouts'],
    10002: ['oak_leaves', 'spruce_leaves', 'birch_leaves', 'jungle_leaves', 'acacia_leaves', 'dark_oak_leaves', 'mangrove_leaves', 'cherry_leaves',
            'azalea_leaves', 'flowering_azalea_leaves', 'pale_oak_leaves', 'vine'],
    10003: ['water', 'flowing_water'],
    10004: ['sugar_cane', 'bamboo'],
    10010: ['glowstone', 'sea_lantern', 'shroomlight', 'lantern', 'soul_lantern', 'torch', 'wall_torch', 'soul_torch', 'soul_wall_torch',
            'copper_torch', 'copper_wall_torch', 'redstone_lamp:lit=true', 'lava', 'magma_block', 'jack_o_lantern', 'end_rod', 'beacon',
            'campfire:lit=true', 'soul_campfire:lit=true', 'ochre_froglight', 'verdant_froglight', 'pearlescent_froglight', 'fire', 'soul_fire',
            'glow_lichen', 'crying_obsidian', 'amethyst_cluster', 'candle:lit=true', 'white_candle:lit=true', 'red_candle:lit=true',
            'afterworld:waystone', 'afterworld:seal:solved=true', 'afterworld:phylactery', 'afterworld:anomaly_core'],
}

PROPERTIES = """# Afterworld Shaders
oldLighting=false
separateAo=true
underwaterOverlay=false
vignette=false
sun=true
moon=true
shadowTerrain=true
shadowTranslucent=false
shadowEntities=true
shadowBlockEntities=true
dynamicHandLight=true
program.world-1/shadow.enabled=false
program.world1/shadow.enabled=false
# Lightning and the shapes of light blend over the world (their cores still shine: see plain.glsl).
blend.gbuffers_lightning=SRC_ALPHA ONE_MINUS_SRC_ALPHA ONE ONE_MINUS_SRC_ALPHA
blend.world-1/gbuffers_lightning=SRC_ALPHA ONE_MINUS_SRC_ALPHA ONE ONE_MINUS_SRC_ALPHA
blend.world1/gbuffers_lightning=SRC_ALPHA ONE_MINUS_SRC_ALPHA ONE ONE_MINUS_SRC_ALPHA

screen=<profile> <empty> [LIGHT] [WORLD] [LOOK] [SURFACE]
screen.LIGHT=SHADOWS SHADOW_RES SHADOW_DIST SHADOW_SOFTNESS TORCH_WARMTH
screen.WORLD=WAVING_PLANTS WATER_WAVES FOG_DENSITY GODRAYS GODRAY_STRENGTH
screen.LOOK=BLOOM BLOOM_STRENGTH EXPOSURE SATURATION VIGNETTE
screen.SURFACE=NORMAL_MAPS NORMAL_STRENGTH SPECULAR_MAPS
sliders=SHADOW_RES SHADOW_DIST SHADOW_SOFTNESS TORCH_WARMTH FOG_DENSITY GODRAY_STRENGTH BLOOM_STRENGTH EXPOSURE SATURATION VIGNETTE NORMAL_STRENGTH

profile.LITE=!SHADOWS !GODRAYS BLOOM_STRENGTH=0.25 !NORMAL_MAPS !SPECULAR_MAPS
profile.BALANCED=SHADOWS SHADOW_RES=1024 SHADOW_DIST=64.0 GODRAYS
profile.FULL=SHADOWS SHADOW_RES=2048 SHADOW_DIST=128.0 GODRAYS
"""

LANG = {
    'profile.LITE': 'Lite', 'profile.BALANCED': 'Balanced', 'profile.FULL': 'Full',
    'screen.LIGHT': 'Light & Shadow', 'screen.WORLD': 'World', 'screen.LOOK': 'Look',
    'option.SHADOWS': 'Shadows', 'option.SHADOW_RES': 'Shadow Quality', 'option.SHADOW_DIST': 'Shadow Distance',
    'option.SHADOW_SOFTNESS': 'Shadow Softness', 'option.TORCH_WARMTH': 'Torch Warmth', 'option.WAVING_PLANTS': 'Swaying Plants',
    'option.WATER_WAVES': 'Water Waves', 'option.FOG_DENSITY': 'Fog Density', 'option.GODRAYS': 'Sun Rays',
    'option.GODRAY_STRENGTH': 'Sun Ray Strength', 'option.BLOOM': 'Bloom', 'option.BLOOM_STRENGTH': 'Bloom Strength',
    'option.EXPOSURE': 'Exposure', 'option.SATURATION': 'Colour', 'option.VIGNETTE': 'Vignette',
    'option.SHADOWS.comment': 'Soft sun and moon shadows. The biggest cost; turn off on slow machines.',
    'option.SHADOW_RES.comment': 'Sharper shadows cost more. 1536 is a good balance.',
    'option.GODRAYS.comment': 'Soft rays from the sun and moon, strongest at dawn and dusk.',
    'screen.SURFACE': 'Surfaces', 'option.NORMAL_MAPS': 'Surface Relief', 'option.NORMAL_STRENGTH': 'Relief Strength',
    'option.SPECULAR_MAPS': 'Shine & Glow',
    'option.NORMAL_MAPS.comment': "Bricks, bark and stone take their relief from the texture pack's normal maps (the modpack's Patrix 32x has them).",
    'option.SPECULAR_MAPS.comment': 'Smooth surfaces catch the sun and glowing details light up, as the texture pack marks them.',
}


def main():
    for world, wdefs in WORLDS.items():
        folder = os.path.join(SH, world) if world else SH
        os.makedirs(folder, exist_ok=True)
        for name, (src, defs) in PROGRAMS.items():
            if world and name == 'shadow':
                continue
            for stage, ext in (('VSH', 'vsh'), ('FSH', 'fsh')):
                lines = ['#version 120', f'#define {stage}'] + [f'#define {d}' for d in wdefs + defs]
                lines += ['#include "/lib/settings.glsl"', '#include "/lib/common.glsl"', f'#include "/program/{src}.glsl"', '']
                with open(os.path.join(folder, f'{name}.{ext}'), 'w') as f:
                    f.write('\n'.join(lines))
    with open(os.path.join(SH, 'shaders.properties'), 'w') as f:
        f.write(PROPERTIES)
    with open(os.path.join(SH, 'block.properties'), 'w') as f:
        for k, names in BLOCKS.items():
            f.write(f'block.{k}=' + ' '.join(names) + '\n')
    os.makedirs(os.path.join(SH, 'lang'), exist_ok=True)
    with open(os.path.join(SH, 'lang', 'en_us.lang'), 'w') as f:
        for k, v in LANG.items():
            f.write(f'{k}={v}\n')
    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    with zipfile.ZipFile(OUT, 'w', zipfile.ZIP_DEFLATED) as z:
        for root, _, files in os.walk(PACK):
            for fn in sorted(files):
                p = os.path.join(root, fn)
                z.write(p, os.path.relpath(p, PACK))
    print('wrote', OUT, os.path.getsize(OUT), 'bytes')


if __name__ == '__main__':
    main()
