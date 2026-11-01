# Better Lights in BBS Animation Editor — Light Rig and CAMERA Scene Lights

## Screenshots / version scope

Designed against the **BetterLights 1.0.3.PREVIEW** integration in Blockbuster
2.7.1, Minecraft Forge 1.12.2 / Java 8. Its settings are discovered through
Blockbuster's own `BetterLightsMorph.getValueManager()` / ValueSerializer, so
the actual list of nested settings comes from the installed light provider.
The following group names and specific values are observed in the user's
original Light 0 screenshot, not a claim about every BetterLights build.

| Group / field | Screenshot state | Explanation |
|---|---|---|
| **Light** | Checked | Enables the actual illumination contribution. Disabling it is not necessarily the same as deleting the light object; other effects are independent |
| **Shadows** | Checked | Enables shadow casting/occlusion by the source, if the active shader, surface and BetterLights pipeline support it. Potentially expensive |
| **Flare effect** | Unchecked | Camera-facing bright flare/glare from the source; unlike Light, it is a visual lens effect, not a mechanism for lighting nearby blocks |
| **Volumetric Fog Effect** | Unchecked | Enables in-air scattering (visible illuminated haze/shafts) rather than only illuminated world surfaces. This can be expensive and depends on shader support |
| **Samples** | 20 | Number of volumetric ray-march/integration steps (quality/performance tradeoff). Higher values normally reduce noise/banding and increase work |
| **Scattering Albedo** | 0.5 | Fraction of extinction attributed to scattering instead of absorption. Larger values cause more light to be re-scattered into visible fog, subject to the shader model |
| **Density** | 20.0 | Density/strength of this source's volumetric medium. Greater density generally increases visible fog and attenuation. The screenshot does *not* establish physical units or linear visual response |
| **Anisotropy** | 0.4 | Angular bias of scattering. 0 is approximately isotropic, positive values emphasize forward scattering, negative values backscattering in common phase-function models. The exact implementation/range should be confirmed in BetterLights shaders |

**Important:** The white horizontal lines under the screenshot's group headings
are GUI separators/section elements, not evidence of individual unlabelled
numeric sliders. `Samples`, `Scattering Albedo`, `Density` and
`Anisotropy` are *nested under Volumetric Fog Effect*; their values may
be stored even when the enclosing effect is disabled.

The editor intentionally doesn't implement fake controls or assume an
intensity/range or softness property where the installed provider exposes
none. The properties list, boolean switches and hexadecimal color values
come from native Blockbuster `ValueSerializer`. This also preserves unknown
settings and nested configuration fields in the original morph NBT.

## Single native lighting engine (new architecture)

Both editors now drive **BetterLightsWorldEngine** in
`BbsFsWorldCameraDriver.renderTick(START)`. It owns one
BetterLights `LightCaster` per stable source ID (actor or CAMERA static).
Sources are prepared **before** the native Minecraft/OptiFine world pass
and its shader uniforms, not during `RenderWorldLast`.

- Actor and CAMERA lights use the same `BetterLightsWorldState`: world
  Origin, world Target, normalized direction, all in Minecraft blocks.
- Native Blockbuster/MCLib `ValueSerializer` applies **every installed
  BetterLights property**, including optional shadow, flare and fog values.
  Only position/direction are overridden with editor-owned world geometry.
- Source slots do **not** create an `EntityActor`, call
  `BetterLightsMorph.update`, create Blockbuster dummy runnables, or draw
  the light morph a second time. They register the caster through the
  provider's own temporary-light manager once per stable source.
- Changing native settings updates the one slot; switching world/scenes,
  deleting/disabling a light, or closing the editor unregisters it.
  Shader/BetterLights API access is isolated behind reflection, allowing
  the editor to load without BetterLights installed.
- The wireframe and movement gizmos are editorial overlays, not exported
  footage; for POSE they consume the same `BetterLightsWorldState` as the
  lighting backend.

**World coordinate contract:** no model pixels (no division by 16),
Steve skeleton offset, actor-facing yaw flip, or camera-relative transform.

## POSE — actor light rig

Select a BetterLightsMorph actor in **POSE**. Instead of a Steve skeleton,
the editor presents two independent control tracks, both rooted at the
actor's Record XYZ sampled at the current scene time:

- `Origin` — editable world-block **offset** for the light source.
- `Target` — editable world-block offset for a separate **aiming point**
  (initially four blocks along world +Z from Record). Moving Origin does
  not automatically move Target. The light always aims from current Origin
  toward current Target.

The original Record `.dat` is never modified by either track.
Keyframe interpolation and duplication work through existing AnimationBone
tracks. **RIG KEYS** shows XYZ controls for the selected light key (no
fake skeletal rotation or scale). **LIGHT** shows the native provider
properties; selecting an Origin key edits native settings on that key,
otherwise it edits the actor's base BetterLights morph.

`LightSettings` compounds are preserved in the scene editor.dat and
numeric light properties are interpolated by the existing settings sampler.
Switches are discrete. Pausing, seeking or rendering fractional 60 FPS
samples must update the **same** caster rather than spawning a new one.

**Migration:** old `Target` keys from the earlier Origin-relative
implementation may need manual repositioning. Existing key data is not
silently rewritten, since it is ambiguous whether it represented a point
or a direction vector.

## CAMERA — static light editor, no keys

CAMERA's **LIGHTS** tab retains the ADD/REMOVE source list, ORIGIN,
TARGET, and LIGHT properties. ORIGIN and TARGET are independent *absolute*
world coordinates, editable through axis handles or numeric fields.
No source creates a Record actor or a CAMERA timeline clip.

**Workflow:** click **+ ADD** to create a source six world blocks in front
of the actual film camera; it aims four blocks further along the camera view.
Select an item in the LIGHTS list (scroll the list if needed) and edit:

- **ORIGIN**: world position of the light. Move its three X/Y/Z arrows
  directly inside the film viewport or type precise XYZ in the right panel.
- **TARGET**: an *independent* world point for the beam. Switch with the
  TARGET tab or click the cyan **T** marker in the viewport. The source's
  **O** marker switches back to Origin.
- **LIGHT**: all native BetterLights settings, preserved as NBT, including
  nested shadows, flare and volumetric effects. The O/T markers and beam
  guide remain available to inspect positions.
- **REMOVE** / **TOGGLE LIGHT**: immediately disable or dispose the
  matching native LightCaster without creating any additional actors.

CAMERA now uses **the same native 3D Move gizmo as POSE**. The active
Origin or Target is drawn by `EditorGizmoController.draw3D` inside the
Preview framebuffer with the same red/green/blue solid arrows, perspective
and gray XY/XZ/YZ plane handles as the working actor light rig. Input also
uses that controller's hit-test/axis/plane drag logic.

A private, unsaved control Bone/Key provides the movement delta; it is NOT
a Blockbuster scene actor, recorded bone, or persisted key. The delta is
applied directly to the selected static light's absolute world XYZ and
saved in editor.dat on mouse release. The LIGHTS inspector keeps numeric
coordinate editing and all native BetterLights settings.

The old 2D GUI arrow/letter renderer and its separate `CameraLightWorldGizmo`
logic have been removed. Only the light-beam outline and a small marker for
the *other* anchor remain in the GUI preview. Neither the native gizmo
nor the markers are part of final rendered footage. A coincident Origin/
Target pair is rejected because a spotlight direction would be undefined.

Static lights remain saved in scene editor.dat under
`CameraSceneLights` with stable IDs, enabled state, NBT settings and
world points. The rendering engine now consumes these saved values
**directly** instead of synthesizing an actor/Record each frame.

When the editor is not in CAMERA, scene-only static sources are not
registered. Animated light actors remain available in POSE and other
normal scene preview modes (subject to actor visibility settings).

## Acceptance checks requiring real Minecraft/BetterLights

1. A BetterLightsMorph actor shows exactly two POSE tracks, Origin and
   Target. Existing normal characters still have their full skeletons.
   Origin key moves the light and Target key changes its beam direction.
2. Add Origin keys at frames 0, 20 and 40. Change native color, range/
   intensity where available, and Shadows/Flare/Fog toggles in LIGHT;
   verify the two positions and numeric interpolation, not only NBT.
3. Ctrl-drag an Origin key: both transform and light properties are copied.
   Ctrl+S, reopen, compare original light actor Record and saved key data.
4. In CAMERA, create two independent lights, move each Origin and Target
   with the projected gizmos, and edit native settings. No light keys
   should appear on the clip timeline.
5. Save editor.dat, reopen; all scene light coordinates, enabled flags,
   colors and nested settings should persist.
6. Render 20/60 FPS with multiple lights, shadows and volumetric effects.
   Compare the native MP4 with SHOT preview; watch for a one-frame
   LightCaster activation delay, shader shadow instability or unexpected
   light following the camera.
7. Delete or disable sources, close CAMERA; verify immediate caster unregistration and that
   sources do not accumulate on scene changes. Repeat without BetterLights loaded.
8. Test an extreme FOV/9:16 preview; the lightweight projected XYZ
   handles may require tighter native-matrix alignment in future. They
   are not the skeletal EditorGizmoController and have no Rotate ring.

Successful Java 8 and full Forge compilation is not a substitute for these
shaderpack/client tests.

## Reflected CAMERA pass: BetterLights origin scope

The normal BBS CAMERA frame uses `CameraBetterLightsCameraOrigin.begin`
at RenderTick.START: BetterLights' OptiFine uniform bridge subtracts
`Minecraft.player`'s interpolated coordinates rather than the detached
`PreviewCameraEntity`'s render-camera coordinates. The shim temporarily
maps the player fields onto the MAIN preview camera, and restores the
real player at RenderTick.END.

When CAMERA draws a planar mirror inside RenderWorldLast, the
`PreviewCameraEntity` is temporarily moved to the **reflected** position.
Previously, `Minecraft.player` remained at the main camera origin,
so BetterLights could evaluate the same world-fixed caster using a
different coordinate origin from the reflected BSL matrices. This
causes camera-locked or sliding light on reflected surfaces even when
the separate shadow-map projection is already correct.

The mirror callback now creates a nested
`CameraBetterLightsCameraOrigin.MirrorScope` around reflected world
and prepared-actor drawing, AFTER moving the preview camera and BEFORE
executing its terrain shaders. The scope saves all nine player position
fields, temporarily copies the actual reflected camera fields into
them, and restores **the prior main CAMERA shim** on close. The outer
RenderTick.END still restores the real player. No world position of any
LightCaster is changed, no extra light sources are created, and no
entity simulation is rerun. Exception and aborted-frame cleanup are
covered by smoke tests.

One-time diagnostic:
`[BBS Editor][Mirror][BetterLights] Reflected light origin applied; main=... reflected=...`

To A/B-test this change, pass
`-Dbbs.editor.camera.mirrorBSLLightOrigin=false` and restart the client.

This isolates the known PLAYER-versus-REFLECTED-viewpoint bug. If
light still appears to move, BetterLights may be caching its uniforms
during OptiFine's main `useProgram` rather than re-evaluating them
during the auxiliary private GL program draw. In that case the next
step is a scoped uniform refresh using the installed BetterLights
build's actual GLSL names/API, not a second world render or an
arbitrary brightness/position multiplier.

### Dynamic BetterLights GLSL uniform rebasing for reflected CAMERA

Field testing confirmed the mirror's nested player-position spoof runs,
but light still follows the editor camera. The reason can be that
BetterLights 1.0.3's `OptifineShaderUniformsHandler` uploads caster
uniforms during the main OptiFine world render and does not run when the
mirror selects already-compiled programs using raw `glUseProgram`.
Changing `Minecraft.player` in RenderWorldLast cannot automatically
change a uniform already stored on the GPU.

The BSL mirror now performs a **value-verified** rebase of live GL
`vec3`/`vec4` uniforms on the borrowed compiled programs. It does
NOT guess provider-specific names or automatically change everything
containing 'light'. Instead it:

- Gets the *same registered* world-space caster origins already prepared
  once at RenderTick.START by `BetterLightsWorldEngine`.
- Enumerates the compiled program's actual active `vec3`/`vec4`
  uniforms (including arrays), reads their existing values with
  `glGetUniform`, and matches the XYZ against a caster's position
  either in main-camera-relative space or after the main model-view.
- For confirmed matches only, recomputes the caster's corresponding
  reflected-camera-relative or reflected-view coordinates and writes
  them with the existing BSL uniform backup/restore mechanism.
  `vec4.w` remains intact.
- If an active uniform contains *absolute world coordinates*, it is
  already camera-independent and remains unchanged. Sun, moon, shadow,
  camera uniforms and unrelated GLSL state are never modified.
- Restores all changes at the end of the mirror pass before OptiFine
  resumes drawing the regular scene.

On first successful application the log includes
`[Mirror][BetterLights] Updated compiled GLSL light position` plus
the uniform name and old/new coordinates. If none match, it prints
`No direct position-uniform match yet` with candidate names, to
distinguish a truly unsupported provider layout (e.g., buffer blocks
or shader-generated light matrices) from an incorrectly guessed fix.

A/B test:
```
-Dbbs.editor.camera.mirrorBSLLightUniforms=false
```
The nested player-origin mirror scope remains independently controlled
by `mirrorBSLLightOrigin`. A Java 8 regression test covers relative,
view-space, absolute world-space, and unrelated uniforms; actual
in-game BSL/BetterLights parity still requires visual verification.

### Mirror BetterLights: identifying the real shader data layout

The log `No direct position-uniform match yet; ... vec3/4 candidates=1`
was misleading: the old probe emitted that result after the FIRST
borrowed GLSL program in `overrideCamera`, not after all terrain,
deferred, composite, and final programs were examined.

The new diagnostic records the unique compiled GLSL program IDs
encountered by the reflected render. For each program it prints the
actual active uniform count, the number of `vec3/vec4` declarations,
other uniform types, names containing relevant light/caster keywords,
and a limited sample of vector values. A final `No direct
position-uniform match after entire frame` message only prints after
the mirror compositor has closed and all programs have been visited.

A read-only provider probe also identifies shader/uniform/patcher
classes from the **user-installed BetterLights JAR**, without needing
to redistribute the private provider binary or guess a package name.
It lists the names/signatures of candidate uniform-handler methods and
fields. It does not call arbitrary private methods, edit provider
sources, or mutate main OptiFine state.

To collect decisive logs:
```powershell
Select-String -Path '.\run\logs\latest.log' `
  -SimpleMatch '[Mirror][LightProbe]', `
               'No direct position-uniform match after entire frame', `
               'Updated compiled GLSL light position'
```

You can disable only this one-time probe using
`-Dbbs.editor.camera.mirrorBSLLightProbe=false`. The existing
value-confirmed rebase remains enabled independently. If there are
no source uniforms in all programs, the next implementation must
use the actual provider pipeline (e.g., buffer texture/light
compositor) rather than inventing a position-uniform name.

### Verified BetterLights positionScreenSpace reprojection

When BetterLights supplies positive `lightCount` in a compiled BSL
program, the provider's `lights[i].positionScreenSpace` may still
contain coordinates calculated from the MAIN camera. The mirror now
checks this vec4 separately from `lights[i].position`. It projects
each currently registered world caster using the actual MAIN view
and projection matrices and tests four supported encodings: eye-space
vec4, clip-space vec4, normalized-device vec4 and UV/depth vec4.
An encoding is accepted only if every component agrees with the
existing GPU uniform to within a small bounded numeric tolerance.
Only then is the same caster projected through the REFLECTED view
and projection, and only that single GLSL uniform is overridden.
The existing uniform backup restores the original value before
returning to the main camera.

Zero/unknown uniforms are NOT populated or guessed. The one-time
`Reprojected verified positionScreenSpace` log identifies any actual
change. Otherwise the GLSL program stays untouched. Pure Java 8
tests cover all four encodings and rejection of unrelated values.
This is a conservative compatibility fix; visual outcome remains
unverified until in-game tests with populated BetterLights uniforms.
