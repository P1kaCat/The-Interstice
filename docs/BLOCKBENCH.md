# Editing the Adherent in Blockbench

The creature's geometry now lives in **`models/adherent.bbmodel`** (Blockbench *Modded Entity*, box UV,
64×64 skin embedded). The game reads it through generated code:

```
models/adherent.bbmodel  --tools/bbmodel/bbmodel_to_java.py-->  AdherentGeometry.java (generated, do not edit)
```

`AdherentModel` / `AdherentAnimator` animate the bones procedurally, so Blockbench animations are not used.

## Workflow

1. Edit `models/adherent.bbmodel` in Blockbench (or through the Blockbench MCP, below). Save.
2. Regenerate the Java geometry (add `--texture` to also export the skin you painted in Blockbench to
   `textures/entity/adherent.png`):

   ```bash
   python -I tools/bbmodel/bbmodel_to_java.py --texture
   ```

3. Build or run the client:

   ```bash
   ./gradlew runClient
   ```

## Rules the converter enforces

- These bones must exist, with exactly these parents (the animator drives them):
  `body > neck > head > jaw`, `body > chest_l / chest_r`,
  `body > arm_l > fore_l > hand_l`, `body > arm_r > fore_r > hand_r`,
  `body > leg_l > shin_l`, `body > leg_r > shin_r`. Extra bones and cubes are fine.
- Bone names must be unique, lowercase, `a-z 0-9 _`.
- Cubes cannot be rotated on their own (Java entity models can't): rotate a bone instead.
- Box UV only. Keep the texture at 64×64 unless you also change the resolution in Blockbench.

Blockbench's own *Export → Java Entity* targets Fabric **Yarn** names; this mod uses Mojang mappings, which
is why the converter exists. Don't paste Blockbench's exported class into the project.

## Blockbench MCP (so Claude can edit the model)

Two servers from <https://github.com/jasonjgardner/blockbench-mcp-plugin>. Both run code downloaded from
GitHub, so they are not set up automatically; run these yourself in the project folder if you want them.

**Headless** (edits `.bbmodel` files without Blockbench open, can render previews):

```bash
claude mcp add bbmodel --scope project -- cmd /c npx -y github:jasonjgardner/blockbench-mcp-plugin --root "C:/Users/kylli/Desktop/Mod Minecraft/The Interstice/models"
```

**Desktop plugin** (drives the open Blockbench app): in Blockbench, *File → Plugins → Load Plugin from URL*,
paste `https://jasonjgardner.github.io/blockbench-mcp-plugin/mcp.js`, then:

```bash
claude mcp add blockbench --scope project --transport http http://localhost:3000/bb-mcp
```

Start a new Claude Code session afterwards so the servers load. Previews from the headless server's render
tools need Node 23.6+ (this machine has 22.21) and download ~130 MB on first use; editing works without them.

## Seeding

`tools/bbmodel/seed_adherent_bbmodel.py` produced the `.bbmodel` from the original hand-written geometry
(round trip verified exact: 16 bones, 22 cubes). It refuses to overwrite the file unless given `--force`.
