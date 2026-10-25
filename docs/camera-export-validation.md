# CAMERA Export: real MP4 experimental test and validation

The export pipeline is still experimental. Do not interpret a passing Forge
compile as proof that OptiFine, Jolt or cinematic media renders correctly.

## 0. First CAMERA MP4 with VIDEO clips and the Export dialog

**The second integration stage adds an actual Export Video screen and
frame-exact visual VIDEO clip compositing.** The scene goes from the native
Minecraft world/actor render to the output-resolution FBO, where IMAGE,
VIDEO and SUBTITLE clips are composited in layer order. Raw pixels pass to
FFmpeg's H.264 encoder; FFprobe verifies the resulting MP4. No intermediate
PNG sequence is produced.

### Open the Export Video screen

On Windows, extract FFmpeg 7.1 essentials containing both `ffmpeg.exe` and
`ffprobe.exe`. They should be together in the same `bin` directory.

1. Open CAMERA and click the **fourth mini-icon** beside SHOT / REC / Output
   Format. It shows a small outgoing film arrow. **Ctrl+Shift+F8** opens the
   same dialog; it no longer immediately begins recording.
2. Enter the output width/height (even values), quality (CRF 18/22/26),
   external FFmpeg executable (or `ffmpeg` if in PATH) and MP4 path.
3. Click **START RENDER**. Watch the encoded frame count and the progress
   bar. The dialog remains open while encoding.
4. Click **CANCEL EXPORT** or press **Esc** to cancel. Partial MP4 output is
   deleted and the original editor timeline position restored.
5. On completion use the exact path entered in the dialog. By default this
   is `run/config/bbs-editor/exports/camera-<timestamp>.mp4`.

The export dialog now offers **20, 30 and 60 FPS** H.264 MP4. The film
timestamp is evaluated as `outputFrame * 20 / FPS`, without advancing the
Minecraft world or Jolt solver multiple times at the same 20 TPS tick.
Camera paths, Record actor positions/angles, authored animation bones,
Body Parts keys and VIDEO source timestamps are sampled at fractional time.
Discrete Character actions and vanilla world events remain at 20 TPS.
Jolt remains a **20 TPS solver** with render-only swing interpolation;
high-FPS output must not be interpreted as 60 Hz physical simulation.
Full OptiFine/Jolt visual parity at 30/60 FPS requires a live client test.

Sanity check a 150-tick (7.5-second) scene: 150 frames at 20 FPS, 225
at 30 FPS and 450 at 60 FPS. Verify a moving actor/camera stays smooth
rather than repeating 20 TPS images. Test 60 FPS with hair/Jolt and
a moving VIDEO overlay, then export 20 FPS again to catch regressions.
AUDIO clips now mix into 48 kHz stereo AAC in a separate offline pass. VIDEO
clips use their explicit Audio track when supplied, otherwise their embedded
sound if present; silent VIDEO is valid. The sound pass trims, delays, loops,
applies volume and basic linear fades. Curved BBS FS envelope parity remains
future work. Video pixels are copied into the muxed MP4 without re-encoding.
FFmpeg's selected executable path is remembered in the machine-local
`config/bbs-editor/camera-export.properties` file on closing or starting
the dialog, including between client launches.

### VIDEO clip semantics

- VIDEO time is `(sceneTick - clip.tick + clip.offset) / 20` seconds.
- The FFmpeg decoder runs on a background worker, not in OpenGL. The
  native export loop waits for the exact decoded timestamp of **every**
  active VIDEO layer, rather than showing the preview decoder's earlier
  fallback frame.
- VIDEO obeys the clip layer, normalized x/y/width/height, opacity,
  transform, tint, fullscreen and smooth settings, as IMAGE does.
- With **Loop=true**, FFmpeg repeats the source until the clip ends.
  Without Loop, a clip that outlives its source produces an actionable
  error instead of silently duplicating an outdated video image.
- Missing source files are rejected before encoding starts. A negative
  offset produces transparent video until the source's starting frame.
- Exact frame decoding still creates one FFmpeg process per requested VIDEO
  frame, but now fast-seeks to two seconds before the requested timestamp,
  then decodes the remainder accurately. A pixel-by-pixel FFmpeg regression
  checks that frames match the previous from-start decoder, including loops.
  Reusing a persistent decoder remains a later optimization.

### Functional modifier clips and real 60 FPS procedural time

The nine CAMERA **Modifier** types now have visible defaults and inspector
controls. Creating one switches CAMERA navigation from FREE to SHOT so the
effect can be inspected immediately. Each modifier acts on the camera
result from LOWER tracks at that exact scene time, without modifying
Record data. A clip on layer 0 cannot drag a non-existent lower animation.

- **Translate:** X/Y/Z world offset; optionally `local=On` to use camera
  right/up/forward and rotate offsets with the current camera orientation.
- **Angle:** additive yaw/pitch/roll and optional FOV, per-channel mask.
- **Shake:** shake amount and frequency, deterministic sine or seeded noise;
  local clip time and per-channel toggles.
- **Look:** world-point or actor target, camera-relative offsets and optional
  motion-forward direction (test with a moving Path).
- **Orbit:** pivot from actor, editable block or forward point; distance,
  yaw/pitch and yaw/pitch speed (degrees per scene tick). Works without an
  actor selector and keeps the camera pointed at its pivot.
- **Drag:** seek-safe low-pass follow of lower-layer camera tracks, with
  rate, strength and channel mask; remains reproducible on paused scrubs.
- **Dolly Zoom:** focal distance plus from/to FOV. Increasing focal length
  is counteracted by backward translation to hold an object near focus.
- **Math:** deterministic expressions on x/y/z/yaw/pitch/roll/fov with
  access to lower camera channels and timeline variables; respects mask.
- **Remapper:** map local time to a new source time or keyframe the mapping;
  samples the lower stack at fractional times without integer rounding.

A procedural effect (Orbit/Shake/Math/Drag) now evaluates at the exact
20/30/60 FPS output timestamp. The exporter no longer approximates its
motion by blending the entire modified 20TPS camera pose at adjacent ticks.
Camera activation boundaries remain hard cuts, not smooth crossfades.

**Manual QA:** place Path/Keyframe movement on layer 0 and Translate,
Angle, Shake on layers 1-3. Pause/scrub and verify all combine. Move Shake
off and on; its effect should disappear/reappear. Next test Orbit with
no target and with a named actor, Look at a block, Drag inertia,
Dolly Zoom from 70 to 45 FOV, Math oscillation, and time Remapper.
Check saved/reopened scene, SHOT vs FREE navigation, and 60 FPS native
MP4 against the film preview. Visual parity with every BBS FS legacy
parameter is not guaranteed by Java8 smoke tests.

### BetterLights 1.0.3 integration — preview-only actor light lifecycle

BetterLights 1.0.3.PREVIEW is an **optional** Forge 1.12.2 mod. The installed
Blockbuster 2.7.1 already implements BetterLightsMorph lightcaster creation,
world registration and positional updates. Its source registers a LightCaster
in `BetterLightsMorph.update(EntityLivingBase)` and updates position in
`render(...)`. CAMERA uses a detached editor-only EntityActor that does not
normally tick in the world; drawing the morph alone could show its cone/icon
without the actual registered light.

`CameraBetterLightsBridge` now calls Blockbuster's own LightMorph `update`
once per distinct *logical* scene frame, so the lightcaster can register
without advancing its animation three times when recording 60 FPS. While
the same tick is drawn repeatedly (paused scene or fractional export), the
bridge maintains Blockbuster's already-created dummy's lifespan without
ticking its animation. The compatibility adapter does **not** link directly
against dz.betterlights classes and is inert when `better_lights` isn't
loaded. The ordinary native RenderWorldLast world frame and OptiFine
entity shader path remain unchanged.

Current verified scope: **top-level Blockbuster BetterLightsMorph actors**.
Morphs attached through Body Parts, nested/sequencer morphs, accurate
shadow-map ordering in BetterLights, and first-frame lighting in exact
offline MP4 are **not yet verified** and may require extending lifecycle
ownership beyond preview-only actor preparation. This is a safe first
integration step, not full BetterLights parity.

Live regression with BetterLights 1.0.3 and Blockbuster 2.7.1:
- place a standalone BetterLightsMorph into a recorded actor, verify its
  registered point/spot lighting (not just the debug cone) in SHOT preview;
- pause/seek and resume after several seconds; check no light leaks or
  accumulation when reopening the editor or selecting a different actor;
- export 20 and 60 FPS with a moving light/actor; compare source brightness
  and shadows on the walls and actors with the in-game scene;
- repeat with BetterLights absent: CAMERA must launch/preview/export
  without linkage or class-loading errors.

### Export modal: no duplicate CAMERA interface at native film resolutions

When **START RENDER** succeeds, the setup controls are replaced with one
compact progress window (output size/FPS, stage, captured and encoded frames,
one progress bar and **Cancel Export**). The rest of the editor is covered
with an opaque dark background and is **not redrawn** during export.
The native world/actors/media still advance one exact film timestamp each
render tick, inside CameraNativeFrameProbe.beforeGui(), before GUI drawing.

The native output framebuffer temporarily changes Minecraft.displayWidth
and displayHeight. In Minecraft 1.12.2 the normal overlay/projection may have
been prepared while those were set to the output format (e.g. 3840x2160 or
1080x1920). The editor now restores the original target **and reinitializes
the GUI orthographic projection** before drawing progress. Restoring only
the framebuffer/viewport was not enough: it could show a second tiny/stretched
editor GUI whose shape depended on the film aspect ratio.

Regression in a live client:
- Start 1920x1080/60, 1080x1920/60 and 3840x2160/60 exports with
  a VIDEO clip and camera/actor motion. Expect exactly one centered,
  identically sized progress modal, without timeline or inspector ghosting.
- Observe the captured/encoded counts changing while editor chrome remains
  hidden; verify the final MP4 still contains all overlays and audio.
- Cancel partway and check that no old GUI projection, stretched toolbar,
  scissor rectangle or altered window resolution remains after restoring
  CAMERA. Repeat with OptiFine enabled/disabled and an active shaderpack.
- An export start failure should leave the settings dialog visible and usable.

### True render-time preview sampling (20 TPS, 60 FPS visuals)

The editor timeline and Blockbuster Record remain at 20 logical ticks/sec.
When **playing** (and not recording a physics bake), CAMERA now samples
Minecraft's per-render partial tick and applies the same fractional timestamp
to selected/background Record motion, keyed Pose and Body Parts, and the
visible camera. MP4 export uses the exact output-frame timestamp instead
of assuming Minecraft render partial ticks (which are unrelated to 60 FPS
offline frame order). A 60 FPS video must have distinct visual actor poses
at 20.0, 20.333 and 20.667 scene ticks.

The Record sampler creates temporary render-only poses and does NOT mutate
original .dat frames. A sampled actor never blends its translation/rotation
again in the native morph renderer. The Emoticons Animator advances only
on the original integer Record ticks, but its pose is rendered using the
fractional scene time. Jolt solver/baking retain their fixed 20 TPS stepping
and paused/seeked scene poses use whole ticks.

**Visual QA**: compare the same moving arm, full-body run and background
actor in ordinary Blockbuster playback, CAMERA preview and native MP4 at
20 FPS/60 FPS. Pause and scrub: bones must remain still. Record/bake hair:
no extra Jolt steps, skipped keys or interpolated simulation input. Test a
camera hard-cut at the same time; the cut must stay instantaneous. Repeat
using an Emoticons walking actor and a Chameleon Body Part, including a
portrait 9:16 export. Forge and Java 8 CI do not establish visual parity
of installed client mods, especially shaderpacks.

### Keyframed IMAGE / VIDEO motion in preview and MP4

Both IMAGE and VIDEO now support their own **media transform** keys, independent
of camera position keys. Select a media clip in CAMERA, expand Media Animation
and click **Add media key at playhead**. Edit X/Y, Width/Height, Rotation,
Scale X/Y and Opacity for each key, and choose a per-key interpolation curve.
The keys appear on the clip's timeline and can be dragged, duplicated and
deleted using the same timeline gestures as camera pose keys.

A preview drag with existing keys creates a media key at the current scene
frame when the mouse actually moves, rather than overwriting the base image
transform. For old clips without media keys, the existing static transform
controls continue to work. Media values are saved with the editor scene and
survive restarting the client. Fractional 30/60 FPS output evaluates the
key curve at each output timestamp.

Regression: create two IMAGE or VIDEO keys at frame 0 and 20 with x=0.2
then 0.8, opacity=0 then 1 and rotation 0 then 90. Scrub the preview to
frames 0/10/20 and compare with a 60 FPS export at times 0/0.5/1.0s.
Check click-selection outline follows the animated frame and that a simple
click without dragging does not silently create a key. Verify Ctrl+S and
reopen preserve media key values and interpolation.

### Avoid duplicated audio while testing Lip Sync and CAMERA

A WAV loaded in the normal editor (Character > LIP SYNC) is a **separate
local Java Sound monitoring source** from CAMERA's AUDIO/VIDEO film clips.
Playing both with the same file previously made the audio sound doubled.

- In the normal editor, click **WAV SOUND ON / WAV MUTED** on the waveform
  (available in Character, Pose and Body Parts), or click **WAV MONITOR**
  in Character > LIP SYNC. This immediately mutes/unmutes only local WAV
  monitoring while keeping the file loaded, its waveform and viseme keys.
- The toggle persists in `config/bbs-editor/wav-monitor-muted.txt`
  independently of the scene and the FFmpeg encoder.
- Muted WAV monitoring resumes at the current timeline position when
  enabled; it never resumes from an old audio position.
- During offline MP4 export local WAV monitoring pauses automatically,
  regardless of its toggle. CAMERA AUDIO/VIDEO clips are mixed as before
  by FFmpeg and are never muted by this editor-only preference.
- Regression: use the same WAV in Lip Sync and an AUDIO clip. When WAV
  monitor is off, only the CAMERA clip should be heard during editor
  preview and the final MP4 must still include the soundtrack. Re-enable
  monitor and verify lip sync still works without reloading or rebaking.

### IMAGE overlay aspect-ratio and orientation check

IMAGE clips previously treated Width/Height as independent screen-space
fractions, so an ordinary source was stretched differently in horizontal and
vertical frames. Now both preview and offline MP4 use the same pixel-based
source dimensions and **contain** the original image in that bounding box by
default. Image pixels are uploaded at original resolution (subject to the
existing 4096-pixel maximum per side); any downscaling is only due to the
chosen display size. `fit=stretch` remains an explicit distortion option;
`fit=cover` crops the edges within the box. New and older IMAGE clips
without a fit field use contain.

The IMAGE inspector also exposes independent `flipX` and `flipY` options
to diagnose/correct an explicitly mirrored source without affecting the
world, subtitles or other clips. The default is no added reflection.

Regression: add a recognizable asymmetrical PNG with readable text and
known source dimensions. Confirm its aspect and orientation in a 16:9 preview
and native MP4, then change the output to 1080x1920 and repeat. Compare
image orientation to the ORIGINAL source, not only preview vs MP4.
Check fullscreen/cover, nontrivial rotation, opacity, and old saved scenes.

### Clean film preview: no Minecraft chat or diagnostic HUD

Both the CAMERA preview and the MP4 reuse the native Minecraft world pass.
The film camera now cancels `RenderGameOverlayEvent.Pre` (including ALL)
and the dedicated Forge `RenderGameOverlayEvent.Chat` event while the
editor owns that world render. `hideGUI` alone did not remove chat or
OptiFine's `OpenGL Error: 1282 (Invalid operation)` message.

In a live client, test with a recently printed chat message still visible,
and, when available, a mod/debug message:

1. Open CAMERA / SHOT and verify the world inside the preview frame has no
   game chat, OpenGL error overlay, F3 text or crosshair.
2. Export 20 FPS MP4 and check first/middle/last frames are equally clean.
3. Add a CAMERA SUBTITLE clip and verify it **remains** in preview and MP4;
   cinematic subtitles are composited after native HUD suppression.
4. Close CAMERA and verify Minecraft chat history is intact and ordinary
   game HUD/chat settings resume (without deleting received messages).
5. Check the editor's own panels, export progress/errors and modal text
   remain visible: only the native game overlay is suppressed.

This intentionally hides the chat's **rendering**; it does not fix the
underlying OpenGL error, nor does it block overlays drawn independently by
other mods outside Forge's in-game HUD events.

### Limits that still need live in-game validation

OptiFine world/FBO behavior, real multi-actor and Jolt rendering, complex
world-dependent timing, shaderpacks, subtitle font sizing and the visual
parity of vertical 9:16 clips have not been verified in the actual
Minecraft dev client. No PNG preflight is required to reach the MP4 dialog.
The experimental MP4 should first be visually checked with one simple actor,
a moving camera and a subtitle. Check camera position, output-size pixels,
proper actor movement, GL/FBO restoration and correct length. In particular,
test whether OptiFine shaderpacks respect the temporary native framebuffer.
If any frame is black or stretched, do not merge the branch into main.

---

## 1. Native output-resolution probe (Windows / Forge 1.12.2)

The current CAMERA branch contains an *opt-in* single-frame native-world
probe. It does not produce a video and should not be used for production.

1. Launch the Forge dev client with the explicit diagnostic flag:

   ```powershell
   .\gradlew.bat runClient -Dbbs.editor.export.probe=true
   ```

   The client configuration forwards the flag to Minecraft. Without it,
   no native world framebuffer replacement takes place.
2. Confirm the normal modpack loads as before.
3. Enter a scene and switch the editor to CAMERA / SHOT. Keep the same
   animation frame visible.
4. Choose **1920×1080** in the Output Format popup.
5. Press **Ctrl+Shift+F9**, then inspect
   `run/config/bbs-editor/export-probes/world-1920x1080-*.png`.
6. Test a **1080×1920** portrait frame, then (only on capable GPUs)
   **3840×2160**.
7. Repeat 1080p with OptiFine off, OptiFine on and an active shaderpack.
8. After each attempt confirm the live preview is normal and responsive:
   press Play, move the free camera, switch actor and mode, and reopen a
   scene. No persistent 1080p GL viewport, GUI resizing or altered HUD
   should remain.
9. In the console search for `[BBS Editor][Export Probe]`. Report the
   complete diagnostic line on failure.

**Important:** Without additional flags, this probe captures the
*clean native world pass only*, before the CAMERA GUI. That is intentional:
it proves high-resolution world rendering independently of media overlays.

An additional, still-experimental mode can be enabled for IMAGE/SUBTITLE:

```powershell
.\gradlew.bat -Dbbs.editor.export.probe=true -Dbbs.editor.export.probe.media=true runClient
```

Then repeat **Ctrl+Shift+F9**; results are named
`film-<width>x<height>-*.png`, rather than `world-*.png`.
IMAGE and SUBTITLE are composited in native *output pixels*, independent of
GUI scale, and no mouse selection chrome is included. If an active VIDEO
clip exists, this mode fails visibly rather than exporting a stale frame:
the old async preview decoder is not frame-exact. Audio is not written to
either PNG. Subtitle font sizing and parity with the GUI must still be
checked at 1080p and vertical 9:16 in a real Minecraft client.

A clean native-world image establishes only the high-resolution render
milestone; a film PNG establishes the next static-media layer milestone.

### Acceptance checks

- PNG dimensions equal the selected output format (not the game window).
- Camera FOV and subject framing match the expected 16:9 or 9:16 film view.
- No black frame, stale previous frame, missing actors or partial UI panels.
- Proper color, depth, particles and transparent actors when OptiFine used.
- Running the probe twice yields two separate files.
- Switching back to ordinary CAMERA restores the original game dimensions
  and normal full-window GUI.
- On any failure: no stuck screenshot key, no locked Minecraft FBO and no
  duplicate Jolt simulation.

If these fail, keep the branch unmerged and provide the image/log: do not
mask the problem by upscaling `BbsFsPreviewTarget`.

## 2. FFmpeg/FFprobe preflight on Windows

Use the user's original FFmpeg 7.1 essentials package or a compatible
installation containing both `ffmpeg.exe` and `ffprobe.exe`.

Example (replace the folder with the *actual extracted* bin folder):

```powershell
$bin = "C:\tools\ffmpeg-7.1-essentials_build\bin"
& "$bin\ffmpeg.exe" -version
& "$bin\ffmpeg.exe" -hide_banner -encoders | Select-String "libx264"
& "$bin\ffprobe.exe" -version
```

A later UI will remember the executable path. The current test backend
does not bundle third-party binaries with the Minecraft mod.

## 3. Automated tests in PR #95

- `CameraExportPlan`: 20 TPS timeline to exact 24/30/60 FPS frame time.
- `CameraExportSession`: backpressure, order, progress, cancel, failure.
- `CameraExportReadback`: exact-sized source FBO and GL state restoration.
- `CameraExportFrameBridge`: commits only the requested frame timestamp.
- `CameraFfmpegEncoder`: actual H.264 MP4 output from raw RGBA.
- `CameraExportVerifier`: FFprobe reports the expected resolution, rate
  and decoded output frame count.

CI runs those isolated tests in Java 8, plus a separate full Forge 1.12.2
compile. It cannot exercise Minecraft's real native world renderer or
shaderpacks; the in-game probe above remains mandatory.

## 4. Gate before interactive MP4 export

Do not add an Export UI button or claim 4K until:
- High-resolution native PNG probe passes with and without OptiFine.
- The output FBO is rendered directly, never enlarged from screen pixels.
- Actor pose/physics sampling provides correct fractional time for 60 FPS.
- The compositor paints media *in output pixels*, not scaled GUI units.
- A deterministic audio pass exists, with video clip audio as appropriate.
- A short in-game scene is verified by FFprobe and visually inspected.
