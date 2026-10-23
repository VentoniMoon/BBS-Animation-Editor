# CAMERA 3.0 — offline render implementation roadmap

Status: **export foundation only**. This document is a work plan, not a
statement that the editor already exports its scenes.

## Existing, verified ownership boundaries (do not break)

- Minecraft renders the actual world and OptiFine shader pipeline once.
- `BbsFsWorldCameraDriver` sets the world camera before native rendering.
- `BbsFsWorldFrameCapture` copies the *clean pre-editor-GUI* world color/depth
  from the Minecraft framebuffer.
- `BbsFsPreviewTarget` contains the composite preview at *display* resolution.
- `CameraFilmCompositor` draws Image/Video/Subtitle in the film rectangle.
- `CameraOutputSettings` holds the requested output width and height.
- `CameraClipDocument` and `EditorTimeline` represent a 20 TPS film scene.
- `CameraAudioClips` uses Java Sound for **preview**, not export.
- `CameraVideoFrames` asynchronously requests one FFmpeg-decoded frame
  per 20 TPS tick and may reuse a prior frame during preview. This is NOT
  acceptable as an exact offline-export decoder.
- `JoltPhysicsOfflineRunner` and `BodyPartPhysicsBakeController` already
  enforce tick-by-tick input ownership. Never rerun Jolt arbitrarily per
  60 FPS output frame.

## Night work breakdown / small reviewable PRs

### 1. Offline export foundation (this PR)

- `CameraExportPlan`: immutable width/height/FPS/timeline duration and exact
  mapping `videoFrame -> sceneTick + subTick`, independent of wall time.
- `CameraFfmpegEncoder`: external FFmpeg/ffmpeg.exe via direct ProcessBuilder
  argv, no shell; accepts bottom-left-origin RGBA buffers via stdin and
  creates H.264 / yuv420p MP4. No PNG temp sequence is required for MP4.
- Validate user output path, even dimensions, FPS, CRF, allowed x264 presets,
  frame byte length, completion, cancellation and resource cleanup.
- Java 8 smoke tests run without Forge/OpenGL and optionally verify an
  actual encoded MP4 when FFmpeg is available.

This first PR deliberately **does not** add a GUI Export button, change
Minecraft rendering, render camera actors, or claim real 1080p/4K exports.
The encoder is ready to receive clean frames when the renderer is implemented.

### 2. Export Session + exact frame ownership

The initial frame-queue half is implemented in this branch:
- `CameraExportSession` has a bounded two-frame queue and separate FFmpeg
  writer thread, exact monotonically increasing output frame indices,
  error propagation, progress and cancellation. Its `openFfmpeg()` entry
  connects the worker to `CameraFfmpegEncoder`.
- `CameraExportReadback` only accepts an exact native-resolution framebuffer
  region; it restores GL read bindings, read-buffer state and pixel packing.
- `CameraExportFrameBridge` requests an explicit timestamp, then allows a
  GL-thread `glReadPixels` + queue submission *only* for that same frame.
  The existing GUI-sized framebuffer fails with an explicit mismatch instead
  of being scaled to match the requested resolution.

Remaining work:
- Introduce a game-facing render driver that snapshots playback,
  selected scene, camera, view entity, GUI/HUD state and output settings.
- The export loop must *request* one output timestamp and *wait* for one
  completed native world/actor/clip frame. No GPU calls on worker threads.
- Provide Start / Cancel / Finish / Failed with accurate progress and
  explicit errors. Persist no editor state or partially written MP4 after
  cancellation, always restore Minecraft view/timer/GL ownership.
- Sampling at 30/60 FPS is genuinely fractional: do not duplicate 20 TPS
  frames. Interpolate camera, actor poses and baked Jolt results only when
  the needed samples are available. Any unsupported effect fails visibly.
- Test short scenes, scene endings, pause/seek, duplicate renders,
  cancellation and exception-driven cleanup.

### 3. Native high-resolution offscreen film render

**Highest risk / next experiment before promising output quality.**

Direct bytecode inspection of the user-supplied
`bbs-fs-2.8-1.12.2-alpha.1.jar` identifies the relevant BBS FS mechanism:
`BBSRendering.onWorldRenderBegin(float)` saves
`Minecraft.displayWidth/displayHeight`, the existing client framebuffer
and OpenGL viewport, validates `GL_MAX_TEXTURE_SIZE`, creates/reuses a
dedicated output-size `Framebuffer`, temporarily replaces the render
dimensions, and binds it before the native world render. BBS FS also has
`BBSRendering.renderFramebuffer(Framebuffer)` as an interception hook,
`onRenderBeforeScreen()` to restore state before drawing UI, and
`updateTimer(Timer)` to advance exactly `20/FPS` ticks per encoded frame.
Its `WorldVideoExportSession.applyExportTarget()` activates
`setCustomSize(true,width,height)`. Resizing the OS window is optional,
not the fundamental output-resolution mechanism.

**Therefore the next integration must hook Minecraft's native world pass
before the GUI, not re-run `EntityRenderer.renderWorld()` inside GUI.**
The current `BbsFsWorldFrameCapture` copies a finished *display-size*
world image and cannot be an authentic high-resolution export source by
itself. Replacing it with a scaled bitmap would be a regression.
The existing native world capture uses Minecraft's current display
framebuffer; resizing or stretching that frame is not real 1080p/4K output.

- Prototype a *single native world pass* targeting a dedicated
  `Framebuffer(outputWidth, outputHeight)`, and verify OptiFine's shader
  chain, framebuffer depth, native actors and fog still render correctly.
- Keep GUI coordinate system entirely separate from the offscreen image.
- Render world/actors then composite Image/Video/Subtitle directly into
  that target in output pixel coordinates, with no gizmos, selection border,
  icons or timeline in the finished texture.
- Read pixels using `glReadPixels` on the render thread; restore FBO
  read/draw bindings, viewport, scissor, color/depth mask and shader program
  even when errors occur. Pixel-buffer objects / double-buffer staging can
  be considered only after validating correctness.
- Test odd window sizes, GUI scale >1, Full HD, vertical 9:16,
  4K (only if hardware supports target), OptiFine enabled/disabled, and
  shaderpack variants. Handle unsupported FBO sizes explicitly.

### 4. Deterministic clip media and sound

- Export mode must decode each active Video overlay at the exact output
  timestamp; no asynchronous old-frame fallback. Video decoding cannot
  block the main rendering thread indefinitely.
- Offline 48 kHz stereo WAV mixing (per-clip offsets, trimming, volumes
  and envelopes), plus any supported Video clip audio, is evaluated from
  scene time, not Java Sound real-time playback.
- Encode sound in a separate deterministic pass or mux afterward
  with FFmpeg AAC; ensure missing audio files produce actionable errors.
- Check first/last media frames, clips with offsets, fades, short loops,
  overlap, transparent overlays and subtitles.

### 5. Export UI, regression suite and release gate

- Add one minimal Export icon to CAMERA, using the compact style.
- Dialog: output path; selected saved output resolution; FPS;
  H.264 quality (CRF); encoder path (defaults to system PATH or a
  user-selected Windows FFmpeg 7.1 essentials bin); progress/cancel.
- Validate independent resolution/FPS with `ffprobe`, frame count and
  source/target duration. Compare reference frames with SCREENSHOT/preview.
- Full scene regression: Dolly/Path/Keyframe/Shake, multi-actor Blockbuster,
  Emoticons, Chameleon, Body Parts, Jolt/weld, image/video/subtitle,
  vertical crop, one sound clip, OptiFine with and without shaders.
- Do not merge into `main` until CI passes; manual in-game visual tests
  remain mandatory for framebuffer and sound correctness.

## Runtime contract

- `sceneTick=outputFrame*20/fps` rationally (including fractional tick);
  frame count is `ceil(sceneTicks*fps/20)`.
- Encode bytes as **bottom-up RGBA**, length
  `outputWidth*outputHeight*4`; the x264 backend applies one vertical flip.
- The UI and mouse overlays must never be read into the exported image.
- A slow FFmpeg process must delay the NEXT scene frame request rather than
  duplicate/drop a sampled timestamp; the current queue enforces this.
- The render driver MUST NOT reuse the regular editor FBO when its native
  resolution does not exactly match `CameraExportPlan`.
- Before enabling the real export UI, wire a native world-render hook with
  exception-safe restoration, and test the resulting OptiFine world target.
- The original project, source record data, viewport, selected actor,
  camera state and Minecraft game timer are restored even on failure.
- Export is a user-driven blocking job with explicit progress, not an
  always-running background process.
