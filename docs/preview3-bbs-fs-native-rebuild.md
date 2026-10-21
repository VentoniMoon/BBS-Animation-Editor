# Preview 3.0 — BBS FS-style native film viewport

Status: **Draft PR #80**, not merged to `main`. Native actors are now the
only recorded-actor renderer; the custom world implementation was removed.
Minecraft/OptiFine visual rendering is not yet validated on the user's modpack.

## Architecture reference

Reference: user-provided `bbs-fs-2.8-1.12.2-alpha.1.jar` (Minecraft 1.12.2).
Its `ForgeCameraHandler` installs a camera before Minecraft's world render;
`BBSRendering` copies the native framebuffer before drawing the UI; the
`UIFilmPreview` displays that texture.

This editor **adapts** the technique without importing the BBS FS JAR or
its bytecode transformer. It does **not** call `renderWorld()` from GUI
and does **not** render a second sky, fog, terrain or cloud pass.

## Current frame lifecycle

1. `RenderTickEvent.START`: `BbsFsWorldCameraDriver` samples
   `EditorCamera`, attaches a detached `PreviewCameraEntity` to Minecraft
   and suppresses the player's HUD and head bob until render tick end.
   Minecraft camera conventions are mapped by `BbsFsCameraConventions`.
2. Vanilla Minecraft / OptiFine performs the **normal single world frame**.
3. `RenderWorldLastEvent`: `BbsFsWorldProjection` captures the actual
   perspective; `BbsFsNativeActorPass` draws selected and background
   Blockbuster record actors inside this world frame through their existing
   morph, Body Parts, Emoticons, Chameleon, Jolt and Weld code. The
   `BbsFsWorldGlGuard` restores GL state even on failure. A frame's actor
   simulation/rendering is executed **at most once**.
4. The beginning of `AnimationEditorScreen.drawScreen` calls
   `BbsFsWorldFrameCapture.beginFrame`. It snapshots the completed color
   and best-effort depth before any GUI pixel. It cannot recapture the GUI
   on a second `drawScreen` in the same native world tick.
5. `PreviewSceneRenderCoordinator` prepares the film FBO, crops the native
   world frame via `BbsFsWorldFrameCapture.copyWorld`, and draws only the
   GUI-facing 3D gizmo overlay; **no actor is drawn a second time in GUI**.
   `BbsFsPreviewTarget.present` displays the finished texture.
6. `RenderTickEvent.END` restores GUI/bobbing state. Closing the editor
   restores the original Minecraft view/perspective and frees the snapshot
   and film framebuffer.

The active film indicator means: `FS` = color+depth, `F~` = color-only
(depth occlusion uncertain), `F!` = no captured native world frame.

## Obsolete infrastructure removed

The following classes have been deleted, rather than kept as inactive
fallbacks:

- `MinecraftWorldPreview`
- `PreviewWorldRenderer`
- `PreviewSkyRenderer`
- `PreviewCloudRenderer`
- `PreviewFogRenderer`
- `PreviewWeatherRenderer`
- `PreviewNativeWorldBridge`
- `PreviewShaderBridge`
- `PreviewFramebuffer`

`AnimationPreview` still exists **only** as a small scene-space metadata
object for animation controllers. `PreviewRenderBackend` is now a thin
facade over `BbsFsPreviewTarget`.

The old `PreviewNativeWorldBridge` smoke test and its stubs were removed.
All remaining Java8 smoke tests cover native actor single-frame ownership,
scene coordinator cleanup, camera parity, projection, film FBO, GL guard
and the no-recursive-world architecture.

## Full compilation (stage 3)

`.github/workflows/preview3-full-forge-compile.yml` builds the **entire**
editor with `./gradlew -PciDependencies compileJava`, Java 8, Minecraft
1.12.2, Forge 14.23.5.2860 and mapping 20171003-1.12.

`-PciDependencies` resolves the exact public Curse Maven releases used
locally: Blockbuster 2.7.1 (5492194), McLib 2.4.3 (5440933), and Emoticons
1.1.2 (4528666). Metamorph 1.4 and Aperture 1.8.2 continue to come from
the existing project's `libs/`. Without that CI flag, local
`run/mods` paths work exactly as before.

A successful `compileJava` verifies source/API compatibility but does
**not** prove shader lifecycle, in-game physics, or clip playback.
Jolt's optional repackaging via `-PenableJoltExperimental` is separate.

## Awaiting the user's deferred game tests

- Verify native actor rendering for Blockbuster, Emoticons, Chameleon,
  Body Parts/Weld and all chosen morphs; compare with BBS FS and check
  OptiFine shaderpack entity lighting and depth/occlusion.
- Test 3D gizmo axes/pivots, all three transform modes, native camera yaw,
  pitch, roll and FOV, orbit/free/track modes and selection/autofocus.
- Test multi-actor frame ownership, Jolt simulation/contacts, bake,
  textures, sky, translucent blocks and weather.
- Test scene switching, saving/export, ESC/re-enter, framebuffer resizing,
  chunk culling and returning from another GUI.
- If any of these fail, fix the **single native world pipeline**;
  do not resurrect duplicate world or actor rendering.

Once validated, we can build the full Aperture/BBS FS camera editor on top
of this pipeline without another renderer rewrite.
