# CAMERA Export: real MP4 experimental test and validation

The export pipeline is still experimental. Do not interpret a passing Forge
compile as proof that OptiFine, Jolt or cinematic media renders correctly.

## 0. First actual CAMERA MP4 (not a PNG diagnostic)

**The first end-to-end native Minecraft-to-MP4 loop is now wired.** It renders
one native world/actor/IMAGE/SUBTITLE frame for each 20 TPS scene tick, captures
the clean output-resolution film FBO and feeds FFmpeg's H.264 encoder directly.
No PNG files are produced. The encoder and ffprobe run off the GL thread.

On Windows, install/extract FFmpeg 7.1 essentials so that both `ffmpeg.exe`
and `ffprobe.exe` are in the same `bin` folder. Either place this folder
in the system PATH or configure the actual executable location:

```powershell
git fetch origin
git switch camera-export-foundation
git pull origin camera-export-foundation
.\gradlew.bat -Dbbs.editor.export.ffmpeg="C:\tools\ffmpeg-7.1-essentials_build\bin\ffmpeg.exe" runClient
```

Replace the example folder with your *actual* FFmpeg installation path.
When FFmpeg is already available through PATH, simply run
`.\gradlew.bat runClient` without any special properties.

1. Open a recorded scene in **CAMERA**.
2. Choose the output aspect/resolution (start with 1920×1080).
3. Disable AUDIO and VIDEO clips for this first **silent** export mode;
   IMAGE and SUBTITLE overlays can be included.
4. Press **Ctrl+Shift+F8**. Video export begins at scene frame 0, continues
   through the full scene at **20 FPS**, and automatically pauses playback.
   The toolbar shows encoded/total frame progress.
5. Press **Ctrl+Shift+F8** or **Esc** to cancel the export. Cancellation
   deletes the incomplete MP4 and restores the previous editor timeline.
6. On success check
   `run/config/bbs-editor/exports/camera-<width>x<height>-*.mp4`.
   FFprobe validates actual decoded frame count, FPS and resolution before
   the export reports completed. No PNG preflight is required.

This initial output uses CAMERA clip positions regardless of FREE vs SHOT
preview mode. It is intentionally limited to **20 FPS**: while sub-tick camera
sampling exists, full 30/60 FPS export also requires continuous actor/Jolt
sampling; repeating a 20 TPS pose would not be real 60 FPS. Audio mux and
frame-exact VIDEO decoding are still future stages. Native Minecraft world
ticks (including environmental motion) have not yet been isolated from the
real-time game clock: complex time-dependent scenes require special care.

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
