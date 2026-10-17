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

## Boundaries of this stage

- This is **not** Wemppy's entire Collision tab: the editor does not yet provide arbitrary per-bone manual primitive markup, texture-alpha shapes, shoulder/head markup, or saved presets.
- Our three fitted Emoticons capsules are conservative proxy shapes, **not** triangle-mesh Jolt collisions or a pixel-accurate skin.
- The first link exclusion is a conservative attachment policy; a future per-bone collision group can excuse only the exact source bone and retain other nearby surfaces.
- The revised native `JoltHairChain` requires a fresh `jolt-hair-prototype-java8.jar`. Updating Java classes in the Forge editor alone cannot change code inside the already-built JAR.

## Testing in IntelliJ

1. Pull `main` and verify that `jolt-java8.yml` completed successfully.
2. From its `jolt-java8-experimental-jars` Actions artifact, replace the two bundled libraries in `libs/` (the prototype JAR is mandatory for the new collision-group behavior).
3. Refresh Gradle in IntelliJ and run the existing `runClient` with `-Dbbs.editor.physics.jolt.preview=true -Dbbs.editor.physics.jolt.torsoContacts=true -Dbbs.editor.physics.jolt.compoundTorso=true -Dbbs.editor.physics.jolt.drawColliders=true`.
4. Check unrotated frame 0, inclined frame 20 and a moving torso. The 3 green collision proxies must track the actor; hair strands should fall with world gravity and should no longer be pushed away at their attachments. Inspect side/rear views for shoulder penetration.
5. If contact behavior changes unexpectedly, compare Jolt console diagnostics and visualize centerline/pivot markers. Do not compensate by flipping axis signs or reversing gravity.
