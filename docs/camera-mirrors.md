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
across the selected mirror plane. Its **isolated RenderGlobal** keeps a
separate view frustum/visible chunk list and is restored in a finally block.
No world render is issued recursively from
RenderWorldLast or the editor GUI. A separate OpenGL framebuffer stores the
result (maximum dimension 1024 pixels); the normal native world pass then
overwrites the ordinary game framebuffer.

**Recorded Blockbuster/Metamorph/Chameleon actors are intentionally NOT
rendered in the auxiliary pass.** The mutable Blockbuster actor renderer,
Body Parts state and Jolt physics must have exactly one render owner per
normal frame. Only vanilla world terrain/entities are eligible for the
experimental mirror texture. Actor reflections will return later through a
read-only, independently sampled pose renderer.

The mirrored world image is mapped onto the 3D surface in the main
RenderWorldLast with depth-testing so normal blocks can occlude the mirror.
During the auxiliary render, an eye-space OpenGL clip plane removes geometry
on the BACK side of the mirror, including a wall directly behind it. The
plane is installed before the world pass and reinstalled at camera setup.
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
4. Move an actor, step keyframes, and verify its **main-world model** is
   stable (no duplicated body parts or corrupted morphs). Recorded actors
   are NOT yet reflected; this is deliberate until a stateless pass exists.
5. Move and resize the rectangle, rotate yaw, place it in front of a wall,
   and view it at angles. Ensure nearby terrain properly occludes it.
6. Save editor.dat, reopen the scene, and check all mirror parameters.
7. First test with OptiFine shaders **OFF**. With shaders ON the renderer
   intentionally shows a non-reflective fallback rather than risking global
   OptiFine state and destroying the normal world. Export remains unverified.

Under MIRROR PROPERTIES an experimental runtime status appears.
Check `run/logs/latest.log` for `[BBS Editor][Mirror]` if capture stops.

## Limitations / safety

- This is an **experimental world-only reflection**. Active OptiFine
  shaderpacks use the tinted fallback surface by default: second native
  world passes alter OptiFine global shader state outside GL attrib stacks.
  This is intentionally disabled, not yet solved.
- Full compatibility with OptiFine/BetterLights and Minecraft's depth
  buffers is **not yet visually confirmed**.
- The auxiliary pass is expensive: it renders the world a second time while
  a mirror faces the camera.
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
- Actor reflection is disabled in code until a read-only rendering
  implementation is ready; the old `mirrorActors` flag is obsolete.

## Future

1. Verify the isolated world-only renderer preserves main-world chunks,
   block textures, record actors and GL state in the actual game.
2. Add robust near-plane clipping, improved projected UVs, and render-target
   handling for shaderpacks.
3. Optionally batch/limit multi-mirror capture and introduce surface presets.
4. After mirror stabilization, unify editor window/panel style.
