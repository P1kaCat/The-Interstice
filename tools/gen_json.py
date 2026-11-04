"""Generates blockstates, models, item models, particles, lang and sounds.json for THE INTERSTICE."""
import json
import os

ROOT = os.path.join(os.path.dirname(__file__), "..", "src")
A = os.path.join(ROOT, "main", "resources", "assets", "interstice")
C = os.path.join(ROOT, "client", "resources", "assets", "interstice")


def w(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        json.dump(obj, f, indent=2)


simple = ["weathered_concrete", "concrete_panel", "stained_concrete", "cracked_concrete", "concrete_floor",
          "concrete_rubble", "rusted_plate", "dark_metal", "metal_grate"]
for b in simple:
    w(f"{A}/blockstates/{b}.json", {"variants": {"": {"model": f"interstice:block/{b}"}}})
    parent = "minecraft:block/cube_all"
    w(f"{A}/models/block/{b}.json", {"parent": parent, "textures": {"all": f"interstice:block/{b}"}})
    w(f"{A}/models/item/{b}.json", {"parent": f"interstice:block/{b}"})

# pipe (rotated pillar)
w(f"{A}/blockstates/rusted_pipe.json", {"variants": {
    "axis=y": {"model": "interstice:block/rusted_pipe"},
    "axis=z": {"model": "interstice:block/rusted_pipe", "x": 90},
    "axis=x": {"model": "interstice:block/rusted_pipe", "x": 90, "y": 90}}})
w(f"{A}/models/block/rusted_pipe.json", {"parent": "minecraft:block/cube_column",
                                          "textures": {"side": "interstice:block/rusted_pipe", "end": "interstice:block/rusted_pipe_top"}})
w(f"{A}/models/item/rusted_pipe.json", {"parent": "interstice:block/rusted_pipe"})

# small caged lamps
for lamp in ["industrial_lamp", "sodium_lamp", "emergency_lamp"]:
    w(f"{A}/blockstates/{lamp}.json", {"variants": {"": {"model": f"interstice:block/{lamp}"}}})
    t = f"interstice:block/{lamp}"
    w(f"{A}/models/block/{lamp}.json", {
        "parent": "minecraft:block/block",
        "textures": {"particle": t, "all": t},
        "elements": [{
            "from": [4, 4, 4], "to": [12, 12, 12],
            "faces": {d: {"uv": [2, 2, 14, 14], "texture": "#all"} for d in ["north", "south", "east", "west", "up", "down"]},
            "shade": False
        }]})
    w(f"{A}/models/item/{lamp}.json", {"parent": f"interstice:block/{lamp}"})

# flood lamp: dispenser-style vertical orientation for all six facings
w(f"{A}/models/block/flood_lamp.json", {"parent": "minecraft:block/orientable_vertical",
                                         "textures": {"front": "interstice:block/flood_lamp_front", "side": "interstice:block/flood_lamp_side"}})
rot = {"up": {}, "down": {"x": 180}, "north": {"x": 90}, "south": {"x": 90, "y": 180}, "west": {"x": 90, "y": 270}, "east": {"x": 90, "y": 90}}
w(f"{A}/blockstates/flood_lamp.json", {"variants": {f"facing={k}": dict({"model": "interstice:block/flood_lamp"}, **v) for k, v in rot.items()}})
w(f"{A}/models/item/flood_lamp.json", {"parent": "interstice:block/flood_lamp"})

w(f"{A}/models/item/adherent_spawn_egg.json", {"parent": "minecraft:item/template_spawn_egg"})

# particles
w(f"{C}/particles/falling_dust.json", {"textures": [f"interstice:dust_{i}" for i in range(4)]})
w(f"{C}/particles/mote.json", {"textures": ["interstice:mote_0"]})

# ---------------------------------------------------------------- sounds.json
S = {}


def snd(name, files, distance=None, stream=False, subtitle=None):
    entries = []
    for f in files:
        e = {"name": f"interstice:{f}"}
        if distance:
            e["attenuation_distance"] = distance
        if stream:
            e["stream"] = True
        entries.append(e)
    d = {"sounds": entries}
    if subtitle:
        d["subtitle"] = subtitle
    S[name] = d


snd("ambient.drone", ["ambient/drone"], stream=True)
snd("ambient.air", ["ambient/air"], stream=True)
snd("ambient.hum", ["ambient/hum"], stream=True)
snd("env.impact_far", [f"env/impact_far_{i}" for i in range(1, 4)], 256, subtitle="subtitles.interstice.impact_far")
snd("env.chain_groan", [f"env/chain_groan_{i}" for i in range(1, 4)], 200, subtitle="subtitles.interstice.chain_groan")
snd("env.chain_impact", [f"env/chain_impact_{i}" for i in range(1, 3)], 220, subtitle="subtitles.interstice.chain_impact")
snd("env.structure_groan", [f"env/structure_groan_{i}" for i in range(1, 4)], 96, subtitle="subtitles.interstice.structure_groan")
snd("env.rumble", [f"env/rumble_{i}" for i in range(1, 3)], 256, subtitle="subtitles.interstice.rumble")
snd("env.debris", [f"env/debris_{i}" for i in range(1, 4)], 64, subtitle="subtitles.interstice.debris")
snd("env.scuttle_far", [f"env/scuttle_far_{i}" for i in range(1, 4)], 128, subtitle="subtitles.interstice.scuttle")
snd("env.unexplained", [f"env/unexplained_{i}" for i in range(1, 4)], 96, subtitle="subtitles.interstice.unexplained")
snd("env.lamp_fail", ["env/lamp_fail"], 24, subtitle="subtitles.interstice.lamp_fail")
snd("event.quake", ["event/quake"], 256, stream=True, subtitle="subtitles.interstice.quake")
snd("event.horde", ["event/horde"], 200, stream=True, subtitle="subtitles.interstice.horde")
snd("event.premonition", ["event/premonition"], stream=True)
snd("creature.idle", [f"creature/idle_{i}" for i in range(1, 4)], 24, subtitle="subtitles.interstice.creature.idle")
snd("creature.hurt", [f"creature/hurt_{i}" for i in range(1, 3)], 24, subtitle="subtitles.interstice.creature.hurt")
snd("creature.death", ["creature/death"], 32, subtitle="subtitles.interstice.creature.death")
snd("creature.scream", [f"creature/scream_{i}" for i in range(1, 3)], 64, subtitle="subtitles.interstice.creature.scream")
snd("creature.step", [f"creature/step_{i}" for i in range(1, 5)], 16)
snd("creature.alert", ["creature/alert"], 40, subtitle="subtitles.interstice.creature.alert")
snd("creature.land", ["creature/land"], 48, subtitle="subtitles.interstice.creature.land")
snd("creature.sniff", [f"creature/sniff_{i}" for i in range(1, 3)], 20, subtitle="subtitles.interstice.creature.sniff")
VOICE = {"hide": "hide.", "dont_move": "don't move.", "not_yet": "not yet.", "go": "go. now.",
         "they_hear": "they can hear you.", "look_up": "look up.", "closer": "closer.", "stay": "stay where you are."}
for line in VOICE:
    snd(f"voice.{line}", [f"voice/{line}"])  # no vanilla subtitle: the mod draws its own, optional one
w(f"{A}/sounds.json", S)

# --------------------------------------------------------------------- lang
lang = {
    "block.interstice.weathered_concrete": "Weathered Concrete",
    "block.interstice.concrete_panel": "Concrete Panel",
    "block.interstice.stained_concrete": "Stained Concrete",
    "block.interstice.cracked_concrete": "Cracked Concrete",
    "block.interstice.concrete_floor": "Concrete Floor",
    "block.interstice.concrete_rubble": "Concrete Rubble",
    "block.interstice.rusted_plate": "Rusted Plate",
    "block.interstice.dark_metal": "Dark Metal",
    "block.interstice.metal_grate": "Metal Grate",
    "block.interstice.rusted_pipe": "Rusted Pipe",
    "block.interstice.industrial_lamp": "Industrial Lamp",
    "block.interstice.sodium_lamp": "Sodium Lamp",
    "block.interstice.emergency_lamp": "Emergency Lamp",
    "block.interstice.flood_lamp": "Flood Lamp",
    "entity.interstice.adherent": "Adherent",
    "item.interstice.adherent_spawn_egg": "Adherent Spawn Egg",
    "key.interstice.settings": "Interstice settings",
    "key.categories.interstice": "The Interstice",
    "subtitles.interstice.impact_far": "Distant metal impact",
    "subtitles.interstice.chain_groan": "Chain grinds",
    "subtitles.interstice.chain_impact": "Chain strikes",
    "subtitles.interstice.structure_groan": "Structure groans",
    "subtitles.interstice.rumble": "Deep rumbling",
    "subtitles.interstice.debris": "Debris falls",
    "subtitles.interstice.scuttle": "Something moves on the wall",
    "subtitles.interstice.unexplained": "Something",
    "subtitles.interstice.lamp_fail": "Lamp fails",
    "subtitles.interstice.quake": "The walls tremble",
    "subtitles.interstice.horde": "A great many things approach",
    "subtitles.interstice.creature.idle": "Adherent clicks",
    "subtitles.interstice.creature.hurt": "Adherent hurts",
    "subtitles.interstice.creature.death": "Adherent dies",
    "subtitles.interstice.creature.scream": "Adherent falls",
    "subtitles.interstice.creature.alert": "Adherent notices you",
    "subtitles.interstice.creature.land": "Something hits the ground",
    "subtitles.interstice.creature.sniff": "Adherent listens",
}
for k, v in VOICE.items():
    lang[f"subtitles.interstice.voice.{k}"] = v
w(f"{A}/lang/en_us.json", lang)
print("json assets written")
