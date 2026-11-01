# CAMERA → MIRRORS (experimental)

Minecraft 1.12.2 · Forge 14.23.5.2860 · OptiFine G5 · BBS Animation Editor

## Scope

A MIRROR is a static, rectangular, single-sided world-space scene object,
**not** a CAMERA timeline clip or a physical block. MIRRORS sits beside
CLIPS and LIGHTS in CAMERA. It is saved in each scene's `editor.dat` as
the optional NBT tag `Scene.CameraSceneMirrors`; older scenes load unchanged.

The MIRRORS list supports up to 32 independent objects per scene:
- Add / Delete and select by row.
- Center position X/Y/Z in world blocks, vertical plane YAW in degrees.
- Width/Height in blocks (0.25 to 64).
- Enabled toggle.
- Drag inside the rectangular viewport hitbox to slide the mirror in its
  own plane; exact numbers can be entered in the inspector.
- The outline is editor-only; the native world receives a physical
  depth-tested reflective face with a narrow frame, offset 0.015 block
  toward its front to avoid z-fighting with a wall.

## Rendering pipeline

A mirror is a 3D surface, not a new world or separate scene. It uses the
EXISTING Minecraft WorldClient and the EXISTING RenderGlobal's compiled
chunk buffers. The reflected image uses its own color/depth framebuffer
only; that is a standard render target, not another world.

At RenderWorldLast, after the normal world is ready:

1. BBS FS renders the normal recorded actors and evaluates their timeline,
   Body Parts and Jolt pose exactly once.
2. Mirror capture creates a reflected camera and builds a temporary
   **mirror-specific visibility list** from the EXISTING ViewFrustum's
   compiled RenderChunk objects. The original renderInfos list and VBO
   camera origin are restored in a finally/AutoCloseable scope. No new
   WorldClient, RenderGlobal, ViewFrustum, or second EntityRenderer frame
   is created. Chunks only visible from the mirror may be compiled lazily
   using the NORMAL Minecraft ChunkRenderDispatcher, budgeted per frame.
3. The already-prepared selected/background EntityActor objects are drawn
   a second time from that reflected camera using read-only render methods.
   These methods DO NOT call drawActor(), morph update, entity tick, Body
   Parts prepare, matrix capture, animation adapters, bake or Jolt sampling.
4. The resulting texture is mapped onto the depth-tested mirror surface
   in the normal native world framebuffer.
5. The regular BBS FS native framebuffer capture and export continues.

The reflective viewport uses a static single-sided rectangle; other mirrors
display a tinted fallback (only one captured reflection per frame). Mirror
geometry never modifies blocks or changes the selected scene.

**Current limitation:** The mirror can see any compiled RenderChunk already
present within the normal ViewFrustum's loaded render-distance area.
Geometry outside that loaded range is unavailable until Minecraft loads
it normally. New back-facing chunks can take a few frames to compile.
The mirror never creates a second world, chunk renderer, or chunk cache.

The mirror sky calls vanilla RenderGlobal.renderSky(partialTicks, 2),
matching Minecraft's non-anaglyph world pass. Using pass 0 previously
applied the anaglyph RGB transform and tinted the mirrored sky pink/purple.

The reflection now delegates weather/biome fog color and the -1 sky /
0 terrain fog phases to the actual native EntityRenderer methods
(updateFogColor/setupFog). It renders vanilla clouds with pass=2 and
the native farther cloud projection. The fog colors are restored after
capture; the main CAMERA preview uses the unchanged BBS FS native pass.
Rain/particles, BetterLights and OptiFine shaderpack parity are still not
fully reproduced by the independent mirror image capture.

## OptiFine shaderpack backend (first stage)

The mirror now probes the active OptiFine G5 renderer reflectively at
RenderWorldLast and distinguishes TWO paths:

1. **Forward-only shaderpacks** (exactly one color and one depth buffer,
   no active deferred, composite or final GLSL programs) can enter the
   experimental shader-reflection path. The existing OptiFine programs
   for sky, clouds, terrain, water and entities are reused, and their
   cameraPosition / gbuffer projection uniforms are directed at the
   reflected view. Their output goes to the mirror's PRIVATE framebuffer.
   Shaders.useProgram restores the previously active program on exit.
   No extra world, shadow render, Blockbuster tick or Jolt step occurs.
   Shaderpacks may still require additional uniforms and state; this path
   needs visual verification with the user's particular pack.
2. **Deferred / multi-attachment shaderpacks** still show a tinted
   fallback. The mirror surface reports the reason (GBuffer color/depth
   count, or presence of deferred/composite/final GLSL stages). Those
   packs require an independent ping-pong GBuffer, frame-dependent
   uniforms and final composite; borrowing a single Minecraft FBO
   would overwrite the main BBS FS shader frame.

## BSL 8.2.02-BetterLights target layout (next shader stage)

A runtime log from the active BSL pack reports:

```
pack=BSL_v8.2.02-BetterLights.zip
colors=8 depth=3 deferred=2 composite=5 final=true
```

The new `CameraMirrorOptiFineTargets` resource class can allocate a private
8-color multi-render-target GBuffer (with two textures per color for
composite ping-pong), three depth textures and its own framebuffer.
Allocation is bounded to 768 px on the longest side and checks hardware
MRT attachment limits and GL framebuffer completeness. The resource
allocator also accepts the **exact** per-channel internal texture formats
from OptiFine's live `gbuffersFormat[]` configuration; silently making every
channel the same format could corrupt BSL's encoded material/lighting data.

This is **resource groundwork only**. The class is not yet invoked by the
normal mirror capture. A diagnostic-only opt-in validates the private
OpenGL targets, the actual `Program.getDrawBuffers()` / `getBuffersFlip()`
metadata and `deferred_pre` / `composite_pre` flips. It draws NO world and
never calls OptiFine's main composite:

```
-Dbbs.editor.camera.mirrorBSLPreflight=true
```

Set this as a **JVM argument before Minecraft launch**. Open CAMERA with
BSL_v8.2.02-BetterLights.zip and inspect `run/logs/latest.log` for
`[BBS Editor][Mirror][BSL Preflight]`. A passing test reports the actual
buffer formats, per-pass color targets, flips, framebuffer completeness,
and GPU attachment support. After the test, the resources are deleted and
the normal OptiFine FBO and viewport are restored. If not opted in, the
GPU test does not allocate textures.

The BSL mirror is still deliberately disabled
because the following must be implemented and verified before activation:
correct native shader-specific color attachment formats and draw-buffer
mappings; depthtex0/1/2 copies at opaque/translucent boundaries; private
deferred/composite ping-pong execution with correct samplers, shadow textures,
camera matrices and flips; final shader output to the mirror's texture;
restoring every main-frame OptiFine global and OpenGL state.

In particular, it must NOT call `Shaders.renderCompositeFinal()` directly
against the normal BBS FS GBuffer. Doing so would mutate the main world's
color attachments and flip history.

### Experimental BSL shader reflection (opt-in, NOT production)

The mirror has an experimental private multi-pass executor using the real
compiled BSL programs and the same posed world/actors:

```
-Dbbs.editor.camera.mirrorBSLExperimental=true
```

This is a **separate client JVM argument**. Without it the validated
non-shader reflection and multipass tinted fallback behave as before.

The experimental implementation allocates/reuses a separate private
8-colortex/3-depth MRT, renders a reflected geometry pass into it, executes
the seven active compiled BSL deferred/composite GLSL programs using each
program's own DRAWBUFFERS mapping, and runs the BSL final GLSL program
to the mirror surface texture. It snapshots/restores normal-frame GL
framebuffer, viewport, 16 texture bindings and changed GLSL uniforms.

After the first runtime screenshot (bright wall/blue reflection despite a
"PASS: 7 shader stages" log), three important corrections were added:
1. The OptiFine 1.12.2 composite samplers are explicitly assigned to the
   native slots: colortex0..3 = 0..3, colortex4..7 = 7..10 and
   depthtex0..2 = 6, 11, 12. Sampling the existing uniform values was
   invalid because a program not activated via Shaders.useProgram may
   still have all its samplers on GL_TEXTURE0. All assigned sampler uniforms
   are restored at scope close, and the fullscreen UV uses texture unit 0.
2. CameraMirrorNativeAtmosphere does NOT clear the private BSL MRT after
   per-buffer initialization; that formerly wiped out all eight channels
   with a shared fog color. Per-channel background clears now follow the
   ACTIVE OptiFine gbuffersClear[] and gbuffersClearColor[] settings.
   Normal no-shader fog clearing is unchanged.
   The reflection also uses its own current view matrices as initial
   previous-frame reprojection uniforms instead of borrowing the main
   camera's history.
3. The BSL geometry, seven postprocessing passes and final framebuffer now
   all have the SAME resolution (maximum 768px on the longest side).
   Earlier the final shader ran at 1024px with `viewWidth/viewHeight`
   uniforms set to 768px, mismatching screen-space sampling and lens
   effects. The nonshader mirror retains its previous 1024px resolution.

### BSL lighting/atmosphere parity pass (October 31)

Based on the next in-game screenshot, geometry and mirror-plane clipping
work but the postprocessed BSL image still has wrong atmospheric lighting,
fog, sky, clouds and blur. This source update addresses the most obvious
mismatches with native OptiFine G5:

- Mirror terrain now selects ProgramTerrainSolid, ProgramTerrainCutoutMip
  and ProgramTerrainCutout by layer, falling back to ProgramTerrain when
  the pack does not compile a dedicated program. The sky uses
  ProgramSkyTextured rather than treating every dome/sky pixel as basic.
- The PRIVATE deferred programs execute at the opaque/translucent world
  boundary (AFTER the opaque depth snapshot, BEFORE ProgramWater), not at
  the end of the frame. Only composite and final run after all geometry.
  Every intermediate fullscreen pass restores textures, program, FBO,
  viewport and matrices before world rendering resumes.
- The private colortex buffers use NEAREST sampling for encoded material,
  normals and masks; final display texture filtering remains separate.
- Each private GBuffer attachment follows OptiFine's gbuffersClear[] /
  gbuffersClearColor[] rules instead of forcing all attachments to zero.
- BSL GLSL programs receive live OptiFine fog, sky, rain, wetness, sun
  and shadow angles, frame timing and environment uniforms. Sun/moon/up
  and shadow-light view directions are transformed from the main view to
  the reflected camera's view matrix.
- The ordinary, already-rendered OptiFine shadow depth/color textures
  are bound as read-only inputs to the mirror's postprocessing; no new
  shadow pass or global Shaders.dfb is created.

The experimental path is STILL opt-in and MUST be visually retested:
the prepared actor draw currently happens AFTER the translucent world
pass, the reflected camera has no independent temporal history, and
BSL's complex clouds/custom weather, postprocess and mixed-resolution
effects may require additional work. A passing Java 8 compilation does
NOT mean BSL image parity has been achieved.

The experimental path remains opt-in and awaits another runtime image
test for confirmation.

### BSL grain and repeated-final diagnostic

The experimental BSL mirror was visibly grainier than the main camera.
The private mirror GBuffer had been hard-limited to **768px** and the
Minecraft `Framebuffer` carrying its finished image defaults to point
sampling (`GL_NEAREST`). Magnifying that texture on the projected
mirror surface accentuated individual screen-space dither pixels.

The experimental BSL capture now defaults to a **1024px** maximum side;
it can be adjusted (bounded from 512 to 1536) using a client JVM arg:

```
-Dbbs.editor.camera.mirrorBSLMaxSize=1280
```

Use 768 if GPU load increases too much. The FINAL mirror color is
sampled with `GL_LINEAR`, while private material/normal/depth textures
remain `GL_NEAREST` (these cannot safely be smoothed). The mirror also
explicitly binds OptiFine's active read-only `noisetex` to unit 15 during
private GLSL postprocessing rather than sampling an unrelated texture left
on that unit. This does NOT disable BSL's dithering or provide
independent temporal antialiasing history.

There is still a possible **double tonemap** issue: the mirror's BSL
`final` output is drawn at `RenderWorldLast` before the normal
camera's final composite. To compare the outcome without changing the
default pipeline, use this additional diagnostic JVM arg:

```
-Dbbs.editor.camera.mirrorBSLSkipFinal=true
```

This displays the private `colortex0` **after** the seven BSL
`deferred/composite` stages but **before** its own `final` stage.
The main normal camera continues its own OptiFine processing. A
pre-final color buffer may be HDR and will be clamped into the LDR
mirror framebuffer, so this is a comparison test, not a supported
final rendering mode. Do not combine it with `mirrorBSLDebugColor`.
The default remains the full seven stages + BSL final.
Visual game tests are still required to verify grain reduction and
determine where tone mapping belongs.

### BSL back-wall reflection fix

The next in-game image showed the mirror reflecting the **stone wall
behind it** rather than the world in front of the mirror. This is a
geometry clipping error, not another color/fog postprocessing problem:
GL_CLIP_PLANE0 is a fixed-function user clip plane and cannot be relied
on with custom OptiFine GLSL vertex shaders that do not write
gl_ClipVertex or gl_ClipDistance.

The BSL-only auxiliary geometry renderer now replaces the standard near
plane with an oblique plane matching the mirror's FRONT half-space
(`CameraMirrorObliqueProjection`). This changes the hardware clip-space
near boundary seen by gl_Position, so the wall behind the mirror is
clipped regardless of user clip-plane support. Apply it AFTER the
reflected-camera perspective setup for BOTH RenderGlobal chunk rendering
and the separate prepared-actor projection. The normal CAMERA projection,
no-shader mirror and native world renderer are unchanged.

A pure Java 8 geometry regression test asserts that the back wall gets
negative clip-space near distance, while front-of-mirror landscape
remains visible, including for rotated/tipped clipping planes. The
runtime prints once: `[BSL Render] Oblique projection installed`.
Visual runtime verification is still required to confirm BSL's actual
vertex programs respect the new projection.
It never runs Minecraft/OptiFine beginRender recursively, never constructs
a new WorldClient, and never steps Jolt a second time.

**Important:** This is the first actual runtime version and has not yet
been visually verified in-game. Native OptiFine shader hooks are disabled
temporarily in the auxiliary renderer (the compiled BSL GLSL is used
explicitly) so a nested beginWater/renderDeferred cannot composite the
MAIN world. The original isRenderingWorld/other shader flags, program,
program ID and draw-buffer references are restored afterwards.

The private depthtex1 is now snapped at the opaque/translucent terrain
boundary, and private depthtex2 after the scene pass. However, the
current actor draw occurs AFTER the translucent world pass (rather
than between opaque/translucent), and additional BSL custom uniforms,
sampler aliases, temporal history and pack-specific postprocessing may
still need work. The final image, main-world shader stability and actor
reflections MUST be visually verified before using this for recording.

To isolate any remaining bright/incorrect output, temporarily add

```
-Dbbs.editor.camera.mirrorBSLDebugColor=0
```

alongside `-Dbbs.editor.camera.mirrorBSLExperimental=true`. This presents
the raw PRIVATE `colortex0` through an OpenGL blit, without deferred,
composite or final. It is NOT expected to look like fully lit BSL; it tells
whether the reflected scene geometry exists inside the raw GBuffer. Remove
the debug flag to return to normal deferred/composite/final rendering.

If the experimental pass fails it logs
`[BBS Editor][Mirror]` / `[BBS Editor][Mirror][BSL Render]`,
latches the mirror off for the editor session, and leaves the default
renderer unchanged. Restart without the experimental flag to go back to
the safe known-good behavior. For a test, turn OFF the old
`mirrorBSLPreflight` argument, enable only
`-Dbbs.editor.camera.mirrorBSLExperimental=true`, pull main, start
Minecraft with BSL BetterLights and open one mirror. Inspect
`[BBS Editor][Mirror][BSL Render]` for the first successful composite
or `[BBS Editor][Mirror]` for the first failure. Check if the ordinary
world is still correct both before and after toggling away from CAMERA.
Do not enable `mirrorUnsafeShaders`.

Never use the old JVM override `mirrorUnsafeShaders` to force
unvalidated multi-pass rendering; the normal CAMERA world must remain
untouched. Full support for this BSL pack is not yet claimed.

## First game test

1. Launch with a recent editor build; enter a scene containing characters.
2. Open CAMERA → MIRRORS → + ADD. The rectangle initially appears about
   five blocks in front of the camera, vertical and facing the viewer.
3. Stand in front of it and confirm it shows the world from the reflected
   viewpoint (not merely a flipped camera screenshot).
4. Move an actor and step keyframes. The original model must remain stable
   and appear in the mirror if it is on the mirror's front side. Both renders
   should use the same pose, without a second Jolt step.
5. Move and resize the rectangle, rotate yaw, place it in front of a wall,
   and view it at angles. Ensure nearby terrain properly occludes it.
6. Save editor.dat, reopen the scene, and check all mirror parameters.
7. First test with OptiFine shaders **OFF** to establish the unchanged
   baseline, then turn on the desired pack. Simple forward packs can
   attempt a shader reflection; multi-pass packs show a reason on the
   mirror inspector / surface. Compare world brightness, shadow, sky,
   actors and shader-specific effects. Export remains unverified.

Under MIRROR PROPERTIES an experimental runtime status appears. As of
the latest debug update, it shows the actual OptiFine shaderpack name and
GBuffer requirements (color/depth count, deferred, composite and final
programs) across multiple lines. The same details are logged once per
detected configuration under `[BBS Editor][Mirror][Shader]` in
`run/logs/latest.log`. This is the most useful diagnostic to attach for a
shaderpack that still shows the blue fallback.

The forward-shader path now snapshots and restores the original GL shader
uniforms (cameraPosition, projection/view matrices) before OptiFine
composites the ordinary camera's frame. This protects the main CAMERA
shader image. It does **not** implement deferred reflections by itself.

## Limitations / safety

- This is an **experimental shared-world reflection**. Forward-only
  shaderpacks may render through an isolated one-color OptiFine path.
  Deferred/composite shaderpacks remain disabled rather than modifying
  the normal world's global shader frame.
- Full compatibility with OptiFine/BetterLights and Minecraft's depth
  buffers is **not yet visually confirmed**.
- Rendering a reflection involves an extra draw of mirror-visible chunks
  and prepared actors; this costs GPU time but does not duplicate chunks.
- Reflected actors are drawn from prepared runtime poses without calling the
  animation/physics evaluator twice. Native morph/Emoticons renderers may
  still perform their own per-draw visual updates; game validation is needed.
- Mirror chunks are chosen separately with a reflected frustum; this fixes
  the previous sky/blue-void reflection caused by reusing the main-camera
  renderInfos list. Source RenderChunks remain limited to the normal
  ViewFrustum's loaded area, and missing ones compile progressively.
- If terrain/reflection capture cannot initialize it fails closed with
  diagnostic status rather than copying the normal camera.
- Current mirrored FOV/UVs use the CPU viewport camera projection; very
  wide output aspect ratios and extremely oblique views may need refinement.
- Mirror-plane clipping is **experimental**, implemented using the OpenGL
  fixed-function clip plane. Some OptiFine shaders may ignore the plane
  because shader vertex programs control clipping themselves. Test with
  shaders OFF first; wall and character geometry behind the mirror should
  disappear from the reflection. A shader-compatible oblique-projection
  fallback may be necessary.
- A mirror is a *visual* plane, not a solid collision box.
- There is not yet an independent reflective capture for each simultaneous
  mirror, and mirrors do not recursively reflect each other.
- A real game check is required before the renderer is considered complete.

Emergency JVM flags (must be supplied before starting Minecraft):

- `-Dbbs.editor.camera.mirrors=false` disables the auxiliary world pass.
  Mirror objects remain editable, but display their tinted fallback.
- `-Dbbs.editor.camera.mirrorUnsafeShaders=true` deliberately bypasses
  the shaderpack guard for debugging, at the risk of renderer corruption.
  Do not enable for normal scene editing.
- The old `mirrorActors` flag is obsolete: prepared actor reflection is
  implemented by the dedicated read-only draw method. Its runtime behavior
  still needs verification in Minecraft.

## Future

1. Verify the shared-world reflection preserves main-world geometry,
   depth, actor morphs and physics in the actual game.
2. Add robust near-plane clipping, improved projected UVs, and render-target
   handling for shaderpacks.
3. Optionally batch/limit multi-mirror capture and introduce surface presets.
4. After mirror stabilization, unify editor window/panel style.

### BSL final-image spatial denoising (October 31)

BSL's final shader still introduces high-frequency dither/noise.
The mirrored camera cannot safely reuse the main camera's temporal
antialiasing history (its projected pixels and previous camera matrices
do not match the reflected view). Raising the render resolution and using
`GL_LINEAR` reduces magnification but does not eliminate the dither.

The experimental BSL mirror now uses a private **13-tap edge-aware spatial
filter** on the **finished LDR color**, after BSL's own final stage.
It runs in an independent GLSL 1.20 fragment program and scratch FBO,
then blits only that cleaned image to the mirror's display texture.
Color-difference weighting protects sharp Minecraft/actor edges.
Normal/material/depth GBuffer channels, global OptiFine targets and
shadow textures are not touched. A failure of this optional filter
falls back to the unchanged reflection rather than disabling the mirror.

Comparison without this extra filter:
```
-Dbbs.editor.camera.mirrorBSLDenoise=false
```

Tune its blend strength from 0 (off) to 1 (maximum edge-aware output),
default 0.90:
```
-Dbbs.editor.camera.mirrorBSLDenoiseStrength=0.90
```

The `mirrorBSLDebugColor` and `mirrorBSLSkipFinal` diagnostic modes
bypass the new filter. This is spatial cleanup, **not** independent
temporal AA: changing geometry and BSL-specific lighting/sky parity still
require separate verification. Test on the active shaderpack in-game.

### BSL mirror: one final tonemap, HDR target (October 31)

The new screenshots exposed a more fundamental bug than grain: switching
the optional denoiser could reveal a nearly uniform blue rectangle, while
the older BSL picture had obvious overexposure. A post-filter cannot fix
an incorrect color pipeline.

In CAMERA, the reflection is drawn during RenderWorldLast, inside the
native OptiFine frame. The mirror's own seven deferred/composite stages
generate HDR `colortex0`. Previously the mirror also ran BSL's `final`
program and stored that already-tonemapped image into Minecraft's default
**8-bit** framebuffer. The ordinary camera could then process this image
again. Filtering that result only hid noise and risked blur.

The experimental BSL path now defaults to:
1. Private reflected geometry + private BSL deferred/composite passes.
2. Copy **linear HDR** colortex0 to a `RGBA16F` mirror surface texture.
3. Draw that surface into the ordinary world's native shader frame.
4. Let the **main BSL final** tonemap the entire image only once.

The private final shader and its LDR-only denoiser are bypassed in this
HDR path. Both denoiser on/off should therefore produce the same geometry
and base exposure; a future independent mirror TAA can be added later.

To compare the previous path (private BSL final + optional denoiser):
```
-Dbbs.editor.camera.mirrorBSLFinalMode=final
```

If an active shaderpack renders the mirror only after its main final, the
legacy mode can be used as a fallback. HDR mode is the default for this
specific BSL RenderWorldLast integration, but visual testing with
BSL_v8.2.02-BetterLights is still required. HDR presentation cannot by
itself fix missing geometry, pack-specific samplers or incorrect lighting
uniforms.

### BSL procedural sky and cloud program fix (October 31)

The reflected HDR scene showed terrain but a featureless dark sky while
the main view had pink BSL clouds. The mirror had selected
`ProgramSkyTextured` for **the whole** native RenderGlobal sky call.
BSL's sky gradient, cloud algorithm and horizon are implemented in its
**`gbuffers_skybasic`** program; skytextured handles the texture-based
skybox and celestial objects. Rendering a complete sky using only
skytextured skipped the pack's atmospheric shading.

For the experimental multi-pass BSL path the sky renderer now defaults
to `ProgramSkyBasic`. The forward and shaderless mirror paths keep their
prior behavior. Shader-specific sampler locations are explicitly
configured during borrowed geometry programs (including lightmap,
normals, shadow and noisetex, with all modified uniforms backed up).
Live OptiFine world time/day, night vision and blindness inputs are
also forwarded for the cloud and sky programs.

To test the previous behavior without reverting the source:
```
-Dbbs.editor.camera.mirrorBSLSkyProgram=textured
```

The debug log now includes
`[BBS Editor][Mirror][BSL Render] Sky program requested=... compiledId=...`
so missing/uncompiled skybasic can be diagnosed. The native shaderpack
may still vary the sky program mid-draw: further in-game visual testing
is necessary, especially for sun/moon textures and fog.

### BSL independent sky + special pre-flips (November diagnostic)

OptiFine invokes most shaderpack geometry helpers only while
`Shaders.isRenderingWorld` is true. The isolated mirror *must* disable that
flag to prevent native `Shaders.beginWater` and `renderDeferred` from
compositing the main world's GBuffer. Simply calling
`RenderGlobal.renderSky` under that scope may therefore produce no
shaderpack sky, even though the private terrain/actor geometry succeeds.

The experimental BSL mirror now runs the **actual compiled
`ProgramSkyBasic` shader** over the independent reflection GBuffer
before terrain. Its full-screen quad is intentionally rendered with
depth writes/tests off, retaining untouched depth for terrain and actors.
The BSL skybasic vertex shader detects stars from equal RGB vertex
colors; this quad uses non-equal RGB to avoid incorrectly labeling the
entire sky as a star. It restores OpenGL state when finished and does
not allocate another WorldClient. To compare against the previous
behavior, use `-Dbbs.editor.camera.mirrorBSLSkyBackdrop=false`.

The private compositor also applies the live OptiFine
`ProgramDeferredPre` and `ProgramCompositePre` ping-pong flip flags
before executing the real shader passes, rather than silently ignoring
these special prepasses. These changes affect only private mirror
texture indices.

This is a targeted parity repair; the full main-renderer sky/fog/
lighting equivalence still needs an in-game BSL screenshot check.

### BSL mirror after-final overlay (dark/tinted reflection fix)

When the private reflected camera runs its own BSL deferred/composite
passes, its finished HDR color is NOT a valid material/normal/albedo input
to the main camera's GBuffer. Writing it into main colortex0 at
RenderWorldLast makes OptiFine process it a second time (lighting, color
curves, fog and tonemapping). This produced a dark orange tint across the
whole reflection even though the private sky was now visible.

The experimental BSL mirror now defaults to **late presentation**:

1. Render the same reflected WorldClient geometry and prepared actors
   into private BSL buffers and run its own final shader.
2. During RenderWorldLast, copy the main scene depth into a separate
   transparent overlay FBO. Draw the projective mirror quad into it with
   native depth testing. Only visible mirror pixels are opaque; nearby
   actors and blocks in front stay visible.
3. Once OptiFine's own final shader finishes, but BEFORE
   `AnimationEditorScreen` captures the world / draws GUI, alpha-composite
   that overlay into Minecraft's resolved color framebuffer.
4. Never run BSL deferred/composite/final on the same mirror color twice.
   Preserve existing mirror geometry and skip denoising in the late mode.

No second WorldClient, new chunks, actor animation or Jolt integration
is introduced. All state and framebuffer bindings are restored. If
native-depth copying fails the renderer logs a single failure and
uses the old WorldLast path for subsequent frames.

To compare against the previous HDR-in-GBuffer rendering:
```
-Dbbs.editor.camera.mirrorBSLPresentation=world
```

This is a change in presentation order, not an arbitrary brightness
increase. It must be validated in-game against the active shaderpack:
private-final uniform parity and shaderpack-specific depth attachment
formats can still cause image differences.

### Opaque alpha mask for the late BSL mirror (October 31)

The late overlay previously copied **both RGB and alpha** from the
private BSL final texture. BSL may use alpha for atmospheric or sky
information, not for physical mirror transparency. Alpha blending then
mixed the reflected blocks with the native scene behind the mirror,
creating a washed-out / overly bright glass-window effect despite the
sky looking correct.

The overlay now uses two depth-tested, read-only passes:

1. Draw the true reflected RGB with `glColorMask(true,true,true,false)`;
   retain the overlay's transparent clear alpha.
2. Draw the same mirror face and frame untextured with
   `glColorMask(false,false,false,true)` and vertex alpha 1.
   Foreground geometry occlusion comes from the copied native depth;
   shaderpack texture alpha is ignored.

The final full-frame composition uses ordinary
`GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA` blending with an explicit
`GL_FUNC_ADD` equation and preserves the original main framebuffer
alpha channel. Thus alpha=0 outside the mirror and alpha=1 on visible
mirror pixels, yielding a fully opaque surface rather than tinted glass.

The regular (nonshader or legacy in-world) mirror still uses its
original draw path. No changes to reflected camera, GBuffer programs,
sky calculations, actors or Jolt physics are introduced. In-game
visual parity must be verified, as separate gamma/exposure differences
could still remain after the alpha issue is eliminated.

### Native BSL auto-exposure history (colortex2) handoff

BSL's `composite5` reads the last frame's exposure value at the
lower-left corner of `colortex2`, then divides scene brightness by
`2 * tempExposure + 0.125`. A private GBuffer with no initialized
temporal feedback yields `tempExposure=0` and can produce roughly
eightfold overexposure. Fixing alpha or setting the world's `timeAngle`
does not initialize this history.

After the private clear contract but before mirror rendering, the BSL
executor now obtains the main OptiFine `dfbColorTexturesFlip.getA(2)`
texture and GPU-blits **only the 4x2 bottom-left metadata region** to
both sides of its own private `colortex2` ping-pong allocation. The
rest of `colortex2` is untouched to avoid borrowing the main camera's
per-pixel TAA history. Scratch framebuffer binding, read/draw state and
texture state are restored; the native OptiFine texture is read-only.
This makes the reflected and normal cameras start from the same current
adaptive-exposure metadata.

Disable for A/B comparison with:
```
-Dbbs.editor.camera.mirrorBSLNativeExposure=false
```

The log prints a one-time `Mirrored colortex2 metadata seeded` on
success, or an explicit warning if the OptiFine API is incompatible.
This specifically tests auto-exposure mismatch; further in-game
validation of all color data and shaderpack passes remains necessary.

### BSL terrain lightmap transform parity (October 31)

The sky, BB/Chameleon actor and gamma-encoded private final output are
visually close to the main camera, but reflective grass and block faces
remain washed out. The BSL `gbuffers_terrain` VSH computes its block
lightmap from `gl_TextureMatrix[1] * gl_MultiTexCoord1`, where OptiFine
chunk VBOs contain the 0..240 packed lightmap values.

Unlike Minecraft's normal `EntityRenderer` render pass, the mirror's
manual `RenderGlobal.renderBlockLayer` calls do not explicitly prepare
this texture matrix. If the prior screen/shader stage left texture unit 1
with an identity matrix, BSL clamps almost every lightmap coordinate to
maximum illumination even while sky and actor programs appear correct.

The BSL mirror block pass now pushes texture unit 1's existing texture
matrix, installs vanilla 1.12.2's standard 1/256 scale with an 8-texel
offset, renders SOLID/CUTOUT_MIPPED/CUTOUT/TRANSLUCENT, and pops the
original matrix. The unit and matrix modes are restored even on error.
Nothing changes for the regular nonshader mirror or main Minecraft
camera. The one-time `[Mirror][Terrain Lightmap]` log prints the
previous matrix scale/offset so a field test can confirm if it was wrong.

For a controlled A/B comparison, disable only this new lightmap scope:
```
-Dbbs.editor.camera.mirrorBSLTerrainLightmap=false
```

If the logged previous matrix already had 1/256 scale, or visual parity
is unchanged, the remaining issue lies in another terrain-specific
input (e.g. OptiFine VBO vertex attributes, material IDs or light
uniforms), not a global color multiplier.

### Active OptiFine GLSL program vs actual GL program (October 31)

The last in-game log confirmed the terrain lightmap matrix already had
the **correct vanilla values** (scale 0.00390625, offsets 0.03125).
The supplemental mirror lightmap transform is thus disabled by default;
it can still be toggled using
`-Dbbs.editor.camera.mirrorBSLTerrainLightmap=true` for diagnosis.

The main difference in the mirror's block rendering is the compiled
shader program *lifecycle*. Private `CameraMirrorBSLCompositor.geometryStage`
called raw `GL20.glUseProgram(ProgramTerrain)` so that native OptiFine
would not rebind its shared main-world framebuffers. But OptiFine
maintains a separate **`Shaders.activeProgram` / `activeProgramID` /
`activeDrawBuffers`** cache. `RenderGlobal.renderBlockLayer()` calls
`EntityRenderer.enableLightmap()` and `disableLightmap()`, which may
switch from cached `ProgramTextured` to `ProgramTexturedLit` through
`Shaders.useProgram()`, replacing the raw `ProgramTerrain` halfway
through a draw while all the private GBuffer targets stay active.

The compositor now updates all three OptiFine bookkeeping fields to
the borrowed compiled program before rendering a private geometry
stage; its existing `close()` already restores the original native
program, program ID and draw buffers without calling global
`Shaders.useProgram`. This prevents native lightmap hooks from
mistaking terrain rendering for an ordinary textured draw.

The mirror also verifies `GL_CURRENT_PROGRAM` immediately after each
solid/cutout layer. Logs now include either `Terrain GLSL verified`
or `TERRAIN PROGRAM OVERRIDE` (expected/actual IDs) for testing.
This is an evidence-driven terrain-program correction; full BSL
reflective lighting parity still needs an in-game screenshot.

### World-anchored BSL shadow map for reflected camera

BSL calculates terrain shadows by projecting positions relative to its
GBuffer camera through `shadowModelView`. The independent mirror reuses
the main camera's shadow textures, but had also reused the unmodified
main `shadowModelView`, so the shadow slid over grass when the camera
moved, even though terrain color and clouds were correct.

The mirror now receives the original and reflected camera positions.
It copies the native OptiFine `shadowModelView` and right-multiplies
it by `translate(reflectedEye-mainEye)`. Thus, for any block world
position P:

`shadowMirror*(P-reflectedEye) = shadowMain*(P-mainEye)`

The transformed matrix and its inverse are assigned only to private
shader programs and restored after rendering. The native shadow FBO,
depth/color textures and `shadowProjection` are read-only and shared.
No duplicate world or shadow-map render is created. An independent
Java 8 invariant test checks several camera displacements.

Log: `BSL shadow map anchored to main world camera; mirror-main delta=...`
or `Mirror shadow anchoring unavailable` if the OptiFine API is missing.

To compare previous behavior:
```
-Dbbs.editor.camera.mirrorBSLShadowAnchor=false
```

This corrects the world-coordinate transform. Reflected points outside
the main camera's shadow-distance coverage still cannot receive shadows
from the shared shadow map; long-distance parity requires independent
shadow coverage and should be checked in-game.

### Private BSL shadow pass for the reflected camera (first version)

OptiFine's normal shadow pass computes shadowtex0/1 and shadowcolor
textures using the main camera's shadow-map coverage. Reusing those
textures, even with a corrected receiver `shadowModelView`, does not
include occluders outside the main camera's shadow coverage. The apparent
large shifting shadow on mirror grass came from this limitation.

The experimental BSL reflection now builds an **independent shadow FBO**
using OptiFine's compiled `ProgramShadow` and the existing
`WorldClient`, `RenderGlobal` and prepared chunk VBOs. It renders
SOLID, CUTOUT_MIPPED and CUTOUT occluders from the reflected camera's
shadow-map origin, copies opaque depth into shadowtex1 before rendering
TRANSLUCENT, and supplies those private depth/color textures on the
normal OptiFine shader sampler units 4/5/13/14. The private shadow
shader's own projection, and the receiver's `shadowModelView`, use
the same (reflected-camera-relative) coordinate system. In particular
the main-camera rebase applied for shared shadow maps is NOT applied
while private shadows are active.

Both camera and OptiFine active-program bookkeeping, draw buffers,
depth buffers, texture bindings and projection/modelview matrices
are scoped and restored; the main `sfb`/native shadow resources
remain untouched. Resources are cached until the editor resets.
A failed private pass falls back to the prior shared shadow map,
with a once-only error log instead of a partially shadowed scene.
Shader-specific shadow depth and color buffer counts, and shadow-map
resolution, are read at runtime. The default dimension cap is 2048,
configurable via `mirrorBSLShadowMaxSize` (256..4096).

Diagnostics:
```
-Dbbs.editor.camera.mirrorBSLPrivateShadows=false
-Dbbs.editor.camera.mirrorBSLShadowMaxSize=2048
-Dbbs.editor.camera.mirrorBSLShadowTranslucent=true
```

This first version includes **block/chunk shadow casters**, including
cutout and translucent block layers. It does NOT render recorded actor
poses or tile entities into the private shadow map yet; they remain
properly visible in the mirror GBuffer, but their cast shadows are not
guaranteed. Prepared-pose actor shadow casting is a separate integration
task. Scene parity must be tested in-game with BSL BetterLights,
especially for transparent blocks and large outdoor areas.

### Private shadow map: stabilize camera snapping and shadow-caster coverage

A private shadow FBO being complete does not guarantee stable shadows.
Two bugs remained in the first implementation:

* The private shadow pass reused `CameraMirrorChunkVisibility.begin`,
  which selects chunks in the **reflected viewing frustum**. Casters
  behind/aside the camera can still shadow the ground seen in the
  reflection, so the rendered shadow map lost/gained occluders as the
  camera moved or turned. The new `beginShadow` selects compiled
  chunks inside the **shadow-map footprint**, regardless of view yaw.
  The shadow projection itself clips them to the light-space map.
* OptiFine's orthographic `shadowModelView` is snapped with the
  MAIN camera remainder `(float)eye % shadowIntervalSize`. A
  reflected map must instead use the REFLECTED camera remainder.
  The new `retargetOrthographicSnap` right-multiplies just the
  remainder delta, preserving the sun-facing rotation, and passes
  the same matrix to both private shadow rendering and BSL receivers.
  The test verifies identical projections while the camera moves
  inside one snapped cell, including signed negative positions.

The transparent-glass stage now reasserts the shadow framebuffer,
draw buffer, shader program, texture atlas and GL alpha/depth state
after the opaque depth copy. This mirrors OptiFine's pre-translucent
shadow boundary and prevents unrelated chunk hooks from redirecting
the tinted shadowcolor write.

Diagnostics:
`Caster chunk selection: ... radius=...`
and `Private mirror shadow rendered ... translucent=true`.

Isolate stained-glass shadow coloring without disabling the entire
reflection:
```
-Dbbs.editor.camera.mirrorBSLShadowTranslucent=false
```
Disable private shadow maps to compare against shared-map fallbacks:
```
-Dbbs.editor.camera.mirrorBSLPrivateShadows=false
```

The private pass still draws block VBO casters only; animated actor
casters need a separately scoped prepared-pose shadow pass. The result
is intended to fix view-driven caster loss, snap drift and shadowcolor
state mismatches; final validation requires in-game movement tests.

### Stable shadow origin anchored to the physical mirror

Field capture showed a *moving* long shadow in the reflection even
though a private shadow FBO reported success and its caster frustum
included 51 chunks. The previous shadow view still targeted the moving
**reflected eye**. This means the shadow-map projection and effective
coverage can change as the user moves the editor camera, even with
the mirror and sun stationary. A full private shadow map by itself
does not eliminate this dependency.

The default orthographic BSL shadow pass now anchors its world-space
shadow projection to the **physical mirror center**. It computes the
native OptiFine sun rotation as before but replaces the main-camera
modulo translation with the precise offset needed to center that same
sun-aligned map around a fixed mirror-world anchor. The private FBO
uses this model-view for rasterization and supplies **the exact same
matrix** to BSL's terrain/actor shadow lookups. Both the main and
reflected eye may move without altering the shadow-space coordinates
of any stationary world block.

Shadow-caster selection is centered on the same mirror-world anchor,
not the reflected eye. The chunk VBO coordinate origin remains the
reflected eye because BSL's shadow vertex program and its GLSL inverse
matrix use that camera-relative coordinate system. No second world,
entity simulation, or OptiFine global shadow FBO is created.

The Java 8 invariant test now compares world-point shadow coordinates
over three different real/reflected camera positions, including
negative world coordinates and changes spanning OptiFine snap intervals.
The one-time private shadow log includes the fixed world anchor.

Colored glass shadow writes remain optional:
```
-Dbbs.editor.camera.mirrorBSLShadowTranslucent=false
```

This isolates translucent shadowcaster failures without disabling
transparent world blocks in the reflection itself. If colored-glass
rendering is still wrong, compare the setting with and without the
translucent shadow stage to identify whether the cause is
`shadowcolor0` or the main GBuffer/composite. In-game tests remain
necessary; compile-time matrix tests do not prove shaderpack visual
parity.

### Native OptiFine shadow eye versus BBS preview camera (October 31)

The physical mirror and caster selection are now fixed in world space, but
shadow drift can persist if the shaderpack's **original** shadow view
is de-snapped using the wrong camera. In OptiFine 1.12.2
`Shaders.setCameraShadow(partialTicks)` obtains the shadow-map origin
from the interpolated `mc.getRenderViewEntity().lastTickPos*/pos*`.
This is not necessarily the BBS preview camera pose; the two can move
independently during editing, particularly while viewing actors.

The previous `fixedWorldAnchor` call cancelled a modulo translation
computed from **BBS preview camera** `main.x/y/z`, not the actual
native `RenderViewEntity` used to construct
`Shaders.shadowModelView`. This left a residual position-dependent
translation despite a stable world anchor. The call now supplies the
same interpolated native eye coordinates used by OptiFine, while still
using the reflected eye for chunk VBO coordinates and the physical
mirror as the fixed shadow-map center.

The new diagnostic prints both native and editor eye coordinates,
the mirror anchor and shadow interval:
`OptiFine shadow native eye=... editor eye=... mirror anchor=...`.
A pure Java 8 regression test now deliberately separates these two
camera positions and confirms that cancelling the editor-eye remainder
would produce a different (wrong) shadow matrix.

This correction applies to orthographic OptiFine shadow maps; any
remaining artifacts require examining the actual shadow-map depth
content and shaderpack-specific shadow sampling, not further
unverified adjustments to lightmap, color or exposure.

### BSL mirror projection: symmetric perspective compatibility (shadow drift)

A/B tests showed that disabling either the private shadow map or the
colored/translucent shadow stage did NOT fix the moving dark regions.
The common cause can be in the BSL terrain shader rather than in shadow
texture allocation. The mirror previously replaced its perspective near
clip plane with an **oblique** projection to hide the physical wall behind
the mirror.

BSL's `ToNDC()` from `lib/util/spaceConversion.glsl` does *not* invert
a general projection matrix. It reads only four diagonal inverse
projection terms. Oblique near-plane clipping changes projection[2]
and projection[6] (screen-X/Y dependence of depth), so the resulting
world positions are incorrect. Both shadow-map sampling and BSL's
cloud-shadow code then appear to slide when the camera moves.

The BSL mirror now defaults to a **standard symmetric perspective**
whose near plane is moved to 98% of the mirror-plane intersection along
the optical axis. This preserves BSL's sparse inverse-projection
contract, avoids re-shading any pixel with the wrong projection, and
passes the new near distance to BSL's `near` uniform. It is an
approximation of a slanted mirror clipping plane: for strongly off-axis
camera views, some behind-plane geometry may leak or some desired
geometry may be clipped at an edge. That exact-edge case will require
a shader-aware geometric clipping solution; reusing oblique projection
is not safe for BSL screen-space lighting.

A/B diagnostic:
```
-Dbbs.editor.camera.mirrorBSLProjectionMode=perspective
-Dbbs.editor.camera.mirrorBSLProjectionMode=oblique
-Dbbs.editor.camera.mirrorBSLProjectionMode=off
```
Only ONE of these values should be used at a time. `perspective` is
the new default. `oblique` is the old exact clip that can make shadows
swim. `off` uses the original unmodified camera projection without
mirror near-plane approximation, for diagnostic isolation only.

The independent Java 8 projection test verifies that the perspective
alternative preserves the depth projection's diagonal form and far
plane, while the oblique projection introduces off-diagonal depth terms.
Game screenshots with camera movement are still required for visual
confirmation. Shadow map calculations are not changed in this patch.

### BSL perspective corner-safe clipping (November 1)

The initial BSL-safe perspective matched the near plane to
98% of the mirror plane on the **optical axis**. At grazing angles the
physical mirror's side and lower corners can project substantially
closer to the reflected eye. Minecraft then clips legitimate terrain
from a portion of the mirror, exposing the rendered sky clear color
as a pink rectangle. The normal eye-space near plane cannot be taken
from the optical axis alone.

When rendering a physical mirror, the renderer now evaluates all four
world-space rectangle corners using the same `viewVector` convention
as the camera. The shader-compatible near distance is set no greater
than 80% of the closest *positive* eye-space corner depth. If any
corner lies behind the eye, it uses the original Minecraft near plane
rather than clipping the face. The projection remains a standard
symmetric perspective, preserving the shaderpack's shadow/world-depth
reconstruction math and the previously validated static shadows.

This is intentionally conservative: a symmetric near plane cannot
exactly match an angled geometric mirror half-space. Some geometry
behind the plane could be exposed at extreme angles. Full clipping
parity would need a shader-compatible world-space clip implementation,
not the broken oblique depth projection. Pure Java regression tests
cover a shallow corner and a corner crossing the eye plane.

### BetterLights lightCount and live active-light arrays

The user-provided log revealed the actual BetterLights-injected GLSL
uniforms: `lightCount`, `lights[i].position`,
`lights[i].positionScreenSpace`, `lights[i].isEnabled`,
`lights[i].color`, etc. The initial examples showing
`lights[0..3].position=(0,0,0)` did not establish which indices were
actually active, and the previous candidate matcher could not
safely change unknown source coordinates.

For each linked shader program that has `lightCount`, the new probe
now reads that count and enumerates up to 24 slots, logging up to
8 populated/enabled light structs, including the corresponding
world/screen positions, colors and intensity. It does not change
lighting uniforms or call BetterLights' unknown private handler.
Only live, evidenced values can justify a subsequent rebase, avoiding
regression of working shadows and BSL exposure.

Use:
```powershell
Select-String -Path '.\run\logs\latest.log' `
  -SimpleMatch '[Mirror][LightProbe] Light struct'
```

### Native BetterLights hook and isolated fragment-accurate shader clip

**Evidence:** the mirrored BSL programs declare `lightCount` and
`lights[i].position`, but every inspected program reported
`lightCount=0` even though the editor has a registered light. The
existing auxiliary compositor bypasses `Shaders.useProgram` and
directly calls `glUseProgram`. BetterLights's OptiFine patching can
depend on the native program-selection lifecycle.

To recover that lifecycle WITHOUT starting a second main world render,
the BSL compositor now calls the original `Shaders.useProgram(Program)`
with `isRenderingDfb=false` only for programs with `lightCount` when
an editor light is registered. It snapshots and restores original
OptiFine active-program state, prevents native draw-buffer output
redirection, rebinds the mirror's own geometry/composite FBO, and
backs up the original `lightCount` GLSL uniform. Log states whether
this actually populated the compiled program or still returned zero.
The hook can be disabled with
`-Dbbs.editor.camera.mirrorBSLNativeLightRefresh=false`.

The missing-wall/behind-wall clipping and BSL's moving shadows require
different handling. The BSL fragment shader has an inverse projection
routine that cannot invert an oblique camera matrix; restoring oblique
clipping would regress the shadow fix. An ordinary near plane cannot
geometrically isolate a tilted mirror. The experimental replacement
uses a *separate copy* of the already linked terrain/water program:
recompiles only the VERTEX shader with one extra output:
`gl_ClipVertex = gl_ModelViewMatrix * gl_Vertex;`. The original fragment
shader, vertex attributes and OptiFine program are unchanged. The
existing world-space `GL_CLIP_PLANE0` is then applied by the fixed
compatibility clip unit in the mirror-only clone. BSL retains the
same symmetric perspective and valid fragment-space reconstruction.

Every active native program uniform, including sampler addresses and
BetterLights light structs, is copied to its private clone before
setting reflected-camera uniforms. The original compiled programs are
never relinked and can continue rendering the main world. Private
clones are cached and deleted when mirror resources reset. If GLSL
source is absent, patch compilation fails or a driver rejects
`gl_ClipVertex`, the mirror falls back to its existing symmetric
perspective path, with a once-only error message. The clipping feature
can be disabled for A/B tests using
`-Dbbs.editor.camera.mirrorBSLShaderPlaneClip=false`.

In-game validation still required: compiled shader compatibility,
accurate fragment clipping on the wall, proper BetterLights count,
and unchanged shadows and transparent blocks. Do not treat CI
compilation as visual proof.
