# Jolt Physics 6.0.0 on Java 8 — BBS Animation Editor experiment

**Status:** the complete upstream Java bindings compile with Java 8 bytecode; the original
native Jolt 6.0.0 ReleaseSp DLL/SO **runs on real Java 8** on **Windows x64 and Linux x64**.
Native physics worlds, SwingTwist joints, curved and nonuniform chains and a
reflection-only preview adapter are tested on real Java 8 in CI. The opt-in
bridge has been wired into the actual Body Parts renderer source, but **the
Forge development client has now been compiled and launched with all three native Jolt hair chains (2026-10-17); the inclined-pose visual result remains under investigation.**

Branches: `main` contains the opt-in Jolt integration; `feature/jolt-java8-port` carries new experiments. The old main is preserved in `backup/main-pre-jolt-20261017`.

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
- Each real bone segment has its own rest length and orientation; authored
  curved and nonuniform Chameleon strands are preserved without flattening.
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
- `JoltJava8BootstrapSmoke` — fail-closed process-level JNI init from configured native file.
- `JoltBoneQuaternionMapperSmoke` — native bone quaternions remain continuous
  through anchor X=50–75° with Y=180°.
- `JoltChameleonPreviewRigSmoke` — original 4×4 Chameleon world matrices, animated parent,
  curved strand and local swing extraction.
- `JoltPreviewBridgeSmoke` — opt-in reflective legacy preview, repeated frames,
  seeking and native scene release.
- `JoltHorstHairFixtureSmoke` — left/right/back geometry measured from the
  supplied hair model, including real unequal lengths, with each chain
  rotated through anchor X=0°→80°→0° (no large physical jumps).

## Minecraft 1.12.2 tilt diagnosis (2026-10-17)

The Forge 1.12.2 dev client has loaded native Jolt Physics 6.0.0 and
all three Horst chains (`left_strand_1`, `right_strand_1`,
`back_strand_1`). In the user's real X-tilt test, the first diagnostic
reported **all seven** tested links diverging by precisely the parent
pose change: **55°, 5°, 5°, 15° and then 100°**, while the timeline
frame remained 20. On each *subsequent* render, these differences
returned to ~0°. This is strong evidence of a **one-render stale
parent/physics sample** when editing a paused pose; it is not evidence
of seven independent Chameleon sign errors.

However, after the final large rotation, `back_strand_1` was reported
as *both* intended and rendered pointing upward with zero error. The
original diagnostic compared only two values **downstream** of the
quaternion bridge, and could not distinguish a genuine native Jolt
upward link from a quaternion-to-bone rest-correction error.

### Direct native rigid-body test (newer JAR required)

The second-generation diagnostic reads the **raw Jolt rigid-body
quaternion**, rotates its physical capsule's local `-Y` axis to world
coordinates, then compares it independently with the quaternion-bridge
prediction and the actual post-render Chameleon next-bone pivot.

In client JVM arguments keep:

```text
-Dbbs.editor.physics.jolt.preview=true
-Dbbs.editor.physics.jolt.diagnostic=true
```

The logger writes a `[BBS Animation Editor][Jolt][Compare]` record per
bone (at most once per second) containing:

```text
SOURCE_CHANGED frame=20 bone=back_strand_1 raw=(...) intended=(...) rendered=(...) bridgeDeg=... errorDeg=... parentChangeDeg=...
```

- `SOURCE_CHANGED`: the actual Emoticons parent bone has changed since
  the frozen Jolt sample was generated. **Ignore that draw's
  physics/render angular mismatch**; the subsequent settled render
  contains the new physical sample. No hardcoded axis sign is changed.
- `BRIDGE_MISMATCH`: raw native Jolt segment and the expected
  post-quaternion-bridge segment differ by 10° or more *without* an
  intervening detected parent edit.
- `WRITEBACK_MISMATCH`: bridge predicts one direction but the actual
  Chameleon segment differs by at least 10°, *without* a detected parent edit.
- `NATIVE_UP`: the raw Jolt segment itself points substantially upward;
  the issue is upstream of the Chameleon renderer, e.g. motor target,
  gravity or the joint swing-cone limit.
- `aligned`: all comparisons are consistent. In an old prototype JAR,
  `raw=unavailable` makes this status weaker: redownload the artifact.

**A newer `jolt-hair-prototype-java8.jar` is required for raw physics
read-back.** An older installed prototype continues functioning, but
logs an explanatory `[Jolt][Raw]` warning and cannot perform this
native-specific comparison.

The experimental Jolt joint has a fixed default 80° cone half-angle.
Unlike the configurable `bend` in Wemppy's
[BoneChainRig](https://github.com/Wemppy4/bbs-physics-engine/blob/master/src/client/java/wemppy/bbs_physics/client/scene/BoneChainRig.java),
a strongly rotated attachment can constrain a segment so it cannot
hang vertically. The new **opt-in** A/B parameter:

```text
-Dbbs.editor.physics.jolt.bendDegrees=170
```

widens the allowed swing. This is not enabled by default and does not
change the old 80° behavior for existing users. Allowed range: 10–175°;
invalid values fail the experimental scene safely, rather than partially
allocating JNI resources.

Suggested A/B: hold frame 20; test X=0/55/60/65/80°; inspect the
**second** diagnostic entry after each pose change. Compare default
80° against opt-in 170° while keeping the same stiffness, damping and
gravity. A separate zero-stiffness run can distinguish spring-motor
restoring torque. Do **not** tune three axes at once.

To inspect the log from the project root:

```powershell
Select-String -Path ".\\run\\logs\\latest.log" -Pattern "Jolt\\]\\[Compare|Jolt\\]\\[Raw"
```

Neither diagnostic writes keys or changes exported `.dat`. There are
still no body collisions in the Jolt path. The real in-game bend result
has not been verified; Linux and Windows native Java 8 tests exercise
the new API and opt-in cone.

## Reproduce the build and download experimental JARs

[CI: Jolt JNI Java 8 compatibility](https://github.com/VentoniMoon/BBS-Animation-Editor/actions/workflows/jolt-java8.yml)

The GitHub Actions workflow checks out upstream `6.0.0` and runs
`tools/prepare_sources.py`, compiles all production files with
`javac --release 8`, switches to the Temurin Java 8 runtime, then
runs native physics tests on Ubuntu and Windows.

On a passing run, artifact `jolt-java8-experimental-jars` contains:
- `jolt-jni-6.0.0-java8.jar`: Java 8 classes rebuilt from upstream + cleaner.
- `jolt-jni-6.0.0-java8-bundled.jar`: the rebuilt Java 8 classes **plus original Windows x64 DLL and Linux x64 SO** from the pinned ReleaseSp artifacts. Resource extraction from this single JAR is verified on both platforms.
- `jolt-hair-prototype-java8.jar`: independent four-link chain implementation.
- `UPSTREAM-LICENSE.txt`: upstream MIT license.

The standalone JNI/prototype JARs are **experimental**; `jolt-jni-6.0.0-java8.jar`
is class-only and requires a matching platform native library. The `bundled`
variant embeds the verified Windows/Linux ReleaseSp binaries and is directly
supported by `JoltJava8Bootstrap`. **None are Forge mods**: they are development
libraries, and placing them alone into `mods/` does not integrate the engine.
The future Forge mod build must carry these resources on its classpath.

## Quaternion bone-pose bridge

`JoltBoneQuaternionMapper` implements the coordinate-space identity

```text
targetBoneLocal = inverse(parentWorld) * simulatedBodyWorld * bodyToBoneRestCorrection
bodyToBoneRestCorrection = inverse(bodyRestWorld) * boneRestWorld
```

The data stays as quaternions (x, y, z, w) through the physics adapter,
avoiding intermediate Euler decomposition and its wraparound. A continuity
helper keeps adjacent frames on the same quaternion hemisphere.
**The legacy Chameleon render-space conversion and final Euler application
remain to be validated in Minecraft.**

## Optional native library bootstrap

`src/main/java/com/example/examplemod/physics/JoltJava8Bootstrap.java`
provides a Java 8 startup entry point:

```java
if (JoltJava8Bootstrap.available(minecraft.mcDataDir)) {
    // Jolt has been loaded and its factory/types initialized.
} else {
    // Continue using the legacy animation physics.
}
```

With `-Dbbs.editor.jolt.native=C:\\path\\to\\joltjni.dll` it loads a
configured file. Otherwise it looks for the 6.0.0 native binary as a
classpath resource, extracts it into `config/bbs-editor/jolt-natives`
using a content-hashed filename, then starts Jolt. Windows/Linux x64 are
supported; unsupported systems return `false` without crashing startup.
The native-resource extraction path (including the single bundled Java 8
JAR), and intentional missing-file fallback, are exercised on real Java 8
under both Windows and Linux in CI. **Native extraction in the Forge development client was confirmed in-game on 2026-10-17.** A native
process abort is not recoverable by Java exception handling.

## Seek / paused edit / Jolt rotation behavior

The opt-in renderer now tracks the origin of each frozen bone swing. Native
Jolt swings are allowed the full shortest-arc angle up to PI; the historical
Verlet output still obeys its 1.20-radian (~68.8°) cap. This is important
around the problematic `anchor X ≈ 60°` region.

When a Jolt scene is created by a seek or on a paused bone edit, it runs **24
fixed physics steps** with the current kinematic anchor, then caches and
publishes the gravity-settled pose. Rendering the same frame again uses the
same output, without stepping physics repeatedly. Changing any clean child
bone matrix or terminal tip while paused now invalidates the cached rig and
initializes a new one. The `JoltPreviewBridgeSmoke` and
`NativePreviewAngleSmoke` tests cover these paths on Java 8.

This is **not** a full simulation of the intervening animated parent frames
on a timeline seek. Reproducible final baking will require replay from a
defined initial frame using the correct historical parent poses.

## Body collision status

`EmoticonsCollisionGeometryProvider.snapshot()` returns CPU-skinned vertices
in **mesh-local** coordinates. `JoltChameleonPreviewRig` consumes bone
transforms in **world** coordinates. These cannot be combined until we have
verified the correct mesh-to-world transform and triangle winding in an
actual Minecraft 1.12.2 preview. For safety, Jolt currently applies no
collisions with the torso, rather than inventing an invisible capsule.
This prevents the old collider from forcing hair upwards but also means
hair may penetrate the body until collision-space tests pass.

## Experimental Forge Body Parts integration (opt-in only)

The normal editor now includes `BodyPartJoltPreviewAdapter`, and
`BodyPartsPreviewRenderer` can route a physical Chameleon strand through
the native Jolt rig instead of the legacy Verlet solver **only when explicitly
enabled**. This preview does not save or bake Jolt values into records and
does not yet collide with the Emoticons torso.

On the [Jolt JNI Java 8 workflow](https://github.com/VentoniMoon/BBS-Animation-Editor/actions/workflows/jolt-java8.yml)
download the **jolt-java8-experimental-jars** ZIP artifact from a successful
run. For Windows, install it automatically with:

```powershell
powershell -ExecutionPolicy Bypass -File jolt-java8-port/tools/prepare-windows-test.ps1 -ArtifactZip "C:\\Downloads\\jolt-java8-experimental-jars.zip" -Build
```

The script validates Java class entries and the Windows DLL before copying
the required JARs. To install without immediately building, omit `-Build`.
Alternatively extract and put these two files in the editor project's
`libs/` directory:

- `jolt-jni-6.0.0-java8-bundled.jar`
- `jolt-hair-prototype-java8.jar`

Build the experimental mod (the vanilla build stays unchanged):

```powershell
.\\gradlew.bat clean build -PenableJoltExperimental
```

Launch the Minecraft 1.12.2 development JVM with:

```text
-Dbbs.editor.physics.jolt.preview=true
```

The renderer uses **real native clean Chameleon 4x4 world matrices** and
returns one preview-only bone-axis-angle correction per simulated bone.
When the Jolt bundle is missing, or a strand is currently unsupported,
it logs the reason and falls back to legacy physics without a fatal
exception. Turn off the opt-in flag to return to the old solver.

**Prototype limitations:**
- Curved, non-uniform strands are supported and the native tests now use
  actual pivot/tip measurements of all three Horst hair chains. Degenerate
  or mirrored bone transforms still reject cleanly and fall back. The
  in-game Horst model now loads Jolt natively; its tilted-pose
  orientation is still being investigated.
- No rigid-body collision shapes assigned to the torso yet. This isolates
  gravity and the 60-degree artifact from mesh penetration.
- The Chameleon pose writer still converts local axis-angle to Euler.
  Legacy Verlet swings clamp to 1.20 radians while native Jolt swings
  can reach PI; in-game rotation validation is in progress.
- Timeline seeks and parameter changes recreate the native rig. The
  existing `BlockbusterActorPreviewRenderer.clear()` calls
  `BodyPartsPreviewRenderer.clear()`, which now disposes Jolt scenes.
- The opt-in development Forge client has now run the native physics.
  GitHub Actions still cannot build a full Forge mod because the repository
  does not include the user's local Blockbuster/Metamorph dependencies.
  A release-ready shaded Forge JAR is not yet verified.

## Remaining before a Forge 1.12.2 integration

1. Verify the shaded release Forge JAR independently of the development
   `runClient` classpath (the latter successfully loaded Jolt in-game).
2. Validate the Chameleon preview bridge on the actual Horst model, including
   in-game quaternion/Euler orientation and transform units. The adapter must match render-space bone
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

**Do not enable this experimental mode by default until in-game behavior is corrected.**
