"""
Skin weights shared by the exporter (export_adherent.py) and the joint report (joint_report.py).

Two schemes:
- "legacy" (v5 and before): own bone, plus a 50/50 blend with the parent at the own pivot and with the
  nearest child at its pivot, fading out linearly over JOINT_RADIUS px of distance to the pivot. The blend
  zone has the same size on a thin finger and on the Burden's thigh, and a vertex on the outer side of a thick
  limb (far from the pivot) gets no blend at all: the skin creases there.
- "plane" (v6): along the main chain of the skeleton (hips-spine-chest-neck-head, shoulder-arm-fore-hand-
  fingers, thigh-shin-foot-toes), the hand-over between two bones is a smoothstep across the joint's
  bisecting plane: the signed distance of the vertex to that plane, over a width proportional to the
  vertex's distance from the joint axis (the local thickness of the limb). Every vertex of a ring changes
  bone at the same place along the limb, whatever its side, and a thick joint blends over a longer stretch
  than a thin one. Side branches (chest-shoulder, hips-thigh, head-jaw) keep the legacy radial blend; the
  generator's explicit hand-over gradients ("interstice_skin") are mixed on top as before.

Both return at most three (bone, weight) pairs per vertex, weights summing to 1.
"""
import numpy as np

JOINT_RADIUS = 3.0
# main chain: bone base name -> its main child (the joint whose hand-over is plane-based)
CHAIN = {"hips": "spine", "spine": "chest", "chest": "neck", "neck": "head", "shoulder": "arm", "arm": "fore",
         "fore": "hand", "hand": "fingers", "thigh": "shin", "shin": "foot", "foot": "toes"}
# direction of a bone without a main child (rest, Blockbench space)
LEAF_DIR = {"head": (0, -0.35, -1), "jaw": (0, -0.1, -1), "fingers": (0, -0.25, -1), "toes": (0, -0.3, -1)}
# plane blend width = clamp(WIDTH_K * distance to the joint axis, WIDTH_MIN, WIDTH_MAX) px, either side of the plane
WIDTH_K, WIDTH_MIN, WIDTH_MAX = 0.55, 1.2, 7.0


def _base(n):
    return n[:-2] if n[-2:] in ("_l", "_r") else n


def _side(n):
    return n[-2:] if n[-2:] in ("_l", "_r") else ""


def _smooth(e0, e1, x):
    t = np.clip((x - e0) / (e1 - e0), 0.0, 1.0)
    return t * t * (3 - 2 * t)


class Rig:
    """names, parents (index or None), pivots (Blockbench px), hanging: set of bone indices (moss strands)."""

    def __init__(self, names, parents, pivots, hanging=()):
        self.names = list(names)
        self.parents = list(parents)
        self.P = np.array(pivots, float)
        self.hanging = set(hanging)
        n = len(self.names)
        self.children = {i: [j for j, p in enumerate(self.parents) if p == i and j not in self.hanging] for i in range(n)}
        self.main_child = {}
        for i, nm in enumerate(self.names):
            want = CHAIN.get(_base(nm))
            if want:
                tgt = want + (_side(nm) if want not in ("neck", "head", "spine", "chest") else "")
                if tgt in self.names:
                    self.main_child[i] = self.names.index(tgt)
        self.main_parent = {c: p for p, c in self.main_child.items()}
        self.dir = np.zeros((n, 3))
        for i, nm in enumerate(self.names):
            if i in self.main_child:
                d = self.P[self.main_child[i]] - self.P[i]
            else:
                d = np.array(LEAF_DIR.get(_base(nm), (0, -1, 0)), float)
            self.dir[i] = d / max(1e-9, np.linalg.norm(d))
        # joint planes, keyed by the child bone: (point, unit normal pointing into the child)
        self.plane = {}
        for c, p in self.main_parent.items():
            nrm = self.dir[p] + self.dir[c]
            if np.linalg.norm(nrm) < 1e-3:
                nrm = self.dir[p]
            self.plane[c] = (self.P[c], nrm / np.linalg.norm(nrm))

    # ---------------------------------------------------------------- legacy
    def legacy(self, p, b):
        js, ws = [b], [1.0]
        par = self.parents[b]
        if par is not None:
            w = 0.5 * max(0.0, 1.0 - np.linalg.norm(p - self.P[b]) / JOINT_RADIUS)
            if w > 0:
                js.append(par)
                ws.append(w)
        best, bd = None, 1e9
        for c in self.children[b]:
            d = np.linalg.norm(p - self.P[c])
            if d < bd:
                best, bd = c, d
        if best is not None:
            w = 0.5 * max(0.0, 1.0 - bd / JOINT_RADIUS)
            if w > 0:
                js.append(best)
                ws.append(w)
        ws[0] = 1.0 - sum(ws[1:])
        return _pad(js, ws)

    # ---------------------------------------------------------------- plane
    def _cross(self, p, c):
        """Share of the child c at point p across the joint plane parent(c)|c."""
        J, nrm = self.plane[c]
        d = p - J
        s = float(d @ nrm)
        r = float(np.linalg.norm(d - s * nrm))
        w = min(WIDTH_MAX, max(WIDTH_MIN, WIDTH_K * r))
        return float(_smooth(-w, w, s))

    def plane_weights(self, p, b):
        acc = {}
        own = 1.0
        if b in self.main_parent:                      # hand-over from the parent, across this bone's joint
            t = self._cross(p, b)
            acc[self.main_parent[b]] = 1.0 - t
            own -= 1.0 - t
        elif self.parents[b] is not None:              # side branch: radial blend at the own pivot
            w = 0.5 * max(0.0, 1.0 - np.linalg.norm(p - self.P[b]) / JOINT_RADIUS)
            if w > 0:
                acc[self.parents[b]] = w
                own -= w
        if b in self.main_child:                        # hand-over to the main child
            c = self.main_child[b]
            t = self._cross(p, c)
            if t > 0:
                acc[c] = acc.get(c, 0.0) + t
                own -= t
        side = [c for c in self.children[b] if c != self.main_child.get(b)]
        if side:                                        # nearest side child (shoulder on the chest, thigh on the hips, jaw)
            c = min(side, key=lambda c: np.linalg.norm(p - self.P[c]))
            w = 0.5 * max(0.0, 1.0 - np.linalg.norm(p - self.P[c]) / JOINT_RADIUS)
            if w > 0:
                acc[c] = acc.get(c, 0.0) + w
                own -= w
        if own < 0:                                     # a short bone overlapped by both joints: rescale
            tot = sum(acc.values())
            acc = {k: v / tot for k, v in acc.items()}
            own = 0.0
        acc[b] = acc.get(b, 0.0) + own
        top = sorted(acc.items(), key=lambda kv: -kv[1])[:3]
        tot = sum(w for _, w in top) or 1.0
        return _pad([j for j, _ in top], [w / tot for _, w in top])

    def weights(self, p, b, scheme="plane"):
        return self.legacy(p, b) if scheme == "legacy" else self.plane_weights(p, b)


def _pad(js, ws):
    js, ws = list(js)[:3], list(ws)[:3]
    while len(js) < 3:
        js.append(js[0])
        ws.append(0.0)
    return js, ws


def mix_weights(js, ws, other, wo):
    """(1 - wo) of the joint-blended weights, wo of the other bone; the three largest kept, renormalised."""
    acc = {}
    for j, w in zip(js, ws):
        acc[j] = acc.get(j, 0.0) + w * (1 - wo)
    acc[other] = acc.get(other, 0.0) + wo
    top = sorted(acc.items(), key=lambda kv: -kv[1])[:3]
    tot = sum(w for _, w in top) or 1.0
    return _pad([j for j, _ in top], [w / tot for _, w in top])
