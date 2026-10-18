# Emoticons whole-parent Jolt contacts + tilted-world gravity (2026-10-18)

## User-reported regressions
- Physics chains react to keyed animation and walking, but with the actor/Emoticons parent tilted the visible hair can remain in the parent's orientation instead of falling toward WORLD DOWN.
- Forward braids intersect parts of the *original Emoticons parent mesh* even while colliding with an added Chameleon chest Box.
- Existing three approximate torso capsules cover only a fraction of Emoticons; a rounded capsule also leaves gaps relative to a rectangular torso, and separately overlapping chest collider shapes can push strands sideways.

## Gravity repair
- Native Jolt gravity **remains (0,-9.81×gravity,0)**; no axis sign was changed.
- Root Jolt SwingTwist motor had the same high-frequency stiffness as interior joints. It kept a tilted chain close to the parent's LOCAL orientation even though gravity acts in world space.
- Reduce *only* the first motor's spring frequency multiplier to 0.35, retaining key-driven angular impulses and normal authored stiffness on downstream joints.
- Added two **real Jolt JNI** regressions, `JoltWorldGravityTiltSmoke` and `JoltVisualGravityBridgeSmoke`, on Linux and Windows Java 8:
  - At a 75° head/body tilt and strength 0.55, physical initial link world direction improved from Y≈-0.80 to Y≈-0.99 at frame 20, and remained nearly downward after settling.
  - Output quaternion converted to a local Chameleon swing followed by animated Emoticons parent orientation retains world-down gravity in the pure JNI mathematical test.
  - Root-key and internal-bone rotation tests still respond, with measured **nonzero** displacement.
- **Limit**: automated tests do not render Chameleon with Forge/OptiFine. If hair still visually follows the tilted body, compare native `[Jolt][Compare]` and rendered directions using `-Dbbs.editor.physics.jolt.diagnostic=true` before changing transform signs.

## New complete Emoticons PARENT colliders
- `EmoticonsSkinCollisionGroups` already classifies CPU-skinned triangles by their **Emoticons bone influences**: HEAD, TORSO, LEFT_ARM, RIGHT_ARM, LEFT_LEG, RIGHT_LEG and OTHER.
- `BodyPartEmoticonsFullBodyColliders` now builds additional, native Jolt kinematic BOX volumes from **all available groups**, not only the parent bone to which a hair BodyPart is attached.
- Segment elongated groups into short forms based on skinned triangle vertices to reduce bounding-box empty space. No guessed 1.8-block player template or fixed Minecraft pose dimensions.
- `EmoticonsSkinnedBodyHull` fits their fixed Box dimensions in the representative Emoticons bone's local frame, then repositions/rotates them with the REAL **last-rendered native bone matrix** every tick (and full actor-world transform).
- The cached reference shapes are per rendered actor, preserved across animation frames and reset when preview is cleared or mesh topology changes. This prevents axis-aligned bounding-box shape changes from resetting the Jolt scene each time an arm swings.
- This is still a **bone-driven skinned-mesh approximation**, NOT a watertight triangle-mesh collider or exact per-vertex deformation. It deliberately excludes invalid/degenerate regions instead of silently inventing large volumes.
- The old independent three-capsule torso contacts are disabled *for a chain* when new TORSO hulls exist, avoiding duplicates (two overlapping parent contact systems).
- User-added Chameleon BodyPart shapes (e.g. `boobs`) are preserved and prioritized within the 32-native-shape budget.
- Existing first-link own-root collision exclusion still applies individually: no self-push-out from the source scalp collider, but free front strands can collide with external body parts.
- New control: `-Dbbs.editor.physics.jolt.fullParentColliders=false` disables this feature (legacy three-torso-capsule A/B test). It defaults to **true** with `preview=true` and `torsoContacts=true`.
- Active group/box diagnostic: `[BBS Animation Editor][Jolt][ParentSkin] groups=… triangles=… shapes=…`.
- The user's existing `-Dbbs.editor.physics.jolt.overlayBoxModel=boobs` hides parent **wireframe** boxes by design. Remove this filter to see ALL Emoticons colliders; it does not affect actual Jolt contacts.
- The external Blender 2.79 `emoticons_rig.blend` file confirms the presence of bones with names including `anchor`, `body`, `low_body`, and `head`. Actual collider geometries are generated from renderer-skinned Emoticons meshes rather than assuming the Blender rest mesh is identical to the live morph.

## CI coverage and in-client caveats
- Java 8 headless `EmoticonsFullBodyHullSmoke` exercises the mesh region segmentation (all six humanoid groups), rotated bone-local Box transforms and invariance of dimensions while bones rotate.
- JNI tests verify that hair gravity is world-relative and local swing math remains consistent after an animated tilt.
- **No complete Forge/OptiFine Minecraft client compile or visual acceptance test is covered by these CI jobs.**
- Actual attached mesh and new hulls must be checked together in Preview for each Emoticons model variation.
- To isolate a possible parent-skin geometry problem, compare identical walks from frame 0 with `fullParentColliders=true` versus `false`; do not change hair stiffness or geometry at the same time.
- Existing `[Jolt][NativeContactProbe]` diagnoses actual SOLVED hair segments vs Chameleon passive Box colliders; it does not yet compute full contact manifolds for all new Emoticons parent boxes.
- Baking ragdoll/whole-world contacts into a record is not part of this change.

## A/B tests requested
1. **Body gravity without collisions**: actor fixed, key the parent/head from 0° to 75° over frames 0–20, hold till frame 60. Both front hair and head hair should lag and then hang toward global DOWN. Start timeline from 0.
2. **Gravity with contacts**: repeat same animation with full-parent collisions ON. Check whether contact normals redirect hair as expected, without snap/teleport.
3. **Whole parent**: turn on collider overlay without `overlayBoxModel=boobs`; inspect head, chest, arms and legs while walking. Check whether their boxes follow animations.
4. **Front braids / chest**: compare original `boobs` added Box at Shape scale 1.25 with full-parent ON/OFF. Both must stay collidable and native Jolt should not repeatedly print `[Jolt][ChainInit]` on consecutive frames.
5. **Regression**: validate native head movement and bone keys, pause/seek, Emoticons walking/jump, Chameleon bodyparts and .dat persistence.

Log shortcut:
```powershell
Select-String -Path '.\\run\\logs\\latest.log' -SimpleMatch '[Jolt][ParentSkin]','[Jolt][ChainInit]','[Jolt][BoneDrive]','[Jolt][Compare]','[Jolt][NativeContactProbe]' | Select-Object -Last 100
```
