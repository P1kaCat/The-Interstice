"""
Assembles the creature validation captures taken by the dev AutoTest in "model" mode
(run/screenshots/interstice_*.png) into documentation proofs:

- interstice_seq_<name>_NNN.png -> animated GIF at real game speed (one GIF frame per captured frame;
  step through them in any GIF viewer to inspect contacts). Family sequences are named
  <species>_<clip> and go to docs/creature/family/; the older Adherent-only sequences (walk, run, ...)
  go to docs/creature/.
- still views -> downscaled PNGs (family and species views in docs/creature/family/, benchmark views in
  docs/performance/, the older v1..v5 Adherent views in docs/creature/).
- one contact sheet per species (front, profile, three-quarter, rear, wall) in docs/creature/family/.

Frames are cropped to the centre (the camera tracks the subject), downscaled and quantised to a small
palette to keep the repository light.

Usage: python -I tools/creature/make_gifs.py
"""
import glob
import os
import re

from PIL import Image, ImageDraw

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
SRC = os.path.join(ROOT, "run", "screenshots")
OUT = os.path.join(ROOT, "docs", "creature")
FAMILY = os.path.join(OUT, "family")
PERF = os.path.join(ROOT, "docs", "performance")
SPECIES = ["adherent", "crawler", "strider", "burden", "clinger", "hollow", "watcher", "fractured"]
# game ticks between captured frames (one tick = 50 ms), by clip
INTERVAL = {"walk": 2, "run": 1, "rear": 2, "fall_recover": 4, "climb": 2, "turn": 3}
LEGACY_INTERVAL = {"walk": 2, "run": 1, "rear": 2, "fall_recover": 2, "climb": 2}
WIDTH, FAMILY_WIDTH = 480, 300
COLORS, FAMILY_COLORS = 64, 32


def shrink(img, width, crop=False):
    img = img.convert("RGB")
    if crop:
        w, h = img.size
        box = (0.12, 0.05, 0.88, 0.95) if crop is True else crop
        img = img.crop((int(w * box[0]), int(h * box[1]), int(w * box[2]), int(h * box[3])))
    w, h = img.size
    return img.resize((width, int(h * width / w)), Image.LANCZOS)


def gif(files, path, ms, width, colors, crop):
    frames = [shrink(Image.open(f), width, crop) for f in files]
    palette = frames[len(frames) // 2].quantize(colors=colors, method=Image.MEDIANCUT)
    frames = [f.quantize(palette=palette, dither=Image.Dither.NONE) for f in frames]
    frames[0].save(path, save_all=True, append_images=frames[1:], duration=ms, loop=0, optimize=True)
    print(f"{os.path.relpath(path, ROOT)}: {len(frames)} frames, {ms} ms/frame, {os.path.getsize(path) // 1024} KB")


def main():
    for d in (OUT, FAMILY, PERF):
        os.makedirs(d, exist_ok=True)
    seqs = {}
    for f in sorted(glob.glob(os.path.join(SRC, "interstice_seq_*.png"))):
        m = re.match(r"interstice_seq_(.+)_(\d{3})\.png", os.path.basename(f))
        if m:
            seqs.setdefault(m.group(1), []).append(f)
    for name, files in seqs.items():
        sp = name.split("_")[0]
        if sp in SPECIES:
            clip = name[len(sp) + 1:]
            # wall clips: the creature is small in the middle of a noisy concrete wall, crop tight
            # wall clips: the creature is small on a noisy concrete wall lit by flickering motes; crop tight,
            # keep every other frame (the GIF frame time doubles, real speed is unchanged)
            if clip == "climb":
                gif(files[::2], os.path.join(FAMILY, f"{name}.gif"), INTERVAL[clip] * 100, 240, 24, (0.3, 0.1, 0.75, 0.7))
            else:
                gif(files, os.path.join(FAMILY, f"{name}.gif"), INTERVAL.get(clip, 2) * 50, FAMILY_WIDTH, FAMILY_COLORS, True)
        else:
            gif(files, os.path.join(OUT, f"{name}.gif"), LEGACY_INTERVAL.get(name, 2) * 50, WIDTH, COLORS, False)
    for f in sorted(glob.glob(os.path.join(SRC, "interstice_*.png"))):
        base = os.path.basename(f).replace("interstice_", "")
        if base.startswith("seq_"):
            continue
        if base.split("_")[0] in SPECIES:
            continue   # per-species views go into the species contact sheet below
        if base.startswith("bench_"):
            dest = os.path.join(PERF, base)
        elif re.match(r"v\d_", base):
            dest = os.path.join(OUT, base)
        else:
            dest = os.path.join(FAMILY, base)
        shrink(Image.open(f), WIDTH if not base.startswith("family_") else 854).save(dest)
        print("still", os.path.relpath(dest, ROOT))
    views = ["front", "profile", "three_quarter", "rear", "wall"]
    for sp in SPECIES:
        paths = [os.path.join(SRC, f"interstice_{sp}_{v}.png") for v in views]
        if not all(os.path.exists(p) for p in paths):
            continue
        tiles = [shrink(Image.open(p), 320) for p in paths]
        h = max(t.height for t in tiles)
        sheet = Image.new("RGB", (320 * len(tiles), h + 18), (20, 20, 20))
        d = ImageDraw.Draw(sheet)
        for i, (t, v) in enumerate(zip(tiles, views)):
            sheet.paste(t, (i * 320, 18))
            d.text((i * 320 + 6, 3), f"{sp} - {v}", fill=(230, 230, 230))
        dest = os.path.join(FAMILY, f"{sp}_sheet.png")
        sheet.save(dest)
        print("sheet", os.path.relpath(dest, ROOT))


if __name__ == "__main__":
    main()
