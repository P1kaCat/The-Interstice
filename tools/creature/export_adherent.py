"""
Exports models/adherent.bbmodel (Blockbench Generic: meshes attached to bones + animations) to the
game asset used by the in-game skinned-mesh renderer:

    src/client/resources/assets/interstice/models/entity/adherent.json
    src/client/resources/assets/interstice/textures/entity/adherent.png

What the exporter adds on top of the Blockbench file:
- Smooth skinning at joints. In Blockbench each mesh is rigidly parented to one bone. Here every
  vertex near a joint is blended 50/50 at the pivot (fading out over JOINT_RADIUS) between the bone and
  its parent / child, so the two overlapping segments meet without a gap when the joint bends.
- Gradient hand-over (the generator's "interstice_skin" block): a mesh can be carried by another bone at
  one end and hand over to its own bone along its length (limbs growing out of the torso, connective
  masses, moss roots). Mixed with the joint blending above.
- Hanging bones (moss strands, "interstice": {"dangle": [x, y, z]} on the bone): written to the asset so
  the game swings them; they never take part in the joint blending of body meshes.
- Eyes (meshes whose name contains "_eye_"): their triangles are listed separately ("eyeIndices") for the
  emissive pass, kept unreduced in LOD1; a second texture named <species>_eyes.png is the emissive atlas.
- Moss (meshes "_moss_" / "_mossbed_") is left out of LOD1 (too small to read at that distance).
- Smooth normals (per mesh, by position), oriented outwards.
- LOD1 for the horde's mid-distance instances: vertex clustering (grid per dominant bone).
- Animation channels (rotation degrees / position pixels) with Catmull-Rom interpolation, as authored.

Works on any edited version of the .bbmodel, as long as bone names stay stable.
Usage: python -I tools/creature/export_adherent.py [species ...|all]   (default: adherent)
Every species of the family uses the same 24-bone skeleton: models/<species>.bbmodel ->
models/entity/<species>.json + textures/entity/<species>.png.
"""
import base64
import json
import math
import os
import sys

import numpy as np

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
ASSET_DIR = os.path.join(ROOT, "src", "client", "resources", "assets", "interstice")
FAMILY = ["adherent", "crawler", "strider", "burden", "clinger", "hollow", "watcher", "fractured"]
NAME = "adherent"
SRC = OUT_JSON = OUT_PNG = None


def select(name):
    global NAME, SRC, OUT_JSON, OUT_PNG
    NAME = name
    SRC = os.path.join(ROOT, "models", f"{name}.bbmodel")
    OUT_JSON = os.path.join(ASSET_DIR, "models", "entity", f"{name}.json")
    OUT_PNG = os.path.join(ASSET_DIR, "textures", "entity", f"{name}.png")
    global OUT_GLOW
    OUT_GLOW = os.path.join(ASSET_DIR, "textures", "entity", f"{name}_eyes.png")


JOINT_RADIUS = 3.0     # px over which a joint blends between neighbouring bones
LOD1_CELL = 1.7        # px grid for the horde LOD (scaled by body length relative to the Adherent's 52 px)
REQUIRED = ["hips", "spine", "chest", "neck", "head", "jaw"] + [f"{b}_{s}" for s in "lr" for b in
            ("shoulder", "arm", "fore", "hand", "fingers", "thigh", "shin", "foot", "toes")]
# cycle length in blocks of travel for distance-driven locomotion (stride / stance / 16), used if the
# custom "interstice" block was dropped when the file was re-saved by Blockbench
CYCLE_FALLBACK = {"walk": 6.0 / 0.72 / 16, "run": 10.0 / 0.42 / 16, "climb": 6.0 / 0.75 / 16}


def fail(msg):
    sys.exit(f"export_adherent: {msg}")


def main():
    with open(SRC, encoding="utf-8") as fh:
        model = json.load(fh)
    elements = {e["uuid"]: e for e in model.get("elements", [])}
    bones, parents, pivots, mesh_bone, dangles = [], [], [], [], []
    extra = model.get("interstice_skin", {})

    def walk(node, parent):
        if isinstance(node, str):
            if parent is None:
                fail("an element is outside every bone")
            e = elements.get(node)
            if e and e.get("type") == "mesh":
                mesh_bone.append((e, parent))
            return
        idx = len(bones)
        bones.append(node["name"])
        parents.append(parent)
        pivots.append([float(c) for c in node["origin"]])
        if any(abs(float(a)) > 1e-6 for a in node.get("rotation", [0, 0, 0])):
            fail(f"bone '{node['name']}' has a rest rotation; keep rest rotations at 0 and pose the mesh instead")
        dangles.append(node.get("interstice", {}).get("dangle"))
        for c in node.get("children", []):
            walk(c, idx)

    for top in model.get("outliner", []):
        walk(top, None)
    missing = [b for b in REQUIRED if b not in bones]
    if missing:
        fail(f"missing bones: {missing}")
    if len(set(bones)) != len(bones):
        fail("bone names must be unique")
    hanging = {i for i, d in enumerate(dangles) if d}
    children = {i: [j for j, p in enumerate(parents) if p == i and j not in hanging] for i in range(len(bones))}
    P = np.array(pivots)
    tw = model.get("resolution", {}).get("width", 512)
    th = model.get("resolution", {}).get("height", 512)

    positions, normals, uvs, joints, weights, indices = [], [], [], [], [], []
    kind = []   # per triangle: 0 body, 1 eye, 2 moss
    for e, b in mesh_bone:
        ex = extra.get(e.get("uuid"))
        ex_bone = bones.index(ex["bone"]) if ex and ex["bone"] in bones else None
        tri_kind = 1 if "_eye_" in e["name"] else 2 if ("_moss_" in e["name"] or "_mossbed_" in e["name"]) else 0
        if any(abs(float(a)) > 1e-6 for a in e.get("rotation", [0, 0, 0])):
            fail(f"mesh '{e['name']}' is rotated; apply the rotation to its vertices")
        origin = np.array(e.get("origin", [0, 0, 0]), float)
        vpos = {k: origin + np.array(v, float) for k, v in e["vertices"].items()}
        # smooth normals by vertex key
        acc = {k: np.zeros(3) for k in vpos}
        tris = []
        for f in e["faces"].values():
            vs = f["vertices"]
            if len(vs) < 3:
                continue
            for a, c, d in ((vs[0], vs[1], vs[2]),) + (((vs[0], vs[2], vs[3]),) if len(vs) == 4 else ()):
                n = np.cross(vpos[c] - vpos[a], vpos[d] - vpos[a])
                for k in (a, c, d):
                    acc[k] += n
                tris.append((a, c, d, f))
        centroid = np.mean(list(vpos.values()), axis=0)
        outward = sum(np.dot(acc[k], vpos[k] - centroid) for k in vpos)
        sign = 1.0 if outward >= 0 else -1.0
        cache = {}
        for a, c, d, f in tris:
            for k in (a, c, d):
                uv = f["uv"].get(k, [0, 0])
                key = (k, round(uv[0], 3), round(uv[1], 3))
                if key not in cache:
                    p = vpos[k]
                    n = acc[k] * sign
                    nl = np.linalg.norm(n)
                    n = n / nl if nl > 1e-9 else np.array([0, 1.0, 0])
                    js, ws = skin_weights(p, b, parents, children, P)
                    if ex_bone is not None:
                        js, ws = mix_weights(js, ws, ex_bone, float(ex["w"][int(k[1:])]))
                    cache[key] = len(positions)
                    positions.append(p)
                    normals.append(n)
                    uvs.append((uv[0] / tw, uv[1] / th))
                    joints.append(js)
                    weights.append(ws)
                indices.append(cache[key])
            kind.append(tri_kind)
    positions, normals, uvs = np.array(positions), np.array(normals), np.array(uvs)
    joints, weights, indices = np.array(joints), np.array(weights), np.array(indices)
    kind = np.array(kind)
    tri = indices.reshape(-1, 3)
    lod0 = pack_lod(positions, normals, uvs, joints, weights, indices)
    lod0["eyeIndices"] = [int(v) for v in tri[kind == 1].reshape(-1)]
    cell = LOD1_CELL * max(0.5, float(np.ptp(positions[:, 2])) / 52.0)
    body = tri[kind == 0].reshape(-1)
    used = np.unique(body)
    sub = np.full(len(positions), -1, int)
    sub[used] = np.arange(len(used))
    c_pos, c_nor, c_uv, c_jo, c_we, c_ind = cluster(positions[used], normals[used], uvs[used], joints[used], weights[used],
                                                    sub[body], cell)
    # the eyes go into LOD1 unreduced (a distant creature in the dark is mostly its eyes)
    eye_v = np.unique(tri[kind == 1].reshape(-1)) if (kind == 1).any() else np.zeros(0, int)
    remap = {int(v): len(c_pos) + i for i, v in enumerate(eye_v)}
    if len(eye_v):
        c_pos = np.concatenate([c_pos, positions[eye_v]])
        c_nor = np.concatenate([c_nor, normals[eye_v]])
        c_uv = np.concatenate([c_uv, uvs[eye_v]])
        c_jo = np.concatenate([c_jo, joints[eye_v]])
        c_we = np.concatenate([c_we, weights[eye_v]])
    eye1 = np.array([remap[int(v)] for v in tri[kind == 1].reshape(-1)], int)
    lod1 = pack_lod(c_pos, c_nor, c_uv, c_jo, c_we, np.concatenate([c_ind, eye1]).astype(int))
    lod1["eyeIndices"] = [int(v) for v in eye1]

    anims = {}
    for a in model.get("animations", []):
        chans = []
        for uid, an in a.get("animators", {}).items():
            name = an.get("name")
            if name not in bones:
                continue
            for ch, kind in (("rotation", "rot"), ("position", "pos")):
                kfs = sorted((k for k in an.get("keyframes", []) if k.get("channel") == ch), key=lambda k: float(k["time"]))
                if not kfs:
                    continue
                times = [round(float(k["time"]), 4) for k in kfs]
                vals = []
                for k in kfs:
                    dp = k["data_points"][0]
                    vals += [round(float(dp[c]), 4) for c in "xyz"]
                chans.append({"bone": bones.index(name), "type": kind, "times": times, "values": vals})
        meta = a.get("interstice", {})
        anims[a["name"]] = {"length": float(a.get("length", 1)), "loop": a.get("loop") == "loop",
                            "cycleBlocks": round(float(meta.get("cycle_blocks", CYCLE_FALLBACK.get(a["name"], 0))), 4),
                            "cycleDegrees": round(float(meta.get("cycle_degrees", 0)), 4),
                            "channels": chans}

    asset = {"format": 1, "source": f"models/{NAME}.bbmodel", "texture": f"interstice:textures/entity/{NAME}.png",
             "bones": [dict({"name": n, "parent": -1 if p is None else p, "pivot": [round(c, 4) for c in pv]},
                            **({"dangle": [round(float(c), 4) for c in d]} if d else {}))
                       for n, p, pv, d in zip(bones, parents, pivots, dangles)],
             "lods": [lod0, lod1], "animations": anims}
    os.makedirs(os.path.dirname(OUT_JSON), exist_ok=True)
    with open(OUT_JSON, "w", encoding="utf-8", newline="\n") as fh:
        json.dump(asset, fh, separators=(",", ":"))
    tex = model.get("textures", [])
    src = tex[0].get("source", "") if tex else ""
    if src.startswith("data:image/png;base64,"):
        with open(OUT_PNG, "wb") as fh:
            fh.write(base64.b64decode(src.split(",", 1)[1]))
    glow = [t for t in tex if t.get("name", "").endswith("_eyes.png") and t.get("source", "").startswith("data:image/png;base64,")]
    if glow:
        with open(OUT_GLOW, "wb") as fh:
            fh.write(base64.b64decode(glow[0]["source"].split(",", 1)[1]))
    print(f"wrote {OUT_JSON}: {len(bones)} bones, LOD0 {len(lod0['indices']) // 3} tris / {len(lod0['positions']) // 3} verts, "
          f"LOD1 {len(lod1['indices']) // 3} tris / {len(lod1['positions']) // 3} verts, {len(anims)} animations "
          f"({os.path.getsize(OUT_JSON) // 1024} KB)")


def skin_weights(p, b, parents, children, P):
    """Own bone, plus a 50/50 blend at the own pivot (with the parent) and at the nearest child pivot."""
    js, ws = [b], [1.0]
    par = parents[b]
    if par is not None:
        d = np.linalg.norm(p - P[b])
        w = 0.5 * max(0.0, 1.0 - d / JOINT_RADIUS)
        if w > 0:
            js.append(par)
            ws.append(w)
    best, bd = None, 1e9
    for c in children[b]:
        d = np.linalg.norm(p - P[c])
        if d < bd:
            best, bd = c, d
    if best is not None:
        w = 0.5 * max(0.0, 1.0 - bd / JOINT_RADIUS)
        if w > 0:
            js.append(best)
            ws.append(w)
    ws[0] = 1.0 - sum(ws[1:])
    while len(js) < 3:
        js.append(b)
        ws.append(0.0)
    return js[:3], ws[:3]


def mix_weights(js, ws, other, wo):
    """(1 - wo) of the joint-blended weights, wo of the other bone; the three largest kept, renormalised."""
    acc = {}
    for j, w in zip(js, ws):
        acc[j] = acc.get(j, 0.0) + w * (1 - wo)
    acc[other] = acc.get(other, 0.0) + wo
    top = sorted(acc.items(), key=lambda kv: -kv[1])[:3]
    tot = sum(w for _, w in top) or 1.0
    js2, ws2 = [j for j, _ in top], [w / tot for _, w in top]
    while len(js2) < 3:
        js2.append(js2[0])
        ws2.append(0.0)
    return js2, ws2


def cluster(pos, nor, uv, jo, we, ind, cell=LOD1_CELL):
    """Vertex clustering decimation per dominant bone."""
    key_to_new, groups = {}, []
    remap = np.zeros(len(pos), int)
    for i, p in enumerate(pos):
        k = (int(jo[i][int(np.argmax(we[i]))]),) + tuple(np.floor(p / cell).astype(int))
        if k not in key_to_new:
            key_to_new[k] = len(groups)
            groups.append([])
        remap[i] = key_to_new[k]
        groups[key_to_new[k]].append(i)
    npos = np.array([pos[g].mean(axis=0) for g in groups])
    nnor = np.array([nor[g].sum(axis=0) for g in groups])
    nnor /= np.maximum(1e-9, np.linalg.norm(nnor, axis=1))[:, None]
    nuv = np.array([uv[g[0]] for g in groups])
    njo = np.array([jo[g[0]] for g in groups])
    nwe = np.array([we[g[0]] for g in groups])
    tris = remap[ind].reshape(-1, 3)
    keep = (tris[:, 0] != tris[:, 1]) & (tris[:, 1] != tris[:, 2]) & (tris[:, 0] != tris[:, 2])
    return npos, nnor, nuv, njo, nwe, tris[keep].reshape(-1)


def pack_lod(pos, nor, uv, jo, we, ind):
    return {"positions": [round(float(v), 4) for v in pos.reshape(-1)],
            "normals": [round(float(v), 3) for v in nor.reshape(-1)],
            "uvs": [round(float(v), 5) for v in uv.reshape(-1)],
            "joints": [int(v) for v in jo.reshape(-1)],
            "weights": [round(float(v), 4) for v in we.reshape(-1)],
            "indices": [int(v) for v in ind.reshape(-1)]}


if __name__ == "__main__":
    args = sys.argv[1:] or ["adherent"]
    if args == ["all"]:
        args = [n for n in FAMILY if os.path.exists(os.path.join(ROOT, "models", f"{n}.bbmodel"))]
    for n in args:
        select(n)
        main()
