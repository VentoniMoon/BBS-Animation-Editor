# Chameleon Weld — per-BodyPart opt-in

Weld now belongs to each **attached Chameleon morph instance**, not to the
model registry key. Only the attachment whose **MODEL → WELD GEOMETRY**
toggle is ON receives seam deformation.

A disabled part follows Chameleon's original renderer exactly, even if
another part uses the same `hair body` geometry in the same scene.

## Workflow in the editor

1. Select the actor and the **Body Parts** attachment row (not an individual
   internal bone keyframe).
2. Expand **MODEL** in the Body Parts inspector.
3. Click **WELD GEOMETRY: OFF** to turn welding ON for that specific
   Chameleon attachment. Toggle back to OFF to disable.
4. Save the editor with Ctrl+S and use the existing **Export** control to
   update the Blockbuster record .dat.
5. Preview and playback now read the same morph-level Weld flag.

Existing editor saves with no `Weld` field load OFF. Existing exported
recordings also load OFF until the corresponding attachment is configured
and exported again. No implicit special treatment of `hair body`.

For now only Chameleon cubic attachments are supported; other morph types
show **WELD: CHAMELEON ONLY** instead of an actionable toggle.

## How the record retains a per-instance Weld choice

`BodyPartModelData` stores `weldEnabled`. The editor's
`BodyPartsPersistence` codec saves the Boolean `Weld` in each
attachment entry of `editor.dat`.

At export time, the flag is set on the specific native `ChameleonMorph`.
A tiny Forge ASM hook extends that morph instance with `bbs$weld` and
preserves the value across:

- `ChameleonMorph.toNBT` (adds optional `BBSWeld=true` tag)
- `ChameleonMorph.fromNBT` (missing tag = false)
- `ChameleonMorph.copy`
- `ChameleonMorph.canMerge` (on successful merge)

That means Metamorph morph loading, transition/copy operations, Blockbuster
records, and editor re-import continue to agree on which attachment needs
welding. Nothing is stored in the shared model cache.

The morph render wrapper supplies the **current morph instance** on a
thread-local render stack. The existing `ChameleonRenderer.render(Model)`
hook welds only when that instance's Boolean is true, immediately restores
vertices afterward, and preserves nested body parts.

## Geometry behavior

Weld matches parent DOWN and child UP faces using the cubes' actual
origin/size/inflate coordinates rather than relying on the presence of UVs.
This supports the seven known joints of the user's `hair body` model,
including the side strands whose cubes omit face textures.

UVs, keyframe angles, Jolt physics, collision settings and authored
geo.json geometry are not changed.

This is the first seam-corner implementation. Smoothed normals, subdivided
bends, cube rotations and non-Chameleon morphs require further development.

## Developer-client testing

Local Jolt jars and existing local Gradle settings can stay untouched:

```powershell
git pull --ff-only origin main
.\gradlew runClient -PenableJoltExperimental
```

The previous optional `-Dbbs.editor.weld.preview=true` flag is no
longer required: Weld defaults OFF **per attachment**, while the runtime
support is available. To disable all runtime welding for diagnostics, put
`jvmArg '-Dbbs.editor.weld.runtime=false'` in the local
`minecraft.runs.client` Gradle block.

Useful startup markers:

```text
[BBS Editor][Weld] ChameleonMorph per-part NBT and render context hooked
[BBS Editor][Weld] Native ChameleonRenderer.render(Model) hooked
```

Useful active-instance marker:

```text
[BBS Editor][Weld][Runtime] Per-morph Weld active: 7 matching joints
```

The message appears once per session. It indicates that at least one
attachment was welded, not that all Chameleon instances are welded.

## Verification and scope

Java 8 tests cover seven rest-face pairs, two instances with opposite
Weld settings on identical model geometry, NBT roundtrip, morph copy,
successful merge, render context nesting, and vertex restoration after
a simulated renderer exception.

**Not yet verified in the user's Forge development client.** Please test
one Weld ON and one Weld OFF model in one scene, reopen the editor and
re-export before checking normal Blockbuster playback.

The BBS Animation Editor mod with the Chameleon hooks must be installed
in the **rendering client**. A `.dat` record by itself cannot cause an
unmodified Chameleon renderer to weld.
