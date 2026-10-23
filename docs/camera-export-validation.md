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

The screen deliberately shows **20 FPS, H.264 MP4**. It does not
pretend 30/60 FPS is available: that requires sub-tick actor and Jolt physics.
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
