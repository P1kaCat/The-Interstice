"""Level 00, The Threshold: smooth, high-resolution concrete (2026-11-06).

The Threshold must not look blocky: these are 256 x 256 procedural textures (each spans 4 blocks of the drawn
corridor, ~64 px per block) built from wrapped Gaussian noise, so they tile with no seam, and drawn with linear
filtering ("blur" in their .mcmeta) so no pixel shows. Cold, nearly colourless, restrained.
  - threshold_wall: smooth poured concrete, soft cloudy mottling, faint pores, a faint formwork seam;
  - threshold_floor: darker, denser concrete, worn to a dull sheen along the middle;
  - threshold_light: the ceiling light slot (cold white, brighter down the middle).

    python -I tools/assets/make_threshold_textures.py
"""
import json
import os

import numpy as np
from PIL import Image, PngImagePlugin
from scipy.ndimage import gaussian_filter

OUT = os.path.join(os.path.dirname(__file__), "..", "..", "src", "main", "resources", "assets", "interstice", "textures", "block")
N = 256


def noise(sigma, seed):
    n = gaussian_filter(np.random.default_rng(seed).standard_normal((N, N)), sigma, mode="wrap")
    return (n - n.mean()) / (n.std() + 1e-9)


def save(name, rgb, note):
    info = PngImagePlugin.PngInfo()
    info.add_text("interstice", note + "; tools/assets/make_threshold_textures.py")
    a = np.dstack([np.clip(rgb, 0, 255), np.full((N, N), 255.0)]).astype(np.uint8)
    Image.fromarray(a, "RGBA").save(os.path.join(OUT, name + ".png"), pnginfo=info)
    with open(os.path.join(OUT, name + ".png.mcmeta"), "w", encoding="utf-8", newline="\n") as f:
        json.dump({"texture": {"blur": True}}, f)


def concrete(base, seed, cloud=9.0, fine=3.0, pores=0.004, seam=True):
    lum = noise(40, seed) * cloud + noise(12, seed + 1) * cloud * 0.5 + noise(1.2, seed + 2) * fine
    r = np.random.default_rng(seed + 3)
    holes = gaussian_filter((r.random((N, N)) < pores).astype(float), 0.7, mode="wrap")
    lum -= holes / (holes.max() + 1e-9) * 22
    if seam:
        y = np.arange(N)
        d = np.minimum(np.abs(y - 128), N - np.abs(y - 128))
        lum -= (np.exp(-d * d / 2.0) * 7)[:, None]
    tint = np.array([0.975, 1.0, 1.035])
    return (base + lum)[..., None] * tint


if __name__ == "__main__":
    save("threshold_wall", concrete(118, 11), "procedural smooth concrete 256px (Level 00 walls and ceiling)")
    floor = concrete(82, 21, cloud=7, fine=2.5, pores=0.002, seam=False)
    x = np.arange(N)
    sheen = np.exp(-((x - 128) / 70.0) ** 2) * 6   # worn along the middle of the corridor
    floor += sheen[None, :, None]
    save("threshold_floor", floor, "procedural dense concrete 256px (Level 00 floor)")
    yy, xx = np.mgrid[0:N, 0:N]
    core = np.exp(-((xx - 127.5) / 70.0) ** 2)
    light = np.dstack([215 + 40 * core, 222 + 33 * core, 232 + 23 * core])
    save("threshold_light", light, "procedural ceiling light slot 256px (Level 00)")
    print("threshold textures ok")
