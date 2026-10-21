# Preview 3.0 — stages 1–4 (BBS FS-inspired)

This is the first integration slice of Preview 3.0, based on the concepts
seen in **BBS FS 2.8 alpha** (camera state, scene render coordination,
native world rendering, shared editor projections). Our code remains
standalone and compatible with Minecraft Forge 1.12.2/Java 8.

**Important:** the default renderer is preserved until an actual game test
confirms parity with the standard world view. No Jolt, Weld, Body Parts
export, Record or keyframe format is changed here.

## Stage 1 — Camera Core

- `PreviewCameraPose`: immutable world-space eye XYZ, yaw, pitch, roll,
  vertical FOV, near/far planes; shortest-angle interpolation.
- `EditorCamera`: retains existing Orbit movement and Target/Distance API,
  now supports `ORBIT`, `FREE` and `TRACK`, `sample()` and `apply(pose)`.
- `EditorSceneViewport.applyCameraTrack()` switches camera to TRACK and
  blocks manual Orbit/WASD/scroll interference. `releaseCameraTrack()`
  returns to manual Orbit. Auto-focus only runs in Orbit mode.
- No camera-track editing UI yet: that is stage 5.

## Stage 2 — World Render Pipeline

- The existing, known-good split `RenderGlobal` renderer remains **default**.
- `PreviewNativeWorldBridge` adds an **opt-in** experimental path using
  Minecraft's real `EntityRenderer.renderWorld` with the preview FBO and
  a temporary camera entity, restoring framebuffer, FOV and entity in
  `finally`.
- Uses Forge camera events for native FOV/roll. Captures opaque/translucent
  terrain, weather, clouds and vanilla entities in the native path.
- If initialization or rendering throws, native mode disables itself for
  the current session and switches to the old split renderer.
- The native path still needs a real OptiFine/shader-pack and viewport
  aspect/depth/GUI-region test. It must NOT be treated as feature-complete
  or enabled by default.

For a dedicated developer test, add in **local** `build.gradle`, inside
`minecraft { runs { client { ... } } }`:

```groovy
jvmArg '-Dbbs.editor.preview3.nativeWorld=true'
```

Remove it afterwards to return to the default renderer. We deliberately
did not edit tracked Gradle configuration: the user has their own Jolt
settings, and a prior `build.gradle` pull caused a stash conflict.

## Stage 3 — Unified Scene Pass

- `PreviewSceneRenderCoordinator` owns ordered world/actor/overlay passes.
- Background actors render in one shared FBO before the selected actor.
- Render state and global Blockbuster/Emoticons snapshots are restored by
  their respective established controllers. The coordinator closes world
  / preview FBO in `finally` even if an actor renderer throws.
- The background renderer cache is invalidated on scene switches to avoid
  reusing stale EntityActor morph instances.
- Frame 0, play/pause, scene actors and Jolt bake are not rescheduled or
  resimulated by this renderer refactor. The editor's tick remains the
  authoritative time source.

## Stage 4 — Unified Overlays

- `PreviewCameraProjection.project` and `ray` use the same camera
  position/yaw/pitch/roll/FOV and near plane as the real 3D passes.
- `EditorGizmoController` GUI projection and GL perspective/rotation
  use camera optics instead of hard-coded FOV 60 degrees.
- `JoltContactScreenProjection` delegates to the same projector, without
  modifying native contact or physics state.
- The gizmo continues to draw in the selected actor's 3D world pass.
- Existing per-model bone spaces, rotation-axis signs and drag logic
  are preserved; only the camera projection source changed.

## Automated tests and limitations

`.github/workflows/preview3-java8.yml` compiles with Java 8 and checks:

- Orbit/Track pose application, pitch/yaw/roll, FOV, near plane.
- World-to-screen projection and inverse picking rays.
- Camera midpoint rotation across ±180°.
- Selected and background actor pass ordering.
- Exception-safe world pass cleanup.
- Experimental native-world FBO, game camera and FOV restoration on
  normal and deliberately throwing renderer paths (with test stubs).

The original Jolt Java 8 workflow also compiles the shared camera
projection sources and passes both Linux and Windows native smoke tests.

**The full Forge/OptiFine in-game 3D picture cannot be validated by
these isolated tests.** In particular, native FBO viewport cropping,
particle/hand visibility, visual shader equivalence and the original
gizmo hitboxes need real Minecraft tests.

## In-game regression checklist

1. Launch normal `gradlew runClient -PenableJoltExperimental`, unchanged
   local Jolt settings; leave experimental Native mode OFF.
2. Test actor selection and all models (Blockbuster, Emoticons, Chameleon),
   Body Parts and 3D gizmos at FOV 60.
3. Test shared projector with FOV 35 and 90, Roll ±30, and Orbit rotation.
   Verify that gizmo and Jolt contact overlays still coincide with bones.
4. Scrub/pause/play a multi-actor scene. All actors must share the same tick
   without old morph snapshots crossing actor boundaries.
5. Test hair Jolt playback/bake and Weld in ordinary Blockbuster playback.
6. Separately enable `nativeWorld=true` and compare world/sky/transparent
   blocks/weather/depth/shaders, screenshot both results. Disable on any
   discrepancy, and attach `latest.log` with `[Preview3]` lines.

Next: once Native World Preview passes real gameplay parity, build
**stage 5** camera clips/editor and **stage 6** Aperture import/export.
