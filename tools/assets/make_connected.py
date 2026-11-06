"""Connected-texture tiles and material-transition overlays (2026-11-05), read by the client's ConnectedTextures.

Connected tiles: from two textures per material, a framed one F and a frameless one C, five tiles
textures/block/ctm/<name>_<i>.png: 0 isolated (F), 1 centre (C), 2 joined left-right (C + F's top and bottom
border), 3 joined up-down (C + F's left and right border), 4 inner corners (C + F's four corner squares).
  - rusted plates (4 variants): F = the texture, C = the centre crop of a PixelLab inpainting of a 2x2 mosaic whose
    seams and rivets were masked (the crop wraps exactly, the frame is gone);
  - containers, corrugated sheet, oxidized plate, hazard plating: C = the texture, F = a PixelLab inpainting of a
    frame ring at its edges;
  - grimy glass: F = the pane texture, C = its interior stretched and wrapped (procedural).
Transition overlays: textures/block/overlay/<set>_<l|r|t|b><0|1>.png, the spreader's own texture cut by a ragged
mask from one edge (two variants), with a darker rim where it lies on the neighbour (flesh adds thin veins).

    python -I tools/assets/make_connected.py
"""
import os

import numpy as np
from PIL import Image, PngImagePlugin
from scipy.ndimage import binary_dilation, gaussian_filter

ROOT = os.path.join(os.path.dirname(__file__), "..", "..")
TEX = os.path.join(ROOT, "src", "main", "resources", "assets", "interstice", "textures", "block")
RAW = os.path.join(os.path.dirname(__file__), "pixellab_raw", "connected")
N = 32


def load(path):
    return np.asarray(Image.open(path).convert("RGBA")).astype(float)


def save(rel, a, note):
    p = os.path.join(TEX, rel + ".png")
    os.makedirs(os.path.dirname(p), exist_ok=True)
    info = PngImagePlugin.PngInfo()
    info.add_text("interstice", note)
    Image.fromarray(np.clip(a, 0, 255).astype(np.uint8), "RGBA").save(p, pnginfo=info)


def tiles(name, F, C, bs, bt, note):
    """bs: border width on the left/right edges, bt: on the top/bottom edges."""
    t = [F, C, C.copy(), C.copy(), C.copy()]
    # the strips run on across the joints: their ends (where F has the side strip) repeat the strip's middle
    top, bot = F[:bt].copy(), F[N - bt:].copy()
    for strip in (top, bot):
        strip[:, :bs] = strip[:, bs:2 * bs]
        strip[:, N - bs:] = strip[:, N - 2 * bs:N - bs]
    left, right = F[:, :bs].copy(), F[:, N - bs:].copy()
    for strip in (left, right):
        strip[:bt] = strip[bt:2 * bt]
        strip[N - bt:] = strip[N - 2 * bt:N - bt]
    t[2][:bt], t[2][N - bt:] = top, bot
    t[3][:, :bs], t[3][:, N - bs:] = left, right
    for ys in (slice(0, bt), slice(N - bt, N)):
        for xs in (slice(0, bs), slice(N - bs, N)):
            t[4][ys, xs] = F[ys, xs]
    for i, a in enumerate(t):
        save(f"ctm/{name}_{i}", a, note)


PROCEDURAL_FRAME = {"oxidized_plate"}   # its inpainted frame came out white


def frame(C, bs, bt):
    """A frame drawn from the material itself: darkened border, a lighter inner edge line, rivets at the corners."""
    F = C.copy()
    ring = np.zeros((N, N), bool)
    ring[:bt] = ring[N - bt:] = True
    ring[:, :bs] = ring[:, N - bs:] = True
    F[ring, :3] *= 0.5
    inner = np.zeros((N, N), bool)
    inner[bt, bs:N - bs] = inner[N - bt - 1, bs:N - bs] = True
    inner[bt:N - bt, bs] = inner[bt:N - bt, N - bs - 1] = True
    F[inner, :3] = np.clip(F[inner, :3] * 1.25, 0, 255)
    for y in (bt + 2, N - bt - 4):
        for x in (bs + 2, N - bs - 4):
            F[y:y + 2, x:x + 2, :3] = np.clip(C[y:y + 2, x:x + 2, :3].mean((0, 1)) * 1.45, 0, 255)
            F[y + 2, x:x + 2, :3] *= 0.6
    return F


def connected():
    for v in ("rusted_plate", "rusted_plate_1", "rusted_plate_2", "rusted_plate_3"):
        F = load(os.path.join(TEX, v + ".png"))
        C = load(os.path.join(RAW, v + "_mosaic.png"))[16:48, 16:48]
        C[..., 3] = 255
        tiles(v, F, C, 2, 2, "connected tiles: frameless centre from a PixelLab inpainting; tools/assets/make_connected.py")
    for n, bs, bt in (("container_red", 3, 4), ("container_teal", 3, 4), ("corrugated_metal", 3, 3), ("oxidized_plate", 3, 3), ("hazard_stripe", 3, 3)):
        raw = os.path.join(RAW, n + "_framed.png")
        if not os.path.exists(raw):
            print("missing", raw)
            continue
        C = load(os.path.join(TEX, n + ".png"))
        F = load(raw)
        F[..., 3] = 255
        if n in PROCEDURAL_FRAME:
            F = frame(C, bs, bt)
        tiles(n, F, C, bs, bt, "connected tiles: frame from a PixelLab inpainting; tools/assets/make_connected.py")
    # bookshelf: frameless centre = centre crop of a PixelLab inpainting of a 1x2 mosaic with the side posts masked
    raw = os.path.join(RAW, "bookshelf_mosaic.png")
    if os.path.exists(raw):
        F = load(os.path.join(TEX, "rotten_bookshelf.png"))
        C = load(raw)[:, 16:48]
        C[..., 3] = 255
        tiles("rotten_bookshelf", F, C, 4, 0, "connected tiles: frameless centre from a PixelLab inpainting; tools/assets/make_connected.py")
    # glass: the interior (inside the 4 px frame) stretched over the whole tile, then wrapped
    F = load(os.path.join(TEX, "grimy_glass_pane.png"))
    inner = Image.fromarray(F[4:28, 4:28].astype(np.uint8), "RGBA").resize((N, N), Image.NEAREST)
    C = np.asarray(inner).astype(float)
    sh = np.roll(np.roll(C, 16, 0), 16, 1)
    d = np.minimum(np.minimum(np.arange(N), N - 1 - np.arange(N)), 6) / 6
    w = np.minimum(d[:, None], d[None, :])[..., None]
    C = C * w + sh * (1 - w)
    tiles("grimy_glass", F, C, 4, 4, "connected tiles: procedural from grimy_glass_pane; tools/assets/make_connected.py")
    print("connected ok")


def overlays():
    sets = {"flesh": ("flesh_growth", 9, True), "soil": ("grime_soil", 8, False), "rubble": ("concrete_rubble", 7, False),
            "plaster": ("plaster_wall", 7, False), "path": ("farm_path", 5, False)}
    yy, xx = np.mgrid[0:N, 0:N]
    for name, (src, depth, veins) in sets.items():
        tex = load(os.path.join(TEX, src + ".png"))
        for var in (0, 1):
            r = np.random.default_rng(sum(map(ord, name)) * 10 + var)
            # ragged reach along the edge, plus a few detached clumps
            reach = depth + gaussian_filter(r.standard_normal(N), 2.2, mode="wrap") * 9
            reach = np.clip(reach, 2, 15)
            base = (xx < reach[yy]).astype(float)   # spreader on the left ('l'); the others are rotations
            for _ in range(3):
                cx, cy, rad = r.uniform(reach.mean(), reach.mean() + 6), r.uniform(2, N - 2), r.uniform(1.0, 2.2)
                base[(xx - cx) ** 2 + (yy - cy) ** 2 < rad * rad] = 1
            vein = np.zeros((N, N))
            if veins:
                for _ in range(4):
                    y, x, ang = r.uniform(0, N), 0.0, r.normal(0, 0.5)
                    for _ in range(int(r.integers(10, 22))):
                        vein[int(y) % N, min(N - 1, int(x))] = 1
                        ang += r.normal(0, 0.45)
                        x += max(0.3, np.cos(ang))
                        y += np.sin(ang)
            for side, rot in (("l", 0), ("t", 3), ("r", 2), ("b", 1)):
                m = np.rot90(base, rot) > 0.5
                vm = np.rot90(vein, rot) > 0.5
                out = np.zeros((N, N, 4))
                out[m] = tex[m]
                rim = binary_dilation(m, iterations=1) & ~m
                inner_rim = m & binary_dilation(~m, iterations=1)
                out[inner_rim, :3] *= 0.62
                out[m, 3] = 255
                out[rim, :3] = tex[rim, :3] * 0.35   # a thin shadow where the spill lies on the neighbour
                out[rim, 3] = 160 if name != "flesh" else 200
                if veins:
                    vv = vm & ~m
                    out[vv, :3] = np.array([92.0, 22, 26])
                    out[vv, 3] = 255
                out[..., 3] = np.where(out[..., 3] >= 128, 255, 0)
                save(f"overlay/{name}_{side}{var}", out, f"transition overlay from {src}; tools/assets/make_connected.py")
    print("overlays ok")


if __name__ == "__main__":
    connected()
    overlays()
