# Jolt hair-vs-body collisions: upstream comparison

The reference implementation is [Wemppy4/bbs-physics-engine](https://github.com/Wemppy4/bbs-physics-engine) (MIT), particularly:

- `src/client/java/wemppy/bbs_physics/client/collision/CollisionCollector.java`: collects explicitly marked bone/form collision shapes.
- `src/client/java/wemppy/bbs_physics/client/collision/CollisionShapes.java`: converts measured cubic and BOBJ geometry into bone-space primitives.
- `src/client/java/wemppy/bbs_physics/client/scene/BoneRig.java`: kinematic collider per marked bone, updated from the animated render matrices.
- `src/client/java/wemppy/bbs_physics/client/scene/BoneChainRig.java`: Jolt dynamic hair connected to animated bone pins; skips collision against its own attachment.
- `src/client/java/wemppy/bbs_physics/client/scene/ActorCollisionGroup.java`: per-pair native exclusions; avoids two colliders fighting at joints.
- `src/main/java/wemppy/bbs_physics/engine/PhysicsLayers.java`: dynamic hair meets kinematic bones without all bone-to-bone pairs.

This repo targets Forge **Minecraft 1.12.2 / Java 8**, not the upstream Fabric Java 17+ runtime. The design ideas are adapted rather than copying incompatible source code.

## Implemented

1. `JoltHairChain`: one native `GroupFilterTable` per chain. Subgroup zero is the kinematic pin, 1..N are dynamic segments, and N+1..N+3 are the actor's torso colliders. Exclude contact of the attached pin and the first segment against those torso colliders. Downstream hair segments retain real contacts. Existing object-layer filters still prevent hair/hair contacts.
2. `BodyPartJoltMultiTorsoFit`: preserve the animated Emoticons bone axis and width from weighted, CPU-skinned torso geometry. Robustly extend the three capsule zones beyond the two torso pivots if the skinned surface supports it. Keep the validated former fit when the proposed extension has inadequate samples or bad dimensions. Never fabricate an unmeasured collider.
3. The kinematic torso bodies already follow Emoticons animation via `setTorsoSegments` and `moveKinematic`; the Chameleon Y180 transform fix remains untouched.
4. Native and Java8 geometry checks run via `.github/workflows/jolt-java8.yml` on `main`.

## Generic attached Chameleon collision implementation (October 17)

The initial hair prototype was dependent on fixed hair.geo names such as
left_strand_3 or back_strand_4. This has been removed from the live terminal-tip
path. The last bone's tip is measured from the actual decoded Chameleon
quad vertices and cube-local pivot/rotation. The user must still explicitly
enable the relevant physics chain, and the final bone must contain valid
geometry.

New generic attached model collision path:

1. BodyPartChameleonCollisionBoxes collects any passive cube on any bone in
   any added Chameleon model. It excludes animated physics-chain bones to avoid
   creating competing static/dynamic owners.
2. BodyPartJoltBoxGeometry converts decoded Chameleon quads, both cube-level
   pivot/rotation and bone/BodyPart transforms, into one true oriented box.
   It rejects mirrored/sheared and degenerate transforms rather than guessing.
3. After the parent Emoticons and BodyParts are rendered normally,
   BodyPartsPreviewRenderer snapshots all available attached Chameleon parts.
   Passive cubes are supplied to active physics chains, **including passive
   head/scalp cubes belonging to the same attached model as the hair**.
   Driven hair bones are excluded from passive shapes; the pin and first
   physical link are exempted from those contacts to prevent root explosions.
   Each chain's Body collisions switch controls both these boxes and
   the existing torso proxies.
4. JoltHairChain.setAdditionalBoxes uses up to 32 kinematic BoxShape bodies.
   Shapes are created once and moved via moveKinematic at each adjacent tick.
   On topology changes the preview rig is recreated, not silently reused.
5. The accepted native scene exposes these boxes to the GUI overlay, drawn
   as orange wireframe cuboids, and the HUD reports extras N.
6. Java8 native smoke and pure geometry tests cover the supplied rotated,
   two-cube chest model, attached-box contacts, and root hair inertia/recovery.

Currently supported: Chameleon collision-only BodyParts attached to an
Emoticons actor, with optional Jolt hair chains on separate Chameleon parts.
Support for collision mark-up on each bone, alternative actor morph types,
Chameleon chain branching, texture-alpha/triangle collision, multi-actor
contact worlds, and a fully seek-stable inertia/bake cache remains future work.

The existing Jolt root uses moveKinematic and dynamic swing/twist bodies with
spring motors. Consequently it physically exhibits lag and recovery on
adjacent animation ticks without a fake visual rotation. On non-adjacent seeks
the rig is recreated and settled; faithful inertial scrubbing will need a
persistent sequential physics cache.

## Boundaries of this stage

- This is **not** Wemppy's entire Collision tab: the editor now infers Chameleon cube boxes for passive added parts but still lacks per-bone editable collision markup, texture-alpha shapes, and saved presets.
- Our three fitted Emoticons capsules are conservative proxy shapes, **not** triangle-mesh Jolt collisions or a pixel-accurate skin.
- The first link exclusion is a conservative attachment policy; a future per-bone collision group can excuse only the exact source bone and retain other nearby surfaces.
- The revised native `JoltHairChain` requires a fresh `jolt-hair-prototype-java8.jar`. Updating Java classes in the Forge editor alone cannot change code inside the already-built JAR.

## Testing in IntelliJ

1. Pull `main` and verify that `jolt-java8.yml` completed successfully.
2. From its `jolt-java8-experimental-jars` Actions artifact, replace the two bundled libraries in `libs/` (the prototype JAR is mandatory for the new collision-group behavior).
3. Refresh Gradle in IntelliJ and run the existing `runClient` with `-Dbbs.editor.physics.jolt.preview=true -Dbbs.editor.physics.jolt.torsoContacts=true -Dbbs.editor.physics.jolt.compoundTorso=true -Dbbs.editor.physics.jolt.drawColliders=true`.
4. Check unrotated frame 0, inclined frame 20 and a moving torso. The 3 green collision proxies must track the actor; hair strands should fall with world gravity and should no longer be pushed away at their attachments. Inspect side/rear views for shoulder penetration.
5. If contact behavior changes unexpectedly, compare Jolt console diagnostics and visualize centerline/pivot markers. Do not compensate by flipping axis signs or reversing gravity.

## Diagnostics for a live editor test

- Open a parent Emoticons actor with any physical Chameleon hair chain and an
  optional additional Chameleon chest model (e.g. the provided two-cube
  rotated `boobs.geo.json`).
- Enable Jolt preview, torso contacts and collider drawing, refresh the Java 8
  prototype JAR from a successful latest GitHub Actions artifact.
- In the console, inspect `[Jolt][BodyPartBoxes]`: it reports the count of
  recognized passive cube colliders, candidates, rejected cubes, and errors
  such as a missing parent bone matrix.
- In the GUI, inspect `extras N` and the amber box outlines. `extras 0`
  means the Chameleon candidate cubes were not installed in that native
  sample; inspect the diagnostic before altering physics signs.
- Verify both model arrangements: separate front-hair + chest BodyParts, and
  hair chains on a Chameleon head model containing passive scalp cubes.
- Playing frames in order provides Jolt inertia and damped recovery. Arbitrary
  seeks still restart/settle the rig; deterministic seek/replay and baking need
  a sequential frame cache before being called production-ready.
- The CI covers pure Java geometry and native Java 8 contact/inertia tests,
  **not** compilation of the whole Forge editor or a Minecraft render test.

## Diagnosing visually rotated extra BodyPart boxes

The GUI's orange outline was initially drawn for **all** accepted passive
Chameleon cubes. Because its debug pass disables depth testing, boxes behind
the actor can appear through the head/chest; these are not necessarily the
colliders of the inspected model. It also repeated identical passive boxes
for each hair-owning BodyPart in a multi-chain scene.

The box conversion has a Java8 regression comparing all eight Jolt OBB
corners with the actual Chameleon stack order
`T(cubePivot) * Rz * Ry * Rx * T(-cubePivot)` under a rotated parent
(`X=-83.23°`) and nested `Y=180°`. Do **not** blindly rotate all boxes
by 90 degrees. A mismatch in the Forge client must be attributed to a
specific source cube/attachment/render state.

To isolate just the two-cube chest model without changing **any native
collision behavior**, append to IntelliJ runClient VM options:

`-Dbbs.editor.physics.jolt.overlayBoxModel=boobs`

The GUI HUD now reports `extras shown/total [boobs]` and labels projected
box centers by source model. The console prints
`[BBS Animation Editor][Jolt][ExtraBox]` for each native box (model name,
center, quaternion, half dimensions). This lets us check whether the apparent
90° rotation comes from the chest's cube-local transform, from a different
model's shapes, or from attachment/world-space matrices. Remove the property
to show all boxes again. This option **filters display only**, not physics.

Compare frames 0 and 20 from the same back/side camera angles. For unresolved
misalignment supply `[Jolt][BodyPartBoxes]`, `[Jolt][ExtraBox]` and
`[Jolt][GUIOverlay]` lines; screenshot alone does not establish a quaternion
error. The current UI overlay intentionally has no per-pixel depth test.
