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

Next architectural step after baseline rendering validation:
migrate selected/other recorded actors into a native-world event/pass so their
lighting and shader participation are identical to ordinary Minecraft entities.
Do not attempt that by blindly calling `renderWorld` twice.
