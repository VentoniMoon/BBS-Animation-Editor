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
"PASS: 7 shader stages" log), two important corrections were added:
1. The OptiFine 1.12.2 composite samplers are explicitly assigned to the
   native slots: colortex0..3 = 0..3, colortex4..7 = 7..10 and
   depthtex0..2 = 6, 11, 12. Sampling the existing uniform values was
   invalid because a program not activated via Shaders.useProgram may
   still have all its samplers on GL_TEXTURE0. All assigned sampler uniforms
   are restored at scope close, and the fullscreen UV uses texture unit 0.
2. CameraMirrorNativeAtmosphere does NOT clear the private BSL MRT after
   per-buffer initialization; that formerly wiped out all eight channels
   with a shared fog color. BSL's colortex1 is independently cleared white
   and the other channels black. Normal no-shader fog clearing is unchanged.
   The reflection also uses its own current view matrices as initial
   previous-frame reprojection uniforms instead of borrowing the main
   camera's history.
3. The BSL geometry, seven postprocessing passes and final framebuffer now
   all have the SAME resolution (maximum 768px on the longest side).
   Earlier the final shader ran at 1024px with `viewWidth/viewHeight`
   uniforms set to 768px, mismatching screen-space sampling and lens
   effects. The nonshader mirror retains its previous 1024px resolution.

The experimental path remains opt-in and awaits another runtime image
test for confirmation.

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
