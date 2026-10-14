"""Builds the Afterworld modpack as a Modrinth .mrpack.

    python3 tools/build_pack.py            (after ./gradlew build)

The pack lists the other mods, the shader pack and the resource pack by their Modrinth download
links (pack/files.json); launchers fetch and hash-check them on install. The Afterworld jar and
the pack's settings travel inside the file as overrides (pack/overrides).

Files are client "required" unless files.json says otherwise: SKLauncher installs "optional" files
switched off (Distant Horizons ships that way, for speed) and skips "unsupported" ones. In
singleplayer the client also runs the world generator.
"""
import json
import pathlib
import re
import zipfile

ROOT = pathlib.Path(__file__).resolve().parent.parent
PACK = ROOT / "pack"


def prop(name):
    text = (ROOT / "gradle.properties").read_text()
    return re.search(rf"^{name}\s*=\s*(.+)$", text, re.M).group(1).strip()


def main():
    version = prop("mod_version")
    files = json.loads((PACK / "files.json").read_text())
    index = {
        "formatVersion": 1,
        "game": "minecraft",
        "versionId": version,
        "name": "Afterworld",
        "summary": "The world that plays back. Afterworld with shaders, distant horizons, ambience and performance mods, tuned to work together.",
        "files": [
            {
                "path": f["path"],
                "hashes": {"sha1": f["sha1"], "sha512": f["sha512"]},
                "env": {"client": f.get("client", "required"), "server": f["server"]},
                "downloads": [f["url"]],
                "fileSize": f["size"],
            }
            for f in files
        ],
        "dependencies": {"minecraft": prop("minecraft_version"), "fabric-loader": prop("loader_version")},
    }
    jar = ROOT / "build" / "libs" / f"afterworld-{version}.jar"
    if not jar.exists():
        raise SystemExit(f"{jar} is missing: run ./gradlew build first")
    out = ROOT / "dist" / f"Afterworld-Modpack-{version}.mrpack"
    out.parent.mkdir(exist_ok=True)
    with zipfile.ZipFile(out, "w", zipfile.ZIP_DEFLATED) as z:
        z.writestr("modrinth.index.json", json.dumps(index, indent=1))
        z.write(jar, f"overrides/mods/{jar.name}")
        z.write(ROOT / "src/main/resources/assets/afterworld/icon.png", "overrides/icon.png")
        for f in sorted((PACK / "overrides").rglob("*")):
            if f.is_file():
                z.write(f, "overrides/" + f.relative_to(PACK / "overrides").as_posix())
    total = sum(f["size"] for f in files) + jar.stat().st_size
    print(f"{out.relative_to(ROOT)}: {len(files)} downloads + Afterworld, {total / 1e6:.0f} MB once installed")


if __name__ == "__main__":
    main()
