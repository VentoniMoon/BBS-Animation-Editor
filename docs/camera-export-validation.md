# CAMERA Export: native PNG probe and video verification

The export pipeline is still experimental. Do not interpret a passing Forge
compile as proof that OptiFine, Jolt or cinematic media renders correctly.

## 1. Native output-resolution probe (Windows / Forge 1.12.2)

The current CAMERA branch contains an *opt-in* single-frame native-world
probe. It does not produce a video and should not be used for production.

1. In `gradle.properties` or your run JVM arguments, enable the system
   property `-Dbbs.editor.export.probe=true`.
2. Launch the normal dev client using `.\gradlew.bat runClient`.
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

**Important:** The PNG is the *world pass only*, captured before the CAMERA
GUI and screen-space FilmCompositor. Image, Video and Subtitle overlays are
not yet included in this probe. A clean native-world image establishes only
the high-resolution render milestone.

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
