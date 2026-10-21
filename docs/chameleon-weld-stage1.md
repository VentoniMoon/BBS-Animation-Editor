# Chameleon Weld — editor Preview + native Blockbuster playback

This implementation uses the original ChameleonRenderer to draw its model
and wraps its `render(Model)` call with a reversible Weld seam adjustment.

## Why the playback fix is different from Editor Preview

Blockbuster Record playback renders attached Chameleon morphs through
`ChameleonMorph.renderModel()` → `ChameleonRenderer.render(Model)`, outside
the editor's `BlockbusterActorPreviewRenderer`. The original Preview-only
Weld consequently did not appear in recorded scene playback.

The editor mod now registers an ASM 5 LaunchClassLoader transformer during
client preInit. It safely renames the original ChameleonRenderer.render(Model)
implementation and creates a wrapper:

1. Call `ChameleonWeldRuntime.begin(model)` **after native animation pose
   evaluation**, just before the original renderer.
2. Render Chameleon using the unmodified underlying renderer.
3. Call `ChameleonWeldRuntime.end()` in a bytecode try/finally, even if the
   renderer throws.

The runtime scopes are thread-local and restore every temporarily adjusted
vertex. Physics state, Jolt colliders, animator keyframes, textures/UVs,
Blockbuster .dat and source geo.json are **not modified**.

If the hook is installed successfully, the editor skips its older Weld pass to
avoid welding the same vertices twice; the native hook covers both paths.

## Model selection

Native Weld is currently **restricted to the exact registered Chameleon key
`hair body`**, matching `chameleon.hair body` in the user's test record.
The model identifier inside the geo.json often says `geometry.steve` and
cannot be used for identifying the attachment.

To add explicitly named models (experimental), set
`-Dbbs.editor.weld.models=hair body;some other model` in the Minecraft JVM.
Do not enable broad discovery for unrelated models; automatic matches are
limited to single-cube parent-child seams.

## Correctly matching all seven hair seams

Chameleon permits UV-omitted faces: its ModelCube.generateQuads will not create
rendered vertices for those untextured faces. The earlier implementation
found just three seams by scanning the existing quad vertices, missing the
left/right strands.

Weld now reconstructs the authored face corners using exactly Chameleon's
origin, size, and inflate formula. It patches existing rendered vertices
only, preserving all texture UVs and omitting untextured faces.

Expected joints for the reference hair body model:

- left_strand_1 → left_strand_2 → left_strand_3 (2 seams)
- right_strand_1 → right_strand_2 → right_strand_3 (2 seams)
- back_strand_1 → back_strand_2 → back_strand_3 → back_strand_4 (3 seams)

## A/B test in the developer client

After merging latest `main`:

```powershell
git pull --ff-only origin main
.\gradlew runClient -PenableJoltExperimental -Dbbs.editor.weld.preview=true
```

Open the editor and compare against **ordinary Blockbuster Record playback in
the world**, for example hairbake_1; both should use the same native Weld.

Useful log markers:

```text
[BBS Editor][Weld] Native ChameleonRenderer.render(Model) hooked
[BBS Editor][Weld][Runtime] Native Chameleon render: 7 matching joints; model=hair body
```

Compare without Weld:

```powershell
.\gradlew runClient -PenableJoltExperimental -Dbbs.editor.weld.preview=false
```

The client Gradle config forwards the existing `bbs.editor.weld.preview`
property to the Minecraft process; the runtime hook respects it. A dedicated
`bbs.editor.weld.runtime` JVM system property takes precedence. In an actual
built mod without a flag, model-scoped runtime Weld defaults to ON.

## Tested and untested

- CI on Java 8 validates 7 authored rest seams, an ASM 5 render wrapper,
  live one-joint posed geometry, hair-only filtering, and exception-safe
  restoration.
- Still requires an in-game test with the user's exact Chameleon 1.2.1,
  Blockbuster 2.7.1 and Jolt-enabled editor.
- This version stitches face corners; it does not yet add curved vertex
  subdivision or smoothed surface normals like the full BBS FS cubic Weld.
- Weld requires the BBS Animation Editor mod on the **rendering client**. A
  .dat record by itself does not serialize a dynamic weld renderer hook.

If the native hook marker does not appear during game startup, check whether
another mod already loaded ChameleonRenderer before our client preInit. The
editor Preview retains its previous Weld fallback when the hook is absent.
