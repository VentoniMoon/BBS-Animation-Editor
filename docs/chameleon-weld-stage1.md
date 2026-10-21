# Experimental Chameleon Weld: hair-body Preview (stage 1)

This first implementation is **not a port of BBS FS's cubic renderer**.
It provides an opt-in, reversible vertex-space Weld for static Chameleon
Body Parts using the same seam-pair idea.

## What the experiment does

- Finds parent/child Chameleon bones that each own exactly one cube.
- Matches the parent's lower face to the child's upper face in the
  *authored rest pose*. Unrelated/offset faces are rejected.
- Uses the exact Chameleon 1.12.2 bone transform order (Z/Y/X rotation,
  pivot/scale, including Chameleon's X-translation convention).
- Resolves matching 3D corners of the two adjacent faces to one common
  seam in model space (default **parentShare = 0.5**).
- Updates every adjacent face vertex at those corners, preserving texture UV.
- Restores original vertex data in a `try-with-resources` after the actor
  is rendered. No Jolt sample, keyframe, Record .dat or geo.json is changed.
- Starts **OFF** by default.

## Preview A/B test

Run the ForgeGradle development client with the following JVM property:

```powershell
git pull origin main
.\gradlew runClient -Dbbs.editor.weld.preview=true
```

Normal control (identical physics/keys, Weld disabled):

```powershell
.\gradlew runClient -Dbbs.editor.weld.preview=false
```

The preview log should report:

```text
[BBS Editor][Weld] Preview seam welding active, 7 matching joints
```

The count may differ when additional single-cube Chameleon attachments
are present. The initial experimental pass skips Chameleon models with
action animations, multi-cube bones and cube-local rotations rather
than touching unsupported geometry.

## Verified rest bindings: `hair body1.geo.json`

| Source bone | Source face | Target bone | Target face |
|---|---|---|---|
| left_strand_1 | DOWN | left_strand_2 | UP |
| left_strand_2 | DOWN | left_strand_3 | UP |
| right_strand_1 | DOWN | right_strand_2 | UP |
| right_strand_2 | DOWN | right_strand_3 | UP |
| back_strand_1 | DOWN | back_strand_2 | UP |
| back_strand_2 | DOWN | back_strand_3 | UP |
| back_strand_3 | DOWN | back_strand_4 | UP |

Every rest-face pair matches in all three axes (including each cube's
inflation). These seams do not require guessed contact positions.

## Deliberate limits

1. **Preview only.** Existing exported `.dat` files and ordinary Blockbuster
   world playback still use Chameleon's default rendering. Export/runtime
   welding needs a separate, safe integration into Chameleon's render phase.
2. This first implementation directly deforms quad corners and doesn't
   subdivide faces or share smoothed vertex normals (unlike the more
   sophisticated BBS FS CubicCubeRenderer Weld).
3. The matching heuristic is limited to coincident, axis-aligned, one-cube
   parent/child DOWN/UP faces. Explicit per-model weld rules and a UI panel
   are planned after the hair test.
4. Disable the property to rule out rendering-only issues; if the test
   produces shading artifacts, pivot offsets or gaps, capture a short video
   and `latest.log` (including `[BBS Editor][Weld]`).

The Jolt simulation, deterministic 20Hz physics bake, collider fitting and
interpolated Chameleon pose export are **not modified by Weld**.
