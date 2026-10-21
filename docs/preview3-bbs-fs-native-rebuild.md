# Preview 3.0 – BBS FS-style native world rendering

> Work-in-progress, **not merged**. The real Minecraft/OptiFine world frame,
> camera state and shaders are authoritative. Do not resurrect the hand-built
> `PreviewWorldRenderer`/sky/fog/terrain pipeline as the default.

## Source architecture

Reference artifact: `bbs-fs-2.8-1.12.2-alpha.1.jar` (BBS FS for MC 1.12.2).

Verified from jar bytecode:

- `mchorse.bbs_mod.forge.camera.ForgeCameraHandler.renderTick` installs a
  detached camera entity at `RenderTickEvent.START`; stores and restores the
  prior Minecraft view and third-person setting; applies camera during world
  render, not after it.
- Its camera entity explicitly synchronizes `chunkCoordX/Y/Z` and has
  `getEyeHeight() == 0`.
- `mchorse.bbs_mod.client.BBSRendering.onWorldRenderBegin` manages a separate
  framebuffer when rendering at a custom resolution; it never does a nested
  `EntityRenderer.renderWorld` from a GUI.
- `BBSRendering.onRenderBeforeScreen` copies the completed native framebuffer
  to a texture with `GL30.glBlitFramebuffer`, restores read/draw framebuffers
  and scissor state, then returns to the normal game rendering.
- `UIFilmPreview` displays that completed texture inside its editor.

This implementation **adapts** those principles for our standalone Forge
editor; it does not bundle or depend on the BBS FS JAR and does not copy its
bytecode transformer. In our case the first statement in
`AnimationEditorScreen.drawScreen` is the before-editor-GUI capture point.

## Current call order

1. Minecraft RenderTick.START:
   `BbsFsWorldCameraDriver` installs and updates `PreviewCameraEntity`,
   resets projection capture and temporarily hides Minecraft's HUD.
2. Vanilla/OptiFine draws its **one normal world frame** using that camera.
   Forge `RenderWorldLastEvent` calls `BbsFsWorldProjection.capture` to
   preserve the actual near/far/FOV projection used to write world depth.
3. First line of `AnimationEditorScreen.drawScreen`:
   `BbsFsWorldFrameCapture.beginFrame` copies the clean world frame to a
   persistent snapshot FBO **before** any dark panels, text or controls.
4. `PreviewSceneRenderCoordinator.draw` calls
   `EditorSceneViewport.draw` → `BbsFsPreviewTarget.begin` →
   `BbsFsWorldFrameCapture.copyWorld`: crops the native color/depth to the
   film viewport. No `MinecraftWorldPreview.draw` and no shader re-entry.
5. Selected/background actor renderers and editor gizmo use the same native
   projection corrected for the crop (fallback camera perspective if not
   available), drawing into the full-size editor FBO with a film scissor.
   Body Parts/Jolt/Weld and actor saving logic remain otherwise unchanged.
6. `BbsFsPreviewTarget.end` restores render program, framebuffer, viewport
   and OpenGL state. `BbsFsPreviewTarget.present` shows the film texture.
7. RenderTick.END restores `hideGUI`. Exiting the editor restores the
   original camera, perspective and releases all native preview FBOs.

## Deliberately eliminated from active world render

- `PreviewWorldRenderer.beginSplitRender` and `renderLayer` passes
- `PreviewSkyRenderer` and `PreviewFogRenderer` world reconstruction
- `PreviewWeatherRenderer` re-render
- Experimental `PreviewNativeWorldBridge.renderWorld` recursion from GUI
- `PreviewShaderBridge.beginOptiFineRender` double shader initialization

The old Java classes are retained only for reference/backwards compatibility
during migration; no active scene world render calls into them. The
`MinecraftWorldPreview` instance is now created lazily ONLY if a legacy
caller explicitly asks for it.

## Not yet validated – do NOT merge

No full Forge 1.12.2 runtime is available in the editing environment. Run
on the user's actual mod set with Blockbuster, Emoticons, Chameleon,
OptiFine and Jolt.

- Java 8 Gradle compilation with dependencies and mappings
- FBO color and depth copy, especially OptiFine final shader targets
- Check actor/world depth occlusion: alpha / MSAA / depth-format differences
- Distortion when preview aspect is wider or narrower than display
- Camera position/yaw/pitch/FOV/roll, free/orbit/track modes and chunk culling
- All Body Parts transforms, gizmo axes, Jolt contact overlay/Weld
- Multi-actor state restoration, selected scene Record export/save
- Native world's existing entities vs record-only preview actor duplicates
- Lifecycle: ESC, re-enter, resize window, world change, return from child GUI

## Additional migration implemented (opt-in, not yet visually validated)

The selected and background Blockbuster/Emoticons/Chameleon record actors can
now be rendered directly at the **native** `RenderWorldLastEvent` using
`BbsFsNativeActorPass`, with the actual GL viewport dimensions (not the
display GUI framebuffer size). This route calls the established Body Parts,
Jolt and Weld renderer exactly once and prevents the later GUI compositor
from replaying that actor/Jolt frame. An exception-safe
`BbsFsWorldGlGuard` restores RenderManager viewer position, shader program,
framebuffer bindings, viewport and matrix stack.

This route is controlled by `-Dbbs.editor.preview3.nativeActors=true`
(client JVM; also forwarded from `build.gradle` to the dev client).
**Default remains OFF**: without this switch, the native Minecraft WORLD
is still captured BBS FS-style, while editor actors temporarily use the
working compositing path. Native actors must be visually checked against
OptiFine and all morph types before making the switch permanent.

`BbsFsWorldFrameCapture` also requires confirmation that Forge emitted a
fresh native `RenderWorldLastEvent` in the current tick: skipped world frames
can no longer accidentally capture yesterday's editor GUI. The toolbar
indicator shows `FS` only for a successful native film texture transfer;
`F!` means capture was not available.

Java 8 CI has separate isolated smoke tests for the original scene
coordinator and the new native actor single-frame ownership / GL guard
cleanup. These are NOT substitutes for full Forge compilation or actual
modpack, OptiFine and shader tests.

## Remaining engineering work

- Verify true native entity shader/lightmap lifecycle with enabled native
  actor mode and progressively make it the only actor route.
- Remove dead older renderer classes and stop using the lazy compatibility
  methods only after build and visual checks demonstrate equivalent behavior.
- Verify alpha, translucent world/depth, framebuffer formats, shaderpack
  internal resolution, 4k HiDPI/resize and sky/weather.
- Verify Jolt bake, Weld, Body Parts transforms, gizmo pivots, all model
  types, multi-actor scenes, camera tracks and saved records.
- Do **not** invoke Minecraft `renderWorld` twice or reintroduce split
  sky/chunk/fog passes as a fallback.

## Frame freshness, orientation and GPU-state hardening (2026-10-21)

- Native camera angles are now transformed explicitly via
  `BbsFsCameraConventions`: editor yaw=0 looks toward -Z, Minecraft
  `EntityRenderer` applies `entityYaw + 180`, and pitch/roll are inverted
  to match the actor/gizmo projection. Forge CameraSetup receives all three
  axes, preventing inherited camera-mod rotations. Cardinal axis parity
  (including positive/negative X/Z) is covered by the Java 8 camera test.
- The camera-to-editor frame snapshot is **idempotent** per native world tick.
  Even if another mod calls `AnimationEditorScreen.drawScreen` twice, it
  never overwrites the clean world texture with the newly drawn GUI.
- Color transfer follows BBS FS's linear framebuffer blit; depth uses nearest.
  Failed depth transfers are tracked separately from successful color blits,
  so the small viewport indicator is `FS` for complete color+depth,
  `F~` for color only (uncertain model occlusion), or `F!` for a missing
  native world frame.
- The frame snapshot, actor GL guard and film framebuffer preserve and
  restore OpenGL's active texture unit as well as shader program, framebuffer
  bindings and viewport. This is particularly important when OptiFine or
  Body Parts touches lightmap and normal texture units.
- New Java 8 CI now compiles the actual `BbsFsWorldFrameCapture`,
  `BbsFsPreviewTarget`, `BbsFsWorldProjection` and
  `BbsFsWorldGlGuard` classes against small Minecraft/LWJGL mock APIs.
  It checks frame freshness, color/depth blits, compositor lifecycle,
  GL matrix/texture restoration and projection. This detects Java source
  issues that the earlier camera/actor-only CI could not detect.

These mock-API compile checks are narrower than a complete Forge dev build.
The actual Minecraft classpath, OptiFine interaction and visual occlusion
remain unverified, by design: full modpack tests will follow later.
