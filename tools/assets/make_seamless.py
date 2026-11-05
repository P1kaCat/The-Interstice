"""Make generated block textures tile without a visible grid (2026-11-05, after the PixelLab batch).

Generated swatches tend to have a vignette (brighter centre, darker edges) and edges that do not match, so a
wall or a road showed a checkerboard. For each texture:
  1. flatten: divide out the large-scale brightness (a wrapped Gaussian blur of the luminance), keeping the mean;
  2. seams: blend with a copy shifted by half a tile, using the shifted copy near the borders (its borders are
     the original's interior, continuous across the wrap) and the original in the middle.
Patterned materials (bricks, tiles, planks, ridges) get a lighter seam blend so their pattern stays crisp.

    python -I tools/assets/make_seamless.py name [name ...]
"""
import os
import sys

import numpy as np
from PIL import Image, PngImagePlugin
from scipy.ndimage import gaussian_filter

BLOCK = os.path.join(os.path.dirname(__file__), "..", "..", "src", "main", "resources", "assets", "interstice", "textures", "block")
PATTERNED = {"grimy_brick", "floorboards", "corrugated_metal", "container_red", "container_teal", "clinic_tile",
             "stained_tile", "sidewalk", "hazard_stripe", "steel_beam", "oxidized_plate", "asphalt_line"}


def seamless(rgb, width):
    h, w, _ = rgb.shape
    rolled = np.roll(rgb, (h // 2, w // 2), axis=(0, 1))
    yy, xx = np.mgrid[0:h, 0:w]
    d = np.minimum(np.minimum(xx, w - 1 - xx), np.minimum(yy, h - 1 - yy)).astype(float)
    wgt = np.clip(1 - d / width, 0, 1) ** 1.5
    return rgb * (1 - wgt[..., None]) + rolled * wgt[..., None]


def process(name):
    p = os.path.join(BLOCK, name + ".png")
    img = Image.open(p)
    info_txt = img.info.get("interstice", "")
    a = np.asarray(img.convert("RGBA")).astype(float)
    rgb, alpha = a[..., :3], a[..., 3]
    lum = rgb.mean(axis=2)
    low = gaussian_filter(lum, sigma=7, mode="wrap")
    k = 0.9 if name not in PATTERNED else 0.75
    gain = (lum.mean() / np.maximum(low, 1)) ** k
    rgb = rgb * gain[..., None]
    if name == "asphalt_line":   # keep the line where it is: only flatten, blend the top and bottom edges
        h = rgb.shape[0]
        rolled = np.roll(rgb, h // 2, axis=0)
        yy = np.arange(h)[:, None]
        d = np.minimum(yy, h - 1 - yy).astype(float)
        wgt = np.clip(1 - d / 6, 0, 1) ** 1.5
        rgb = rgb * (1 - wgt[..., None]) + rolled * wgt[..., None]
    else:
        rgb = seamless(rgb, 5 if name in PATTERNED else 9)
    out = np.dstack([np.clip(rgb, 0, 255), alpha]).astype(np.uint8)
    info = PngImagePlugin.PngInfo()
    info.add_text("interstice", (info_txt or "PixelLab generation") + "; made seamless (tools/assets/make_seamless.py)")
    Image.fromarray(out, "RGBA").save(p, pnginfo=info)


def seam_score(name):
    a = np.asarray(Image.open(os.path.join(BLOCK, name + ".png")).convert("RGB")).astype(float)
    edge = (np.abs(a[:, 0] - a[:, -1]).mean() + np.abs(a[0] - a[-1]).mean()) / 2
    inner = (np.abs(np.diff(a, axis=1)).mean() + np.abs(np.diff(a, axis=0)).mean()) / 2
    lum = a.mean(axis=2)
    h = lum.shape[0]
    vign = lum[h // 4:3 * h // 4, h // 4:3 * h // 4].mean() - np.concatenate([lum[:3].ravel(), lum[-3:].ravel(), lum[:, :3].ravel(), lum[:, -3:].ravel()]).mean()
    return edge / max(inner, 1e-6), vign


if __name__ == "__main__":
    for n in sys.argv[1:]:
        before = seam_score(n)
        process(n)
        after = seam_score(n)
        print(f"{n:18s} seam ratio {before[0]:.2f} -> {after[0]:.2f}   vignette {before[1]:+.1f} -> {after[1]:+.1f}")
