"""Floor textures that span 2x2 blocks (2026-11-05): asphalt, its centre line, the sidewalk.

A 64x64 seamless texture is built per material (fine aggregate grain, large soft stains, a few hairline cracks
that run across block joints) in the colours of the PixelLab swatches, and cut into four 32x32 quarters,
<name>_0..3.png, shown by MacroTileBlock according to the block's position. Nothing repeats inside 2 blocks
and the stains and cracks carry over the joints.

    python -I tools/assets/make_macro_textures.py
"""
import os

import numpy as np
from PIL import Image, PngImagePlugin
from scipy.ndimage import gaussian_filter

B = os.path.join(os.path.dirname(__file__), "..", "..", "src", "main", "resources", "assets", "interstice", "textures", "block")
N = 64
rng = np.random.default_rng(2026)


def wrap_noise(sigma, seed):
    r = np.random.default_rng(seed)
    n = gaussian_filter(r.standard_normal((N, N)), sigma, mode="wrap")
    return (n - n.mean()) / (n.std() + 1e-9)


def cracks(count, seed):
    """Hairline cracks: random walks that wrap around the tile."""
    r = np.random.default_rng(seed)
    m = np.zeros((N, N))
    for _ in range(count):
        x, y = r.random() * N, r.random() * N
        ang = r.random() * 6.28
        for _ in range(int(20 + r.random() * 30)):
            m[int(y) % N, int(x) % N] = 1
            ang += r.normal(0, 0.45)
            x += np.cos(ang)
            y += np.sin(ang)
    return m


def mean_colour(name):
    p = os.path.join(B, name + ".png")
    return np.asarray(Image.open(p).convert("RGB")).reshape(-1, 3).astype(float).mean(0)


def save_quarters(name, rgb):
    info = PngImagePlugin.PngInfo()
    info.add_text("interstice", "macro texture (2x2 blocks) from tools/assets/make_macro_textures.py, PixelLab colours")
    for t in range(4):
        qx, qz = t & 1, t >> 1
        q = rgb[qz * 32:(qz + 1) * 32, qx * 32:(qx + 1) * 32]
        img = np.dstack([np.clip(q, 0, 255), np.full((32, 32), 255)]).astype(np.uint8)
        Image.fromarray(img, "RGBA").save(os.path.join(B, f"{name}_{t}.png"), pnginfo=info)
    print("wrote", name, "quarters")


def asphalt():
    base = mean_colour("asphalt")
    grain = rng.standard_normal((N, N)) * 6
    specks = (rng.random((N, N)) > 0.93) * rng.uniform(10, 22, (N, N)) - (rng.random((N, N)) < 0.05) * 9
    stains = wrap_noise(6, 1) * 6 + wrap_noise(2.5, 2) * 3
    c = cracks(3, 3)
    lum = grain + specks + stains - c * 16
    return base[None, None, :] + lum[..., None] * np.array([1.0, 1.0, 1.02])


def asphalt_line(a):
    out = a.copy()
    wear = wrap_noise(1.2, 9) * 0.5 + rng.random((N, N)) * 0.8
    paint = np.array([152.0, 124, 50])
    for cx in (16, 48):
        for x in range(cx - 3, cx + 3):
            for y in range(N):
                if wear[y, x] > 0.15:
                    out[y, x] = paint * (0.72 + 0.25 * min(1.0, wear[y, x]))
    return out


def sidewalk():
    base = mean_colour("sidewalk")
    grain = rng.standard_normal((N, N)) * 5
    stains = wrap_noise(7, 11) * 7 + wrap_noise(3, 12) * 3
    lum = grain + stains
    out = base[None, None, :] + lum[..., None]
    # slabs: 16 px (half a block) each, a slight tone per slab, dark joints, a few chipped corners
    for sy in range(0, N, 16):
        for sx in range(0, N, 16):
            out[sy:sy + 16, sx:sx + 16] *= 0.94 + 0.12 * rng.random()
    out[0::16, :] *= 0.6
    out[:, 0::16] *= 0.6
    c = cracks(1, 13)
    out *= (1 - 0.18 * c)[..., None]
    return out


if __name__ == "__main__":
    a = asphalt()
    save_quarters("asphalt", a)
    save_quarters("asphalt_line", asphalt_line(a))
    save_quarters("sidewalk", sidewalk())
