# Jolt/Chameleon actor-world audit — 2026-10-18

Status: architectural code changes are committed on `main` and Java 8
headless tests are running. Minecraft Forge 1.12.2 render/client validation
**must still be performed locally**. This document does not claim visual
collision correctness before those tests.

## Symptoms from editor tests

1. Hair attached to the head followed the *actor's movement*, but its
   response to local bone animation was weak, whereas long hair attached
   to the body mainly responded to bone animation rather than actor travel.
2. Amber boxes and colored capsules looked rotated about the Y axis
   (roughly 90° from the character silhouette when viewed from above).
3. Two nominally symmetric front strands behaved differently near a
   separately attached `boobs.geo.json` model containing overlapping,
   individually rotated cubes.
4. On adjacent preview frames hair/face looked jerky and a one-bone
   `headwear` strand exhibited only slight wobbling.
5. Non-adjacent timeline seeks did not reproduce physically identical
   trajectories, because Jolt restored/settled the current pose without
   evaluating all intervening animated poses.

## Confirmed architecture error: model-local versus actor-world frame

The Emoticons `BOBJBone.mat` captured after rendering expresses an animated
bone in the model's frame, NOT the full actor-world frame. The actor renderer
subsequently translates the entity and rotates by `Y(360°-renderYawOffset)`.
Our Jolt bridge used the first frame for root pins and dynamic bodies, while
the GUI wireframe added just the actor translation, not the actor's yaw.

Consequences:
- Walking the entity through the scene did not give Jolt a corresponding
  root translation/velocity. A limb's own local rotation DID move the pin,
  accounting for the different response of head and body attachments.
- A rotated actor was drawn in one yaw frame while his debug colliders
  were projected in another. The apparent ~90° rotational offset was a
  plausible outcome, **not** evidence that a capsule's quaternion needed
  an ad-hoc 90° correction.
- Even if the amber wireframes were corrected cosmetically, contacts would
  still be evaluated in a different frame from actual actor translation.

### Code changes

**`BodyPartJoltActorSpace`** reproduces the entity renderer's yaw and
translation. The normal Chameleon child `scale + Y180` remains intact.
The same matrix now transforms:
- every native Jolt hair pivot and the terminal Chameleon cube tip;
- the parent kinematic anchor pose;
- the fitted torso's single or three kinematic capsule endpoints;
- all passive Chameleon BodyPart cube centers **and** cube orientations;
- the renderer's optional predicted-versus-actual diagnostics.

The debug snapshot declares `worldSpace`, so the GUI does not apply actor
translation a second time. This is an architectural correction, not
an X/Z or gravity sign inversion.

The existing 20 Hz Jolt substep behavior, constraints, rest corrections,
root-contact exclusions, and `BodyPartChameleonChildRenderSpace` Y180
configuration were **not changed**.

## Contact asymmetry: instrument first, do not fake symmetry

The two chest cubes in the user-provided geo fixture are not identical
volumes, but they overlap significantly and have a shared rotated-pivot
orientation. Both currently produce kinematic contacts. Depending on
animated root pose and first/second segment position, one long strand may
already be closer to or inside a chest box at frame 0 while the other is
outside. Jolt should not be coerced into equal impulses unless their
*actual geometry* and world poses agree.

A pure Java8 `BodyPartJoltContactProbe` computes signed clearance from
a point to an oriented box in the same actor-world coordinates.
Enable `-Dbbs.editor.physics.jolt.contactProbe=true` for logs every five
frames: `[Jolt][ContactProbe]` reports root, first-link midpoint and
terminal clearances and the nearest source BodyPart. Negative clearance
means the probe point lies inside the OBB, **not** a measured native
contact impulse. Compare left and right with the same actor pose.

Future: a user-authored per-bone collision enable/disable selection and
compound collision shape UI, rather than forcefully including every
decorative cube. Do not merge partially overlapping cubes or disable
contacts globally until the source geometry and author intent are known.

## Playback stability and UI visual debug

`BodyPartPhysicsFrameState` has been adjusted to publish the current Jolt
sample on a **paused** timeline step once it has completed the post-render
physics update, rather than indefinitely applying only the preceding
frame's swing. During normal continuous playback, the frozen previous
sample remains a compatibility path. True fractional-tick interpolation
is still **not implemented**.

`JoltContactGuiOverlay` accepts two display-only filters:
- `-Dbbs.editor.physics.jolt.overlayBoxModel=boobs`: show only the chest
  extra boxes; simulation contacts remain active.
- `-Dbbs.editor.physics.jolt.overlayChain=left_strand_1`: show only that
  physical hair chain **plus body collider context**. Does not alter
  physical simulation.

Diagnostic chain provenance travels with `JoltContactOverlaySnapshot.Shape`.
There is no GUI depth test for the wireframes yet, so lines behind the
head may be visible through it.

## What the supplied video demonstrates (selected frames)

The supplied `Hair physics — BBS FS tips(1).mp4` is about 27m57s long,
30 fps. Reviewed representative frames throughout, with denser frames
around Blockbench hair creation (~6–17 min) and physics adjustment
(~18–25 min), rather than claiming to have manually interpreted every
one of approximately 50,000 individual frames.

- A hairstyle is assembled from **several narrow mesh strips** with a
  distinct pivot/bone structure, not treated as a single solid head block.
  The visible bend follows the *segments*; a single `headwear` cube with
  one dynamic bone can swing but cannot create curvature along its length.
- The demonstration repeatedly changes selected hair bones/strands and
  their physics parameters, tests turns/leans in the BBS FS preview and
  inspects the result. The animation is driven by the model's actual
  parent transforms and the physical chain's spatial lag.
- A source model built this way needs a meaningful anchored first pivot,
  intermediate joints, and terminal tip for a natural multiple-bend
  hairstyle. Merely increasing gravity or swapping X/Z axes is not a
  substitute for those bones.

## Sources reviewed for adapting **concepts**, not copying code

### Wemppy4/bbs-physics-engine (public repository; no declared license)

- `client/collision/CollisionCollector.java`: explicit collidable form
  slots and native matrix-cache path; skip nested dynamics.
- `client/collision/CollisionShapes.java`: rotated cube shapes account
  for the model's frame flip and scale, rather than guessing world axes.
- `client/scene/BoneRig.java`: one kinematic body per selected actor bone,
  each driven by **actorWorld × per-bone matrix**; distinguish teleport
  / reset placement from physical `moveKinematic` steering.
- `client/scene/BoneChainRig.java`: the first physical segment can be
  pinned to a marked parent bone's existing collider or a separate
  kinematic pin; exempt the root/parent collision pair while preserving
  downstream body contacts and physical inertia.
- `client/scene/ActorCollisionGroup.java`: actor-scoped self-collision
  filtering with subgroups.
- `engine/PhysicsTimeline.java` and `PhysicsCache.java`: simulate one
  recorded tick after preparing that tick's animated pose; store world
  rigid transforms for each tick; interpolate the recorded **world**
  quaternions before converting to current render-local pose.
- `client/scene/PhysicsBake.java`: bake the blended pose *as shown in the
  viewport*, not blindly native rigid body coordinates; isolate existing
  keys before writing new ones.

Because `bbs-physics-engine` does not declare an explicit repository
license, the editor implements these principles independently; none of
the upstream Java source is copied wholesale.

### Wemppy4/bbs-fs (MIT license)

- `client/cubic/physics/ModelPhysicsRuntime.java`:
  per-entity state, clean root/model world frames, render-time
  reconciliation, invalidate/checkpoint on authoring operations.
- `client/cubic/physics/ModelPhysicsCache.java`:
  compiled chain paths and per-render epoch caching.
- `client/cubic/physics/PhysicsRig.java`:
  true per-bone directions and rest lengths from authored geometry.
- `client/cubic/physics/PhysicsForces.java`:
  local or world-relative gravity and wind noise models.
- `client/cubic/physics/ModelPhysicsWorldCollisions.java`:
  world block collision/friction and swept bounds.
- `main/cubic/physics/BonePhysicsIO.java` and
  `client/ui/forms/editors/panels/UIModelPhysicsFormPanel.java`:
  persistence and author-controlled physics shape settings.

The FS built-in bone-physics runtime and the separate Jolt addon are
different engines. They share useful architectural ideas, but FS's
Verlet-style per-bone solver should **not** be mixed into our Jolt preview.

## Next architectural passes, NOT claimed done

1. Record sequential Jolt ticks, with animated actor/body/collider matrices
   sampled at each tick. Seek to recorded poses; avoid settling a single
   target pose as if all previous animation had already occurred.
2. Interpolate world-space quaternion poses over fractional timeline time,
   then convert back to the current Chameleon local bone space. This
   removes 20 Hz stepping artifacts without damping out genuine inertia.
3. Add collision markup per bone and explicit owner filtering, then fit
   parent Emoticons head/shoulders/arms in addition to the torso. Currently
   these parent zones are **not fully collidable**.
4. Add configurable terminal-tip direction/bone support for hair caps where
   the lowest-Y cube face does not represent the visually free end.
5. Implement deterministic offline bake into `.dat` *after* exact replay
   and preview parity are verified. This pass must respect existing keys
   and Character/Body Parts timelines.
6. Implement physics undo/cancel checkpoint and settings fingerprints.
7. Optional authored wind, relative gravity, world-block contacts,
   per-body friction and BBS-FS-style debug selection.

## In-editor validation sequence (Forge client, not yet checked by CI)

With the current Java code from `main` and existing Java8 Jolt JNI JARs:

1. With debug overlay enabled, rotate an Emoticons actor by yaw 0, 90,
   180 and 270 and orbit the camera from above. Both hair and chest
   wireframes must remain attached to the *visible* actor. There must
   be no 90° Y displacement. A failure needs world-space matrix
   diagnostics before any axis patch.
2. Animate only the **actor's translation**, leaving head/body bones
   static. Both `headwear` and body strands must now receive inertial
   displacement; test moving and stopping in both X/Z directions.
3. Animate only the **head/parent bones** with actor stationary. Check
   that both chain families respond and their root pivots remain fixed.
4. Test front strands with Body collisions OFF, then ON. Use
   `overlayChain=left_strand_1`, then `right_strand_1`, and optional
   `overlayBoxModel=boobs`. With `contactProbe=true`, compare
   `firstLinkClearance` across frames 0, 10, 20, 30. Do not assume
   equal impulses if their measured distances differ.
5. Scrub paused frames one by one and replay 0–40 continuously. Inspect
   natural inertia, snapping at action transitions, and that
   `[Jolt][ChainInit]` is not repeated hundreds of times at one tick.
6. Verify keyframe editing, interpolation and save/export remain unaffected.

The Java 8 Jolt CI test suite is **not** a substitute for this Forge/OptiFine
preview test, nor for a pixel-aligned render comparison.
