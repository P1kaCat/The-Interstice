"""
Offline posed preview: a species posed at a given time of a clip, skinned with a chosen weight scheme and
skinning method, rasterised with a z-buffer and smooth shading (so creases, pinches and interpenetrations at
the joints show). Development aid for comparing skinning changes side by side; the in-game captures remain
the real validation.

Usage: python -I tools/creature/pose_preview.py <species> <out.png> <clip>:<time>[,<clip>:<time>...]
         [--views=90,140] [--variants=legacy/lbs,plane/dqs] [--zoom=1.0] [--focus=x,y,z]
Time is in seconds (one-shot clips) or 0..1 phase (loops, as the generator writes them, scaled by length).
"""
import math
import os
import sys

import numpy as np
from PIL import Image, ImageDraw

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import joint_report as jr  # noqa: E402
from skinning import Rig, mix_weights  # noqa: E402

MAT = {"skin": (150, 138, 134), "head": (150, 138, 134), "bone": (190, 182, 166), "nail": (40, 36, 36),
       "tooth": (210, 205, 190), "mouth": (70, 30, 32), "pit": (60, 50, 50), "eye": (220, 40, 30),
       "moss": (70, 86, 52), "mossbed": (64, 78, 48)}


SKIP = set()
HIGHLIGHT = []


def build(name, scheme):
    m, names, parents, pivots, hanging, meshes, clips = jr.load(name)
    rig = Rig(names, parents, pivots, hanging)
    extra = m.get("interstice_skin", {})
    X, J, W, col, tris = [], [], [], [], []
    for e, b in meshes:
        mat = e["name"].split("_")[-2]
        if mat in SKIP:
            continue
        o = np.array(e.get("origin", [0, 0, 0]), float)
        keys = list(e["vertices"])
        base = len(X)
        idx = {k: base + i for i, k in enumerate(keys)}
        ex = extra.get(e.get("uuid"))
        for k in keys:
            p = o + np.array(e["vertices"][k], float)
            js, ws = rig.weights(p, b, "legacy" if b in rig.hanging else scheme)
            if ex and ex["bone"] in names:
                js, ws = mix_weights(js, ws, names.index(ex["bone"]), float(ex["w"][int(k[1:])]))
            X.append(p)
            J.append(js)
            W.append(ws)
        for f in e["faces"].values():
            vs = [idx[k] for k in f["vertices"]]
            for i in range(1, len(vs) - 1):
                tris.append((vs[0], vs[i], vs[i + 1]))
                col.append((230, 40, 40) if any(h in e["name"] for h in HIGHLIGHT) else MAT.get(mat, (150, 138, 134)))
    return names, parents, rig, np.array(X), np.array(J), np.array(W), np.array(tris), np.array(col, float), clips


def pose_at(names, parents, rig, clips, clip, t):
    length, loop, chans = clips[clip]
    if loop:
        t = t * length
    rots = np.zeros((len(names), 3))
    poss = np.zeros((len(names), 3))
    for b, ch, times, vals in chans:
        (rots if ch == "rotation" else poss)[b] += jr.sample(times, vals, t, loop)
    for b in rig.hanging:          # moss strands: no clip; hang them straight (direction kept from rest)
        rots[b] = 0
    return jr.skin_mats(names, parents, rig.P, rots, poss)


def raster(Y, tris, col, yaw, w, h, scale, focus):
    c, s = math.cos(math.radians(yaw)), math.sin(math.radians(yaw))
    R = np.array([[c, 0, s], [0, 1, 0], [-s, 0, c]])
    P = (Y - focus) @ R.T
    # vertex normals (smooth over shared vertices)
    A, B, C = P[tris[:, 0]], P[tris[:, 1]], P[tris[:, 2]]
    fn = np.cross(B - A, C - A)
    vn = np.zeros_like(P)
    for k in range(3):
        np.add.at(vn, tris[:, k], fn)
    vn /= np.maximum(1e-9, np.linalg.norm(vn, axis=1))[:, None]
    light = np.array([0.45, 0.75, -0.5])
    light /= np.linalg.norm(light)
    sx = w / 2 + P[:, 0] * scale
    sy = h / 2 - P[:, 1] * scale
    z = P[:, 2]
    img = np.full((h, w, 3), (205, 205, 200), float)
    zb = np.full((h, w), np.inf)
    for t_, cc in zip(tris, col):
        xs, ys = sx[t_], sy[t_]
        x0, x1 = max(0, int(math.floor(xs.min()))), min(w - 1, int(math.ceil(xs.max())))
        y0, y1 = max(0, int(math.floor(ys.min()))), min(h - 1, int(math.ceil(ys.max())))
        if x0 > x1 or y0 > y1:
            continue
        gx, gy = np.meshgrid(np.arange(x0, x1 + 1) + 0.5, np.arange(y0, y1 + 1) + 0.5)
        (ax, bx, cx), (ay, by, cy) = xs, ys
        den = (by - cy) * (ax - cx) + (cx - bx) * (ay - cy)
        if abs(den) < 1e-9:
            continue
        l0 = ((by - cy) * (gx - cx) + (cx - bx) * (gy - cy)) / den
        l1 = ((cy - ay) * (gx - cx) + (ax - cx) * (gy - cy)) / den
        l2 = 1 - l0 - l1
        inside = (l0 >= -1e-4) & (l1 >= -1e-4) & (l2 >= -1e-4)
        if not inside.any():
            continue
        zz = l0 * z[t_[0]] + l1 * z[t_[1]] + l2 * z[t_[2]]
        sub = zb[y0:y1 + 1, x0:x1 + 1]
        win = inside & (zz < sub)
        if not win.any():
            continue
        n = l0[..., None] * vn[t_[0]] + l1[..., None] * vn[t_[1]] + l2[..., None] * vn[t_[2]]
        n /= np.maximum(1e-9, np.linalg.norm(n, axis=-1))[..., None]
        # lit from the camera side whatever the winding: shade by |n . light| with a soft ambient
        d = np.abs(n @ light)
        rim = np.clip(1 - np.abs(n[..., 2]), 0, 1) ** 3 * 0.25
        shade = 0.28 + 0.62 * d + rim
        sub[win] = zz[win]
        img[y0:y1 + 1, x0:x1 + 1][win] = (shade[..., None] * cc)[win]
    return Image.fromarray(np.clip(img, 0, 255).astype(np.uint8))


def main():
    args = [a for a in sys.argv[1:] if not a.startswith("--")]
    opts = dict((a[2:].split("=", 1) + [""])[:2] for a in sys.argv[1:] if a.startswith("--"))
    name, out, shots = args[0], args[1], [s.split(":") for s in args[2].split(",")]
    views = [float(v) for v in opts.get("views", "90,140").split(",")]
    variants = [v.split("/") for v in opts.get("variants", "legacy/lbs,plane/dqs").split(",")]
    zoom = float(opts.get("zoom", "1.0"))
    if opts.get("highlight"):
        HIGHLIGHT.extend(opts["highlight"].split(";"))
    if "nomoss" in opts:
        SKIP.update(("moss", "mossbed"))
    w, h = int(opts.get("w", 420)), int(opts.get("h", 320))
    built = {}
    tiles = []
    if "gif" in opts:   # an animated clip: N frames, one view per column, first variant; fixed camera
        n = int(opts["gif"] or 24)
        scheme, method = variants[0]
        names, parents, rig, X, J, W, tris, col, clips = built.setdefault(scheme, build(name, scheme))
        clip = shots[0][0]
        length, loop, _ = clips[clip]
        focus = np.array([0.0, (X[:, 1].max() + X[:, 1].min()) / 2 + float(opts.get("lift", 0)), (X[:, 2].max() + X[:, 2].min()) / 2])
        scale = zoom * min(w / (np.ptp(X[:, 2]) * 1.5), h / (np.ptp(X[:, 1]) * 2.2))
        frames = []
        for i in range(n):
            tm = i / n if loop else length * i / (n - 1)
            M = pose_at(names, parents, rig, clips, clip, tm)
            Y = jr.deform(M, X, J, W, method)
            fr = Image.new("RGB", (w * len(views), h))
            for j, yaw in enumerate(views):
                im = raster(Y, tris, col, yaw, w, h, scale, focus)
                d = ImageDraw.Draw(im)
                gy = h / 2 + focus[1] * scale
                d.line([(0, gy), (w, gy)], fill=(120, 120, 110))
                d.text((6, 4), f"{name} {clip} {tm:.2f}{'' if not loop else ' (phase)'} yaw {yaw:.0f}", fill=(0, 0, 0))
                fr.paste(im, (j * w, 0))
            frames.append(fr)
        dur = int(1000 * length / n) if opts.get("realtime") is not None else 80
        frames[0].save(out, save_all=True, append_images=frames[1:], duration=dur, loop=0)
        print(out)
        return
    for clip, tm in shots:
        for yaw in views:
            row = []
            for scheme, method in variants:
                if scheme not in built:
                    built[scheme] = build(name, scheme)
                names, parents, rig, X, J, W, tris, col, clips = built[scheme]
                M = pose_at(names, parents, rig, clips, clip, float(tm))
                Y = jr.deform(M, X, J, W, method)
                if "bone" in opts:     # centre on the posed pivot of a bone (a joint close-up)
                    bi = names.index(opts["bone"])
                    focus = M[bi, :, :3] @ rig.P[bi] + M[bi, :, 3]
                elif "focus" in opts:
                    focus = np.array([float(v) for v in opts["focus"].split(",")])
                else:
                    focus = np.array([0.0, (Y[:, 1].max() + Y[:, 1].min()) / 2, (Y[:, 2].max() + Y[:, 2].min()) / 2])
                scale = zoom * min(w / max(1e-6, np.ptp(Y[:, 2]) * 1.15), h / max(1e-6, np.ptp(Y[:, 1]) * 1.25))
                im = raster(Y, tris, col, yaw, w, h, scale, focus)
                ImageDraw.Draw(im).text((6, 4), f"{name} {clip}@{tm} yaw {yaw:.0f} {scheme}/{method}", fill=(0, 0, 0))
                row.append(im)
            tiles.append(row)
    cols = max(len(r) for r in tiles)
    sheet = Image.new("RGB", (w * cols, h * len(tiles)), (255, 255, 255))
    for i, row in enumerate(tiles):
        for j, im in enumerate(row):
            sheet.paste(im, (j * w, i * h))
    sheet.save(out)
    print(out)


if __name__ == "__main__":
    main()
