#!/usr/bin/env python3
"""Finds translation keys used in code that are missing from en_us.json.

Literal keys in translatable(...) calls are checked exactly. Keys built with string concatenation
("prefix." + x) are reported as prefixes that have no entries at all."""
import json, os, re, sys

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.join(HERE, '..', 'src')
LANG = json.load(open(os.path.join(ROOT, 'main', 'resources', 'assets', 'afterworld', 'lang', 'en_us.json')))
VANILLA_OK = ('gui.', 'key.', 'multiplayer.', 'loading.progress', 'options.', 'menu.', 'selectWorld.')

literal = re.compile(r'translatable\(\s*"([^"]+)"\s*[,)]')
# Any string literal shaped like one of our keys (catches keys picked by a ternary or kept in an array).
shaped = re.compile(r'"((?:shop|screen|message|notice|gui|hud|quest|codex|dialogue|edict|board|contract|tooltip|item|block|entity)\.afterworld\.[a-z0-9_.]*[a-z0-9_])"')
prefix = re.compile(r'translatable\(\s*"([^"]+)"\s*\+')
built = re.compile(r'"((?:shop|screen|message|notice|seal|quest|codex|dialogue)\.afterworld\.[a-z0-9_.]*\.)"\s*\+')
missing, prefixes = set(), set()
for dirpath, _, files in os.walk(ROOT):
    for f in files:
        if not f.endswith('.java'):
            continue
        src = open(os.path.join(dirpath, f), encoding='utf-8').read()
        for k in set(literal.findall(src)) | set(shaped.findall(src)):
            if k not in LANG and not k.startswith(VANILLA_OK):
                missing.add((k, f))
        for k in set(prefix.findall(src)) | set(built.findall(src)):
            if not any(key.startswith(k) for key in LANG) and not k.startswith(VANILLA_OK):
                prefixes.add((k, f))
# The story's objectives and chapters are named by id in code ("quest.afterworld.obj." + id).
chapter_src = open(os.path.join(ROOT, 'main', 'java', 'dev', 'afterworld', 'quest', 'Chapter.java'), encoding='utf-8').read()
for oid in re.findall(r'new Objective\("([a-z_]+)"', chapter_src):
    if 'quest.afterworld.obj.' + oid not in LANG:
        missing.add(('quest.afterworld.obj.' + oid, 'Chapter.java'))
for cid in re.findall(r'^\t[A-Z]+\("([a-z]+)"', chapter_src, re.M):
    if 'quest.afterworld.chapter.' + cid not in LANG:
        missing.add(('quest.afterworld.chapter.' + cid, 'Chapter.java'))
for k, f in sorted(missing):
    print(f'missing key   {k}   ({f})')
for k, f in sorted(prefixes):
    print(f'empty prefix  {k}*   ({f})')
print(f'{len(missing)} missing keys, {len(prefixes)} empty prefixes, {len(LANG)} entries')
sys.exit(1 if missing or prefixes else 0)
