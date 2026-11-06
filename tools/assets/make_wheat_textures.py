"""Level 02, The Wheat Farm: textures (2026-11-06).

  - textures/level/wheat_stalks.png: the two PixelLab stalk sprites side by side (64 x 64) for the drawn wheat
    (WheatRenderer); green stems turned to straw, the ground line under sprite 0 removed;
  - textures/level/wheat_canopy.png: the wheat seen from above and afar (the far field plane);
  - textures/block/wheat_field.png: the tilled soil under the wheat (furrows along the track);
  - textures/block/farm_path.png: the farm track (PixelLab), stray coloured specks cleaned out, wrapped;
  - textures/level/turbine.png: smooth off-white paint with faint grime runs (procedural, 64 px, linear filtering).

    python -I tools/assets/make_wheat_textures.py
"""
import json
import os

import numpy as np
from PIL import Image, PngImagePlugin
from scipy.ndimage import gaussian_filter, median_filter

ROOT = os.path.join(os.path.dirname(__file__), "..", "..")
RAW = os.path.join(os.path.dirname(__file__), "pixellab_raw", "wheat_farm")
A = os.path.join(ROOT, "src", "main", "resources", "assets", "interstice", "textures")


def load(n):
    return np.asarray(Image.open(os.path.join(RAW, n + ".png")).convert("RGBA")).astype(float)


def save(rel, a, note, blur=False):
    p = os.path.join(A, rel + ".png")
    os.makedirs(os.path.dirname(p), exist_ok=True)
    info = PngImagePlugin.PngInfo()
    info.add_text("interstice", note + "; tools/assets/make_wheat_textures.py")
    Image.fromarray(np.clip(a, 0, 255).astype(np.uint8), "RGBA").save(p, pnginfo=info)
    if blur:
        with open(p + ".mcmeta", "w", encoding="utf-8", newline="\n") as f:
            json.dump({"texture": {"blur": True}}, f)


GOLD = np.array([[0.00, 62, 40, 18], [0.30, 150, 96, 38], [0.55, 205, 150, 66], [0.78, 236, 196, 112], [1.00, 252, 232, 170]], float)


def straw(a):
    """Gradient map: every pixel's luminance onto one golden ramp (stems, ears and awns all ripe straw)."""
    a = a.copy()
    lum = (0.3 * a[..., 0] + 0.55 * a[..., 1] + 0.15 * a[..., 2]) / 255.0
    lo, hi = lum[a[..., 3] > 0].min(), lum[a[..., 3] > 0].max()
    t = np.clip((lum - lo) / max(hi - lo, 1e-3), 0, 1)
    for c in range(3):
        a[..., c] = np.interp(t, GOLD[:, 0], GOLD[:, c + 1])
    return a


def stalks_unused():
    s0 = straw(load("wheat_stalks_0"))
    s0[59:, :, 3] = 0          # the strip of ground under the clump
    s1 = straw(load("wheat_stalks_1"))
    atlas = np.concatenate([s0, s1], axis=1)
    atlas[..., 3] = np.where(atlas[..., 3] > 100, 255, 0)
    save("level/wheat_stalks", atlas, "two PixelLab wheat clumps (32x64), stems turned to straw")


def noise(n, sigma, seed):
    x = gaussian_filter(np.random.default_rng(seed).standard_normal((n, n)), sigma, mode="wrap")
    return (x - x.mean()) / (x.std() + 1e-9)


def field():
    """The wheat seen from above and afar: a smooth golden canopy in the stalks' own colours (procedural, 128 px)."""
    n = 128
    t = 0.62 + noise(n, 14, 31) * 0.07 + noise(n, 3, 32) * 0.05
    # the ears: short, slightly diagonal bright strokes, with a shadow under each
    r = np.random.default_rng(33)
    marks = np.zeros((n, n))
    for _ in range(900):
        x, y = r.integers(0, n), r.integers(0, n)
        for k in range(r.integers(3, 6)):
            marks[(y + k) % n, (x + k // 2) % n] += 1.0
            marks[(y + k + 1) % n, (x + k // 2 + 1) % n] -= 0.55
    t += gaussian_filter(marks, 0.6, mode="wrap") * 0.09
    t = np.clip(t, 0, 1)
    rgb = np.stack([np.interp(t, GOLD[:, 0], GOLD[:, c + 1]) for c in range(3)], -1)
    save("level/wheat_canopy", np.dstack([rgb, np.full((n, n), 255.0)]), "procedural wheat canopy 128px", blur=True)


def soil():
    """Tilled farmland under the wheat: four furrows per block running along the track (z), damp dark troughs, drier
    crumbly ridges, clods and a little straw stubble (procedural, 128 px, smooth)."""
    n = 128
    u = np.arange(n) / n
    # ridge profile across x: rounded ridges, narrow troughs, slightly wobbling along z
    wob = noise(n, 9, 51) * 0.012
    phase = (u[None, :] + wob) * 4.0
    ridge = 0.5 - 0.5 * np.cos(2 * np.pi * phase)
    ridge = ridge ** 0.7
    clods = noise(n, 1.2, 52) * 0.10 + noise(n, 4, 53) * 0.07
    lum = 0.42 + 0.30 * ridge + clods * (0.6 + 0.4 * ridge)
    damp = np.clip(1 - ridge * 1.6, 0, 1)
    dry = np.array([132.0, 98, 66])
    wet = np.array([70.0, 48, 32])
    col = dry[None, None, :] * (1 - damp[..., None]) + wet[None, None, :] * damp[..., None]
    rgb = col * np.clip(lum, 0.15, 1.3)[..., None] / 0.62
    # stubble: short pale straw bits on the ridges
    r = np.random.default_rng(54)
    for _ in range(70):
        x, y = r.integers(0, n), r.integers(0, n)
        if ridge[y, x] < 0.55:
            continue
        for k in range(r.integers(2, 5)):
            rgb[(y + k) % n, x] = rgb[(y + k) % n, x] * 0.3 + np.array([200.0, 168, 104]) * 0.7
    save("block/wheat_field", np.dstack([rgb, np.full((n, n), 255.0)]), "procedural tilled soil 128px", blur=True)


def path():
    """Packed dry earth with fine grit (procedural, 128 px, smooth): no pattern to repeat along the track."""
    n = 128
    lum = 0.55 + noise(n, 10, 41) * 0.05 + noise(n, 2, 42) * 0.035
    r = np.random.default_rng(43)
    grit = gaussian_filter((r.random((n, n)) < 0.012).astype(float), 0.6, mode="wrap")
    lum += grit / grit.max() * 0.08 - gaussian_filter((r.random((n, n)) < 0.008).astype(float), 0.7, mode="wrap") * 0.6
    base = np.array([168.0, 134, 96])
    rgb = base[None, None, :] * np.clip(lum, 0.2, 1.2)[..., None] / 0.55
    save("block/farm_path", np.dstack([rgb, np.full((n, n), 255.0)]), "procedural packed earth 128px", blur=True)


def path_pixellab_unused():
    a = load("farm_path")
    rgb = a[..., :3]
    med = np.stack([median_filter(rgb[..., c], size=5, mode="wrap") for c in range(3)], -1)
    sat = rgb.max(-1) - rgb.min(-1)
    odd = (sat > 90) | (rgb.mean(-1) < 45)
    rgb[odd] = med[odd]
    sh = np.roll(np.roll(rgb, 16, 0), 16, 1)
    d = np.minimum(np.minimum(np.arange(32), 31 - np.arange(32)), 6) / 6
    w = np.minimum(d[:, None], d[None, :])[..., None]
    rgb = rgb * w + sh * (1 - w)
    save("block/farm_path", np.dstack([rgb, np.full((32, 32), 255.0)]), "PixelLab farm track, cleaned and wrapped")


def turbine():
    n = 64
    r = np.random.default_rng(5)
    base = 226 + gaussian_filter(r.standard_normal((n, n)), 6, mode="wrap") * 6
    runs = gaussian_filter(r.standard_normal(n), 1.5, mode="wrap")[None, :] * np.linspace(0.2, 1, n)[:, None]
    base -= np.clip(runs, 0, None) * 18
    rgb = np.dstack([base * 0.99, base, base * 1.02])
    save("level/turbine", np.dstack([rgb, np.full((n, n), 255.0)]), "procedural turbine paint", blur=True)


if __name__ == "__main__":
    # stalks() is superseded too (make_wheat_32.py)
    field()
    # soil() and path() are superseded by the 32x32 PixelLab pass (make_wheat_32.py), which writes those textures
    turbine()
    print("wheat farm textures ok")
