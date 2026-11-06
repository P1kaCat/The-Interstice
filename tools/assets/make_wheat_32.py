"""Level 02, The Wheat Farm: the 32x32 pass (2026-11-06), from PixelLab (tools/assets/pixellab_jobs/wheat_farm_32.txt).

  - textures/block/wheat_field.png, wheat_field_1.png: tilled farmland, four furrows per block running along the track
    (z), one furrow pattern made seamless, the two variants differing only by their clods and stubble. Blockstate:
    the two, never rotated, so the rows run on unbroken (connected) from block to block;
  - textures/block/farm_path.png, farm_path_1.png: the packed earth track, two variants, any rotation;
  - textures/level/wheat_stalks.png: two ripe clumps (32x64 each) gradient-mapped onto one straw ramp (no green).
The track spills a ragged edge onto the farmland beside it (ConnectedTextures, overlay set "path",
tools/assets/make_connected.py).

    python -I tools/assets/make_wheat_32.py
"""
import json
import os

import numpy as np
from PIL import Image, PngImagePlugin

ROOT = os.path.join(os.path.dirname(__file__), "..", "..")
RES = os.path.join(ROOT, "src", "main", "resources", "assets", "interstice")
RAW = os.path.join(os.path.dirname(__file__), "pixellab_raw", "wheat_farm_32")
GOLD = np.array([[0.00, 62, 40, 18], [0.30, 150, 96, 38], [0.55, 205, 150, 66], [0.78, 236, 196, 112], [1.00, 252, 232, 170]], float)


def load(n):
    return np.asarray(Image.open(os.path.join(RAW, n + ".png")).convert("RGBA")).astype(float)


def save(rel, a, note):
    p = os.path.join(RES, "textures", rel + ".png")
    info = PngImagePlugin.PngInfo()
    info.add_text("interstice", note)
    Image.fromarray(np.clip(np.round(a), 0, 255).astype(np.uint8), "RGBA").save(p, pnginfo=info)
    meta = p + ".mcmeta"
    if os.path.exists(meta):
        os.remove(meta)   # pixel art: no blur


def grade(a, ref):
    """a's colours moved onto ref's per-channel mean and spread."""
    out = a.copy()
    for c in range(3):
        out[..., c] = (a[..., c] - a[..., c].mean()) / (a[..., c].std() + 1e-6) * ref[..., c].std() + ref[..., c].mean()
    return out


def seamless(rgb, width, axes=(0, 1)):
    """Blend the edges with the half-rolled image so the tile wraps along the given axes."""
    h, w, _ = rgb.shape
    rolled = rgb
    for ax in axes:
        rolled = np.roll(rolled, (h, w)[ax] // 2, axis=ax)
    yy, xx = np.mgrid[0:h, 0:w]
    d = np.full((h, w), 1e9)
    if 0 in axes:
        d = np.minimum(d, np.minimum(yy, h - 1 - yy))
    if 1 in axes:
        d = np.minimum(d, np.minimum(xx, w - 1 - xx))
    wgt = np.clip(1 - d / width, 0, 1) ** 1.5
    return rgb * (1 - wgt[..., None]) + rolled * wgt[..., None]


def straw(a):
    a = a.copy()
    lum = (0.3 * a[..., 0] + 0.55 * a[..., 1] + 0.15 * a[..., 2]) / 255.0
    m = a[..., 3] > 0
    lo, hi = np.percentile(lum[m], 2), np.percentile(lum[m], 98)
    t = np.clip((lum - lo) / max(hi - lo, 1e-3), 0, 1)
    for c in range(3):
        a[..., c] = np.interp(t, GOLD[:, 0], GOLD[:, c + 1])
    return a


def model(name, top, side="interstice:block/grime_soil"):
    p = os.path.join(RES, "models", "block", name + ".json")
    with open(p, "w", encoding="utf-8", newline="\n") as f:
        json.dump({"parent": "minecraft:block/cube_bottom_top", "textures": {"top": top, "side": side, "bottom": side}}, f, indent=2)
        f.write("\n")


def blockstate(name, variants):
    p = os.path.join(RES, "blockstates", name + ".json")
    with open(p, "w", encoding="utf-8", newline="\n") as f:
        json.dump({"variants": {"": variants}}, f, indent=2)
        f.write("\n")


def clods(base, seed):
    """The same furrows with their own scatter of clods, pebbles and straw stubble (so the rows run on unbroken from
    block to block, while no two blocks look alike)."""
    a = base.copy()
    r = np.random.default_rng(seed)
    lum = a[..., :3].mean(-1)
    ridge = lum > np.percentile(lum, 55)
    for _ in range(26):
        y, x = r.integers(0, 32), r.integers(0, 32)
        if not ridge[y, x]:
            continue
        k = r.random()
        if k < 0.45:      # a lit clod with its shadow below
            a[y, x, :3] *= 1.22
            a[(y + 1) % 32, x, :3] *= 0.7
        elif k < 0.75:    # a pebble
            a[y, x, :3] = a[y, x, :3] * 0.4 + np.array([150.0, 140, 128]) * 0.6
        else:             # stubble: a short pale straw stroke along the row
            for d in range(r.integers(2, 4)):
                a[(y + d) % 32, x, :3] = a[(y + d) % 32, x, :3] * 0.35 + np.array([196.0, 162, 100]) * 0.65
    return a


def farmland():
    a0 = load("farmland_0")
    # a touch darker and less saturated than PixelLab's: damp soil under the evening light
    a0[..., :3] = a0[..., :3] * 0.86
    g = a0[..., :3].mean(-1, keepdims=True)
    a0[..., :3] = g + (a0[..., :3] - g) * 0.85
    a0[..., :3] = seamless(a0[..., :3], 6, axes=(0,))   # along the furrows; across them the tile already wraps
    a0[..., 3] = 255
    for i, seed in enumerate((61, 62)):
        save("block/wheat_field" + ("" if i == 0 else "_1"), clods(a0, seed),
             "PixelLab tilled farmland, graded, seamless, clods; make_wheat_32.py")
    model("wheat_field", "interstice:block/wheat_field")
    model("wheat_field_1", "interstice:block/wheat_field_1")
    # never rotated: the furrows must stay in line from block to block
    blockstate("wheat_field", [{"model": "interstice:block/wheat_field"}, {"model": "interstice:block/wheat_field_1"}])


def path():
    p2, p3 = load("farm_path_2"), load("farm_path_3")
    p3 = grade(p3, p2)
    for i, a in enumerate((p2, p3)):
        a[..., :3] = seamless(a[..., :3], 5)
        a[..., 3] = 255
        save("block/farm_path" + ("" if i == 0 else "_1"), a, "PixelLab packed earth track, seamless; make_wheat_32.py")
    model("farm_path", "interstice:block/farm_path")
    model("farm_path_1", "interstice:block/farm_path_1")
    blockstate("farm_path", [{"model": "interstice:block/farm_path" + s, **({"y": y} if y else {})}
                             for s in ("", "_1") for y in (0, 90, 180, 270)])


def stalks():
    s0, s1 = straw(load("wheat_ripe_0")), straw(load("wheat_ripe_1"))
    atlas = np.concatenate([s0, s1], axis=1)
    atlas[..., 3] = np.where(atlas[..., 3] > 100, 255, 0)
    save("level/wheat_stalks", atlas, "two PixelLab ripe wheat clumps (32x64), straw ramp; make_wheat_32.py")


if __name__ == "__main__":
    farmland()
    path()
    stalks()
    print("wheat 32 ok")
