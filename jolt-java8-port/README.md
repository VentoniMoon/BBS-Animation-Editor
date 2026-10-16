# Jolt Physics 6.0.0 on Java 8 — BBS Animation Editor experiment

**Status:** the complete upstream Java bindings compile with Java 8 bytecode; the original
native Jolt 6.0.0 ReleaseSp DLL/SO **runs on real Java 8** on **Windows x64 and Linux x64**.
Native physics worlds, SwingTwist pendulums, and a four-segment hair-chain prototype are
tested in CI. **This is NOT yet connected to Minecraft, Chameleon, or the editor renderer.**

Branches: `feature/jolt-java8-port`. The editor's `main` is intentionally untouched.

## Original sources and licensing

- [Jolt JNI 6.0.0](https://github.com/stephengold/jolt-jni/tree/6.0.0)
  by Stephen Gold, MIT license. The build stages the **exact pinned upstream tag**
  (601 Java sources, including JPMS descriptor), patches two Java 11-only features,
  and produces 600 compilable Java 8 source files after replacing the descriptor.
  The upstream MIT license is copied into the generated output and packaged JAR.
- [Wemppy / bbs-physics-engine](https://github.com/Wemppy4/bbs-physics-engine).
  The standalone `JoltHairChain` takes its general physics design from
  `BoneChainRig`, `ChainRig`, and `PhysicsJoints` (MIT).
  It is **not** a drop-in copy of the Fabric/BBS 1.20 implementation.

## Java 8 compatibility changes

Upstream `build.gradle` specifies `options.release = 11`.
The upstream `JoltPhysicsObject` uses `java.lang.ref.Cleaner` (Java 9+).
`tools/prepare_sources.py` stages the upstream source and changes precisely:

- Remove `module-info.java` (JPMS is unsupported in Java 8).
- Replace `Cleaner` with `Java8Cleaner` (phantom references and a daemon).
- Retain all existing JNI method signatures, classes, and `JoltPhysicsObject` cleanup logic.

The cleaner retains cleanup references until the referent dies and never retains the
referent itself. A cleanup Runnable must **never capture the object being cleaned**.
The regression `JoltOwnerLifecycleSmoke` verifies explicit `close()` twice and the
garbage-collection path invoke each freeing action only once.

## Standalone physics proof of concept

`src/main/java/com/example/examplemod/physics/JoltHairChain.java`:

- One non-colliding kinematic attachment body and 1–64 dynamic capsule segments.
- Actual Jolt `SwingTwistConstraint` joints, with motor springs when stiffness > 0.
- Rest length fixed in physical space, authored orientation is not derived from Euler.
- Root-to-tip spring falloff; independent linear/angular damping.
- Fixed tick of 50ms with three substeps (~60Hz), matching Wemppy's timing.
- Read-back of each native segment's actual position and quaternion.
- `moveAnchor(x, y, z, qx, qy, qz, qw)` for animated parental transforms.
- Explicit body and constraint destruction when closing the simulation.
- **No body collisions yet.** This is intentional: isolate chain stability first.

Tests:
- `Java8CleanerSmoke` — automatic cleanup on actual Java 8.
- `JoltOwnerLifecycleSmoke` — no double-free on manual close / GC.
- `JoltNativeSmoke` — loads original ReleaseSp and registers physical types.
- `JoltChainSmoke` — free swinging segment attached via SwingTwist.
- `JoltHairChainSmoke` — 4-link strand swings with moving anchor, then relaxes.
- `JoltAnchorRotationSmoke` — checks continuous anchor X movement across 55–70°.

## Reproduce the build and download experimental JARs

[CI: Jolt JNI Java 8 compatibility](https://github.com/VentoniMoon/BBS-Animation-Editor/actions/workflows/jolt-java8.yml)

The GitHub Actions workflow checks out upstream `6.0.0` and runs
`tools/prepare_sources.py`, compiles all production files with
`javac --release 8`, switches to the Temurin Java 8 runtime, then
runs native physics tests on Ubuntu and Windows.

On a passing run, artifact `jolt-java8-experimental-jars` contains:
- `jolt-jni-6.0.0-java8.jar`: Java 8 classes rebuilt from upstream + cleaner.
- `jolt-hair-prototype-java8.jar`: independent four-link chain implementation.
- `UPSTREAM-LICENSE.txt`: upstream MIT license.

These are **experimental class-only JARs**. The original platform-specific
ReleaseSp native library must still be loaded (via `System.load` or the future
legacy Forge bootstrap). Do **not** drop these into the editor's mods folder.

## Remaining before a Forge 1.12.2 integration

1. Port the native-library bootstrap from Wemppy's `JoltNatives`, with safe
   platform extraction, version hash, errors reported without crashing Minecraft.
2. Create a Chameleon bone adapter. The adapter must match render-space bone
   matrices and drive/read each *bone quaternion* rather than re-infer angles
   from particle line segments.
3. Represent user-authored Chameleon geometry as physical bodies and optional
   explicitly marked colliders; never force oversized hidden contact capsules.
4. Build one preview scene world per timeline context, handle seeks/resets, and
   verify deterministic playback/baking to Blockbuster `.dat`.
5. Test Horst's three strands on the Emoticons `low_body` bone, including the
   known discontinuity when root/anchor X passes **60 degrees**.
6. Verify native lifecycle under open/close/reopen, repeated actor selection,
   JVM shutdown, and multiple models before enabling by default.

**Do not merge this branch into main until the in-game tests are complete.**
