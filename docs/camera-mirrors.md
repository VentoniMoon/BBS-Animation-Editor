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
