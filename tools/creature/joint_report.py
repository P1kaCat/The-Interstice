"""
Joint deformation report: how much the skin stretches or collapses where bones meet, over every clip of a
species, for a skin-weight scheme and a skinning method. Reads models/<species>.bbmodel directly (no
regeneration), so weight and skinning changes can be compared in seconds.

Metric: for every edge of the body skin (materials skin/head; no moss, teeth, nails, eyes, spikes), the ratio
of its posed length to its rest length, at 17 times per clip. 1.00 = the skin keeps its size; 0.4 = a fold
collapsing; 2.0 = skin stretched to twice its length. "bad" counts edges outside 0.7-1.45.

Usage: python -I tools/creature/joint_report.py <species|all> [--schemes legacy,plane] [--methods lbs,dqs]
"""
import json
import math
import os
import sys

import numpy as np

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from skinning import Rig, mix_weights  # noqa: E402

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
FAMILY = ["adherent", "crawler", "strider", "burden", "clinger", "hollow", "watcher", "fractured"]
BODY = ("skin", "head")
CLIPS = ("walk", "run", "climb", "turn", "rear", "threat", "recover", "fall", "search", "observe", "cling", "idle")


def load(name):
    with open(os.path.join(ROOT, "models", f"{name}.bbmodel"), encoding="utf-8") as fh:
        m = json.load(fh)
    elements = {e["uuid"]: e for e in m["elements"]}
    names, parents, pivots, dangle, uid = [], [], [], [], {}
    meshes = []

    def walk(node, parent):
        if isinstance(node, str):
            e = elements.get(node)
            if e and e.get("type") == "mesh":
                meshes.append((e, parent))
            return
        i = len(names)
        names.append(node["name"])
        parents.append(parent)
        pivots.append([float(c) for c in node["origin"]])
        dangle.append(bool(node.get("interstice", {}).get("dangle")))
        uid[node["uuid"]] = i
        for c in node.get("children", []):
            walk(c, i)
    for top in m["outliner"]:
        walk(top, None)
    clips = {}
    for a in m["animations"]:
        chans = []
        for u, an in a["animators"].items():
            if an.get("name") not in names:
                continue
            b = names.index(an["name"])
            for ch in ("rotation", "position"):
                kf = sorted((k for k in an["keyframes"] if k["channel"] == ch), key=lambda k: float(k["time"]))
                if kf:
                    chans.append((b, ch, np.array([float(k["time"]) for k in kf]),
                                  np.array([[float(k["data_points"][0][c]) for c in "xyz"] for k in kf])))
        clips[a["name"]] = (float(a["length"]), a["loop"] == "loop", chans)
    return m, names, parents, pivots, {i for i, d in enumerate(dangle) if d}, meshes, clips


def catmull(p0, p1, p2, p3, f):
    return 0.5 * ((2 * p1) + (-p0 + p2) * f + (2 * p0 - 5 * p1 + 4 * p2 - p3) * f * f + (-p0 + 3 * p1 - 3 * p2 + p3) * f * f * f)


def sample(times, vals, t, loop):
    n = len(times)
    if n == 1 or t <= times[0]:
        return vals[0]
    if t >= times[-1]:
        return vals[-1]
    i = min(max(0, int(np.searchsorted(times, t, side="right")) - 1), n - 2)
    f = (t - times[i]) / (times[i + 1] - times[i])

    def at(k):
        if loop:
            return vals[k % (n - 1)]
        return vals[min(max(k, 0), n - 1)]
    return catmull(at(i - 1), at(i), at(i + 1), at(i + 2), f)


def rot(r):
    x, y, z = (math.radians(v) for v in r)
    cx, sx, cy, sy, cz, sz = math.cos(x), math.sin(x), math.cos(y), math.sin(y), math.cos(z), math.sin(z)
    Rx = np.array([[1, 0, 0], [0, cx, -sx], [0, sx, cx]])
    Ry = np.array([[cy, 0, sy], [0, 1, 0], [-sy, 0, cy]])
    Rz = np.array([[cz, -sz, 0], [sz, cz, 0], [0, 0, 1]])
    return Rz @ Ry @ Rx


def skin_mats(names, parents, P, rots, poss):
    """Per bone (R, t) such that posed = R @ rest + t (the game's skin matrix)."""
    n = len(names)
    GR, Gt = [None] * n, [None] * n
    out = np.zeros((n, 3, 4))
    for b in range(n):
        p = parents[b]
        tr = P[b] - (P[p] if p is not None else 0) + poss[b]
        R = rot(rots[b])
        if p is None:
            GR[b], Gt[b] = R, tr
        else:
            GR[b], Gt[b] = GR[p] @ R, GR[p] @ tr + Gt[p]
        out[b, :, :3] = GR[b]
        out[b, :, 3] = Gt[b] - GR[b] @ P[b]
    return out


def qmul(a, b):
    w1, x1, y1, z1 = a[..., 0], a[..., 1], a[..., 2], a[..., 3]
    w2, x2, y2, z2 = b[..., 0], b[..., 1], b[..., 2], b[..., 3]
    return np.stack([w1 * w2 - x1 * x2 - y1 * y2 - z1 * z2, w1 * x2 + x1 * w2 + y1 * z2 - z1 * y2,
                     w1 * y2 - x1 * z2 + y1 * w2 + z1 * x2, w1 * z2 + x1 * y2 - y1 * x2 + z1 * w2], axis=-1)


def mat_to_quat(R):
    w = math.sqrt(max(0.0, 1 + R[0, 0] + R[1, 1] + R[2, 2])) / 2
    x = math.sqrt(max(0.0, 1 + R[0, 0] - R[1, 1] - R[2, 2])) / 2
    y = math.sqrt(max(0.0, 1 - R[0, 0] + R[1, 1] - R[2, 2])) / 2
    z = math.sqrt(max(0.0, 1 - R[0, 0] - R[1, 1] + R[2, 2])) / 2
    x = math.copysign(x, R[2, 1] - R[1, 2])
    y = math.copysign(y, R[0, 2] - R[2, 0])
    z = math.copysign(z, R[1, 0] - R[0, 1])
    q = np.array([w, x, y, z])
    return q / np.linalg.norm(q)


def deform(M, X, J, W, method):
    if method == "lbs":
        out = np.zeros_like(X)
        for k in range(3):
            Mk = M[J[:, k]]
            out += W[:, k:k + 1] * (np.einsum("nij,nj->ni", Mk[:, :, :3], X) + Mk[:, :, 3])
        return out
    # dual quaternion skinning
    qr = np.array([mat_to_quat(M[b, :, :3]) for b in range(len(M))])
    tq = np.concatenate([np.zeros((len(M), 1)), M[:, :, 3]], axis=1)
    qd = 0.5 * qmul(tq, qr)
    r0 = qr[J[:, 0]]
    br = np.zeros((len(X), 4))
    bd = np.zeros((len(X), 4))
    for k in range(3):
        rk, dk = qr[J[:, k]], qd[J[:, k]]
        s = np.sign(np.sum(rk * r0, axis=1))
        s[s == 0] = 1
        br += (W[:, k] * s)[:, None] * rk
        bd += (W[:, k] * s)[:, None] * dk
    nrm = np.linalg.norm(br, axis=1)[:, None]
    br /= nrm
    bd /= nrm
    conj = br * np.array([1, -1, -1, -1])
    t = 2 * qmul(bd, conj)[:, 1:]
    v = np.concatenate([np.zeros((len(X), 1)), X], axis=1)
    return qmul(qmul(br, v), conj)[:, 1:] + t


def report(name, schemes, methods, quiet=False):
    m, names, parents, pivots, hanging, meshes, clips = load(name)
    rig = Rig(names, parents, pivots, hanging)
    P = rig.P
    extra = m.get("interstice_skin", {})
    X, edges, owner = [], [], []
    for e, b in meshes:
        mat = e["name"].split("_")[-2]
        if mat not in BODY:
            continue
        o = np.array(e.get("origin", [0, 0, 0]), float)
        keys = list(e["vertices"])
        base = len(X)
        idx = {k: base + i for i, k in enumerate(keys)}
        for k in keys:
            X.append(o + np.array(e["vertices"][k], float))
            owner.append((b, e.get("uuid"), k))
        es = set()
        for f in e["faces"].values():
            vs = f["vertices"]
            for i in range(len(vs)):
                a_, c_ = idx[vs[i]], idx[vs[(i + 1) % len(vs)]]
                es.add((min(a_, c_), max(a_, c_)))
        edges += sorted(es)
    X = np.array(X)
    E = np.array(edges)
    L0 = np.linalg.norm(X[E[:, 0]] - X[E[:, 1]], axis=1)
    keep = L0 > 0.05
    E, L0 = E[keep], L0[keep]
    edge_bone = np.array([owner[i][0] for i in E[:, 0]])
    results = {}
    for scheme in schemes:
        J = np.zeros((len(X), 3), int)
        W = np.zeros((len(X), 3))
        for i, (b, u, k) in enumerate(owner):
            js, ws = rig.weights(X[i], b, scheme)
            ex = extra.get(u)
            if ex and ex["bone"] in names:
                js, ws = mix_weights(js, ws, names.index(ex["bone"]), float(ex["w"][int(k[1:])]))
            J[i], W[i] = js, ws
        for method in methods:
            lines, allr = [], []
            for clip in CLIPS:
                if clip not in clips:
                    continue
                length, loop, chans = clips[clip]
                ratios = []
                for t in np.linspace(0, length, 17):
                    rots = np.zeros((len(names), 3))
                    poss = np.zeros((len(names), 3))
                    for b, ch, times, vals in chans:
                        (rots if ch == "rotation" else poss)[b] += sample(times, vals, t, loop)
                    M = skin_mats(names, parents, P, rots, poss)
                    Y = deform(M, X, J, W, method)
                    ratios.append(np.linalg.norm(Y[E[:, 0]] - Y[E[:, 1]], axis=1) / L0)
                R = np.array(ratios)            # (times, edges)
                lo, hi = R.min(axis=0), R.max(axis=0)
                bad = np.mean((lo < 0.7) | (hi > 1.45)) * 100
                worst_bone = {}
                for eb, l, h in zip(edge_bone, lo, hi):
                    s = max(h, 1 / max(l, 1e-3))
                    if s > worst_bone.get(eb, (0,))[0]:
                        worst_bone[eb] = (s, l, h)
                wb = sorted(worst_bone.items(), key=lambda kv: -kv[1][0])[:3]
                lines.append(f"  {clip:8s} 1-99% {np.percentile(R, 1):.2f}-{np.percentile(R, 99):.2f}  extremes {R.min():.2f}-{R.max():.2f}  "
                             f"bad {bad:4.1f}%  worst: " + ", ".join(f"{names[b]} {l:.2f}-{h:.2f}" for b, (s, l, h) in wb))
                allr.append((R.min(), R.max(), bad, np.percentile(R, 1), np.percentile(R, 99)))
            A = np.array(allr)
            summary = (f"{name:9s} {scheme:6s} {method}: worst extremes {A[:, 0].min():.2f}-{A[:, 1].max():.2f}, "
                       f"mean 1-99% {A[:, 3].mean():.2f}-{A[:, 4].mean():.2f}, mean bad {A[:, 2].mean():.2f}%")
            results[(scheme, method)] = (summary, lines)
            if not quiet:
                print(summary)
                print("\n".join(lines))
    return results


if __name__ == "__main__":
    args = [a for a in sys.argv[1:] if not a.startswith("--")]
    opts = dict(a[2:].split("=", 1) for a in sys.argv[1:] if a.startswith("--") and "=" in a)
    schemes = opts.get("schemes", "legacy,plane").split(",")
    methods = opts.get("methods", "lbs,dqs").split(",")
    species = FAMILY if args in ([], ["all"]) else args
    summ = []
    for s in species:
        res = report(s, schemes, methods, quiet="--summary" in sys.argv)
        summ += [v[0] for v in res.values()]
    print("\n".join(summ))
