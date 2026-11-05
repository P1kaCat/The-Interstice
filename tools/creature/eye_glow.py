"""Emissive eye atlas grading (2026-11-05 Corridor rework).

The eye atlases from the generator are a dim, fairly dark red (median about 140/255 on the red channel), so the
eyes were nearly invisible in the Corridor's darkness. This grades every lit texel to a saturated crimson at a
much higher level while keeping the eye shapes and the soft edges exactly where they are (only colour changes,
alpha and coverage are untouched):

    level = (red / max red) ** 0.45      brightens the falloff, the core reaches 1.0
    colour = level * (255, 22, 12)        crimson, very little green/blue so it never turns orange or pink

Applied by export_adherent.py when it writes <species>_eyes.png; can be run on its own:
    python -I tools/creature/eye_glow.py [species ...]
"""
import io
import os
import sys

import numpy as np
from PIL import Image

CRIMSON = np.array([255.0, 22.0, 12.0])
GAMMA = 0.45


def grade(img: Image.Image) -> Image.Image:
    a = np.asarray(img.convert("RGBA")).astype(np.float64)
    rgb = a[..., :3]
    lum = rgb.max(axis=2)
    lit = lum > 6
    if not lit.any():
        return img.convert("RGBA")
    level = np.zeros_like(lum)
    level[lit] = (lum[lit] / lum[lit].max()) ** GAMMA
    out = a.copy()
    out[..., :3] = np.where(lit[..., None], level[..., None] * CRIMSON, rgb)
    return Image.fromarray(np.clip(out + 0.5, 0, 255).astype(np.uint8), "RGBA")


def grade_bytes(png: bytes) -> bytes:
    buf = io.BytesIO()
    grade(Image.open(io.BytesIO(png))).save(buf, "PNG")
    return buf.getvalue()


if __name__ == "__main__":
    root = os.path.join(os.path.dirname(__file__), "..", "..", "src", "client", "resources", "assets", "interstice", "textures", "entity")
    names = sys.argv[1:] or ["adherent", "crawler", "strider", "burden", "clinger", "hollow", "watcher", "fractured"]
    for n in names:
        p = os.path.join(root, f"{n}_eyes.png")
        before = np.asarray(Image.open(p).convert("RGBA"))
        g = grade(Image.open(p))
        g.save(p)
        after = np.asarray(g)
        lit = before[..., :3].max(axis=2) > 6
        print(f"{n}: {lit.sum()} lit texels, median red {np.median(before[..., 0][lit]):.0f} -> {np.median(after[..., 0][lit]):.0f}")
