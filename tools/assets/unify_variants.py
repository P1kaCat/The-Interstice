"""Make the random variants of a material join without a grid (2026-11-05, night).

Each material of the concrete/metal family has 4 random variants (make_concrete_32.py). Their edges did not match
each other and their overall brightness differed a little, so a large wall showed every block. Here every variant
is pulled, in a band along its edges, towards one shared edge texture (variant 0 made wrap-around), and scaled to
its mean brightness: any two variants side by side now meet like the shared texture meets itself, seamlessly, while
their interiors (cracks, stains) stay different. Also: the steel beam side is made to wrap along its length.

    python -I tools/assets/make_concrete_32.py && python -I tools/assets/unify_variants.py   (run after it, once)
"""
import os

import numpy as np
from PIL import Image, PngImagePlugin

TEX = os.path.join(os.path.dirname(__file__), "..", "..", "src", "main", "resources", "assets", "interstice", "textures", "block")
N = 32
# materials of one group share their edges (the three wall concretes stand side by side in the same walls)
GROUPS = [["weathered_concrete", "stained_concrete", "cracked_concrete"], ["concrete_panel"], ["concrete_floor"],
          ["concrete_rubble"], ["rusted_plate"], ["dark_metal"], ["metal_grate"]]
BAND = 7


def load(n):
    im = Image.open(os.path.join(TEX, n + ".png"))
    return np.asarray(im.convert("RGBA")).astype(float), im.info.get("interstice", "")


def save(n, a, note):
    info = PngImagePlugin.PngInfo()
    info.add_text("interstice", (note + "; edges unified (tools/assets/unify_variants.py)") if "unified" not in note else note)
    Image.fromarray(np.clip(a, 0, 255).astype(np.uint8), "RGBA").save(os.path.join(TEX, n + ".png"), pnginfo=info)


def wrap(a):
    """Blend with a half-shifted copy near the borders: the result tiles with itself."""
    sh = np.roll(np.roll(a, N // 2, 0), N // 2, 1)
    d = np.minimum(np.minimum(np.arange(N), N - 1 - np.arange(N)), 8) / 8
    w = np.minimum(d[:, None], d[None, :])[..., None]
    out = a.copy()
    out[..., :3] = a[..., :3] * w + sh[..., :3] * (1 - w)
    return out


def edge_weight():
    d = np.minimum(np.minimum(np.arange(N), N - 1 - np.arange(N))[:, None], np.minimum(np.arange(N), N - 1 - np.arange(N))[None, :])
    t = np.clip(d / BAND, 0, 1)
    return (t * t * (3 - 2 * t))[..., None]   # 0 on the edge (shared texture), 1 inside (the variant)


def main():
    w = edge_weight()
    for group in GROUPS:
        shared = wrap(load(group[0])[0])
        for fam in group:
            names = [fam] + [f"{fam}_{i}" for i in (1, 2, 3)]
            imgs = [load(n) for n in names]
            target = imgs[0][0][..., :3][imgs[0][0][..., 3] > 0].mean()   # the family keeps its own brightness
            edge = shared.copy()
            edge[..., :3] *= target / max(shared[..., :3][shared[..., 3] > 0].mean(), 1)
            for n, (a, note) in zip(names, imgs):
                out = a.copy()
                mean = a[..., :3][a[..., 3] > 0].mean()
                out[..., :3] = a[..., :3] * (target / max(mean, 1))
                out[..., :3] = out[..., :3] * w + edge[..., :3] * (1 - w)
                if fam == "metal_grate":
                    out[..., 3] = a[..., 3]   # keep the holes where they are (same pattern in every variant)
                save(n, out, note)
            print("unified", fam)
    # the steel beam's side wraps along its length (vertical in the texture)
    a, note = load("steel_beam")
    sh = np.roll(a, N // 2, 0)
    d = np.minimum(np.arange(N), N - 1 - np.arange(N))
    wv = (np.clip(d / 8, 0, 1))[:, None, None]
    out = a.copy()
    out[..., :3] = a[..., :3] * wv + sh[..., :3] * (1 - wv)
    save("steel_beam", out, note)
    print("steel beam wraps along its length")


if __name__ == "__main__":
    main()
