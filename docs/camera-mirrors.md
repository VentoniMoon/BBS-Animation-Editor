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

An optional **second native world pass** is performed at RenderTick.START,
prior to the normal world pass, from the reflected position and direction
across the selected mirror plane. No world render is issued recursively from
RenderWorldLast or the editor GUI. A separate OpenGL framebuffer stores the
result (maximum dimension 1024 pixels); the normal native world pass then
overwrites the ordinary game framebuffer.

Recorded Blockbuster actors are rendered from the **reflected camera** in the
auxiliary RenderWorldLast, then rendered normally afterward. Both calls use
the SAME timeline tick. Existing movement and Jolt caches are expected to
reuse already simulated tick poses rather than advancing time twice.
This must still be verified in the actual game.

The mirrored world image is mapped onto the 3D surface in the main
RenderWorldLast with depth-testing so normal blocks can occlude the mirror.
It remains visible in the CAMERA preview and is eligible for native export,
because it is rendered in the world before frame capture.

Only **one mirror's texture is captured per frame** (closest visible
front-facing mirror). Additional enabled mirrors draw as dark tinted planes.
Mirrors never recursively reflect other mirrors.

## First game test

1. Launch with a recent editor build; enter a scene containing characters.
2. Open CAMERA → MIRRORS → + ADD. The rectangle initially appears about
   five blocks in front of the camera, vertical and facing the viewer.
3. Stand in front of it and confirm it shows the world from the reflected
   viewpoint (not merely a flipped camera screenshot).
4. Move an actor, step keyframes, and check the same actor is visible in the
   reflection. Test a model with Jolt hair; it should not simulate twice.
5. Move and resize the rectangle, rotate yaw, place it in front of a wall,
   and view it at angles. Ensure nearby terrain properly occludes it.
6. Save editor.dat, reopen the scene, and check all mirror parameters.
7. Check with shaders enabled and disabled, then a short CAMERA export.

Under MIRROR PROPERTIES an experimental runtime status appears.
Check `run/logs/latest.log` for `[BBS Editor][Mirror]` if capture stops.

## Limitations / safety

- This is a **first experimental render**. OptiFine shaderpacks may render
  their final image through another internal FBO and bypass the ordinary
  framebuffer used by the mirror copy; test with shaders OFF first.
- Full compatibility with OptiFine/BetterLights and Minecraft's depth
  buffers is **not yet visually confirmed**.
- The auxiliary pass is expensive: it renders the world a second time while
  a mirror faces the camera.
- Current mirrored FOV/UVs use the CPU viewport camera projection; very
  wide output aspect ratios and extremely oblique views may need refinement.
- True mirror-plane *clipping* (hiding geometry located behind the reflective
  surface from the virtual camera) is not implemented. Placing a mirror
  flush against thick walls may reveal incorrect occlusion in reflection.
- A mirror is a *visual* plane, not a solid collision box.
- There is not yet an independent reflective capture for each simultaneous
  mirror, and mirrors do not recursively reflect each other.
- A real game check is required before the renderer is considered complete.

Emergency JVM flags (must be supplied before starting Minecraft):

- `-Dbbs.editor.camera.mirrors=false` disables the auxiliary world pass.
  Mirror objects remain editable, but display their tinted fallback.
- `-Dbbs.editor.camera.mirrorActors=false` keeps the world reflection but
  omits the extra recorded-actor draw if Jolt or actor preview has trouble.

## Future

1. Validate actor, morph, OptiFine and Jolt compatibility in the actual game.
2. Add robust near-plane clipping, improved projected UVs, and render-target
   handling for shaderpacks.
3. Optionally batch/limit multi-mirror capture and introduce surface presets.
4. After mirror stabilization, unify editor window/panel style.
