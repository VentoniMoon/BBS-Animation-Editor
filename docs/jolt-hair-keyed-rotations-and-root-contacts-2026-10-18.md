# Jolt BodyParts keyed rotations and hair collision follow-up (2026-10-18)

## Reports reproduced in the editor

- Both head-hair and body-hair Jolt chains responded to actor world movement,
  but neither responded convincingly to 0°→tilted bone animation keys,
  including uninterrupted playback.
- Front braids passed through added Chameleon chest geometry and the
  animated parent body, despite native collision proxies and overlay
  being visible.
- One braid naturally starts slightly rotated by authored animation.
  **Do not enforce artificial mirror symmetry** or invert Chameleon axes.
- GUI capsule orientation / full mesh coverage still require a Forge
  screenshot or clip; head, shoulders, and arms remain incompletely
  represented by the fitted Emoticons torso proxies.

## Cause 1: Missing animated motor targets

`BodyPartJoltPreviewAdapter.sample()` always provided current clean
`boneWorld[]` to the *pose reconstruction* stage but formerly called
`JoltChameleonPreviewRig.advance(frame,root,parentMatrix)` with only
the root position and the immediate parent's rotation.

The native `JoltHairChain.moveAnchor()` therefore received either world
root translation or the change in its parent's orientation, but missed
the *first driven bone's own authored animation keys*. Intermediate
bone key rotations were also never sent to Jolt joint motors; when a
Jolt world was active, the renderer's additive correction would cancel
the animated Chameleon pose back toward whatever the untargeted physics
world was doing.

### Fix

- Extended native entrypoint:
  `advance(int frame,float[] root,float[] parent,float[][] cleanBoneWorld)`.
  Legacy three-argument entrypoint remains available.
- Root pin target now uses the **relative orientation change of the first
  authored bone** against that bone's own rest pose, not only the parent
  pose. The initial Jolt `-Y` segment orientation stays unchanged.
- For bone indices 1..N-1, compute clean per-bone world rotation deltas
  relative to the native chain's rest pose, convert the relative motion
  into the **actual original twist/plane constraint frame**, and send
  `SwingTwistConstraint.setTargetOrientationCs()` to Jolt.
  This drives angular *motors*, not instantaneous body orientation.
  No rewrite of keys or gravity and no arbitrary per-axis negation.
- The Java 8 preview reflection bridge detects the new entrypoint,
  passes the current bone matrices and warns if the locally installed
  `jolt-hair-prototype-java8.jar` is older.
- Optional `-Dbbs.editor.physics.jolt.diagnostic=true` emits
  `[Jolt][BoneDrive]` every fifth adjacent simulation frame:
  `parentStepDegrees`, `rootStepDegrees`,
  `maxInternalStepDegrees`, `rootShift`,
  `animatedJointTargets`. If the render visibly tilts but all rotation
  values remain zero, inspect Emoticons parent matrix capture and
  Chameleon clean matrix sampling before changing Jolt dynamics.

### Native checks

- `JoltAnimatedRootRotationSmoke`: stationary actor, first chain bone
  X:0°→60° while parent stays still. Physical tip changes position.
- `JoltAnimatedInternalBoneSmoke`: stationary actor and root, middle
  chain bone X:0°→55°; the Jolt joint motors move a downstream tip.
- Existing `JoltAnchorRotationSmoke`, travel, inertia, rest-pose and
  multi-strand regressions continue to pass in Java 8 Linux and Windows.

## Cause 2: First dynamic segment was excluded from ALL body-part contacts

Earlier native `JoltHairChain.setAdditionalShapes()` disabled collision
between group 1 (the *first dynamic hair link*) and every added
Chameleon collider. In a short chain that segment is the **only** hair
collision segment: it could never contact the chest.

`setTorsoCollider()` and `setTorsoSegments()` had the same broad
first-link exclusion against the parent Emoticons torso proxies.

### Fix

- Always exclude the **kinematic pin** (group 0) from contacts.
- Exclude the **first dynamic link** only when its initial root is
  actually enclosed by that specific physical shape. This prevents
  Jolt from ejecting hair from its own scalp attachment without
  masking contact against unrelated chest/shoulder regions.
- Both oriented Box/Sphere/Capsule descriptors and fitted parent torso
  capsules use geometric point/primitive checks, not a guessed filename,
  source author, left/right side or model-specific coordinate inversion.
- Subsequent hair segments remain collidable according to Jolt's
  existing native contact filtering. Collider positions, rotations and
  native shape types are unchanged.

### Native checks

- `JoltFirstLinkChestContactSmoke`: a single dynamic hair segment
  intersects an added chest Box below the root, with a measurable
  native reaction.
- `JoltFirstLinkTorsoContactSmoke`: same initial geometry against a
  parent torso capsule, measurable response.
- `JoltOwnRootColliderExclusionSmoke`: scalp Box enclosing the root
  does not kick the first dynamic segment out.
- Existing `JoltMirroredFrontHairNativeSmoke` and
  `JoltAttachedBoxNativeSmoke` still pass.

## Known limitations and required Minecraft visual validation

1. The full Forge/OptiFine client has **not** been compiled or run by
   the headless Java 8 CI. JNI tests verify native mechanics, not
   Metamorph's visible render transforms.
2. The current Preview still cannot replay the exact *historical*
   animated world matrices on a direct seek to a previously unseen frame.
   Its standalone `JoltPhysicsOfflineRunner` does support a strict
   recorded historical source; it is not integrated into render scrubbing.
   **Test 0→20 playback before judging paused frame 20**.
3. World mesh collision with the whole Emoticons parent (head, face,
   arms, legs and shoulders) is **not** implemented; three fitted torso
   capsules are proxies, not a watertight skinned-mesh collider.
4. Collision group root ownership is determined at **native chain
   creation**, not reclassified every time attached geometry moves.
   Reproductions with dynamically changing contact ownership need
   a separate per-shape authoring control / native contact group update.
5. User-authored hair segments can have different initial directions
   and lengths. Equal stiffness does not imply symmetric trajectories.
6. Wrong 90° debug wireframe yaw, visual one-bone hair tip placement and
   frame-to-frame smoothing require comparison with the actual Minecraft
   Preview after this update. Do not alter `Y180` or sign conventions
   based only on line-art projections.

## Suggested in-game regression

Use a copy of the original scene and the updated experimental Jolt Java8
artifact from GitHub Actions. Keep preview=true, exactChildSpace=true,
torsoContacts=true, compoundTorso=true and drawColliders=true.

- **A**: actor held in place; parent head X 0°→45° on frames 0–20.
  Watch head hair plus body strand, from playback at frame 0.
- **B**: actor still; first Chameleon strand bone key X 0°→45°.
  Confirm root pin follows and Jolt's free end lags.
- **C**: actor still; intermediate Chameleon strand bone key X 0°→45°.
  Confirm downstream segments react while upper anchor remains fixed.
- **D**: walking actor with fixed skeleton (previously working).
  Confirm hair inertial sway continues correctly.
- **E**: front hair overlapping external `boobs.geo.json` and fitted
  Emoticons torso. Toggle real body collision ON/OFF; compare actual
  contact behaviour, not only debug colored capsules.
- **F**: rotate character by 90° and inspect wireframes from above:
  actor / hair / torso / chest should remain in a single world frame.
- **G**: verify pause/scrub/replay and Character/Pose/Body Parts save
  remain unchanged, then log `[Jolt][BoneDrive]`,
  `[Jolt][ContactProbe]`, `[Jolt][ChainInit]` if a visual bug persists.

Native tests need to be re-run with the new Java8 prototype artifact,
and the whole-Forge build/client must still be verified by the user.
