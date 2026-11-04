"""
Assembles the creature validation captures (run/screenshots/interstice_seq_<name>_NNN.png, taken by the
dev AutoTest in "model" mode) into animated GIFs in docs/creature/ at real game speed (one GIF frame per
captured frame; step through them in any GIF viewer to inspect contacts). Frames are downscaled and
quantised to a small palette to keep the repository light. Still views (interstice_v*.png) are copied
as downscaled PNGs.

Usage: python -I tools/creature/make_gifs.py
"""
import glob
import os
import re

from PIL import Image

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
SRC = os.path.join(ROOT, "run", "screenshots")
OUT = os.path.join(ROOT, "docs", "creature")
# game ticks between captured frames (one tick = 50 ms)
INTERVAL = {"walk": 2, "run": 1, "rear": 2, "fall_recover": 2, "climb": 2}
WIDTH = 480
COLORS = 64


def shrink(img):
    w, h = img.size
    return img.convert("RGB").resize((WIDTH, int(h * WIDTH / w)), Image.LANCZOS)


def main():
    os.makedirs(OUT, exist_ok=True)
    seqs = {}
    for f in sorted(glob.glob(os.path.join(SRC, "interstice_seq_*.png"))):
        m = re.match(r"interstice_seq_(.+)_(\d{3})\.png", os.path.basename(f))
        if m:
            seqs.setdefault(m.group(1), []).append(f)
    for name, files in seqs.items():
        frames = [shrink(Image.open(f)) for f in files]
        palette = frames[len(frames) // 2].quantize(colors=COLORS, method=Image.MEDIANCUT)
        frames = [f.quantize(palette=palette, dither=Image.Dither.NONE) for f in frames]
        ms = INTERVAL.get(name, 2) * 50
        path = os.path.join(OUT, f"{name}.gif")
        frames[0].save(path, save_all=True, append_images=frames[1:], duration=ms, loop=0, optimize=True)
        print(f"{path}: {len(frames)} frames, {ms} ms/frame, {os.path.getsize(path) // 1024} KB")
    for f in sorted(glob.glob(os.path.join(SRC, "interstice_v*.png"))):
        name = os.path.basename(f).replace("interstice_", "")
        shrink(Image.open(f)).save(os.path.join(OUT, name))
        print("still", name)


if __name__ == "__main__":
    main()
